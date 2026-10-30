// SPDX-License-Identifier: MPL-2.0

plugins {
    id("glow.java")
    id("com.gradleup.shadow") version "9.4.0"
    application
}

dependencies {
    implementation(project(":cli"))
}

application {
    mainClass = "io.github.bric3.glowadoc.bootstrap.Main"
}

val bootstrap = sourceSets.create("bootstrap")

val dependencyNotices = tasks.register<CollectDependencyNotices>("dependencyNotices") {
    group = "distribution"
    description = "Collect licence and notice files from runtime dependencies."
    artifacts.from(configurations.runtimeClasspath)
    destination = layout.buildDirectory.dir("dependency-notices")
}

tasks.named<JavaCompile>(bootstrap.compileJavaTaskName) {
    options.release = 8
    classpath = files()
}

tasks.shadowJar {
    from(bootstrap.output)
    archiveBaseName = "glow-adoc"
    archiveClassifier = ""
    mergeServiceFiles()
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    exclude(
        "META-INF/*.SF",
        "META-INF/*.RSA",
        "META-INF/*.DSA",
        "module-info.class",
        "META-INF/versions/*/module-info.class",
    )
    manifest {
        attributes(
            "Multi-Release" to "true",
            "Implementation-Version" to project.version,
            "Enable-Native-Access" to "ALL-UNNAMED",
        )
    }
    isZip64 = true
    from(rootProject.layout.projectDirectory.file("LICENSE")) {
        into("META-INF/glow-adoc")
    }
    from(rootProject.layout.projectDirectory.file("THIRD-PARTY.md")) {
        into("META-INF/glow-adoc")
    }
    from(rootProject.layout.projectDirectory.dir("licenses")) {
        into("META-INF/third-party")
    }
    from(dependencyNotices.flatMap { it.destination }) {
        into("META-INF/third-party")
    }
}

val executableJar = tasks.register<ExecutableJar>("executableJar") {
    group = "distribution"
    description = "Create a shell-prefixed executable copy of the Shadow jar."
    inputJar = tasks.shadowJar.flatMap { it.archiveFile }
    outputJar = layout.buildDirectory.file("libs/glow-adoc-${project.version}-app.jar")
}

tasks.register<Copy>("install") {
    group = "distribution"
    description = "Install the glow-adoc executable to ~/.local/bin."
    from(executableJar.flatMap { it.outputJar })
    into(providers.systemProperty("user.home").map { "$it/.local/bin" })
    rename { "glow-adoc" }
    filePermissions {
        unix("rwxr-xr-x")
    }
}

tasks.assemble {
    dependsOn(executableJar)
}

tasks.jar {
    enabled = false
}

tasks.distTar {
    enabled = false
}

tasks.distZip {
    enabled = false
}

tasks.named("shadowDistTar") {
    enabled = false
}

tasks.named("shadowDistZip") {
    enabled = false
}

tasks.test {
    jvmArgumentProviders.add(
        objects.newInstance<PackagedCliArguments>().apply {
            jar = tasks.shadowJar.flatMap { it.archiveFile }
            executable = executableJar.flatMap { it.outputJar }
            fixture = rootProject.layout.projectDirectory.file("fixtures/showcase.adoc")
        },
    )
}

val aotTrain = tasks.register<AotTrain>("aotTrain") {
    group = "distribution"
    description = "Train a Java 27 AOT cache against the packaged application."
    jar = tasks.shadowJar.flatMap { it.archiveFile }
    document = rootProject.layout.projectDirectory.file("fixtures/showcase.adoc")
    javaExecutable = providers.systemProperty("java.home").map { "$it/bin/java" }
    cache = layout.buildDirectory.file("aot/glow-adoc.aot")
}

// SPDX-License-Identifier: MPL-2.0

import io.github.bric3.glowadoc.buildlogic.AotTrain
import io.github.bric3.glowadoc.buildlogic.CollectDependencyNotices
import io.github.bric3.glowadoc.buildlogic.ExecutableJar
import io.github.bric3.glowadoc.buildlogic.InstallExecutable
import io.github.bric3.glowadoc.buildlogic.PackagedCliArguments

plugins {
    id("glowadoc.java")
    id("com.gradleup.shadow") version "9.6.1"
    application
}

dependencies {
    implementation(project(":glowadoc-cli-common"))
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
    launcherScript = layout.projectDirectory.file("src/main/scripts/java-launcher.sh")
    outputJar = layout.buildDirectory.file("libs/glow-adoc-${project.version}-app.jar")
}

val releaseMetadata = tasks.register<WriteProperties>("releaseMetadata") {
    destinationFile = layout.buildDirectory.file("release-metadata/version.properties")
    property("version", project.version.toString())
}

tasks.register<Sync>("releaseArtifacts") {
    group = "distribution"
    description = "Stage release jars, version metadata and notices."
    into(layout.buildDirectory.dir("release"))
    from(tasks.shadowJar.flatMap { it.archiveFile })
    from(executableJar.flatMap { it.outputJar })
    from(releaseMetadata.flatMap { it.destinationFile })
}

val installWithoutAot = tasks.register<InstallExecutable>("installWithoutAot") {
    group = "distribution"
    description = "Install glow-adoc to ~/.local/bin without AOT training."
    jar = executableJar.flatMap { it.outputJar }
    launcherScript = layout.projectDirectory.file("src/main/scripts/installed-launcher.sh")
    executable = layout.file(providers.systemProperty("user.home").map { File("$it/.local/bin/glow-adoc") })
    val dataHome = providers.environmentVariable("XDG_DATA_HOME")
        .filter { File(it).isAbsolute }
        .orElse(providers.systemProperty("user.home").map { "$it/.local/share" })
    installedJar = layout.file(dataHome.map { File(it, "glow-adoc/glow-adoc.jar") })
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
    environment("COLORTERM", "truecolor")
    environment("XDG_CACHE_HOME", layout.buildDirectory.dir("test-cache").get().asFile.absolutePath)
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
    description = "Pre-train an installable executable with the bundled AsciiDoc workload."
    jar = executableJar.flatMap { it.outputJar }
    executable = layout.buildDirectory.file("aot/glow-adoc.jar")
}

installWithoutAot.configure {
    mustRunAfter(aotTrain)
}

tasks.register("install") {
    group = "distribution"
    description = "Pre-train locally, then install glow-adoc with a reusable AOT cache."
    dependsOn(aotTrain, installWithoutAot)
}

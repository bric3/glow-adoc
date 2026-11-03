// SPDX-License-Identifier: MPL-2.0

import io.github.bric3.glowadoc.buildlogic.AOT_JAR_TIMESTAMP
import io.github.bric3.glowadoc.buildlogic.AotTrain
import io.github.bric3.glowadoc.buildlogic.CollectDependencyNotices
import io.github.bric3.glowadoc.buildlogic.ExecutableJar
import io.github.bric3.glowadoc.buildlogic.InstallExecutable
import io.github.bric3.glowadoc.buildlogic.PackagedCliArguments
import java.nio.file.FileSystems
import java.nio.file.Files

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
    outputLauncher = layout.buildDirectory.file("launcher/glow-adoc")
}

val aotRuntime = providers.exec {
    commandLine("/bin/sh", layout.projectDirectory.file("src/main/scripts/java-launcher.sh"), "--aot-runtime-identity")
}.standardOutput.asText

val aotTrain = tasks.register<AotTrain>("aotTrain") {
    group = "distribution"
    description = "Train a packaged AOT cache with the launcher's selected JDK and bundled AsciiDoc workload."
    jar = executableJar.flatMap { it.outputJar }
    runtimeIdentity = aotRuntime.map { it.trim().lineSequence().last() }
    cacheDirectory = layout.buildDirectory.dir("aot")
}

val distributionContents = copySpec {
    into("glow-adoc-${project.version}") {
        from(executableJar.flatMap { it.outputLauncher }) {
            into("bin")
            filePermissions { unix("755") }
        }
        from(executableJar.flatMap { it.outputJar }) {
            into("lib")
            rename { "glow-adoc.jar" }
        }
        from(rootProject.layout.projectDirectory.file("LICENSE"))
        from(rootProject.layout.projectDirectory.file("THIRD-PARTY.md"))
    }
}

val distributionWithoutAotZip = tasks.register<Zip>("distributionWithoutAotZip") {
    group = "distribution"
    description = "Package the launcher and jar without AOT."
    archiveBaseName = "glow-adoc"
    archiveClassifier = "without-aot"
    destinationDirectory = layout.buildDirectory.dir("distributions")
    with(distributionContents)
}

val distributionZip = tasks.register<Zip>("distributionZip") {
    group = "distribution"
    description = "Package the launcher, jar and trained AOT cache in a versioned ZIP."
    archiveBaseName = "glow-adoc"
    destinationDirectory = layout.buildDirectory.dir("distributions")
    with(distributionContents)
    val root = "glow-adoc-${project.version}"
    from(aotTrain.flatMap { it.cacheDirectory }) {
        into("$root/lib/aot")
    }
    doLast {
        // ZIP needs a UTC timestamp field so extraction in another time zone preserves AOT validity.
        FileSystems.newFileSystem(archiveFile.get().asFile.toPath()).use { zip ->
            Files.setLastModifiedTime(zip.getPath("$root/lib/glow-adoc.jar"), AOT_JAR_TIMESTAMP)
        }
    }
}

val releaseMetadata = tasks.register<WriteProperties>("releaseMetadata") {
    destinationFile = layout.buildDirectory.file("release-metadata/version.properties")
    property("version", project.version.toString())
}

tasks.register<Sync>("releaseArtifacts") {
    group = "distribution"
    description = "Stage the release ZIP and version metadata."
    into(layout.buildDirectory.dir("release"))
    from(distributionZip.flatMap { it.archiveFile })
    from(distributionWithoutAotZip.flatMap { it.archiveFile })
    from(releaseMetadata.flatMap { it.destinationFile })
}

tasks.withType<InstallExecutable>().configureEach {
    jar = executableJar.flatMap { it.outputJar }
    launcherScript = layout.projectDirectory.file("src/main/scripts/installed-launcher.sh")
    executable = layout.file(providers.systemProperty("user.home").map { File("$it/.local/bin/glow-adoc") })
    val dataHome = providers.environmentVariable("XDG_DATA_HOME")
        .filter { File(it).isAbsolute }
        .orElse(providers.systemProperty("user.home").map { "$it/.local/share" })
    installedJar = layout.file(dataHome.map { File(it, "glow-adoc/glow-adoc.jar") })
    installedAot = layout.dir(dataHome.map { File(it, "glow-adoc/aot") })
}

tasks.register<InstallExecutable>("installWithoutAot") {
    group = "distribution"
    description = "Install glow-adoc to ~/.local/bin without AOT."
}

tasks.register<InstallExecutable>("install") {
    group = "distribution"
    description = "Install glow-adoc and its packaged AOT cache."
    aotCache = aotTrain.flatMap { it.cacheDirectory }
}

tasks.assemble {
    dependsOn(executableJar, distributionZip, distributionWithoutAotZip)
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
            distribution = distributionZip.flatMap { it.archiveFile }
            distributionWithoutAot = distributionWithoutAotZip.flatMap { it.archiveFile }
            aotJava = layout.file(aotRuntime.map { File(it.lineSequence().first()) })
            fixture = rootProject.layout.projectDirectory.file("fixtures/showcase.adoc")
        },
    )
}

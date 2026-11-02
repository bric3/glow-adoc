// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.buildlogic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;

class QualityTest {
    @TempDir
    Path directory;

    @Test
    void enforcesHeadersAndFormattingAndPreservesExecutableDirectives() throws Exception {
        Files.createDirectories(directory.resolve("gradle"));
        Files.copy(Path.of(System.getProperty("quality.catalog")), directory.resolve("gradle/libs.versions.toml"));
        Files.writeString(
                directory.resolve("settings.gradle.kts"),
                "rootProject.name = \"quality-fixture\"\ndependencyResolutionManagement { repositories { mavenCentral() } }\n"
        );
        Files.writeString(
                directory.resolve("build.gradle.kts"),
                "// SPDX-License-Identifier: MPL-2.0\nplugins { base; id(\"glowadoc.quality\") }\n"
        );
        Files.createDirectories(directory.resolve("src/main/java"));
        Files.createDirectories(directory.resolve("src/test/java"));
        Path table = directory.resolve("src/test/java/Table.java");
        Files.writeString(
                table,
                "import org.tabletest.junit.TableTest;\nclass Table {\n@TableTest(\"\"\"\nvalue | "
                + "expected\na | 1\nlonger | 2\n\"\"\")\nvoid example(String value, int expected) {}\n}\n"
        );
        Files.createDirectories(directory.resolve("tools"));
        Files.createDirectories(directory.resolve("fixtures"));
        Path java = directory.resolve("src/main/java/Sample.java");
        Files.writeString(java, "class Sample{int value(){return 42;}}\n");
        Path jbang = directory.resolve("tools/Demo.java");
        Files.writeString(jbang, "//JAVA 27\nclass Demo {}\n");
        Path existing = directory.resolve("tools/Existing.java");
        String directives =
                "//JAVA 27\n//DEPS org.example:demo:1.0\n// SPDX-License-Identifier: MPL-2.0\nclass Existing {}\n";
        Files.writeString(existing, directives);
        Path shell = directory.resolve("tools/demo.sh");
        Files.writeString(shell, "#!/bin/sh\nprintf 'demo\\n'\n");
        Path excluded = directory.resolve("fixtures/Sample.java");
        Files.writeString(excluded, "unformatted fixture\n");
        Path convention = directory.resolve("build-logic/conventions/src/main/java/Convention.java");
        Files.createDirectories(convention.getParent());
        Files.writeString(convention, "class Convention{int value(){return 42;}}\n");
        Path generated = directory.resolve("build-logic/conventions/build/generated/src/main/java/Generated.java");
        Files.createDirectories(generated.getParent());
        Files.writeString(generated, "generated fixture\n");
        Path launcher = directory.resolve("glowadoc-cli-jvm/src/main/scripts/launcher.sh");
        Files.createDirectories(launcher.getParent());
        Files.writeString(launcher, "#!/bin/sh\nprintf 'launcher\\n'\n");

        assertTrue(runner("checkLicenses").buildAndFail().getOutput().contains("FAILED"));
        runner("applyLicenses", "spotlessApply").build();
        assertTrue(Files.readString(java).startsWith("// SPDX-License-Identifier: MPL-2.0\n"));
        assertTrue(Files.readString(java).contains("return 42;"));
        assertTrue(Files.readString(convention).startsWith("// SPDX-License-Identifier: MPL-2.0\n"));
        assertTrue(Files.readString(convention).contains("return 42;"));
        assertEquals("generated fixture\n", Files.readString(generated));
        assertTrue(Files.readString(launcher).startsWith("#!/bin/sh\n# SPDX-License-Identifier: MPL-2.0\n"));
        assertEquals(
                1,
                Files
                    .readString(table)
                    .lines()
                    .filter(line -> line.contains("|"))
                    .map(line -> line.indexOf('|'))
                    .distinct()
                    .count()
        );
        assertTrue(Files.readString(jbang).contains("//JAVA 27\n"));
        assertTrue(Files
            .readString(existing)
            .startsWith("//JAVA 27\n//DEPS org.example:demo:1.0\n// SPDX-License-Identifier: MPL-2.0\n")
        );
        assertTrue(Files.readString(shell).startsWith("#!/bin/sh\n# SPDX-License-Identifier: MPL-2.0\n"));
        assertTrue(Files.readString(shell).contains("printf 'demo\\n'"));
        assertEquals("unformatted fixture\n", Files.readString(excluded));
        String formatted = Files.readString(java);
        runner("spotlessApply", "checkLicenses").build();
        assertEquals(formatted, Files.readString(java));
        runner("check", "--configuration-cache").build();
        assertTrue(runner("check", "--configuration-cache")
            .build()
            .getOutput()
            .contains("Reusing configuration cache"));
    }

    private GradleRunner runner(String... arguments) {
        return GradleRunner
            .create()
            .withProjectDir(directory.toFile())
            .withPluginClasspath()
            .withArguments(arguments);
    }
}

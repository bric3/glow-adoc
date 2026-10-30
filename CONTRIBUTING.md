<!-- SPDX-License-Identifier: MPL-2.0 -->

# Contributing

Use Java 27 and the checked-in Gradle wrapper. Application sources target Java 25; the compatibility bootstrap targets Java 8. `mise.toml` selects the development JDK.

## Build and check

```sh
./gradlew checkAll
./gradlew :cli-jvm:shadowJar :cli-jvm:executableJar
java -jar cli-jvm/build/libs/glow-adoc-0.1.0-SNAPSHOT.jar fixtures/showcase.adoc
```

Use focused checks while iterating, for example `./gradlew :asciidoctor:test` or `./gradlew :terminal:test`. Packaged-CLI tests exercise stdin, configuration overrides, paths containing spaces, pager input, theme notifications and Kitty protocol output through a pseudo-terminal.

To check Mermaid rendering, install a reviewed Merman release separately and run:

```sh
GLOW_TEST_MERMAN=/path/to/merman-cli ./gradlew :diagrams:test
```

The Merman test is skipped when that variable is absent. Do not add downloads or tool installation to ordinary Gradle builds.

## Formatting and design

Follow `.editorconfig`: four spaces for Java and Kotlin, two for YAML and Ruby, UTF-8 and LF line endings. Format Java with Google Java Format's AOSP style and Kotlin with ktlint's IntelliJ IDEA style. Keep one statement per line, expand control-flow blocks, and separate methods and logical groups with blank lines.

Keep changes small and read the connected code. [ARCHITECTURE.md](ARCHITECTURE.md) describes module ownership. Parser, highlighting and UI implementations belong in their adapters. Tests should check observable behaviour, especially source preservation, capability fallbacks, resource restrictions and cleanup.

Add the MPL SPDX header to new owned source files. Preserve upstream copyright and licence notices when reusing code. Use concise Conventional Commit messages such as `fix: preserve source indentation`.

## Packaging and dependencies

Check dependency licences before introducing or changing a distributed dependency. Keep `plantuml-mit`; the default `plantuml` artifact has different licensing and features. The project licence does not replace dependency licences. See [THIRD-PARTY.md](THIRD-PARTY.md).

After an intentional dependency update, regenerate locks and verification metadata, inspect the resulting changes, and run checks:

```sh
./gradlew checkAll --write-locks --write-verification-metadata sha256
./gradlew checkAll
```

Review checksums against trusted publications when adding artifacts. Automatic checksum generation records what was downloaded; it does not independently authenticate a new publication.

Verify install changes using a temporary home:

```sh
./gradlew -Duser.home="$PWD/cli-jvm/build/install-check" :cli-jvm:install
./cli-jvm/build/install-check/.local/bin/glow-adoc --version
```

Train AOT with `./gradlew :cli-jvm:aotTrain`. Load the resulting cache with the same JDK build that ran Gradle. Native Image is experimental; successful compilation requires a separate runtime smoke test.

## Before sending a change

Run the relevant checks and review the final diff for unrelated changes. State any failed, skipped or unavailable verification. Changes to terminal rendering need manual checks in Ghostty/Kitty, an unsupported terminal and SSH when those behaviours are affected. PTY tests establish protocol behaviour, not visual appearance.

GitHub CI runs Linux/macOS checks, AOT and a pinned Merman integration. Native Image has a manual feasibility workflow. Tag pushes prepare a draft release; publication remains manual. Update the Homebrew template's release fields only after real artifacts and checksums exist.

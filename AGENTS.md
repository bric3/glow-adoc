<!-- SPDX-License-Identifier: MPL-2.0 -->

# Working on glow-adoc

## Build and verification

- Run the Gradle wrapper with Java 27. Prefer `build-brief ./gradlew ...` locally; show plain wrapper commands in documentation.
- Application sources target Java 25. The dependency-free JVM bootstrap targets Java 8 and reports the Java 27 runtime requirement.
- Run `./gradlew checkAll` after code changes. Run focused module tests while iterating.
- Set `GLOW_TEST_MERMAN` to a reviewed local executable to enable the Merman integration test. Ordinary builds must not install Merman.
- Verify packaging changes through the final Shadow jar and executable jar. Verify install changes with a temporary `user.home`.
- PTY tests verify protocol handling. Real-terminal appearance, SSH and Native Image runtime compatibility require separate checks.

## Code and module boundaries

- Keep changes focused and preserve existing user changes. Commit or push only when explicitly requested for this repository.
- Use four-space indentation for Java and Kotlin. Expand multiple statements, control-flow blocks and methods onto separate lines. Separate methods and logical groups with blank lines.
- Use Google Java Format's AOSP style for Java and ktlint's IntelliJ IDEA style for Kotlin; honour `.editorconfig`.
- Keep AsciidoctorJ nodes inside `asciidoctor`, Lumis4J inside `highlight-lumis4j`, and terminal/UI types inside their adapters. Read [ARCHITECTURE.md](ARCHITECTURE.md) before changing module boundaries.
- Add behaviour where its data and knowledge live. Prefer existing APIs and narrow dependencies; avoid abstractions for hypothetical features.
- Add comments for non-obvious reasons. Use international English and ASCII punctuation in prose; preserve meaningful Unicode in source and tests.
- Use Java or Kotlin with JBang for non-trivial maintenance scripts. Simple shell commands are fine for discovery and builds.

## Rendering and licensing

- Preserve source text and formatting boundaries. Never forward document-supplied terminal controls.
- Keep file resources within the document base directory. Preserve subprocess time/output limits, cancellation and terminal restoration.
- Missing terminal capabilities and renderers must leave readable output. Test partial replies and unsupported capabilities when changing detection.
- Owned source files use `SPDX-License-Identifier: MPL-2.0`. Preserve upstream notices and review the final dependency graph when changing dependencies.
- Use the MIT PlantUML artifact. Do not replace it with the default GPL artifact without reviewing distribution obligations.
- Keep third-party notice packaging, dependency locks and verification metadata current. See [THIRD-PARTY.md](THIRD-PARTY.md).

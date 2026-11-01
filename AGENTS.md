<!-- SPDX-License-Identifier: MPL-2.0 -->

# Working on glow-adoc

## Build

- Use Java 27 and the Gradle wrapper. Prefer `build-brief ./gradlew` locally; document plain wrapper commands.
- Application sources target Java 25; the dependency-free bootstrap targets Java 8 and reports the Java 27 requirement.
- Run focused tests while iterating and `./gradlew checkAll` after code changes.
- Enable optional Merman tests with `GLOW_TEST_MERMAN` pointing to a reviewed executable. Builds must not install Merman.
- Verify both final jars after packaging changes. Verify install changes with a temporary `user.home`.
- PTY checks establish protocol behaviour. Appearance, fonts, SSH and Native Image runtime need separate checks.

## Changes

- Preserve existing work. Commit or push only when explicitly requested for this repository.
- Use `io.github.bric3.glowadoc` as the base package for owned Java and Kotlin types, including tests, build logic and tools.
- Read [Architecture](ARCHITECTURE.md) before changing boundaries. Keep AsciidoctorJ in `asciidoctor`, Lumis4J in `highlight-lumis4j`, and terminal/UI types in their adapters.
- Put behaviour with its data and knowledge. Prefer existing APIs and narrow dependencies; avoid speculative abstractions.
- Use four-space Java/Kotlin indentation, Google Java Format AOSP and ktlint IntelliJ style. Honour `.editorconfig`.
- Expand control-flow blocks. Separate methods and logical groups. Comment non-obvious reasons.
- Use international English and ASCII punctuation in prose; preserve meaningful Unicode in code and tests.
- Use Java or Kotlin with JBang for non-trivial maintenance scripts. Simple shell discovery/build commands are allowed.

## Rendering and licensing

- Preserve source text and formatting boundaries. Never forward document-supplied terminal controls.
- Confine includes to the document directory. Images may also use declared roots and explicit absolute CLI directories. Check resolved symlinks against those roots.
- Preserve subprocess time/output limits, cancellation and terminal restoration.
- Unsupported capabilities and renderers must retain readable output. Test partial replies and unsupported capabilities after detection changes.
- Owned sources use `SPDX-License-Identifier: MPL-2.0`. Preserve upstream notices and review dependency licensing after changes.
- Keep MIT PlantUML. Replacing it with the GPL artifact requires reviewing distribution obligations.
- Keep packaged notices, dependency locks and verification metadata current. See [Third-party software](THIRD-PARTY.md).

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
- Prefix subproject and included build names with `glowadoc-`. Name application module directories after their projects.
- Read [Architecture](ARCHITECTURE.md) before changing boundaries. Keep AsciidoctorJ in `glowadoc-asciidoctor`, Lumis4J in `glowadoc-highlight-lumis4j`, and terminal/UI types in their adapters.
- Keep diagram engines in their adapter modules. The pager depends only on `glowadoc-diagrams-common`.
- Put behaviour with its data and knowledge. Prefer existing APIs and narrow dependencies; avoid speculative abstractions.
- Use four-space indentation and 120 columns. Spotless runs Prince of Space, then the TableTest formatter, for Java and ktlint IntelliJ style for Kotlin. Honour `.editorconfig`.
- Prefer TableTest for input/output matrices, using argument converters when useful. Keep ordinary JUnit tests for lifecycle and asynchronous scenarios.
- Cover behavioural changes with observable tests before editing production code. Measure hot paths and cold start before retaining performance changes.
- Run `./gradlew spotlessApply :glowadoc-build-logic:spotlessApply` to format and `./gradlew checkAll` to check formatting, SPDX headers and tests. Use `applyLicenses` to add owned headers; preserve shebangs and JBang directives.
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

<!-- SPDX-License-Identifier: MPL-2.0 -->

# Contributing

Use Java 27 and the Gradle wrapper. Application bytecode targets Java 25; the bootstrap targets Java 8.
Read [Architecture](ARCHITECTURE.md) before changing module boundaries.

## Build and verify

```sh
./gradlew checkAll
./gradlew :glowadoc-cli-jvm:distributionZip
./gradlew :glowadoc-cli-jvm:distributionWithoutAotZip
./gradlew :glowadoc-cli-jvm:install
```

Run focused module tests while iterating. Enable the optional Mermaid integration with
`GLOW_TEST_MERMAN=/path/to/merman-cli ./gradlew :glowadoc-diagrams-merman:test`. Builds must not install Merman.

PTY tests verify events and protocol output. Check appearance, fonts, graphics, cancellation and
restoration separately in a real terminal. Include SSH checks when changing transport behaviour.
Use [the performance procedure](docs/PERFORMANCE.adoc) for startup and scrolling changes.

## Code

Use four-space indentation and 120 columns. Spotless runs Prince of Space followed by the TableTest
formatter for Java, and ktlint's IntelliJ style for Kotlin. Apply formatting explicitly:

```sh
./gradlew spotlessApply
```

`checkAll` checks formatting and MPL-2.0 SPDX headers in application, bootstrap, build and tool sources.
Yumi Licenser owns headers; `applyLicenses` inserts missing headers
while preserving shebangs and JBang directives. Fixtures, generated files and upstream notices are excluded.

Prefer TableTest for input/output matrices. Use argument converters for domain values; ordinary JUnit
tests suit lifecycle, asynchronous and PTY scenarios. Add observable regression coverage before changing
behaviour. Measure performance changes against the same JDK, fixtures and cache state.

Expand control-flow blocks and separate methods and logical groups. Honour `.editorconfig`.

Keep parser nodes, highlighter implementations and terminal types inside their adapters.
Preserve source text, graphemes, folding state, resource boundaries and readable fallbacks.
Test observable behaviour. Keep changes focused and preserve unrelated work.

New owned files need the MPL SPDX header. Preserve upstream notices.
Use Conventional Commits. Commit, push and publication require explicit authorisation.

## Packaging

Verify both jars. Test installation with a temporary home:

```sh
XDG_DATA_HOME="$PWD/glowadoc-cli-jvm/build/install-check/data" \
XDG_CACHE_HOME="$PWD/glowadoc-cli-jvm/build/install-check/cache" \
./gradlew -Duser.home="$PWD/glowadoc-cli-jvm/build/install-check" :glowadoc-cli-jvm:install
./glowadoc-cli-jvm/build/install-check/.local/bin/glow-adoc --version
```

AOT training uses the launcher's selected JDK: `./gradlew :glowadoc-cli-jvm:aotTrain`.
The task writes `cache.aot` and `cache.key` under `glowadoc-cli-jvm/build/aot`, with Gradle up-to-date and build-cache support.
`install` and `distributionZip` include that cache; `installWithoutAot` and `distributionWithoutAotZip` omit it.
Native Image requires a separate runtime smoke test.
Check the small script and its separate jar. The installed jar's bytes, basename and timestamp
must match the staged `glow-adoc.jar` so AOT loading survives relocation. Training, installation and the ZIP
use the fixed UTC jar timestamp `1980-02-01T00:00:00Z`, including after build-cache restoration.

Dependency changes require licence review, updated locks and verified checksums:

```sh
./gradlew checkAll --write-locks --write-verification-metadata sha256
./gradlew checkAll
```

Checksum generation records downloaded bytes; verify new publications independently.
Keep `plantuml-mit` and the [packaged dependency notices](THIRD-PARTY.md).

## Release

CI checks pull requests on Linux and macOS, and pushes on Linux. It covers configuration-cache reuse,
AOT and pinned Merman integration.
Native Image has a manual feasibility workflow. Run `./gradlew :glowadoc-settings-conventions:test` for version-policy
tests and `jbang tools/Release.java --self-test` for release lifecycle checks.

The settings convention `glowadoc.versioning` applies Javier's Semver plugin. Untagged repositories build
`0.1.0-SNAPSHOT`. A clean `v0.1.0` checkout builds `0.1.0`; subsequent commits build `0.1.1-SNAPSHOT`.
Select a minor bump with `-Psemver.scope=minor -Psemver.tagPrefix=v`.
Gradle builds do not create or push tags.

Create a draft release with its intended version tag, such as `v0.1.0` or `v0.2.0-rc.1` for a prerelease.
Keep that tag uncreated until publication. After the Linux and Merman CI jobs pass, master refreshes
the newest draft. If there is no draft, the refresh is skipped.

CI preserves the title, release notes and prerelease flag, updates the target commit, and packages the
tested application classes and resources with the draft version. It creates the tag only in its private
checkout. `-PreleaseTag=TAG` selects that exact version and requires a clean checkout at the tag;
`-PreleasePrerelease=true|false` checks that the GitHub prerelease flag matches the version.

The versioned ZIP and its checksum replace previous glow-adoc assets after successful uploads.
The archive contains a `glow-adoc-VERSION` directory with `bin/glow-adoc`, `lib/glow-adoc.jar` and licence notices.
CI verifies the target commit and uploaded assets. Publish the draft to create its release tag and trigger
another checked build on Linux from that tag, which refreshes the published assets. Release and prerelease tags
are supported; snapshot tags and build metadata are rejected.

Retry final uploads through the Release workflow's manual dispatch with the published tag.
`./gradlew :glowadoc-cli-jvm:releaseArtifacts` stages the ZIP and version metadata under `glowadoc-cli-jvm/build/release`.
Keep GitHub release immutability disabled for this publish-then-replace process.
Complete the Homebrew template's release fields before distribution.

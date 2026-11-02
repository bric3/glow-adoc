<!-- SPDX-License-Identifier: MPL-2.0 -->

# Contributing

Use Java 27 and the Gradle wrapper. Application bytecode targets Java 25; the bootstrap targets Java 8.
Read [Architecture](ARCHITECTURE.md) before changing module boundaries.

## Build and verify

```sh
./gradlew checkAll
./gradlew :cli-jvm:shadowJar :cli-jvm:executableJar
./gradlew :cli-jvm:install
```

Run focused module tests while iterating. Enable the optional Mermaid integration with
`GLOW_TEST_MERMAN=/path/to/merman-cli ./gradlew :diagrams:test`. Builds must not install Merman.

PTY tests verify events and protocol output. Check appearance, fonts, graphics, cancellation and
restoration separately in a real terminal. Include SSH checks when changing transport behaviour.
Use [the performance procedure](docs/PERFORMANCE.adoc) for startup and scrolling changes.

## Code

Use four-space Java/Kotlin indentation, Google Java Format AOSP style and ktlint IntelliJ style.
Expand control-flow blocks and separate methods and logical groups. Honour `.editorconfig`.

Keep parser nodes, highlighter implementations and terminal types inside their adapters.
Preserve source text, graphemes, folding state, resource boundaries and readable fallbacks.
Test observable behaviour. Keep changes focused and preserve unrelated work.

New owned files need the MPL SPDX header. Preserve upstream notices.
Use Conventional Commits. Commit, push and publication require explicit authorisation.

## Packaging

Verify both jars. Test installation with a temporary home:

```sh
./gradlew -Duser.home="$PWD/cli-jvm/build/install-check" :cli-jvm:install
./cli-jvm/build/install-check/.local/bin/glow-adoc --version
```

AOT training uses the executable launcher's selected JDK: `./gradlew :cli-jvm:aotTrain`.
`install` trains before installation; `installWithoutAot` skips training. Native Image requires a separate runtime smoke test.

Dependency changes require licence review, updated locks and verified checksums:

```sh
./gradlew checkAll --write-locks --write-verification-metadata sha256
./gradlew checkAll
```

Checksum generation records downloaded bytes; verify new publications independently.
Keep `plantuml-mit` and the [packaged dependency notices](THIRD-PARTY.md).

## Release

CI covers Linux/macOS, configuration-cache reuse, AOT and pinned Merman integration.
Native Image has a manual feasibility workflow. Run `./gradlew -p build-logic test` for version-policy
tests and `jbang tools/Release.java --self-test` for release lifecycle checks.

The settings convention `glow.versioning` applies Javier's Semver plugin. Untagged repositories build
`0.1.0-SNAPSHOT`. A clean `v0.1.0` checkout builds `0.1.0`; subsequent commits build `0.1.1-SNAPSHOT`.
Select a minor bump with `-Psemver.scope=minor -Psemver.tagPrefix=v`.
Builds do not create or push tags.

Every successful master release build creates or updates the upcoming version's draft, target commit,
snapshot jars and checksums. Human release notes are preserved. Publish the draft to create its release
tag and trigger a final build from that tag. Final jars and checksums replace snapshot downloads after
successful uploads. Snapshot assets remain public while that build runs.

Retry final uploads through the Release workflow's manual dispatch with the published tag.
`./gradlew :cli-jvm:releaseArtifacts` stages jars and version metadata under `cli-jvm/build/release`.
Keep GitHub release immutability disabled for this publish-then-replace process.
Complete the Homebrew template's release fields before distribution.

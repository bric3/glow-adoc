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
Installation does not train a cache. Native Image requires a separate runtime smoke test.

Dependency changes require licence review, updated locks and verified checksums:

```sh
./gradlew checkAll --write-locks --write-verification-metadata sha256
./gradlew checkAll
```

Checksum generation records downloaded bytes; verify new publications independently.
Keep `plantuml-mit` and the [packaged dependency notices](THIRD-PARTY.md).

## Release

CI covers Linux/macOS, configuration-cache reuse, AOT and pinned Merman integration.
Native Image has a manual feasibility workflow. Tag pushes prepare draft releases;
publication remains manual. Complete the Homebrew template's release fields before distribution.

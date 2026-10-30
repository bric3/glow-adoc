<!-- SPDX-License-Identifier: MPL-2.0 -->

# Third-party software

glow-adoc's own source is licensed under [MPL 2.0](LICENSE). Dependency code, native libraries, Ruby gems, syntax grammars, themes and other bundled resources retain their upstream licences. The project licence does not replace those terms.

## Runtime dependency review

The review uses the resolved runtime graph, the published POMs at the versions in `cli-jvm/gradle.lockfile`, and the upstream distributions. Versions and checksums remain recorded in the lockfiles and `gradle/verification-metadata.xml`.

| Components                                        | Published licence terms                                |
| ------------------------------------------------- | ------------------------------------------------------ |
| AsciidoctorJ and its API                          | Apache-2.0                                             |
| Aesh command and terminal libraries               | Apache-2.0                                             |
| TamboUI modules                                   | MIT                                                    |
| Lumis4J and Chicory                               | Apache-2.0; bundled resources retain upstream terms    |
| PlantUML `plantuml-mit`                           | MIT; upstream attribution is included in `licenses/`   |
| TOMLJ                                             | Apache-2.0                                             |
| ANTLR runtime and ASM modules                     | BSD-3-Clause                                           |
| JRuby base and standard library                   | EPL-2.0 OR GPL-2.0 OR LGPL-2.1                         |
| JNR POSIX                                         | EPL-2.0 OR GPL-2.0 OR LGPL-2.1                         |
| Dirgra                                            | EPL-1.0                                                |
| Other JNR, Headius, Joda-Time and JiteScript code | Predominantly Apache-2.0; retain component notices     |
| JFFI                                              | Apache-2.0 and LGPL terms; inspect bundled notices     |
| JCodings, Joni, JNR x86 assembler, Checker Qual   | MIT                                                    |
| JZlib and CRaC                                    | BSD variants                                           |

These published terms permit combining the project with dependency components while preserving their obligations. JRuby and JNR POSIX offer EPL alternatives; their inclusion does not make glow-adoc's own files GPL-licensed. Source availability and notice requirements for copyleft components still apply. MPL compatibility is not permission to remove another component's licence.

The default `net.sourceforge.plantuml:plantuml` artifact is GPL-licensed. This project deliberately uses `net.sourceforge.plantuml:plantuml-mit` at the same version. The MIT variant supplies the UML/Smetana rendering used here; ditaa is not included. Changing the PlantUML variant requires reviewing both feature support and distribution terms.

References: [Mozilla MPL FAQ](https://www.mozilla.org/en-US/MPL/2.0/FAQ/), [PlantUML distributions](https://plantuml.com/download), [PlantUML MIT publication](https://central.sonatype.com/artifact/net.sourceforge.plantuml/plantuml-mit/1.2026.6).

## Notices in the jars

The Shadow and shell-prefixed jars include:

- `META-INF/glow-adoc/LICENSE` and `META-INF/glow-adoc/THIRD-PARTY.md` for this project.
- `META-INF/third-party/` with reference licence texts and PlantUML's own attribution output.
- Per-artifact subdirectories preserving licence, notice, copying, copyright and author files found in runtime jars, including nested Ruby-gem notices.

The collector preserves identical filenames from different dependencies separately. It does not infer the licence of every native or generated payload. Before publishing a release, review embedded-component notices against the exact upstream versions, supply any missing notices, and make the corresponding copyleft sources available as required.

Merman is an optional, separately installed executable. Its Rust dependencies, notices and installation channels remain part of its upstream distribution; glow-adoc does not bundle its binary.

Build tools and test dependencies are not included merely because Gradle uses them. The Gradle wrapper retains its upstream Apache licence header, and generated build outputs are outside this project's source-licence headers.

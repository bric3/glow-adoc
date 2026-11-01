<!-- SPDX-License-Identifier: MPL-2.0 -->

# Third-party software

Project source uses [MPL 2.0](LICENSE). Dependencies, native libraries, gems, grammars, themes and
bundled resources retain their upstream licences. Preserve those obligations when distributing the jars.

## Dependency terms

The review covers the resolved runtime graph, published POMs and upstream distributions.
Exact versions and checksums are recorded in lockfiles and `gradle/verification-metadata.xml`.

| Components                                      | Published licence terms                             |
| ----------------------------------------------- | --------------------------------------------------- |
| AsciidoctorJ and its API                        | Apache-2.0                                          |
| Aesh command and terminal libraries             | Apache-2.0                                          |
| TamboUI modules                                 | MIT                                                 |
| Lumis4J and Chicory                             | Apache-2.0; bundled resources retain upstream terms |
| PlantUML plantuml-mit                           | MIT; upstream attribution is in licenses/           |
| TOMLJ                                           | Apache-2.0                                          |
| jsoup                                           | MIT                                                 |
| ANTLR runtime and ASM                           | BSD-3-Clause                                        |
| JRuby base and standard library                 | EPL-2.0 OR GPL-2.0 OR LGPL-2.1                      |
| JNR POSIX                                       | EPL-2.0 OR GPL-2.0 OR LGPL-2.1                      |
| Dirgra                                          | EPL-1.0                                             |
| Other JNR, Headius, Joda-Time and JiteScript    | Predominantly Apache-2.0; retain component notices  |
| JFFI                                            | Apache-2.0 and LGPL terms; inspect bundled notices  |
| JCodings, Joni, JNR x86 assembler, Checker Qual | MIT                                                 |
| JZlib and CRaC                                  | BSD variants                                        |

JRuby/JNR POSIX offer EPL alternatives. Source-availability and notice obligations still apply.
MPL compatibility does not remove another component's licence requirements.

Use `net.sourceforge.plantuml:plantuml-mit`. The default PlantUML artifact is GPL-licensed.
The MIT distribution supplies UML/Smetana rendering and excludes ditaa.
Review feature support and distribution obligations before changing variants.

References: [MPL FAQ](https://www.mozilla.org/en-US/MPL/2.0/FAQ/),
[PlantUML distributions](https://plantuml.com/download),
[MIT artifact](https://central.sonatype.com/artifact/net.sourceforge.plantuml/plantuml-mit/1.2026.6).

## Packaged notices

Both jars include:

- Project licence and this document under `META-INF/glow-adoc/`.
- Reference licence texts and PlantUML attribution under `META-INF/third-party/`.
- Per-artifact copies of licence, notice, copying, copyright and author files, including nested gem notices.

The collector keeps identical filenames from different dependencies separate.
It does not determine the licence of every native or generated payload.
Before publication, review notices against the exact upstream versions, add missing notices and
provide corresponding copyleft sources where required.

Merman is separately installed and is not bundled. Its dependencies and notices belong to its distribution.
Build/test dependencies are not included merely because Gradle uses them.
The Gradle wrapper retains its upstream Apache header. Upstream notice texts in `licenses/` are preserved.

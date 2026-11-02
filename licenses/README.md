<!-- SPDX-License-Identifier: MPL-2.0 -->

# Licence references

This directory holds manually maintained licence texts and distribution notices copied into both
published jars under `META-INF/third-party/`. The original references were added in the initial implementation,
commit `62bc859`. They describe upstream terms; the project's own licence is [MPL 2.0](../LICENSE).

| File                                               | Reference and purpose                                                                                         |
| -------------------------------------------------- | ------------------------------------------------------------------------------------------------------------- |
| [Apache-2.0.txt](Apache-2.0.txt)                   | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt), used by several runtime dependencies.          |
| [EPL-1.0.txt](EPL-1.0.txt)                         | [EPL 1.0](https://www.eclipse.org/legal/epl-v10.html), declared by Dirgra.                                    |
| [EPL-2.0.txt](EPL-2.0.txt)                         | [EPL 2.0](https://www.eclipse.org/legal/epl-2.0/), selected for JRuby and JNR POSIX, and used by JRuby Prism. |
| [plantuml-mit.txt](plantuml-mit.txt)               | The pinned PlantUML MIT 1.2026.9 distribution's attribution and licence output.                               |
| [antlr4-BSD-3-Clause.txt](antlr4-BSD-3-Clause.txt) | ANTLR 4.13.2's original notice, retained for the parser bundled in TOMLJ 2.2.0.                               |
| [prism-MIT.txt](prism-MIT.txt)                     | Prism's original MIT copyright and licence notice.                                                            |

The generic texts were imported on 2026-10-29 from
[SPDX's licence-list data](https://github.com/spdx/license-list-data/tree/main/text):
`Apache-2.0.txt`, `EPL-1.0.txt`, `EPL-2.0.txt`, `GPL-3.0-only.txt` and `LGPL-3.0-only.txt`.
The import used the upstream `main` branch; the checked-in copies retain that imported content.
The standalone GPL/LGPL copies were later removed after selecting EPL-2.0 for JRuby/JNR POSIX and
Apache-2.0 for JFFI. Original dependency notices retain the upstream alternative terms.
Preserve upstream text when updating these files.

`plantuml-mit.txt` was captured from the MIT jar's licence output and verified byte-for-byte:

```sh
java -Djava.awt.headless=true -jar /path/to/plantuml-mit-1.2026.9.jar -license
```

These references are separate from the notices extracted automatically from dependency jars.
`antlr4-BSD-3-Clause.txt` preserves ANTLR 4.13.2's
[original notice](https://github.com/antlr/antlr4/blob/4.13.2/LICENSE.txt); TOMLJ's jar omits it.
`prism-MIT.txt` preserves Prism's original notice from upstream revision
`5092bf7f65635bfe6e0a042f98b76fc1010f1378`; the Prism Maven jars omit it.
[`CollectDependencyNotices`](../build-logic/conventions/src/main/kotlin/io/github/bric3/glowadoc/buildlogic/CollectDependencyNotices.kt)
creates those per-artifact copies in `glowadoc-cli-jvm/build/dependency-notices/`.
See the [direct and transitive dependency inventory](../THIRD-PARTY.md) for their owners and roles.

The [dependency inventory](../THIRD-PARTY.md#selected-licence-alternatives) records the selected
alternatives. Each component's upstream distribution supplies its applicable terms.

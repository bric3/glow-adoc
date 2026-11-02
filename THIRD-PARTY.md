<!-- SPDX-License-Identifier: MPL-2.0 -->

# Third-party software

Project source uses [MPL 2.0](LICENSE). External components retain their upstream terms.
Versions below describe the distributed runtime; lockfiles and `gradle/verification-metadata.xml`
pin the complete graph and downloaded bytes.

> Direct dependencies are external libraries declared by application modules. Transitive dependencies
> are brought in by those libraries. Both groups contribute code and resources to the shaded jars.

## Direct runtime dependencies

| Dependency                 | Declared by                  | Purpose and published terms                                                 |
| -------------------------- | ---------------------------- | --------------------------------------------------------------------------- |
| AsciidoctorJ 3.0.1         | `glowadoc-asciidoctor`       | AsciiDoc conversion; Apache-2.0.                                            |
| jsoup 1.23.2               | `glowadoc-asciidoctor`       | HTML passthrough parsing; MIT.                                              |
| TamboUI core 0.6.0         | `glowadoc-render`            | Terminal text widths and layout types; MIT.                                 |
| TamboUI TUI 0.6.0          | `glowadoc-pager`             | Pager event loop and rendering; MIT.                                        |
| TamboUI Aesh backend 0.6.0 | `glowadoc-terminal`          | Terminal connection; MIT.                                                   |
| Aesh 3.18.6                | `glowadoc-cli`               | Command parsing; Apache-2.0.                                                |
| TOMLJ 1.1.1                | `glowadoc-cli`               | Configuration parsing; Apache-2.0.                                          |
| Lumis4J 0.0.10             | `glowadoc-highlight-lumis4j` | Syntax highlighting; Apache-2.0, with upstream terms for bundled resources. |
| PlantUML MIT 1.2026.6      | `glowadoc-diagrams`          | Embedded diagrams; MIT, with bundled-resource notices.                      |

Use `net.sourceforge.plantuml:plantuml-mit`. Its feature set excludes ditaa.
See [PlantUML's distributions](https://plantuml.com/download) and the
[stored distribution notice](licenses/plantuml-mit.txt).

## Transitive runtime dependencies

| Component                                       | Introduced by        | Purpose and published terms                                                                               |
| ----------------------------------------------- | -------------------- | --------------------------------------------------------------------------------------------------------- |
| AsciidoctorJ API                                | AsciidoctorJ         | Converter interfaces; Apache-2.0.                                                                         |
| JRuby runtime 9.4.14.0                          | AsciidoctorJ         | Run the Ruby Asciidoctor engine; EPL-2.0 selected from the upstream alternatives.                         |
| Ruby standard library and bundled gems          | JRuby                | Ruby libraries; Ruby/BSD and component-specific terms in the packaged notices.                            |
| JZlib 1.1.5                                     | JRuby base           | Ruby zlib/gzip implementation; BSD.                                                                       |
| CRaC API 1.5.0                                  | JRuby base           | JRuby checkpoint/restore integration; BSD-2-Clause.                                                       |
| Joda-Time 2.12.7                                | JRuby base           | Ruby `Time`, `Date` and time-zone handling; Apache-2.0.                                                   |
| JiteScript 0.4.1                                | JRuby base           | Generate JVM bytecode for Ruby execution, including dynamic scopes; Apache-2.0.                           |
| ASM modules                                     | JRuby and JNR        | JVM bytecode support; BSD-3-Clause.                                                                       |
| JNR POSIX 3.1.20                                | JRuby                | POSIX integration; EPL-2.0 selected from the upstream alternatives.                                       |
| JNR FFI, constants, sockets and network helpers | JRuby/JNR            | Native runtime integration; Apache-2.0.                                                                   |
| JFFI 1.3.13                                     | JNR FFI              | Native calls; Apache-2.0 selected from the upstream alternatives, with component-specific native notices. |
| JNR x86 assembler                               | JNR FFI              | Native-call stubs; MIT.                                                                                   |
| JCodings and Joni                               | JRuby                | Encodings and regular expressions; MIT.                                                                   |
| Dirgra                                          | JRuby                | Graph analysis; EPL-1.0.                                                                                  |
| Headius backport9, invokebinder and options     | JRuby                | JVM compatibility, method binding and options; Apache-2.0.                                                |
| Chicory runtime, Wasm, WASI and logging         | Lumis4J              | Execute the bundled highlighter; Apache-2.0.                                                              |
| TamboUI widgets and annotations                 | TamboUI TUI          | UI support; MIT.                                                                                          |
| Aesh terminal and readline modules              | Aesh/TamboUI backend | Terminal input and capabilities; Apache-2.0.                                                              |
| ANTLR runtime and Checker Qual                  | TOMLJ                | Parser runtime and annotations; BSD-3-Clause and MIT respectively.                                        |

### Selected licence alternatives

glow-adoc distributes JRuby and JNR POSIX under EPL-2.0. Their pinned
[JRuby notice](https://github.com/jruby/jruby/blob/9.4.14.0/COPYING) and
[JNR POSIX notice](https://github.com/jnr/jnr-posix/blob/jnr-posix-3.1.20/LICENSE.txt)
explicitly permit this choice. These links also provide access to their corresponding source trees.
Dirgra remains under its own EPL-1.0 terms.

JFFI is distributed under the Apache-2.0 option in its
[pinned licence](https://github.com/jnr/jffi/blob/jffi-1.3.13/LICENSE).
JRuby's notice also identifies JFFI and JNR FFI as redistributed under Apache-2.0.

These choices are recorded here and packaged with the application. They require no Gradle flag,
replacement artifact or rebuild of the dependencies. Preserve original upstream notices, including
the alternative terms they contain. Ruby libraries, bundled gems and native components retain their
own terms; selecting EPL for JRuby does not relicense them or glow-adoc's MPL-2.0 sources.

### Why the JRuby libraries are present

The dependency chain is `glowadoc-asciidoctor -> AsciidoctorJ -> JRuby -> jruby-base`.
[JRuby's pinned POM](https://github.com/jruby/jruby/blob/9.4.14.0/core/pom.xml) declares JZlib,
CRaC, Joda-Time and JiteScript. Our Java sources do not need to import them for JRuby to use them:
`RubyTime` references Joda-Time, `DynamicScopeGenerator` references JiteScript, zlib extensions
reference JZlib, and `CheckpointMain` references CRaC. These references are visible with `jdeps`.

glow-adoc does not enable CRaC checkpointing. The CRaC API is present because JRuby declares it.
Excluding these libraries requires a separate JRuby compatibility evaluation; absence of direct
application imports does not establish that an exclusion is safe.

Inspect the installed graph with:

```sh
./gradlew :glowadoc-cli-jvm:dependencies --configuration runtimeClasspath
./gradlew :glowadoc-cli-jvm:dependencyInsight --configuration runtimeClasspath --dependency jzlib
```

## Licence files and packaged notices

[`licenses/`](licenses/README.md) contains checked-in reference texts and the PlantUML distribution
notice. It was introduced in the initial implementation and is maintained manually. Gradle copies it
into `META-INF/third-party/`; it does not generate or choose these licences.

`CollectDependencyNotices` separately extracts licence, notice, copying, copyright and author files
from resolved runtime jars, including nested JRuby gems. These generated copies live under
`glowadoc-cli-jvm/build/dependency-notices/` and are packaged under artifact-specific directories in
`META-INF/third-party/`, so identical filenames from different dependencies survive shading.
The project licence and this inventory are packaged under `META-INF/glow-adoc/`.

Bundled gems, native libraries, grammars, themes and other resources retain their own terms.
Reference texts supplement their notices. MPL does not replace dependency licences; see the
[MPL FAQ](https://www.mozilla.org/en-US/MPL/2.0/FAQ/).

## Build, test and optional tools

Gradle, Kotlin, Shadow, Semver, Spotless, Prince of Space, Yumi Licenser, ktlint, JUnit, TableTest
and the Aesh annotation processor are build/test tools and are not included in the runtime merely
because the build uses them. Version pins and checksums cover their resolved dependencies too.

Merman is installed separately and is not bundled. JMH, Reggie and JSVG are used only by explicitly
invoked JBang tools; those tool dependencies are not application runtime dependencies.
The Gradle wrapper retains its upstream Apache header.

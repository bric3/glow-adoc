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
| JSVG 2.2.0                 | `glowadoc-images`            | SVG parsing and rasterisation; MIT.                                         |
| TamboUI core 0.6.0         | `glowadoc-render`            | Terminal text widths and layout types; MIT.                                 |
| TamboUI TUI 0.6.0          | `glowadoc-pager`             | Pager event loop and rendering; MIT.                                        |
| TamboUI Aesh backend 0.6.0 | `glowadoc-terminal`          | Terminal connection; MIT.                                                   |
| Aesh 3.18.6                | `glowadoc-cli-common`        | Command parsing; Apache-2.0.                                                |
| TOMLJ 2.2.0                | `glowadoc-cli-common`        | Configuration parsing; Apache-2.0.                                          |
| Lumis4J 0.0.10             | `glowadoc-highlight-lumis4j` | Syntax highlighting; Apache-2.0, with upstream terms for bundled resources. |
| PlantUML MIT 1.2026.9      | `glowadoc-diagrams-plantuml` | Embedded diagrams; MIT, with bundled-resource notices.                      |
| Traceur Ditaa 0.1.0        | `glowadoc-diagrams-ditaa`    | Embedded ditaa diagrams; MPL-2.0.                                           |

Use `net.sourceforge.plantuml:plantuml-mit`. Its feature set excludes ditaa.
See [PlantUML's distributions](https://plantuml.com/download) and the
[stored distribution notice](licenses/plantuml-mit.txt).

[Traceur Ditaa](https://github.com/bric3/traceur-ditaa/tree/v0.1.0) renders `[ditaa]` blocks separately.
Its [published POM](https://repo.maven.apache.org/maven2/io/github/bric3/traceur/traceur-ditaa/0.1.0/traceur-ditaa-0.1.0.pom)
declares MPL-2.0 and no runtime dependencies. Its licence is collected from the jar with the other packaged notices.

## Transitive and bundled runtime components

| Component                                       | Introduced by        | Purpose and published terms                                                                               |
| ----------------------------------------------- | -------------------- | --------------------------------------------------------------------------------------------------------- |
| AsciidoctorJ API                                | AsciidoctorJ         | Converter interfaces; Apache-2.0.                                                                         |
| JRuby runtime 10.1.2.0                          | AsciidoctorJ         | Run the Ruby Asciidoctor engine; EPL-2.0 selected from the upstream alternatives.                         |
| JRuby Prism 2.0.4                               | JRuby                | Integrate the Prism parser; EPL-2.0.                                                                      |
| Prism parser API and Wasm 0.0.4                 | JRuby Prism          | Parse Ruby source; MIT. Original notice in `licenses/prism-MIT.txt`.                                      |
| Redline runtime and native bridge 0.0.5         | Prism Wasm           | Execute the parser through Chicory and JFFI; Apache-2.0.                                                  |
| FastDoubleParser 2.0.1                          | JRuby base           | Parse floating-point numbers; MIT, with component notices.                                                |
| Ruby standard library and bundled gems          | JRuby                | Ruby libraries; Ruby/BSD and component-specific terms in the packaged notices.                            |
| JZlib 1.1.5                                     | JRuby base           | Ruby zlib/gzip implementation; BSD.                                                                       |
| CRaC API 1.5.0                                  | JRuby base           | JRuby checkpoint/restore integration; BSD-2-Clause.                                                       |
| Joda-Time 2.14.0                                | JRuby base           | Ruby `Time`, `Date` and time-zone handling; Apache-2.0.                                                   |
| JiteScript 0.4.1                                | JRuby base           | Generate JVM bytecode for Ruby execution, including dynamic scopes; Apache-2.0.                           |
| ASM modules                                     | JRuby and JNR        | JVM bytecode support; BSD-3-Clause.                                                                       |
| JNR POSIX 3.2.4                                 | JRuby                | POSIX integration; EPL-2.0 selected from the upstream alternatives.                                       |
| JNR FFI, constants, sockets and network helpers | JRuby/JNR            | Native runtime integration; Apache-2.0.                                                                   |
| JFFI 1.4.3                                      | JNR FFI              | Native calls; Apache-2.0 selected from the upstream alternatives, with component-specific native notices. |
| JNR x86 assembler                               | JNR FFI              | Native-call stubs; MIT.                                                                                   |
| JCodings and Joni                               | JRuby                | Encodings and regular expressions; MIT.                                                                   |
| Dirgra                                          | JRuby                | Graph analysis; EPL-1.0.                                                                                  |
| Headius backport9, invokebinder and options     | JRuby                | JVM compatibility, method binding and options; Apache-2.0.                                                |
| Chicory runtime, Wasm, WASI and logging         | Lumis4J and Prism    | Execute the bundled highlighter and Ruby parser; Apache-2.0.                                              |
| TamboUI widgets and annotations                 | TamboUI TUI          | UI support; MIT.                                                                                          |
| Aesh terminal and readline modules              | Aesh/TamboUI backend | Terminal input and capabilities; Apache-2.0.                                                              |
| ANTLR runtime 4.13.2 (bundled)                  | TOMLJ                | Relocated parser runtime; BSD-3-Clause. Original notice in `licenses/antlr4-BSD-3-Clause.txt`.            |

`glowadoc-asciidoctor` constrains `jruby`, `jruby-base` and `jruby-stdlib` to the same version.
These remain transitive dependencies of AsciidoctorJ. JRuby Prism's
[published POM](https://repo.maven.apache.org/maven2/org/jruby/jruby-prism/2.0.4/jruby-prism-2.0.4.pom)
specifies EPL-2.0. Prism's [original MIT notice](https://github.com/ruby/prism/blob/5092bf7f65635bfe6e0a042f98b76fc1010f1378/LICENSE.md)
is retained separately because its Maven jars omit the notice. Redline's
[release licence](https://github.com/roastedroot/chicory-redline/blob/0.0.5/LICENSE) specifies Apache-2.0.

TOMLJ 2.2.0 bundles ANTLR under `org.tomlj.internal.antlr` and no longer depends on Checker Qual.
Its jar omits ANTLR's licence file, so the parser's
[upstream notice](https://github.com/antlr/antlr4/blob/4.13.2/LICENSE.txt) is retained separately.

### Selected licence alternatives

glow-adoc distributes JRuby and JNR POSIX under EPL-2.0. Their pinned
[JRuby notice](https://github.com/jruby/jruby/blob/10.1.2.0/COPYING) and
[JNR POSIX notice](https://github.com/jnr/jnr-posix/blob/3.2.4/LICENSE.txt)
explicitly permit this choice. These links also provide access to their corresponding source trees.
Dirgra remains under its own EPL-1.0 terms.

JFFI is distributed under the Apache-2.0 option in its
[published licence alternatives](https://repo.maven.apache.org/maven2/com/github/jnr/jffi/1.4.3/jffi-1.4.3.pom).
JRuby's notice also identifies JFFI and JNR FFI as redistributed under Apache-2.0.

These choices are recorded here and packaged with the application. They require no Gradle flag,
replacement artifact or rebuild of the dependencies. Preserve original upstream notices, including
the alternative terms they contain. Ruby libraries, bundled gems and native components retain their
own terms; selecting EPL for JRuby does not relicense them or glow-adoc's MPL-2.0 sources.

### Why the JRuby libraries are present

The dependency chain is `glowadoc-asciidoctor -> AsciidoctorJ -> JRuby -> jruby-base`.
[JRuby's published POM](https://repo.maven.apache.org/maven2/org/jruby/jruby-base/10.1.2.0/jruby-base-10.1.2.0.pom) declares JZlib,
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

Merman is installed separately and is not bundled. JMH and Reggie are used only by explicitly
invoked JBang tools; those tool dependencies are not application runtime dependencies.
The Gradle wrapper retains its upstream Apache header.

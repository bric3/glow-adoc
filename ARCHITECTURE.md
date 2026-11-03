<!-- SPDX-License-Identifier: MPL-2.0 -->

# Architecture

The CLI loads configuration and source, AsciidoctorJ produces a document model, layout produces
terminal rows, and direct output or the pager displays them.

## Ownership

| Module                     | Responsibility                                                           |
| -------------------------- | ------------------------------------------------------------------------ |
| glowadoc-document          | Immutable blocks, spans, styles, appearance and safe text/link handling  |
| glowadoc-cache             | Private bounded entries, integrity checks and packaged artifact identity |
| glowadoc-asciidoctor       | AsciidoctorJ, includes, shortcodes and conversion                        |
| glowadoc-highlight-common  | Highlighter contract and deferred, cached highlighting                   |
| glowadoc-highlight-lumis4j | Lumis4J, ANSI style decoding and persisted highlight spans               |
| glowadoc-render            | Wrapping, tables, decoration and stable block positions                  |
| glowadoc-terminal          | Aesh connection, replies, colours, ANSI and Kitty graphics               |
| glowadoc-images            | Bounded SVG loading and rasterisation through JSVG                       |
| glowadoc-pager             | TamboUI events, viewport, folding, search and placements                 |
| glowadoc-diagrams-common   | Diagram contract, caching, background work and local image resolution    |
| glowadoc-diagrams-ditaa    | Embedded MPL-2.0 Traceur Ditaa                                           |
| glowadoc-diagrams-plantuml | Embedded MIT PlantUML                                                    |
| glowadoc-diagrams-merman   | Mermaid diagrams through bounded Merman processes                        |
| glowadoc-cli-common        | Shared arguments, configuration and assembly                             |
| glowadoc-cli-jvm           | Bootstrap, jars, launcher, install and AOT                               |
| glowadoc-cli-native        | Experimental complete-application Native Image                           |

The model has no parser, terminal or UI dependency. Concrete implementation types stay inside adapters.

## Conversion and layout

The converter emits private XML to preserve inline boundaries through Asciidoctor substitutions.
jsoup normalises supported HTML; XML parsing disables external entities. Scripts/styles are discarded.
Titles, table geometry, callouts and disclosure metadata survive conversion.

Layout assigns stable positions before folding. Text wraps by terminal cells; code scrolls horizontally.
Table ownership determines span-aware separators. Direct output expands disclosures.
The pager retains folding and viewport state during reflow.

Packaged file reads retain normalised converter XML in a private disk cache. Source, attributes,
shortcode policy, application bytes and runtime context validate each entry. Includes and temporal
substitutions bypass caching. A hit uses the secure XML parser and leaves JRuby uninitialised.
The CLI selects cache directories. `glowadoc-asciidoctor` owns conversion keys and XML;
`glowadoc-cache` owns private files, checksums and the shared packaged artifact hash.

The first pager frame uses source text. A serial background worker highlights requested blocks,
caches results by source/language/appearance and requests reflow. Closing cancels queued work;
an active task releases its highlighter when it finishes. `glowadoc-highlight-lumis4j` also persists validated
spans by source/language/appearance and artifact identity. A disk hit leaves the Wasm engine uninitialised.
Literal code splits at line endings; prose retains grapheme-aware wrapping.
Detected YAML front matter uses literal rows and a horizontal separator. A bounded safe YAML parser
reads its string title; the reader assigns it to the Asciidoctor document when no explicit document title
exists. CLI and TOML settings can disable this heading, and the conversion cache key includes that choice.
The converter emits explicit document titles and retains section headings in their source positions.
Layout adds external-link suffixes before wrapping, retaining link targets on the glyphs.
Rendered rows resolve mouse columns
to safe external targets, including horizontally scrolled content.

## Terminal lifecycle

One Aesh connection carries keys, mouse reports and replies through TamboUI.
Reply filtering prevents protocol responses from becoming commands.
Colour detection uses OSC queries, theme notifications, bounded polling and manual refresh.
The pager highlights hovered links and activates them on release, cancelling drags.
The terminal adapter opens local links through a bounded system-opener process. SSH sessions retain
native OSC 8 activation in the client terminal. Closing cancels the opener without terminating the browser.

Image metadata reserves rows. Visible assets upload once per session; stable placement IDs update
position and crop without retransmitting pixels. PNG bytes stream directly; other formats convert once.
Placements specify columns; the terminal derives height from the image's aspect ratio. Scroll crops
use the reported cell proportions, including partial image rows. Cell-size replies trigger layout again.
SVG images and diagram backends share a raster density based on cell height against a 16 px reference
text line. Their cache keys include density, so cell-size reports request a new raster at the required
resolution. SVG source downloads are reused. Ditaa, PlantUML and Merman apply density while rendering.
Scaling stays within the SVG dimension and pixel limits.
Off-screen placements are removed while image data remains available.
Least-recently-used off-screen images are evicted above the 128 MiB residency target;
the visible working set remains resident. Exit deletes all owned images.

Image-only paragraphs retain their images and links as one asset group. The pager places images
beside each other with a one-cell gap and wraps at the reading width. Missing members retain short,
linked alt labels. Block images remain separate.
After the first text frame, `glowadoc-images` loads visible local SVG images and HTTPS badges from
Shields and GitHub Actions on a serial background worker. Requests have a three-second deadline,
a 256 KiB source limit and no redirects. Other remote hosts retain linked alt text.
SVG parsing disables DTDs and external entities; JSVG denies external resources and bounds document
complexity and raster dimensions. Session-local PNGs reuse the existing Kitty pipeline and are deleted
on exit. Downloads, SVG parsing and rasterisation stay outside the first-text path.

After the first text frame, the pager requests visible diagrams on a serial background worker.
Completed images replace source and preserve the viewport's block and relative row. Off-screen
diagrams wait until displayed. Theme changes retain the previous image until its replacement is ready.
Background diagnostics pass through the event loop; closing cancels work and terminates renderer processes.

The CLI supplies PlantUML, Traceur Ditaa and Merman implementations of `DiagramBackend` to the common renderer.
The pager depends only on `glowadoc-diagrams-common`; renderer-specific dependencies stay in their
adapter modules. The common renderer owns caching, the 100 KB source limit and the 15-second render
deadline. Adapters bound image output to 10 MB; Merman also bounds diagnostic output to 64 KB.
Additional engines such as svgbob can use the same contract. Adding one also requires recognising
its AsciiDoc block style and registering the adapter in the CLI. No svgbob renderer is bundled.

## Resources and distribution

Project conventions and packaging task types live in the `build-logic/conventions` included build.
Its `glowadoc.java` plugin configures application modules; `glowadoc.quality` runs from the root
and checks application, build-logic and tool sources. The separate `build-logic/settings-conventions`
build provides `glowadoc.versioning` and owns the Semver dependency and release-policy tests.
`checkAll` runs tests in both included builds. Root quality checks cover both source trees,
so neither included build needs to apply its own convention.

Includes stay within the document directory. Images also use configured roots and absolute directories
explicitly supplied through CLI attributes. Resolved symlinks must remain within a root.
Project roots stay within the configuration directory's tree; discovery cannot select an executable.

PlantUML uses its sandbox profile and rejects include/import/theme directives.
Merman has time/output limits and cancellation. These controls are not an OS sandbox.
Unsupported capabilities and failed renderers retain readable text.

The Gradle daemon uses Java 27; application bytecode targets 25 and the bootstrap targets 8.
Shadow preserves service registrations, JRuby resources and multi-release metadata.
The downloadable ZIP contains a versioned root directory with `bin/glow-adoc`, the plain Shadow jar
at `lib/glow-adoc.jar`, and licence notices. Its POSIX launcher selects Java and resolves the jar
relative to the extracted directory, including when the launcher is symlinked into PATH.
The local executable jar prepends the same launcher to the Shadow jar.
Both launcher templates live in `glowadoc-cli-jvm/src/main/scripts` and are declared file inputs
to the packaging and installation tasks.
Local installation writes a small script to `~/.local/bin/glow-adoc` and the jar to
`$XDG_DATA_HOME/glow-adoc/glow-adoc.jar`, defaulting to `~/.local/share/glow-adoc/glow-adoc.jar`.
The script reads the jar through zsh's sh emulation when available, falling back to `/bin/sh`.
This avoids the large-file startup cost observed with `/bin/sh` on macOS. The portable executable
jar remains available; `java -jar` also opens it directly.
The launcher requests Java 27 from mise, or delegates to the next available manager's selected JDK,
then falls back to `java` on PATH.
It performs no version probes or installed-version searches; the Java 8-compatible bootstrap checks
the Java 27 minimum. A generation hash of the launcher template and Shadow jar filters impossible
AOT lookups. Possible hits still require the full jar hash and runtime identity. When the launcher hashes the jar
for AOT lookup, it supplies that digest and the artifact path to Java. The document cache reuses the
digest only for the matching runtime jar and a valid SHA-256 value; ordinary Java invocation hashes the jar itself.
Artifact ownership comes from live class-resource jar URLs. Archived protection domains can retain
training-time paths, so their CodeSource URLs cannot identify a relocated runtime jar.

AOT training uses a bundled, project-owned corpus. `install` trains a staged `glow-adoc.jar` before
installing it; `installWithoutAot` skips training. The CLI extracts the declared corpus includes and
assets into a private temporary directory and removes it after rendering. Training and installation
preserve the jar basename and timestamp so HotSpot
can relocate the cache. Training and possible hits enter the selected manager's environment before
resolving Java and reading its release metadata. An available `JAVA_HOME` inside that environment
resolves manager shims and gives aliases of the same JDK a stable cache identity. A different JDK
selected directly on PATH takes precedence over an inherited `JAVA_HOME`.
Unmanaged launches keep Java on PATH and skip the extra shell invocation.
Cache identity includes jar bytes and filename, Java path and release metadata, OS/architecture
and JVM option environment. The installation directory
is excluded from identity; full executable bytes still validate candidates. Missing metadata disables
automatic AOT and prevents training. A missing cache selects ordinary startup.
Source uses MPL 2.0; dependencies retain their own licences and notices.

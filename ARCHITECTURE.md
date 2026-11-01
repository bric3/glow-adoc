<!-- SPDX-License-Identifier: MPL-2.0 -->

# Architecture

The CLI loads configuration and source, AsciidoctorJ produces a document model, layout produces
terminal rows, and direct output or the pager displays them.

## Ownership

| Module            | Responsibility                                                           |
| ----------------- | ------------------------------------------------------------------------ |
| document          | Immutable blocks, spans, styles, appearance and safe text/link handling  |
| cache             | Private bounded entries, integrity checks and packaged artifact identity |
| asciidoctor       | AsciidoctorJ, includes, shortcodes and conversion                        |
| highlight         | Highlighter contract and deferred, cached highlighting                   |
| highlight-lumis4j | Lumis4J, ANSI style decoding and persisted highlight spans               |
| render            | Wrapping, tables, decoration and stable block positions                  |
| terminal          | Aesh connection, replies, colours, ANSI and Kitty graphics               |
| pager             | TamboUI events, viewport, folding, search and placements                 |
| diagrams          | Embedded MIT PlantUML and bounded Merman processes                       |
| cli               | Arguments, configuration and assembly                                    |
| cli-jvm           | Bootstrap, jars, launcher, install and AOT                               |
| cli-native        | Experimental complete-application Native Image                           |

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
The CLI selects cache directories. `asciidoctor` owns conversion keys and XML;
`cache` owns private files, checksums and the shared packaged artifact hash.

The first pager frame uses source text. A serial background worker highlights requested blocks,
caches results by source/language/appearance and requests reflow. Closing cancels queued work;
an active task releases its highlighter when it finishes. `highlight-lumis4j` also persists validated
spans by source/language/appearance and artifact identity. A disk hit leaves the Wasm engine uninitialised.
Literal code splits at line endings; prose retains grapheme-aware wrapping.

## Terminal lifecycle

One Aesh connection carries keys, mouse reports and replies through TamboUI.
Reply filtering prevents protocol responses from becoming commands.
Colour detection uses OSC queries, theme notifications, bounded polling and manual refresh.

Image metadata reserves rows. Visible assets upload once per session; stable placement IDs update
position and crop without retransmitting pixels. PNG bytes stream directly; other formats convert once.
Off-screen placements are removed while image data remains available.
Least-recently-used off-screen images are evicted above the 128 MiB residency target;
the visible working set remains resident. Exit deletes all owned images.

After the first text frame, the pager requests visible diagrams on a serial background worker.
Completed images replace source and preserve the viewport's block and relative row. Off-screen
diagrams wait until displayed. Theme changes retain the previous image until its replacement is ready.
Background diagnostics pass through the event loop; closing cancels work and terminates renderer processes.

## Resources and distribution

Includes stay within the document directory. Images also use configured roots and absolute directories
explicitly supplied through CLI attributes. Resolved symlinks must remain within a root.
Project roots stay within the configuration directory's tree; discovery cannot select an executable.

PlantUML uses its sandbox profile and rejects include/import/theme directives.
Merman has time/output limits and cancellation. These controls are not an OS sandbox.
Unsupported capabilities and failed renderers retain readable text.

The Gradle daemon uses Java 27; application bytecode targets 25 and the bootstrap targets 8.
Shadow preserves service registrations, JRuby resources and multi-release metadata.
The executable jar prepends a POSIX launcher with Java-manager selection.
The launcher delegates to the first available manager, then falls back to `java` on PATH.
It performs no version probes or installed-version searches; the Java 8-compatible bootstrap checks
the Java 27 minimum. A generation hash of the launcher template and Shadow jar filters impossible
AOT lookups. Possible hits still require the full executable hash and runtime identity. When the launcher hashes the jar
for AOT lookup, it supplies that digest and the artifact path to Java. The document cache reuses the
digest only for the matching runtime jar and a valid SHA-256 value; ordinary Java invocation hashes the jar itself.
Artifact ownership comes from live class-resource jar URLs. Archived protection domains can retain
training-time paths, so their CodeSource URLs cannot identify a relocated runtime jar.

AOT training uses a bundled, project-owned corpus. `install` trains a staged executable before
installing it; `installWithoutAot` skips training. The CLI extracts the declared corpus includes and
assets into a private temporary directory and removes it after rendering. Training and installation
preserve the executable basename and timestamp so HotSpot
can relocate the cache. Training and possible hits enter the selected manager's environment before
resolving Java and reading its release metadata. Cache identity includes executable bytes and filename,
Java path and release metadata, OS/architecture and JVM option environment. The installation directory
is excluded from identity; full executable bytes still validate candidates. Missing metadata disables
automatic AOT and prevents training. A missing cache selects ordinary startup.
Source uses MPL 2.0; dependencies retain their own licences and notices.

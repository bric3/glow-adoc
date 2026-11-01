<!-- SPDX-License-Identifier: MPL-2.0 -->

# Architecture

The CLI loads configuration and source, AsciidoctorJ produces a document model, layout produces
terminal rows, and direct output or the pager displays them.

## Ownership

| Module            | Responsibility                                                          |
| ----------------- | ----------------------------------------------------------------------- |
| document          | Immutable blocks, spans, styles, appearance and safe text/link handling |
| asciidoctor       | AsciidoctorJ, includes, shortcodes and conversion                       |
| highlight         | Highlighter contract and deferred, cached highlighting                  |
| highlight-lumis4j | Lumis4J and ANSI style decoding                                         |
| render            | Wrapping, tables, decoration and stable block positions                 |
| terminal          | Aesh connection, replies, colours, ANSI and Kitty graphics              |
| pager             | TamboUI events, viewport, folding, search and placements                |
| diagrams          | Embedded MIT PlantUML and bounded Merman processes                      |
| cli               | Arguments, configuration and assembly                                   |
| cli-jvm           | Bootstrap, jars, launcher, install and AOT                              |
| cli-native        | Experimental complete-application Native Image                          |

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
The CLI selects the cache directory; `asciidoctor` owns identity, persistence and conversion.

The first pager frame uses source text. A serial background worker highlights requested blocks,
caches results by source/language/appearance and requests reflow. Closing cancels queued work;
an active task releases its highlighter when it finishes.

## Terminal lifecycle

One Aesh connection carries keys, mouse reports and replies through TamboUI.
Reply filtering prevents protocol responses from becoming commands.
Colour detection uses OSC queries, theme notifications, bounded polling and manual refresh.

Image metadata reserves rows. Visible assets upload once per session; stable placement IDs update
position and crop without retransmitting pixels. PNG bytes stream directly; other formats convert once.
Off-screen placements are removed while image data remains available.
Least-recently-used off-screen images are evicted above the 128 MiB residency target;
the visible working set remains resident. Exit deletes all owned images.

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

AOT training is explicit. Cache identity includes executable bytes and location, Java path and version output,
OS/architecture and JAVA_OPTS. A missing cache selects ordinary startup.
Source uses MPL 2.0; dependencies retain their own licences and notices.

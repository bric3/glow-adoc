<!-- SPDX-License-Identifier: MPL-2.0 -->

# glow-adoc

Requires Java 27+. Read AsciiDoc through an embedded pager or direct text output.

- Styled tables, quotes, admonitions, callouts and collapsible blocks.
- Live search, mouse controls and terminal appearance detection.
- Progressive syntax highlighting and reusable Kitty image placements.
- CLI attributes, project configuration and explicit image directories.
- Optional local AOT training through `glow-adoc --train-aot FILE`.
- Private conversion caching for unchanged files, with `--no-cache` to force conversion.
- JRuby native extensions disabled by default; Java handles document file access.

The first pager frame precedes syntax highlighting. Images retain uploaded pixels while scrolling;
only placement and crop commands change. The local 20-run PTY comparison reduced median first-frame
startup from 3.59 s to 1.50 s and scroll-frame p95 from 70-82 ms to 3-4 ms.
See [Performance](PERFORMANCE.adoc) for samples, runtime settings and measurement limits.

Conversion caching subsequently reduced repeated opens to 347 ms median. Empty-cache opens fell
from 1474 ms to 1299 ms. Both results use twenty fresh JVMs per mode without AOT.

Mermaid graphics require separately installed Merman. PlantUML uses the MIT distribution; ditaa is unavailable.
Native Image remains experimental. See the README for installation and the CLI reference for options.

<!-- SPDX-License-Identifier: MPL-2.0 -->

# glow-adoc

Requires Java 27+. Read AsciiDoc through an embedded pager or direct text output.

- Styled tables, quotes, admonitions, callouts and collapsible blocks.
- Live search, mouse controls and terminal appearance detection.
- Reading-position OSC progress, cached dark-mode image dimming and hover restoration.
- Numbered callout pills, distinct admonition colours and reflowed linked references.
- Progressive syntax highlighting and reusable Kitty image placements.
- CLI attributes, project configuration and explicit image directories.
- Local AOT training with a bundled, self-contained corpus through `glow-adoc --train-aot`, or a custom FILE.
- `./gradlew :cli-jvm:install` trains the bundled corpus before installation; `installWithoutAot` skips training.
- Private conversion and highlight caches, with `--no-cache` to bypass both.
- Visible diagrams render in the background after the first text frame.
- JRuby native extensions disabled by default; Java handles document file access.

The first pager frame precedes syntax highlighting. Images retain uploaded pixels while scrolling;
only placement and crop commands change. The local 20-run PTY comparison reduced median first-frame
startup from 3.59 s to 1.50 s and scroll-frame p95 from 70-82 ms to 3-4 ms.
See [Performance](PERFORMANCE.adoc) for samples, runtime settings and measurement limits.

Conversion caching subsequently reduced repeated opens to 347 ms median. Empty-cache opens fell
from 1474 ms to 1299 ms. Both results use twenty fresh JVMs per mode without AOT.

A 23-document corpus pass reduced the median of per-document highlighted-output medians from
2565 ms to 279 ms on repeated opens. The PlantUML showcase's repeat first frame fell from
1371 ms to 318 ms. Full ANSI output remained identical across the corpus.

Mermaid graphics require separately installed Merman. PlantUML uses the MIT distribution; ditaa is unavailable.
Native Image remains experimental. See the README for installation and the CLI reference for options.

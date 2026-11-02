// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.buildlogic

import dev.yumi.gradle.licenser.api.comment.HeaderComment

// Yumi's C-style handler reads block comments; preserve this project's one-line SPDX headers.
class SpdxLineComment(private val prefix: String) : HeaderComment {
    override fun readHeaderComment(source: String): HeaderComment.Result {
        val separator = extractLineSeparator(source)
        var start = 0
        if (source.startsWith("#!")) {
            val newline = source.indexOf('\n')
            start = if (newline < 0) source.length else newline + 1
        }
        var at = start
        while (at < source.length) {
            val end = source.indexOf('\n', at).let { if (it < 0) source.length else it }
            val line = source.substring(at, end).trimEnd('\r')
            if (line.startsWith("$prefix SPDX-License-Identifier:")) {
                return HeaderComment.Result(at, end, listOf(line.removePrefix(prefix).trim()), separator)
            }
            if (!line.startsWith(prefix)) break
            at = end + 1
        }
        return HeaderComment.Result(start, start, null, separator)
    }

    override fun writeHeaderComment(header: List<String>, separator: String): String =
        header.joinToString(separator) { "$prefix $it" } + separator.takeIf { header.isNotEmpty() }.orEmpty()
}

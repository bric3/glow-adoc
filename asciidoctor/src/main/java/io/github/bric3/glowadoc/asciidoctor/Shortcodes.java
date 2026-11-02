// SPDX-License-Identifier: MPL-2.0
package io.github.bric3.glowadoc.asciidoctor;

final class Shortcodes {
    private Shortcodes() {
    }

    static String hide(String source) {
        var out = new StringBuilder();
        String fence = "";
        boolean code = false;
        char quote = 0;
        int tag = -1;
        for (String line : source.split("(?<=\\n)", -1)) {
            String stripped = line.strip();
            if (tag < 0 && stripped.matches("-{4,}|\\.{4,}|\\+{4,}")) {
                if (fence.isEmpty()) {
                    fence = stripped;
                } else if (fence.equals(stripped)) {
                    fence = "";
                }
                out.append(line);
                continue;
            }
            if (!fence.isEmpty()) {
                out.append(line);
                continue;
            }
            if (tag < 0 && !line.isEmpty() && (line.charAt(0) == ' ' || line.charAt(0) == '\t')) {
                out.append(line);
                continue;
            }
            for (int i = 0; i < line.length(); ) {
                char ch = line.charAt(i);
                if (tag >= 0) {
                    if (quote != 0) {
                        if (ch == '\\' && i + 1 < line.length()) {
                            i += 2;
                            continue;
                        }
                        if (ch == quote) {
                            quote = 0;
                        }
                    } else if (ch == '"' || ch == '\'' || ch == '`') {
                        quote = ch;
                    } else if (line.startsWith(tag == '<' ? ">}}" : "%}}", i)) {
                        tag = -1;
                        i += 3;
                        continue;
                    }
                    i++;
                    continue;
                }
                if (ch == '`') {
                    code = !code;
                }
                if (!code
                        && (line.startsWith("{{<", i) || line.startsWith("{{%", i))
                        && !(i > 0 && line.charAt(i - 1) == '\\')
                        && !line.startsWith("/*", i + 3)) {
                    tag = line.charAt(i + 2);
                    i += 3;
                    continue;
                }
                out.append(ch);
                i++;
            }
        }
        if (tag >= 0) {
            return source;
        }
        return out.toString();
    }
}

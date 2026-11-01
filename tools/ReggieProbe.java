//JAVA 27
//DEPS com.datadoghq:reggie:0.5.0
// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.tools;

import com.datadoghq.reggie.Reggie;

import java.util.List;
import java.util.regex.Pattern;

/** Compare the grapheme operation used by TextLayout with the published Reggie artifact. */
public class ReggieProbe {
    public static void main(String[] args) {
        var jdk = Pattern.compile("\\X");
        var reggie = Reggie.compile("\\X");
        System.out.println("Reggie 0.5.0, Java " + Runtime.version());
        System.out.println("matcher=" + reggie.getClass().getName());
        System.out.println("input,jdk_find,reggie_find,jdk_matches,reggie_matches");
        for (String input : List.of("a", "X", "e\u0301", "👩‍💻", "日本", "\r\n")) {
            String escaped = input.replace("\r", "\\r").replace("\n", "\\n");
            System.out.printf(
                    "%s,%s,%s,%s,%s%n",
                    escaped,
                    jdk.matcher(input).find(),
                    reggie.find(input),
                    jdk.matcher(input).matches(),
                    reggie.matches(input));
        }
    }
}

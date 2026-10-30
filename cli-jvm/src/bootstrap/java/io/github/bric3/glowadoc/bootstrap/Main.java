// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.bootstrap;

import java.lang.reflect.InvocationTargetException;

/** Dependency-free bootstrap, following jardiff's compatibility launcher technique. */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Throwable {
        String version = System.getProperty("java.specification.version");
        int major = Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
        if (major < 27) {
            System.err.println(
                    "glow-adoc requires Java 27 or later; current Java: "
                            + System.getProperty("java.version"));
            System.exit(1);
        }
        try {
            Class.forName("io.github.bric3.glowadoc.cli.Main")
                    .getMethod("main", String[].class)
                    .invoke(null, (Object) args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}

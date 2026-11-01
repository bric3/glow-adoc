// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.cli;

import org.aesh.AeshRuntimeRunner;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        // Java handles document files; the embedded Ruby runtime needs no native extensions.
        System.setProperty(
                "jruby.native.enabled", System.getProperty("jruby.native.enabled", "false"));
        System.exit(
                AeshRuntimeRunner.builder()
                        .command(ReadCommand.class)
                        .args(args)
                        .execute()
                        .getResultValue());
    }
}

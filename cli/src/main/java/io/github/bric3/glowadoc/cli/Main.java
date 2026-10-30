// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.glowadoc.cli;

import org.aesh.AeshRuntimeRunner;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        System.exit(
                AeshRuntimeRunner.builder()
                        .command(ReadCommand.class)
                        .args(args)
                        .execute()
                        .getResultValue());
    }
}

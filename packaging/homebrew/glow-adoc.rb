# SPDX-License-Identifier: MPL-2.0

# Suggested formula. Replace the version and checksum after a release.
class GlowAdoc < Formula
  desc "Read AsciiDoc in the terminal"
  homepage "https://github.com/bric3/glow-adoc"
  url "https://github.com/bric3/glow-adoc/releases/download/vVERSION/glow-adoc-VERSION.zip"
  version "VERSION"
  sha256 "REPLACE_WITH_RELEASE_SHA256"
  license "MPL-2.0"

  depends_on "openjdk@27"
  # Mermaid diagrams additionally use an optional `brew install merman-cli`.

  def install
    libexec.install "bin", "lib"
    (bin/"glow-adoc").write <<~SH
      #!/bin/sh
      export JAVA_HOME="#{Language::Java.java_home("27")}"
      export PATH="$JAVA_HOME/bin:$PATH"
      exec "#{libexec}/bin/glow-adoc" "$@"
    SH
    (bin/"glow-adoc").chmod 0755
  end

  test do
    assert_match "glow-adoc", shell_output("#{bin}/glow-adoc --version")
    (testpath/"sample.adoc").write "= Hello\n\nA *bold* sentence.\n"
    assert_match "A bold sentence.", shell_output("#{bin}/glow-adoc --no-pager --color=never sample.adoc")
  end
end

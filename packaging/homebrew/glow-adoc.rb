# SPDX-License-Identifier: MPL-2.0

# Suggested formula. Replace the repository, version and checksum after a release.
class GlowAdoc < Formula
  desc "Read AsciiDoc in the terminal"
  homepage "https://github.com/OWNER/glow-adoc"
  license "MPL-2.0"
  url "https://github.com/OWNER/glow-adoc/releases/download/vVERSION/glow-adoc-VERSION-app.jar"
  version "VERSION"
  sha256 "REPLACE_WITH_RELEASE_SHA256"

  depends_on "openjdk@27"
  # Mermaid diagrams additionally use an optional `brew install merman-cli`.

  def install
    libexec.install "glow-adoc-#{version}-app.jar" => "glow-adoc.jar"
    (libexec/"glow-adoc.jar").chmod 0755
    (bin/"glow-adoc").write <<~SH
      #!/bin/sh
      export JAVA_HOME="#{Formula["openjdk@27"].opt_prefix}"
      if [ -x /bin/zsh ]; then
        exec /bin/zsh --emulate sh "#{libexec}/glow-adoc.jar" "$@"
      fi
      exec /bin/sh "#{libexec}/glow-adoc.jar" "$@"
    SH
    (bin/"glow-adoc").chmod 0755
  end

  test do
    assert_match "glow-adoc", shell_output("#{bin}/glow-adoc --version")
    (testpath/"sample.adoc").write "= Hello\n\nA *bold* sentence.\n"
    assert_match "A bold sentence.", shell_output("#{bin}/glow-adoc --no-pager --color=never sample.adoc")
  end
end

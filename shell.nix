{ pkgs ? import <nixpkgs> {}, ... }:

let
jdk = pkgs.jdk21;
gradle= pkgs.gradle.override { java = jdk; };
in
pkgs.mkShell
{
  packages = with pkgs; [jdk gradle nodejs_24];

  # Playwright downloads its own browsers and links them against a Debian-shaped system, which is
  # not what this one is. `playwright install --with-deps` therefore asks for sudo and fails. The
  # browsers come from nixpkgs instead, already linked against the right libraries, and the two
  # variables tell Playwright to use them and to stop trying to fetch its own.
  PLAYWRIGHT_BROWSERS_PATH = "${pkgs.playwright-driver.browsers}";
  PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = "1";
}

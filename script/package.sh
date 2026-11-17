#!/usr/bin/env bash
# Builds a native package with a bundled Java runtime (jlink + jpackage).
#
#   ./script/package.sh            # default type for this OS (dmg on macOS, deb on Linux)
#   ./script/package.sh app-image  # portable folder, no installer
#
# Output goes to dist/. Requires JDK 21+ (jpackage ships with the JDK).
set -euo pipefail

cd "$(dirname "$0")/.."

case "$(uname -s)" in
  Darwin) default_type="dmg" ;;
  Linux) default_type="deb" ;;
  *) default_type="app-image" ;;
esac
type="${1:-$default_type}"

./mvnw -B -ntp clean verify

# jpackage needs a purely numeric version: 0.1.0-SNAPSHOT -> 0.1.0
version="$(./mvnw -B -q -ntp help:evaluate -Dexpression=project.version -DforceStdout)"
app_version="${version%%-*}"

rm -rf dist
mkdir -p dist

args=(
  --type "$type"
  --name "Call Recording Downloader"
  --app-version "$app_version"
  --vendor "leoleiria"
  --description "Download call recordings, voicemails and transcriptions from Ringover. Not affiliated with Ringover."
  --copyright "Copyright (c) 2026 @leoleiria (https://github.com/leoleiria)"
  --module-path modules/app/target/modules
  --jlink-options "--strip-debug --no-man-pages --no-header-files"
  --module io.github.leoleiria.callrecording.app/io.github.leoleiria.callrecording.app.Launcher
  --dest dist
)
if [[ "$(uname -s)" == "Darwin" ]]; then
  args+=(--icon packaging/icons/app.icns)
else
  args+=(--icon packaging/icons/app.png)
fi
if [[ "$type" != "app-image" ]]; then
  args+=(--license-file LICENSE)
fi
if [[ "$type" == "deb" || "$type" == "rpm" ]]; then
  args+=(--linux-shortcut --linux-menu-group "Office")
fi

jpackage "${args[@]}"

echo "Package written to dist/"
ls -l dist

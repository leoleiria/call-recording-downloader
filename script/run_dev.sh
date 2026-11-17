#!/usr/bin/env bash
# Builds the project and starts the desktop app from source.
set -euo pipefail
cd "$(dirname "$0")/.."
./mvnw -B -ntp -q install -DskipTests
./mvnw -B -ntp -pl modules/app javafx:run

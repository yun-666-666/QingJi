#!/bin/sh
# ML Kit includes AAR resources, native libraries, models and manifest components.
# Gradle packages all of them; the previous bare javac/aapt path cannot build OCR.
set -eu
D=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
exec "${GRADLE_BIN:-gradle}" -p "$D" assembleRelease "$@"

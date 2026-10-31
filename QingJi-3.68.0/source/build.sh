#!/bin/sh
# Desktop Gradle entry. Current source has no bundled OCR or ML Kit dependency.
# Requires Android SDK 36 and compatible Gradle/JDK; signing is configured separately.
set -eu
D=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
exec "${GRADLE_BIN:-gradle}" -p "$D" assembleRelease "$@"

#!/bin/sh
# Build an unsigned APK. Private release-signing material is intentionally excluded.
set -eu
D=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
: "${ANDROID_JAR:?Set ANDROID_JAR to Android API 34 android.jar}"
: "${R8_JAR:?Set R8_JAR to r8.jar containing com.android.tools.r8.R8}"
: "${AAPT2:?Set AAPT2 to an executable aapt2 for your host}"
VERSION=$(python3 -c 'import sys,xml.etree.ElementTree as E; print(E.parse(sys.argv[1]).getroot().get("{http://schemas.android.com/apk/res/android}versionName"))' "$D/AndroidManifest.xml")
OUTPUT=${1:-"$D/build/QingJi-$VERSION-unsigned.apk"}
case "$OUTPUT" in /*) ;; *) OUTPUT="$PWD/$OUTPUT" ;; esac
mkdir -p "$(dirname "$OUTPUT")"
python3 "$D/rules/compile_rules.py" --check
python3 "$D/rules/compile_page_rules.py" --check
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT HUP INT TERM
mkdir -p "$T/classes" "$T/dex"
javac -g:none -source 8 -target 8 -encoding UTF-8 -cp "$ANDROID_JAR" -d "$T/classes" "$D"/src/me/minis/ledger/*.java
jar cf "$T/all.jar" -C "$T/classes" .
java -cp "$R8_JAR" com.android.tools.r8.R8 --release --lib "$ANDROID_JAR" --min-api 26 --pg-conf "$D/tools/release-rules.pro" --pg-map-output "$OUTPUT.mapping.txt" --output "$T/dex" "$T/all.jar"
"$AAPT2" compile --dir "$D/res" -o "$T/res.zip"
"$AAPT2" link -o "$T/unsigned.apk" --manifest "$D/AndroidManifest.xml" -I "$ANDROID_JAR" -A "$D/assets-v2" "$T/res.zip"
python3 "$D/tools/add_dex.py" "$T/unsigned.apk" "$T/dex/classes.dex"
cp "$T/unsigned.apk" "$OUTPUT"
echo "Unsigned APK: $OUTPUT"
echo 'Use zipalign/apksigner with your own key to install a self-built APK.'
echo 'A different signing key cannot replace the bundled release APK.'

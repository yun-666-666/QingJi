#!/bin/sh
set -eu
D=/var/minis/shared/qingji-3.50/work
O=/var/minis/shared/qingji-3.50/dist
TOOLS=/var/minis/shared/adblock-module/tools
OLD=/var/minis/shared/notification-ledger
T=$(mktemp -d /tmp/qingji368.XXXXXX)
trap 'rm -rf "$T"' EXIT
mkdir -p "$T/classes" "$T/dex" "$O"
javac -g:none -source 8 -target 8 -encoding UTF-8 -cp "$TOOLS/android34.jar" -d "$T/classes" "$D"/src/me/minis/ledger/*.java
jar cf "$T/all.jar" -C "$T/classes" .
java -cp "$TOOLS/r8.jar" com.android.tools.r8.R8 --release --lib "$TOOLS/android34.jar" --min-api 26 --pg-conf "$D/tools/release-rules.pro" --pg-map-output "$O/mapping.txt" --output "$T/dex" "$T/all.jar"
"$TOOLS/aapt2" compile --dir "$D/res" -o "$T/res.zip"
python3 -c 'import pathlib,sys; s=pathlib.Path(sys.argv[1]).read_text(); pathlib.Path(sys.argv[2]).write_text(s.replace("<manifest ","<manifest package=\"com.bjbyhd.screenreader_huawei.biller\" ",1))' "$D/AndroidManifest.xml" "$T/AndroidManifest.xml"
NODE_PATH=/var/minis/shared/qingji-3.50/validation/node_modules node "$D/tools/minify_assets.cjs" "$D/assets-v2" "$T/assets"
"$TOOLS/aapt2" link -o "$T/unsigned.apk" --manifest "$T/AndroidManifest.xml" -I "$TOOLS/android34.jar" -A "$T/assets" "$T/res.zip"
python3 "$D/tools/add_dex.py" "$T/unsigned.apk" "$T/dex/classes.dex"
javac -cp "$OLD/tools/apksig.jar" -d "$T/classes" "$OLD/tools/SignApk.java"
java -cp "$T/classes:$OLD/tools/apksig.jar" SignApk "$OLD/experiments/screenreader/keys/ledger.jks" "$T/unsigned.apk" "$O/QingJi-3.68.0.apk"
"$TOOLS/aapt2" dump badging "$O/QingJi-3.68.0.apk" | head -6
sha256sum "$O/QingJi-3.68.0.apk"

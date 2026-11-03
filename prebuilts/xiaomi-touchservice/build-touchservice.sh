#!/bin/bash
#
# SPDX-FileCopyrightText: 2026 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#
# Adds the MIUI stand-in classes to the stock TouchService. extract-files.py runs it on the extracted
# APK, by hand it takes the stock APK from system_ext of the stock ROM and the output APK:
#
#   device/xiaomi/uke/prebuilts/xiaomi-touchservice/build-touchservice.sh <stock TouchService.apk> \
#       <patched TouchService.apk>
#
set -e

HERE="$(cd "$(dirname "$0")" && pwd)"
APK="$1"
OUT="$2"
ROOT="$HERE/../../../../.."
TMP="$(mktemp -d)"

JAVAC="${JAVAC:-$ROOT/prebuilts/jdk/jdk21/linux-x86/bin/javac}"
D8="${D8:-$ROOT/out/host/linux-x86/bin/d8}"

mkdir -p "$TMP/classes" "$TMP/dex"
"$JAVAC" -nowarn -Xlint:-options --release 8 -d "$TMP/classes" $(find "$HERE/stubs" -name '*.java')
(cd "$TMP/classes" && zip -q -r "$TMP/stubs.jar" .)
"$D8" --min-api 30 --output "$TMP/dex" "$TMP/stubs.jar"

# classes2.dex goes beside the stock classes.dex. The other entries stay stored as they are, the
# platform signature is added by the build (android_app_import)
[ "$APK" -ef "$OUT" ] || cp "$APK" "$OUT"
cp "$TMP/dex/classes.dex" "$TMP/classes2.dex"
(cd "$TMP" && zip -q -j "$OUT" classes2.dex)

rm -rf "$TMP"

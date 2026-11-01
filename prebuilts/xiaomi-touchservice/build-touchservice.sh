#!/bin/bash
#
# SPDX-FileCopyrightText: 2026 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#
# Adds the MIUI stand-in classes to the stock TouchService. Run from the Android tree root with the
# stock APK and jar from system_ext of the stock ROM:
#
#   device/xiaomi/uke/prebuilts/xiaomi-touchservice/build-touchservice.sh <stock TouchService.apk> \
#       <stock xiaomi-pencilengine-pad.jar>
#
set -e

HERE="$(cd "$(dirname "$0")" && pwd)"
APK="$1"
JAR="$2"
OUT="$HERE"
TMP="$(mktemp -d)"

JAVAC="${JAVAC:-prebuilts/jdk/jdk21/linux-x86/bin/javac}"
D8="${D8:-out/host/linux-x86/bin/d8}"

mkdir -p "$TMP/classes" "$TMP/dex"
"$JAVAC" -nowarn -Xlint:-options --release 8 -d "$TMP/classes" $(find "$HERE/stubs" -name '*.java')
(cd "$TMP/classes" && zip -q -r "$TMP/stubs.jar" .)
"$D8" --min-api 30 --output "$TMP/dex" "$TMP/stubs.jar"

# classes2.dex goes beside the stock classes.dex. The other entries stay stored as they are, the
# platform signature is added by the build (android_app_import)
cp "$APK" "$OUT/TouchService.apk"
cp "$TMP/dex/classes.dex" "$TMP/classes2.dex"
(cd "$TMP" && zip -q -j "$OUT/TouchService.apk" classes2.dex)

mkdir -p "$HERE/../xiaomi-pencilengine"
cp "$JAR" "$HERE/../xiaomi-pencilengine/xiaomi-pencilengine-pad.jar"
rm -rf "$TMP"

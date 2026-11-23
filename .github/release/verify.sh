#!/usr/bin/env bash
set -euo pipefail
apk="$1"
tools="$ANDROID_HOME/build-tools/37.0.0"
expected=6d3570114da628a5085edf54c43186e0bc8cf6592f0bff6dec7ab7ee75c93366
"$tools/apksigner" verify --print-certs "$apk" > "$RUNNER_TEMP/thor-apk-certs.txt"
actual="$(sed -n 's/.*certificate SHA-256 digest: //p' "$RUNNER_TEMP/thor-apk-certs.txt" | sort -u)"
test "$actual" = "$expected" || { echo 'APK signer is not the Foldcade Thor release key'; exit 1; }
badging="$("$tools/aapt2" dump badging "$apk" | sed -n '1p')"
expected_package="package: name='app.foldcade.thor' versionCode='$VERSION_CODE' versionName='$VERSION_NAME' "
[[ "$badging" == "$expected_package"* ]] || { echo 'APK version/package mismatch'; exit 1; }
cp "$apk" "$RUNNER_TEMP/foldcade-thor.apk"

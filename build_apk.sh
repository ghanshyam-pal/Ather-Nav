#!/usr/bin/env bash
set -e

echo "=== Building AtherNav Universal Release APK ==="
chmod +x ./gradlew
./gradlew assembleRelease --no-daemon

mkdir -p output
cp app/build/outputs/apk/release/app-release.apk output/app.apk

echo ""
echo "=== Build Complete! ==="
echo "APK location: output/app.apk"
ls -lh output/app.apk
echo ""
echo "SHA-256 Checksum:"
shasum -a 256 output/app.apk

echo ""
echo "To install on a connected phone via ADB, run:"
echo "  adb install -r output/app.apk"
echo ""

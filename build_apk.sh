#!/data/data/com.termux/files/usr/bin/bash
set -e

export ANDROID_HOME=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/build-tools/34.0.0

cd /data/data/com.termux/files/home/StickerWidgetApp

echo "=========================================="
echo " Building StickerWidget Debug APK (CLI)   "
echo "=========================================="

./gradlew assembleDebug --no-daemon

echo ""
echo "=========================================="
echo " Build Complete!                          "
echo " APK Output:                              "
echo " app/build/outputs/apk/debug/app-debug.apk"
echo "=========================================="

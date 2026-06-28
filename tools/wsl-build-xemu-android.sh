#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="$ROOT/xemu/build-android-aarch64-wsl"

if [[ ! -f "$BUILD/build.ninja" ]]; then
  echo "Missing configured xemu Android build. Run tools/wsl-configure-xemu-android.sh first." >&2
  exit 1
fi

cd "$BUILD"
ninja qemu-system-i386 libxemu-core-i386.so

mkdir -p "$ROOT/android/app/src/main/jniLibs/arm64-v8a"
cp -f "$BUILD/qemu-system-i386" \
  "$ROOT/android/app/src/main/jniLibs/arm64-v8a/libqemu-system-i386.so"
cp -f "$BUILD/libxemu-core-i386.so" \
  "$ROOT/android/app/src/main/jniLibs/arm64-v8a/libxemu-core-i386.so"

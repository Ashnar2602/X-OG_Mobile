#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPS="$ROOT/deps"
VCPKG_ROOT="$DEPS/vcpkg"
NDK_ROOT="$DEPS/android-ndk-r27c-linux/android-ndk-r27c"
NDK_ZIP="$DEPS/android-ndk-r27c-linux.zip"

mkdir -p "$DEPS"

if [[ ! -d "$NDK_ROOT" ]]; then
  if [[ ! -f "$NDK_ZIP" ]]; then
    curl -L -o "$NDK_ZIP" "https://dl.google.com/android/repository/android-ndk-r27c-linux.zip"
  fi
  rm -rf "$DEPS/android-ndk-r27c-linux"
  mkdir -p "$DEPS/android-ndk-r27c-linux"
  unzip -oq "$NDK_ZIP" -d "$DEPS/android-ndk-r27c-linux"
fi

if [[ ! -x "$VCPKG_ROOT/vcpkg" ]]; then
  (cd "$VCPKG_ROOT" && ./bootstrap-vcpkg.sh -disableMetrics)
fi

export ANDROID_NDK_HOME="$NDK_ROOT"
export ANDROID_NDK_ROOT="$NDK_ROOT"

(cd "$VCPKG_ROOT" && ./vcpkg install glib:arm64-android pixman:arm64-android libsamplerate:arm64-android --clean-after-build)

PKGCONF="$(find "$VCPKG_ROOT/downloads/tools" -path '*mingw64*' -prune -o -name pkgconf -type f -print | head -n 1 || true)"
if [[ -z "$PKGCONF" ]]; then
  PKGCONF="$(command -v pkg-config)"
fi

echo "WSL Android dependencies ready."
echo "NDK_ROOT=$NDK_ROOT"
echo "VCPKG_ROOT=$VCPKG_ROOT"
echo "PKG_CONFIG=$PKGCONF"

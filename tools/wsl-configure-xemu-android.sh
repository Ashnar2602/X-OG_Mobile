#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
XEMU="$ROOT/xemu"
NDK_ROOT="$ROOT/deps/android-ndk-r27c-linux/android-ndk-r27c"
VCPKG_ROOT="$ROOT/deps/vcpkg"
TOOLCHAIN="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin"
PREFIX="$VCPKG_ROOT/installed/arm64-android"
BUILD="$XEMU/build-android-aarch64-wsl"

if [[ ! -x "$TOOLCHAIN/aarch64-linux-android29-clang" ]]; then
  echo "Missing Linux Android NDK. Run tools/wsl-bootstrap-android-deps.sh first." >&2
  exit 1
fi

export PKG_CONFIG="${PKG_CONFIG:-pkg-config}"
export PKG_CONFIG_LIBDIR="$PREFIX/lib/pkgconfig"
export PKG_CONFIG_PATH="$PREFIX/lib/pkgconfig"
export PKG_CONFIG_SYSROOT_DIR=
export AR="$TOOLCHAIN/llvm-ar"
export RANLIB="$TOOLCHAIN/llvm-ranlib"
export NM="$TOOLCHAIN/llvm-nm"
export STRIP="$TOOLCHAIN/llvm-strip"

find "$XEMU" -maxdepth 3 \( \
  -name configure -o \
  -name "*.sh" -o \
  -name "*.py" -o \
  -path "$XEMU/scripts/hxtool" -o \
  -path "$XEMU/scripts/minikconf.py" \
\) -type f -exec sed -i 's/\r$//' {} +

rm -rf "$BUILD"
mkdir -p "$BUILD"
cd "$BUILD"

bash ../configure \
  --cross-prefix= \
  --cc="$TOOLCHAIN/aarch64-linux-android29-clang" \
  --cxx="$TOOLCHAIN/aarch64-linux-android29-clang++" \
  --host-cc=cc \
  --cpu=aarch64 \
  --target-list=i386-softmmu \
  --disable-werror \
  --disable-rust \
  --disable-docs \
  --disable-tools \
  --disable-guest-agent \
  --disable-gtk \
  --disable-sdl \
  --disable-sdl-image \
  --disable-vnc \
  --disable-spice \
  --disable-curses \
  --disable-opengl \
  --disable-virglrenderer \
  --disable-libusb \
  --disable-usb-redir \
  --disable-curl \
  --disable-gnutls \
  --disable-nettle \
  --disable-gcrypt \
  --disable-bzip2 \
  --disable-zstd \
  --disable-lzo \
  --disable-libnfs \
  --disable-libiscsi \
  --disable-linux-aio \
  --disable-linux-io-uring \
  --disable-slirp \
  --disable-pa \
  --disable-pipewire \
  --disable-alsa \
  --disable-jack \
  --disable-oss \
  --disable-sndio \
  --audio-drv-list= \
  --extra-cflags="-DXBOX=1 -I$PREFIX/include -mbranch-protection=none -fno-sanitize=safe-stack" \
  --extra-cxxflags="-mbranch-protection=none -fno-sanitize=safe-stack" \
  --extra-ldflags="-L$PREFIX/lib -mbranch-protection=none"

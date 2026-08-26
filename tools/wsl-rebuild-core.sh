#!/usr/bin/env bash
# =============================================================================
# X-OG Mobile - rebuild the Android xemu core (.so libraries) under WSL2
#
# Usage (from PowerShell/cmd):
#   wsl -e bash -lc 'setsid nohup bash /mnt/c/<path>/tools/wsl-rebuild-core.sh \
#        > ~/xog-rebuild.log 2>&1 < /dev/null &'
#   wsl -e bash -lc 'tail -f ~/xog-rebuild.log'
#
# Or interactively inside WSL:  bash tools/wsl-rebuild-core.sh
#
# Requirements: WSL2 with Ubuntu 24.04 (git, curl, unzip, rsync, cmake,
# ninja, python3, pkg-config), ~10 GB free disk in the Linux filesystem,
# internet access. First run takes ~1h (NDK download + vcpkg builds);
# later runs are incremental and take minutes.
#
# The repository working tree is never modified except
# android/app/src/main/jniLibs/arm64-v8a/, which receives the outputs.
# All heavy state lives in $HOME/xog-deps, $HOME/xog-src, $HOME/xog-build-*.
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/xemu"
JNILIBS="$ROOT/android/app/src/main/jniLibs/arm64-v8a"

DEPS="$HOME/xog-deps"
NDK_ROOT="$DEPS/android-ndk-r27c-linux/android-ndk-r27c"
VCPKG_ROOT="$DEPS/vcpkg"
PREFIX="$VCPKG_ROOT/installed/arm64-android"
SRCCOPY="$HOME/xog-src"
BUILD="$HOME/xog-build-android-aarch64"
TOOLCHAIN="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin"

echo "== [0/6] Environment checks"
for tool in git curl unzip rsync cmake ninja python3 pkg-config cc; do
  command -v "$tool" >/dev/null || { echo "Missing '$tool'. On Ubuntu:"; \
    echo "  sudo apt-get install -y git curl unzip rsync cmake ninja-build python3 pkg-config build-essential"; exit 1; }
done

mkdir -p "$DEPS" "$JNILIBS"

echo "== [1/6] Android NDK (linux, r27c)"
if [[ ! -x "$TOOLCHAIN/aarch64-linux-android29-clang" ]]; then
  rm -rf "$DEPS/android-ndk-r27c-linux"
  mkdir -p "$DEPS/android-ndk-r27c-linux"
  if [[ ! -f "$DEPS/android-ndk-r27c-linux.zip" ]]; then
    curl -fL -C - -o "$DEPS/android-ndk-r27c-linux.zip" \
      "https://dl.google.com/android/repository/android-ndk-r27c-linux.zip"
  fi
  unzip -oq "$DEPS/android-ndk-r27c-linux.zip" -d "$DEPS/android-ndk-r27c-linux"
fi
echo "NDK ok: $TOOLCHAIN/aarch64-linux-android29-clang"

echo "== [2/6] vcpkg + arm64-android deps (glib, pixman, libsamplerate)"
if [[ ! -x "$VCPKG_ROOT/vcpkg" ]]; then
  git clone --depth 1 https://github.com/microsoft/vcpkg.git "$VCPKG_ROOT"
  (cd "$VCPKG_ROOT" && ./bootstrap-vcpkg.sh -disableMetrics)
fi
export ANDROID_NDK_HOME="$NDK_ROOT"
export ANDROID_NDK_ROOT="$NDK_ROOT"
if [[ ! -f "$PREFIX/lib/pkgconfig/glib-2.0.pc" ]]; then
  (cd "$VCPKG_ROOT" && ./vcpkg install glib:arm64-android pixman:arm64-android libsamplerate:arm64-android --clean-after-build)
fi
echo "vcpkg ok"

echo "== [3/6] Copy source to Linux filesystem"
mkdir -p "$SRCCOPY"
rsync -a --delete \
  --exclude '.git' --exclude 'build*' --exclude 'subprojects/.wraplock' \
  --exclude '*.o' --exclude '.venv' \
  "$SRC/" "$SRCCOPY/"
# Windows checkouts may carry CRLF; strip it from everything meson/git parse.
find "$SRCCOPY" -maxdepth 3 \( \
  -name configure -o -name "*.sh" -o -name "*.py" -o -name "meson.build" -o \
  -path "$SRCCOPY/scripts/hxtool" -o -path "$SRCCOPY/scripts/minikconf.py" \
\) -type f -exec sed -i 's/\r$//' {} +
echo "source copy ok: $SRCCOPY"

# Meson's wrap fetch has been observed producing truncated checkouts on some
# setups. Pre-fetch every subproject ourselves so meson uses them as-is.
echo "== [4/6] Pre-fetch meson subprojects"
prefetch() {
  local name=$1 rev=$2 url=$3
  local dest="$SRCCOPY/subprojects/$name"
  if [[ -d $dest/.git ]] && \
     git -C "$dest" cat-file -e "$rev^{commit}" 2>/dev/null && \
     [[ -z $(git -C "$dest" status --porcelain --untracked-files=no) ]]; then
    return 0
  fi
  echo "  prefetch $name @$rev"
  rm -rf "$dest"
  if ! git clone --quiet --depth 1 "$url" "$dest" 2>/dev/null; then
    git clone --quiet "$url" "$dest"
  fi
  if ! git -C "$dest" cat-file -e "$rev^{commit}" 2>/dev/null; then
    git -C "$dest" fetch --quiet --depth 1 origin "$rev" 2>/dev/null || true
  fi
  # Force full worktree materialization; partial checkouts have been observed.
  git -C "$dest" clean -fdx --quiet || true
  git -C "$dest" checkout -f --quiet --detach "$rev"
  if [[ -n $(git -C "$dest" status --porcelain) ]]; then
    git -C "$dest" clean -fdx --quiet || true
    git -C "$dest" checkout -f --quiet --detach "$rev"
  fi
  if [[ -n $(git -C "$dest" status --porcelain) ]]; then
    echo "ERROR: could not materialize complete worktree for $name" >&2
    return 1
  fi
}
prefetch berkeley-softfloat-3 b64af41c3276f97f0e181920400ee056b9c88037 https://gitlab.com/qemu-project/berkeley-softfloat-3.git
prefetch berkeley-testfloat-3 e7af9751d9f9fd3b47911f51a5cfd08af256a9ab https://gitlab.com/qemu-project/berkeley-testfloat-3.git
prefetch dtc b6910bec11614980a21e46fbccc35934b671bd81 https://gitlab.com/qemu-project/dtc.git
prefetch genconfig 42f85f9a2457e61d7e32542c07723565a284fcd6 https://github.com/mborgerson/genconfig.git
prefetch imgui b911105fca3ca1b025706dd168e5798070f143a1 https://github.com/xemu-project/imgui
prefetch implot 8553562dbb2025fd520f4bed57b094767b96c670 https://github.com/xemu-project/implot
prefetch keycodemapdb f5772a62ec52591ff6870b7e8ef32482371f22c6 https://gitlab.com/qemu-project/keycodemapdb.git
prefetch libblkio f84cc963a444e4cb34813b2dcfc5bf8526947dc0 https://gitlab.com/libblkio/libblkio
prefetch libvfio-user 0b28d205572c80b568a1003db2c8f37ca333e4d7 https://gitlab.com/qemu-project/libvfio-user.git
prefetch nv2a_vsh_cpu 561fe80da57a881f89000256b459440c0178a7ce https://github.com/xemu-project/nv2a_vsh_cpu
prefetch slirp 26be815b86e8d49add8c9a8b320239b9594ff03d https://gitlab.freedesktop.org/slirp/libslirp.git
prefetch SPIRV-Reflect c90b7b781cdcff63cf1b409ffc7ca0a714e0425e https://github.com/KhronosGroup/SPIRV-Reflect
prefetch tomlplusplus 30172438cee64926dc41fdd9c11fb3ba5b2ba9de https://github.com/marzer/tomlplusplus
prefetch volk 3ca312a4f38baa63d8006b6905abbeeb89c8087d https://github.com/zeux/volk
prefetch VulkanMemoryAllocator 3aa921224c154a0d2c43912bc88e1c42ce1f7607 https://github.com/GPUOpen-LibrariesAndSDKs/VulkanMemoryAllocator
prefetch glslang 275822a6261ee689aadb1da5f09a0ec2f058685c https://github.com/KhronosGroup/glslang

# Same workaround for [wrap-file] subprojects: download, verify hash, extract
# and overlay the wrapdb patch ourselves. (curl is disabled by configure.)
prefetch_file() {
  local dirname=$1 url=$2 hash=$3 marker=$4 patch_url=${5:-}
  local dest="$SRCCOPY/subprojects/$dirname"
  if [[ -d $dest && -e "$dest/$marker" ]]; then return 0; fi
  echo "  prefetch-file $dirname"
  rm -rf "$dest"
  mkdir -p "$HOME/xog-dl"
  curl -fsSL -o "$HOME/xog-dl/$dirname.src" "$url"
  echo "$hash  $HOME/xog-dl/$dirname.src" | sha256sum --check --quiet -
  mkdir -p "$dest.tmp"
  tar -xf "$HOME/xog-dl/$dirname.src" -C "$dest.tmp" --strip-components=1
  if [[ -n $patch_url ]]; then
    curl -fsSL -o "$HOME/xog-dl/$dirname.patch.zip" "$patch_url"
    rm -rf "$HOME/xog-dl/$dirname.patch"
    mkdir -p "$HOME/xog-dl/$dirname.patch"
    unzip -oq "$HOME/xog-dl/$dirname.patch.zip" -d "$HOME/xog-dl/$dirname.patch"
    # wrapdb zips may nest everything under a single top directory
    local entries top
    entries=$(ls -A "$HOME/xog-dl/$dirname.patch" | wc -l)
    top=$(ls -A "$HOME/xog-dl/$dirname.patch" | head -1)
    if [[ $entries == 1 && -d "$HOME/xog-dl/$dirname.patch/$top" ]]; then
      cp -a "$HOME/xog-dl/$dirname.patch/$top"/. "$dest.tmp"/
    else
      cp -a "$HOME/xog-dl/$dirname.patch"/. "$dest.tmp"/
    fi
  fi
  [[ -e "$dest.tmp/$marker" ]] || { echo "ERROR: $dirname missing $marker after extract" >&2; return 1; }
  mv "$dest.tmp" "$dest"
}
prefetch_file xxHash-0.8.3 https://github.com/Cyan4973/xxHash/archive/v0.8.3.tar.gz aae608dfe8213dfd05d909a57718ef82f30722c392344583d3f39050c7f29a80 meson.build https://wrapdb.mesonbuild.com/v2/xxhash_0.8.3-2/get_patch
prefetch_file SDL3-3.4.10 https://github.com/libsdl-org/SDL/releases/download/release-3.4.10/SDL3-3.4.10.tar.gz 12b34280415ec8418c864408b93d008a20a6530687ee613d60bfbd20411f2785 CMakeLists.txt
prefetch_file json-3.2.0 https://github.com/nlohmann/json/archive/v3.2.0/json-3.2.0.tar.gz 2de558ff3b3b32eebfb51cf2ceb835a0fa5170e6b8712b02be9c2c07fcfe52a1 meson.build https://wrapdb.mesonbuild.com/v2/json_3.2.0-1/get_patch

# volk ships no meson.build upstream; ours is vendored in the repository and
# must be restored after prefetch re-clones the directory.
if [[ ! -f "$SRC/subprojects/volk/meson.build" ]]; then
  echo "ERROR: $SRC/subprojects/volk/meson.build missing" >&2
  exit 1
fi
cp -f "$SRC/subprojects/volk/meson.build" "$SRCCOPY/subprojects/volk/meson.build"
echo "subprojects ready"

echo "== [5/6] Configure + build"
# Keep the build directory across runs: ninja re-runs configure
# automatically when meson.build changes, so core patches rebuild
# incrementally.
mkdir -p "$BUILD"
cd "$BUILD"
if [[ ! -f build.ninja ]]; then
export PKG_CONFIG=pkg-config
# Meson finds cmake only via env var / machine files, not PATH scan.
export CMAKE=/usr/bin/cmake
export CMAKE_MAKE_PROGRAM="$(command -v ninja)"
export PKG_CONFIG_LIBDIR="$PREFIX/lib/pkgconfig"
export PKG_CONFIG_PATH="$PREFIX/lib/pkgconfig"
export PKG_CONFIG_SYSROOT_DIR=
export AR="$TOOLCHAIN/llvm-ar"
export RANLIB="$TOOLCHAIN/llvm-ranlib"
export NM="$TOOLCHAIN/llvm-nm"
export STRIP="$TOOLCHAIN/llvm-strip"
bash "$SRCCOPY/configure" \
  --cross-prefix= \
  --cc="$TOOLCHAIN/aarch64-linux-android29-clang" \
  --cxx="$TOOLCHAIN/aarch64-linux-android29-clang++" \
  --host-cc=cc \
  --cpu=aarch64 \
  --target-list=i386-softmmu \
  --disable-werror \
  --disable-rust \
  --disable-containers \
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
fi

ninja qemu-system-i386 libxemu-core-i386.so

echo "== [6/6] Install into jniLibs"
file "$BUILD/qemu-system-i386" "$BUILD/libxemu-core-i386.so"
cp -f "$BUILD/qemu-system-i386" "$JNILIBS/libqemu-system-i386.so"
cp -f "$BUILD/libxemu-core-i386.so" "$JNILIBS/libxemu-core-i386.so"
ls -la "$JNILIBS"
echo "== CORE REBUILD COMPLETE =="
echo "Next: cd android && ./gradlew.bat assembleDebug, then install and test."

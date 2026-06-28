$ErrorActionPreference = "Stop"

$repo = Resolve-Path "$PSScriptRoot\..\xemu"
$build = Join-Path $repo "build-android-aarch64"
$bash = "C:\Program Files\Git\bin\bash.exe"
$vcvars = "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat"

if (!(Test-Path $bash)) {
  throw "Git Bash not found at $bash"
}
if (!(Test-Path $vcvars)) {
  throw "Visual Studio vcvars64.bat not found at $vcvars"
}

cmd.exe /c "`"$vcvars`" >nul && set" | ForEach-Object {
  $name, $value = $_ -split "=", 2
  if ($name -and $value) {
    Set-Item -Path "Env:$name" -Value $value
  }
}

$bashPath = "/c/Users/stall/AppData/Local/Programs/Python/Python312:/c/Users/stall/AppData/Roaming/Python/Python312/Scripts:/c/Android/Sdk/cmake/3.22.1/bin:/c/Android/Sdk/ndk/27.2.12479018/toolchains/llvm/prebuilt/windows-x86_64/bin:`$PATH"
$ndkBin = "C:/Android/Sdk/ndk/27.2.12479018/toolchains/llvm/prebuilt/windows-x86_64/bin"
$python = "/c/Users/stall/AppData/Local/Programs/Python/Python312/python.exe"
$ninja = "/c/Android/Sdk/cmake/3.22.1/bin/ninja.exe"

Push-Location $repo
try {
  & $bash -lc @"
set -e
export PATH="$bashPath"
export AR="$ndkBin/llvm-ar.exe"
export RANLIB="$ndkBin/llvm-ranlib.exe"
export NM="$ndkBin/llvm-nm.exe"
export STRIP="$ndkBin/llvm-strip.exe"
export PKG_CONFIG=false
rm -rf build-android-aarch64
mkdir build-android-aarch64
cd build-android-aarch64
../configure \
  --python="$python" \
  --ninja="$ninja" \
  --cross-prefix= \
  --cc="$ndkBin/aarch64-linux-android29-clang.cmd" \
  --cxx="$ndkBin/aarch64-linux-android29-clang++.cmd" \
  --host-cc=cl \
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
  --extra-cflags='-DXBOX=1'
"@
  if ($LASTEXITCODE -ne 0) {
    throw "xemu Android configure failed with exit code $LASTEXITCODE"
  }
} finally {
  Pop-Location
}

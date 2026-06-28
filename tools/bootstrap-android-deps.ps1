$ErrorActionPreference = "Stop"

$root = Resolve-Path "$PSScriptRoot\.."
$vcpkgRoot = Join-Path $root "deps\vcpkg"
$ndk = "C:\Android\Sdk\ndk\27.2.12479018"

if (!(Test-Path $ndk)) {
  throw "Android NDK not found at $ndk"
}

if (!(Test-Path $vcpkgRoot)) {
  New-Item -ItemType Directory -Force -Path (Split-Path $vcpkgRoot) | Out-Null
  git clone https://github.com/microsoft/vcpkg.git $vcpkgRoot
}

Push-Location $vcpkgRoot
try {
  if (!(Test-Path ".\vcpkg.exe")) {
    .\bootstrap-vcpkg.bat -disableMetrics
  }

  $env:ANDROID_NDK_HOME = $ndk
  $env:ANDROID_NDK_ROOT = $ndk
  .\vcpkg.exe install glib:arm64-android pixman:arm64-android libsamplerate:arm64-android --clean-after-build
  if ($LASTEXITCODE -ne 0) {
    throw "vcpkg install failed with exit code $LASTEXITCODE"
  }
} finally {
  Pop-Location
}

$pkgconf = Join-Path $vcpkgRoot "installed\x64-windows\tools\pkgconf\pkgconf.exe"
if (!(Test-Path $pkgconf)) {
  $pkgconf = Get-ChildItem $vcpkgRoot -Recurse -Filter pkgconf.exe | Select-Object -First 1 -ExpandProperty FullName
}

Write-Host "Android dependencies ready."
Write-Host "VCPKG_ROOT=$vcpkgRoot"
Write-Host "PKG_CONFIG=$pkgconf"

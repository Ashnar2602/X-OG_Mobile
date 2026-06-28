$ErrorActionPreference = "Stop"

$repo = Resolve-Path "$PSScriptRoot\..\xemu"
$env:PATH = "C:\Users\stall\AppData\Local\Programs\Python\Python312;C:\Users\stall\AppData\Roaming\Python\Python312\Scripts;C:\Android\Sdk\cmake\3.22.1\bin;$env:PATH"

Push-Location (Join-Path $repo "build-android-aarch64")
try {
  ninja qemu-system-i386
} finally {
  Pop-Location
}

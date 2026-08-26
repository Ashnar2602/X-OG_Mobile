# X-OG Mobile

Language: [English](README.md) | [Italiano](README.it.md) | [Français](README.fr.md) | [Deutsch](README.de.md) | [Español](README.es.md)

X-OG Mobile is an experimental Android port of xemu for original Xbox emulation. It is currently a proof of concept, but it already runs as a real Android app with video output, physical controller input, audio playback, game library scanning, app-private system file setup, pause/resume, and basic game metadata.

This project is not affiliated with Microsoft, Xbox, or the upstream xemu project.

## Current Status

The Android APK is standalone at app build level. Running:

```powershell
cd android
.\gradlew.bat assembleDebug
```

builds a functional debug APK because the prebuilt Android native core libraries are included in:

```text
android/app/src/main/jniLibs/arm64-v8a/
```

Those libraries were built from the vendored xemu source tree in `xemu/`. The core is not stock xemu: it contains Android-specific changes for `ANativeWindow`, Vulkan presentation, AAudio output, controller input, Android settings, orderly shutdown, pause, and resume.

The vendored xemu tree intentionally excludes upstream test fixtures, local build directories, and package caches. They are not needed for the Android app build, and some upstream test fixtures contain private keys used only for tests.

Important distinction:

- Building the APK is standalone today.
- Rebuilding the xemu core from source is not yet wired into Gradle.
- Updating upstream xemu requires updating `xemu/`, rebuilding the `.so` libraries, and replacing the files under `jniLibs`.

The upstream base is recorded in `XEMU_UPSTREAM.txt`:

```text
xemu commit 92407546f45cf20f207a9facc89f515bf1a6c1c2
```

## Features

- Native Android UI, package id `emu.xbox.og`
- Android 10+ (`minSdk 29`), target SDK 36
- `arm64-v8a` only
- Direct SAF game selection, without copying large ISO/XISO files into app storage
- App-private import for MCPX, BIOS, and HDD image
- xemu-generated EEPROM
- Vulkan renderer path for Android
- Experimental GLES presenter stub/fallback path
- Physical controller input
- AAudio playback
- Pause/resume overlay and lifecycle pause handling
- Game library folder scan
- XBE title extraction from disc images
- RAWG metadata lookup with app-private JSON/cover cache

## Not Included

This repository does not include and will not provide:

- Xbox BIOS
- MCPX ROM
- HDD images containing copyrighted dashboards
- Games, ISO, or XISO files
- Any Microsoft copyrighted system software

Users must provide their own legally obtained files.

## Build Requirements

Recommended local environment:

- JDK 17 or 21
- Android SDK platform 36
- Android NDK `27.2.12479018`
- CMake 3.22.1

Build:

```powershell
cd C:\Progetti\X1\x-og_mobile\android
.\gradlew.bat assembleDebug
```

APK output:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## Core Rebuild Notes

The vendored xemu core can be fully rebuilt from this repository alone, without
any external state. This is required whenever the core sources change (e.g.
when applying upstream xemu commits).

Requirements:

- Windows with WSL2 and Ubuntu 24.04 (`wsl --install -d Ubuntu-24.04`)
- ~10 GB free disk space in the WSL Linux filesystem
- Internet access (first run downloads the Linux NDK ~664 MB, vcpkg and all
  meson subprojects)
- Inside WSL: `sudo apt-get install -y git curl unzip rsync cmake ninja-build
  python3 pkg-config build-essential`

Rebuild (from PowerShell, replace the path with your checkout location):

```powershell
# one-time: keep a WSL session alive so background builds survive
Start-Process pwsh -WindowStyle Hidden -ArgumentList '-NoProfile','-Command',
  'wsl -e bash -lc ''sleep 14400'''

# launch the build in the background and follow the log
wsl -e bash -lc 'setsid nohup bash /mnt/c/<path>/tools/wsl-rebuild-core.sh \
  > $HOME/xog-rebuild.log 2>&1 < /dev/null &'
wsl -e bash -lc 'tail -f $HOME/xog-rebuild.log'
```

First run takes about one hour (NDK download, vcpkg builds of
glib/pixman/libsamplerate for `arm64-android`, full compile). Later runs are
incremental and take minutes. When it finishes, the fresh libraries are already
installed into `android/app/src/main/jniLibs/arm64-v8a/`; then just rebuild the
APK with Gradle.

Notes:

- All heavy build state lives inside the WSL home directory (`~/xog-deps`,
  `~/xog-src`, `~/xog-build-android-aarch64`) and is safe to delete at any time;
  the next run recreates it. The repository tree itself is never modified by
  the script.
- `xemu/subprojects/volk/meson.build` is intentionally vendored in this
  repository (upstream volk ships none) because `meson.build` consumes volk as
  a plain meson subproject exposing `volk_dep` as a PIC static library.
- The script pre-fetches every meson wrap subproject at its pinned revision:
  meson's own wrap fetch has been observed to produce truncated checkouts on
  some setups. It also exports `CMAKE` explicitly because meson does not scan
  `PATH` for cmake.

## License

X-OG Mobile is distributed under the GNU General Public License version 2, matching the xemu/QEMU base used by the project. See `LICENSE`.

The vendored xemu tree contains components under additional compatible open source licenses. See:

- `xemu/LICENSE`
- `xemu/COPYING`
- `xemu/COPYING.LIB`
- file-level license headers inside `xemu/`

Any redistribution of binaries must comply with the GPL and the licenses of the bundled third-party components.

## Roadmap

- Integrate xemu core rebuild into Gradle
- Improve renderer selection and GLES support
- Add a richer error/log console
- Add save persistence validation tests
- Add touch controls after the physical controller path is stable
- Improve metadata matching and local library presentation

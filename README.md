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

Those libraries were built from the vendored xemu source tree in `xemu/`. The core is not stock xemu: it contains Android-specific changes for `ANativeWindow`, Vulkan presentation, AAudio output, controller input, Android settings, orderly shutdown, pause, and resume. It also includes an Android block protocol, `androidfd:`, so game discs selected through SAF are read from an already-open file descriptor instead of reopening `/proc/self/fd` or copying the ISO/XISO into app storage.

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
- Direct SAF game selection via xemu `androidfd:` block access, without copying large ISO/XISO files into app storage
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

The project includes scripts under `tools/` for configuring and rebuilding the vendored xemu core, including WSL-oriented helpers. This path is still developer-facing and is not yet integrated into `gradlew assembleDebug`.

Current expected flow after changing the core:

```text
1. rebuild libxemu-core-i386.so from xemu/
2. copy the rebuilt library into android/app/src/main/jniLibs/arm64-v8a/
3. rebuild the APK with Gradle
```

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

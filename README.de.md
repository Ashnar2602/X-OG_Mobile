# X-OG Mobile

Sprachen: [English](README.md) | [Italiano](README.it.md) | [Français](README.fr.md) | [Deutsch](README.de.md) | [Español](README.es.md)

X-OG Mobile ist ein experimenteller Android-Port von xemu zur Emulation der ersten Xbox. Das Projekt ist derzeit noch ein Proof of Concept, funktioniert aber bereits als echte Android-App mit Videoausgabe, physischer Controller-Eingabe, Audio, Bibliotheksscan, Einrichtung der Systemdateien im privaten App-Speicher, Pause/Fortsetzen und einfachen Spiele-Metadaten.

Dieses Projekt ist nicht mit Microsoft, Xbox oder dem upstream xemu-Projekt verbunden.

## Aktueller Stand

Das Android-APK ist auf App-Build-Ebene standalone. Der Befehl:

```powershell
cd android
.\gradlew.bat assembleDebug
```

erstellt ein funktionierendes Debug-APK, weil die vorkompilierten nativen Android-Core-Bibliotheken bereits hier enthalten sind:

```text
android/app/src/main/jniLibs/arm64-v8a/
```

Diese Bibliotheken wurden aus dem vendorizierten xemu-Quellbaum in `xemu/` gebaut. Der Core ist kein unverändertes xemu: Er enthält Android-spezifische Änderungen für `ANativeWindow`, Vulkan-Präsentation, AAudio, Controller-Eingabe, Android-Einstellungen, geordnetes Herunterfahren, Pause und Fortsetzen. Außerdem enthält er ein Android-Blockprotokoll, `androidfd:`, damit über SAF ausgewählte Discs aus einem bereits geöffneten File Descriptor gelesen werden, ohne `/proc/self/fd` erneut zu öffnen oder die ISO/XISO in den App-Speicher zu kopieren.

Der vendorizierte xemu-Baum schließt upstream-Test-Fixtures, lokale Build-Verzeichnisse und Paket-Caches bewusst aus. Sie werden für den Android-App-Build nicht benötigt, und einige upstream-Test-Fixtures enthalten private Schlüssel, die nur für Tests verwendet werden.

Wichtige Unterscheidung:

- Das APK kann heute standalone gebaut werden.
- Der Neubau des xemu-Cores aus dem Quellcode ist noch nicht in Gradle integriert.
- Ein Update von upstream xemu erfordert ein Update von `xemu/`, einen Neubau der `.so`-Bibliotheken und das Ersetzen der Dateien in `jniLibs`.

Die upstream-Basis ist in `XEMU_UPSTREAM.txt` dokumentiert:

```text
xemu commit 92407546f45cf20f207a9facc89f515bf1a6c1c2
```

## Funktionen

- Native Android-Oberfläche, package id `emu.xbox.og`
- Android 10+ (`minSdk 29`), target SDK 36
- Nur `arm64-v8a`
- Direkte Spielauswahl über SAF und xemu-Blockzugriff `androidfd:`, ohne große ISO/XISO-Dateien in den App-Speicher zu kopieren
- App-privater Import für MCPX, BIOS und HDD-Image
- Von xemu erzeugte EEPROM-Datei
- Android-Vulkan-Rendererpfad
- Experimenteller GLES-Presenter als Stub/Fallback
- Physische Controller-Eingabe
- Audio über AAudio
- Pause/Fortsetzen-Overlay und Pause im Android-Lifecycle
- Scan eines Spielebibliotheksordners
- XBE-Titelextraktion aus Disc-Images
- RAWG-Metadaten mit privatem JSON/Cover-Cache

## Nicht Enthalten

Dieses Repository enthält nicht und wird nicht bereitstellen:

- Xbox-BIOS
- MCPX-ROM
- HDD-Images mit urheberrechtlich geschützten Dashboards
- Spiele, ISO- oder XISO-Dateien
- Urheberrechtlich geschützte Microsoft-Systemsoftware

Benutzer müssen ihre eigenen legal erworbenen Dateien bereitstellen.

## Build-Anforderungen

Empfohlene lokale Umgebung:

- JDK 17 oder 21
- Android SDK platform 36
- Android NDK `27.2.12479018`
- CMake 3.22.1

Build:

```powershell
cd C:\Progetti\X1\x-og_mobile\android
.\gradlew.bat assembleDebug
```

APK-Ausgabe:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## Hinweise zum Core-Neubau

Das Projekt enthält Skripte unter `tools/`, um den vendorizierten xemu-Core zu konfigurieren und neu zu bauen, einschließlich WSL-Helfern. Dieser Ablauf ist noch entwicklerorientiert und nicht in `gradlew assembleDebug` integriert.

Aktueller Ablauf nach Änderungen am Core:

```text
1. libxemu-core-i386.so aus xemu/ neu bauen
2. die Bibliothek nach android/app/src/main/jniLibs/arm64-v8a/ kopieren
3. das APK mit Gradle neu bauen
```

## Lizenz

X-OG Mobile wird unter der GNU General Public License Version 2 verteilt, passend zur verwendeten xemu/QEMU-Basis. Siehe `LICENSE`.

Der vendorizierte xemu-Baum enthält weitere Komponenten unter kompatiblen Open-Source-Lizenzen. Siehe:

- `xemu/LICENSE`
- `xemu/COPYING`
- `xemu/COPYING.LIB`
- dateispezifische Lizenzhinweise unter `xemu/`

Jede Weitergabe von Binärdateien muss die GPL und die Lizenzen der enthaltenen Drittkomponenten einhalten.

## Roadmap

- xemu-Core-Neubau in Gradle integrieren
- Renderer-Auswahl und GLES-Unterstützung verbessern
- Eine bessere Fehler-/Log-Konsole hinzufügen
- Tests zur Persistenz von Spielständen hinzufügen
- Touch-Steuerung nach Stabilisierung des physischen Controllers ergänzen
- Metadaten-Matching und lokale Bibliothek verbessern

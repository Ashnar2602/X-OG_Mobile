# X-OG Mobile

Langues : [English](README.md) | [Italiano](README.it.md) | [Français](README.fr.md) | [Deutsch](README.de.md) | [Español](README.es.md)

X-OG Mobile est un port Android expérimental de xemu pour l'émulation de la première Xbox. Le projet reste un proof of concept, mais il fonctionne déjà comme une véritable application Android avec sortie vidéo, manette physique, audio, scan de bibliothèque, configuration des fichiers système dans le stockage privé de l'application, pause/reprise et métadonnées de base des jeux.

Ce projet n'est affilié ni à Microsoft, ni à Xbox, ni au projet xemu upstream.

## État Actuel

L'APK Android est standalone au niveau de la build de l'application. La commande :

```powershell
cd android
.\gradlew.bat assembleDebug
```

produit un APK debug fonctionnel, car les bibliothèques natives Android précompilées du core sont incluses dans :

```text
android/app/src/main/jniLibs/arm64-v8a/
```

Ces bibliothèques ont été compilées depuis l'arborescence xemu vendue avec le projet dans `xemu/`. Le core n'est pas un xemu stock : il contient des changements Android pour `ANativeWindow`, la présentation Vulkan, AAudio, l'entrée manette, les paramètres Android, l'arrêt ordonné, la pause et la reprise. Il inclut aussi un protocole block Android, `androidfd:`, afin que les disques sélectionnés via SAF soient lus depuis un file descriptor déjà ouvert, sans rouvrir `/proc/self/fd` ni copier l'ISO/XISO dans le stockage de l'application.

L'arborescence xemu vendue avec le projet exclut volontairement les fixtures de test upstream, les répertoires de build locaux et les caches de paquets. Ils ne sont pas nécessaires à la build Android et certaines fixtures de test upstream contiennent des clés privées utilisées uniquement pour les tests.

Distinction importante :

- La build de l'APK est standalone aujourd'hui.
- La recompilation du core xemu depuis les sources n'est pas encore reliée à Gradle.
- Mettre à jour xemu upstream demande de mettre à jour `xemu/`, recompiler les bibliothèques `.so`, puis remplacer les fichiers dans `jniLibs`.

La base upstream est indiquée dans `XEMU_UPSTREAM.txt` :

```text
xemu commit 92407546f45cf20f207a9facc89f515bf1a6c1c2
```

## Fonctionnalités

- Interface Android native, package id `emu.xbox.og`
- Android 10+ (`minSdk 29`), target SDK 36
- ABI unique `arm64-v8a`
- Sélection directe des jeux via SAF et accès block xemu `androidfd:`, sans copie de grandes ISO/XISO dans le stockage de l'application
- Import privé de MCPX, BIOS et image HDD
- EEPROM générée par xemu
- Chemin renderer Vulkan Android
- Chemin GLES expérimental comme stub/fallback
- Manette physique
- Audio via AAudio
- Overlay pause/reprise et pause liée au lifecycle Android
- Scan de dossier de bibliothèque
- Extraction du titre XBE depuis les images disque
- Métadonnées RAWG avec cache privé JSON/cover

## Non Inclus

Ce dépôt n'inclut pas et ne fournira pas :

- BIOS Xbox
- ROM MCPX
- Images HDD contenant des dashboards sous copyright
- Jeux, ISO ou XISO
- Logiciels système Microsoft sous copyright

Les utilisateurs doivent fournir leurs propres fichiers obtenus légalement.

## Prérequis de Build

Environnement conseillé :

- JDK 17 ou 21
- Android SDK platform 36
- Android NDK `27.2.12479018`
- CMake 3.22.1

Build :

```powershell
cd C:\Progetti\X1\x-og_mobile\android
.\gradlew.bat assembleDebug
```

APK généré :

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## Notes sur la Recompilation du Core

Le projet inclut des scripts dans `tools/` pour configurer et recompiler le core xemu vendorizé, avec des helpers orientés WSL. Ce flux reste destiné aux développeurs et n'est pas encore intégré à `gradlew assembleDebug`.

Flux actuel après modification du core :

```text
1. recompiler libxemu-core-i386.so depuis xemu/
2. copier la bibliothèque dans android/app/src/main/jniLibs/arm64-v8a/
3. reconstruire l'APK avec Gradle
```

## Licence

X-OG Mobile est distribué sous GNU General Public License version 2, conformément à la base xemu/QEMU utilisée par le projet. Voir `LICENSE`.

L'arborescence xemu vendue avec le projet contient aussi des composants sous d'autres licences open source compatibles. Voir :

- `xemu/LICENSE`
- `xemu/COPYING`
- `xemu/COPYING.LIB`
- les en-têtes de licence dans les fichiers sous `xemu/`

Toute redistribution de binaires doit respecter la GPL et les licences des composants tiers inclus.

## Feuille de Route

- Intégrer la recompilation du core xemu dans Gradle
- Améliorer la sélection renderer et le support GLES
- Ajouter une console d'erreurs/logs plus complète
- Ajouter des tests de persistance des sauvegardes
- Ajouter les contrôles tactiles après stabilisation de la manette physique
- Améliorer le matching des métadonnées et la bibliothèque locale

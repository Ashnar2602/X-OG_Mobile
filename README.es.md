# X-OG Mobile

Idiomas: [English](README.md) | [Italiano](README.it.md) | [Français](README.fr.md) | [Deutsch](README.de.md) | [Español](README.es.md)

X-OG Mobile es un port experimental de xemu para Android orientado a la emulación de la Xbox original. El proyecto todavía es una prueba de concepto, pero ya funciona como una aplicación Android real con salida de vídeo, mando físico, audio, escaneo de biblioteca, configuración de archivos de sistema en el almacenamiento privado de la app, pausa/reanudación y metadatos básicos de juegos.

Este proyecto no está afiliado con Microsoft, Xbox ni con el proyecto xemu upstream.

## Estado Actual

El APK de Android es standalone a nivel de build de la app. Ejecutando:

```powershell
cd android
.\gradlew.bat assembleDebug
```

se genera un APK debug funcional porque las bibliotecas nativas Android precompiladas del core ya están incluidas en:

```text
android/app/src/main/jniLibs/arm64-v8a/
```

Esas bibliotecas se compilaron desde el árbol xemu vendorizado en `xemu/`. El core no es xemu stock: contiene cambios específicos para Android, como `ANativeWindow`, presentación Vulkan, AAudio, entrada de mando, ajustes Android, apagado ordenado, pausa y reanudación.

El árbol xemu vendorizado excluye intencionadamente fixtures de prueba upstream, directorios de build locales y cachés de paquetes. No son necesarios para la build Android y algunas fixtures de prueba upstream contienen claves privadas usadas solo para tests.

Distinción importante:

- La build del APK ya es standalone.
- La recompilación del core xemu desde el código fuente aún no está integrada en Gradle.
- Actualizar xemu upstream requiere actualizar `xemu/`, recompilar las bibliotecas `.so` y reemplazar los archivos en `jniLibs`.

La base upstream está registrada en `XEMU_UPSTREAM.txt`:

```text
xemu commit 92407546f45cf20f207a9facc89f515bf1a6c1c2
```

## Funciones

- UI Android nativa, package id `emu.xbox.og`
- Android 10+ (`minSdk 29`), target SDK 36
- Solo ABI `arm64-v8a`
- Selección directa de juegos mediante SAF, sin copiar grandes ISO/XISO al almacenamiento de la app
- Importación privada para MCPX, BIOS e imagen HDD
- EEPROM generada por xemu
- Ruta de renderer Vulkan para Android
- Ruta GLES experimental como stub/fallback
- Entrada de mando físico
- Audio mediante AAudio
- Overlay de pausa/reanudación y pausa vinculada al lifecycle Android
- Escaneo de carpeta de biblioteca
- Extracción del título XBE desde imágenes de disco
- Metadatos RAWG con caché privada JSON/cover

## No Incluido

Este repositorio no incluye ni proporcionará:

- BIOS de Xbox
- ROM MCPX
- Imágenes HDD con dashboards protegidos por copyright
- Juegos, ISO o XISO
- Software de sistema Microsoft protegido por copyright

Los usuarios deben aportar sus propios archivos obtenidos legalmente.

## Requisitos de Build

Entorno recomendado:

- JDK 17 o 21
- Android SDK platform 36
- Android NDK `27.2.12479018`
- CMake 3.22.1

Build:

```powershell
cd C:\Progetti\X1\x-og_mobile\android
.\gradlew.bat assembleDebug
```

APK generado:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## Notas sobre la Recompilación del Core

El proyecto incluye scripts en `tools/` para configurar y recompilar el core xemu vendorizado, incluidos helpers para WSL. Este flujo sigue siendo para desarrolladores y aún no está integrado en `gradlew assembleDebug`.

Flujo actual después de cambiar el core:

```text
1. recompilar libxemu-core-i386.so desde xemu/
2. copiar la biblioteca a android/app/src/main/jniLibs/arm64-v8a/
3. reconstruir el APK con Gradle
```

## Licencia

X-OG Mobile se distribuye bajo la GNU General Public License versión 2, igual que la base xemu/QEMU usada por el proyecto. Ver `LICENSE`.

El árbol xemu vendorizado contiene componentes bajo otras licencias open source compatibles. Ver:

- `xemu/LICENSE`
- `xemu/COPYING`
- `xemu/COPYING.LIB`
- cabeceras de licencia en los archivos dentro de `xemu/`

Cualquier redistribución de binarios debe cumplir con la GPL y con las licencias de los componentes de terceros incluidos.

## Roadmap

- Integrar la recompilación del core xemu en Gradle
- Mejorar la selección de renderer y el soporte GLES
- Añadir una consola de errores/logs más completa
- Añadir pruebas de persistencia de partidas guardadas
- Añadir controles táctiles después de estabilizar el mando físico
- Mejorar el matching de metadatos y la biblioteca local

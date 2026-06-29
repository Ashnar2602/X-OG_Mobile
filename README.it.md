# X-OG Mobile

Lingue: [English](README.md) | [Italiano](README.it.md) | [Français](README.fr.md) | [Deutsch](README.de.md) | [Español](README.es.md)

X-OG Mobile è un port Android sperimentale di xemu per l'emulazione della prima Xbox. Al momento è ancora un proof of concept, ma è già una vera app Android con output video, input da controller fisico, audio, scansione libreria giochi, configurazione dei file di sistema nello storage privato dell'app, pausa/ripresa e metadati di base dei giochi.

Questo progetto non è affiliato con Microsoft, Xbox o con il progetto xemu upstream.

## Stato Attuale

L'APK Android è standalone a livello di build dell'app. Eseguendo:

```powershell
cd android
.\gradlew.bat assembleDebug
```

si ottiene un APK debug funzionante, perché le librerie native Android precompilate del core sono già incluse in:

```text
android/app/src/main/jniLibs/arm64-v8a/
```

Quelle librerie sono state compilate dal sorgente xemu vendorizzato in `xemu/`. Il core non è xemu stock: contiene modifiche specifiche per Android, tra cui `ANativeWindow`, presentazione Vulkan, audio AAudio, input controller, settings Android, shutdown ordinato, pausa e resume. Include anche un protocollo block Android, `androidfd:`, così i dischi selezionati tramite SAF vengono letti da un file descriptor già aperto invece di riaprire `/proc/self/fd` o copiare la ISO/XISO nello storage dell'app.

Il sorgente xemu vendorizzato esclude volutamente test fixture upstream, directory di build locali e package cache. Non servono alla build Android e alcune fixture di test upstream contengono chiavi private usate solo per i test.

Distinzione importante:

- La build dell'APK oggi è standalone.
- La ricompilazione del core xemu da sorgente non è ancora collegata a Gradle.
- Aggiornare xemu upstream richiede aggiornare `xemu/`, ricompilare le librerie `.so` e sostituire i file in `jniLibs`.

La base upstream è registrata in `XEMU_UPSTREAM.txt`:

```text
xemu commit 92407546f45cf20f207a9facc89f515bf1a6c1c2
```

## Funzionalità

- UI Android nativa, package id `emu.xbox.og`
- Android 10+ (`minSdk 29`), target SDK 36
- Solo ABI `arm64-v8a`
- Selezione diretta dei giochi tramite SAF e accesso block xemu `androidfd:`, senza copiare grandi ISO/XISO nello storage dell'app
- Import in storage privato app per MCPX, BIOS e immagine HDD
- EEPROM generata da xemu
- Renderer Vulkan Android
- Path GLES sperimentale come stub/fallback
- Input da controller fisico
- Audio via AAudio
- Overlay pausa/resume e pausa automatica durante il lifecycle Android
- Scansione cartella libreria giochi
- Estrazione titolo XBE dalle immagini disco
- Lookup metadati RAWG con cache privata JSON/cover

## Cosa Non È Incluso

Questo repository non include e non fornirà:

- BIOS Xbox
- ROM MCPX
- Immagini HDD contenenti dashboard protette da copyright
- Giochi, ISO o XISO
- Software di sistema Microsoft protetto da copyright

L'utente deve fornire solo file ottenuti legalmente.

## Requisiti di Build

Ambiente consigliato:

- JDK 17 o 21
- Android SDK platform 36
- Android NDK `27.2.12479018`
- CMake 3.22.1

Build:

```powershell
cd C:\Progetti\X1\x-og_mobile\android
.\gradlew.bat assembleDebug
```

Output APK:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## Note sulla Ricompilazione del Core

Il progetto include script in `tools/` per configurare e ricompilare il core xemu vendorizzato, inclusi helper per WSL. Questa parte è ancora pensata per sviluppatori e non è ancora integrata in `gradlew assembleDebug`.

Flusso attuale dopo modifiche al core:

```text
1. ricompilare libxemu-core-i386.so da xemu/
2. copiare la libreria in android/app/src/main/jniLibs/arm64-v8a/
3. ricompilare l'APK con Gradle
```

## Licenza

X-OG Mobile è distribuito sotto GNU General Public License versione 2, in linea con la base xemu/QEMU usata dal progetto. Vedi `LICENSE`.

Il sorgente xemu vendorizzato contiene componenti con ulteriori licenze open source compatibili. Vedi:

- `xemu/LICENSE`
- `xemu/COPYING`
- `xemu/COPYING.LIB`
- gli header di licenza nei singoli file sotto `xemu/`

Qualsiasi redistribuzione di binari deve rispettare la GPL e le licenze dei componenti di terze parti inclusi.

## Roadmap

- Integrare la ricompilazione del core xemu in Gradle
- Migliorare selezione renderer e supporto GLES
- Aggiungere una console errori/log più completa
- Aggiungere test di persistenza dei salvataggi
- Aggiungere controlli touch dopo la stabilizzazione del controller fisico
- Migliorare matching metadati e libreria locale

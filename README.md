# 📱 Analizzatore-Spazio

> 🗂️ Analizza lo spazio di archiviazione del tuo dispositivo Android, categoria per categoria, in modo semplice e veloce.

[![Android](https://img.shields.io/badge/Android-34-%233DDC84?logo=android&labelColor=white)](https://developer.android.com) [![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-%237F52FF?logo=kotlin&labelColor=white)](https://kotlinlang.org) [![Compose](https://img.shields.io/badge/Jetpack%20Compose-%234285F4?logo=jetpackcompose&labelColor=white)](https://developer.android.com/jetpack/compose)

---

## ✨ Caratteristiche

- 🧮 **Suddivisione reale dello spazio**: calcola le dimensioni reali (non stime) di App, Foto, Video, Audio, Documenti, Altro e Sistema.
- 📊 **Grafico ad anello** interattivo con i colori delle categorie, senza spazi vuoti tra i segmenti.
- 🏷️ **Legenda** con tutte le categorie, anche quelle vuote, e percentuali precise sul totale (2 decimali) — es. 60 MB su 128 GB = ~0.05%.
- 🚀 **Apertura file**: tocca un file (foto, video, audio, documento, APK…) e aprilo con le app del sistema tramite il resolver classico "Apri con" — identico a un file manager.
- 📂 **Altro**: raccoglie tutto ciò che non rientra nelle altre categorie (APK, ISO, EXE, archivi, font e altro).
- 💾 **Cache intelligente**: l'analisi viene salvata, così tornando indietro non devi ri- analizzare nulla.
- ⏪ **Back funzionante**: nessuna schermata bianca, premendo Indietro si torna alla Home (e doppio tap per uscire).
- 🔐 **Accesso completo ai file**: gestione automatica dei permessi di archiviazione.

---

## 🗂️ Categorie analizzate

| Icona | Categoria | Colore | Contenuto |
|-------|-----------|--------|-----------|
| 📱 | **Applicazioni** | 🔵 | App installate, con possibilità di avvio diretto |
| 📸 | **Foto** | 🟢 | Immagini raggruppate per album |
| 🎬 | **Video** | 🟠 | Video raggruppati per album |
| 🎵 | **Audio** | 🟣 | Brani e registrazioni |
| 📄 | **Documenti** | 🔴 | PDF, Word, Excel, PowerPoint, Testo, Archivi, APK |
| 📚 | **Altro** | ⚪ | Ogni altro tipo di file (EXE, ISO, BIN, DAT…) |
| ⚙️ | **Sistema** | 🩶 | Spazio occupato dal sistema = usato − categorie |

---

## 🛠️ Tecnologie

- 🧩 **Linguaggio**: Kotlin 1.9.22
- 🎨 **UI**: Jetpack Compose + Material 3 (BOM 2024.04.01)
- 📦 **Build**: Gradle / AGP 8.2.2, compileSdk & targetSdk 34, minSdk 26
- 🖼️ **Icona**: adaptive icon con anello colorato dei 7 colori delle categorie su sfondo blu

---

## 🔧 Come si compila

```bash
JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.12+1-ms
ANDROID_HOME=/opt/android-sdk
./gradlew assembleDebug --no-daemon
```

📦 L'APK generato si trova in `app/build/outputs/apk/debug/app-debug.apk` e una copia pronta all'installazione in **`Analizzatore-Spazio.apk`** (root del progetto).

> ⚠️ **Nota**: per una corretta compilazione servono **JDK 21** (le versioni più nuove possono rompere Kotlin 1.9.22) e Android SDK.

---

## 📲 Installazione

1. Copia `Analizzatore-Spazio.apk` sul telefono 📤
2. Installa e apri l'app ✅
3. Concedi **Accesso a tutti i file** quando richiesto 🔓
4. Il giro di analisi parte da solo 🚀

---

## 📁 Struttura del progetto

```
app/src/main/java/com/simonecompany/analizzatorespazio/
├── MainActivity.kt                    🏠 Activity principale
├── AnalizzatoreSpazioApp.kt           💉 Applicazione
├── ui/
│   ├── navigation/AppNavigation.kt    🧭 Navigazione (Home ⇄ Dettaglio)
│   ├── screens/
│   │   ├── MainScreen.kt              📊 Home con donut + legenda
│   │   └── DetailScreen.kt            🔍 Dettaglio categoria e apertura file
│   └── components/
│       ├── DonutChart.kt              🍩 Grafico ad anello
│       └── LegendList.kt              🏷️ Legenda con percentuali
└── viewmodel/
    ├── StorageViewModel.kt            📦 Logica di scansione e categorie
    ├── StateHolders.kt                🧠 Stato UI
    └── ScanCache.kt                   💾 Cache delle analisi


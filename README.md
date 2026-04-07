# ImageEditor – Android 16 App

## Features
- 📂 Bild laden via SAF (kein Google Fotos, kein Account nötig)
- ⬛ Schwarzen Rahmen hinzufügen (Slider 0–200px)
- 🟥 Schwarzes Rechteck per Slider (Breite/Höhe) + Finger-Drag positionieren
- 💾 Speichern via SAF (freie Ordnerwahl: Downloads, Wallpapers etc.)
- Keine Permissions im Manifest nötig

## Voraussetzungen
- Android Studio Meerkat (2024.3+)
- JDK 17
- Android 16 (API 36) Emulator oder Gerät

## Setup in Android Studio
1. Projekt öffnen: `File → Open → ImageEditor/`
2. Warten bis Gradle Sync abgeschlossen
3. Run auf Emulator oder Gerät

## Projektstruktur
```
app/src/main/java/com/example/imageeditor/
├── model/
│   └── EditorState.kt          ← Zentraler UI-State
├── viewmodel/
│   └── EditorViewModel.kt      ← Business Logic, Bitmap-Rendering
├── ui/
│   ├── screens/
│   │   └── EditorScreen.kt     ← Hauptbildschirm
│   ├── components/
│   │   ├── ImageCanvas.kt      ← Canvas mit Drag-Support
│   │   └── BottomControls.kt   ← Slider + Buttons
│   └── theme/
│       └── Theme.kt            ← Material3 Dark Theme
└── MainActivity.kt
```

## Bekannte Hinweise
- `minSdk = 36` → nur Android 16+ Geräte
- Dark Theme by default (optimal für AMOLED)
- Das schwarze Rechteck ist auf AMOLED-Displays unsichtbar (Pixel = aus)
- Export immer als PNG (verlustfrei)

# Chiaroscuro

A minimalist Android image editor built for AMOLED displays. Named after the painting technique of strong contrasts between light and dark.

## Features

### Black Rectangle
Place a black rectangle anywhere on the image to cover watermarks or unwanted elements. The rectangle is previewed in red/transparent so you can see exactly where it sits on dark backgrounds — exported as pure black.

### AMOLED Analyzer
Detects near-black pixels that waste battery on AMOLED screens by keeping pixels lit instead of off.

- **Threshold Slider (0–50):** Controls how dark a pixel must be to be flagged. Values 5–15 are recommended to preserve shadow detail.
- **Warm-Tint Mode:** Only targets pixels with a warm color cast (R−B > 3). Useful for images with sepia-toned dark backgrounds — neutral shadows in faces and details are left untouched.
- **Analyze:** Highlights affected pixels in red so you can preview the impact before committing.
- **Apply:** Converts all flagged pixels to pure `#000000`.

### Export
Always exports as PNG with black pixels converted to transparent (`alpha = 0`). On AMOLED displays, transparent pixels are fully off — identical to black, but the file compresses significantly smaller.

### Quick Action (FAB ⚡)
One-tap action that places the black rectangle at a preconfigured position. Useful when processing multiple images with the same watermark location. Edit `QuickAction.kt` to set your coordinates.

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material3 (dark theme)
- **Architecture:** MVVM — `ViewModel` + `StateFlow`
- **File Access:** SAF (Storage Access Framework) — no storage permissions required
- **Min SDK:** 36 (Android 16)

## Project Structure

```
app/src/main/java/com/github/reygnn/chiaroscuro/
├── model/
│   ├── EditorState.kt       — central UI state
│   └── QuickAction.kt       — hardcoded quick action coordinates
├── viewmodel/
│   └── EditorViewModel.kt   — business logic, bitmap operations
├── ui/
│   ├── screens/
│   │   └── EditorScreen.kt  — main screen
│   └── components/
│       ├── ImageCanvas.kt   — canvas with drag support
│       └── BottomControls.kt — sliders and buttons
└── MainActivity.kt
```

## Setup

1. Clone the repo
2. Open in Android Studio Meerkat or later
3. Let Gradle sync
4. Run on an Android 16 device or emulator

## Customizing the Quick Action

Edit `QuickAction.kt` to match your watermark position:

```kotlin
object QuickAction {
    const val RECT_X      = 683f   // X position in pixels
    const val RECT_Y      = 1291f  // Y position in pixels
    const val RECT_WIDTH  = 57     // width in pixels
    const val RECT_HEIGHT = 57     // height in pixels
}
```

## License

MIT

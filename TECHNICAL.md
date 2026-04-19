# Chiaroscuro — Technical Documentation

Architecture, data flow, design decisions, and contributor guide. For user-facing documentation see `README.md`.

---

## Table of Contents

1. [Stack](#stack)
2. [Architecture Overview](#architecture-overview)
3. [Project Structure](#project-structure)
4. [Data Flow](#data-flow)
5. [Key Design Decisions](#key-design-decisions)
6. [Coordinate System & Math](#coordinate-system--math)
7. [Preferences & Persistence](#preferences--persistence)
8. [Export Pipeline](#export-pipeline)
9. [Localization](#localization)
10. [Testing](#testing)
11. [Build & Setup](#build--setup)
12. [Cloning the App](#cloning-the-app)
13. [Known Limitations & TODOs](#known-limitations--todos)

---

## Stack

```
agp                      = 8.13.2
kotlin                   = 2.2.21
composeBom               = 2026.03.01
datastore                = 1.2.1
navigation               = 2.9.7
lifecycleRuntimeKtx      = 2.10.0

# Testing
junit                    = 4.13.2
mockk                    = 1.13.13
kotlinx-coroutines-test  = 1.9.0
turbine                  = 1.1.0
```

- Single-module Gradle project.
- `minSdk = compileSdk = targetSdk = 36`.
- JVM target 17 via `kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }`.
- Full R8 (shrink + obfuscate + optimize), no compat mode.

---

## Architecture Overview

MVVM with Jetpack Compose, StateFlow, and a repository-backed single source of truth.

```
┌─────────────────────────┐   ┌──────────────────────────┐
│    EditorScreen         │   │   PreferencesScreen      │
│    (Compose)            │   │   (Compose)              │
└────────────┬────────────┘   └────────────┬─────────────┘
             │                             │
             ▼                             ▼
┌─────────────────────────┐   ┌──────────────────────────┐
│    EditorViewModel      │   │   PreferencesViewModel   │
│    + EditorState        │   │                          │
│    + source/analysis    │   │                          │
│      Bitmap StateFlows  │   │                          │
└────────────┬────────────┘   └────────────┬─────────────┘
             │                             │
             └──────────────┬──────────────┘
                            ▼
             ┌─────────────────────────────┐
             │   PreferencesRepository     │ ← Single Source of Truth
             │   (interface)               │
             └──────────────┬──────────────┘
                            ▼
             ┌─────────────────────────────┐
             │ DataStorePreferencesRepo    │
             │ (input clamping,            │
             │  distinctUntilChanged)      │
             └──────────────┬──────────────┘
                            ▼
             ┌─────────────────────────────┐
             │   DataStore<Preferences>    │
             └─────────────────────────────┘
```

Image processing lives in a separate pure-Kotlin package:

```
ViewModel ─── ImageProcessing (thin Bitmap adapter) ─── AmoledTransform (pure IntArray, JVM-testable)
                                                  └── ImageGeometry   (pure math, JVM-testable)
```

No DI framework. `ChiaroscuroApplication` acts as the composition root, lazily creating the single `PreferencesRepository` instance that both ViewModels consume via their `Factory` (reading from `APPLICATION_KEY` in `CreationExtras`).

---

## Project Structure

```
app/src/main/java/com/github/reygnn/chiaroscuro/
│
├── ChiaroscuroApplication.kt    — composition root; holds single PreferencesRepository
├── MainActivity.kt              — edge-to-edge, NavHost (Editor + Preferences)
│
├── model/
│   ├── EditorState.kt           — immutable UI state data class (no Bitmaps inside)
│   ├── ExportMessage.kt         — sealed interface for localizable export outcomes
│   └── QuickAction.kt           — (legacy) hardcoded fallback coordinates
│
├── preferences/
│   ├── UserPreferences.kt           — immutable snapshot + defaults + bounds + ExportBackground enum
│   ├── PreferencesRepository.kt     — public interface (single source of truth)
│   └── DataStorePreferencesRepository.kt  — DataStore-backed impl w/ clamping
│
├── imaging/
│   ├── AmoledTransform.kt       — pure pixel kernels (IntArray in/out, Android-free)
│   ├── ImageGeometry.kt         — pure screen↔image coordinate math
│   └── ImageProcessing.kt       — thin Bitmap↔IntArray adapter (not unit-tested)
│
├── viewmodel/
│   ├── EditorViewModel.kt       — orchestrates image ops, export, cross-screen sync
│   └── PreferencesViewModel.kt  — thin wrapper forwarding to repository
│
├── ui/
│   ├── screens/
│   │   ├── EditorScreen.kt       — main editing screen, SAF launchers, FAB, menu
│   │   └── PreferencesScreen.kt  — settings screen
│   ├── components/
│   │   ├── AppIcons.kt           — 3 hand-defined ImageVectors (no material-icons-extended)
│   │   ├── FormRows.kt           — IntTextFieldRow, StringTextFieldRow, IntSliderRow
│   │   ├── ImageCanvas.kt        — Canvas composable, zoom/pan, rect overlay
│   │   └── BottomControls.kt     — scrollable control panel
│   └── theme/
│       └── Theme.kt              — Material3 dark theme

app/src/main/res/
├── values/strings.xml           — en-US (default)
├── values-de/strings.xml        — Deutsch
├── mipmap-anydpi-v26/ic_launcher.xml  — adaptive icon
└── drawable/
    ├── ic_launcher_background.xml
    ├── ic_launcher_foreground.xml
    └── ic_launcher_monochrome.xml

app/src/test/java/com/github/reygnn/chiaroscuro/
├── testing/
│   ├── MainDispatcherRule.kt
│   └── TESTING_CONVENTIONS.kt   — read this before writing tests
├── preferences/
│   ├── DataStorePreferencesRepositoryTest.kt
│   └── FakeDataStore.kt
├── viewmodel/
│   ├── EditorViewModelTest.kt
│   ├── PreferencesViewModelTest.kt
│   └── FakePreferencesRepository.kt
└── imaging/
    ├── AmoledTransformTest.kt
    └── ImageGeometryTest.kt
```

---

## Data Flow

```
User action (tap / gesture / text input)
       │
       ▼
EditorScreen / PreferencesScreen (Compose)
       │  calls VM method
       ▼
ViewModel
       │  1. Eager state update:  _state.update { ... }
       │  2. Persist via launch:  repository.setX(value)
       │  3. Heavy work off main: withContext(Dispatchers.IO) { ImageProcessing... }
       ▼
PreferencesRepository
       │  - Input clamping (see UserPreferences.Companion bounds)
       │  - distinctUntilChanged on the settings Flow
       ▼
DataStore<Preferences>
       │
       ▼ emits
Repository.settings (Flow<UserPreferences>)
       │
       ├──► EditorViewModel.init collects → updates EditorState (cross-screen sync)
       └──► PreferencesViewModel.appPrefs (stateIn)
                ▼
       UI recomposes via collectAsStateWithLifecycle()
```

**Cross-screen sync:** a preference written from `PreferencesScreen` is observed by `EditorViewModel`'s init-time `repository.settings.collect { ... }` and merged into `EditorState`. The editor's own setters update `_state` eagerly for responsiveness; the collect is idempotent for self-originated changes.

---

## Key Design Decisions

### Repository as single source of truth
Earlier each ViewModel instantiated its own `AppPreferences` against the shared DataStore — two independent `.map { }` chains for the same data. The `PreferencesRepository` interface collapses this into one, adds input clamping for range-bound keys, and guarantees `distinctUntilChanged` semantics. Tests verify the contract; ViewModels rely on it.

### No DI framework
Hilt and Koin are overkill for a two-ViewModel app. `ChiaroscuroApplication` holds a single `by lazy` repository; both VMs read it from `CreationExtras[APPLICATION_KEY]` in their `Factory`. If a third VM appears, this still works. If a fourth layer appears, `Application` becomes the DI-graph entry point without any of the current code moving.

### Bitmaps outside `EditorState`
`android.graphics.Bitmap` uses reference-equality for `equals`, which breaks `data class` semantics: two bitmaps with identical pixels compare unequal, `copy()` produces a deceptively shared reference, and `toString()` leaks a Bitmap address. Instead, `_sourceBitmap` and `_analysisBitmap` are separate `MutableStateFlow<Bitmap?>` fields on the VM. Consequence: Compose's recomposition scope is also sharper — a slider moving `amoledThreshold` doesn't bust composables that only observe bitmaps.

### Pure-Kotlin pixel kernels
`AmoledTransform` and `ImageGeometry` take primitive inputs (`IntArray`, `Int`, `Float`) and return pure outputs. No `android.graphics.*` imports. Consequence: 100% JVM unit-testable without Robolectric or instrumented tests. The thin `ImageProcessing` adapter does the `Bitmap ↔ IntArray` conversion and is deliberately not tested — any non-trivial logic added here must first be extracted to a pure kernel.

### TextField commit on focus loss (not per-keystroke)
Earlier versions wrote to DataStore on every keystroke and re-keyed the local text state from the external value. This produced two race conditions: mid-typing characters could be lost when a late DataStore emission overwrote local state, and every keystroke incurred a serialized DataStore transaction. The current `IntTextFieldRow` / `StringTextFieldRow` keep text in local state and commit only when focus is lost.

### Slider commit on drag end (not per frame)
`onValueChange` fires on every frame of a drag — up to 120× per second on a high-refresh display. The current `IntSliderRow` uses `onValueChangeFinished` to commit once per gesture, avoiding DataStore write storms.

### Export mode as enum, not boolean
`ExportBackground` is an enum (`AMOLED`, `TRANSPARENT`) persisted by name, not a boolean like `isTransparent`. Enums are extensible — if a third mode ever appears (e.g. white background for print) we add an enum case without schema migration and without a second redundant boolean.

---

## Coordinate System & Math

Three coordinate spaces are involved. The logic lives in `ImageGeometry.computeRectOriginInImage` and is covered by `ImageGeometryTest`.

### 1. Image Space
Origin: top-left of the source bitmap. Units: pixels. `(0,0)` to `(bitmap.width, bitmap.height)`.

### 2. Canvas Space (unzoomed)
The image is scaled to fit the canvas while maintaining aspect ratio (`fitCenter`):

```kotlin
val baseScale = minOf(canvasW / bitmap.width, canvasH / bitmap.height)
val baseOffX  = (canvasW - bitmap.width  * baseScale) / 2f
val baseOffY  = (canvasH - bitmap.height * baseScale) / 2f
```

A point `(imgX, imgY)` in image space maps to `(baseOffX + imgX * baseScale, baseOffY + imgY * baseScale)` in canvas space.

### 3. Screen Space (zoomed)
The zoom transform is applied via `withTransform { translate(zoomOffset); scale(zoomScale, pivot=canvasCenter) }`. A canvas-space point `(cx, cy)` maps to screen space as:

```
screenX = (cx - canvasW/2) * zoomScale + canvasW/2 + zoomOffset.x
screenY = (cy - canvasH/2) * zoomScale + canvasH/2 + zoomOffset.y
```

### Rect Back-Calculation (screen → image)

The rectangle is fixed at screen-space center. On export, we need its top-left in image pixels:

```kotlin
// Step 1: Rect top-left in screen space
val rectScreenW = rectWidth  * baseScale * zoomScale
val rectScreenH = rectHeight * baseScale * zoomScale
val screenX     = canvasW / 2f - rectScreenW / 2f
val screenY     = canvasH / 2f - rectScreenH / 2f

// Step 2: Screen → canvas space (invert zoom transform)
val canvasX = (screenX - zoomOffset.x - canvasW / 2f) / zoomScale + canvasW / 2f
val canvasY = (screenY - zoomOffset.y - canvasH / 2f) / zoomScale + canvasH / 2f

// Step 3: Canvas → image space (invert baseScale + offset)
val imgX = ((canvasX - baseOffX) / baseScale).toInt()
val imgY = ((canvasY - baseOffY) / baseScale).toInt()
```

The resulting `(imgX, imgY)` is where the black rectangle is drawn on the bitmap before export.

---

## Preferences & Persistence

Persistence uses Jetpack DataStore Preferences. The `DataStorePreferencesRepository` clamps range-bound inputs before writing and emits through `distinctUntilChanged`.

All keys, defaults, and bounds are declared on `UserPreferences.Companion` as constants — single source of truth, test-referenceable.

```
Key                  Type      Default     Bounds              Description
──────────────────────────────────────────────────────────────────────────────────
amoled_threshold     Int       50          [0, 50]             AMOLED filter threshold
amoled_warm_mode     Boolean   false       —                   Warm tint mode
fab_apply_amoled     Boolean   true        —                   FAB: apply AMOLED step
fab_place_rect       Boolean   true        —                   FAB: place rectangle step
rect_x               Float     683f        unvalidated         (legacy — UI-editable, not used)
rect_y               Float     1291f       unvalidated         (legacy — UI-editable, not used)
rect_width           Int       57          ≥ 1                 Rectangle width in pixels
rect_height          Int       57          ≥ 1                 Rectangle height in pixels
sleeve_counter       Int       1           ≥ 1                 Auto-incremented on each save
filename_prefix      String    "sleeve"    blank → default     Filename prefix
export_background    String    "AMOLED"    enum or fallback    Export rendering mode
```

`export_background` is persisted as `ExportBackground.name` (`"AMOLED"` or `"TRANSPARENT"`). Unknown or corrupted values fall back to the default rather than throwing, so a bad write can never crash the app at launch.

Settings emitted by the repository are observed by both `EditorViewModel.init { ... }` (merged into `EditorState`) and `PreferencesViewModel.appPrefs` (via `stateIn`).

---

## Export Pipeline

```
sourceBitmap (Bitmap, from ViewModel)
       │
       ▼  Bitmap.copy(ARGB_8888, mutable=true)        [in writeExport]
working: Bitmap
       │
       ├──► [if rectVisible && canvasSize != Zero]
       │       ImageGeometry.computeRectOriginInImage(...)
       │       ImageProcessing.drawBlackRect(working, x, y, w, h)  ← mutates working
       │
       ▼
background mode switch:
  AMOLED      → working stays as-is (black pixels stay black, fully opaque)
  TRANSPARENT → ImageProcessing.blackToTransparent(working)
                  ↓ delegates to AmoledTransform.blackToTransparent(IntArray)
       │
       ▼
result: Bitmap
       │
       ├── compress(PNG, quality=100)  ← quality param ignored for PNG
       │
       ▼
ContentResolver.openOutputStream(uri) → file on disk
       │
       └── repository.incrementCounter()
```

Errors during export are caught via `runCatching` and surfaced as `ExportMessage.Error(throwable.message)` which the screen renders via the `msg_error` string resource.

---

## Localization

Two locales are declared: `values/` (en-US, default) and `values-de/` (Deutsch). AGP's `androidResources.localeFilters` is set to `["en", "de"]`, so AndroidX library strings for other locales are stripped at build time.

Locale selection follows the system language. No in-app switcher. Android 13+ supports per-app-locale override via the system settings if a user needs something different.

User-visible strings live in `strings.xml`. `ExportMessage` (sealed interface in `model/`) carries the export-outcome variant; `EditorScreen` resolves it to a localized toast at display time, keeping the ViewModel `Context`-free.

Number formatting in the AMOLED pixel-percentage display uses `Locale.US` deliberately (`String.format(Locale.US, "%.1f", pct)`) to match the technical notation in the rest of the UI. Everything else follows the active locale.

---

## Testing

JVM unit tests cover all logic layers. Instrumented tests and Robolectric are deliberately not used — the test loop stays fast and CI-friendly.

```
Layer                                  Tests   Location
───────────────────────────────────────────────────────────────────────────────
Repository contract (clamping,           19    DataStorePreferencesRepositoryTest
  distinctUntilChanged, counter,
  prefix fallback, export background)
EditorViewModel (state transitions,      13    EditorViewModelTest
  cross-screen sync, zoom bounds)
PreferencesViewModel (forwarding,         9    PreferencesViewModelTest
  appPrefs state, export mode)
AMOLED pixel kernel (threshold           22    AmoledTransformTest
  boundaries, warm-mode deltas,
  immutability, count correctness)
Coordinate geometry (baseScale,           5    ImageGeometryTest
  pan/zoom invariants)
───────────────────────────────────────────────────────────────────────────────
```

Not covered: `ImageProcessing` (thin Bitmap adapter — see the file's top-of-class comment for the policy); real DataStore persistence across process restart; Compose UI rendering.

### Conventions

Read `app/src/test/java/com/github/reygnn/chiaroscuro/testing/TESTING_CONVENTIONS.kt` before writing tests. Key points:

- One `TestDispatcher` installed via `MainDispatcherRule`, reused in `runTest(mainRule.testDispatcher)`. Never a separate `TestScope` or `StandardTestDispatcher`.
- **ViewModels and their collaborators are constructed inside the `runTest` block**, never as test-class fields — JUnit's `@Rule` setup runs *after* field initializers, so a VM built as a field binds `viewModelScope` to the real `Dispatchers.Main` (which has no looper under JVM tests).
- `org.junit.Assert.*` for assertions, not `kotlin.test.*`.
- MockK with `relaxed = true` for thin collaborators; hand-rolled in-memory fakes (`FakePreferencesRepository`, `FakeDataStore`) for Flow-heavy interfaces.
- Turbine for StateFlow assertions.

### Running tests

```bash
./gradlew :app:testDebugUnitTest
```

Sub-second on any developer machine.

---

## Build & Setup

### Requirements
- Android Studio Meerkat or later
- JDK 17
- Android 16 (API 36) device or emulator

### R8 / ProGuard

Release builds have `isMinifyEnabled = true` and `isShrinkResources = true`. `proguard-rules.pro` is intentionally empty (with explanatory comments): AndroidX libraries ship their own consumer rules embedded in each AAR, and R8 picks them up automatically. Do **not** add blanket `-keep class androidx.** { *; }` rules — this was the single biggest source of bloat in an earlier version of this project.

### First Run
```bash
git clone git@github.com:reygnn/chiaroscuro.git
cd chiaroscuro
# Open in Android Studio → Gradle sync → Run
```

No API keys, no secrets, no external services. The app works fully offline.

### Release AAB

```bash
./gradlew :app:bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```

Current release AAB is ~3.4 MB.

---

## Cloning the App

This app is designed to be cloned via Linux scripts to produce sibling apps with different package names. The intentional sed-targets are:

**Package / applicationId** (globally):
- `app/build.gradle.kts`: `namespace`, `applicationId`
- All `.kt` files: `package ...`, `import ...`
- `AndroidManifest.xml` uses relative `.MainActivity` / `.ChiaroscuroApplication` references which follow the package automatically

**Brand-visible strings** (if the clone ships under a different name):
- `res/values/strings.xml`: `app_name`
- `res/values-de/strings.xml`: `app_name`

**DataStore file name** (must differ between co-installable clones):
- `ChiaroscuroApplication.kt`: `preferencesDataStore(name = "chiaroscuro_prefs")`

**Launcher icon** (optional but usually desired):
- `res/drawable/ic_launcher_foreground.xml`, `ic_launcher_background.xml`, `ic_launcher_monochrome.xml`

Nothing else is package-coupled. The repository pattern, imaging kernels, form rows, and test foundation are reusable verbatim.

---

## Known Limitations & TODOs

**Coordinate rounding on export**
The back-calculation from screen to image space uses `toInt()` which truncates. At high zoom levels this is accurate. At zoom = 1 with a non-integer `baseScale`, there may be ±1px offset. For watermark covering this is imperceptible.

**No undo**
Once AMOLED correction is applied, the previous `sourceBitmap` is lost. A simple undo stack (keeping the previous bitmap reference) would be a natural next feature. The kernel is already non-mutating, so adding undo would only require the VM to hold a history list.

**AMOLED analysis discards on any setting change**
`_analysisBitmap` is cleared whenever `setAmoledThreshold` or `toggleAmoledWarmMode` is called. This prevents showing stale analysis results but means the user must re-analyze after changing settings.

**Canvas size race condition**
`canvasSize` is reported via `onSizeChanged` which fires after first layout. If the user somehow triggers a save before the canvas has rendered (unlikely), `canvasSize` would be `Size.Zero` and the rect would not be drawn on export. The export checks for this: `if (s.rectVisible && s.canvasSize != Size.Zero)`.

**`rect_x` and `rect_y` in Preferences are legacy**
In an earlier version, the rectangle had a manually draggable position stored in DataStore. In the current version, the rectangle is always centered in the canvas and its position is derived from zoom/pan. The `rect_x`/`rect_y` DataStore keys remain editable via `PreferencesScreen` but are no longer read by `EditorViewModel` during positioning. Future cleanup: either wire them back in or remove the UI rows.

**Photo Picker vs. SAF**
Currently uses `ACTION_OPEN_DOCUMENT` (SAF file picker). Switching to `PickVisualMedia` (Android Photo Picker) would default to showing recent images first — better UX for the primary workflow of processing the most recent wallpaper. This is a one-line change in `EditorScreen`.

**`minSdk = 36` cuts off most devices**
Deliberate: the app targets a single Android 16 device. If this clone is meant for broader distribution, drop `minSdk` to 26–29 — the entire stack (Compose, DataStore, adaptive icons, coroutines) works on those levels without changes.

**No baseline profile**
Cold-start is not yet optimized via an app-specific baseline profile. For a single-Activity Compose app this typically makes a noticeable difference. Sensible next step if cold-start becomes a concern: add a `:macrobenchmark` module with `BaselineProfileRule`.

**No accessibility audit**
The app has `contentDescription` on all icon buttons and uses `stringResource` everywhere, but has not been exercised with TalkBack. Slider labels and the AMOLED overlay state changes in particular should be verified before distribution.

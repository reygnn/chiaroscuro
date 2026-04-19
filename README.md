# Chiaroscuro

An Android image editor for AMOLED displays. Strip watermarks, remove near-black pixel noise, and export battery-efficient images in one tap.

The name comes from the Renaissance painting technique of contrasting light and dark — which is exactly what the app does with pixel values.

---

## What it does

Three things, in the order you typically use them:

1. **Cover small watermarks** (e.g. the Gemini AI star icon in the bottom-right of an AI-generated wallpaper) with a black rectangle you position by panning the image under it.

2. **Fix near-black pixel noise** on AMOLED displays. AI-generated dark backgrounds often contain values like `#010101` or `#0A0808` — technically dark but still lit on AMOLED, wasting battery. The analyzer finds these pixels and turns them into pure black.

3. **Export** as PNG. Either with black pixels kept black (default, ideal for AMOLED wallpapers) or with black pixels turned transparent (cutout for layering).

---

## How to use

### Load an image
Tap **📂 Load**. Any PNG or JPG will do.

### Cover a watermark
1. Tap the **Rectangle** chip to turn it **Active**.
2. Adjust **Width** and **Height** until the rectangle is slightly bigger than the watermark.
3. Pinch-zoom into the watermark corner. Pan (drag) the image so the watermark sits under the red preview rectangle.
4. The rectangle stays fixed in the middle of the canvas — you position the watermark by moving the image.

### Fix AMOLED near-black pixels
1. Tap **🔍 Analyze**. Pixels that would be corrected are highlighted in red.
2. Adjust the **Threshold** slider if needed (default 10 is usually fine; higher = more aggressive).
3. Flip on **Warm Tint** if the image has a warm dark tint (AI-generated stuff often does) and you want to preserve neutral shadows.
4. Tap **✅ Apply** to commit the correction. Tap **✕ Cancel** to discard.

### Quick Action (⚡ FAB)
The lightning-bolt floating action button runs your preferred steps in one shot:
- Apply AMOLED correction (if enabled in Preferences)
- Place the black rectangle (if enabled in Preferences)
- Open the save dialog with a suggested filename

Configure what the FAB does in **⋮ → Preferences → FAB Quick Action**.

### Save
Tap **💾 Save**. Pick a location. Done. The counter increments automatically, so the next save suggests `sleeve_002.png`, then `sleeve_003.png`, etc.

---

## Settings (⋮ → Preferences)

**FAB Quick Action** — which steps the ⚡ button runs.

**AMOLED Filter** — default threshold and warm-tint mode used when the editor first opens. Adjustable again per-image in the editor itself.

**Black Rectangle** — default rectangle dimensions. The `X` and `Y` fields are legacy and currently have no effect on placement.

**File Naming** — filename prefix (default `sleeve`) and the auto-incrementing counter. The **Reset counter to 001** button restarts numbering.

**Export** — choose how black pixels are written:
- **Off (default):** black stays black. Fully opaque PNG. Best for AMOLED wallpapers — the display renders black as fully-off pixels natively.
- **On:** black becomes transparent. PNG with alpha channel. Best when you want a cutout to layer over something else.

---

## Tips

- **Double-tap** the canvas to reset zoom and pan to 1.0×/center.
- The analyzer only shows the overlay — it doesn't change your image. You can tweak the threshold back and forth before applying.
- AMOLED correction is destructive for the session. Reload the image (📂 Load) to start over.
- The app works completely offline. No account, no network, no tracking.

---

## Requirements

- Android 16 (API 36) or newer.

---

## Language

English and Deutsch. The app follows your system language.

---

## Privacy

No network requests, no analytics, no permissions beyond what the system file picker grants. Your images are read and written through the Android Storage Access Framework with scoped, temporary access — the app never sees your full gallery.

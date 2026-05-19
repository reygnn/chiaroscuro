# Chiaroscuro

An Android image editor for AMOLED displays. Strip watermarks, remove near-black pixel noise, and export battery-efficient images in one tap.

The name comes from the Renaissance painting technique of contrasting light and dark — which is exactly what the app does with pixel values.

---

## What it does

Three things, in the order you typically use them:

1. **Cover small watermarks** (e.g. the Gemini AI sparkle in the bottom-right of an AI-generated wallpaper) with a black diamond — or axis-aligned rectangle, your choice — that you position by panning the image under it. The position is remembered across image loads: re-activate the cover and it jumps straight onto the saved sparkle spot.

2. **Fix near-black pixel noise** on AMOLED displays. AI-generated dark backgrounds often contain values like `#010101` or `#0A0808` — technically dark but still lit on AMOLED, wasting battery. The analyzer finds these pixels and turns them into pure black.

3. **Export** as PNG. Either with black pixels kept black (default, ideal for AMOLED wallpapers) or with black pixels turned transparent (cutout for layering).

---

## How to use

The 🛠 floating action button in the bottom-right opens a vertical speed-dial with all the per-image actions. The ☰ mini-FAB inside it opens the **Commands** card for the sliders.

### Load an image
Open the speed-dial and tap **📂 Load**. Any PNG or JPG will do.

### Cover a watermark
1. Tap **◇ Rectangle** in the speed-dial. The cover appears in the centre of the canvas.
2. If you've already exported once before, the cover jumps directly onto the saved sparkle position and blinks 5× so you can spot it instantly. Otherwise it stays centred and you align it manually.
3. Open the **Commands** card (☰) to adjust **Width** and **Height** until the cover is slightly larger than the watermark.
4. Pinch-zoom into the watermark corner. Pan (drag) the image so the sparkle sits under the red preview cover.
5. The cover stays fixed in the centre of the canvas — you position the watermark by moving the image. On the next save the new position is written back to Preferences automatically.

### Fix AMOLED near-black pixels
1. Tap **🔍 Analyze** in the speed-dial. Pixels that would be corrected are highlighted in red.
2. Open the **Commands** card (☰) and adjust the **Threshold** slider if needed (default 10 is usually fine; higher = more aggressive).
3. Flip on **Warm Tint** if the image has a warm dark tint (AI-generated stuff often does) and you want to preserve neutral shadows.
4. Tap **✅ Apply** to commit the correction. Tap **✕ Cancel** to discard.

### Quick Action (⚡)
The lightning-bolt mini-FAB at the top of the speed-dial runs your preferred steps in one shot:
- Apply AMOLED correction (if enabled in Preferences)
- Place the watermark cover (if enabled in Preferences)
- Open the save dialog with a suggested filename

Configure what it does in **⋮ → Preferences → FAB Quick Action**.

### Save
From the speed-dial tap **💾 Save**. Pick a location. Done. The counter increments automatically, so the next save suggests `image_002.png`, then `image_003.png`, etc.

---

## Settings (⋮ → Preferences)

**FAB Quick Action** — which steps the ⚡ button runs.

**AMOLED Filter** — default threshold and warm-tint mode used when the editor first opens. Adjustable again per-image in the editor itself.

**Black Rectangle** — default cover dimensions and shape. The `X` / `Y` fields hold the sparkle position in image pixels; they're refreshed automatically every time you save, so once you've nailed the spot on one wallpaper, every later wallpaper of the same resolution gets the cover dropped straight on. **Rotate 45°** switches between a diamond (matches the 4-point Gemini sparkle) and an axis-aligned rectangle.

**File Naming** — filename prefix (default `image`) and the auto-incrementing counter. The **Reset counter to 001** button restarts numbering.

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

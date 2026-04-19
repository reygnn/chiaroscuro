# AMOLED Correction — How It Works

This document explains in precise detail which pixels the AMOLED correction targets and what the Warm Tint Mode changes about that targeting. If you want to know exactly why a specific pixel in your image was or wasn't affected, this is the document.

For general usage see `README.md`. For architecture see `TECHNICAL.md`.

---

## Table of Contents

1. [Why this exists](#why-this-exists)
2. [The core rule: what counts as near-black](#the-core-rule-what-counts-as-near-black)
3. [Warm Tint Mode](#warm-tint-mode)
4. [Worked examples](#worked-examples)
5. [Analyze vs Apply](#analyze-vs-apply)
6. [Choosing a threshold](#choosing-a-threshold)
7. [What the correction does NOT do](#what-the-correction-does-not-do)
8. [The exact code](#the-exact-code)

---

## Why this exists

On an AMOLED display each pixel is its own light source. A pure black pixel (`#000000`) is physically off — it draws no power and emits no light. Any pixel with non-zero color values, even one that looks black to the human eye, keeps the corresponding subpixels on and draws power.

Many images — especially AI-generated wallpapers, dark photographs, and scans — contain large regions of "almost black" pixels like `#010101`, `#050403`, or `#0A0604`. Visually indistinguishable from black. Electrically, fully lit. Over the area of a full-screen wallpaper this adds up to measurable battery drain and slightly hazier black levels.

The AMOLED correction finds these pixels and snaps them to pure `#000000`.

---

## The core rule: what counts as near-black

A pixel is classified as near-black — and will be corrected — when **all three** of the following conditions are true simultaneously:

### Condition 1 — Red channel is at or below the threshold

```
R ≤ threshold
```

### Condition 2 — Green channel is at or below the threshold

```
G ≤ threshold
```

### Condition 3 — Blue channel is at or below the threshold

```
B ≤ threshold
```

### Plus — Condition 4: at least one channel must be non-zero

```
R > 0  OR  G > 0  OR  B > 0
```

This last condition excludes pure black (`#000000`) from the match. Pure black is already optimal, there is nothing to fix, and we never want to count it or mark it as "needing correction".

### Putting it together

A pixel `(R, G, B)` at threshold `T` is considered near-black if and only if:

```
(R ≤ T)  AND  (G ≤ T)  AND  (B ≤ T)  AND  (R > 0 OR G > 0 OR B > 0)
```

The alpha channel of the pixel is **ignored** — transparency has no effect on classification.

---

## Warm Tint Mode

Warm Tint Mode is an **additional filter** that runs on top of the near-black rule. It does not replace the core rule — it narrows it.

### What it adds

When Warm Tint Mode is **on**, a pixel is only corrected if — in addition to passing the near-black test above — it also satisfies:

```
(R − B) > 3
```

Note the **strict** greater-than. A pixel with exactly `R − B = 3` is **not** matched.

### Why this filter exists

AI-generated dark backgrounds often have a warm (reddish/brownish) tint. A pixel like `(8, 5, 3)` looks black-brown and wastes battery, exactly what we want to fix. But a neutral shadow in a real photograph — say `(12, 12, 12)` — also falls under the near-black rule at threshold 15. Correcting neutral shadows to pure black flattens facial features, destroys depth in dark clothing, and generally ruins detailed subjects.

Warm Tint Mode lets you say: *correct the brown/warm cast, leave the neutral shadows alone*.

The `R − B > 3` test works because:

- Neutral grays have `R ≈ G ≈ B` (difference around 0). Excluded.
- Warm darks have `R > B` by several units (`R − B` of 4, 5, 10, …). Included.
- Cool darks (blue-tinted shadows) have `R < B`, so `R − B` is negative. Excluded.

The cutoff of **3** is empirical: it's large enough to ignore compression noise and normal grayscale variation, small enough to catch genuine warm casts.

### What Warm Tint does NOT check

- It does not look at the Green channel. Only the red-minus-blue gradient matters.
- It does not care about the absolute color temperature of the image overall. It's a per-pixel test.
- It does not change the threshold. A pixel must still be at or below the threshold on all three channels first.

### Summary logic

```
near_black = (R ≤ T) AND (G ≤ T) AND (B ≤ T) AND (R > 0 OR G > 0 OR B > 0)

if Warm Tint Mode is ON:
    match = near_black AND (R − B > 3)
else:
    match = near_black
```

---

## Worked examples

All examples use a threshold of **10** unless otherwise noted.

### Example 1 — Pure black
**Pixel:** `(0, 0, 0)` → `#000000`
**Near-black?** No. Condition 4 fails: all three channels are zero.
**Result (normal mode):** Unchanged.
**Result (warm mode):** Unchanged.
**Why:** Already optimal. Never touched.

### Example 2 — Barely-lit gray
**Pixel:** `(1, 1, 1)` → `#010101`
**Near-black?** Yes. All channels ≤ 10, at least one > 0.
**Normal mode:** Corrected to `(0, 0, 0)`.
**Warm mode:** `R − B = 0`, not > 3. **Not corrected.**
**Why:** Perfectly neutral dark gray. Normal mode considers it waste. Warm mode leaves it, assuming it's intentional shadow detail.

### Example 3 — Warm near-black
**Pixel:** `(8, 5, 3)` → `#080503`
**Near-black?** Yes. All channels ≤ 10.
**Normal mode:** Corrected to `(0, 0, 0)`.
**Warm mode:** `R − B = 5`, which is > 3. **Corrected to `(0, 0, 0)`.**
**Why:** Textbook warm dark tint. Both modes catch it.

### Example 4 — Edge case, `R − B = 3`
**Pixel:** `(10, 5, 7)` → `#0A0507`
**Near-black?** Yes.
**Normal mode:** Corrected.
**Warm mode:** `R − B = 3`, but the test is **strictly greater than 3**. **Not corrected.**
**Why:** By design. The cutoff is exclusive to prevent borderline-neutral pixels from being treated as warm.

### Example 5 — Edge case, `R − B = 4`
**Pixel:** `(10, 5, 6)` → `#0A0506`
**Near-black?** Yes.
**Normal mode:** Corrected.
**Warm mode:** `R − B = 4`, which is > 3. **Corrected.**
**Why:** Just barely over the warm-tint cutoff.

### Example 6 — Cool near-black
**Pixel:** `(3, 5, 10)` → `#03050A`
**Near-black?** Yes.
**Normal mode:** Corrected.
**Warm mode:** `R − B = −7`. Not > 3. **Not corrected.**
**Why:** Blue-tinted dark. Warm mode doesn't consider this a warm cast, so it's preserved — assumed to be atmosphere or cool shadow.

### Example 7 — Red channel over threshold
**Pixel:** `(15, 5, 3)` → `#0F0503`
**Near-black?** No. Red channel 15 > threshold 10. Condition 1 fails.
**Normal mode:** Unchanged.
**Warm mode:** Unchanged — doesn't even reach the warm-tint test.
**Why:** Warm Tint Mode still requires the pixel to be near-black first. Warm pixels above threshold are considered real image content, not waste.

### Example 8 — Single bright channel
**Pixel:** `(5, 5, 50)` → `#050532`
**Near-black?** No. Blue channel 50 > threshold 10. Condition 3 fails.
**Normal mode:** Unchanged.
**Warm mode:** Unchanged.
**Why:** Even one channel over the threshold disqualifies the pixel. This is a dark blue, not near-black.

### Example 9 — Threshold exactly matches
**Pixel:** `(10, 10, 10)` at threshold 10 → `#0A0A0A`
**Near-black?** Yes. The comparison is `≤`, so threshold values are included.
**Normal mode:** Corrected.
**Warm mode:** `R − B = 0`. Not corrected.
**Why:** Threshold is inclusive. If you want to exclude this pixel, lower the threshold to 9.

### Example 10 — Mid-gray
**Pixel:** `(120, 120, 120)` → `#787878`
**Near-black?** No. All channels well above any reasonable threshold.
**Normal mode:** Unchanged.
**Warm mode:** Unchanged.
**Why:** Regular image content. Never a candidate.

---

## Analyze vs Apply

The editor exposes two operations that both use the rules above, but differ in what they produce.

### Analyze (🔍)

- Produces a **preview overlay** where matched pixels are colored pure red (`#FF0000`) and all other pixels are kept exactly as they were.
- Also reports a **count**: how many pixels matched.
- Does **not** modify your source image.
- Is purely inspection — you can analyze, tweak threshold and Warm Tint, analyze again, and your original bitmap never changes.
- The overlay is discarded automatically the moment you change the threshold or toggle Warm Tint, because the match set would no longer be valid.

### Apply (✅)

- Produces a **new bitmap** where matched pixels are replaced with pure black (`#000000`).
- Replaces the editor's working bitmap with the new one.
- Is **destructive for the session**: there is no undo. To start over, reload the image.
- Does not write to disk — the image is only saved when you tap **Save**.

Both operations use the exact same match criteria. What Analyze marks red is precisely what Apply would turn black.

---

## Choosing a threshold

The threshold is an integer from 0 to 50, exposed as a slider.

Rules of thumb based on what the pixel value space looks like:

- **0** — Nothing is ever corrected. Only pure black pixels already satisfy Condition 4's negation, and they're excluded anyway.
- **1 to 4** — Catches only the barely-lit pixels (`#010101` territory). Very safe. Useful for photographs where you only want to kill compression/encoding noise in near-black regions.
- **5 to 15** — Recommended for most content. Removes low-value dark noise without flattening intentional shadow detail. Combined with Warm Tint, this is the safe range for AI wallpapers with warm casts.
- **16 to 30** — Aggressive. Will start catching genuinely dark but intentional shadows in photographs (hair, dark clothing, shaded surfaces). Fine for abstract or heavily stylized wallpapers, risky for photos.
- **31 to 50** — Very aggressive. Treats anything dark as waste. Appropriate only when you want a fully AMOLED-friendly image and accept losing all shadow gradation.

The **higher** the threshold, the **more** pixels are matched. There is no adaptive behavior — the threshold is a hard cutoff.

---

## What the correction does NOT do

- It does not touch the **alpha** channel of any pixel. A transparent pixel with near-black color is still classified by its RGB values (alpha is ignored during classification) but its alpha value is preserved verbatim in the output.
- It does not look at **surrounding pixels**. There is no neighborhood analysis, no edge detection, no smoothing. Each pixel is evaluated independently.
- It does not correct **dark but non-black-like** colors. Dark red (`#400000`), dark navy (`#000040`), dark forest green (`#004000`) — none of these are matched at any threshold ≤ 50, because at least one channel exceeds the threshold.
- It does not round or approximate. The comparisons are exact integer tests on the 8-bit channel values.
- It does not **re-encode** your image. The bitmap stays ARGB_8888 throughout. The only compression happens during the final PNG save.
- It is **not** dithered. Matched pixels jump directly to pure black. If you zoom into a corrected region and see a hard edge between corrected and uncorrected pixels, that's expected — if you want a soft transition, lower the threshold.

---

## The exact code

The classification logic lives in `app/src/main/java/com/github/reygnn/chiaroscuro/imaging/AmoledTransform.kt` as a pure Kotlin function:

```kotlin
fun isNearBlack(argb: Int, threshold: Int, warmMode: Boolean): Boolean {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    val nearBlack = r <= threshold && g <= threshold && b <= threshold &&
        (r > 0 || g > 0 || b > 0)
    return if (warmMode) nearBlack && (r - b) > WARM_TINT_MIN_DELTA else nearBlack
}
```

`WARM_TINT_MIN_DELTA` is a constant set to `3`. Changing it in code changes the warm-tint cutoff for all future matches.

The function is pure (no Android dependencies, no state, no side effects) and is exercised by `AmoledTransformTest` with explicit coverage for every boundary case discussed in this document — threshold edges, warm-tint `R − B = 3` vs `R − B = 4`, pure black exclusion, and the alpha-ignored invariant.

If the behavior ever needs to change, this is the one function to edit. The rest of the pipeline — Analyze, Apply, the UI sliders, the settings — all route through it.

package com.github.reygnn.chiaroscuro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * App theme.
 *
 * Pulls the colour scheme directly from the system's dynamic-colour
 * source (Material You) — so chiaroscuro follows the same wallpaper-
 * derived tonal palette as every other Material 3 surface on the
 * device, and switches between light and dark with the system. With
 * `minSdk = 36` dynamic colour is always available; no baseline
 * fallback needed.
 *
 * Splash-theme XML carries its own light/night variants (see
 * `res/values/themes.xml` and `res/values-night/themes.xml`), so the
 * pre-Compose splash also follows the system theme. The canvas
 * surface inside the editor uses theme tokens (`surfaceContainer*`),
 * never hard-coded hex.
 */
@Composable
fun ImageEditorTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = if (isSystemInDarkTheme()) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

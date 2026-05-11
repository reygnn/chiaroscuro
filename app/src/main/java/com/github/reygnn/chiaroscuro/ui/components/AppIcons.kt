package com.github.reygnn.chiaroscuro.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Hand-defined [ImageVector]s used by the app.
 *
 * Previously the project depended on androidx.compose.material:material-icons-extended
 * which shipped ~11.000 icons into release DEX. We hand-roll the few we actually
 * need as plain vector paths. The enclosing `object` is itself lazily initialized
 * on first access, so no additional `by lazy` wrapping is needed.
 */
object AppIcons {

    val ArrowBack: ImageVector = ImageVector.Builder(
        name = "ArrowBack",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(20f, 11f)
        lineTo(7.83f, 11f)
        lineTo(13.42f, 5.41f)
        lineTo(12f, 4f)
        lineTo(4f, 12f)
        lineTo(12f, 20f)
        lineTo(13.41f, 18.59f)
        lineTo(7.83f, 13f)
        lineTo(20f, 13f)
        close()
    }.build()
}
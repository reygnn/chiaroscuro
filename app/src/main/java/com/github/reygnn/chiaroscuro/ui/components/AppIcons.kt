package com.github.reygnn.chiaroscuro.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Hand-defined [ImageVector]s used by the app.
 *
 * Previously the project depended on androidx.compose.material:material-icons-extended
 * which shipped ~11.000 icons into release DEX. We only use three, so they
 * live here as plain vector paths. The enclosing `object` is itself lazily
 * initialized on first access, so no additional `by lazy` wrapping is needed.
 */
object AppIcons {

    val FlashOn: ImageVector = ImageVector.Builder(
        name = "FlashOn",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
        moveTo(7f, 2f)
        lineTo(7f, 13f)
        lineTo(10f, 13f)
        lineTo(10f, 22f)
        lineTo(17f, 11f)
        lineTo(13f, 11f)
        lineTo(13f, 2f)
        close()
    }.build()

    val MoreVert: ImageVector = ImageVector.Builder(
        name = "MoreVert",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 8f)
        moveToRelative(-1.5f, 0f)
        arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 3f, 0f)
        arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, -3f, 0f)
    }.path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 12f)
        moveToRelative(-1.5f, 0f)
        arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 3f, 0f)
        arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, -3f, 0f)
    }.path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 16f)
        moveToRelative(-1.5f, 0f)
        arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 3f, 0f)
        arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, -3f, 0f)
    }.build()

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
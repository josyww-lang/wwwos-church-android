package org.yourchurch.app.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private fun lineIcon(name: String, draw: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = draw
        )
    }.build()

/** A small church with a cross on top: the Church tab. */
val ChurchIcon: ImageVector by lazy {
    lineIcon("Church") {
        moveTo(3f, 21f); horizontalLineTo(21f)
        moveTo(10f, 21f); verticalLineTo(17.5f)
        quadTo(10f, 15.5f, 12f, 15.5f); quadTo(14f, 15.5f, 14f, 17.5f); verticalLineTo(21f)
        moveTo(12f, 3f); verticalLineTo(10f)
        moveTo(9.5f, 6f); horizontalLineTo(14.5f)
        moveTo(6f, 21f); verticalLineTo(14f)
        moveTo(4f, 16f); lineTo(12f, 12f); lineTo(20f, 16f)
        moveTo(18f, 14f); verticalLineTo(21f)
    }
}

/** A round speech bubble: the Chat tab. */
val ChatCircleIcon: ImageVector by lazy {
    lineIcon("ChatCircle") {
        moveTo(3f, 20f)
        lineTo(4.3f, 16.1f)
        arcTo(9f, 8f, 0f, true, true, 7.7f, 19f)
        lineTo(3f, 20f)
    }
}

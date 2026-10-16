package org.yourchurch.app.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A simple cross, used for prayer. */
val CrossIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Cross",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(10f, 2f)
            horizontalLineTo(14f)
            verticalLineTo(8f)
            horizontalLineTo(20f)
            verticalLineTo(12f)
            horizontalLineTo(14f)
            verticalLineTo(22f)
            horizontalLineTo(10f)
            verticalLineTo(12f)
            horizontalLineTo(4f)
            verticalLineTo(8f)
            horizontalLineTo(10f)
            close()
        }
    }.build()
}

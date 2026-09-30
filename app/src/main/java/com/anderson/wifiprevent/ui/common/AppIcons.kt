package com.anderson.wifiprevent.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AppIcons {
    val Connection: ImageVector = ImageVector.Builder("Connection", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(3f, 9f); curveTo(8f, 4.5f, 16f, 4.5f, 21f, 9f)
            moveTo(6f, 12f); curveTo(9.5f, 9f, 14.5f, 9f, 18f, 12f)
            moveTo(9f, 15f); curveTo(11f, 13.5f, 13f, 13.5f, 15f, 15f)
            moveTo(12f, 18f); lineTo(12f, 18.1f)
        }
    }.build()
    val Analysis: ImageVector = ImageVector.Builder("Analysis", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(12f, 3f); lineTo(20f, 6f); lineTo(20f, 12f)
            curveTo(20f, 17f, 15f, 20f, 12f, 21f)
            curveTo(9f, 20f, 4f, 17f, 4f, 12f); lineTo(4f, 6f); close()
            moveTo(8f, 12f); lineTo(11f, 15f); lineTo(16f, 9f)
        }
    }.build()
    val History: ImageVector = ImageVector.Builder("History", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(4f, 7f); curveTo(8f, 0f, 21f, 3f, 21f, 12f)
            curveTo(21f, 21f, 8f, 24f, 4f, 17f)
            moveTo(4f, 3f); lineTo(4f, 8f); lineTo(9f, 8f)
            moveTo(12f, 7f); lineTo(12f, 12f); lineTo(15.5f, 14f)
        }
    }.build()
}

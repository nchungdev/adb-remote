package app.duinch.adbagent.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.unit.dp

/**
 * Consistent 24dp / 2dp-stroke geometric vector icon set (Lucide-inspired).
 * Eliminates emoji glyphs in UI controls and avoids 25MB material-icons-extended bloat.
 */
object AppIcons {

    private inline fun strokeIcon(
        name: String,
        block: PathBuilder.() -> Unit
    ): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        )
        val pathNodes = PathBuilder().apply(block).nodes
        builder.addPath(
            pathData = pathNodes,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
        return builder.build()
    }

    val ShieldBolt: ImageVector by lazy {
        strokeIcon("ShieldBolt") {
            moveTo(12f, 2f)
            lineTo(19f, 5f)
            verticalLineTo(11f)
            curveTo(19f, 16f, 15.8f, 20.3f, 12f, 22f)
            curveTo(8.2f, 20.3f, 5f, 16f, 5f, 11f)
            verticalLineTo(5f)
            close()
            moveTo(13f, 7f)
            lineTo(10f, 12f)
            horizontalLineTo(14f)
            lineTo(11f, 17f)
        }
    }

    val LinkWireless: ImageVector by lazy {
        strokeIcon("LinkWireless") {
            moveTo(5f, 9f)
            curveTo(8.9f, 5.1f, 15.1f, 5.1f, 19f, 9f)
            moveTo(8.5f, 12.5f)
            curveTo(10.4f, 10.6f, 13.6f, 10.6f, 15.5f, 12.5f)
            moveTo(12f, 16f)
            horizontalLineTo(12.02f)
            moveTo(7f, 19f)
            horizontalLineTo(17f)
        }
    }

    val Sliders: ImageVector by lazy {
        strokeIcon("Sliders") {
            moveTo(4f, 6f); horizontalLineTo(20f)
            moveTo(4f, 12f); horizontalLineTo(20f)
            moveTo(4f, 18f); horizontalLineTo(20f)
            moveTo(9f, 4f); verticalLineTo(8f)
            moveTo(15f, 10f); verticalLineTo(14f)
            moveTo(11f, 16f); verticalLineTo(20f)
        }
    }

    val Terminal: ImageVector by lazy {
        strokeIcon("Terminal") {
            moveTo(4f, 5f)
            horizontalLineTo(20f)
            verticalLineTo(19f)
            horizontalLineTo(4f)
            close()
            moveTo(7f, 9f)
            lineTo(10f, 12f)
            lineTo(7f, 15f)
            moveTo(12f, 15f)
            horizontalLineTo(16f)
        }
    }

    val CloudLock: ImageVector by lazy {
        strokeIcon("CloudLock") {
            moveTo(6.5f, 18f)
            horizontalLineTo(17.5f)
            curveTo(19.4f, 18f, 21f, 16.4f, 21f, 14.5f)
            curveTo(21f, 12.7f, 19.6f, 11.2f, 17.8f, 11f)
            curveTo(17.1f, 7.6f, 14.1f, 5f, 10.5f, 5f)
            curveTo(6.6f, 5f, 3.5f, 8.1f, 3.5f, 12f)
            curveTo(3.5f, 15.3f, 4.8f, 18f, 6.5f, 18f)
            moveTo(10f, 13f)
            horizontalLineTo(14f)
            verticalLineTo(16f)
            horizontalLineTo(10f)
            close()
        }
    }

    val CheckCircle: ImageVector by lazy {
        strokeIcon("CheckCircle") {
            moveTo(21f, 12f)
            curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
            curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f)
            curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
            curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f)
            moveTo(8.5f, 12.5f)
            lineTo(11f, 15f)
            lineTo(16f, 9.5f)
        }
    }

    val AlertCircle: ImageVector by lazy {
        strokeIcon("AlertCircle") {
            moveTo(21f, 12f)
            curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
            curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f)
            curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
            curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f)
            moveTo(12f, 8f)
            verticalLineTo(12.5f)
            moveTo(12f, 16f)
            horizontalLineTo(12.02f)
        }
    }

    val Download: ImageVector by lazy {
        strokeIcon("Download") {
            moveTo(12f, 4f)
            verticalLineTo(15f)
            moveTo(8f, 11f)
            lineTo(12f, 15f)
            lineTo(16f, 11f)
            moveTo(5f, 19f)
            horizontalLineTo(19f)
        }
    }

    val Refresh: ImageVector by lazy {
        strokeIcon("Refresh") {
            moveTo(20f, 11f)
            curveTo(19.5f, 6.8f, 16f, 3.5f, 11.7f, 3.5f)
            curveTo(7.2f, 3.5f, 3.5f, 7.2f, 3.5f, 11.7f)
            curveTo(3.5f, 16.2f, 7.2f, 20f, 11.7f, 20f)
            curveTo(15.2f, 20f, 18.2f, 17.8f, 19.4f, 14.7f)
            moveTo(20f, 6f)
            verticalLineTo(11f)
            horizontalLineTo(15f)
        }
    }

    val ChevronDown: ImageVector by lazy {
        strokeIcon("ChevronDown") {
            moveTo(6f, 9f)
            lineTo(12f, 15f)
            lineTo(18f, 9f)
        }
    }

    val ChevronRight: ImageVector by lazy {
        strokeIcon("ChevronRight") {
            moveTo(9f, 6f)
            lineTo(15f, 12f)
            lineTo(9f, 18f)
        }
    }

    val Clock: ImageVector by lazy {
        strokeIcon("Clock") {
            moveTo(21f, 12f)
            curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
            curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f)
            curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
            curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f)
            moveTo(12f, 7f)
            verticalLineTo(12f)
            lineTo(15.5f, 14f)
        }
    }

    val Usb: ImageVector by lazy {
        strokeIcon("Usb") {
            moveTo(12f, 2f); verticalLineTo(12f)
            moveTo(8f, 6f); horizontalLineTo(16f); verticalLineTo(12f); horizontalLineTo(8f); close()
            moveTo(10f, 2f); verticalLineTo(6f)
            moveTo(14f, 2f); verticalLineTo(6f)
            moveTo(12f, 12f); verticalLineTo(20f)
            moveTo(9f, 20f); horizontalLineTo(15f)
        }
    }

    val Copy: ImageVector by lazy {
        strokeIcon("Copy") {
            moveTo(9f, 9f)
            horizontalLineTo(19f)
            verticalLineTo(19f)
            horizontalLineTo(9f)
            close()
            moveTo(5f, 15f)
            horizontalLineTo(4f)
            verticalLineTo(5f)
            horizontalLineTo(14f)
            verticalLineTo(6f)
        }
    }

    val MoreVertical: ImageVector by lazy {
        strokeIcon("MoreVertical") {
            moveTo(12f, 5f); horizontalLineTo(12.05f)
            moveTo(12f, 12f); horizontalLineTo(12.05f)
            moveTo(12f, 19f); horizontalLineTo(12.05f)
        }
    }
}

package com.example.minutesreader.ui

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/** material-icons-extended（大きい）を入れずに済むよう、必要なアイコンだけ定義。 */
object PlayerIcons {
    val Pause: ImageVector by lazy {
        materialIcon(name = "Filled.Pause") {
            materialPath {
                moveTo(6f, 19f); horizontalLineTo(10f); verticalLineTo(5f); horizontalLineTo(6f); close()
                moveTo(14f, 5f); verticalLineTo(19f); horizontalLineTo(18f); verticalLineTo(5f); close()
            }
        }
    }
    val SkipPrevious: ImageVector by lazy {
        materialIcon(name = "Filled.SkipPrevious") {
            materialPath {
                moveTo(6f, 6f); horizontalLineTo(8f); verticalLineTo(18f); horizontalLineTo(6f); close()
                moveTo(9.5f, 12f); lineTo(18f, 18f); verticalLineTo(6f); close()
            }
        }
    }
    val SkipNext: ImageVector by lazy {
        materialIcon(name = "Filled.SkipNext") {
            materialPath {
                moveTo(6f, 18f); lineTo(14.5f, 12f); lineTo(6f, 6f); close()
                moveTo(16f, 6f); verticalLineTo(18f); horizontalLineTo(18f); verticalLineTo(6f); close()
            }
        }
    }
    val Remove: ImageVector by lazy {
        materialIcon(name = "Filled.Remove") {
            materialPath {
                moveTo(19f, 13f); horizontalLineTo(5f); verticalLineTo(11f); horizontalLineTo(19f); close()
            }
        }
    }
    val Headphones: ImageVector by lazy {
        materialIcon(name = "Filled.Headphones") {
            materialPath {
                moveTo(12f, 3f)
                curveTo(7.03f, 3f, 3f, 7.03f, 3f, 12f)
                verticalLineTo(19f)
                curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
                horizontalLineTo(8f)
                verticalLineTo(13f)
                horizontalLineTo(5f)
                verticalLineTo(12f)
                curveTo(5f, 8.13f, 8.13f, 5f, 12f, 5f)
                reflectiveCurveTo(19f, 8.13f, 19f, 12f)
                verticalLineTo(13f)
                horizontalLineTo(16f)
                verticalLineTo(21f)
                horizontalLineTo(19f)
                curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
                verticalLineTo(12f)
                curveTo(21f, 7.03f, 16.97f, 3f, 12f, 3f)
                close()
            }
        }
    }
}

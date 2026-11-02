package app.duinch.adbagent.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.duinch.adbagent.R

@Immutable
data class AdbDimens(
    val spacingNone: Dp = 0.dp,
    val spacingXxs: Dp = 2.dp,
    val spacingXs: Dp = 4.dp,
    val spacingS: Dp = 8.dp,
    val spacingM: Dp = 12.dp,
    val spacingMd: Dp = 14.dp,
    val spacingL: Dp = 16.dp,
    val spacingXl: Dp = 20.dp,
    val spacingXxl: Dp = 24.dp,
    val spacingXxxl: Dp = 32.dp,
    val radiusSmall: Dp = 10.dp,
    val radiusMedium: Dp = 14.dp,
    val radiusLarge: Dp = 16.dp,
    val radiusCard: Dp = 20.dp,
    val radiusPill: Dp = 9999.dp,
    val btnHeightNormal: Dp = 48.dp,
    val btnHeightPrimary: Dp = 52.dp,
    val iconSizeS: Dp = 16.dp,
    val iconSizeM: Dp = 20.dp,
    val iconSizeL: Dp = 24.dp,
    val iconSizeXl: Dp = 32.dp,
    val cardGifHeight: Dp = 340.dp
)

val LocalAdbDimens = staticCompositionLocalOf { AdbDimens() }

@Composable
fun rememberAdbDimens(): AdbDimens {
    return AdbDimens(
        spacingNone = dimensionResource(R.dimen.spacing_none),
        spacingXxs = dimensionResource(R.dimen.spacing_xxs),
        spacingXs = dimensionResource(R.dimen.spacing_xs),
        spacingS = dimensionResource(R.dimen.spacing_s),
        spacingM = dimensionResource(R.dimen.spacing_m),
        spacingMd = dimensionResource(R.dimen.spacing_md),
        spacingL = dimensionResource(R.dimen.spacing_l),
        spacingXl = dimensionResource(R.dimen.spacing_xl),
        spacingXxl = dimensionResource(R.dimen.spacing_xxl),
        spacingXxxl = dimensionResource(R.dimen.spacing_xxxl),
        radiusSmall = dimensionResource(R.dimen.radius_small),
        radiusMedium = dimensionResource(R.dimen.radius_medium),
        radiusLarge = dimensionResource(R.dimen.radius_large),
        radiusCard = dimensionResource(R.dimen.radius_card),
        radiusPill = dimensionResource(R.dimen.radius_pill),
        btnHeightNormal = dimensionResource(R.dimen.btn_height_normal),
        btnHeightPrimary = dimensionResource(R.dimen.btn_height_primary),
        iconSizeS = dimensionResource(R.dimen.icon_size_s),
        iconSizeM = dimensionResource(R.dimen.icon_size_m),
        iconSizeL = dimensionResource(R.dimen.icon_size_l),
        iconSizeXl = dimensionResource(R.dimen.icon_size_xl),
        cardGifHeight = dimensionResource(R.dimen.card_gif_height)
    )
}

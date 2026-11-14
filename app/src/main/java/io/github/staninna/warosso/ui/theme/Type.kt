package io.github.staninna.warosso.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.staninna.warosso.R

/** Google Sans Flex (OFL 1.1) with its roundness axis turned all the way up. */
@OptIn(ExperimentalTextApi::class)
private val GoogleSansFlex = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold).map { weight ->
        Font(
            resId = R.font.google_sans_flex,
            weight = weight,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight.weight),
                FontVariation.width(100f),
                FontVariation.Setting("ROND", 100f),
            ),
        )
    },
)

private fun style(sizeSp: Int, lineHeightSp: Int, weight: FontWeight, letterSpacingSp: Double = 0.0) = TextStyle(
    fontFamily = GoogleSansFlex,
    fontWeight = weight,
    fontSize = sizeSp.sp,
    lineHeight = lineHeightSp.sp,
    letterSpacing = letterSpacingSp.sp,
)

/** The Material type scale, heavier than stock so text holds up in daylight. */
val WarOssoTypography = Typography(
    displayLarge = style(57, 64, FontWeight.Bold, -0.25),
    displayMedium = style(45, 52, FontWeight.Bold),
    displaySmall = style(36, 44, FontWeight.Bold),
    headlineLarge = style(32, 40, FontWeight.Bold),
    headlineMedium = style(28, 36, FontWeight.Bold),
    headlineSmall = style(24, 32, FontWeight.Bold),
    titleLarge = style(22, 28, FontWeight.SemiBold),
    titleMedium = style(16, 24, FontWeight.SemiBold, 0.15),
    titleSmall = style(14, 20, FontWeight.Medium, 0.1),
    bodyLarge = style(16, 24, FontWeight.Normal, 0.5),
    bodyMedium = style(14, 20, FontWeight.Normal, 0.25),
    bodySmall = style(12, 16, FontWeight.Normal, 0.4),
    labelLarge = style(14, 20, FontWeight.SemiBold, 0.1),
    labelMedium = style(12, 16, FontWeight.SemiBold, 0.5),
    labelSmall = style(11, 16, FontWeight.SemiBold, 0.5),
)

/** The house number, as large as the screen allows. Tabular digits keep it from jumping while typing. */
val HouseNumberStyle = TextStyle(
    fontFamily = GoogleSansFlex,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 112.sp,
    lineHeight = 120.sp,
    letterSpacing = (-2).sp,
    fontFeatureSettings = "tnum",
)

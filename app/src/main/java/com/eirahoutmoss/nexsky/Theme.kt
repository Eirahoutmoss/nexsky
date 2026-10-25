package com.eirahoutmoss.nexsky

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- Palette (from the brand concept: deep navy, blue accent, signal rainbow)
val Bg = Color(0xFF070C1C)
val Bg2 = Color(0xFF0C1430)
val PanelColor = Color(0xFF0F1B2E)
val PanelHi = Color(0xFF15243B)
val Line = Color(0xFF1F3150)
val Muted = Color(0xFF8DA0B8)
val TextHi = Color(0xFFF2F6FB)
val Accent = Color(0xFF1E7BFF)
val Accent2 = Color(0xFF38C8FF)
val Good = Color(0xFF2FD18C)
val Warn = Color(0xFFF5A524)
val Danger = Color(0xFFFF4D4F)

val AccentBrush = Brush.horizontalGradient(listOf(Accent, Accent2))

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
)

private fun Typography.withInter(): Typography = Typography(
    displayLarge = displayLarge.copy(fontFamily = Inter), displayMedium = displayMedium.copy(fontFamily = Inter),
    displaySmall = displaySmall.copy(fontFamily = Inter), headlineLarge = headlineLarge.copy(fontFamily = Inter),
    headlineMedium = headlineMedium.copy(fontFamily = Inter), headlineSmall = headlineSmall.copy(fontFamily = Inter),
    titleLarge = titleLarge.copy(fontFamily = Inter), titleMedium = titleMedium.copy(fontFamily = Inter),
    titleSmall = titleSmall.copy(fontFamily = Inter), bodyLarge = bodyLarge.copy(fontFamily = Inter),
    bodyMedium = bodyMedium.copy(fontFamily = Inter), bodySmall = bodySmall.copy(fontFamily = Inter),
    labelLarge = labelLarge.copy(fontFamily = Inter), labelMedium = labelMedium.copy(fontFamily = Inter),
    labelSmall = labelSmall.copy(fontFamily = Inter),
)

@Composable
fun NexTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = Color.White, secondary = Accent2,
            background = Bg, surface = PanelColor, surfaceVariant = PanelHi, onSurface = TextHi,
            outline = Line, secondaryContainer = Color(0xFF13305A), onSecondaryContainer = TextHi,
        ),
        typography = Typography().withInter(),
        content = content,
    )
}

/** "Nex" in white, "Sky" in the blue gradient. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
@Composable
fun Wordmark(size: TextUnit = 20.sp) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = TextHi)) { append("Nex") }
            withStyle(SpanStyle(brush = AccentBrush)) { append("Sky") }
        },
        style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = size, letterSpacing = (-0.5).sp),
    )
}

@Composable
fun TopBrandBar(trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp, 16.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.nexsky_logo), contentDescription = null, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(10.dp))
        Wordmark(20.sp)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** A rounded navy card. */
@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(PanelHi, PanelColor)), RoundedCornerShape(18.dp))
            .border(1.dp, Line, RoundedCornerShape(18.dp))
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String) {
    Text(text.uppercase(), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
}

fun directionName(deg: Double): String = dirName(deg)

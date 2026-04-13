package com.example.tricount.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.tricount.R

// ─────────────────────────────────────────────────────────────────────────────
// Lato — bundled in res/font/
//   lato_regular.ttf  →  FontWeight.Normal  (400)
//   lato_bold.ttf     →  FontWeight.Bold    (700)
//
// Place both .ttf files in app/src/main/res/font/ and add matching
// entries to R (Android Studio does this automatically on sync):
//   R.font.lato_regular
//   R.font.lato_bold
// ─────────────────────────────────────────────────────────────────────────────

val Lato = FontFamily(
    Font(resId = R.font.lato_regular, weight = FontWeight.Normal),
    Font(resId = R.font.lato_bold,    weight = FontWeight.Bold),
)

// ─────────────────────────────────────────────────────────────────────────────
// Typography scale
// Weights are clamped to Normal / Bold because only those two cuts are
// bundled. Styles that previously used SemiBold, Medium, or ExtraBold
// fall back to the nearest available cut automatically (Bold for ≥ 600,
// Normal for everything below). If you later add lato_light.ttf etc.,
// just register the extra Font() entries above and restore the weights.
// ─────────────────────────────────────────────────────────────────────────────

val Typography = Typography(

    displayLarge = TextStyle(
        fontFamily    = Lato,
        fontWeight    = FontWeight.Normal,
        fontSize      = 57.sp,
        lineHeight    = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily    = Lato,
        fontWeight    = FontWeight.Normal,
        fontSize      = 45.sp,
        lineHeight    = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily    = Lato,
        fontWeight    = FontWeight.Normal,
        fontSize      = 36.sp,
        lineHeight    = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily    = Lato,
        fontWeight    = FontWeight.Bold,
        fontSize      = 32.sp,
        lineHeight    = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily    = Lato,
        fontWeight    = FontWeight.Bold,
        fontSize      = 28.sp,
        lineHeight    = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily    = Lato,
        fontWeight    = FontWeight.Bold,
        fontSize      = 24.sp,
        lineHeight    = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily    = Lato,
        fontWeight    = FontWeight.Bold,
        fontSize      = 22.sp,
        lineHeight    = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily    = Lato,
        fontWeight    = FontWeight.Bold,
        fontSize      = 16.sp,
        lineHeight    = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily    = Lato,
        fontWeight    = FontWeight.Normal,
        fontSize      = 14.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily    = Lato,
        fontWeight    = FontWeight.Normal,
        fontSize      = 16.sp,
        lineHeight    = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily    = Lato,
        fontWeight    = FontWeight.Normal,
        fontSize      = 14.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily    = Lato,
        fontWeight    = FontWeight.Normal,
        fontSize      = 12.sp,
        lineHeight    = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily    = Lato,
        fontWeight    = FontWeight.Bold,
        fontSize      = 14.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily    = Lato,
        fontWeight    = FontWeight.Normal,
        fontSize      = 12.sp,
        lineHeight    = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily    = Lato,
        fontWeight    = FontWeight.Normal,
        fontSize      = 11.sp,
        lineHeight    = 16.sp,
        letterSpacing = 0.5.sp
    ),
)
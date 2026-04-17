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
// ─────────────────────────────────────────────────────────────────────────────

val Lato = FontFamily(
    Font(resId = R.font.lato_regular, weight = FontWeight.Normal),
    Font(resId = R.font.lato_bold,    weight = FontWeight.Bold),
)

// ─────────────────────────────────────────────────────────────────────────────
// Typography scale
//
// NO colors are set here — colors always come from MaterialTheme.colorScheme
// at the call site so they adapt automatically to light/dark mode.
//
// Heading weights: Bold (≥ headline level)
// Body / label weights: Normal, except labelLarge which is Bold
// ─────────────────────────────────────────────────────────────────────────────

val Typography = Typography(

    // ── Display / H1–H3 ──────────────────────────────────────────────────────

    displayLarge = TextStyle(          // H1 — hero titles
        fontFamily    = Lato,
        fontWeight    = FontWeight.Bold,
        fontSize      = 57.sp,
        lineHeight    = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(         // H2 — large section openers
        fontFamily    = Lato,
        fontWeight    = FontWeight.Bold,
        fontSize      = 45.sp,
        lineHeight    = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(          // H3 — page/card primary titles
        fontFamily    = Lato,
        fontWeight    = FontWeight.Bold,
        fontSize      = 36.sp,
        lineHeight    = 44.sp,
        letterSpacing = 0.sp
    ),

    // ── Headline / H4–H5 ─────────────────────────────────────────────────────

    headlineLarge = TextStyle(         // H4 — section headers inside a screen
        fontFamily    = Lato,
        fontWeight    = FontWeight.Bold,
        fontSize      = 32.sp,
        lineHeight    = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(        // H4b
        fontFamily    = Lato,
        fontWeight    = FontWeight.Bold,
        fontSize      = 28.sp,
        lineHeight    = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(         // H5 — card/sheet headers
        fontFamily    = Lato,
        fontWeight    = FontWeight.Bold,
        fontSize      = 24.sp,
        lineHeight    = 32.sp,
        letterSpacing = 0.sp
    ),

    // ── Title / H6 + prominent labels ────────────────────────────────────────

    titleLarge = TextStyle(            // H6 — top-app-bar title, dialog title
        fontFamily    = Lato,
        fontWeight    = FontWeight.Bold,
        fontSize      = 22.sp,
        lineHeight    = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(           // menu item titles, list-row primary text
        fontFamily    = Lato,
        fontWeight    = FontWeight.Bold,
        fontSize      = 16.sp,
        lineHeight    = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(            // secondary list text, sub-labels
        fontFamily    = Lato,
        fontWeight    = FontWeight.Normal,
        fontSize      = 14.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.1.sp
    ),

    // ── Body ─────────────────────────────────────────────────────────────────

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

    // ── Label / chips / badges ────────────────────────────────────────────────

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
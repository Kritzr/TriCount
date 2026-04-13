package com.example.tricount.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// Microsoft Teams — Violet / Indigo primary palette
// ─────────────────────────────────────────────────────────────────────────────

val TeamsViolet900  = Color(0xFF3D2F81)   // deep brand violet  — dark primary / light primaryContainer text
val TeamsViolet800  = Color(0xFF4A3A9A)
val TeamsViolet700  = Color(0xFF5C4EB5)   // core brand — light primary
val TeamsViolet600  = Color(0xFF6B5EC4)
val TeamsViolet400  = Color(0xFF7382FF)   // accent — FABs, highlights  (#7382ff from your palette)
val TeamsViolet200  = Color(0xFFB182FF)   // soft accent — dark primary / A100 equivalent
val TeamsViolet50   = Color(0xFFE8E5F7)   // very pale violet — light primaryContainer

// ─────────────────────────────────────────────────────────────────────────────
// Teams Blue / Cyan — secondary, tertiary & highlights
// ─────────────────────────────────────────────────────────────────────────────

val TeamsBlue500    = Color(0xFF4530A8)   // electric teal-blue — secondary accent / "Live" badge
val TeamsBlueMid    = Color(0xFF4E5FBF)   // medium periwinkle  — #4E5FBF from your palette
val TeamsBlueSlate  = Color(0xFF5864A6)   // muted slate-blue   — #5864A6 from your palette

// ─────────────────────────────────────────────────────────────────────────────
// Neutral — surface & on-surface tokens
// ─────────────────────────────────────────────────────────────────────────────

val TeamsNeutral50  = Color(0xFFF2F2F2)   // near-white surface  — #F2F2F2 from your palette
val TeamsNeutral100 = Color(0xFFE0E0E0)
val TeamsNeutral200 = Color(0xFFC8C8C8)

// ─────────────────────────────────────────────────────────────────────────────
// Status — Rose / Busy indicator
// ─────────────────────────────────────────────────────────────────────────────

val TeamsRose       = Color(0xFFD18088)   // #D18088 — "Busy" / soft red status

// ─────────────────────────────────────────────────────────────────────────────
// Semantic surface tokens
// ─────────────────────────────────────────────────────────────────────────────

// Light — pale violet card surface with deep violet text
val ListSurfaceLight     = Color(0xFFF0EEF9)   // very pale violet (lighter than TeamsViolet50)
val ListBoundaryLight    = TeamsBlueMid        // #4E5FBF — periwinkle border on light
val ListTextLight        = TeamsViolet900      // #3D2F81 — deep violet, high contrast

// Dark — deep navy-violet elevated surface with light violet text
// ── List surface (Individual Balances rows) ──
val ListSurfaceDark      = Color(0xFF1C1C1C)
val ListBoundaryDark     = Color(0xFF2E2E2E)   // very subtle divider
val ListTextDark         = Color(0xFFE0E0E0)

// ─────────────────────────────────────────────────────────────────────────────
// Light Theme
// ─────────────────────────────────────────────────────────────────────────────

val md_theme_light_primary              = TeamsViolet700        // #5C4EB5 — core brand
val md_theme_light_onPrimary            = Color.White
val md_theme_light_primaryContainer     = TeamsViolet50         // #E8E5F7
val md_theme_light_onPrimaryContainer   = TeamsViolet900        // #3D2F81

val md_theme_light_secondary            = TeamsBlue500          // #25C3E6 — teal-blue accent
val md_theme_light_onSecondary          = Color.White
val md_theme_light_secondaryContainer   = Color(0xFFDDF5FA)     // very pale cyan
val md_theme_light_onSecondaryContainer = Color(0xFF003B47)

val md_theme_light_tertiary             = TeamsBlueMid          // #4E5FBF — periwinkle
val md_theme_light_onTertiary           = Color.White
val md_theme_light_tertiaryContainer    = TeamsViolet50         // #E8E5F7
val md_theme_light_onTertiaryContainer  = TeamsViolet900

val md_theme_light_error                = Color(0xFFBA1A1A)
val md_theme_light_errorContainer       = Color(0xFFFFDAD6)
val md_theme_light_onError              = Color.White
val md_theme_light_onErrorContainer     = Color(0xFF410002)

val md_theme_light_background           = Color(161618)
val md_theme_light_onBackground         = TeamsViolet900        // #3D2F81

val md_theme_light_surface              = Color(0xFFF0F0F5)
val md_theme_light_onSurface            = TeamsViolet900        // #3D2F81
val md_theme_light_surfaceVariant       =Color(0xFFF5F5F8)  // tab bar background
val md_theme_light_onSurfaceVariant     = TeamsBlueSlate        // #5864A6

val md_theme_light_outline              = TeamsBlueMid          // #4E5FBF
val md_theme_light_outlineVariant       = ListBoundaryLight     // #4E5FBF
val md_theme_light_inverseOnSurface     = TeamsViolet50
val md_theme_light_inverseSurface       = TeamsViolet900
val md_theme_light_inversePrimary       = TeamsViolet200        // #B182FF
val md_theme_light_surfaceTint          = TeamsBlueMid
val md_theme_light_scrim                = Color(0xFF000000)

val md_theme_light_surfaceContainerLowest  = Color(0xFFFFFFFF)
val md_theme_light_surfaceContainerLow     = Color(0xFFF3F3F6)   // barely-there violet
val md_theme_light_surfaceContainer        =Color(0xFFF5F5F8) // tab bar background
val md_theme_light_surfaceContainerHigh    = Color(0xFFE8E8EE)  // #E8E5F7
val md_theme_light_surfaceContainerHighest = Color(0xFFDDDAF5)   // deeper violet tint

// ─────────────────────────────────────────────────────────────────────────────
// Dark Theme — #1C1C1C base, violet accent palette
// ─────────────────────────────────────────────────────────────────────────────

val md_theme_dark_primary               = TeamsViolet200        // #B182FF — desaturated accent
val md_theme_dark_onPrimary             = TeamsViolet900        // #3D2F81
val md_theme_dark_primaryContainer      = Color(0xFF1C1C1C)   // was #3A3060 (blue-purple)
val md_theme_dark_onPrimaryContainer    = Color(0xFFE3E3E3)   // light readable text
val md_theme_dark_secondary             = TeamsBlue500          // #25C3E6 — teal on dark
val md_theme_dark_onSecondary           = Color(0xFF002B35)
val md_theme_dark_secondaryContainer    = Color(0xFF1C1C1C)   // was #004D5E
val md_theme_dark_onSecondaryContainer  = Color(0xFFE3E3E3)

val md_theme_dark_tertiary              = TeamsBlueMid          // #4E5FBF
val md_theme_dark_onTertiary            = Color.White
val md_theme_dark_tertiaryContainer     = Color(0xFF1C1C1C)   // was TeamsBlueSlate
val md_theme_dark_onTertiaryContainer   = Color(0xFFE3E3E3)


val md_theme_dark_error                 = Color(0xFFFFB4AB)
val md_theme_dark_errorContainer        = Color(0xFF93000A)
val md_theme_dark_onError               = Color(0xFF690005)
val md_theme_dark_onErrorContainer      = Color(0xFFFFDAD6)

val md_theme_dark_background            = Color(0xFF000000)   // pure black

val md_theme_dark_onBackground          = Color(0xFFE3E3E3)      // #B182FF — soft violet on dark

val md_theme_dark_surface               = Color(0xFF000000)   // pure black
val md_theme_dark_onSurface              = Color(0xFFE3E3E3)
val md_theme_dark_surfaceVariant        = Color(0xFF1C1C1C)   // #1A1530 — deep violet-navy list bg
val md_theme_dark_onSurfaceVariant      = TeamsViolet400        // #7382FF — accent violet

val md_theme_dark_outline               = TeamsViolet400        // #7382FF
val md_theme_dark_outlineVariant        = TeamsViolet900        // #3D2F81
val md_theme_dark_inverseOnSurface      = Color(0xFF1C1B1F)
val md_theme_dark_inverseSurface        = Color(0xFFE8E5F7)
val md_theme_dark_inversePrimary        = TeamsViolet700
val md_theme_dark_surfaceTint           = TeamsBlueMid
val md_theme_dark_scrim                 = Color(0xFF000000)

val md_theme_dark_surfaceContainerLowest  = Color(0xFF000000)
val md_theme_dark_surfaceContainerLow     = Color(0xFF0A0A0A)
val md_theme_dark_surfaceContainer        = Color(0xFF1C1C1C)
val md_theme_dark_surfaceContainerHigh    = Color(0xFF232323)
val md_theme_dark_surfaceContainerHighest = Color(0xFF2A2A2A)
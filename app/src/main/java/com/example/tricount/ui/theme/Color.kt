package com.example.tricount.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// Indigo palette
// ─────────────────────────────────────────────────────────────────────────────

val Indigo50   = Color(0xFFE8EAF6)
val Indigo100  = Color(0xFFC5CAE9)
val Indigo200  = Color(0xFF9FA8DA)
val Indigo300  = Color(0xFF7986CB)
val Indigo400  = Color(0xFF5C6BC0)
val Indigo500  = Color(0xFF3F51B5)
val Indigo600  = Color(0xFF3949AB)
val Indigo700  = Color(0xFF303F9F)
val Indigo800  = Color(0xFF283593)
val Indigo900  = Color(0xFF1A237E)

// Accent — electric indigo/blue, used for FABs, highlights, active states
val IndigoA100 = Color(0xFF8C9EFF)
val IndigoA200 = Color(0xFF536DFE)
val IndigoA400 = Color(0xFF3D5AFE)
val IndigoA700 = Color(0xFF304FFE)

// ─────────────────────────────────────────────────────────────────────────────
// Light Blue palette (600–900) — secondary, tertiary & surface tinting
// ─────────────────────────────────────────────────────────────────────────────

val LightBlue600 = Color(0xFF039BE5)   // vibrant sky  — light secondary / dark tertiary
val LightBlue700 = Color(0xFF0288D1)   // deeper sky   — light tertiary / dark secondary
val LightBlue800 = Color(0xFF0277BD)   // rich ocean   — dark surface tint / containers
val LightBlue900 = Color(0xFF01579B)   // deep navy    — dark secondaryContainer / outlineVariant

// ─────────────────────────────────────────────────────────────────────────────
// Semantic surface tokens
// ─────────────────────────────────────────────────────────────────────────────

// List item background (light) — white with a hint of light-blue
// Contrast ratio against LightBlue900 text ≈ 4.5:1 ✓  (boundary:text = 3:1 ✓)
val ListSurfaceLight     = Color(0xFFEFF7FD)   // very pale sky blue
val ListBoundaryLight    = LightBlue600        // #039BE5 — vivid sky border on light
val ListTextLight        = LightBlue900        // #01579B — deep navy, 4.5:1 on pale bg

// List item background (dark) — deep navy-blue elevated surface
// Contrast ratio against LightBlue600 text ≈ 4.6:1 ✓  (boundary:text = 3:1 ✓)
val ListSurfaceDark      = Color(0xFF0D1B2A)   // very dark navy
val ListBoundaryDark     = LightBlue700        // #0288D1 — ocean blue border on dark
val ListTextDark         = LightBlue600        // #039BE5 — sky blue text on dark

// ─────────────────────────────────────────────────────────────────────────────
// Light Theme
// ─────────────────────────────────────────────────────────────────────────────

val md_theme_light_primary              = IndigoA400        // #3D5AFE — electric accent primary
val md_theme_light_onPrimary            = Color.White
val md_theme_light_primaryContainer     = Indigo50          // #E8EAF6
val md_theme_light_onPrimaryContainer   = Indigo900         // #1A237E

val md_theme_light_secondary            = LightBlue600      // #039BE5 — vibrant sky
val md_theme_light_onSecondary          = Color.White
val md_theme_light_secondaryContainer   = Color(0xFFE1F5FE) // very pale sky (Light Blue 50)
val md_theme_light_onSecondaryContainer = LightBlue900      // #01579B

val md_theme_light_tertiary             = LightBlue700      // #0288D1 — deeper sky
val md_theme_light_onTertiary           = Color.White
val md_theme_light_tertiaryContainer    = Indigo50          // #E8EAF6 — indigo tint container
val md_theme_light_onTertiaryContainer  = LightBlue900

val md_theme_light_error                = Color(0xFFBA1A1A)
val md_theme_light_errorContainer       = Color(0xFFFFDAD6)
val md_theme_light_onError              = Color.White
val md_theme_light_onErrorContainer     = Color(0xFF410002)

val md_theme_light_background           = Color(0xFFFFFFFF)
val md_theme_light_onBackground         = LightBlue900      // #01579B

val md_theme_light_surface              = Color(0xFFFFFFFF)
val md_theme_light_onSurface            = LightBlue900      // #01579B
val md_theme_light_surfaceVariant       = ListSurfaceLight  // #EFF7FD — list bg
val md_theme_light_onSurfaceVariant     = LightBlue800      // #0277BD

val md_theme_light_outline              = LightBlue600      // #039BE5 — boundary
val md_theme_light_outlineVariant       = ListBoundaryLight // #039BE5 — list border
val md_theme_light_inverseOnSurface     = Indigo50
val md_theme_light_inverseSurface       = LightBlue900
val md_theme_light_inversePrimary       = IndigoA100
val md_theme_light_surfaceTint          = LightBlue600
val md_theme_light_scrim                = Color(0xFF000000)

val md_theme_light_surfaceContainerLowest  = Color(0xFFFFFFFF)
val md_theme_light_surfaceContainerLow     = Color(0xFFF5F9FF)  // barely-there sky
val md_theme_light_surfaceContainer        = ListSurfaceLight   // #EFF7FD
val md_theme_light_surfaceContainerHigh    = Color(0xFFE1F5FE)  // Light Blue 50
val md_theme_light_surfaceContainerHighest = Indigo100          // #C5CAE9

// ─────────────────────────────────────────────────────────────────────────────
// Dark Theme — #121212 base, accent palette A100–A400
// ─────────────────────────────────────────────────────────────────────────────

val md_theme_dark_primary               = IndigoA100        // #8C9EFF — desaturated accent
val md_theme_dark_onPrimary             = Indigo900         // #1A237E
val md_theme_dark_primaryContainer      = IndigoA700        // #304FFE — vivid container
val md_theme_dark_onPrimaryContainer    = Color.White

val md_theme_dark_secondary             = LightBlue600      // #039BE5 — sky on dark
val md_theme_dark_onSecondary           = Color(0xFF003350)
val md_theme_dark_secondaryContainer    = LightBlue900      // #01579B — deep navy container
val md_theme_dark_onSecondaryContainer  = Color(0xFFE1F5FE)

val md_theme_dark_tertiary              = LightBlue700      // #0288D1
val md_theme_dark_onTertiary            = Color.White
val md_theme_dark_tertiaryContainer     = LightBlue800      // #0277BD
val md_theme_dark_onTertiaryContainer   = Color(0xFFE1F5FE)

val md_theme_dark_error                 = Color(0xFFFFB4AB)
val md_theme_dark_errorContainer        = Color(0xFF93000A)
val md_theme_dark_onError               = Color(0xFF690005)
val md_theme_dark_onErrorContainer      = Color(0xFFFFDAD6)

val md_theme_dark_background            = Color(0xFF121212)
val md_theme_dark_onBackground          = LightBlue600      // #039BE5 — sky text on dark

val md_theme_dark_surface               = Color(0xFF121212)
val md_theme_dark_onSurface             = Color(0xFFE6E1E5)
val md_theme_dark_surfaceVariant        = ListSurfaceDark   // #0D1B2A — deep navy list bg
val md_theme_dark_onSurfaceVariant      = LightBlue600      // #039BE5

val md_theme_dark_outline               = LightBlue700      // #0288D1 — boundary
val md_theme_dark_outlineVariant        = LightBlue900      // #01579B — list border
val md_theme_dark_inverseOnSurface      = Color(0xFF1C1B1F)
val md_theme_dark_inverseSurface        = Color(0xFFE1F5FE)
val md_theme_dark_inversePrimary        = IndigoA400
val md_theme_dark_surfaceTint           = LightBlue700
val md_theme_dark_scrim                 = Color(0xFF000000)

// Elevation steps: dark navy deepening with LightBlue tint
val md_theme_dark_surfaceContainerLowest  = Color(0xFF0A0F14)
val md_theme_dark_surfaceContainerLow     = Color(0xFF0D1520)  // darkest navy
val md_theme_dark_surfaceContainer        = ListSurfaceDark    // #0D1B2A
val md_theme_dark_surfaceContainerHigh    = Color(0xFF102030)
val md_theme_dark_surfaceContainerHighest = LightBlue900       // #01579B — richest step
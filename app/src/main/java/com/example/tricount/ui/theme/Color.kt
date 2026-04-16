package com.example.tricount.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// Microsoft Teams Blue palette — the dominant accent colour
// This is the "Teams blue" (#4F52B2 / #6264A7 range) used in the real app
// ─────────────────────────────────────────────────────────────────────────────

val TeamsBlue700    = Color(0xFF3B3F9E)   // deep blue — dark containers / pressed
val TeamsBlue600    = Color(0xFF4F52B2)   // core Teams blue — LIGHT PRIMARY
val TeamsBlue500    = Color(0xFF6264A7)   // mid blue-indigo — light secondary
val TeamsBlue400    = Color(0xFF8B8CC8)   // soft blue — dark primary / active labels
val TeamsBlue300    = Color(0xFFAFB0DC)   // lighter blue — dark secondary text
val TeamsBlue100    = Color(0xFFE0E1F5)   // pale blue — light primaryContainer
val TeamsBlue50     = Color(0xFFEEEFF9)   // palest blue — light surface tint

// ─────────────────────────────────────────────────────────────────────────────
// Violet — kept only for containers & subtle tinting, NOT for text/icons
// ─────────────────────────────────────────────────────────────────────────────

val TeamsViolet900  = Color(0xFF3D2F81)
val TeamsViolet700  = Color(0xFF5C4EB5)
val TeamsViolet400  = Color(0xFF7382FF)
val TeamsViolet200  = Color(0xFFB182FF)
val TeamsViolet50   = Color(0xFFE8E5F7)

// ─────────────────────────────────────────────────────────────────────────────
// Neutral surfaces
// ─────────────────────────────────────────────────────────────────────────────

val TeamsNeutral50  = Color(0xFFF2F2F2)
val TeamsNeutral100 = Color(0xFFE0E0E0)
val TeamsNeutral200 = Color(0xFFC8C8C8)

// Status
val TeamsRose = Color(0xFFD18088)

// ─────────────────────────────────────────────────────────────────────────────
// Semantic list surface tokens
// ─────────────────────────────────────────────────────────────────────────────

val ListSurfaceLight  = Color(0xFFF0EFF9)   // pale blue-violet card bg
val ListBoundaryLight = TeamsBlue500
val ListTextLight     = TeamsBlue700

val ListSurfaceDark   = Color(0xFF1C1A2E)   // deep dark blue-violet card bg
val ListBoundaryDark  = Color(0xFF35337A)
val ListTextDark      = TeamsBlue300

// ─────────────────────────────────────────────────────────────────────────────
// LIGHT THEME — Teams blue as primary, white background
// ─────────────────────────────────────────────────────────────────────────────

val md_theme_light_primary              = TeamsBlue600          // #4F52B2 — Teams blue
val md_theme_light_onPrimary            = Color.White
val md_theme_light_primaryContainer     = TeamsBlue100          // #E0E1F5 — pale blue
val md_theme_light_onPrimaryContainer   = TeamsBlue700          // #3B3F9E

val md_theme_light_secondary            = TeamsBlue500          // #6264A7
val md_theme_light_onSecondary          = Color.White
val md_theme_light_secondaryContainer   = TeamsBlue50           // #EEF0F9
val md_theme_light_onSecondaryContainer = TeamsBlue700

val md_theme_light_tertiary             = TeamsBlue600
val md_theme_light_onTertiary           = Color.White
val md_theme_light_tertiaryContainer    = TeamsBlue100
val md_theme_light_onTertiaryContainer  = TeamsBlue700

val md_theme_light_error                = Color(0xFFBA1A1A)
val md_theme_light_errorContainer       = Color(0xFFFFDAD6)
val md_theme_light_onError              = Color.White
val md_theme_light_onErrorContainer     = Color(0xFF410002)

val md_theme_light_background           = Color(0xFFFFFFFF)
val md_theme_light_onBackground         = TeamsBlue700

val md_theme_light_surface              = Color(0xFFFFFFFF)
val md_theme_light_onSurface            = TeamsBlue700
val md_theme_light_surfaceVariant       = ListSurfaceLight      // #F0EFF9
val md_theme_light_onSurfaceVariant     = TeamsBlue500

val md_theme_light_outline              = TeamsBlue500
val md_theme_light_outlineVariant       = ListBoundaryLight
val md_theme_light_inverseOnSurface     = TeamsBlue50
val md_theme_light_inverseSurface       = TeamsBlue700
val md_theme_light_inversePrimary       = TeamsBlue300
val md_theme_light_surfaceTint          = TeamsBlue600
val md_theme_light_scrim                = Color(0xFF000000)

val md_theme_light_surfaceContainerLowest  = Color(0xFFFFFFFF)
val md_theme_light_surfaceContainerLow     = Color(0xFFF5F5FC)
val md_theme_light_surfaceContainer        = ListSurfaceLight   // #F0EFF9
val md_theme_light_surfaceContainerHigh    = TeamsBlue100       // #E0E1F5
val md_theme_light_surfaceContainerHighest = Color(0xFFD5D6F0)

// ─────────────────────────────────────────────────────────────────────────────
// DARK THEME — jet-black bg, blue-tinted elevated surfaces, bright blue accents
// Matches the Teams desktop dark mode exactly
// ─────────────────────────────────────────────────────────────────────────────

val md_theme_dark_primary               = TeamsBlue400          // #8B8CC8 — bright blue on dark
val md_theme_dark_onPrimary             = Color(0xFF1A1A3E)
val md_theme_dark_primaryContainer      = Color(0xFF2B2D6E)     // deep blue container
val md_theme_dark_onPrimaryContainer    = TeamsBlue100          // #E0E1F5 readable on dark

val md_theme_dark_secondary             = TeamsBlue300          // #AFB0DC
val md_theme_dark_onSecondary           = Color(0xFF1A1A3E)
val md_theme_dark_secondaryContainer    = Color(0xFF252565)     // deep indigo-blue container
val md_theme_dark_onSecondaryContainer  = TeamsBlue100

val md_theme_dark_tertiary              = TeamsBlue400
val md_theme_dark_onTertiary            = Color(0xFF1A1A3E)
val md_theme_dark_tertiaryContainer     = Color(0xFF2B2D6E)
val md_theme_dark_onTertiaryContainer   = TeamsBlue100

val md_theme_dark_error                 = Color(0xFFFFB4AB)
val md_theme_dark_errorContainer        = Color(0xFF93000A)
val md_theme_dark_onError               = Color(0xFF690005)
val md_theme_dark_onErrorContainer      = Color(0xFFFFDAD6)

val md_theme_dark_background            = Color(0xFF000000)     // jet black
val md_theme_dark_onBackground          = TeamsBlue300          // #AFB0DC — blue-white text

val md_theme_dark_surface               = Color(0xFF000000)
val md_theme_dark_onSurface             = Color(0xFFE4E4F0)     // near-white with blue tint
val md_theme_dark_surfaceVariant        = ListSurfaceDark       // #1C1A2E — blue-dark cards
val md_theme_dark_onSurfaceVariant      = TeamsBlue400          // #8B8CC8 — muted blue labels

val md_theme_dark_outline               = TeamsBlue500          // #6264A7
val md_theme_dark_outlineVariant        = Color(0xFF35337A)     // subtle blue border
val md_theme_dark_inverseOnSurface      = Color(0xFF1A1A3E)
val md_theme_dark_inverseSurface        = TeamsBlue100
val md_theme_dark_inversePrimary        = TeamsBlue600
val md_theme_dark_surfaceTint           = TeamsBlue500
val md_theme_dark_scrim                 = Color(0xFF000000)

// Elevation: jet black → deep blue-tinted surfaces (Teams dark sidebar feel)
val md_theme_dark_surfaceContainerLowest  = Color(0xFF000000)   // pure black
val md_theme_dark_surfaceContainerLow     = Color(0xFF0D0D1F)   // near-black + blue hint
val md_theme_dark_surfaceContainer        = Color(0xFF1C1A2E)   // dark blue-violet — cards
val md_theme_dark_surfaceContainerHigh    = Color(0xFF252550)   // richer blue — elevated
val md_theme_dark_surfaceContainerHighest = Color(0xFF2B2D6E)   // deepest blue — headers/tabs
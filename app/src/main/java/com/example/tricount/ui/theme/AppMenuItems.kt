package com.example.tricount.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
// Shared menu item components
//
// Use NormalMenuItem  for Edit, Archive, View, etc.
// Use DestructiveMenuItem for Delete, Logout, Remove — anything destructive.
//
// Colors are always sourced from MaterialTheme.colorScheme so they adapt
// automatically to light/dark mode without any manual hex values.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Standard menu item — icon and text both use onSurface.
 * Use this for all non-destructive actions (Edit, Archive, View, etc.)
 */
@Composable
fun NormalMenuItem(
    label   : String,
    icon    : ImageVector,
    onClick : () -> Unit,
    enabled : Boolean = true
) {
    DropdownMenuItem(
        leadingIcon = {
            Icon(
                icon, null,
                tint     = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        },
        text    = {
            Text(
                label,
                fontSize = 15.sp,
                color    = MaterialTheme.colorScheme.onSurface
            )
        },
        onClick = onClick,
        enabled = enabled
    )
}

/**
 * Destructive menu item — icon and text both use error (red).
 * Use this only for Delete, Logout, Remove, or any irreversible action.
 */
@Composable
fun DestructiveMenuItem(
    label   : String,
    icon    : ImageVector,
    onClick : () -> Unit,
    enabled : Boolean = true
) {
    DropdownMenuItem(
        leadingIcon = {
            Icon(
                icon, null,
                tint     = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        },
        text    = {
            Text(
                label,
                fontSize = 15.sp,
                color    = MaterialTheme.colorScheme.error
            )
        },
        onClick = onClick,
        enabled = enabled
    )
}
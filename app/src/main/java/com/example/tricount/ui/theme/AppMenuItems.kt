package com.example.tricount.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Unified menu item — icon and text always use onSurface.
 * Use this for ALL menu actions (Edit, Archive, Delete, etc.)
 * Colors adapt automatically to light/dark mode via MaterialTheme.colorScheme.
 */
@Composable
fun AppMenuItem(
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
        text = {
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

// ─────────────────────────────────────────────────────────────────────────────
// Kept for backward compatibility — both now delegate to AppMenuItem
// so all items render with the same onSurface color.
// You can remove these once all call sites are migrated to AppMenuItem.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NormalMenuItem(
    label   : String,
    icon    : ImageVector,
    onClick : () -> Unit,
    enabled : Boolean = true
) = AppMenuItem(label, icon, onClick, enabled)

@Composable
fun DestructiveMenuItem(
    label   : String,
    icon    : ImageVector,
    onClick : () -> Unit,
    enabled : Boolean = true
) = AppMenuItem(label, icon, onClick, enabled)
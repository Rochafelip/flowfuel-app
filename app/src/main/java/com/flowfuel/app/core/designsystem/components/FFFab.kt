package com.flowfuel.app.core.designsystem.components

import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
fun FFFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
) {
    val accessibilityMod = modifier.semantics { this.contentDescription = contentDescription }
    if (text != null) {
        ExtendedFloatingActionButton(
            onClick = onClick,
            icon = { Icon(icon, contentDescription = null) },
            text = { Text(text) },
            modifier = accessibilityMod,
        )
    } else {
        FloatingActionButton(onClick = onClick, modifier = accessibilityMod) {
            Icon(icon, contentDescription = null)
        }
    }
}

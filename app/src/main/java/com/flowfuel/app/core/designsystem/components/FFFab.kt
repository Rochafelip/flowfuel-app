package com.flowfuel.app.core.designsystem.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DefaultFabSize = 56.dp
private val DefaultIconSize = 24.dp

/** [size], quando informado, sobrescreve o tamanho padrão M3 (56dp) — usado para
 * destacar o FAB quando ele funciona como peça central de uma barra de navegação. */
@Composable
fun FFFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    size: Dp = DefaultFabSize,
) {
    val accessibilityMod = modifier.semantics { this.contentDescription = contentDescription }
    val iconSize = DefaultIconSize * (size / DefaultFabSize)
    if (text != null) {
        ExtendedFloatingActionButton(
            onClick = onClick,
            icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize)) },
            text = { Text(text) },
            modifier = accessibilityMod,
        )
    } else {
        FloatingActionButton(onClick = onClick, modifier = accessibilityMod.size(size)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize))
        }
    }
}

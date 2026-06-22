package com.flowfuel.app.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.flowfuel.app.core.designsystem.theme.FFTheme

enum class FFCardVariant { Flat, Elevated, Outlined }

@Composable
fun FFCard(
    modifier: Modifier = Modifier,
    variant: FFCardVariant = FFCardVariant.Flat,
    title: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = FFTheme.extraShapes.card
    val tonal = FFTheme.elevation.level1

    val body: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(FFTheme.spacing.md)) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = FFTheme.spacing.sm)
                )
            }
            content()
        }
    }

    val rootMod = modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.semantics { role = Role.Button } else Modifier)

    when (variant) {
        FFCardVariant.Flat -> if (onClick != null) {
            Card(onClick = onClick, modifier = rootMod, shape = shape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = tonal)
            ) { body() }
        } else {
            Card(modifier = rootMod, shape = shape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = tonal)
            ) { body() }
        }
        FFCardVariant.Elevated -> if (onClick != null) {
            ElevatedCard(onClick = onClick, modifier = rootMod, shape = shape) { body() }
        } else {
            ElevatedCard(modifier = rootMod, shape = shape) { body() }
        }
        FFCardVariant.Outlined -> if (onClick != null) {
            OutlinedCard(onClick = onClick, modifier = rootMod, shape = shape) { body() }
        } else {
            OutlinedCard(modifier = rootMod, shape = shape) { body() }
        }
    }
}

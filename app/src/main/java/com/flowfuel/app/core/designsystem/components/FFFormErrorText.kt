package com.flowfuel.app.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.flowfuel.app.core.domain.FieldError

@Composable
fun FFFormErrorText(
    error: String?,
    modifier: Modifier = Modifier,
) {
    if (error != null) {
        Text(
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier,
        )
    }
}

@Composable
fun FFFormErrorText(
    errors: List<FieldError>,
    modifier: Modifier = Modifier,
) {
    if (errors.isEmpty()) return
    Column(modifier = modifier) {
        errors.forEach { fieldError ->
            Text(
                text = fieldError.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

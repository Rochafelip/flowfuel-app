package com.flowfuel.app.core.designsystem.components

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.flowfuel.app.core.designsystem.theme.FFTheme

enum class FFSnackbarKind { Info, Success, Error }

data class FFSnackbarVisuals(
    override val message: String,
    val kind: FFSnackbarKind = FFSnackbarKind.Info,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
    override val duration: androidx.compose.material3.SnackbarDuration =
        androidx.compose.material3.SnackbarDuration.Short,
) : SnackbarVisuals

@Composable
fun FFSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data: SnackbarData ->
        val visuals = data.visuals
        val (container, content) = when (visuals) {
            is FFSnackbarVisuals -> when (visuals.kind) {
                FFSnackbarKind.Success -> FFTheme.semanticColors.success to FFTheme.semanticColors.onSuccess
                FFSnackbarKind.Error -> androidx.compose.material3.MaterialTheme.colorScheme.error to androidx.compose.material3.MaterialTheme.colorScheme.onError
                FFSnackbarKind.Info -> androidx.compose.material3.MaterialTheme.colorScheme.inverseSurface to androidx.compose.material3.MaterialTheme.colorScheme.inverseOnSurface
            }
            else -> androidx.compose.material3.MaterialTheme.colorScheme.inverseSurface to androidx.compose.material3.MaterialTheme.colorScheme.inverseOnSurface
        }
        Snackbar(
            snackbarData = data,
            containerColor = container,
            contentColor = content,
            actionColor = content,
            dismissActionContentColor = content,
        )
    }
}

package com.flowfuel.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import com.flowfuel.app.core.designsystem.theme.FFTheme

/**
 * Banner informativo persistente (não é um snackbar transitório) para avisos
 * contextuais, ex.: "confira a caixa de spam". Fundo `tertiaryContainer`,
 * mesmo par de cores já usado no `EnergyTypeBadge` de `VehicleDetailsScreen`.
 */
@Composable
fun FFInfoBanner(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(FFTheme.extraShapes.card)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(FFTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FFInfoBannerPreview() {
    FFInfoBanner(text = "Não encontrou o e-mail? Verifique também a caixa de spam ou lixo eletrônico.")
}

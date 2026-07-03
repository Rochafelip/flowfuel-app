package com.flowfuel.app.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.flowfuel.app.feature.vehicle.domain.model.VehicleType

/**
 * Avatar circular do veículo: exibe [photoUrl] via Coil quando presente,
 * com fallback para o ícone de [vehicleType] (mesma técnica de
 * fallback-atrás/imagem-crossfade-na-frente de [UserAvatar]).
 */
@Composable
fun VehiclePhotoAvatar(
    photoUrl: String?,
    vehicleType: VehicleType,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    onClick: (() -> Unit)? = null,
) {
    val badgeSize = (size.value * 0.29f).coerceAtLeast(16f).dp
    val badgeIconSize = (badgeSize.value * 0.57f).dp

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .then(
                    if (onClick != null) Modifier
                        .clickable { onClick() }
                        .semantics { contentDescription = "Foto do veículo. Toque para alterar" }
                    else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Fallback sempre renderizado atrás — visível durante o carregamento e em erro
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (vehicleType == VehicleType.Motorcycle) Icons.Filled.TwoWheeler
                                      else Icons.Filled.DirectionsCar,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(0.5f),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            // Imagem crossfade por cima — invisível durante o carregamento e em erro
            if (!photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(photoUrl)
                        .crossfade(300)
                        .build(),
                    contentDescription = "Foto do veículo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (onClick != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(badgeSize),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 2.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(badgeIconSize),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

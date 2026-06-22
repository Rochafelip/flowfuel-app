package com.flowfuel.app.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

private val AvatarColorPalette = listOf(
    Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFF66BB6A), Color(0xFFFFA726),
    Color(0xFFEF5350), Color(0xFFAB47BC), Color(0xFF29B6F6), Color(0xFF26C6DA),
)

@Composable
fun UserAvatar(
    url: String?,
    name: String?,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    onClick: (() -> Unit)? = null,
) {
    val initials = name
        ?.trim()
        ?.split(" ")
        ?.mapNotNull { it.firstOrNull()?.uppercaseChar() }
        ?.take(2)
        ?.joinToString("")
        .orEmpty()

    val bgColor = if (name.isNullOrBlank()) MaterialTheme.colorScheme.primaryContainer
    else AvatarColorPalette[Math.floorMod(name.hashCode(), AvatarColorPalette.size)]

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
                        .semantics { contentDescription = "Foto de perfil. Toque para alterar" }
                    else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Fallback always rendered behind — visible during load and on error
            AvatarFallback(initials = initials, bgColor = bgColor)

            // Image crossfades in on top; invisible during load and on error
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url)
                        .crossfade(300)
                        .build(),
                    contentDescription = "Foto de perfil",
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

@Composable
private fun AvatarFallback(
    initials: String,
    bgColor: Color,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = CircleShape,
        color = bgColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (initials.isNotEmpty()) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(0.5f),
                    tint = Color.White,
                )
            }
        }
    }
}

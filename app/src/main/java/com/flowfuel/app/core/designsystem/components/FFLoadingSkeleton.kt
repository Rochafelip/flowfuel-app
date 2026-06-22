package com.flowfuel.app.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.theme.FFTheme
import androidx.compose.material3.MaterialTheme

@Composable
private fun shimmerBrush(): Brush {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f).compositeOver(base)
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            tween(FFTheme.motion.shimmerLoopMs, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "translate"
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = androidx.compose.ui.geometry.Offset(translate - 300f, 0f),
        end = androidx.compose.ui.geometry.Offset(translate, 0f),
    )
}

@Composable
fun FFSkeletonBlock(modifier: Modifier = Modifier, height: Dp = 80.dp) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(shimmerBrush())
    )
}

@Composable
fun FFSkeletonLine(modifier: Modifier = Modifier, height: Dp = 14.dp, widthFraction: Float = 1f) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(shimmerBrush())
    )
}

@Composable
fun FFSkeletonCircle(modifier: Modifier = Modifier, size: Dp = 64.dp) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(shimmerBrush())
    )
}

@Composable
fun FFSkeletonList(modifier: Modifier = Modifier, itemCount: Int = 4) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(FFTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.cardGap),
    ) {
        repeat(itemCount) { FFSkeletonBlock() }
    }
}

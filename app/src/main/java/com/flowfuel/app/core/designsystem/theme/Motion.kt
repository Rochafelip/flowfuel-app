package com.flowfuel.app.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable

@Immutable
data class FFMotion(
    val short: Int = 150,
    val medium: Int = 250,
    val long: Int = 400,
    val enter: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f),
    val exit: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f),
    val emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
    val shimmerLoopMs: Int = 1200,
)

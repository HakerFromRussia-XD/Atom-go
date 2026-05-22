package com.atomgo.android.presentation.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.clearAndSetSemantics

internal const val AppStackTransitionMillis = 300
internal const val AppStackDeferredWorkMillis = AppStackTransitionMillis + 120
private const val AppBottomNavHideMillis = 180
private const val AppBottomNavShowMillis = 220
private const val AppStackHiddenOffsetFraction = 1f
private const val AppStackVisibleOffsetFraction = 0f
private const val AppStackUnderlyingOffsetFraction = -0.5f
private val AppStackEasing = CubicBezierEasing(0.33f, 0f, 0.2f, 1f)

internal fun appStackEnter(): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(AppStackTransitionMillis, easing = AppStackEasing)
    )
}

internal fun appStackPopExit(): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(AppStackTransitionMillis, easing = AppStackEasing)
    )
}

@Composable
internal fun AppStackVisibility(
    visible: Boolean,
    precompose: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var shouldRender by remember { mutableStateOf(visible) }
    val offsetFraction = remember { Animatable(AppStackHiddenOffsetFraction) }

    LaunchedEffect(visible, precompose) {
        when {
            visible -> {
                val wasRendered = shouldRender
                shouldRender = true
                if (!wasRendered) {
                    offsetFraction.snapTo(AppStackHiddenOffsetFraction)
                    withFrameNanos { }
                }
                offsetFraction.animateTo(
                    targetValue = AppStackVisibleOffsetFraction,
                    animationSpec = tween(
                        durationMillis = AppStackTransitionMillis,
                        easing = AppStackEasing
                    )
                )
            }

            shouldRender -> {
                offsetFraction.animateTo(
                    targetValue = AppStackHiddenOffsetFraction,
                    animationSpec = tween(
                        durationMillis = AppStackTransitionMillis,
                        easing = AppStackEasing
                    )
                )
                shouldRender = precompose
            }

            precompose -> {
                offsetFraction.snapTo(AppStackHiddenOffsetFraction)
                shouldRender = true
            }
        }
    }

    if (shouldRender) {
        Box(
            modifier = modifier
                .graphicsLayer {
                    translationX = size.width * offsetFraction.value
                }
                .then(if (visible) Modifier else Modifier.clearAndSetSemantics {})
        ) {
            content()
        }
    }
}

internal fun appBottomNavEnter(): EnterTransition {
    return fadeIn(animationSpec = tween(AppBottomNavShowMillis)) +
        slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(AppBottomNavShowMillis)
        )
}

internal fun appBottomNavExit(): ExitTransition {
    return fadeOut(animationSpec = tween(AppBottomNavHideMillis)) +
        slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(AppBottomNavHideMillis)
        )
}

@Composable
internal fun Modifier.appStackUnderlyingOffset(active: Boolean, label: String): Modifier {
    val offsetFraction = remember(label) {
        Animatable(if (active) AppStackUnderlyingOffsetFraction else AppStackVisibleOffsetFraction)
    }

    LaunchedEffect(active) {
        offsetFraction.animateTo(
            targetValue = if (active) AppStackUnderlyingOffsetFraction else AppStackVisibleOffsetFraction,
            animationSpec = tween(
                durationMillis = AppStackTransitionMillis,
                easing = AppStackEasing
            )
        )
    }

    return graphicsLayer {
        translationX = size.width * offsetFraction.value
    }
}

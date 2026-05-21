package com.atomgo.android.presentation.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import kotlinx.coroutines.delay

private const val MotoricaFragmentTransitionMillis = 400
private const val MotoricaBottomNavHideMillis = 180
private const val MotoricaBottomNavShowMillis = 220

internal fun motoricaStackEnter(): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(MotoricaFragmentTransitionMillis, easing = LinearOutSlowInEasing)
    )
}

internal fun motoricaStackPopExit(): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(MotoricaFragmentTransitionMillis, easing = LinearOutSlowInEasing)
    )
}

@Composable
internal fun MotoricaStackVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var shouldRender by remember { mutableStateOf(visible) }
    var targetOffsetFraction by remember { mutableStateOf(if (visible) 0f else 1f) }
    val currentOffsetFraction by animateFloatAsState(
        targetValue = targetOffsetFraction,
        animationSpec = tween(
            durationMillis = MotoricaFragmentTransitionMillis,
            easing = LinearOutSlowInEasing
        ),
        label = "motoricaStackOffset"
    )

    LaunchedEffect(visible) {
        if (visible) {
            shouldRender = true
            targetOffsetFraction = 1f
            withFrameNanos { }
            targetOffsetFraction = 0f
        } else if (shouldRender) {
            targetOffsetFraction = 1f
            delay(MotoricaFragmentTransitionMillis.toLong())
            shouldRender = false
        }
    }

    if (shouldRender) {
        Box(
            modifier = modifier.graphicsLayer {
                translationX = size.width * currentOffsetFraction
            }
        ) {
            content()
        }
    }
}

internal fun motoricaBottomNavEnter(): EnterTransition {
    return fadeIn(animationSpec = tween(MotoricaBottomNavShowMillis)) +
        slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(MotoricaBottomNavShowMillis)
        )
}

internal fun motoricaBottomNavExit(): ExitTransition {
    return fadeOut(animationSpec = tween(MotoricaBottomNavHideMillis)) +
        slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(MotoricaBottomNavHideMillis)
        )
}

@Composable
internal fun Modifier.motoricaUnderlyingOffset(active: Boolean, label: String): Modifier {
    val offsetFraction = animateFloatAsState(
        targetValue = if (active) -0.5f else 0f,
        animationSpec = tween(
            durationMillis = MotoricaFragmentTransitionMillis,
            easing = if (active) FastOutLinearInEasing else LinearOutSlowInEasing
        ),
        label = label
    )
    return graphicsLayer {
        translationX = size.width * offsetFraction.value
    }
}

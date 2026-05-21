package com.atomgo.android.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import com.atomgo.android.AppDesign

@Composable
internal fun Modifier.adminClickable(
    shape: Shape,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return clip(shape).clickable(
        interactionSource = interactionSource,
        indication = ripple(bounded = true, color = AppDesign.Accent),
        enabled = enabled,
        onClick = onClick
    )
}

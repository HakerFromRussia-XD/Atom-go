package com.atomgo.android.presentation.ui

import com.atomgo.android.AppDesign

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AppToast(
    message: String?,
    modifier: Modifier = Modifier,
    bottomPadding: Int = 86
) {
    val density = LocalDensity.current
    val navigationBottomDp = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }

    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = fadeIn(animationSpec = tween(durationMillis = 180)) +
            slideInVertically(initialOffsetY = { it }, animationSpec = tween(durationMillis = 180)),
        exit = fadeOut(animationSpec = tween(durationMillis = 180)) +
            slideOutVertically(targetOffsetY = { it }, animationSpec = tween(durationMillis = 180)),
        modifier = modifier.padding(bottom = bottomPadding.dp + navigationBottomDp)
    ) {
        Surface(
            color = AppDesign.SurfaceBackground.copy(alpha = 0.98f),
            shadowElevation = 10.dp,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = message.orEmpty(),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = AppDesign.Black
            )
        }
    }
}

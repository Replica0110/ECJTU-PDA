package com.lonx.ecjtu.pda.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val AnimationDurationMillis = 300
@Composable
fun BottomSheetDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    scrimColor: Color = Color.Black.copy(alpha = 0.6f),
    containerColor: Color = MiuixTheme.colorScheme.surface, // Use M3 surface or define your own
    shape: Shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), // Round top corners
    content: @Composable ColumnScope.() -> Unit
) {
    // Use AnimatedVisibility to control the presence of the Popup and its content animation.
    // This makes managing enter/exit easier than manually controlling Popup visibility state.
    AnimatedVisibility(
        visible = show,
        enter = fadeIn(animationSpec = tween(durationMillis = AnimationDurationMillis)),
        exit = fadeOut(animationSpec = tween(durationMillis = AnimationDurationMillis))
    ) {
        // Popup creates a new window layer detached from the main composition hierarchy.
        Popup(
            alignment = Alignment.BottomCenter, // Anchor the popup window itself to the bottom
            onDismissRequest = onDismissRequest, // Handles back press if focusable
            properties = PopupProperties(
                focusable = true, // Important for back press dismissal
                dismissOnClickOutside = false, // We handle scrim click manually
                dismissOnBackPress = true
            )
        ) {
            // Use Box for layering scrim and content
            Box(
                modifier = Modifier.fillMaxSize(), // Fill the entire popup window
                contentAlignment = Alignment.BottomCenter // Align dialog content to the bottom of the Box
            ) {
                // 1. Scrim Layer (behind the dialog content)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(scrimColor)
                        .pointerInput(Unit) {
                            // Detect taps on the scrim to dismiss
                            detectTapGestures { onDismissRequest() }
                        }
                )

                // 2. Dialog Content Layer (animates sliding)
                // We need another AnimatedVisibility *inside* to handle the slide,
                // because the Popup itself appears/disappears instantly with the outer fade.
                AnimatedVisibility(
                    visible = show, // Driven by the same state
                    enter = slideInVertically(
                        animationSpec = tween(durationMillis = AnimationDurationMillis),
                        initialOffsetY = { fullHeight -> fullHeight } // Start from below screen
                    ) + fadeIn(animationSpec = tween(durationMillis = AnimationDurationMillis)),
                    exit = slideOutVertically(
                        animationSpec = tween(durationMillis = AnimationDurationMillis),
                        targetOffsetY = { fullHeight -> fullHeight } // Slide down below screen
                    ) + fadeOut(animationSpec = tween(durationMillis = AnimationDurationMillis))
                ) {
                    // Use Surface for elevation/shape/color, or a Box/Column with modifiers
                    Surface( // Or Box(modifier = Modifier.background(containerColor, shape))
                        modifier = modifier // Apply caller's modifier here
                            .fillMaxWidth() // Take full width
                            .wrapContentHeight() // Height based on content
                            // Prevent clicks passing through the dialog content to the scrim
                            .pointerInput(Unit) { detectTapGestures { } }
                            .imePadding() // Adjust for keyboard
                            .navigationBarsPadding(), // Adjust for navigation bar
                        shape = shape,
                        color = containerColor,
                        tonalElevation = 6.dp // Add some elevation if desired
                        // shadowElevation = 8.dp // Optional shadow
                    ) {
                        // Column to arrange title, message, buttons vertically
                        Column(
                            modifier = Modifier.padding(24.dp) // Inner padding for content
                        ) {
                            // Let the caller provide the content (title, message, buttons)
                            // This content receives the ColumnScope
                            content()
                        }
                    }
                } // End Inner AnimatedVisibility (Slide)
            } // End Outer Box (Scrim + Content)
        } // End Popup
    } // End Outer AnimatedVisibility (Fade)
}

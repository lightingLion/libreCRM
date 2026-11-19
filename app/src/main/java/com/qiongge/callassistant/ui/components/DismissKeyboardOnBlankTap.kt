package com.qiongge.callassistant.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

internal fun Modifier.dismissKeyboardOnBlankTap(): Modifier = composed {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    pointerInput(focus, keyboard) {
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Final)
            val up = waitForUpOrCancellation(pass = PointerEventPass.Final)
            if (up != null && (up.position - down.position).getDistance() < viewConfiguration.touchSlop) {
                focus.clearFocus()
                keyboard?.hide()
            }
        }
    }
}

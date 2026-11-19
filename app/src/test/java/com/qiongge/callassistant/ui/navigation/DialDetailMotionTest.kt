package com.qiongge.callassistant.ui.navigation

import com.qiongge.callassistant.ui.screens.dialDetailVisibility
import com.qiongge.callassistant.ui.screens.dialSwitchDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DialDetailMotionTest {
    @Test
    fun selectingEarlierAndLaterRowsEntersFromOppositeSides() {
        assertEquals(-1f, dialSwitchDirection(8, 3), 0f)
        assertEquals(1f, dialSwitchDirection(8, 12), 0f)
    }

    @Test
    fun detailsRetractAndReturnInOrder() {
        for (order in 0..3) {
            assertEquals(1f, dialDetailVisibility(0f, 1f, order), 0f)
            assertEquals(0f, dialDetailVisibility(1f, 1f, order), 0f)
            assertEquals(0f, dialDetailVisibility(0f, 0f, order), 0f)
        }
        val leavingNear = dialDetailVisibility(0.3f, 1f, 0)
        val leavingFar = dialDetailVisibility(0.3f, 1f, 3)
        assertTrue(leavingNear in 0f..1f && leavingFar in 0f..leavingNear)
        assertTrue(dialDetailVisibility(0f, 0.25f, 0) > 0f)
        assertEquals(0f, dialDetailVisibility(0f, 0.25f, 2), 0f)
    }
}

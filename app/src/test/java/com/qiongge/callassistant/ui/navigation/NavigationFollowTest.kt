package com.qiongge.callassistant.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationFollowTest {
    @Test
    fun lagStartsAboveThresholdAndIsBounded() {
        assertEquals(0f, NavigationFollow.sampleLag(0f, 11f, 10f), 0f)
        val nearThreshold = NavigationFollow.sampleLag(0f, 12f, 10f)
        val fast = NavigationFollow.sampleLag(0f, 28f, 10f)
        assertTrue(nearThreshold in 0f..fast && fast > 0f)
        assertTrue(NavigationFollow.sampleLag(fast, 200f, 35f) <= NavigationFollow.maxLagDp)
        assertTrue(NavigationFollow.sampleLag(0f, -28f, 10f) < 0f)
    }

    @Test
    fun stoppedFingerIsCaughtWithoutOvershoot() {
        val initial = NavigationFollow.maxLagDp
        val first = NavigationFollow.catchUp(initial, 23f)
        val second = NavigationFollow.catchUp(initial, 120f)
        assertTrue(first in second..initial)
        assertTrue(second in 0f..0.1f)
    }
}

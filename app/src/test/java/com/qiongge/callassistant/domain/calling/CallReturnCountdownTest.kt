package com.qiongge.callassistant.domain.calling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallReturnCountdownTest {
    @Test
    fun waitsForActualCallAndRestartsAfterLosingForeground() {
        val attempt = CallReturnCountdown(startedAt = 0)
        assertNull(attempt.sample(9_000, foreground = true, inCall = false, delaySeconds = 5))
        assertNull(attempt.sample(10_000, foreground = true, inCall = true, delaySeconds = 5))
        assertNull(attempt.sample(11_000, foreground = true, inCall = false, delaySeconds = 5))
        assertEquals(5, attempt.sample(12_000, foreground = true, inCall = false, delaySeconds = 5))
        attempt.onPause()
        assertNull(attempt.sample(20_000, foreground = false, inCall = false, delaySeconds = 5))
        assertNull(attempt.sample(25_000, foreground = true, inCall = false, delaySeconds = 5))
        assertEquals(5, attempt.sample(26_000, foreground = true, inCall = false, delaySeconds = 5))
        assertEquals(0, attempt.sample(31_000, foreground = true, inCall = false, delaySeconds = 5))
    }

    @Test
    fun shortAttemptCanAdvanceOnReturnAndNextAttemptHasItsOwnCountdown() {
        repeat(3) { index ->
            val start = index * 20_000L
            val attempt = CallReturnCountdown(startedAt = start)
            attempt.onPause()
            assertNull(attempt.sample(start + 500, foreground = true, inCall = false, delaySeconds = 5))
            assertNull(attempt.sample(start + 1_000, foreground = true, inCall = false, delaySeconds = 5))
            assertEquals(5, attempt.sample(start + 2_000, foreground = true, inCall = false, delaySeconds = 5))
            assertEquals(0, attempt.sample(start + 7_000, foreground = true, inCall = false, delaySeconds = 5))
        }
    }

    @Test
    fun ongoingCallAfterReturnBlocksAdvance() {
        val attempt = CallReturnCountdown(startedAt = 0)
        attempt.onPause()
        assertNull(attempt.sample(2_000, foreground = true, inCall = true, delaySeconds = 5))
        assertNull(attempt.sample(20_000, foreground = true, inCall = true, delaySeconds = 5))
        assertNull(attempt.sample(21_000, foreground = true, inCall = false, delaySeconds = 5))
        assertNull(attempt.sample(21_500, foreground = true, inCall = true, delaySeconds = 5))
        assertNull(attempt.sample(21_750, foreground = true, inCall = false, delaySeconds = 5))
        assertNull(attempt.sample(22_749, foreground = true, inCall = false, delaySeconds = 5))
        assertEquals(5, attempt.sample(22_750, foreground = true, inCall = false, delaySeconds = 5))
    }

    @Test
    fun ringingDisarmsExistingCountdownEvenAfterReturningToIdle() {
        val attempt = CallReturnCountdown(startedAt = 0)
        attempt.onOutgoingActivity()
        assertNull(attempt.sample(1_000, foreground = true, inCall = false, delaySeconds = 5))
        assertEquals(5, attempt.sample(2_000, foreground = true, inCall = false, delaySeconds = 5))
        attempt.onIncomingCall()
        assertNull(attempt.sample(7_000, foreground = false, inCall = true, delaySeconds = 5))
        attempt.onPause()
        attempt.onOutgoingActivity()
        assertNull(attempt.sample(20_000, foreground = true, inCall = false, delaySeconds = 5))
    }

    @Test
    fun shortOutgoingStateEventIsEnoughWhenNoActivityPauseWasObserved() {
        val attempt = CallReturnCountdown(startedAt = 0)
        attempt.onOutgoingActivity()
        assertNull(attempt.sample(1_000, foreground = true, inCall = false, delaySeconds = 5))
        assertEquals(5, attempt.sample(2_000, foreground = true, inCall = false, delaySeconds = 5))
        assertEquals(0, attempt.sample(7_000, foreground = true, inCall = false, delaySeconds = 5))
    }
}

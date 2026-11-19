package com.qiongge.callassistant.ui.navigation

import com.qiongge.callassistant.ui.components.WorkbookDragMotion
import com.qiongge.callassistant.ui.components.projectedDragPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkbookDragMotionTest {
    @Test
    fun autoScrollHasSymmetricThresholdAndSpeedCap() {
        assertEquals(0f, WorkbookDragMotion.scrollTarget(96f), 0f)
        val near = WorkbookDragMotion.scrollTarget(98f)
        val farther = WorkbookDragMotion.scrollTarget(173f)
        assertTrue(near > 0f && near < farther)
        assertEquals(-farther, WorkbookDragMotion.scrollTarget(-173f), 0f)
        assertEquals(WorkbookDragMotion.maximumScrollSpeed, WorkbookDragMotion.scrollTarget(900f), 0f)
        val firstFrame = WorkbookDragMotion.scrollSpeed(0f, farther, 0.016f)
        assertTrue(firstFrame in 0f..farther && firstFrame < farther)
        assertTrue(WorkbookDragMotion.scrollSpeed(firstFrame, 0f, 0.008f) in 0f..firstFrame)
    }

    @Test
    fun releaseProjectionUsesVelocityAndCapsItsTravel() {
        assertEquals(100f, projectedDragPosition(100f, 0f, 28f), 0f)
        assertEquals(114f, projectedDragPosition(100f, 100f, 28f), 0.001f)
        assertEquals(128f, projectedDragPosition(100f, 1000f, 28f), 0f)
        assertEquals(72f, projectedDragPosition(100f, -1000f, 28f), 0f)
    }

    @Test
    fun focusSaturatesSmoothly() {
        assertEquals(1f, WorkbookDragMotion.influence(0f), 0f)
        assertEquals(0f, WorkbookDragMotion.influence(72f), 0f)
        assertEquals(WorkbookDragMotion.influence(36f), WorkbookDragMotion.influence(-36f), 0f)
        assertTrue(WorkbookDragMotion.influence(34f) - WorkbookDragMotion.influence(38f) >
            WorkbookDragMotion.influence(0f) - WorkbookDragMotion.influence(4f))
    }
}

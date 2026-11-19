package com.qiongge.callassistant.ui.navigation

import com.qiongge.callassistant.ui.components.WorkbookDragMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WorkbookDragMotionTest {
    @Test
    fun autoScrollHasSymmetricThresholdAndSpeedCap() {
        assertEquals(0f, WorkbookDragMotion.scrollTarget(48f), 0f)
        val near = WorkbookDragMotion.scrollTarget(50f)
        val farther = WorkbookDragMotion.scrollTarget(125f)
        assertTrue(near > 0f && near < farther)
        assertEquals(-farther, WorkbookDragMotion.scrollTarget(-125f), 0f)
        assertEquals(WorkbookDragMotion.maximumScrollSpeed, WorkbookDragMotion.scrollTarget(900f), 0f)
        val firstFrame = WorkbookDragMotion.scrollSpeed(0f, farther, 0.016f)
        assertTrue(firstFrame in 0f..farther && firstFrame < farther)
        assertTrue(WorkbookDragMotion.scrollSpeed(firstFrame, 0f, 0.008f) in 0f..firstFrame)
    }

    @Test
    fun followerForceIsBoundedAndFocusSaturatesSmoothly() {
        val cap = WorkbookDragMotion.maximumAcceleration
        assertEquals(cap, WorkbookDragMotion.acceleration(900f, 0f), 0f)
        assertEquals(-cap, WorkbookDragMotion.acceleration(-900f, 0f), 0f)
        assertTrue(abs(WorkbookDragMotion.acceleration(0f, 300f)) <= cap)
        assertEquals(1f, WorkbookDragMotion.influence(0f), 0f)
        assertEquals(0f, WorkbookDragMotion.influence(72f), 0f)
        assertEquals(WorkbookDragMotion.influence(36f), WorkbookDragMotion.influence(-36f), 0f)
        assertTrue(WorkbookDragMotion.influence(34f) - WorkbookDragMotion.influence(38f) >
            WorkbookDragMotion.influence(0f) - WorkbookDragMotion.influence(4f))
    }
}

package com.qiongge.callassistant.ui.navigation

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationGeometryTest {
    @Test
    fun selectedItemReceivesFortyPercent() {
        assertArrayEquals(intArrayOf(0, 400, 700, 1000), NavigationGeometry.boundaries(1000, 0.2f))
        assertArrayEquals(intArrayOf(0, 300, 700, 1000), NavigationGeometry.boundaries(1000, 0.5f))
        assertArrayEquals(intArrayOf(0, 300, 600, 1000), NavigationGeometry.boundaries(1000, 0.8f))
    }

    @Test
    fun handoffChangesContinuouslyAndConservesTotalWidth() {
        var previous = NavigationGeometry.boundaries(1000, 0f)
        var previousTarget = 0
        for (x in 1..1000) {
            val slots = NavigationGeometry.boundaries(1000, x / 1000f)
            assertEquals(0, slots.first())
            assertEquals(1000, slots.last())
            assertTrue(slots.toList().zipWithNext().all { (left, right) -> right > left })
            assertTrue(slots.indices.all { index -> kotlin.math.abs(slots[index] - previous[index]) <= 2 })
            val target = NavigationGeometry.target(1000, x.toFloat())
            assertTrue(target >= previousTarget)
            previous = slots
            previousTarget = target
        }
        assertEquals(2, previousTarget)
    }

    @Test
    fun responseAcceleratesNearThresholdAndSaturatesAtCenter() {
        fun influence(distance: Float) = NavigationGeometry.influence(0.5f - distance, 1)
        assertEquals(0f, influence(0.31f), 0f)
        assertEquals(1f, influence(0f), 0f)
        assertTrue(influence(0.14f) - influence(0.16f) > influence(0f) - influence(0.02f))
        assertTrue(influence(0.14f) - influence(0.16f) > influence(0.28f) - influence(0.30f))
    }
}

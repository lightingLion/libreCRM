package com.qiongge.callassistant.domain.greeting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class GreetingPolicyTest {
    @Test
    fun localTimeGreetingUsesConfiguredRanges() {
        assertEquals("晚上好", GreetingPolicy.regularGreeting(at(4), null))
        assertEquals("早上好", GreetingPolicy.regularGreeting(at(5), null))
        assertEquals("早上好", GreetingPolicy.regularGreeting(at(11), null))
        assertEquals("下午好", GreetingPolicy.regularGreeting(at(12), null))
        assertEquals("下午好", GreetingPolicy.regularGreeting(at(17), null))
        assertEquals("晚上好", GreetingPolicy.regularGreeting(at(18), null))
    }

    @Test
    fun displayNameIsAppendedOnlyWhenPresent() {
        assertEquals("早上好，小王！", GreetingPolicy.regularGreeting(at(9), " 小王 "))
        assertEquals("早上好", GreetingPolicy.regularGreeting(at(9), "  "))
    }

    @Test
    fun festivalGreetingAppearsOncePerFestivalAndLocalDate() {
        val first = at(9, year = 2026, month = 3, day = 12)
        val arborDay = FestivalGreeting(
            id = "arbor_day",
            name = "植树节",
            message = "记住，地球是礼物。",
        )
        val shown = GreetingPolicy.resolveMenuGreeting(
            now = first,
            displayName = "小王",
            lastFestivalId = null,
            lastFestivalDate = null,
            festival = arborDay,
        )
        assertEquals("记住，地球是礼物。", shown.message)
        assertTrue(shown.isFestivalGreeting)
        assertEquals("arbor_day", shown.festivalIdToRecord)
        assertEquals("2026-03-12", shown.festivalDateToRecord)

        val repeated = GreetingPolicy.resolveMenuGreeting(
            now = first.plusHours(2),
            displayName = "小王",
            lastFestivalId = shown.festivalIdToRecord,
            lastFestivalDate = shown.festivalDateToRecord,
            festival = arborDay,
        )
        assertEquals("早上好，小王！", repeated.message)
        assertFalse(repeated.isFestivalGreeting)

        val nextYear = GreetingPolicy.resolveMenuGreeting(
            now = first.plusYears(1),
            displayName = null,
            lastFestivalId = shown.festivalIdToRecord,
            lastFestivalDate = shown.festivalDateToRecord,
            festival = arborDay,
        )
        assertTrue(nextYear.isFestivalGreeting)
    }

    @Test
    fun qingmingUsesSolarTermDateAndBoundedFormula() {
        assertEquals(LocalDate.of(1961, 4, 5), FestivalCalendar.qingmingDate(1961))
        assertEquals(LocalDate.of(1999, 4, 5), FestivalCalendar.qingmingDate(1999))
        assertEquals(LocalDate.of(2000, 4, 4), FestivalCalendar.qingmingDate(2000))
        assertEquals(LocalDate.of(2026, 4, 5), FestivalCalendar.qingmingDate(2026))
        assertEquals(LocalDate.of(2024, 4, 4), FestivalCalendar.qingmingDate(2024))
        assertEquals(
            FestivalGreeting("qingming", "清明节", "春风记得每一个名字。"),
            FestivalCalendar.forDate(LocalDate.of(2026, 4, 5)),
        )
        assertEquals(null, FestivalCalendar.qingmingDate(2051))
    }

    @Test
    fun holidayCatalogUsesTheSuppliedArborDayCopy() {
        assertEquals(
            FestivalGreeting("arbor_day", "植树节", "记住，地球是礼物。"),
            FestivalCalendar.forDate(LocalDate.of(2026, 3, 12)),
        )
    }

    private fun at(
        hour: Int,
        year: Int = 2026,
        month: Int = 4,
        day: Int = 20,
    ): LocalDateTime = LocalDateTime.of(year, month, day, hour, 0)
}

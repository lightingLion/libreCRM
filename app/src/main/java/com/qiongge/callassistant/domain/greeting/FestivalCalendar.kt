package com.qiongge.callassistant.domain.greeting

import android.icu.util.Calendar
import android.icu.util.ChineseCalendar
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.floor

/**
 * Offline holiday lookup for the supplied greeting copy library.
 *
 * Chinese lunar dates use Android ICU, available on all supported devices
 * (API 24+). Gregorian Qingming dates use the common century-specific formula
 * for 1961–2050; outside that supported range no Qingming greeting is returned.
 */
object FestivalCalendar {
    private data class SolarFestival(
        val id: String,
        val name: String,
        val month: Int,
        val day: Int,
        val message: String,
    )

    private data class LunarFestival(
        val id: String,
        val name: String,
        val month: Int,
        val day: Int,
        val message: String,
    )

    private val solarFestivals = listOf(
        SolarFestival("new_year", "元旦", 1, 1, "新年，重新落子。"),
        SolarFestival("womens_day", "妇女节", 3, 8, "世界因你而不同。"),
        SolarFestival("arbor_day", "植树节", 3, 12, "记住，地球是礼物。"),
        SolarFestival("labor_day", "劳动节", 5, 1, "世界由双手建成。"),
        SolarFestival("childrens_day", "儿童节", 6, 1, "好奇心，永不放假。"),
        SolarFestival("party_day", "建党节", 7, 1, "理想，让远方有了方向。"),
        SolarFestival("army_day", "建军节", 8, 1, "愿守护者皆有归途。"),
        SolarFestival("teachers_day", "教师节", 9, 10, "知识，是会传递的光。"),
        SolarFestival("national_day", "国庆节", 10, 1, "山河无恙，人间值得。"),
        SolarFestival("christmas_eve", "平安夜", 12, 24, "今夜，愿你平安。"),
        SolarFestival("christmas", "圣诞节", 12, 25, "愿冬天也有好消息。"),
    )

    private val lunarFestivals = listOf(
        LunarFestival("spring_festival", "春节", 1, 1, "新岁，万事开场。"),
        LunarFestival("lantern_festival", "元宵节", 1, 15, "灯火照亮团圆。"),
        LunarFestival("dragon_head", "龙抬头", 2, 2, "春天，抬头见喜。"),
        LunarFestival("dragon_boat", "端午节", 5, 5, "粽叶青青，岁月安康。"),
        LunarFestival("qixi", "七夕节", 7, 7, "星河有信，心意有归。"),
        LunarFestival("ghost_festival", "中元节", 7, 15, "思念有声，岁月有痕。"),
        LunarFestival("mid_autumn", "中秋节", 8, 15, "月圆之时，记得想家。"),
        LunarFestival("double_ninth", "重阳节", 9, 9, "登高望远，岁岁安康。"),
        LunarFestival("laba", "腊八节", 12, 8, "腊八粥暖，年关将近。"),
    )

    fun forDate(date: LocalDate): FestivalGreeting? {
        val solar = solarFestivals.firstOrNull {
            it.month == date.monthValue && it.day == date.dayOfMonth
        } ?: qingmingFestival(date)
        if (solar != null) {
            return FestivalGreeting(solar.id, solar.name, solar.message)
        }

        val calendar = ChineseCalendar().apply {
            timeInMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val isLeapMonth = calendar.get(Calendar.IS_LEAP_MONTH) != 0
        if (!isLeapMonth) {
            val lunarFestival = lunarFestivals.firstOrNull { it.month == month && it.day == day }
            if (lunarFestival != null) {
                return FestivalGreeting(
                    lunarFestival.id,
                    lunarFestival.name,
                    lunarFestival.message,
                )
            }

            if (month == 12 && day == calendar.getActualMaximum(Calendar.DAY_OF_MONTH)) {
                return FestivalGreeting("lunar_new_year_eve", "除夕", "灯火为归人而亮。")
            }
        }
        return null
    }

    fun qingmingDate(year: Int): LocalDate? {
        if (year !in 1961..2050) return null
        val yearWithinCentury = year % 100
        val centuryOffset = if (year < 2000) 5.59 else 4.81
        val day = floor(yearWithinCentury * 0.2422 + centuryOffset).toInt() -
            yearWithinCentury / 4
        return LocalDate.of(year, 4, day)
    }

    private fun qingmingFestival(date: LocalDate): SolarFestival? {
        val qingmingDate = qingmingDate(date.year) ?: return null
        if (qingmingDate != date) return null
        return SolarFestival(
            id = "qingming",
            name = "清明节",
            month = 4,
            day = qingmingDate.dayOfMonth,
            message = "春风记得每一个名字。",
        )
    }
}

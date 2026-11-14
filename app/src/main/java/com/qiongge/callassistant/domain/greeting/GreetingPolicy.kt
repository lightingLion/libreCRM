package com.qiongge.callassistant.domain.greeting

import java.time.LocalDateTime

data class FestivalGreeting(
    val id: String,
    val name: String,
    val message: String,
)

data class GreetingResolution(
    val message: String,
    val festivalIdToRecord: String? = null,
    val festivalDateToRecord: String? = null,
    val isFestivalGreeting: Boolean = false,
)

object GreetingPolicy {
    fun regularGreeting(now: LocalDateTime, displayName: String?): String {
        val base = when (now.hour) {
            in 5..11 -> "早上好"
            in 12..17 -> "下午好"
            else -> "晚上好"
        }
        val name = displayName?.trim().orEmpty()
        return if (name.isBlank()) base else "$base，$name！"
    }

    fun resolveMenuGreeting(
        now: LocalDateTime,
        displayName: String?,
        lastFestivalId: String?,
        lastFestivalDate: String?,
        festival: FestivalGreeting?,
    ): GreetingResolution {
        val date = now.toLocalDate()
        val alreadyShownToday =
            festival != null &&
                lastFestivalId == festival.id &&
                lastFestivalDate == date.toString()

        if (festival != null && !alreadyShownToday) {
            return GreetingResolution(
                message = festival.message,
                festivalIdToRecord = festival.id,
                festivalDateToRecord = date.toString(),
                isFestivalGreeting = true,
            )
        }

        return GreetingResolution(message = regularGreeting(now, displayName))
    }
}

package com.qiongge.callassistant.domain.calling

/** Tracks a single outgoing attempt. Only a foreground, idle phone can advance it. */
internal class CallReturnCountdown(private val startedAt: Long) {
    private companion object {
        const val IDLE_STABILITY_MILLIS = 1_000L
    }

    private var leftForeground = false
    private var observedCall = false
    private var idleSince: Long? = null
    private var deadline: Long? = null
    var interrupted: Boolean = false
        private set

    fun onOutgoingActivity() {
        observedCall = true
        resetCountdown()
    }

    fun onIncomingCall() {
        interrupted = true
        deadline = null
    }

    fun onPause() {
        leftForeground = true
        resetCountdown()
    }

    fun resetCountdown() {
        idleSince = null
        deadline = null
    }

    /** Null means waiting, zero means the next call may start. */
    fun sample(now: Long, foreground: Boolean, inCall: Boolean, delaySeconds: Int): Int? {
        if (interrupted) return null
        if (inCall) observedCall = true
        if (!foreground || inCall) {
            resetCountdown()
            return null
        }
        if ((!leftForeground && !observedCall) || now - startedAt < IDLE_STABILITY_MILLIS) return null
        val idleStart = idleSince ?: now.also { idleSince = it }
        if (now - idleStart < IDLE_STABILITY_MILLIS) return null
        val end = deadline ?: (now + delaySeconds * 1_000L).also { deadline = it }
        return ((end - now + 999L) / 1_000L).toInt().coerceAtLeast(0)
    }
}

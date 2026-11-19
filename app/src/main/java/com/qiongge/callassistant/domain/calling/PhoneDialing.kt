package com.qiongge.callassistant.domain.calling

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Base64
import androidx.core.content.ContextCompat
import java.security.MessageDigest

internal data class DialSim(val key: String, val label: String, val handle: PhoneAccountHandle)

internal enum class PhoneCallState { IDLE, RINGING, BUSY, UNKNOWN }

internal class PhoneDialing(private val context: Context) {
    private val telecom = context.getSystemService(TelecomManager::class.java)
    private val telephony = context.getSystemService(TelephonyManager::class.java)
    private val subscriptions = context.getSystemService(SubscriptionManager::class.java)

    fun hasPhoneStatePermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_PHONE_STATE,
    ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun callState(): PhoneCallState {
        if (!hasPhoneStatePermission()) return PhoneCallState.UNKNOWN
        return when (readPhoneState { telephony?.callState }) {
            TelephonyManager.CALL_STATE_IDLE -> PhoneCallState.IDLE
            TelephonyManager.CALL_STATE_RINGING -> PhoneCallState.RINGING
            TelephonyManager.CALL_STATE_OFFHOOK -> PhoneCallState.BUSY
            else -> when (readPhoneState { telecom?.isInCall }) {
                true -> PhoneCallState.BUSY
                false -> PhoneCallState.IDLE
                null -> PhoneCallState.UNKNOWN
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun availableSims(): List<DialSim> {
        if (!hasPhoneStatePermission() || telephony == null) return emptyList()
        val handles = readPhoneState { telecom?.callCapablePhoneAccounts }.orEmpty()
        return handles.mapNotNull { handle ->
            val line = readPhoneState { telephony.createForPhoneAccountHandle(handle) } ?: return@mapNotNull null
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) readPhoneState {
                val id = telephony.getSubscriptionId(handle)
                if (id == SubscriptionManager.INVALID_SUBSCRIPTION_ID) null else subscriptions?.getActiveSubscriptionInfo(id)
            } else null
            val name = info?.displayName?.toString()?.takeIf(String::isNotBlank)
                ?: readPhoneState { line.simOperatorName }?.takeIf(String::isNotBlank)
            val slotLabel = info?.simSlotIndex?.takeIf { it >= 0 }?.let { "SIM ${it + 1}" }
            DialSim(keyFor(handle), listOfNotNull(slotLabel, name).joinToString(" · "), handle)
        }.mapIndexed { index, sim ->
            if (sim.label.startsWith("SIM ")) sim
            else sim.copy(label = listOf("通话卡 ${index + 1}", sim.label).filter(String::isNotBlank).joinToString(" · "))
        }
    }

    private fun keyFor(handle: PhoneAccountHandle): String {
        val identity = "${handle.componentName.flattenToString()}:${handle.id}"
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.encodeToByteArray())
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    private inline fun <T> readPhoneState(read: () -> T): T? = try {
        read()
    } catch (_: SecurityException) {
        null
    } catch (_: UnsupportedOperationException) {
        null
    }
}

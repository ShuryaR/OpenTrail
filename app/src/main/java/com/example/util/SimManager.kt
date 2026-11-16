package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

data class SimSlotInfo(
    val slotIndex: Int, // 0 = SIM 1, 1 = SIM 2
    val subscriptionId: Int,
    val displayName: String,
    val carrierName: String
)

enum class WorkSimPreference(val slotIndex: Int, val label: String) {
    ANY(-1, "Any SIM (System Default)"),
    SIM_1(0, "SIM 1 Only"),
    SIM_2(1, "SIM 2 Only");

    companion object {
        fun fromSlot(slotIndex: Int): WorkSimPreference =
            entries.firstOrNull { it.slotIndex == slotIndex } ?: ANY
    }
}

object SimManager {

    @SuppressLint("MissingPermission")
    fun getActiveSims(context: Context): List<SimSlotInfo> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }
        val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
            as? SubscriptionManager ?: return emptyList()
        val list = runCatching { subManager.activeSubscriptionInfoList }.getOrNull().orEmpty()
        return list.map { info ->
            SimSlotInfo(
                slotIndex = info.simSlotIndex,
                subscriptionId = info.subscriptionId,
                displayName = info.displayName?.toString().orEmpty().ifBlank { "SIM ${info.simSlotIndex + 1}" },
                carrierName = info.carrierName?.toString().orEmpty().ifBlank { "Cellular" }
            )
        }.sortedBy { it.slotIndex }
    }

    /**
     * Hands off a cellular phone call directly to the device's native SIM dialer using ACTION_CALL
     * (falling back to ACTION_DIAL if CALL_PHONE permission has not been granted yet).
     * When a preferred Work SIM slot is selected, attaches the matching Telecom PhoneAccountHandle.
     */
    @SuppressLint("MissingPermission")
    fun placeNativeSimCall(
        context: Context,
        phoneNumber: String,
        preferredSimSlot: WorkSimPreference = WorkSimPreference.ANY
    ): Boolean {
        val cleaned = phoneNumber.trim()
        if (cleaned.isBlank()) return false

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val action = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action, Uri.parse("tel:${Uri.encode(cleaned)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (hasCallPermission && preferredSimSlot != WorkSimPreference.ANY) {
            resolvePhoneAccountForSlot(context, preferredSimSlot.slotIndex)?.let { handle ->
                intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
            }
        }

        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    private fun resolvePhoneAccountForSlot(context: Context, targetSlot: Int): PhoneAccountHandle? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return null
        val accounts = runCatching { telecom.callCapablePhoneAccounts }.getOrNull().orEmpty()
        if (accounts.isEmpty()) return null

        val sims = getActiveSims(context)
        val matchingSubId = sims.firstOrNull { it.slotIndex == targetSlot }?.subscriptionId
        if (matchingSubId != null) {
            val matched = accounts.firstOrNull { handle ->
                handle.id.contains(matchingSubId.toString())
            }
            if (matched != null) return matched
        }
        return accounts.getOrNull(targetSlot)
    }
}

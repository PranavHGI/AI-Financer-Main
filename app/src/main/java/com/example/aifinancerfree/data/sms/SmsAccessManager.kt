package com.example.aifinancerfree.data.sms

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.aifinancerfree.data.local.ConsentPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for SMS ingestion access:
 * enabled = consentFlag (user agreement) && READ_SMS granted && RECEIVE_SMS granted.
 */
class SmsAccessManager(
    private val context: Context,
    private val consentPreferences: ConsentPreferences
) {
    private val _isAccessEnabled = MutableStateFlow(isAccessEnabled())
    val isAccessEnabled: StateFlow<Boolean> = _isAccessEnabled.asStateFlow()

    fun hasReadSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasReceiveSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasAllRequiredPermissions(): Boolean {
        return hasReadSmsPermission() && hasReceiveSmsPermission()
    }

    fun isConsentGranted(): Boolean {
        return consentPreferences.isSmsTrackingEnabled()
    }

    fun isAccessEnabled(): Boolean {
        return isConsentGranted() && hasAllRequiredPermissions()
    }

    fun setConsentEnabled(enabled: Boolean) {
        consentPreferences.setSmsTrackingEnabled(enabled)
        refresh()
    }

    fun clearConsent() {
        consentPreferences.clearConsent()
        refresh()
    }

    fun refresh() {
        _isAccessEnabled.value = isAccessEnabled()
    }

    /**
     * Programmatically schedules revocation of SMS runtime permissions on Android 13+ (API 33+).
     * The OS will automatically revoke these permissions.
     *
     * @return true if scheduled on API 33+, false if on older Android versions where system settings must be used.
     */
    fun revokeSmsPermissions(targetContext: Context = context): Boolean {
        var scheduled = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                targetContext.revokeSelfPermissionOnKill(Manifest.permission.READ_SMS)
                targetContext.revokeSelfPermissionOnKill(Manifest.permission.RECEIVE_SMS)
                Log.i("SmsAccessManager", "[PERMISSIONS REVOKED] Scheduled revokeSelfPermissionOnKill for READ_SMS & RECEIVE_SMS")
                scheduled = true
            } catch (e: Exception) {
                Log.e("SmsAccessManager", "[PERMISSIONS REVOKE ERROR] Failed to schedule permission revocation", e)
            }
        }
        return scheduled
    }

    /**
     * Opens the device's system Application Details settings page for this app,
     * allowing the user to immediately view or toggle OS-level runtime permissions.
     */
    fun openAppSettings(targetContext: Context = context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", targetContext.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            targetContext.startActivity(intent)
        } catch (e: Exception) {
            Log.e("SmsAccessManager", "[SETTINGS ERROR] Failed to launch application settings", e)
        }
    }
}

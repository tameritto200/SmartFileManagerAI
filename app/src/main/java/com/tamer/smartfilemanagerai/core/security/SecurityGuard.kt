package com.tamer.smartfilemanagerai.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

/**
 * Enforces Section 14 & Section 17 Security & Privacy rules:
 * - Protects external service API keys using Android Keystore & EncryptedSharedPreferences.
 * - Blocks access/modification to protected system directories (/system, /proc, Android/data, Android/obb).
 * - Prevents execution or installation of APK, EXE, DLL, SH, PY, JS files during scanning.
 * - Scrubs sensitive content from technical logs.
 */
object PathSecurityValidator {

    private val PROTECTED_SYSTEM_PREFIXES = listOf(
        "/system",
        "/proc",
        "/dev",
        "/sys",
        "/vendor",
        "/data/data",
        "/storage/emulated/0/Android/data",
        "/storage/emulated/0/Android/obb"
    )

    private val NON_EXECUTABLE_INSPECTION_EXTENSIONS = setOf(
        "apk", "exe", "dll", "so", "sh", "bat", "py", "js", "jar", "dex"
    )

    fun isProtectedSystemPath(pathOrUri: String): Boolean {
        val normalized = pathOrUri.replace("%2F", "/").lowercase()
        if (normalized.contains("android/data") || normalized.contains("android/obb")) {
            return true
        }
        return PROTECTED_SYSTEM_PREFIXES.any { prefix ->
            normalized.startsWith(prefix.lowercase())
        }
    }

    fun isSafeToModify(pathOrUri: String): Boolean {
        if (pathOrUri.isBlank()) return false
        return !isProtectedSystemPath(pathOrUri)
    }

    /**
     * Ensures technical/binary files are strictly inspected as passive metadata/streams
     * and never executed or launched during scanning or categorization.
     */
    fun requiresPassiveReadOnlyInspection(extension: String): Boolean {
        return extension.lowercase().trimStart('.') in NON_EXECUTABLE_INSPECTION_EXTENSIONS
    }

    fun sanitizeForLog(rawText: String): String {
        if (rawText.length <= 16) return "***REDACTED***"
        return rawText.take(8) + "...[REDACTED]"
    }
}

class EncryptedApiKeyStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs = EncryptedSharedPreferences.create(
        context,
        "secure_ai_keys",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveCloudApiKey(providerId: String, apiKey: String) {
        securePrefs.edit().putString("api_key_$providerId", apiKey.trim()).apply()
    }

    fun getCloudApiKey(providerId: String): String? {
        return securePrefs.getString("api_key_$providerId", null)?.takeIf { it.isNotBlank() }
    }

    fun clearCloudApiKey(providerId: String) {
        securePrefs.edit().remove("api_key_$providerId").apply()
    }

    fun setCloudConsentGranted(granted: Boolean) {
        securePrefs.edit().putBoolean("user_cloud_ai_consent", granted).apply()
    }

    fun isCloudConsentGranted(): Boolean {
        return securePrefs.getBoolean("user_cloud_ai_consent", false)
    }
}

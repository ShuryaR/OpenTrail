package com.example.data.local

import android.content.Context
import android.media.MediaRecorder
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.util.WorkSimPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.oneBookDataStore: DataStore<Preferences> by preferencesDataStore(name = "onebook_prefs")

enum class AudioSourceOption(val mediaRecorderSource: Int, val label: String, val hint: String) {
    VOICE_RECOGNITION(
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        "VOICE_RECOGNITION (Recommended)",
        "Best compatibility on Pixel, Motorola, and Android 10–16+ Accessibility recording."
    ),
    VOICE_COMMUNICATION(
        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        "VOICE_COMMUNICATION",
        "Optimized for VoIP/downlink routing on Samsung & Qualcomm devices."
    ),
    MIC(
        MediaRecorder.AudioSource.MIC,
        "MIC (Raw Microphone)",
        "Fallback raw mic capture if OEM hardware blocks voice streams."
    );

    companion object {
        fun fromName(name: String?): AudioSourceOption =
            entries.firstOrNull { it.name == name } ?: VOICE_RECOGNITION
    }
}

enum class PrivacyFilterMode(val label: String, val description: String) {
    ALL_CALLS(
        "Record & Sync All Calls",
        "Logs and uploads all cellular calls (excluding blocked personal numbers)."
    ),
    CRM_ONLY(
        "Only Sync Calls Matching Frappe CRM Numbers",
        "Ignores unknown/personal calls that do not match any Lead, Deal, or Contact."
    );

    companion object {
        fun fromName(name: String?): PrivacyFilterMode =
            entries.firstOrNull { it.name == name } ?: ALL_CALLS
    }
}

enum class AuthMode {
    API_TOKEN,
    SESSION_COOKIE
}

data class AuthSession(
    val baseUrl: String = "",
    val apiKey: String = "",
    val apiSecret: String = "",
    val sessionCookie: String = "",
    val csrfToken: String = "",
    val loggedInUser: String = "",
    val authMode: AuthMode = AuthMode.API_TOKEN
) {
    val isAuthenticated: Boolean
        get() = baseUrl.isNotBlank() && (
            (authMode == AuthMode.API_TOKEN && apiKey.isNotBlank() && apiSecret.isNotBlank()) ||
                (authMode == AuthMode.SESSION_COOKIE && sessionCookie.isNotBlank())
            )
}

data class AppSettings(
    val audioSource: AudioSourceOption = AudioSourceOption.VOICE_RECOGNITION,
    val oemWatcherEnabled: Boolean = false,
    val privacyFilterMode: PrivacyFilterMode = PrivacyFilterMode.ALL_CALLS,
    val workSimPreference: WorkSimPreference = WorkSimPreference.ANY,
    val onboardingCompleted: Boolean = false,
    val vowifiTipDismissed: Boolean = false,
    val lastSyncEpochMs: Long = 0L,
    val dailyCallGoal: Int = 20
)

/**
 * Encrypts sensitive Frappe API credentials & session cookies using Android Keystore (AES-256-GCM)
 * before persisting in Jetpack DataStore.
 */
class SecureCredentialStore(private val context: Context) {

    companion object {
        private const val KEY_ALIAS = "onebook_crm_master_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128

        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_ENC_API_KEY = stringPreferencesKey("enc_api_key")
        private val KEY_ENC_API_SECRET = stringPreferencesKey("enc_api_secret")
        private val KEY_ENC_SESSION_COOKIE = stringPreferencesKey("enc_session_cookie")
        private val KEY_ENC_CSRF_TOKEN = stringPreferencesKey("enc_csrf_token")
        private val KEY_LOGGED_IN_USER = stringPreferencesKey("logged_in_user")
        private val KEY_AUTH_MODE = stringPreferencesKey("auth_mode")
        private val KEY_AUDIO_SOURCE = stringPreferencesKey("audio_source")
        private val KEY_OEM_WATCHER = booleanPreferencesKey("oem_watcher")
        private val KEY_PRIVACY_FILTER = stringPreferencesKey("privacy_filter")
        private val KEY_WORK_SIM_SLOT = intPreferencesKey("work_sim_slot")
        private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        private val KEY_VOWIFI_DISMISSED = booleanPreferencesKey("vowifi_dismissed")
        private val KEY_LAST_SYNC_MS = longPreferencesKey("last_sync_ms")
        private val KEY_DAILY_CALL_GOAL = intPreferencesKey("daily_call_goal")
    }

    val authSessionFlow: Flow<AuthSession> = context.oneBookDataStore.data.map { prefs ->
        AuthSession(
            baseUrl = prefs[KEY_BASE_URL].orEmpty(),
            apiKey = decrypt(prefs[KEY_ENC_API_KEY]),
            apiSecret = decrypt(prefs[KEY_ENC_API_SECRET]),
            sessionCookie = decrypt(prefs[KEY_ENC_SESSION_COOKIE]),
            csrfToken = decrypt(prefs[KEY_ENC_CSRF_TOKEN]),
            loggedInUser = prefs[KEY_LOGGED_IN_USER].orEmpty(),
            authMode = prefs[KEY_AUTH_MODE]?.let { runCatching { AuthMode.valueOf(it) }.getOrNull() }
                ?: AuthMode.API_TOKEN
        )
    }

    val settingsFlow: Flow<AppSettings> = context.oneBookDataStore.data.map { prefs ->
        AppSettings(
            audioSource = AudioSourceOption.fromName(prefs[KEY_AUDIO_SOURCE]),
            oemWatcherEnabled = prefs[KEY_OEM_WATCHER] ?: false,
            privacyFilterMode = PrivacyFilterMode.fromName(prefs[KEY_PRIVACY_FILTER]),
            workSimPreference = WorkSimPreference.fromSlot(prefs[KEY_WORK_SIM_SLOT] ?: -1),
            onboardingCompleted = prefs[KEY_ONBOARDING_DONE] ?: false,
            vowifiTipDismissed = prefs[KEY_VOWIFI_DISMISSED] ?: false,
            lastSyncEpochMs = prefs[KEY_LAST_SYNC_MS] ?: 0L,
            dailyCallGoal = (prefs[KEY_DAILY_CALL_GOAL] ?: 20).coerceIn(5, 200)
        )
    }

    suspend fun getAuthSession(): AuthSession = authSessionFlow.first()

    suspend fun getSettings(): AppSettings = settingsFlow.first()

    suspend fun saveApiTokenCredentials(
        baseUrl: String,
        apiKey: String,
        apiSecret: String,
        loggedInUser: String
    ) {
        val cleanUrl = normalizeUrl(baseUrl)
        context.oneBookDataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = cleanUrl
            prefs[KEY_ENC_API_KEY] = encrypt(apiKey.trim())
            prefs[KEY_ENC_API_SECRET] = encrypt(apiSecret.trim())
            prefs.remove(KEY_ENC_SESSION_COOKIE)
            prefs.remove(KEY_ENC_CSRF_TOKEN)
            prefs[KEY_LOGGED_IN_USER] = loggedInUser
            prefs[KEY_AUTH_MODE] = AuthMode.API_TOKEN.name
        }
    }

    suspend fun saveSessionCredentials(
        baseUrl: String,
        sessionCookie: String,
        csrfToken: String,
        loggedInUser: String
    ) {
        val cleanUrl = normalizeUrl(baseUrl)
        context.oneBookDataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = cleanUrl
            prefs[KEY_ENC_SESSION_COOKIE] = encrypt(sessionCookie)
            prefs[KEY_ENC_CSRF_TOKEN] = encrypt(csrfToken)
            prefs.remove(KEY_ENC_API_KEY)
            prefs.remove(KEY_ENC_API_SECRET)
            prefs[KEY_LOGGED_IN_USER] = loggedInUser
            prefs[KEY_AUTH_MODE] = AuthMode.SESSION_COOKIE.name
        }
    }

    suspend fun clearSession() {
        context.oneBookDataStore.edit { prefs ->
            prefs.remove(KEY_BASE_URL)
            prefs.remove(KEY_ENC_API_KEY)
            prefs.remove(KEY_ENC_API_SECRET)
            prefs.remove(KEY_ENC_SESSION_COOKIE)
            prefs.remove(KEY_ENC_CSRF_TOKEN)
            prefs.remove(KEY_LOGGED_IN_USER)
            prefs.remove(KEY_AUTH_MODE)
            prefs[KEY_ONBOARDING_DONE] = false
        }
    }

    suspend fun setAudioSource(option: AudioSourceOption) {
        context.oneBookDataStore.edit { it[KEY_AUDIO_SOURCE] = option.name }
    }

    suspend fun setOemWatcherEnabled(enabled: Boolean) {
        context.oneBookDataStore.edit { it[KEY_OEM_WATCHER] = enabled }
    }

    suspend fun setPrivacyFilterMode(mode: PrivacyFilterMode) {
        context.oneBookDataStore.edit { it[KEY_PRIVACY_FILTER] = mode.name }
    }

    suspend fun setWorkSimPreference(pref: WorkSimPreference) {
        context.oneBookDataStore.edit { it[KEY_WORK_SIM_SLOT] = pref.slotIndex }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.oneBookDataStore.edit { it[KEY_ONBOARDING_DONE] = completed }
    }

    suspend fun setVowifiTipDismissed(dismissed: Boolean) {
        context.oneBookDataStore.edit { it[KEY_VOWIFI_DISMISSED] = dismissed }
    }

    suspend fun setLastSyncEpochMs(epochMs: Long) {
        context.oneBookDataStore.edit { it[KEY_LAST_SYNC_MS] = epochMs }
    }

    suspend fun setDailyCallGoal(goal: Int) {
        context.oneBookDataStore.edit { it[KEY_DAILY_CALL_GOAL] = goal.coerceIn(5, 200) }
    }

    private fun normalizeUrl(raw: String): String {
        var u = raw.trim().trimEnd('/')
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "https://$u"
        }
        return u
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        }.getOrDefault("")
    }

    private fun decrypt(encoded: String?): String {
        if (encoded.isNullOrBlank()) return ""
        return runCatching {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size <= 12) return ""
            val iv = combined.copyOfRange(0, 12)
            val cipherText = combined.copyOfRange(12, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        }.getOrDefault("")
    }
}

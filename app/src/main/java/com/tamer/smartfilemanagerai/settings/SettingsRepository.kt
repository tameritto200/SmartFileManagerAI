package com.tamer.smartfilemanagerai.settings

import com.tamer.smartfilemanagerai.core.model.NamingStyle
import com.tamer.smartfilemanagerai.core.security.EncryptedApiKeyStore
import com.tamer.smartfilemanagerai.data.local.SmartFileDatabase
import com.tamer.smartfilemanagerai.data.local.entity.UserSettingsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppPreferencesState(
    val darkThemeEnabled: Boolean = false,
    val defaultNamingStyle: NamingStyle = NamingStyle.DESCRIPTIVE,
    val customRenameTemplate: String = "{تصنيف}_{موضوع}_{تاريخ}",
    val cloudAiConsentGranted: Boolean = false,
    val hasConfiguredApiKey: Boolean = false,
    val voiceDialect: String = "ar-EG",
    val largeFileThresholdMb: Int = 50
)

class SettingsRepository(
    private val database: SmartFileDatabase,
    private val keyStore: EncryptedApiKeyStore
) {
    private val dao = database.searchVoiceAndSessionDao()

    suspend fun loadPreferences(): AppPreferencesState = withContext(Dispatchers.IO) {
        val dark = dao.getSettingValue("dark_theme")?.toBoolean() ?: false
        val styleStr = dao.getSettingValue("naming_style") ?: NamingStyle.DESCRIPTIVE.name
        val style = runCatching { NamingStyle.valueOf(styleStr) }.getOrDefault(NamingStyle.DESCRIPTIVE)
        val template = dao.getSettingValue("custom_template") ?: "{تصنيف}_{موضوع}_{تاريخ}"
        val dialect = dao.getSettingValue("voice_dialect") ?: "ar-EG"
        val threshold = dao.getSettingValue("large_file_mb")?.toIntOrNull() ?: 50

        AppPreferencesState(
            darkThemeEnabled = dark,
            defaultNamingStyle = style,
            customRenameTemplate = template,
            cloudAiConsentGranted = keyStore.isCloudConsentGranted(),
            hasConfiguredApiKey = keyStore.getCloudApiKey("gemini") != null,
            voiceDialect = dialect,
            largeFileThresholdMb = threshold
        )
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        dao.upsertSetting(UserSettingsEntity("dark_theme", enabled.toString()))
    }

    suspend fun setNamingStyle(style: NamingStyle) {
        dao.upsertSetting(UserSettingsEntity("naming_style", style.name))
    }

    suspend fun setCustomTemplate(template: String) {
        dao.upsertSetting(UserSettingsEntity("custom_template", template))
    }

    suspend fun setCloudConsentAndApiKey(consent: Boolean, apiKey: String?) {
        keyStore.setCloudConsentGranted(consent)
        if (!apiKey.isNullOrBlank()) {
            keyStore.saveCloudApiKey("gemini", apiKey)
        }
    }
}

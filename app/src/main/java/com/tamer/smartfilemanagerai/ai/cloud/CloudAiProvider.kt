package com.tamer.smartfilemanagerai.ai.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.tamer.smartfilemanagerai.core.model.ConfidenceLevel
import com.tamer.smartfilemanagerai.core.model.DetailedCategory
import com.tamer.smartfilemanagerai.core.result.AppError
import com.tamer.smartfilemanagerai.core.result.AppResult
import com.tamer.smartfilemanagerai.core.security.EncryptedApiKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class CloudDocumentInsights(
    val summaryAr: String,
    val detectedTopicAr: String,
    val suggestedCategory: DetailedCategory,
    val suggestedFileNameAr: String,
    val keywords: List<String>,
    val confidence: ConfidenceLevel
)

/**
 * Provider-independent Cloud AI interface (Section 3B) so the AI provider can be swapped
 * (Gemini, OpenAI, Claude, or self-hosted LLM) without rewriting the application.
 */
interface CloudAiProvider {
    val providerId: String
    val displayName: String

    suspend fun analyzeDocumentText(
        fileName: String,
        extractedTextPreview: String,
        explicitUserConsentGranted: Boolean
    ): AppResult<CloudDocumentInsights>
}

class GeminiCloudAiProvider(
    private val context: Context,
    private val keyStore: EncryptedApiKeyStore
) : CloudAiProvider {

    override val providerId: String = "gemini"
    override val displayName: String = "Google Gemini Cloud AI"

    override suspend fun analyzeDocumentText(
        fileName: String,
        extractedTextPreview: String,
        explicitUserConsentGranted: Boolean
    ): AppResult<CloudDocumentInsights> = withContext(Dispatchers.IO) {
        // Strict Privacy Gate (Section 3B & 17): Never upload anything without explicit user consent
        if (!explicitUserConsentGranted || !keyStore.isCloudConsentGranted()) {
            return@withContext AppResult.Error(AppError.UserConsentRequired())
        }

        if (!isNetworkConnected()) {
            return@withContext AppResult.Error(AppError.NetworkUnavailable())
        }

        val apiKey = keyStore.getCloudApiKey(providerId)
            ?: return@withContext AppResult.Error(
                AppError.UnexpectedError("لم يتم إعداد مفتاح API للذكاء السحابي في الخزنة المشفرة.")
            )

        try {
            val endpoint = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
            val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            val prompt = """
                أنت مساعد ذكي لتصنيف وتسمية الملفات باللغة العربية.
                اسم الملف الحالي: $fileName
                مقتطف النص المستخرج: ${extractedTextPreview.take(1500)}
                أرجع JSON فقط بالحقول التالية:
                summaryAr, detectedTopicAr, categoryCode, suggestedFileNameAr, keywords
            """.trimIndent()

            val payload = JSONObject().apply {
                put("contents", JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                ))
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }

            if (connection.responseCode !in 200..299) {
                return@withContext AppResult.Error(
                    AppError.UnexpectedError("Cloud AI HTTP ${connection.responseCode}")
                )
            }

            val responseBody = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val rootJson = JSONObject(responseBody)
            val textPart = rootJson
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                .orEmpty()

            val cleanedJson = textPart.substringAfter("{").substringBeforeLast("}")
            val parsed = runCatching { JSONObject("{$cleanedJson}") }.getOrNull()

            val summary = parsed?.optString("summaryAr")?.takeIf { it.isNotBlank() } ?: "تم تحليل المستند سحابياً"
            val topic = parsed?.optString("detectedTopicAr")?.takeIf { it.isNotBlank() } ?: "مستند محلل بالذكاء السحابي"
            val catCode = parsed?.optString("categoryCode").orEmpty()
            val suggestedName = parsed?.optString("suggestedFileNameAr")?.takeIf { it.isNotBlank() } ?: topic.replace(" ", "_")

            AppResult.Success(
                CloudDocumentInsights(
                    summaryAr = summary,
                    detectedTopicAr = topic,
                    suggestedCategory = DetailedCategory.fromId(catCode),
                    suggestedFileNameAr = suggestedName,
                    keywords = listOf(topic),
                    confidence = ConfidenceLevel.HIGH
                )
            )
        } catch (e: Exception) {
            AppResult.Error(AppError.UnexpectedError(e.message ?: "Cloud AI request failed"))
        }
    }

    private fun isNetworkConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

package com.tamer.smartfilemanagerai.search

import com.tamer.smartfilemanagerai.data.local.SmartFileDatabase
import com.tamer.smartfilemanagerai.data.local.entity.FileEntity
import com.tamer.smartfilemanagerai.data.local.entity.SearchHistoryEntity
import com.tamer.smartfilemanagerai.voice.EgyptianNlpCommandParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class SemanticSearchMatch(
    val file: FileEntity,
    val relevanceScore: Int,
    val matchedFieldsSummaryAr: String,
    val extractedSnippet: String?
)

/**
 * Section 8: Semantic Search Engine
 * Searches by content description and natural queries rather than filename alone:
 * - "ابحث عن الفيديو الذي يشرح تثبيت ويندوز 10."
 * - "أريد صورة إيصال الكهرباء."
 * - "اعرض كل ملفات Excel الخاصة بالمدرسة."
 * - "أين ملف PDF الذي يحتوي على كلمة صيانة؟"
 * - "ابحث عن تسجيل صوتي لاجتماع العمل."
 * - "اعرض الفيديوهات التي مدتها أكثر من 10 دقائق."
 */
class SemanticSearchEngine(
    private val database: SmartFileDatabase,
    private val nlpNormalizer: EgyptianNlpCommandParser = EgyptianNlpCommandParser()
) {

    suspend fun search(
        rawQuery: String,
        isVoiceQuery: Boolean = false
    ): List<SemanticSearchMatch> = withContext(Dispatchers.IO) {
        val normalizedQuery = nlpNormalizer.normalizeArabic(rawQuery)
        if (normalizedQuery.isBlank()) return@withContext emptyList()

        val allFiles = database.fileDao().observeAllFiles().first()
        val minDurationMs = parseMinimumDurationFilterMs(normalizedQuery)
        val requiredExtOrType = parseRequiredTypeFilter(normalizedQuery)
        val queryTokens = extractSearchKeywords(normalizedQuery)

        val matches = mutableListOf<SemanticSearchMatch>()

        for (file in allFiles) {
            val metadata = database.metadataAndAnalysisDao().getMetadataForFile(file.fileId)
            val analysis = database.metadataAndAnalysisDao().getAnalysisForFile(file.fileId)
            val tags = database.metadataAndAnalysisDao().getTagsForFile(file.fileId)

            // Apply strict duration filter if query asks e.g. "أكثر من 10 دقائق"
            if (minDurationMs != null) {
                val dur = metadata?.durationMs ?: 0L
                if (dur < minDurationMs) continue
            }

            // Apply file type filter if specified in query (e.g. PDF, Excel, فيديو, صورة, تسجيل صوتي)
            if (requiredExtOrType != null && !matchesRequiredType(file, requiredExtOrType)) {
                continue
            }

            var score = 0
            val matchedReasons = mutableListOf<String>()

            if (minDurationMs != null) {
                score += 40
                matchedReasons.add("المدة تتجاوز الحد المطلوب (${(metadata?.durationMs ?: 0L) / 60000} د)")
            }

            val normName = nlpNormalizer.normalizeArabic(file.currentName + " " + file.originalName)
            val normText = nlpNormalizer.normalizeArabic(metadata?.extractedText.orEmpty())
            val normAnalysis = nlpNormalizer.normalizeArabic(
                "${analysis?.summaryAr.orEmpty()} ${analysis?.detectedTopic.orEmpty()} ${tags.joinToString(" ")}"
            )

            for (token in queryTokens) {
                if (normName.contains(token)) {
                    score += 35
                    matchedReasons.add("اسم الملف ($token)")
                }
                if (normText.contains(token)) {
                    score += 50
                    matchedReasons.add("النص المستخرج داخل الملف ($token)")
                }
                if (normAnalysis.contains(token)) {
                    score += 45
                    matchedReasons.add("تحليل الذكاء الاصطناعي والتصنيف ($token)")
                }
            }

            if (queryTokens.isEmpty() && (minDurationMs != null || requiredExtOrType != null)) {
                score += 25
                matchedReasons.add("تطابق نوع الملف والخصائص")
            }

            if (score > 0) {
                matches.add(
                    SemanticSearchMatch(
                        file = file,
                        relevanceScore = score,
                        matchedFieldsSummaryAr = matchedReasons.distinct().joinToString(" + "),
                        extractedSnippet = metadata?.extractedText?.take(160)
                    )
                )
            }
        }

        val sorted = matches.sortedByDescending { it.relevanceScore }

        database.searchVoiceAndSessionDao().insertSearchHistory(
            SearchHistoryEntity(
                queryText = rawQuery,
                parsedIntentSummary = "الكلمات المفتاحية: ${queryTokens.joinToString(", ")}",
                resultsCount = sorted.size,
                isVoiceQuery = isVoiceQuery
            )
        )

        sorted
    }

    private fun parseMinimumDurationFilterMs(normalizedQuery: String): Long? {
        val regex = Regex("اكثر من\s*(\d+)\s*(دقائق|دقيقه)")
        val match = regex.find(normalizedQuery) ?: return null
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        return minutes * 60_000L
    }

    private fun parseRequiredTypeFilter(normalizedQuery: String): String? {
        return when {
            normalizedQuery.contains("pdf") -> "pdf"
            normalizedQuery.contains("excel") || normalizedQuery.contains("اكسيل") -> "excel"
            normalizedQuery.contains("فيديو") || normalizedQuery.contains("الفيديوهات") -> "video"
            normalizedQuery.contains("صوره") || normalizedQuery.contains("الصور") -> "image"
            normalizedQuery.contains("تسجيل صوتي") || normalizedQuery.contains("صوت") -> "audio"
            else -> null
        }
    }

    private fun matchesRequiredType(file: FileEntity, requiredType: String): Boolean {
        val ext = file.extension.lowercase()
        return when (requiredType) {
            "pdf" -> ext == "pdf"
            "excel" -> ext in setOf("xls", "xlsx", "csv", "ods")
            "video" -> file.primaryGroup == "VIDEOS" || file.mimeType.startsWith("video/")
            "image" -> file.primaryGroup == "IMAGES" || file.mimeType.startsWith("image/")
            "audio" -> file.primaryGroup == "AUDIO" || file.mimeType.startsWith("audio/")
            else -> true
        }
    }

    private fun extractSearchKeywords(normalizedQuery: String): List<String> {
        val boilerplate = setOf(
            "ابحث", "عن", "الفيديو", "الذي", "يشرح", "اريد", "صوره", "اعرض", "كل", "ملفات",
            "الخاصه", "اين", "ملف", "يحتوي", "علي", "كلمه", "تسجيل", "صوتي", "التي", "مدتها",
            "اكثر", "من", "دقائق", "دقيقه", "هات", "دور", "في", "اللي", "فيه", "الأسبوع", "الاسبوع", "فات",
            "pdf", "excel"
        )
        return normalizedQuery
            .replace(Regex("[^\p{L}\p{N}\s]"), " ")
            .split(Regex("\s+"))
            .map { it.trim() }
            .filter { it.length >= 2 && it !in boilerplate && !it.all { ch -> ch.isDigit() } }
            .distinct()
    }
}

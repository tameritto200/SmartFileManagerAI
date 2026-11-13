package com.tamer.smartfilemanagerai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class VoiceIntentType {
    ORGANIZE_ALL_DEVICE,
    ORGANIZE_VIDEOS_BY_CONTENT,
    CREATE_NAMED_FOLDER,
    MOVE_PDF_TO_DOCUMENTS,
    COLLECT_WHATSAPP_FILES,
    SEARCH_FILES_BY_DESCRIPTION,
    RENAME_VIDEOS_BY_CONTENT,
    SHOW_LARGE_FILES,
    SHOW_DUPLICATE_FILES,
    CREATE_FOLDER_PER_YEAR,
    SORT_IMAGES_BY_DATE,
    MOVE_EDUCATIONAL_FILES,
    SHOW_UNUSED_OLD_FILES,
    UNDO_LAST_OPERATION,
    PAUSE_ORGANIZATION,
    RESUME_ORGANIZATION,
    COMPOUND_MULTI_STEP_COMMAND,
    UNKNOWN_QUERY
}

data class VoiceExecutionStep(
    val stepOrder: Int,
    val intentType: VoiceIntentType,
    val descriptionAr: String,
    val targetFilter: String? = null,
    val targetFolderName: String? = null,
    val sortByYearOrDate: Boolean = false,
    val renameOpaqueOnly: Boolean = false
)

data class ParsedVoiceCommandPlan(
    val rawSpokenText: String,
    val detectedDialect: String,
    val isCompound: Boolean,
    val requiresUserConfirmation: Boolean,
    val summaryForTtsAr: String,
    val steps: List<VoiceExecutionStep>
)

/**
 * Section 6: Egyptian Arabic, Modern Standard Arabic, and English NLP Command Parser.
 * Handles all 17 single voice commands AND compound multi-step natural commands.
 * Enforces Voice Safety: requires confirmation before executing bulk/sensitive operations.
 */
class EgyptianNlpCommandParser {

    fun parseSpokenCommand(rawUtterance: String): ParsedVoiceCommandPlan {
        val text = normalizeArabic(rawUtterance)
        val dialect = detectDialect(rawUtterance)

        // Check for compound command (contains multiple clauses connected by "و" with multiple action verbs)
        val compoundSteps = tryParseCompoundCommand(text)
        if (compoundSteps.size > 1) {
            val summary = "تم تحليل أمرك المركب إلى ${compoundSteps.size} خطوات متتالية: " +
                compoundSteps.joinToString("، ثم ") { it.descriptionAr } +
                ". هل توافق على بدء التنفيذ؟"
            return ParsedVoiceCommandPlan(
                rawSpokenText = rawUtterance,
                detectedDialect = dialect,
                isCompound = true,
                requiresUserConfirmation = true,
                summaryForTtsAr = summary,
                steps = compoundSteps
            )
        }

        val singleStep = parseSingleClause(text, stepOrder = 1)
        val isReadOnlyQuery = singleStep.intentType in setOf(
            VoiceIntentType.SEARCH_FILES_BY_DESCRIPTION,
            VoiceIntentType.SHOW_LARGE_FILES,
            VoiceIntentType.SHOW_DUPLICATE_FILES,
            VoiceIntentType.SHOW_UNUSED_OLD_FILES,
            VoiceIntentType.PAUSE_ORGANIZATION,
            VoiceIntentType.RESUME_ORGANIZATION
        )

        return ParsedVoiceCommandPlan(
            rawSpokenText = rawUtterance,
            detectedDialect = dialect,
            isCompound = false,
            requiresUserConfirmation = !isReadOnlyQuery,
            summaryForTtsAr = if (isReadOnlyQuery) {
                "جاري تنفيذ: ${singleStep.descriptionAr}"
            } else {
                "ملخص العملية المطلوبة: ${singleStep.descriptionAr}. يرجى التأكيد صوتياً أو بالضغط على زر الموافقة."
            },
            steps = listOf(singleStep)
        )
    }

    private fun tryParseCompoundCommand(normalizedText: String): List<VoiceExecutionStep> {
        val clauses = normalizedText
            .split(Regex("[,،]|\s+و(?=اعمل|انشا|رتب|صنف|غير|سمي|انقل|هات|اجمع)"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (clauses.size <= 1) return emptyList()

        val steps = mutableListOf<VoiceExecutionStep>()
        var currentContextFilter: String? = null
        var currentTargetFolder: String? = null

        clauses.forEachIndexed { idx, clause ->
            val step = parseSingleClause(clause, idx + 1, currentContextFilter, currentTargetFolder)
            if (step.targetFilter != null) currentContextFilter = step.targetFilter
            if (step.targetFolderName != null) currentTargetFolder = step.targetFolderName
            steps.add(step)
        }
        return steps
    }

    private fun parseSingleClause(
        clause: String,
        stepOrder: Int,
        inheritedFilter: String? = null,
        inheritedFolder: String? = null
    ): VoiceExecutionStep {
        return when {
            clause.contains("تراجع") || clause.contains("ارجع في اخر عمليه") || clause.contains("undo") ->
                VoiceExecutionStep(stepOrder, VoiceIntentType.UNDO_LAST_OPERATION, "التراجع عن آخر عملية واستعادة الوضع السابق")

            clause.contains("اوقف التنظيم") || clause.contains("وقف التنظيم") || clause.contains("pause") ->
                VoiceExecutionStep(stepOrder, VoiceIntentType.PAUSE_ORGANIZATION, "إيقاف عملية الفحص والتنظيم مؤقتاً")

            clause.contains("استكمل عمليه التنظيم") || clause.contains("كمل التنظيم") || clause.contains("resume") ->
                VoiceExecutionStep(stepOrder, VoiceIntentType.RESUME_ORGANIZATION, "استكمال عملية الفحص والتنظيم")

            clause.contains("ملفات الكبيره") || clause.contains("اكبر الملفات") || clause.contains("large files") ->
                VoiceExecutionStep(stepOrder, VoiceIntentType.SHOW_LARGE_FILES, "عرض الملفات كبيرة الحجم")

            clause.contains("ملفات المكرره") || clause.contains("المتشابهه") || clause.contains("duplicates") ->
                VoiceExecutionStep(stepOrder, VoiceIntentType.SHOW_DUPLICATE_FILES, "عرض الملفات المكررة والمتشابهة")

            clause.contains("لم افتحها منذ") || clause.contains("مفتحتهاش من زمان") || clause.contains("قديمه") ->
                VoiceExecutionStep(stepOrder, VoiceIntentType.SHOW_UNUSED_OLD_FILES, "عرض الملفات التي لم يتم فتحها منذ فترة طويلة")

            (clause.contains("اعمل مجلد") || clause.contains("انشا مجلد")) && clause.contains("لكل سنه") ->
                VoiceExecutionStep(stepOrder, VoiceIntentType.CREATE_FOLDER_PER_YEAR, "إنشاء مجلد مستقل لكل سنة وترتيب الملفات حسب السنة", sortByYearOrDate = true)

            clause.contains("اعمل مجلد اسمه") || clause.contains("اعمل لها مجلد اسمه") || clause.contains("انشا مجلد باسم") -> {
                val folderName = clause.substringAfter("اسمه").substringAfter("باسم").trim()
                    .ifBlank { "مجلد جديد" }
                VoiceExecutionStep(
                    stepOrder = stepOrder,
                    intentType = VoiceIntentType.CREATE_NAMED_FOLDER,
                    descriptionAr = "إنشاء مجلد باسم '$folderName'",
                    targetFilter = inheritedFilter,
                    targetFolderName = folderName
                )
            }

            clause.contains("انقل كل ملفات pdf") || (clause.contains("pdf") && clause.contains("مجلد المستندات")) ->
                VoiceExecutionStep(
                    stepOrder = stepOrder,
                    intentType = VoiceIntentType.MOVE_PDF_TO_DOCUMENTS,
                    descriptionAr = "نقل جميع ملفات PDF إلى مجلد المستندات",
                    targetFilter = "pdf",
                    targetFolderName = "01- المستندات/PDF"
                )

            clause.contains("اجمع ملفات الواتساب") || clause.contains("ملفات واتساب") ->
                VoiceExecutionStep(
                    stepOrder = stepOrder,
                    intentType = VoiceIntentType.COLLECT_WHATSAPP_FILES,
                    descriptionAr = "تجميع كافة ملفات WhatsApp في مجلد منفصل",
                    targetFilter = "whatsapp",
                    targetFolderName = "ملفات WhatsApp المجمعة"
                )

            clause.contains("انقل كل الملفات التعليميه") || (clause.contains("الملفات التعليميه") && clause.contains("مجلد التعليم")) ->
                VoiceExecutionStep(
                    stepOrder = stepOrder,
                    intentType = VoiceIntentType.MOVE_EDUCATIONAL_FILES,
                    descriptionAr = "نقل جميع الملفات التعليمية إلى مجلد التعليم",
                    targetFilter = "educational",
                    targetFolderName = "التعليم"
                )

            clause.contains("رتب الصور حسب التاريخ") || (clause.contains("الصور") && clause.contains("التاريخ")) ->
                VoiceExecutionStep(
                    stepOrder = stepOrder,
                    intentType = VoiceIntentType.SORT_IMAGES_BY_DATE,
                    descriptionAr = "ترتيب وتنظيم الصور في مجلدات حسب التاريخ",
                    targetFilter = "images",
                    sortByYearOrDate = true
                )

            clause.contains("غير اسماء") || clause.contains("تسميه الملفات") -> {
                val opaqueOnly = clause.contains("ملهاش اسماء واضحه") || clause.contains("غير واضحه")
                VoiceExecutionStep(
                    stepOrder = stepOrder,
                    intentType = VoiceIntentType.RENAME_VIDEOS_BY_CONTENT,
                    descriptionAr = if (opaqueOnly) {
                        "إعادة تسمية الملفات ذات الأسماء المبهمة بأسماء توضح محتواها"
                    } else {
                        "إعادة تسمية الفيديوهات والملفات بأسماء توضح محتواها"
                    },
                    targetFilter = inheritedFilter ?: "videos",
                    targetFolderName = inheritedFolder,
                    renameOpaqueOnly = opaqueOnly
                )
            }

            clause.contains("رتب كل الفيديوهات") || clause.contains("رتبها حسب السنه") || (clause.contains("فيديوهات") && clause.contains("علاقه بالتعليم")) -> {
                val isEdu = clause.contains("تعليم") || inheritedFilter == "educational_videos"
                val byYear = clause.contains("السنه") || clause.contains("التاريخ")
                VoiceExecutionStep(
                    stepOrder = stepOrder,
                    intentType = VoiceIntentType.ORGANIZE_VIDEOS_BY_CONTENT,
                    descriptionAr = when {
                        byYear -> "ترتيب الفيديوهات المختارة في مجلدات فرعية حسب السنة"
                        isEdu -> "حصر جميع الفيديوهات المرتبطة بالتعليم"
                        else -> "ترتيب جميع الفيديوهات حسب محتواها"
                    },
                    targetFilter = if (isEdu) "educational_videos" else "videos",
                    targetFolderName = inheritedFolder,
                    sortByYearOrDate = byYear
                )
            }

            clause.contains("نظم كل ملفات") || clause.contains("نظم لي الموبايل كله") || clause.contains("رتب الموبايل") ->
                VoiceExecutionStep(
                    stepOrder = stepOrder,
                    intentType = VoiceIntentType.ORGANIZE_ALL_DEVICE,
                    descriptionAr = "تنظيم شامل لكل ملفات الهاتف (صور، فيديوهات، مستندات، صوتيات) مع اقتراح الأسماء والمجلدات"
                )

            clause.startsWith("هات ") || clause.startsWith("دور على ") || clause.startsWith("ابحث عن ") || clause.startsWith("فين ") || clause.startsWith("اين ") ->
                VoiceExecutionStep(
                    stepOrder = stepOrder,
                    intentType = VoiceIntentType.SEARCH_FILES_BY_DESCRIPTION,
                    descriptionAr = "البحث الذكي عن: $clause",
                    targetFilter = clause
                )

            else ->
                VoiceExecutionStep(
                    stepOrder = stepOrder,
                    intentType = VoiceIntentType.SEARCH_FILES_BY_DESCRIPTION,
                    descriptionAr = "البحث الذكي أو تنفيذ الطلب: $clause",
                    targetFilter = clause
                )
        }
    }

    private fun detectDialect(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.any { it in 'a'..'z' } && !lower.any { it in '؀'..'ۿ' } -> "en-US"
            listOf("هات", "دور على", "اللي", "ملهاش", "موبايل", "كله", "فين", "امبارح").any { lower.contains(it) } -> "ar-EG"
            else -> "ar-SA"
        }
    }

    fun normalizeArabic(input: String): String {
        return input.lowercase()
            .replace(Regex("[أإآ]"), "ا")
            .replace("ة", "ه")
            .replace("ى", "ي")
            .replace(Regex("[\u064B-\u065F]"), "") // strip diacritics
            .replace(Regex("\s+"), " ")
            .trim()
    }
}

class EgyptianVoiceAssistant(
    private val context: Context,
    private val nlpParser: EgyptianNlpCommandParser = EgyptianNlpCommandParser()
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _lastParsedPlan = MutableStateFlow<ParsedVoiceCommandPlan?>(null)
    val lastParsedPlan: StateFlow<ParsedVoiceCommandPlan?> = _lastParsedPlan.asStateFlow()

    init {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale("ar", "EG")
                isTtsReady = true
            }
        }
    }

    fun startListening(languageTag: String = "ar-EG") {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    _isListening.value = false
                }
                override fun onError(error: Int) {
                    _isListening.value = false
                }
                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    val spoken = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (spoken.isNotBlank()) {
                        processTextAsVoiceCommand(spoken)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer?.startListening(intent)
    }

    fun processTextAsVoiceCommand(spokenText: String): ParsedVoiceCommandPlan {
        val plan = nlpParser.parseSpokenCommand(spokenText)
        _lastParsedPlan.value = plan
        speakOutLoud(plan.summaryForTtsAr)
        return plan
    }

    fun speakOutLoud(messageAr: String) {
        if (isTtsReady) {
            textToSpeech?.speak(messageAr, TextToSpeech.QUEUE_FLUSH, null, "smart_fm_tts")
        }
    }

    fun stopAndRelease() {
        _isListening.value = false
        speechRecognizer?.destroy()
        speechRecognizer = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}

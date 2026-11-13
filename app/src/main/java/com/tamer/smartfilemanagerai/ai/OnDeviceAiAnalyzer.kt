package com.tamer.smartfilemanagerai.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tamer.smartfilemanagerai.core.model.ConfidenceLevel
import com.tamer.smartfilemanagerai.core.model.DetailedCategory
import com.tamer.smartfilemanagerai.core.security.PathSecurityValidator
import com.tamer.smartfilemanagerai.data.local.SmartFileDatabase
import com.tamer.smartfilemanagerai.data.local.entity.AIAnalysisEntity
import com.tamer.smartfilemanagerai.data.local.entity.FileEntity
import com.tamer.smartfilemanagerai.data.local.entity.TagEntity
import com.tamer.smartfilemanagerai.organizer.AiRenamingEngine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Section 3A & Section 4: On-Device AI Engine
 * Works 100% offline:
 * - Extracts OCR text from images using ML Kit
 * - Extracts text from PDFs using PDFBox-Android and text/CSV/code files directly
 * - Analyzes video metadata & keyframes (without claiming full video knowledge from filename alone)
 * - Computes 64-bit Perceptual Hash (dHash) for images
 * - Classifies files into 45+ detailed categories with calibrated confidence scores
 */
class OnDeviceAiAnalyzer(
    private val context: Context,
    private val database: SmartFileDatabase
) {
    private val renamingEngine = AiRenamingEngine()

    init {
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
        } catch (_: Exception) {
        }
    }

    suspend fun analyzeAndClassifyFile(fileEntity: FileEntity): AIAnalysisEntity = withContext(Dispatchers.IO) {
        val file = File(fileEntity.uriOrPath)
        val ext = fileEntity.extension.lowercase()
        val metadata = database.metadataAndAnalysisDao().getMetadataForFile(fileEntity.fileId)

        var extractedText = metadata?.extractedText ?: ""
        var pHash = metadata?.perceptualHash

        if (file.exists() && file.canRead() && !PathSecurityValidator.requiresPassiveReadOnlyInspection(ext)) {
            when {
                ext == "pdf" && extractedText.isBlank() -> {
                    extractedText = extractPdfText(file)
                }
                ext in setOf("txt", "csv", "rtf", "xml", "json", "html", "md", "sql") && extractedText.isBlank() -> {
                    extractedText = runCatching { file.readText().take(4000) }.getOrDefault("")
                }
                fileEntity.mimeType.startsWith("image/") -> {
                    if (extractedText.isBlank()) {
                        extractedText = runOcrOnImage(file)
                    }
                    if (pHash == null) {
                        pHash = computeImagePerceptualHash(file)
                    }
                }
                fileEntity.mimeType.startsWith("video/") && extractedText.isBlank() -> {
                    extractedText = extractVideoKeyframeTextAndMetadata(file, metadata?.mediaTitle)
                }
            }
        }

        if (metadata != null && (extractedText.isNotBlank() || pHash != null)) {
            database.metadataAndAnalysisDao().upsertMetadata(
                metadata.copy(
                    extractedText = extractedText.ifBlank { metadata.extractedText },
                    perceptualHash = pHash ?: metadata.perceptualHash
                )
            )
        }

        val classification = classifyContent(
            fileName = fileEntity.currentName,
            parentPath = fileEntity.parentFolderPath,
            ext = ext,
            mimeType = fileEntity.mimeType,
            extractedText = extractedText,
            durationMs = metadata?.durationMs,
            width = metadata?.width,
            height = metadata?.height
        )

        val keywords = extractKeywords(fileEntity.currentName, extractedText, classification.category)
        val renameSuggestion = renamingEngine.generateSuggestions(
            originalFileName = fileEntity.currentName,
            extension = ext,
            category = classification.category,
            detectedTopic = classification.topicAr,
            extractedText = extractedText,
            timestampMs = fileEntity.createdAt ?: fileEntity.lastModified,
            confidence = classification.confidenceLevel
        )

        val analysis = AIAnalysisEntity(
            fileId = fileEntity.fileId,
            engineType = "ON_DEVICE",
            providerName = "Local ML Kit + Heuristic Classifier",
            summaryAr = classification.summaryAr,
            detectedTopic = classification.topicAr,
            detectedCategoryCode = classification.category.id,
            confidenceScore = classification.score,
            confidenceLevel = classification.confidenceLevel.name,
            suggestedNameShort = renameSuggestion.shortName,
            suggestedNameDescriptive = renameSuggestion.descriptiveName,
            suggestedNameStructured = renameSuggestion.professionalStructuredName,
            keywordsCsv = keywords.joinToString(",")
        )

        database.metadataAndAnalysisDao().upsertAnalysis(analysis)
        database.metadataAndAnalysisDao().insertTags(
            keywords.map { TagEntity(fileId = fileEntity.fileId, tagName = it, source = "ON_DEVICE_AI") }
        )

        val categoryEntity = database.categoryDao().getCategoryByCode(classification.category.id)
        database.fileDao().updateFileClassification(
            fileId = fileEntity.fileId,
            categoryId = categoryEntity?.categoryId,
            primaryGroup = classification.category.primaryGroup.name,
            score = classification.score,
            level = classification.confidenceLevel.name
        )

        analysis
    }

    data class ClassificationResult(
        val category: DetailedCategory,
        val score: Float,
        val confidenceLevel: ConfidenceLevel,
        val topicAr: String,
        val summaryAr: String
    )

    /**
     * Section 4: Intelligent Categorization across Documents, Videos, Images, Audio, and Technical files.
     * Note: Never claims full video content certainty from an opaque filename alone (assigns LOW/MEDIUM confidence).
     */
    fun classifyContent(
        fileName: String,
        parentPath: String,
        ext: String,
        mimeType: String,
        extractedText: String,
        durationMs: Long?,
        width: Int?,
        height: Int?
    ): ClassificationResult {
        val cleanExt = ext.lowercase().trimStart('.')
        val combinedText = "$fileName $parentPath $extractedText".lowercase()

        // 1. Documents
        if (cleanExt in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "csv", "odt", "ods")) {
            return when {
                containsAny(combinedText, listOf("فاتورة", "ايصال", "إيصال", "كهرباء", "مياه", "غاز", "ضريبة", "invoice", "receipt", "bill")) ->
                    buildResult(DetailedCategory.DOC_INVOICE, 0.92f, "فاتورة أو إيصال مالي", "مستند يحتوي على بيانات فاتورة أو إيصال سداد")
                containsAny(combinedText, listOf("عقد", "اتفاقية", "إيجار", "تمليك", "تعاقد", "contract", "agreement", "lease")) ->
                    buildResult(DetailedCategory.DOC_CONTRACT, 0.91f, "عقد رسمي", "وثيقة تعاقدية أو اتفاقية قانونية")
                containsAny(combinedText, listOf("كشف حساب", "بنك", "رصيد", "تحويل بنكي", "bank statement", "iban", "account statement")) ->
                    buildResult(DetailedCategory.DOC_BANK_STATEMENT, 0.90f, "كشف حساب بنكي", "مستند مالي يوضح المعاملات البنكية")
                containsAny(combinedText, listOf("مدرسة", "طلاب", "درجات", "نتيجة", "جدول حصص", "تحضير", "الأصفر", "تلاميذ", "school", "grade", "student")) ->
                    buildResult(DetailedCategory.DOC_SCHOOL_RECORD, 0.93f, "سجل مدرسي", "ملف مدرسي أو كشف طلاب ودرجات")
                containsAny(combinedText, listOf("تقرير", "ميزانية", "أداء", "سنوي", "شهري", "صيانة", "متابعة", "report", "budget", "annual")) ->
                    buildResult(DetailedCategory.DOC_REPORT, 0.88f, extractSpecificTopic(combinedText, "تقرير عمل"), "تقرير تحليلي أو إداري مفصل")
                containsAny(combinedText, listOf("كتاب", "رواية", "فصل", "مؤلف", "دار نشر", "ebook", "chapter", "isbn")) ->
                    buildResult(DetailedCategory.DOC_EBOOK, 0.85f, "كتاب إلكتروني", "مرجع أو كتاب إلكتروني للقراءة")
                containsAny(combinedText, listOf("محاضرة", "درس", "منهج", "امتحان", "مراجعة", "شرح", "تثبيت", "تعليم", "course", "lecture", "exam", "tutorial")) ->
                    buildResult(DetailedCategory.DOC_EDUCATIONAL, 0.87f, "مادة تعليمية", "ملف تعليمي أو شرح دراسي")
                containsAny(combinedText, listOf("قرار", "إداري", "مذكرة", "خطاب", "تكليف", "محضر", "اجتماع", "official", "memo")) ->
                    buildResult(DetailedCategory.DOC_ADMINISTRATIVE, 0.86f, "مستند إداري", "مكاتبة أو وثيقة إدارية")
                containsAny(combinedText, listOf("سيرة ذاتية", "بطاقة", "شخصي", "جواز", "شهادة ميلاد", "cv", "resume", "personal")) ->
                    buildResult(DetailedCategory.DOC_PERSONAL, 0.84f, "مستند شخصي", "وثيقة شخصية أو سيرة ذاتية")
                cleanExt in setOf("xls", "xlsx", "csv", "ods") ->
                    buildResult(DetailedCategory.DOC_EXCEL_GENERAL, 0.78f, "جدول بيانات Excel", "ملف جداول بيانات وإحصائيات")
                cleanExt in setOf("doc", "docx", "odt", "rtf") ->
                    buildResult(DetailedCategory.DOC_WORD_GENERAL, 0.76f, "مستند نصي Word", "مستند نصي عام")
                cleanExt in setOf("ppt", "pptx") ->
                    buildResult(DetailedCategory.DOC_PPT_GENERAL, 0.80f, "عرض تقديمي", "ملف عرض تقديمي PowerPoint")
                cleanExt == "pdf" && extractedText.isNotBlank() ->
                    buildResult(DetailedCategory.DOC_WORK, 0.75f, "مستند PDF", "مستند PDF يحتوي على نص مستخرج")
                else ->
                    buildResult(DetailedCategory.DOC_PDF_GENERAL, 0.50f, "مستند غير محدد الموضوع", "مستند يحتاج إلى مراجعة لتحديد موضوعه بدقة")
            }
        }

        // 2. Videos
        if (cleanExt in setOf("mp4", "mkv", "avi", "mov", "3gp", "webm") || mimeType.startsWith("video/")) {
            return when {
                combinedText.contains("whatsapp") || fileName.startsWith("VID-", ignoreCase = true) && combinedText.contains("wa") ->
                    buildResult(DetailedCategory.VID_WHATSAPP, 0.92f, "فيديو واتساب", "فيديو وارد عبر تطبيق WhatsApp")
                combinedText.contains("telegram") ->
                    buildResult(DetailedCategory.VID_TELEGRAM, 0.92f, "فيديو تيليجرام", "فيديو وارد عبر تطبيق Telegram")
                containsAny(combinedText, listOf("screen", "recorder", "screenrecord", "تسجيل شاشة", "لقطة شاشة فيديو")) ->
                    buildResult(DetailedCategory.VID_SCREEN_RECORDING, 0.90f, "تسجيل شاشة", "فيديو مسجل لشاشة الهاتف")
                containsAny(combinedText, listOf("excel", "word", "windows", "ويندوز", "تثبيت", "برنامج", "برمجة", "شرح استخدام", "tutorial", "howto")) ->
                    buildResult(DetailedCategory.VID_TUTORIAL, 0.89f, extractVideoTutorialTopic(combinedText), "فيديو شرح تقني أو تعليم استخدام برنامج")
                containsAny(combinedText, listOf("درس", "محاضرة", "تعليم", "كورس", "مراجعة", "مدرسة", "education", "lesson", "course")) ->
                    buildResult(DetailedCategory.VID_EDUCATIONAL, 0.88f, "فيديو تعليمي", "محتوى مرئي تعليمي أو محاضرة")
                containsAny(combinedText, listOf("اجتماع", "مؤتمر", "عرض عمل", "meeting", "zoom", "teams")) ->
                    buildResult(DetailedCategory.VID_MEETING, 0.86f, "فيديو اجتماع", "تسجيل اجتماع عمل أو لقاء مهني")
                containsAny(combinedText, listOf("حفل", "عيد ميلاد", "فرح", "مناسبة", "تخرج", "wedding", "birthday", "party")) ->
                    buildResult(DetailedCategory.VID_EVENTS, 0.85f, "فيديو مناسبة", "توثيق مناسبة اجتماعية أو احتفال")
                containsAny(combinedText, listOf("عائلة", "أسرة", "أولاد", "رحلة", "family", "kids", "trip")) ->
                    buildResult(DetailedCategory.VID_FAMILY, 0.84f, "فيديو عائلي", "فيديو عائلي أو ذكريات")
                containsAny(combinedText, listOf("مضحك", "كوميدي", "funny", "meme", "reel")) ->
                    buildResult(DetailedCategory.VID_FUNNY, 0.82f, "فيديو ترفيهي", "مقطع فيديو ترفيهي قصير")
                // Opaque camera filenames like VID_20261110_123456.mp4 without extracted OCR/frame text:
                // Per Section 4 rule: Never claim full content certainty based only on an opaque filename!
                fileName.matches(Regex("""^(VID|MOV|DJI|VIDEO)[_-]?\d+.*""", RegexOption.IGNORE_CASE)) && extractedText.isBlank() ->
                    buildResult(DetailedCategory.VID_PERSONAL, 0.52f, "تسجيل كاميرا شخصي", "فيديو مسجل بالكاميرا (ثقة منخفضة لعدم وجود نص وصفي كافٍ)")
                else ->
                    buildResult(DetailedCategory.VID_PERSONAL, 0.65f, "فيديو عام", "ملف فيديو تم تحليله عبر البيانات الوصفية")
            }
        }

        // 3. Images
        if (cleanExt in setOf("jpg", "jpeg", "png", "webp", "gif", "heic") || mimeType.startsWith("image/")) {
            return when {
                containsAny(combinedText, listOf("screenshot", "لقطة شاشة", "screen_shot")) ->
                    buildResult(DetailedCategory.IMG_SCREENSHOT, 0.94f, "لقطة شاشة", "صورة ملتقطة لشاشة الهاتف")
                combinedText.contains("whatsapp") || fileName.contains("-WA", ignoreCase = true) ->
                    buildResult(DetailedCategory.IMG_WHATSAPP, 0.92f, "صورة واتساب", "صورة مستلمة عبر تطبيق WhatsApp")
                containsAny(combinedText, listOf("عقد", "فاتورة", "إيصال", "ايصال", "بطاقة", "مستند", "إداري", "شهادة", "تقرير", "كشف", "invoice", "contract", "receipt")) || extractedText.length > 60 ->
                    buildResult(DetailedCategory.IMG_SCANNED_DOC, 0.89f, extractImageDocTopic(combinedText), "صورة مستند أو إيصال تم التعرف على نصه عبر OCR")
                containsAny(combinedText, listOf("تصميم", "شعار", "لوجو", "بوستر", "بنر", "logo", "design", "banner", "poster", "mockup")) ->
                    buildResult(DetailedCategory.IMG_DESIGN, 0.86f, "تصميم جرافيك", "صورة تصميم أو شعار")
                containsAny(combinedText, listOf("منتج", "سلعة", "سعر", "كتالوج", "product", "catalog", "item")) ->
                    buildResult(DetailedCategory.IMG_PRODUCT, 0.84f, "صورة منتج", "صورة توضيحية لمنتج")
                containsAny(combinedText, listOf("درس", "سبورة", "شرح", "جدول", "تعليم", "مدرسة", "education", "diagram")) ->
                    buildResult(DetailedCategory.IMG_EDUCATIONAL, 0.85f, "صورة تعليمية", "صورة تعليمية أو مخطط توضيحي")
                containsAny(combinedText, listOf("عائلة", "أسرة", "رحلة", "family")) ->
                    buildResult(DetailedCategory.IMG_FAMILY, 0.83f, "صورة عائلية", "صورة عائلية")
                else ->
                    buildResult(DetailedCategory.IMG_PERSONAL, 0.72f, "صورة شخصية", "صورة فوتوغرافية ملتقطة بالكاميرا")
            }
        }

        // 4. Audio
        if (cleanExt in setOf("mp3", "wav", "aac", "m4a", "ogg", "flac") || mimeType.startsWith("audio/")) {
            return when {
                combinedText.contains("whatsapp") || fileName.startsWith("AUD-", ignoreCase = true) || cleanExt == "ogg" && combinedText.contains("ptt") ->
                    buildResult(DetailedCategory.AUD_WHATSAPP, 0.93f, "تسجيل صوتي واتساب", "رسالة صوتية عبر WhatsApp")
                containsAny(combinedText, listOf("اجتماع", "لقاء", "عمل", "meeting", "conference")) ->
                    buildResult(DetailedCategory.AUD_MEETING, 0.88f, "تسجيل اجتماع العمل", "تسجيل صوتي لاجتماع أو مناقشة عمل")
                containsAny(combinedText, listOf("محاضرة", "درس", "شرح", "ندوة", "lecture", "class", "lesson")) ->
                    buildResult(DetailedCategory.AUD_LECTURE, 0.89f, "تسجيل محاضرة", "محاضرة صوتية أو درس مسجل")
                containsAny(combinedText, listOf("كتاب صوتي", "رواية", "audiobook", "chapter")) ->
                    buildResult(DetailedCategory.AUD_AUDIOBOOK, 0.86f, "كتاب صوتي", "محتوى كتاب مسموع")
                containsAny(combinedText, listOf("موسيقى", "أغنية", "نشيد", "music", "song", "track")) ->
                    buildResult(DetailedCategory.AUD_MUSIC, 0.84f, "ملف موسيقي", "مقطع صوتي أو موسيقي")
                else ->
                    buildResult(DetailedCategory.AUD_VOICE_REC, 0.78f, "تسجيل صوتي", "تسجيل صوتي عام")
            }
        }

        // 5. Technical & Compressed
        return when {
            cleanExt == "apk" ->
                buildResult(DetailedCategory.TECH_APK, 0.98f, "حزمة تطبيق أندرويد", "ملف تثبيت تطبيق APK (فحص آمن دون تثبيت)")
            cleanExt in setOf("zip", "rar", "7z") ->
                buildResult(DetailedCategory.TECH_ARCHIVE, 0.96f, "أرشيف مضغوط", "ملف مضغوط يحتوي على حزمة ملفات")
            cleanExt in setOf("sql", "db", "sqlite") ->
                buildResult(DetailedCategory.TECH_DATABASE, 0.95f, "قاعدة بيانات", "ملف قاعدة بيانات أو استعلامات SQL")
            cleanExt in setOf("xml", "json", "html", "css", "js", "py", "java", "kt", "kotlin") ->
                buildResult(DetailedCategory.TECH_CODE, 0.95f, "كود برمجي (${cleanExt.uppercase()})", "ملف مصدري برمجي أو ملف إعدادات تقني")
            cleanExt in setOf("dll", "exe") ->
                buildResult(DetailedCategory.TECH_BINARY, 0.95f, "ملف ثنائي تنفيذي", "ملف نظام أو مكتبة برمجية (فحص للقراءة فقط)")
            else ->
                buildResult(DetailedCategory.UNCATEGORIZED_REVIEW, 0.40f, "ملف غير مصنف", "ملف يحتاج إلى مراجعة المستخدم لتحديد تصنيفه")
        }
    }

    private fun buildResult(
        category: DetailedCategory,
        score: Float,
        topicAr: String,
        summaryAr: String
    ): ClassificationResult {
        return ClassificationResult(
            category = category,
            score = score,
            confidenceLevel = ConfidenceLevel.fromScore(score),
            topicAr = topicAr,
            summaryAr = summaryAr
        )
    }

    private fun containsAny(text: String, terms: List<String>): Boolean {
        return terms.any { term -> text.contains(term.lowercase()) }
    }

    private fun extractSpecificTopic(text: String, fallback: String): String {
        return when {
            text.contains("ميزانية") || text.contains("budget") -> "تقرير الميزانية السنوية"
            text.contains("صيانة") || text.contains("maintenance") -> "تقرير الصيانة الدورية"
            text.contains("مدرسة") || text.contains("طلاب") -> "تقرير المدرسة والطلاب"
            else -> fallback
        }
    }

    private fun extractVideoTutorialTopic(text: String): String {
        return when {
            text.contains("excel") || text.contains("اكسيل") || text.contains("إكسيل") -> "شرح استخدام برنامج Excel"
            text.contains("windows 10") || text.contains("ويندوز 10") || text.contains("تثبيت ويندوز") -> "شرح تثبيت ويندوز 10"
            text.contains("word") || text.contains("وورد") -> "شرح برنامج Word"
            else -> "شرح استخدام البرامج"
        }
    }

    private fun extractImageDocTopic(text: String): String {
        return when {
            text.contains("كهرباء") -> "إيصال الكهرباء"
            text.contains("عقد") -> "صورة عقد"
            text.contains("إداري") || text.contains("اداري") || text.contains("قرار") -> "صورة مستند إداري"
            text.contains("فاتورة") -> "صورة فاتورة"
            else -> "صورة مستند"
        }
    }

    private fun extractKeywords(fileName: String, extractedText: String, category: DetailedCategory): List<String> {
        val stopWords = setOf("من", "في", "على", "إلى", "عن", "مع", "هذا", "هذه", "التي", "الذي", "the", "and", "for", "with", "doc", "img", "vid")
        val tokens = "$fileName $extractedText ${category.arabicName}"
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .split(Regex("""\s+"""))
            .map { it.trim().lowercase() }
            .filter { it.length >= 3 && it !in stopWords }
            .distinct()
            .take(12)
        return tokens.ifEmpty { listOf(category.arabicName) }
    }

    private fun extractPdfText(file: File): String {
        return try {
            PDDocument.load(file).use { doc ->
                val stripper = PDFTextStripper()
                stripper.startPage = 1
                stripper.endPage = minOf(5, doc.numberOfPages)
                stripper.getText(doc).trim().take(4000)
            }
        } catch (_: Exception) {
            ""
        }
    }

    private suspend fun runOcrOnImage(file: File): String {
        return try {
            val image = InputImage.fromFilePath(context, Uri.fromFile(file))
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val result = recognizer.process(image).await()
            result.text.trim().take(2000)
        } catch (_: Exception) {
            ""
        }
    }

    private suspend fun extractVideoKeyframeTextAndMetadata(file: File, mediaTitle: String?): String {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val title = mediaTitle ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty()
            val frameBitmap = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            val ocrText = if (frameBitmap != null) {
                val inputImage = InputImage.fromBitmap(frameBitmap, 0)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                runCatching { recognizer.process(inputImage).await().text }.getOrDefault("")
            } else {
                ""
            }
            "$title $ocrText".trim()
        } catch (_: Exception) {
            mediaTitle.orEmpty()
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Computes a 64-bit Difference Hash (dHash) for perceptual image similarity detection.
     */
    fun computeImagePerceptualHash(file: File): String? {
        return try {
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
            val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
            var hash = 0UL
            var bitIndex = 0
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    val leftPixel = scaled.getPixel(x, y)
                    val rightPixel = scaled.getPixel(x + 1, y)
                    val leftGray = ((leftPixel shr 16 and 0xff) * 30 + (leftPixel shr 8 and 0xff) * 59 + (leftPixel and 0xff) * 11) / 100
                    val rightGray = ((rightPixel shr 16 and 0xff) * 30 + (rightPixel shr 8 and 0xff) * 59 + (rightPixel and 0xff) * 11) / 100
                    if (leftGray > rightGray) {
                        hash = hash or (1UL shl bitIndex)
                    }
                    bitIndex++
                }
            }
            hash.toString(16).padStart(16, '0')
        } catch (_: Exception) {
            null
        }
    }
}

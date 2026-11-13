package com.tamer.smartfilemanagerai.core.model

enum class PrimaryFileGroup(val arabicTitle: String, val folderPrefix: String) {
    DOCUMENTS("المستندات", "01- المستندات"),
    VIDEOS("الفيديوهات", "02- الفيديوهات"),
    IMAGES("الصور", "03- الصور"),
    AUDIO("الصوتيات", "04- الصوتيات"),
    TECHNICAL("الملفات التقنية", "05- الملفات التقنية"),
    COMPRESSED("الملفات المضغوطة", "06- الملفات المضغوطة"),
    UNCATEGORIZED("ملفات غير مصنفة", "07- ملفات غير مصنفة")
}

enum class DetailedCategory(
    val id: String,
    val arabicName: String,
    val englishName: String,
    val primaryGroup: PrimaryFileGroup,
    val subFolderName: String
) {
    // 1. Documents
    DOC_PERSONAL("doc_personal", "مستندات شخصية", "Personal Documents", PrimaryFileGroup.DOCUMENTS, "مستندات شخصية"),
    DOC_WORK("doc_work", "مستندات عمل", "Work Documents", PrimaryFileGroup.DOCUMENTS, "مستندات عمل"),
    DOC_EDUCATIONAL("doc_educational", "ملفات تعليمية", "Educational Files", PrimaryFileGroup.DOCUMENTS, "ملفات تعليمية"),
    DOC_EBOOK("doc_ebook", "كتب إلكترونية", "E-Books", PrimaryFileGroup.DOCUMENTS, "كتب إلكترونية"),
    DOC_INVOICE("doc_invoice", "فواتير", "Invoices", PrimaryFileGroup.DOCUMENTS, "فواتير"),
    DOC_CONTRACT("doc_contract", "عقود", "Contracts", PrimaryFileGroup.DOCUMENTS, "عقود"),
    DOC_REPORT("doc_report", "تقارير", "Reports", PrimaryFileGroup.DOCUMENTS, "تقارير"),
    DOC_BANK_STATEMENT("doc_bank_statement", "كشوف حساب", "Bank Statements", PrimaryFileGroup.DOCUMENTS, "كشوف حساب"),
    DOC_SCHOOL_RECORD("doc_school_record", "سجلات مدرسية", "School Records", PrimaryFileGroup.DOCUMENTS, "سجلات مدرسية"),
    DOC_ADMINISTRATIVE("doc_administrative", "ملفات إدارية", "Administrative Files", PrimaryFileGroup.DOCUMENTS, "ملفات إدارية"),
    DOC_TECHNICAL("doc_technical", "ملفات تقنية", "Technical Docs", PrimaryFileGroup.DOCUMENTS, "ملفات تقنية"),
    DOC_PDF_GENERAL("doc_pdf", "ملفات PDF", "PDF Files", PrimaryFileGroup.DOCUMENTS, "PDF"),
    DOC_WORD_GENERAL("doc_word", "ملفات Word", "Word Files", PrimaryFileGroup.DOCUMENTS, "Word"),
    DOC_EXCEL_GENERAL("doc_excel", "ملفات Excel", "Excel Files", PrimaryFileGroup.DOCUMENTS, "Excel"),
    DOC_PPT_GENERAL("doc_ppt", "عروض PowerPoint", "PowerPoint Files", PrimaryFileGroup.DOCUMENTS, "PowerPoint"),

    // 2. Videos
    VID_PERSONAL("vid_personal", "فيديوهات شخصية", "Personal Videos", PrimaryFileGroup.VIDEOS, "شخصية"),
    VID_FAMILY("vid_family", "فيديوهات العائلة", "Family Videos", PrimaryFileGroup.VIDEOS, "العائلة"),
    VID_WORK("vid_work", "فيديوهات العمل", "Work Videos", PrimaryFileGroup.VIDEOS, "العمل"),
    VID_EDUCATIONAL("vid_educational", "فيديوهات تعليمية", "Educational Videos", PrimaryFileGroup.VIDEOS, "تعليمية"),
    VID_TUTORIAL("vid_tutorial", "شروحات البرامج", "Software Tutorials", PrimaryFileGroup.VIDEOS, "شروحات البرامج"),
    VID_SCREEN_RECORDING("vid_screen_rec", "تسجيلات الشاشة", "Screen Recordings", PrimaryFileGroup.VIDEOS, "تسجيلات الشاشة"),
    VID_WHATSAPP("vid_whatsapp", "فيديوهات WhatsApp", "WhatsApp Videos", PrimaryFileGroup.VIDEOS, "WhatsApp"),
    VID_TELEGRAM("vid_telegram", "فيديوهات Telegram", "Telegram Videos", PrimaryFileGroup.VIDEOS, "Telegram"),
    VID_EVENTS("vid_events", "فيديوهات المناسبات", "Event Videos", PrimaryFileGroup.VIDEOS, "المناسبات"),
    VID_FUNNY("vid_funny", "الفيديوهات المضحكة", "Funny Videos", PrimaryFileGroup.VIDEOS, "مضحكة"),
    VID_MEETING("vid_meeting", "فيديوهات الاجتماعات", "Meeting Videos", PrimaryFileGroup.VIDEOS, "الاجتماعات"),

    // 3. Images
    IMG_PERSONAL("img_personal", "صور شخصية", "Personal Photos", PrimaryFileGroup.IMAGES, "شخصية"),
    IMG_FAMILY("img_family", "صور عائلية", "Family Photos", PrimaryFileGroup.IMAGES, "عائلية"),
    IMG_WORK("img_work", "صور العمل", "Work Images", PrimaryFileGroup.IMAGES, "العمل"),
    IMG_SCANNED_DOC("img_scanned_doc", "مستندات مصورة", "Scanned Documents", PrimaryFileGroup.IMAGES, "مستندات"),
    IMG_SCREENSHOT("img_screenshot", "لقطات شاشة", "Screenshots", PrimaryFileGroup.IMAGES, "لقطات الشاشة"),
    IMG_WHATSAPP("img_whatsapp", "صور WhatsApp", "WhatsApp Images", PrimaryFileGroup.IMAGES, "WhatsApp"),
    IMG_EDUCATIONAL("img_educational", "صور تعليمية", "Educational Images", PrimaryFileGroup.IMAGES, "تعليمية"),
    IMG_PRODUCT("img_product", "صور المنتجات", "Product Images", PrimaryFileGroup.IMAGES, "المنتجات"),
    IMG_DESIGN("img_design", "صور التصميمات", "Design Images", PrimaryFileGroup.IMAGES, "تصميمات"),

    // 4. Audio
    AUD_VOICE_REC("aud_voice_rec", "تسجيلات صوتية", "Voice Recordings", PrimaryFileGroup.AUDIO, "تسجيلات"),
    AUD_LECTURE("aud_lecture", "محاضرات", "Lectures", PrimaryFileGroup.AUDIO, "محاضرات"),
    AUD_MEETING("aud_meeting", "اجتماعات", "Meetings", PrimaryFileGroup.AUDIO, "اجتماعات"),
    AUD_MUSIC("aud_music", "موسيقى", "Music", PrimaryFileGroup.AUDIO, "موسيقى"),
    AUD_AUDIOBOOK("aud_audiobook", "كتب صوتية", "Audiobooks", PrimaryFileGroup.AUDIO, "كتب صوتية"),
    AUD_WHATSAPP("aud_whatsapp", "تسجيلات WhatsApp", "WhatsApp Audio", PrimaryFileGroup.AUDIO, "WhatsApp"),
    AUD_VOICE_NOTE("aud_voice_note", "رسائل صوتية", "Voice Notes", PrimaryFileGroup.AUDIO, "رسائل صوتية"),
    AUD_EDUCATIONAL("aud_educational", "ملفات تعليمية", "Educational Audio", PrimaryFileGroup.AUDIO, "تعليمية"),

    // 5. Technical & Compressed
    TECH_APK("tech_apk", "تطبيقات أندرويد APK", "Android APKs", PrimaryFileGroup.TECHNICAL, "APK"),
    TECH_ARCHIVE("tech_archive", "ملفات مضغوطة", "Compressed Archives", PrimaryFileGroup.COMPRESSED, "أرشيف"),
    TECH_CODE("tech_code", "ملفات البرمجة", "Source Code Files", PrimaryFileGroup.TECHNICAL, "ملفات البرمجة"),
    TECH_DATABASE("tech_database", "قواعد البيانات", "Databases", PrimaryFileGroup.TECHNICAL, "قواعد البيانات"),
    TECH_BINARY("tech_binary", "ملفات تنفيذية ومكتبات", "System Binaries", PrimaryFileGroup.TECHNICAL, "ملفات النظام"),

    // 6. Uncategorized / Low Confidence
    UNCATEGORIZED_REVIEW("uncategorized", "ملفات غير مصنفة (للمراجعة)", "Uncategorized (Review)", PrimaryFileGroup.UNCATEGORIZED, "قائمة المراجعة");

    companion object {
        fun fromId(id: String): DetailedCategory =
            entries.firstOrNull { it.id == id } ?: UNCATEGORIZED_REVIEW
    }
}

enum class ConfidenceLevel(val arabicLabel: String, val minScore: Float) {
    HIGH("ثقة مرتفعة", 0.80f),
    MEDIUM("ثقة متوسطة", 0.55f),
    LOW("ثقة منخفضة", 0.0f);

    companion object {
        fun fromScore(score: Float): ConfidenceLevel = when {
            score >= HIGH.minScore -> HIGH
            score >= MEDIUM.minScore -> MEDIUM
            else -> LOW
        }
    }
}

enum class NamingStyle(val arabicLabel: String) {
    SHORT("اسم مختصر"),
    DESCRIPTIVE("اسم وصفي"),
    PROFESSIONAL_STRUCTURED("اسم احترافي منظم بالتاريخ والتصنيف")
}

enum classNamingLanguage {
    ARABIC,
    ENGLISH
}

data class RenameSuggestion(
    val originalName: String,
    val extension: String,
    val shortName: String,
    val descriptiveName: String,
    val professionalStructuredName: String,
    val confidence: ConfidenceLevel,
    val reasonAr: String
) {
    fun getNameForStyle(style: NamingStyle): String = when (style) {
        NamingStyle.SHORT -> shortName
        NamingStyle.DESCRIPTIVE -> descriptiveName
        NamingStyle.PROFESSIONAL_STRUCTURED -> professionalStructuredName
    }
}

enum class OperationType {
    RENAME,
    MOVE,
    ORGANIZE_BATCH,
    CREATE_FOLDER
}

enum class OperationStatus {
    PENDING,
    COMPLETED,
    PARTIAL_FAILURE,
    ROLLED_BACK,
    FAILED
}

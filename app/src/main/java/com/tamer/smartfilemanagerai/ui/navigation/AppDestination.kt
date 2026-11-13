package com.tamer.smartfilemanagerai.ui.navigation

enum class AppDestination(val route: String, val titleAr: String) {
    DASHBOARD("dashboard", "الرئيسية (لوحة التحكم)"),
    ALL_FILES("all_files", "جميع الملفات"),
    DOCUMENTS("documents", "المستندات"),
    IMAGES("images", "الصور"),
    VIDEOS("videos", "الفيديوهات"),
    AUDIO("audio", "الصوتيات"),
    APKS("apks", "التطبيقات وملفات APK"),
    COMPRESSED("compressed", "الملفات المضغوطة"),
    FOLDERS("folders", "المجلدات"),
    DUPLICATES("duplicates", "الملفات المكررة"),
    LARGE_FILES("large_files", "الملفات الكبيرة"),
    UNCATEGORIZED("uncategorized", "الملفات غير المصنفة"),
    VOICE_ASSISTANT("voice_assistant", "المساعد الصوتي المصري"),
    SEMANTIC_SEARCH("semantic_search", "البحث الذكي بالمحتوى"),
    ORGANIZATION_PLAN("organization_plan", "خطة التنظيم والمعاينة"),
    OPERATION_HISTORY("operation_history", "سجل العمليات والتراجع"),
    SETTINGS("settings", "الإعدادات"),
    AI_MANAGEMENT("ai_management", "إدارة الذكاء الاصطناعي"),
    PERMISSIONS_MANAGER("permissions_manager", "إدارة الصلاحيات والتخزين")
}

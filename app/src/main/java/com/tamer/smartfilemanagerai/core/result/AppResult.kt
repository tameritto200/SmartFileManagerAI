package com.tamer.smartfilemanagerai.core.result

sealed class AppError(val messageAr: String, val technicalDetail: String? = null) {
    class PermissionDenied(path: String) :
        AppError("لا توجد صلاحية للوصول إلى المسار: $path. يرجى منح الصلاحية عبر إطار الوصول للتخزين (SAF).", path)

    class ProtectedSystemPath(path: String) :
        AppError("المسار محمي بواسطة نظام Android (مثل Android/data أو Android/obb أو مجلدات النظام) ولا يمكن تعديله.", path)

    class InsufficientStorage(requiredBytes: Long, availableBytes: Long) :
        AppError("مساحة التخزين غير كافية. المطلوب: ${requiredBytes / 1024} ك.ب، المتاح: ${availableBytes / 1024} ك.ب.")

    class CorruptedOrUnreadableFile(fileName: String, cause: String?) :
        AppError("تعذر قراءة الملف '$fileName' لأنه تالف أو قيد الاستخدام من تطبيق آخر.", cause)

    class NetworkUnavailable :
        AppError("الاتصال بالإنترنت غير متاح حالياً. يعمل التطبيق بالذكاء الاصطناعي المحلي (On-Device AI).")

    class UserConsentRequired :
        AppError("يلزم موافقة صريحة من المستخدم قبل إرسال أي بيانات إلى خدمة الذكاء الاصطناعي السحابي.")

    class ExternalModificationDetected(fileName: String) :
        AppError("لا يمكن التراجع عن العملية للملف '$fileName' لأنه تم حذفه أو تعديله من خارج التطبيق.")

    class UnexpectedError(reason: String) :
        AppError("حدث خطأ غير متوقع: $reason", reason)
}

sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val error: AppError) : AppResult<Nothing>()
}

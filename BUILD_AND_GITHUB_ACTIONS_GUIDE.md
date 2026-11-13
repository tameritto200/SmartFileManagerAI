# دليل البناء التلقائي عبر GitHub Actions وإنتاج ملف APK

تم تجهيز مشروع **SmartFileManagerAI** بملف إعداد تلقائي في المسار:
`.github/workflows/android-build.yml`

## خطوات إنتاج ملف الـ APK سحابيًا (بدون الحاجة لـ Android Studio)

1. أنشئ مستودعًا جديدًا (Repository) على حسابك في **GitHub** (مثلاً باسم `SmartFileManagerAI`).
2. فك ضغط ملف `SmartFileManagerAI_GitHub_Ready.zip` وارفع **جميع الملفات والمجلدات الموجودة بداخله** (تأكد من وجود مجلد `.github` المخفي) إلى المستودع.
3. بعد الرفع مباشرة، افتح تبويب **Actions** في صفحة المستودع على GitHub.
4. ستجد عملية البناء **Build & Test SmartFileManagerAI APK** قد بدأت تلقائيًا (ويمكنك تشغيلها يدويًا في أي وقت بالضغط على **Run workflow**).
5. عند انتهاء البناء بنجاح (علامة صح خضراء)، انزل إلى قسم **Artifacts** في أسفل صفحة التشغيل لتحميل:
   - **`SmartFileManagerAI-Debug-APK`**: ملف `app-debug.apk` الجاهز للتثبيت المباشر على هاتفك (OPPO Reno 12 - Android 16).
   - **`SmartFileManagerAI-Release-APK`**: ملف `app-release-unsigned.apk`.
   - **`Unit-Test-Reports`**: تقارير الاختبارات الآلية الـ 15.

## التثبيت على الهاتف وإعداد الصلاحيات
- فعّل خيار **التثبيت من مصادر غير معروفة (Install unknown apps)** للمتصفح أو مدير الملفات عند تثبيت `app-debug.apk`.
- بعد فتح التطبيق:
  1. امنح صلاحية الوصول للمجلدات عبر **Storage Access Framework (SAF)** باختيار المجلدات التي تريد تنظيمها (مثل `Download` أو `Documents` أو `Pictures`).
  2. امنح صلاحية الميكروفون لتشغيل **المساعد الصوتي باللهجة المصرية**.
  3. (اختياري) من صفحة **إدارة الذكاء الاصطناعي** يمكنك تفعيل الذكاء السحابي وإدخال مفتاح API الخاص بك (يُحفظ مشفرًا عبر Android Keystore).

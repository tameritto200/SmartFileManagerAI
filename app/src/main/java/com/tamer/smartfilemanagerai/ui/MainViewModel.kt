package com.tamer.smartfilemanagerai.ui

import android.app.Application
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.tamer.smartfilemanagerai.ai.OnDeviceAiAnalyzer
import com.tamer.smartfilemanagerai.ai.cloud.GeminiCloudAiProvider
import com.tamer.smartfilemanagerai.core.model.NamingStyle
import com.tamer.smartfilemanagerai.core.model.PrimaryFileGroup
import com.tamer.smartfilemanagerai.core.result.AppResult
import com.tamer.smartfilemanagerai.core.security.EncryptedApiKeyStore
import com.tamer.smartfilemanagerai.data.local.SmartFileDatabase
import com.tamer.smartfilemanagerai.data.local.entity.FileEntity
import com.tamer.smartfilemanagerai.data.local.entity.FileOperationEntity
import com.tamer.smartfilemanagerai.data.local.entity.FolderEntity
import com.tamer.smartfilemanagerai.data.local.entity.VoiceCommandEntity
import com.tamer.smartfilemanagerai.duplicate.DuplicateCluster
import com.tamer.smartfilemanagerai.duplicate.DuplicateDetectionEngine
import com.tamer.smartfilemanagerai.history.UndoRecoveryManager
import com.tamer.smartfilemanagerai.organizer.AiRenamingEngine
import com.tamer.smartfilemanagerai.organizer.OrganizationPreviewPlan
import com.tamer.smartfilemanagerai.organizer.SmartOrganizerEngine
import com.tamer.smartfilemanagerai.scanner.FileScanWorker
import com.tamer.smartfilemanagerai.scanner.FileScannerEngine
import com.tamer.smartfilemanagerai.scanner.ScanProgressUiState
import com.tamer.smartfilemanagerai.search.SemanticSearchEngine
import com.tamer.smartfilemanagerai.search.SemanticSearchMatch
import com.tamer.smartfilemanagerai.settings.AppPreferencesState
import com.tamer.smartfilemanagerai.settings.SettingsRepository
import com.tamer.smartfilemanagerai.storage.SafStorageManager
import com.tamer.smartfilemanagerai.storage.StorageSpaceInfo
import com.tamer.smartfilemanagerai.voice.EgyptianNlpCommandParser
import com.tamer.smartfilemanagerai.voice.EgyptianVoiceAssistant
import com.tamer.smartfilemanagerai.voice.ParsedVoiceCommandPlan
import com.tamer.smartfilemanagerai.voice.VoiceIntentType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SmartFileDatabase.getInstance(application)
    private val keyStore = EncryptedApiKeyStore(application)
    val storageManager = SafStorageManager(application)
    val scannerEngine = FileScannerEngine(application, db)
    val onDeviceAi = OnDeviceAiAnalyzer(application, db)
    val cloudAi = GeminiCloudAiProvider(application, keyStore)
    val renamingEngine = AiRenamingEngine()
    val organizerEngine = SmartOrganizerEngine(db, storageManager, renamingEngine)
    val searchEngine = SemanticSearchEngine(db)
    val duplicateEngine = DuplicateDetectionEngine(db)
    val undoManager = UndoRecoveryManager(db, storageManager)
    val voiceAssistant = EgyptianVoiceAssistant(application)
    val nlpParser = EgyptianNlpCommandParser()
    val settingsRepo = SettingsRepository(db, keyStore)

    // Reactive Database Flows
    val allFiles: StateFlow<List<FileEntity>> = db.fileDao().observeAllFiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val documentFiles: StateFlow<List<FileEntity>> = db.fileDao().observeFilesByPrimaryGroup(PrimaryFileGroup.DOCUMENTS.name)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val imageFiles: StateFlow<List<FileEntity>> = db.fileDao().observeFilesByPrimaryGroup(PrimaryFileGroup.IMAGES.name)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val videoFiles: StateFlow<List<FileEntity>> = db.fileDao().observeFilesByPrimaryGroup(PrimaryFileGroup.VIDEOS.name)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val audioFiles: StateFlow<List<FileEntity>> = db.fileDao().observeFilesByPrimaryGroup(PrimaryFileGroup.AUDIO.name)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val technicalFiles: StateFlow<List<FileEntity>> = db.fileDao().observeFilesByPrimaryGroup(PrimaryFileGroup.TECHNICAL.name)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val compressedFiles: StateFlow<List<FileEntity>> = db.fileDao().observeFilesByPrimaryGroup(PrimaryFileGroup.COMPRESSED.name)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uncategorizedFiles: StateFlow<List<FileEntity>> = db.fileDao().observeLowConfidenceFiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val largeFiles: StateFlow<List<FileEntity>> = db.fileDao().observeLargeFiles(50L * 1024 * 1024)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<FolderEntity>> = db.folderDao().observeAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val operationHistory: StateFlow<List<FileOperationEntity>> = db.operationDao().observeAllOperations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scanProgress: StateFlow<ScanProgressUiState> = scannerEngine.progressState

    private val _storageSpace = MutableStateFlow(storageManager.getDeviceStorageSpaceInfo())
    val storageSpace: StateFlow<StorageSpaceInfo> = _storageSpace.asStateFlow()

    private val _duplicateClusters = MutableStateFlow<List<DuplicateCluster>>(emptyList())
    val duplicateClusters: StateFlow<List<DuplicateCluster>> = _duplicateClusters.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SemanticSearchMatch>>(emptyList())
    val searchResults: StateFlow<List<SemanticSearchMatch>> = _searchResults.asStateFlow()

    private val _currentPreviewPlan = MutableStateFlow<OrganizationPreviewPlan?>(null)
    val currentPreviewPlan: StateFlow<OrganizationPreviewPlan?> = _currentPreviewPlan.asStateFlow()

    private val _statusBannerMessageAr = MutableStateFlow<String?>(null)
    val statusBannerMessageAr: StateFlow<String?> = _statusBannerMessageAr.asStateFlow()

    private val _preferences = MutableStateFlow(AppPreferencesState())
    val preferences: StateFlow<AppPreferencesState> = _preferences.asStateFlow()

    init {
        viewModelScope.launch {
            _preferences.value = settingsRepo.loadPreferences()
            _storageSpace.value = storageManager.getDeviceStorageSpaceInfo()
        }
    }

    fun startComprehensiveScan(customPath: String? = null) {
        val targetPath = customPath ?: Environment.getExternalStorageDirectory().absolutePath
        viewModelScope.launch {
            _statusBannerMessageAr.value = "جاري الفحص الشامل وتحليل الملفات بالذكاء الاصطناعي المحلي..."
            val session = scannerEngine.scanDirectoryRoot(targetPath, computeFullSha256 = true)
            val currentList = db.fileDao().observeAllFiles().first()
            for (file in currentList) {
                onDeviceAi.analyzeAndClassifyFile(file)
            }
            _duplicateClusters.value = duplicateEngine.detectAllDuplicates()
            _storageSpace.value = storageManager.getDeviceStorageSpaceInfo()
            _statusBannerMessageAr.value = "اكتمل الفحص الشامل: تم فحص ${session.processedCount} ملف واكتشاف ${session.newFilesCount} ملف جديد."
        }

        // Also schedule via WorkManager for background resilience
        val request = OneTimeWorkRequestBuilder<FileScanWorker>()
            .setInputData(workDataOf(FileScanWorker.KEY_ROOT_PATH to targetPath))
            .build()
        WorkManager.getInstance(getApplication()).enqueueUniqueWork(
            FileScanWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun generateSmartOrganizationPlan(namingStyle: NamingStyle = _preferences.value.defaultNamingStyle) {
        viewModelScope.launch {
            val rootPath = Environment.getExternalStorageDirectory().absolutePath
            val plan = organizerEngine.buildOrganizationPreviewPlan(
                rootStoragePath = rootPath,
                applySmartRename = true,
                namingStyle = namingStyle
            )
            _currentPreviewPlan.value = plan
            _statusBannerMessageAr.value = "تم إعداد خطة التنظيم الذكي (${plan.totalAffectedCount} ملف جاهز للتنفيذ، و${plan.reviewQueueItems.size} ملف في قائمة المراجعة)."
        }
    }

    fun toggleIgnoreFileInPlan(fileId: Long) {
        val current = _currentPreviewPlan.value ?: return
        val updatedItems = current.items.map { item ->
            if (item.fileId == fileId) item.copy(isIgnoredByUser = !item.isIgnoredByUser) else item
        }
        _currentPreviewPlan.value = current.copy(items = updatedItems)
    }

    fun approveAndExecuteCurrentPlan() {
        val plan = _currentPreviewPlan.value ?: return
        viewModelScope.launch {
            val summary = organizerEngine.executeApprovedPlan(plan)
            _currentPreviewPlan.value = null
            _statusBannerMessageAr.value =
                "تم تنفيذ الخطة بنجاح: تم تنظيم ${summary.succeededCount} ملف (${summary.skippedReviewCount} ملف تُرك للمراجعة اليدوية)."
        }
    }

    fun cancelCurrentPlan() {
        _currentPreviewPlan.value = null
        _statusBannerMessageAr.value = "تم إلغاء خطة التنظيم دون إجراء أي تعديل على الملفات."
    }

    fun runSemanticSearch(query: String, isVoice: Boolean = false) {
        viewModelScope.launch {
            val results = searchEngine.search(query, isVoiceQuery = isVoice)
            _searchResults.value = results
            _statusBannerMessageAr.value = "نتائج البحث الذكي عن '$query': تم العثور على ${results.size} ملف مطابق."
        }
    }

    fun refreshDuplicateAnalysis() {
        viewModelScope.launch {
            val clusters = duplicateEngine.detectAllDuplicates()
            _duplicateClusters.value = clusters
            _statusBannerMessageAr.value = "تم فحص التكرار: وُجدت ${clusters.size} مجموعات لملفات متطابقة أو متشابهة."
        }
    }

    fun undoLastAction() {
        viewModelScope.launch {
            when (val res = undoManager.undoLastOperation()) {
                is AppResult.Success -> {
                    _statusBannerMessageAr.value = "تم التراجع بنجاح واستعادة '${res.data.previousName}' لمكانه الأصلي."
                }
                is AppResult.Error -> {
                    _statusBannerMessageAr.value = res.error.messageAr
                }
            }
        }
    }

    fun undoBatch(batchId: String) {
        viewModelScope.launch {
            val summary = undoManager.undoBatchOperations(batchId)
            _statusBannerMessageAr.value = "تم استعادة ${summary.restoredCount} ملف من الدفعة $batchId."
        }
    }

    fun submitSpokenOrTypedVoiceCommand(commandText: String) {
        viewModelScope.launch {
            val plan = voiceAssistant.processTextAsVoiceCommand(commandText)
            db.searchVoiceAndSessionDao().insertVoiceCommand(
                VoiceCommandEntity(
                    spokenText = commandText,
                    detectedDialect = plan.detectedDialect,
                    parsedActionType = plan.steps.firstOrNull()?.intentType?.name ?: "UNKNOWN",
                    isCompound = plan.isCompound,
                    executionPlanJson = plan.steps.joinToString(" | ") { it.descriptionAr },
                    wasApproved = !plan.requiresUserConfirmation
                )
            )
            if (!plan.requiresUserConfirmation) {
                executeApprovedVoicePlan(plan)
            } else {
                _statusBannerMessageAr.value = plan.summaryForTtsAr
            }
        }
    }

    fun executeApprovedVoicePlan(plan: ParsedVoiceCommandPlan) {
        viewModelScope.launch {
            val rootPath = Environment.getExternalStorageDirectory().absolutePath
            for (step in plan.steps) {
                when (step.intentType) {
                    VoiceIntentType.ORGANIZE_ALL_DEVICE -> generateSmartOrganizationPlan()
                    VoiceIntentType.ORGANIZE_VIDEOS_BY_CONTENT -> {
                        val vids = videoFiles.value
                        _currentPreviewPlan.value = organizerEngine.buildOrganizationPreviewPlan(
                            rootStoragePath = rootPath,
                            targetFiles = vids,
                            applySmartRename = true,
                            organizeByYearSubfolder = step.sortByYearOrDate,
                            customTargetFolderOverride = step.targetFolderName
                        )
                    }
                    VoiceIntentType.CREATE_NAMED_FOLDER -> {
                        val folderName = step.targetFolderName ?: "مجلد جديد"
                        storageManager.ensureDirectoryPath(rootPath, folderName)
                        _statusBannerMessageAr.value = "تم إنشاء المجلد '$folderName' بنجاح."
                    }
                    VoiceIntentType.MOVE_PDF_TO_DOCUMENTS -> {
                        val pdfs = allFiles.value.filter { it.extension.equals("pdf", ignoreCase = true) }
                        _currentPreviewPlan.value = organizerEngine.buildOrganizationPreviewPlan(
                            rootStoragePath = rootPath,
                            targetFiles = pdfs,
                            applySmartRename = false,
                            customTargetFolderOverride = "01- المستندات/PDF"
                        )
                    }
                    VoiceIntentType.COLLECT_WHATSAPP_FILES -> {
                        val waFiles = allFiles.value.filter {
                            it.currentName.contains("whatsapp", ignoreCase = true) ||
                                it.currentName.contains("-WA", ignoreCase = true) ||
                                it.parentFolderPath.contains("WhatsApp", ignoreCase = true)
                        }
                        _currentPreviewPlan.value = organizerEngine.buildOrganizationPreviewPlan(
                            rootStoragePath = rootPath,
                            targetFiles = waFiles,
                            applySmartRename = false,
                            customTargetFolderOverride = "ملفات WhatsApp"
                        )
                    }
                    VoiceIntentType.SEARCH_FILES_BY_DESCRIPTION -> {
                        runSemanticSearch(step.targetFilter ?: plan.rawSpokenText, isVoice = true)
                    }
                    VoiceIntentType.RENAME_VIDEOS_BY_CONTENT -> {
                        val targetList = if (step.targetFilter == "educational_videos") {
                            videoFiles.value.filter { it.currentName.contains("شرح") || it.currentName.contains("تعليم") }
                        } else {
                            videoFiles.value
                        }
                        _currentPreviewPlan.value = organizerEngine.buildOrganizationPreviewPlan(
                            rootStoragePath = rootPath,
                            targetFiles = targetList,
                            applySmartRename = true,
                            onlyRenameOpaqueFiles = step.renameOpaqueOnly,
                            organizeByYearSubfolder = step.sortByYearOrDate,
                            customTargetFolderOverride = step.targetFolderName
                        )
                    }
                    VoiceIntentType.SHOW_LARGE_FILES -> {
                        _statusBannerMessageAr.value = "تم فتح قائمة الملفات الكبيرة (${largeFiles.value.size} ملف)."
                    }
                    VoiceIntentType.SHOW_DUPLICATE_FILES -> {
                        refreshDuplicateAnalysis()
                    }
                    VoiceIntentType.CREATE_FOLDER_PER_YEAR -> {
                        _currentPreviewPlan.value = organizerEngine.buildOrganizationPreviewPlan(
                            rootStoragePath = rootPath,
                            organizeByYearSubfolder = true
                        )
                    }
                    VoiceIntentType.SORT_IMAGES_BY_DATE -> {
                        _currentPreviewPlan.value = organizerEngine.buildOrganizationPreviewPlan(
                            rootStoragePath = rootPath,
                            targetFiles = imageFiles.value,
                            organizeByYearSubfolder = true
                        )
                    }
                    VoiceIntentType.MOVE_EDUCATIONAL_FILES -> {
                        val eduFiles = allFiles.value.filter {
                            it.currentName.contains("تعليم") || it.currentName.contains("شرح") || it.currentName.contains("درس")
                        }
                        _currentPreviewPlan.value = organizerEngine.buildOrganizationPreviewPlan(
                            rootStoragePath = rootPath,
                            targetFiles = eduFiles,
                            customTargetFolderOverride = "التعليم"
                        )
                    }
                    VoiceIntentType.SHOW_UNUSED_OLD_FILES -> {
                        val ninetyDaysAgo = System.currentTimeMillis() - (90L * 24 * 3600 * 1000)
                        val oldFiles = db.fileDao().getUnusedFilesSince(ninetyDaysAgo)
                        _statusBannerMessageAr.value = "تم العثور على ${oldFiles.size} ملف لم يتم فتحه منذ أكثر من 90 يوماً."
                    }
                    VoiceIntentType.UNDO_LAST_OPERATION -> undoLastAction()
                    VoiceIntentType.PAUSE_ORGANIZATION -> {
                        scannerEngine.pauseScan()
                        _statusBannerMessageAr.value = "تم إيقاف الفحص والتنظيم مؤقتاً."
                    }
                    VoiceIntentType.RESUME_ORGANIZATION -> {
                        scannerEngine.resumeScan()
                        _statusBannerMessageAr.value = "تم استكمال عملية الفحص والتنظيم."
                    }
                    else -> {}
                }
            }
        }
    }

    fun updateDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setDarkTheme(enabled)
            _preferences.value = settingsRepo.loadPreferences()
        }
    }

    fun updateNamingStyle(style: NamingStyle) {
        viewModelScope.launch {
            settingsRepo.setNamingStyle(style)
            _preferences.value = settingsRepo.loadPreferences()
        }
    }

    fun updateCloudAiConfig(consent: Boolean, apiKey: String?) {
        viewModelScope.launch {
            settingsRepo.setCloudConsentAndApiKey(consent, apiKey)
            _preferences.value = settingsRepo.loadPreferences()
            _statusBannerMessageAr.value = if (consent) {
                "تم حفظ إعدادات الذكاء الاصطناعي السحابي وتشفير المفتاح عبر Android Keystore."
            } else {
                "تم تفعيل الوضع المحلي الكامل (On-Device AI Only) دون إرسال أي بيانات للخارج."
            }
        }
    }

    fun onSafTreeGranted(uri: Uri) {
        storageManager.persistTreePermission(uri)
        _statusBannerMessageAr.value = "تم منح وحفظ صلاحية الوصول للمجلد عبر إطار الوصول للتخزين (SAF)."
    }

    override fun onCleared() {
        super.onCleared()
        voiceAssistant.stopAndRelease()
    }
}

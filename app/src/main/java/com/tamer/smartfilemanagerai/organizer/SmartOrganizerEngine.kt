package com.tamer.smartfilemanagerai.organizer

import com.tamer.smartfilemanagerai.core.model.ConfidenceLevel
import com.tamer.smartfilemanagerai.core.model.DetailedCategory
import com.tamer.smartfilemanagerai.core.model.NamingStyle
import com.tamer.smartfilemanagerai.core.model.PrimaryFileGroup
import com.tamer.smartfilemanagerai.core.result.AppResult
import com.tamer.smartfilemanagerai.data.local.SmartFileDatabase
import com.tamer.smartfilemanagerai.data.local.entity.FileEntity
import com.tamer.smartfilemanagerai.data.local.entity.FileOperationEntity
import com.tamer.smartfilemanagerai.storage.SafStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class PlannedFileItem(
    val fileId: Long,
    val originalName: String,
    val proposedNewName: String,
    val sourceFolderPath: String,
    val targetFolderPath: String,
    val category: DetailedCategory,
    val confidenceLevel: ConfidenceLevel,
    val confidenceScore: Float,
    val sizeBytes: Long,
    val hasNameConflict: Boolean,
    val requiresManualReview: Boolean,
    val isIgnoredByUser: Boolean = false
)

/**
 * Section 12: Preview & Approval Report before executing any bulk operation.
 */
data class OrganizationPreviewPlan(
    val planId: String = UUID.randomUUID().toString(),
    val titleAr: String,
    val rootStoragePath: String,
    val items: List<PlannedFileItem>,
    val namingStyle: NamingStyle
) {
    val activeItems: List<PlannedFileItem>
        get() = items.filter { !it.isIgnoredByUser && !it.requiresManualReview }

    val reviewQueueItems: List<PlannedFileItem>
        get() = items.filter { it.requiresManualReview && !it.isIgnoredByUser }

    val conflictItems: List<PlannedFileItem>
        get() = items.filter { it.hasNameConflict && !it.isIgnoredByUser }

    val totalAffectedCount: Int
        get() = activeItems.size

    val totalAffectedBytes: Long
        get() = activeItems.sumOf { it.sizeBytes }
}

data class BatchExecutionSummary(
    val batchId: String,
    val succeededCount: Int,
    val failedCount: Int,
    val skippedReviewCount: Int,
    val totalMovedBytes: Long,
    val errors: List<String>
)

/**
 * Section 7 & Section 12: Smart Organizer Engine
 * - Creates the customizable smart folder structure:
 *   01- المستندات/ (PDF, Word, Excel, PowerPoint, فواتير, عقود, تقارير...)
 *   02- الفيديوهات/ (شخصية, تعليمية, العمل, WhatsApp, تسجيلات الشاشة...)
 *   03- الصور/ (شخصية, مستندات, لقطات الشاشة, تصميمات...)
 *   04- الصوتيات/ (تسجيلات, محاضرات, اجتماعات, موسيقى...)
 *   05- الملفات التقنية/ (APK, ZIP, ملفات البرمجة, قواعد البيانات...)
 *   06- الملفات المضغوطة/
 *   07- ملفات غير مصنفة/
 * - Never moves low-confidence files automatically; places them in the Review Queue.
 */
class SmartOrganizerEngine(
    private val database: SmartFileDatabase,
    private val storageManager: SafStorageManager,
    private val renamingEngine: AiRenamingEngine = AiRenamingEngine()
) {

    fun getDefaultFolderStructurePreview(): List<String> = listOf(
        "Phone Storage/01- المستندات/PDF",
        "Phone Storage/01- المستندات/Word",
        "Phone Storage/01- المستندات/Excel",
        "Phone Storage/01- المستندات/PowerPoint",
        "Phone Storage/01- المستندات/فواتير",
        "Phone Storage/01- المستندات/عقود",
        "Phone Storage/01- المستندات/تقارير",
        "Phone Storage/01- المستندات/سجلات مدرسية",
        "Phone Storage/02- الفيديوهات/شخصية",
        "Phone Storage/02- الفيديوهات/تعليمية",
        "Phone Storage/02- الفيديوهات/شروحات البرامج",
        "Phone Storage/02- الفيديوهات/العمل",
        "Phone Storage/02- الفيديوهات/WhatsApp",
        "Phone Storage/02- الفيديوهات/تسجيلات الشاشة",
        "Phone Storage/03- الصور/شخصية",
        "Phone Storage/03- الصور/مستندات",
        "Phone Storage/03- الصور/لقطات الشاشة",
        "Phone Storage/03- الصور/تصميمات",
        "Phone Storage/04- الصوتيات/تسجيلات",
        "Phone Storage/04- الصوتيات/محاضرات",
        "Phone Storage/04- الصوتيات/اجتماعات",
        "Phone Storage/04- الصوتيات/موسيقى",
        "Phone Storage/05- الملفات التقنية/APK",
        "Phone Storage/05- الملفات التقنية/ملفات البرمجة",
        "Phone Storage/05- الملفات التقنية/قواعد البيانات",
        "Phone Storage/06- الملفات المضغوطة/أرشيف",
        "Phone Storage/07- ملفات غير مصنفة/قائمة المراجعة"
    )

    /**
     * Generates a complete Preview & Approval plan without touching any files on disk until approved.
     */
    suspend fun buildOrganizationPreviewPlan(
        rootStoragePath: String,
        targetFiles: List<FileEntity>? = null,
        applySmartRename: Boolean = true,
        onlyRenameOpaqueFiles: Boolean = false,
        organizeByYearSubfolder: Boolean = false,
        customTargetFolderOverride: String? = null,
        namingStyle: NamingStyle = NamingStyle.DESCRIPTIVE
    ): OrganizationPreviewPlan = withContext(Dispatchers.IO) {
        val files = targetFiles ?: database.fileDao().observeAllFiles().first()
        val plannedItems = mutableListOf<PlannedFileItem>()
        val targetFolderNameSet = mutableMapOf<String, MutableSet<String>>()

        for (file in files) {
            val analysis = database.metadataAndAnalysisDao().getAnalysisForFile(file.fileId)
            val category = analysis?.let { DetailedCategory.fromId(it.detectedCategoryCode) }
                ?: DetailedCategory.UNCATEGORIZED_REVIEW
            val confidenceScore = analysis?.confidenceScore ?: file.confidenceScore
            val confidenceLevel = ConfidenceLevel.fromScore(confidenceScore)

            // Rule (Section 7): Do not move file if classification confidence is LOW
            val requiresReview = confidenceLevel == ConfidenceLevel.LOW ||
                category == DetailedCategory.UNCATEGORIZED_REVIEW

            val yearFolder = if (organizeByYearSubfolder) {
                val ts = file.createdAt ?: file.lastModified
                "/" + SimpleDateFormat("yyyy", Locale.US).format(Date(ts))
            } else {
                ""
            }

            val relativeTarget = when {
                customTargetFolderOverride != null -> "$customTargetFolderOverride$yearFolder"
                requiresReview -> "${PrimaryFileGroup.UNCATEGORIZED.folderPrefix}/قائمة المراجعة"
                else -> "${category.primaryGroup.folderPrefix}/${category.subFolderName}$yearFolder"
            }

            val fullTargetFolder = "$rootStoragePath/$relativeTarget".replace("//", "/")

            val baseWithoutExt = file.currentName.substringBeforeLast('.', file.currentName)
            val shouldRename = applySmartRename && !requiresReview &&
                (!onlyRenameOpaqueFiles || renamingEngine.isOpaqueFileName(baseWithoutExt))

            val proposedName = if (shouldRename && analysis != null) {
                when (namingStyle) {
                    NamingStyle.SHORT -> analysis.suggestedNameShort
                    NamingStyle.DESCRIPTIVE -> analysis.suggestedNameDescriptive
                    NamingStyle.PROFESSIONAL_STRUCTURED -> analysis.suggestedNameStructured
                }
            } else {
                file.currentName
            }

            val folderSet = targetFolderNameSet.getOrPut(fullTargetFolder) { mutableSetOf() }
            val hasConflict = ! folderSet.add(proposedName.lowercase()) ||
                File(fullTargetFolder, proposedName).let { it.exists() && it.absolutePath != file.uriOrPath }

            plannedItems.add(
                PlannedFileItem(
                    fileId = file.fileId,
                    originalName = file.currentName,
                    proposedNewName = proposedName,
                    sourceFolderPath = file.parentFolderPath,
                    targetFolderPath = fullTargetFolder,
                    category = category,
                    confidenceLevel = confidenceLevel,
                    confidenceScore = confidenceScore,
                    sizeBytes = file.sizeBytes,
                    hasNameConflict = hasConflict,
                    requiresManualReview = requiresReview
                )
            )
        }

        OrganizationPreviewPlan(
            titleAr = customTargetFolderOverride?.let { "خطة نقل وتنظيم إلى: $it" } ?: "خطة التنظيم الذكي الشامل",
            rootStoragePath = rootStoragePath,
            items = plannedItems,
            namingStyle = namingStyle
        )
    }

    /**
     * Executes an approved OrganizationPreviewPlan, logging every operation in FileOperations for Undo.
     */
    suspend fun executeApprovedPlan(plan: OrganizationPreviewPlan): BatchExecutionSummary = withContext(Dispatchers.IO) {
        val batchId = "BATCH_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        var succeeded = 0
        var failed = 0
        var movedBytes = 0L
        val errors = mutableListOf<String>()

        for (item in plan.activeItems) {
            val fileEntity = database.fileDao().getFileById(item.fileId) ?: continue
            val moveResult = storageManager.moveFile(
                sourceUriOrPath = fileEntity.uriOrPath,
                targetFolderUriOrPath = item.targetFolderPath,
                targetFileName = item.proposedNewName
            )

            when (moveResult) {
                is AppResult.Success -> {
                    val finalPath = moveResult.data
                    val finalName = File(finalPath).name
                    val now = System.currentTimeMillis()

                    database.fileDao().updateFileLocationAndName(
                        fileId = fileEntity.fileId,
                        newName = finalName,
                        newUri = finalPath,
                        newParentPath = item.targetFolderPath,
                        modifiedAt = now
                    )

                    database.operationDao().insertOperation(
                        FileOperationEntity(
                            batchId = batchId,
                            operationType = if (item.originalName != finalName) "ORGANIZE_AND_RENAME" else "MOVE",
                            executedAt = now,
                            fileId = fileEntity.fileId,
                            originalPath = fileEntity.uriOrPath,
                            newPath = finalPath,
                            previousName = fileEntity.currentName,
                            newName = finalName,
                            executionStatus = "COMPLETED",
                            canUndo = true
                        )
                    )
                    succeeded++
                    movedBytes += item.sizeBytes
                }
                is AppResult.Error -> {
                    failed++
                    errors.add("${item.originalName}: ${moveResult.error.messageAr}")
                    database.operationDao().insertOperation(
                        FileOperationEntity(
                            batchId = batchId,
                            operationType = "ORGANIZE_AND_RENAME",
                            executedAt = System.currentTimeMillis(),
                            fileId = fileEntity.fileId,
                            originalPath = fileEntity.uriOrPath,
                            newPath = item.targetFolderPath,
                            previousName = fileEntity.currentName,
                            newName = item.proposedNewName,
                            executionStatus = "FAILED",
                            canUndo = false,
                            errorMessage = moveResult.error.messageAr
                        )
                    )
                }
            }
        }

        BatchExecutionSummary(
            batchId = batchId,
            succeededCount = succeeded,
            failedCount = failed,
            skippedReviewCount = plan.reviewQueueItems.size,
            totalMovedBytes = movedBytes,
            errors = errors
        )
    }
}

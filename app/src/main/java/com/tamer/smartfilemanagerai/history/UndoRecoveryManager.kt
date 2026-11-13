package com.tamer.smartfilemanagerai.history

import com.tamer.smartfilemanagerai.core.result.AppError
import com.tamer.smartfilemanagerai.core.result.AppResult
import com.tamer.smartfilemanagerai.data.local.SmartFileDatabase
import com.tamer.smartfilemanagerai.data.local.entity.FileOperationEntity
import com.tamer.smartfilemanagerai.storage.SafStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class UndoBatchResult(
    val batchId: String,
    val restoredCount: Int,
    val failedCount: Int,
    val messagesAr: List<String>
)

/**
 * Section 13: Undo & Recovery Manager
 * - Supports undoing the single last operation or an entire batch operation.
 * - Restores original filenames and original locations.
 * - Handles partial failures gracefully.
 * - Verifies that the file still exists and was not deleted/modified externally before attempting undo.
 */
class UndoRecoveryManager(
    private val database: SmartFileDatabase,
    private val storageManager: SafStorageManager
) {

    suspend fun undoLastOperation(): AppResult<FileOperationEntity> = withContext(Dispatchers.IO) {
        val lastOp = database.operationDao().getLastUndoableOperation()
            ?: return@withContext AppResult.Error(AppError.UnexpectedError("لا توجد عمليات سابقة قابلة للتراجع."))

        undoSingleOperationInternal(lastOp)
    }

    suspend fun undoBatchOperations(batchId: String): UndoBatchResult = withContext(Dispatchers.IO) {
        val ops = database.operationDao().getUndoableOperationsByBatch(batchId)
        var restored = 0
        var failed = 0
        val messages = mutableListOf<String>()

        for (op in ops) {
            when (val res = undoSingleOperationInternal(op)) {
                is AppResult.Success -> {
                    restored++
                    messages.add("تم استعادة '${op.previousName}' إلى موقعه الأصلي بنجاح.")
                }
                is AppResult.Error -> {
                    failed++
                    messages.add("تعذر استعادة '${op.newName}': ${res.error.messageAr}")
                }
            }
        }

        UndoBatchResult(
            batchId = batchId,
            restoredCount = restored,
            failedCount = failed,
            messagesAr = messages
        )
    }

    private suspend fun undoSingleOperationInternal(op: FileOperationEntity): AppResult<FileOperationEntity> {
        val currentFile = File(op.newPath)
        // Rule (Section 13): Do not promise or force undo if file was deleted or modified outside the app
        if (!op.newPath.startsWith("content://") && !currentFile.exists()) {
            database.operationDao().updateOperationStatus(
                operationId = op.operationId,
                status = "EXTERNAL_MODIFICATION",
                canUndo = false,
                error = "تم حذف الملف أو نقله من خارج التطبيق"
            )
            return AppResult.Error(AppError.ExternalModificationDetected(op.newName))
        }

        val originalParentFolder = File(op.originalPath).parent
            ?: return AppResult.Error(AppError.UnexpectedError("المجلد الأصلي غير معروف"))

        val moveBackResult = storageManager.moveFile(
            sourceUriOrPath = op.newPath,
            targetFolderUriOrPath = originalParentFolder,
            targetFileName = op.previousName
        )

        return when (moveBackResult) {
            is AppResult.Success -> {
                val restoredPath = moveBackResult.data
                if (op.fileId != null) {
                    database.fileDao().updateFileLocationAndName(
                        fileId = op.fileId,
                        newName = op.previousName,
                        newUri = restoredPath,
                        newParentPath = originalParentFolder,
                        modifiedAt = System.currentTimeMillis()
                    )
                }
                database.operationDao().updateOperationStatus(
                    operationId = op.operationId,
                    status = "ROLLED_BACK",
                    canUndo = false
                )
                AppResult.Success(op.copy(executionStatus = "ROLLED_BACK", canUndo = false))
            }
            is AppResult.Error -> {
                database.operationDao().updateOperationStatus(
                    operationId = op.operationId,
                    status = "UNDO_FAILED",
                    canUndo = false,
                    error = moveBackResult.error.messageAr
                )
                AppResult.Error(moveBackResult.error)
            }
        }
    }
}

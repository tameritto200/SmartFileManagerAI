package com.tamer.smartfilemanagerai.scanner

import android.content.Context
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tamer.smartfilemanagerai.ai.OnDeviceAiAnalyzer
import com.tamer.smartfilemanagerai.data.local.SmartFileDatabase
import kotlinx.coroutines.flow.first

/**
 * Background Worker using Android WorkManager (Section 2 & Section 14).
 * Scans directories in the background, updates Room index, and runs On-Device AI categorization.
 */
class FileScanWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val rootPath = inputData.getString(KEY_ROOT_PATH)
            ?: Environment.getExternalStorageDirectory().absolutePath

        val db = SmartFileDatabase.getInstance(applicationContext)
        val scanner = FileScannerEngine(applicationContext, db)
        val analyzer = OnDeviceAiAnalyzer(applicationContext, db)

        return try {
            val session = scanner.scanDirectoryRoot(rootPath, computeFullSha256 = true)
            val allFiles = db.fileDao().observeAllFiles().first()
            var analyzedCount = 0
            for (file in allFiles) {
                analyzer.analyzeAndClassifyFile(file)
                analyzedCount++
                if (analyzedCount % 10 == 0) {
                    setProgress(workDataOf("ANALYZED_COUNT" to analyzedCount, "TOTAL" to allFiles.size))
                }
            }
            Result.success(
                workDataOf(
                    "SESSION_ID" to session.sessionId,
                    "PROCESSED_COUNT" to session.processedCount,
                    "NEW_FILES" to session.newFilesCount
                )
            )
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_ROOT_PATH = "root_path"
        const val WORK_NAME = "smart_file_manager_background_scan"
    }
}

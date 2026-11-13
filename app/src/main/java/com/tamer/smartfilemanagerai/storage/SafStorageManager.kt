package com.tamer.smartfilemanagerai.storage

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.tamer.smartfilemanagerai.core.result.AppError
import com.tamer.smartfilemanagerai.core.result.AppResult
import com.tamer.smartfilemanagerai.core.security.PathSecurityValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class StorageSpaceInfo(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long
) {
    val usedRatio: Float
        get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes.toFloat() else 0f
}

class SafStorageManager(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    /**
     * Persistable URI permission granted by user via ACTION_OPEN_DOCUMENT_TREE.
     */
    fun persistTreePermission(treeUri: Uri): AppResult<Unit> {
        return try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            AppResult.Success(Unit)
        } catch (e: SecurityException) {
            AppResult.Error(AppError.PermissionDenied(treeUri.toString()))
        }
    }

    fun getPersistedTreeUris(): List<Uri> {
        return contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri }
    }

    fun getDeviceStorageSpaceInfo(): StorageSpaceInfo {
        return try {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            val total = stat.blockSizeLong * stat.blockCountLong
            val free = stat.blockSizeLong * stat.availableBlocksLong
            StorageSpaceInfo(
                totalBytes = total,
                freeBytes = free,
                usedBytes = (total - free).coerceAtLeast(0L)
            )
        } catch (e: Exception) {
            StorageSpaceInfo(0L, 0L, 0L)
        }
    }

    /**
     * Creates nested folder hierarchy inside a user-authorized DocumentFile tree or local directory.
     */
    suspend fun ensureDirectoryPath(
        rootUriOrPath: String,
        relativeSubPath: String
    ): AppResult<String> = withContext(Dispatchers.IO) {
        if (!PathSecurityValidator.isSafeToModify(rootUriOrPath)) {
            return@withContext AppResult.Error(AppError.ProtectedSystemPath(rootUriOrPath))
        }

        val segments = relativeSubPath.split("/").map { it.trim() }.filter { it.isNotEmpty() }
        try {
            if (rootUriOrPath.startsWith("content://")) {
                val rootUri = Uri.parse(rootUriOrPath)
                var currentDoc = DocumentFile.fromTreeUri(context, rootUri)
                    ?: return@withContext AppResult.Error(AppError.PermissionDenied(rootUriOrPath))

                for (segment in segments) {
                    val existing = currentDoc.findFile(segment)
                    currentDoc = if (existing != null && existing.isDirectory) {
                        existing
                    } else {
                        currentDoc.createDirectory(segment)
                            ?: return@withContext AppResult.Error(AppError.PermissionDenied("$rootUriOrPath/$relativeSubPath"))
                    }
                }
                AppResult.Success(currentDoc.uri.toString())
            } else {
                val targetDir = File(File(rootUriOrPath), segments.joinToString(File.separator))
                if (!targetDir.exists() && !targetDir.mkdirs()) {
                    return@withContext AppResult.Error(AppError.PermissionDenied(targetDir.absolutePath))
                }
                AppResult.Success(targetDir.absolutePath)
            }
        } catch (e: SecurityException) {
            AppResult.Error(AppError.PermissionDenied(rootUriOrPath))
        } catch (e: Exception) {
            AppResult.Error(AppError.UnexpectedError(e.message ?: "Failed to create directory"))
        }
    }

    /**
     * Safely renames a file while preserving its extension and checking for collisions.
     */
    suspend fun renameFile(
        uriOrPath: String,
        proposedNewName: String
    ): AppResult<Pair<String, String>> = withContext(Dispatchers.IO) {
        if (!PathSecurityValidator.isSafeToModify(uriOrPath)) {
            return@withContext AppResult.Error(AppError.ProtectedSystemPath(uriOrPath))
        }

        try {
            if (uriOrPath.startsWith("content://")) {
                val uri = Uri.parse(uriOrPath)
                val docFile = DocumentFile.fromSingleUri(context, uri)
                    ?: return@withContext AppResult.Error(AppError.CorruptedOrUnreadableFile(proposedNewName, "URI not found"))

                val success = docFile.renameTo(proposedNewName)
                if (!success) {
                    return@withContext AppResult.Error(AppError.PermissionDenied(uriOrPath))
                }
                AppResult.Success(Pair(docFile.uri.toString(), proposedNewName))
            } else {
                val sourceFile = File(uriOrPath)
                if (!sourceFile.exists()) {
                    return@withContext AppResult.Error(AppError.ExternalModificationDetected(sourceFile.name))
                }
                val parentDir = sourceFile.parentFile
                    ?: return@withContext AppResult.Error(AppError.PermissionDenied(uriOrPath))
                val resolvedTarget = resolveUniqueFile(parentDir, proposedNewName)
                if (!sourceFile.renameTo(resolvedTarget)) {
                    return@withContext AppResult.Error(AppError.PermissionDenied(uriOrPath))
                }
                AppResult.Success(Pair(resolvedTarget.absolutePath, resolvedTarget.name))
            }
        } catch (e: SecurityException) {
            AppResult.Error(AppError.PermissionDenied(uriOrPath))
        } catch (e: Exception) {
            AppResult.Error(AppError.UnexpectedError(e.message ?: "Rename failed"))
        }
    }

    /**
     * Moves (or copies then removes original on SAF) a file to a target folder, checking storage space and collisions.
     */
    suspend fun moveFile(
        sourceUriOrPath: String,
        targetFolderUriOrPath: String,
        targetFileName: String
    ): AppResult<String> = withContext(Dispatchers.IO) {
        if (!PathSecurityValidator.isSafeToModify(sourceUriOrPath) ||
            !PathSecurityValidator.isSafeToModify(targetFolderUriOrPath)
        ) {
            return@withContext AppResult.Error(AppError.ProtectedSystemPath(sourceUriOrPath))
        }

        try {
            if (!sourceUriOrPath.startsWith("content://") && !targetFolderUriOrPath.startsWith("content://")) {
                val srcFile = File(sourceUriOrPath)
                if (!srcFile.exists()) {
                    return@withContext AppResult.Error(AppError.ExternalModificationDetected(srcFile.name))
                }
                val dstFolder = File(targetFolderUriOrPath)
                if (!dstFolder.exists() && !dstFolder.mkdirs()) {
                    return@withContext AppResult.Error(AppError.PermissionDenied(targetFolderUriOrPath))
                }

                val dstFile = resolveUniqueFile(dstFolder, targetFileName)
                val moved = srcFile.renameTo(dstFile)
                if (moved) {
                    return@withContext AppResult.Success(dstFile.absolutePath)
                }

                // Fallback to stream copy + delete if across mount points
                val space = getDeviceStorageSpaceInfo()
                if (space.freeBytes in 1 until srcFile.length()) {
                    return@withContext AppResult.Error(AppError.InsufficientStorage(srcFile.length(), space.freeBytes))
                }
                srcFile.copyTo(dstFile, overwrite = false)
                if (dstFile.exists() && dstFile.length() == srcFile.length()) {
                    srcFile.delete()
                    return@withContext AppResult.Success(dstFile.absolutePath)
                }
                return@withContext AppResult.Error(AppError.UnexpectedError("تعذر نقل الملف عبر المسارات"))
            } else {
                // SAF Content URI stream copy + delete
                val srcUri = Uri.parse(sourceUriOrPath)
                val targetTreeUri = Uri.parse(targetFolderUriOrPath)
                val targetDirDoc = DocumentFile.fromTreeUri(context, targetTreeUri)
                    ?: return@withContext AppResult.Error(AppError.PermissionDenied(targetFolderUriOrPath))

                val ext = targetFileName.substringAfterLast('.', "").lowercase()
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
                val newDoc = targetDirDoc.createFile(mime, targetFileName)
                    ?: return@withContext AppResult.Error(AppError.PermissionDenied(targetFolderUriOrPath))

                contentResolver.openInputStream(srcUri)?.use { input ->
                    contentResolver.openOutputStream(newDoc.uri)?.use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                } ?: return@withContext AppResult.Error(AppError.CorruptedOrUnreadableFile(targetFileName, "Unable to open SAF stream"))

                DocumentFile.fromSingleUri(context, srcUri)?.delete()
                AppResult.Success(newDoc.uri.toString())
            }
        } catch (e: SecurityException) {
            AppResult.Error(AppError.PermissionDenied(sourceUriOrPath))
        } catch (e: Exception) {
            AppResult.Error(AppError.UnexpectedError(e.message ?: "Move failed"))
        }
    }

    fun resolveUniqueFile(directory: File, desiredName: String): File {
        var candidate = File(directory, desiredName)
        if (!candidate.exists()) return candidate

        val baseName = desiredName.substringBeforeLast('.', desiredName)
        val ext = desiredName.substringAfterLast('.', "")
        var counter = 1
        while (candidate.exists()) {
            val numbered = if (ext.isNotEmpty()) "${baseName}_$counter.$ext" else "${baseName}_$counter"
            candidate = File(directory, numbered)
            counter++
        }
        return candidate
    }
}

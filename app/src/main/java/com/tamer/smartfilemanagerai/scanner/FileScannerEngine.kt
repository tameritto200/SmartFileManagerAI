package com.tamer.smartfilemanagerai.scanner

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import com.tamer.smartfilemanagerai.core.model.DetailedCategory
import com.tamer.smartfilemanagerai.core.model.PrimaryFileGroup
import com.tamer.smartfilemanagerai.core.security.PathSecurityValidator
import com.tamer.smartfilemanagerai.data.local.SmartFileDatabase
import com.tamer.smartfilemanagerai.data.local.entity.FileEntity
import com.tamer.smartfilemanagerai.data.local.entity.FileMetadataEntity
import com.tamer.smartfilemanagerai.data.local.entity.FolderEntity
import com.tamer.smartfilemanagerai.data.local.entity.ScanSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

data class ScanProgressUiState(
    val isScanning: Boolean = false,
    val isPaused: Boolean = false,
    val currentFilePath: String = "",
    val totalDiscovered: Int = 0,
    val processedCount: Int = 0,
    val newFilesCount: Int = 0,
    val updatedFilesCount: Int = 0,
    val skippedUnchangedCount: Int = 0,
    val inaccessibleCount: Int = 0
) {
    val progressPercent: Int
        get() = if (totalDiscovered > 0) ((processedCount * 100) / totalDiscovered).coerceIn(0, 100) else 0
}

class FileScannerEngine(
    private val context: Context,
    private val database: SmartFileDatabase
) {
    private val _progressState = MutableStateFlow(ScanProgressUiState())
    val progressState: StateFlow<ScanProgressUiState> = _progressState.asStateFlow()

    private val isPausedFlag = AtomicBoolean(false)
    private val isCancelledFlag = AtomicBoolean(false)

    fun pauseScan() {
        isPausedFlag.set(true)
        _progressState.value = _progressState.value.copy(isPaused = true)
    }

    fun resumeScan() {
        isPausedFlag.set(false)
        _progressState.value = _progressState.value.copy(isPaused = false)
    }

    fun cancelScan() {
        isCancelledFlag.set(true)
        isPausedFlag.set(false)
        _progressState.value = _progressState.value.copy(isScanning = false, isPaused = false)
    }

    /**
     * Multi-level recursive scanner for File paths or SAF DocumentFile trees.
     * Supports incremental indexing (skips unchanged files via signature), pause/resume,
     * and safe resilience against inaccessible/corrupted files.
     */
    suspend fun scanDirectoryRoot(
        rootPathOrUri: String,
        computeFullSha256: Boolean = true
    ): ScanSessionEntity = withContext(Dispatchers.IO) {
        isCancelledFlag.set(false)
        isPausedFlag.set(false)

        var session = ScanSessionEntity(
            rootUriOrPath = rootPathOrUri,
            status = "RUNNING",
            totalDiscovered = 0,
            processedCount = 0,
            newFilesCount = 0,
            updatedFilesCount = 0,
            skippedUnchangedCount = 0,
            inaccessibleCount = 0,
            lastProcessedPath = null
        )
        val sessionId = database.searchVoiceAndSessionDao().upsertScanSession(session)
        session = session.copy(sessionId = sessionId)

        val discoveredFiles = mutableListOf<File>()
        var inaccessibleDirs = 0

        if (!rootPathOrUri.startsWith("content://")) {
            val rootDir = File(rootPathOrUri)
            collectAccessibleFilesRecursively(rootDir, discoveredFiles) {
                inaccessibleDirs++
            }
        }

        val totalFiles = discoveredFiles.size
        _progressState.value = ScanProgressUiState(
            isScanning = true,
            isPaused = false,
            totalDiscovered = totalFiles,
            inaccessibleCount = inaccessibleDirs
        )

        var processed = 0
        var newCount = 0
        var updatedCount = 0
        var skippedCount = 0
        var errorCount = inaccessibleDirs

        for (file in discoveredFiles) {
            coroutineContext.ensureActive()
            if (isCancelledFlag.get()) {
                session = session.copy(
                    status = "CANCELLED",
                    processedCount = processed,
                    completedAt = System.currentTimeMillis()
                )
                database.searchVoiceAndSessionDao().updateScanSession(session)
                _progressState.value = _progressState.value.copy(isScanning = false)
                return@withContext session
            }

            while (isPausedFlag.get() && !isCancelledFlag.get()) {
                kotlinx.coroutines.delay(250)
            }

            try {
                val absPath = file.absolutePath
                val lastMod = file.lastModified()
                val sizeBytes = file.length()
                val signature = "${absPath}_${sizeBytes}_$lastMod"

                val existing = database.fileDao().getFileByUri(absPath)
                if (existing != null && existing.scanSignature == signature) {
                    skippedCount++
                    processed++
                } else {
                    val parentPath = file.parent ?: rootPathOrUri
                    val folderId = ensureParentFolderIndexed(parentPath)
                    val ext = file.extension.lowercase()
                    val mimeType = resolveMimeType(ext)
                    val primaryGroup = inferInitialPrimaryGroup(ext, mimeType)
                    val creationTime = extractCreationTimestamp(file)

                    val entity = FileEntity(
                        fileId = existing?.fileId ?: 0L,
                        originalName = existing?.originalName ?: file.name,
                        currentName = file.name,
                        extension = ext,
                        mimeType = mimeType,
                        sizeBytes = sizeBytes,
                        createdAt = creationTime,
                        lastModified = lastMod,
                        lastAccessedAt = extractLastAccessTimestamp(file) ?: lastMod,
                        uriOrPath = absPath,
                        parentFolderPath = parentPath,
                        parentFolderId = folderId,
                        contentType = mimeType.substringBefore('/'),
                        primaryGroup = primaryGroup.name,
                        categoryId = existing?.categoryId,
                        confidenceScore = existing?.confidenceScore ?: 0.5f,
                        confidenceLevel = existing?.confidenceLevel ?: "MEDIUM",
                        isHidden = file.isHidden || file.name.startsWith("."),
                        isAccessible = file.canRead(),
                        scanSignature = signature
                    )

                    val savedFileId = database.fileDao().upsertFile(entity)
                    val metadata = extractRichMetadata(
                        fileId = savedFileId,
                        file = file,
                        ext = ext,
                        mimeType = mimeType,
                        computeSha256 = computeFullSha256 && sizeBytes <= 200L * 1024 * 1024
                    )
                    database.metadataAndAnalysisDao().upsertMetadata(metadata)

                    if (existing == null) newCount++ else updatedCount++
                    processed++
                }

                if (processed % 10 == 0 || processed == totalFiles) {
                    _progressState.value = ScanProgressUiState(
                        isScanning = true,
                        isPaused = isPausedFlag.get(),
                        currentFilePath = file.name,
                        totalDiscovered = totalFiles,
                        processedCount = processed,
                        newFilesCount = newCount,
                        updatedFilesCount = updatedCount,
                        skippedUnchangedCount = skippedCount,
                        inaccessibleCount = errorCount
                    )
                    session = session.copy(
                        totalDiscovered = totalFiles,
                        processedCount = processed,
                        newFilesCount = newCount,
                        updatedFilesCount = updatedCount,
                        skippedUnchangedCount = skippedCount,
                        inaccessibleCount = errorCount,
                        lastProcessedPath = file.absolutePath
                    )
                    database.searchVoiceAndSessionDao().updateScanSession(session)
                }
            } catch (e: Exception) {
                // Gracefully record inaccessible/corrupted files without crashing the scan loop
                errorCount++
                processed++
            }
        }

        val completedSession = session.copy(
            status = "COMPLETED",
            totalDiscovered = totalFiles,
            processedCount = processed,
            newFilesCount = newCount,
            updatedFilesCount = updatedCount,
            skippedUnchangedCount = skippedCount,
            inaccessibleCount = errorCount,
            completedAt = System.currentTimeMillis()
        )
        database.searchVoiceAndSessionDao().updateScanSession(completedSession)
        _progressState.value = _progressState.value.copy(
            isScanning = false,
            processedCount = processed,
            newFilesCount = newCount,
            updatedFilesCount = updatedCount,
            skippedUnchangedCount = skippedCount,
            inaccessibleCount = errorCount
        )
        completedSession
    }

    private fun collectAccessibleFilesRecursively(
        dir: File,
        outList: MutableList<File>,
        onInaccessible: () -> Unit
    ) {
        if (!dir.exists() || PathSecurityValidator.isProtectedSystemPath(dir.absolutePath)) {
            onInaccessible()
            return
        }
        val children = try {
            dir.listFiles()
        } catch (e: SecurityException) {
            onInaccessible()
            null
        } ?: run {
            onInaccessible()
            return
        }

        for (child in children) {
            if (child.isDirectory) {
                collectAccessibleFilesRecursively(child, outList, onInaccessible)
            } else if (child.isFile) {
                outList.add(child)
            }
        }
    }

    private suspend fun ensureParentFolderIndexed(parentPath: String): Long {
        val existing = database.folderDao().getFolderByUri(parentPath)
        if (existing != null) return existing.folderId
        val folderFile = File(parentPath)
        return database.folderDao().upsertFolder(
            FolderEntity(
                folderName = folderFile.name.ifBlank { "Root" },
                uriOrPath = parentPath,
                parentPath = folderFile.parent,
                isProtected = PathSecurityValidator.isProtectedSystemPath(parentPath)
            )
        )
    }

    private fun extractCreationTimestamp(file: File): Long? {
        return try {
            val attrs = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
            attrs.creationTime().toMillis()
        } catch (e: Exception) {
            file.lastModified()
        }
    }

    private fun extractLastAccessTimestamp(file: File): Long? {
        return try {
            val attrs = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
            attrs.lastAccessTime().toMillis()
        } catch (e: Exception) {
            null
        }
    }

    private fun extractRichMetadata(
        fileId: Long,
        file: File,
        ext: String,
        mimeType: String,
        computeSha256: Boolean
    ): FileMetadataEntity {
        var width: Int? = null
        var height: Int? = null
        var durationMs: Long? = null
        var exifDate: String? = null
        var exifCamera: String? = null
        var lat: Double? = null
        var lon: Double? = null
        var mediaTitle: String? = null
        var mediaArtist: String? = null

        // Do NOT execute APK/EXE/DLL/scripts; only inspect metadata safely
        if (!PathSecurityValidator.requiresPassiveReadOnlyInspection(ext)) {
            if (mimeType.startsWith("image/")) {
                try {
                    val exif = ExifInterface(file.absolutePath)
                    width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 }
                    height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 }
                    exifDate = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                    exifCamera = exif.getAttribute(ExifInterface.TAG_MODEL)
                    val latLong = exif.latLong
                    if (latLong != null && latLong.size == 2) {
                        lat = latLong[0]
                        lon = latLong[1]
                    }
                } catch (_: Exception) {
                }
            } else if (mimeType.startsWith("video/") || mimeType.startsWith("audio/")) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                    height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                    mediaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    mediaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                } catch (_: Exception) {
                } finally {
                    try {
                        retriever.release()
                    } catch (_: Exception) {
                    }
                }
            }
        }

        val sha256 = if (computeSha256 && file.canRead()) computeFileSha256(file) else null

        return FileMetadataEntity(
            fileId = fileId,
            width = width,
            height = height,
            durationMs = durationMs,
            exifDateTime = exifDate,
            exifCameraModel = exifCamera,
            exifLatitude = lat,
            exifLongitude = lon,
            mediaTitle = mediaTitle,
            mediaArtist = mediaArtist,
            sha256Hash = sha256
        )
    }

    fun computeFileSha256(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(64 * 1024)
                var read = fis.read(buffer)
                while (read != -1) {
                    digest.update(buffer, 0, read)
                    read = fis.read(buffer)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    fun resolveMimeType(ext: String): String {
        val cleanExt = ext.lowercase().trimStart('.')
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(cleanExt)
            ?: when (cleanExt) {
                "pdf" -> "application/pdf"
                "doc", "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "xls", "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                "ppt", "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                "txt", "rtf", "csv", "py", "java", "kt", "kotlin", "xml", "json", "html", "css", "js", "sql" -> "text/plain"
                "mp4", "mkv", "avi", "mov", "3gp", "webm" -> "video/$cleanExt"
                "jpg", "jpeg", "png", "webp", "gif", "heic" -> "image/$cleanExt"
                "mp3", "wav", "aac", "m4a", "ogg", "flac" -> "audio/$cleanExt"
                "zip", "rar", "7z" -> "application/zip"
                "apk" -> "application/vnd.android.package-archive"
                else -> "application/octet-stream"
            }
    }

    fun inferInitialPrimaryGroup(ext: String, mimeType: String): PrimaryFileGroup {
        val clean = ext.lowercase().trimStart('.')
        return when {
            clean in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "csv", "odt", "ods") ->
                PrimaryFileGroup.DOCUMENTS
            clean in setOf("mp4", "mkv", "avi", "mov", "3gp", "webm") || mimeType.startsWith("video/") ->
                PrimaryFileGroup.VIDEOS
            clean in setOf("jpg", "jpeg", "png", "webp", "gif", "heic") || mimeType.startsWith("image/") ->
                PrimaryFileGroup.IMAGES
            clean in setOf("mp3", "wav", "aac", "m4a", "ogg", "flac") || mimeType.startsWith("audio/") ->
                PrimaryFileGroup.AUDIO
            clean in setOf("zip", "rar", "7z") ->
                PrimaryFileGroup.COMPRESSED
            clean in setOf("apk", "xml", "json", "html", "css", "js", "py", "java", "kt", "kotlin", "dll", "exe", "sql", "db") ->
                PrimaryFileGroup.TECHNICAL
            else -> PrimaryFileGroup.UNCATEGORIZED
        }
    }
}

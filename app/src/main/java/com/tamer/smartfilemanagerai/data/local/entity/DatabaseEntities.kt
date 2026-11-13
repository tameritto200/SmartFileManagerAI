package com.tamer.smartfilemanagerai.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 1. Folders Table
 */
@Entity(
    tableName = "Folders",
    indices = [
        Index(value = ["uri_or_path"], unique = true),
        Index(value = ["parent_path"])
    ]
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "folder_id") val folderId: Long = 0,
    @ColumnInfo(name = "folder_name") val folderName: String,
    @ColumnInfo(name = "uri_or_path") val uriOrPath: String,
    @ColumnInfo(name = "parent_path") val parentPath: String?,
    @ColumnInfo(name = "file_count") val fileCount: Int = 0,
    @ColumnInfo(name = "total_size_bytes") val totalSizeBytes: Long = 0L,
    @ColumnInfo(name = "last_scanned_at") val lastScannedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_protected") val isProtected: Boolean = false
)

/**
 * 2. Categories Table
 */
@Entity(
    tableName = "Categories",
    indices = [Index(value = ["category_code"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "category_id") val categoryId: Long = 0,
    @ColumnInfo(name = "category_code") val categoryCode: String,
    @ColumnInfo(name = "name_ar") val nameAr: String,
    @ColumnInfo(name = "name_en") val nameEn: String,
    @ColumnInfo(name = "primary_group") val primaryGroup: String,
    @ColumnInfo(name = "target_subfolder") val targetSubfolder: String,
    @ColumnInfo(name = "is_custom") val isCustom: Boolean = false
)

/**
 * 3. Files Table
 */
@Entity(
    tableName = "Files",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["folder_id"],
            childColumns = ["parent_folder_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["category_id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["uri_or_path"], unique = true),
        Index(value = ["parent_folder_id"]),
        Index(value = ["category_id"]),
        Index(value = ["extension"]),
        Index(value = ["size_bytes"]),
        Index(value = ["last_modified"]),
        Index(value = ["primary_group"])
    ]
)
data class FileEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "file_id") val fileId: Long = 0,
    @ColumnInfo(name = "original_name") val originalName: String,
    @ColumnInfo(name = "current_name") val currentName: String,
    @ColumnInfo(name = "extension") val extension: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long?,
    @ColumnInfo(name = "last_modified") val lastModified: Long,
    @ColumnInfo(name = "last_accessed_at") val lastAccessedAt: Long?,
    @ColumnInfo(name = "uri_or_path") val uriOrPath: String,
    @ColumnInfo(name = "parent_folder_path") val parentFolderPath: String,
    @ColumnInfo(name = "parent_folder_id") val parentFolderId: Long?,
    @ColumnInfo(name = "content_type") val contentType: String,
    @ColumnInfo(name = "primary_group") val primaryGroup: String,
    @ColumnInfo(name = "category_id") val categoryId: Long?,
    @ColumnInfo(name = "confidence_score") val confidenceScore: Float = 0f,
    @ColumnInfo(name = "confidence_level") val confidenceLevel: String = "LOW",
    @ColumnInfo(name = "is_hidden") val isHidden: Boolean = false,
    @ColumnInfo(name = "is_accessible") val isAccessible: Boolean = true,
    @ColumnInfo(name = "scan_signature") val scanSignature: String
)

/**
 * 4. Tags Table + CrossRef
 */
@Entity(
    tableName = "Tags",
    indices = [
        Index(value = ["tag_name", "file_id"], unique = true),
        Index(value = ["file_id"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = FileEntity::class,
            parentColumns = ["file_id"],
            childColumns = ["file_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "tag_id") val tagId: Long = 0,
    @ColumnInfo(name = "file_id") val fileId: Long,
    @ColumnInfo(name = "tag_name") val tagName: String,
    @ColumnInfo(name = "source") val source: String = "ON_DEVICE_AI"
)

/**
 * 5. FileMetadata Table
 */
@Entity(
    tableName = "FileMetadata",
    foreignKeys = [
        ForeignKey(
            entity = FileEntity::class,
            parentColumns = ["file_id"],
            childColumns = ["file_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["file_id"], unique = true),
        Index(value = ["sha256_hash"]),
        Index(value = ["perceptual_hash"])
    ]
)
data class FileMetadataEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "metadata_id") val metadataId: Long = 0,
    @ColumnInfo(name = "file_id") val fileId: Long,
    @ColumnInfo(name = "width") val width: Int? = null,
    @ColumnInfo(name = "height") val height: Int? = null,
    @ColumnInfo(name = "duration_ms") val durationMs: Long? = null,
    @ColumnInfo(name = "exif_date_time") val exifDateTime: String? = null,
    @ColumnInfo(name = "exif_camera_model") val exifCameraModel: String? = null,
    @ColumnInfo(name = "exif_latitude") val exifLatitude: Double? = null,
    @ColumnInfo(name = "exif_longitude") val exifLongitude: Double? = null,
    @ColumnInfo(name = "media_title") val mediaTitle: String? = null,
    @ColumnInfo(name = "media_artist") val mediaArtist: String? = null,
    @ColumnInfo(name = "extracted_text") val extractedText: String? = null,
    @ColumnInfo(name = "page_count") val pageCount: Int? = null,
    @ColumnInfo(name = "sha256_hash") val sha256Hash: String? = null,
    @ColumnInfo(name = "perceptual_hash") val perceptualHash: String? = null,
    @ColumnInfo(name = "audio_fingerprint") val audioFingerprint: String? = null
)

/**
 * 6. AIAnalysis Table
 */
@Entity(
    tableName = "AIAnalysis",
    foreignKeys = [
        ForeignKey(
            entity = FileEntity::class,
            parentColumns = ["file_id"],
            childColumns = ["file_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["file_id"], unique = true)]
)
data class AIAnalysisEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "analysis_id") val analysisId: Long = 0,
    @ColumnInfo(name = "file_id") val fileId: Long,
    @ColumnInfo(name = "engine_type") val engineType: String, // ON_DEVICE or CLOUD
    @ColumnInfo(name = "provider_name") val providerName: String,
    @ColumnInfo(name = "summary_ar") val summaryAr: String,
    @ColumnInfo(name = "detected_topic") val detectedTopic: String,
    @ColumnInfo(name = "detected_category_code") val detectedCategoryCode: String,
    @ColumnInfo(name = "confidence_score") val confidenceScore: Float,
    @ColumnInfo(name = "confidence_level") val confidenceLevel: String,
    @ColumnInfo(name = "suggested_name_short") val suggestedNameShort: String,
    @ColumnInfo(name = "suggested_name_descriptive") val suggestedNameDescriptive: String,
    @ColumnInfo(name = "suggested_name_structured") val suggestedNameStructured: String,
    @ColumnInfo(name = "keywords_csv") val keywordsCsv: String,
    @ColumnInfo(name = "analyzed_at") val analyzedAt: Long = System.currentTimeMillis()
)

/**
 * 7. FileOperations Table (Undo & Recovery Log)
 */
@Entity(
    tableName = "FileOperations",
    foreignKeys = [
        ForeignKey(
            entity = FileEntity::class,
            parentColumns = ["file_id"],
            childColumns = ["file_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["batch_id"]),
        Index(value = ["file_id"]),
        Index(value = ["executed_at"])
    ]
)
data class FileOperationEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "operation_id") val operationId: Long = 0,
    @ColumnInfo(name = "batch_id") val batchId: String,
    @ColumnInfo(name = "operation_type") val operationType: String, // RENAME, MOVE, ORGANIZE, CREATE_FOLDER
    @ColumnInfo(name = "executed_at") val executedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "file_id") val fileId: Long?,
    @ColumnInfo(name = "original_path") val originalPath: String,
    @ColumnInfo(name = "new_path") val newPath: String,
    @ColumnInfo(name = "previous_name") val previousName: String,
    @ColumnInfo(name = "new_name") val newName: String,
    @ColumnInfo(name = "execution_status") val executionStatus: String, // COMPLETED, FAILED, ROLLED_BACK
    @ColumnInfo(name = "can_undo") val canUndo: Boolean = true,
    @ColumnInfo(name = "error_message") val errorMessage: String? = null
)

/**
 * 8. DuplicateGroups Table
 */
@Entity(
    tableName = "DuplicateGroups",
    indices = [
        Index(value = ["signature_key"]),
        Index(value = ["file_id"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = FileEntity::class,
            parentColumns = ["file_id"],
            childColumns = ["file_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DuplicateGroupEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "duplicate_entry_id") val duplicateEntryId: Long = 0,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "signature_key") val signatureKey: String,
    @ColumnInfo(name = "match_type") val matchType: String, // EXACT_SHA256, SIMILAR_IMAGE_PHASH, SIMILAR_VIDEO, BACKUP_COPY
    @ColumnInfo(name = "file_id") val fileId: Long,
    @ColumnInfo(name = "similarity_percent") val similarityPercent: Int,
    @ColumnInfo(name = "is_recommended_keep") val isRecommendedKeep: Boolean,
    @ColumnInfo(name = "detected_at") val detectedAt: Long = System.currentTimeMillis()
)

/**
 * 9. SearchHistory Table
 */
@Entity(
    tableName = "SearchHistory",
    indices = [Index(value = ["searched_at"])]
)
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "search_id") val searchId: Long = 0,
    @ColumnInfo(name = "query_text") val queryText: String,
    @ColumnInfo(name = "parsed_intent_summary") val parsedIntentSummary: String,
    @ColumnInfo(name = "results_count") val resultsCount: Int,
    @ColumnInfo(name = "is_voice_query") val isVoiceQuery: Boolean = false,
    @ColumnInfo(name = "searched_at") val searchedAt: Long = System.currentTimeMillis()
)

/**
 * 10. UserSettings Table
 */
@Entity(tableName = "UserSettings")
data class UserSettingsEntity(
    @PrimaryKey
    @ColumnInfo(name = "setting_key") val settingKey: String,
    @ColumnInfo(name = "setting_value") val settingValue: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 11. VoiceCommands Table
 */
@Entity(
    tableName = "VoiceCommands",
    indices = [Index(value = ["created_at"])]
)
data class VoiceCommandEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "command_id") val commandId: Long = 0,
    @ColumnInfo(name = "spoken_text") val spokenText: String,
    @ColumnInfo(name = "detected_dialect") val detectedDialect: String, // ar-EG, ar-SA, en-US
    @ColumnInfo(name = "parsed_action_type") val parsedActionType: String,
    @ColumnInfo(name = "is_compound") val isCompound: Boolean,
    @ColumnInfo(name = "execution_plan_json") val executionPlanJson: String,
    @ColumnInfo(name = "was_approved") val wasApproved: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/**
 * 12. ScanSessions Table
 */
@Entity(
    tableName = "ScanSessions",
    indices = [Index(value = ["started_at"])]
)
data class ScanSessionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "session_id") val sessionId: Long = 0,
    @ColumnInfo(name = "root_uri_or_path") val rootUriOrPath: String,
    @ColumnInfo(name = "status") val status: String, // RUNNING, PAUSED, COMPLETED, CANCELLED, FAILED
    @ColumnInfo(name = "total_discovered") val totalDiscovered: Int,
    @ColumnInfo(name = "processed_count") val processedCount: Int,
    @ColumnInfo(name = "new_files_count") val newFilesCount: Int,
    @ColumnInfo(name = "updated_files_count") val updatedFilesCount: Int,
    @ColumnInfo(name = "skipped_unchanged_count") val skippedUnchangedCount: Int,
    @ColumnInfo(name = "inaccessible_count") val inaccessibleCount: Int,
    @ColumnInfo(name = "last_processed_path") val lastProcessedPath: String?,
    @ColumnInfo(name = "started_at") val startedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null
)

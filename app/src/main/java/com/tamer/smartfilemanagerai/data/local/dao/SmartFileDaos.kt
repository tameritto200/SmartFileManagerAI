package com.tamer.smartfilemanagerai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.tamer.smartfilemanagerai.data.local.entity.*
import kotlinx.coroutines.flow.Flow

data class FullFileRecord(
    val file: FileEntity,
    val metadata: FileMetadataEntity?,
    val analysis: AIAnalysisEntity?,
    val tags: List<String>
)

@Dao
interface FileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFile(file: FileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFilesBatch(files: List<FileEntity>): List<Long>

    @Query("SELECT * FROM Files WHERE uri_or_path = :uriOrPath LIMIT 1")
    suspend fun getFileByUri(uriOrPath: String): FileEntity?

    @Query("SELECT * FROM Files WHERE file_id = :fileId LIMIT 1")
    suspend fun getFileById(fileId: Long): FileEntity?

    @Query("SELECT * FROM Files ORDER BY last_modified DESC")
    fun observeAllFiles(): Flow<List<FileEntity>>

    @Query("SELECT * FROM Files WHERE primary_group = :group ORDER BY last_modified DESC")
    fun observeFilesByPrimaryGroup(group: String): Flow<List<FileEntity>>

    @Query("SELECT * FROM Files WHERE size_bytes >= :minBytes ORDER BY size_bytes DESC")
    fun observeLargeFiles(minBytes: Long = 50L * 1024 * 1024): Flow<List<FileEntity>>

    @Query("SELECT * FROM Files WHERE confidence_level = 'LOW' OR primary_group = 'UNCATEGORIZED' ORDER BY last_modified DESC")
    fun observeLowConfidenceFiles(): Flow<List<FileEntity>>

    @Query("SELECT * FROM Files WHERE last_accessed_at IS NOT NULL AND last_accessed_at < :cutoffTimestamp ORDER BY last_accessed_at ASC")
    suspend fun getUnusedFilesSince(cutoffTimestamp: Long): List<FileEntity>

    @Query("SELECT COUNT(*) FROM Files")
    fun observeTotalFileCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(size_bytes), 0) FROM Files")
    fun observeTotalIndexedBytes(): Flow<Long>

    @Query("SELECT COALESCE(SUM(size_bytes), 0) FROM Files WHERE primary_group = :group")
    suspend fun getTotalBytesForGroup(group: String): Long

    @Query("UPDATE Files SET current_name = :newName, uri_or_path = :newUri, parent_folder_path = :newParentPath, last_modified = :modifiedAt WHERE file_id = :fileId")
    suspend fun updateFileLocationAndName(fileId: Long, newName: String, newUri: String, newParentPath: String, modifiedAt: Long)

    @Query("UPDATE Files SET category_id = :categoryId, primary_group = :primaryGroup, confidence_score = :score, confidence_level = :level WHERE file_id = :fileId")
    suspend fun updateFileClassification(fileId: Long, categoryId: Long?, primaryGroup: String, score: Float, level: String)
}

@Dao
interface FolderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(folder: FolderEntity): Long

    @Query("SELECT * FROM Folders ORDER BY folder_name ASC")
    fun observeAllFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM Folders WHERE uri_or_path = :uri LIMIT 1")
    suspend fun getFolderByUri(uri: String): FolderEntity?
}

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Query("SELECT * FROM Categories ORDER BY category_id ASC")
    fun observeAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM Categories WHERE category_code = :code LIMIT 1")
    suspend fun getCategoryByCode(code: String): CategoryEntity?
}

@Dao
interface MetadataAndAnalysisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetadata(metadata: FileMetadataEntity): Long

    @Query("SELECT * FROM FileMetadata WHERE file_id = :fileId LIMIT 1")
    suspend fun getMetadataForFile(fileId: Long): FileMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnalysis(analysis: AIAnalysisEntity): Long

    @Query("SELECT * FROM AIAnalysis WHERE file_id = :fileId LIMIT 1")
    suspend fun getAnalysisForFile(fileId: Long): AIAnalysisEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<TagEntity>)

    @Query("SELECT tag_name FROM Tags WHERE file_id = :fileId")
    suspend fun getTagsForFile(fileId: Long): List<String>
}

@Dao
interface OperationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperation(operation: FileOperationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperationsBatch(operations: List<FileOperationEntity>)

    @Query("SELECT * FROM FileOperations ORDER BY executed_at DESC")
    fun observeAllOperations(): Flow<List<FileOperationEntity>>

    @Query("SELECT * FROM FileOperations WHERE can_undo = 1 AND execution_status = 'COMPLETED' ORDER BY executed_at DESC LIMIT 1")
    suspend fun getLastUndoableOperation(): FileOperationEntity?

    @Query("SELECT * FROM FileOperations WHERE batch_id = :batchId AND can_undo = 1 AND execution_status = 'COMPLETED' ORDER BY operation_id DESC")
    suspend fun getUndoableOperationsByBatch(batchId: String): List<FileOperationEntity>

    @Query("UPDATE FileOperations SET execution_status = :status, can_undo = :canUndo, error_message = :error WHERE operation_id = :operationId")
    suspend fun updateOperationStatus(operationId: Long, status: String, canUndo: Boolean, error: String? = null)
}

@Dao
interface DuplicateDao {
    @Query("DELETE FROM DuplicateGroups")
    suspend fun clearAllDuplicateGroups()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDuplicateEntries(entries: List<DuplicateGroupEntity>)

    @Query("SELECT * FROM DuplicateGroups ORDER BY group_id ASC, is_recommended_keep DESC")
    fun observeAllDuplicateEntries(): Flow<List<DuplicateGroupEntity>>
}

@Dao
interface SearchVoiceAndSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchHistory(entry: SearchHistoryEntity): Long

    @Query("SELECT * FROM SearchHistory ORDER BY searched_at DESC LIMIT 50")
    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoiceCommand(command: VoiceCommandEntity): Long

    @Query("SELECT * FROM VoiceCommands ORDER BY created_at DESC LIMIT 50")
    fun observeVoiceCommands(): Flow<List<VoiceCommandEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScanSession(session: ScanSessionEntity): Long

    @Update
    suspend fun updateScanSession(session: ScanSessionEntity)

    @Query("SELECT * FROM ScanSessions ORDER BY started_at DESC LIMIT 1")
    suspend fun getLatestScanSession(): ScanSessionEntity?

    @Query("SELECT * FROM ScanSessions ORDER BY started_at DESC LIMIT 1")
    fun observeLatestScanSession(): Flow<ScanSessionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSetting(setting: UserSettingsEntity)

    @Query("SELECT setting_value FROM UserSettings WHERE setting_key = :key LIMIT 1")
    suspend fun getSettingValue(key: String): String?
}

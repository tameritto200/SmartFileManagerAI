package com.tamer.smartfilemanagerai.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tamer.smartfilemanagerai.core.model.DetailedCategory
import com.tamer.smartfilemanagerai.data.local.dao.*
import com.tamer.smartfilemanagerai.data.local.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        FileEntity::class,
        FolderEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        FileMetadataEntity::class,
        AIAnalysisEntity::class,
        FileOperationEntity::class,
        DuplicateGroupEntity::class,
        SearchHistoryEntity::class,
        UserSettingsEntity::class,
        VoiceCommandEntity::class,
        ScanSessionEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class SmartFileDatabase : RoomDatabase() {

    abstract fun fileDao(): FileDao
    abstract fun folderDao(): FolderDao
    abstract fun categoryDao(): CategoryDao
    abstract fun metadataAndAnalysisDao(): MetadataAndAnalysisDao
    abstract fun operationDao(): OperationDao
    abstract fun duplicateDao(): DuplicateDao
    abstract fun searchVoiceAndSessionDao(): SearchVoiceAndSessionDao

    companion object {
        @Volatile
        private var INSTANCE: SmartFileDatabase? = null

        fun getInstance(context: Context): SmartFileDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SmartFileDatabase::class.java,
                    "smart_file_manager_ai.db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                INSTANCE?.categoryDao()?.insertCategories(
                                    DetailedCategory.entries.map { cat ->
                                        CategoryEntity(
                                            categoryCode = cat.id,
                                            nameAr = cat.arabicName,
                                            nameEn = cat.englishName,
                                            primaryGroup = cat.primaryGroup.name,
                                            targetSubfolder = "${cat.primaryGroup.folderPrefix}/${cat.subFolderName}",
                                            isCustom = false
                                        )
                                    }
                                )
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

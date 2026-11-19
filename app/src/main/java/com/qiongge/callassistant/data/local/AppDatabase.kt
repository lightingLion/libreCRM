package com.qiongge.callassistant.data.local

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.qiongge.callassistant.data.local.dao.CustomerDao
import com.qiongge.callassistant.data.local.dao.ImportDraftDao
import com.qiongge.callassistant.data.local.dao.TaskDao
import com.qiongge.callassistant.data.local.entity.CallRecordEntity
import com.qiongge.callassistant.data.local.entity.ContactStatusConverter
import com.qiongge.callassistant.data.local.entity.CustomerEntity
import com.qiongge.callassistant.data.local.entity.CustomerListEntity
import com.qiongge.callassistant.data.local.entity.FollowUpEntity
import com.qiongge.callassistant.data.local.entity.ImportRecordEntity
import com.qiongge.callassistant.data.local.entity.ImportDraftEntity
import com.qiongge.callassistant.data.local.entity.ImportDraftImageEntity
import com.qiongge.callassistant.data.local.entity.ImportDraftRowEntity
import com.qiongge.callassistant.data.local.entity.ListMembershipEntity
import com.qiongge.callassistant.data.local.entity.TaskProgressEntity

@Database(
    entities = [
        CustomerEntity::class,
        CustomerListEntity::class,
        ListMembershipEntity::class,
        CallRecordEntity::class,
        FollowUpEntity::class,
        ImportRecordEntity::class,
        TaskProgressEntity::class,
        ImportDraftEntity::class,
        ImportDraftImageEntity::class,
        ImportDraftRowEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(ContactStatusConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun taskDao(): TaskDao
    abstract fun importDraftDao(): ImportDraftDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `import_drafts` " +
                        "(`id` INTEGER NOT NULL, `title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `import_draft_images` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `draftId` INTEGER NOT NULL, " +
                        "`displayName` TEXT NOT NULL, `localPath` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                        "`width` INTEGER NOT NULL, `height` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`draftId`) REFERENCES `import_drafts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_draft_images_draftId` ON `import_draft_images` (`draftId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `import_draft_rows` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `draftId` INTEGER NOT NULL, " +
                        "`sourceImageId` INTEGER, `sortOrder` INTEGER NOT NULL, `name` TEXT NOT NULL, `phone` TEXT NOT NULL, " +
                        "`sourceText` TEXT NOT NULL, `sourceLeft` REAL, `sourceTop` REAL, `sourceRight` REAL, `sourceBottom` REAL, " +
                        "`confidence` REAL, `reviewed` INTEGER NOT NULL, `skipped` INTEGER NOT NULL, " +
                        "`duplicateMode` TEXT, `duplicateCustomerId` INTEGER, " +
                        "FOREIGN KEY(`draftId`) REFERENCES `import_drafts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`sourceImageId`) REFERENCES `import_draft_images`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_draft_rows_draftId` ON `import_draft_rows` (`draftId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_draft_rows_sourceImageId` ON `import_draft_rows` (`sourceImageId`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `import_draft_rows` ADD COLUMN `sourceCells` TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `import_draft_rows` ADD COLUMN `sourceFileName` TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `customers` ADD COLUMN `customResult` TEXT")
                db.execSQL("ALTER TABLE `call_records` ADD COLUMN `customResult` TEXT")
                db.execSQL("ALTER TABLE `list_memberships` ADD COLUMN `building` TEXT")
                db.execSQL("ALTER TABLE `list_memberships` ADD COLUMN `room` TEXT")
                db.execSQL("ALTER TABLE `import_draft_rows` ADD COLUMN `building` TEXT")
                db.execSQL("ALTER TABLE `import_draft_rows` ADD COLUMN `room` TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `import_draft_rows` ADD COLUMN `skipReason` TEXT")
            }
        }
    }
}

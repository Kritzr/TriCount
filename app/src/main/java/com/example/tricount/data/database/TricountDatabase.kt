package com.example.tricount.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.tricount.data.dao.TricountDao
import com.example.tricount.data.dao.UserDao
import com.example.tricount.data.entity.ExpenseEntity
import com.example.tricount.data.entity.ExpenseSplitEntity
import com.example.tricount.data.entity.TricountEntity
import com.example.tricount.data.entity.TricountMemberCrossRef
import com.example.tricount.data.entity.UserEntity
import com.example.tricount.data.entity.TricountFavorite
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserEntity::class,
        TricountEntity::class,
        TricountMemberCrossRef::class,
        ExpenseEntity::class,
        TricountFavorite::class,
        ExpenseSplitEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class TricountDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun tricountDao(): TricountDao

    companion object {

        @Volatile
        private var INSTANCE: TricountDatabase? = null

        fun getDatabase(context: Context): TricountDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TricountDatabase::class.java,
                    "tricount_database"
                )
                    .addMigrations(
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10
                    )
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }

        // 4 → 5 : tricount_favorites table
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `tricount_favorites` (
                        `userId` INTEGER NOT NULL,
                        `tricountId` INTEGER NOT NULL,
                        `favoritedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`userId`, `tricountId`),
                        FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`tricountId`) REFERENCES `tricounts`(`id`) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_tricount_favorites_userId` ON `tricount_favorites` (`userId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_tricount_favorites_tricountId` ON `tricount_favorites` (`tricountId`)")
            }
        }

        // 5 → 6 : expense_splits table
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `expense_splits` (
                        `expenseId` INTEGER NOT NULL,
                        `userId` INTEGER NOT NULL,
                        `shares` INTEGER NOT NULL DEFAULT 1,
                        PRIMARY KEY(`expenseId`, `userId`),
                        FOREIGN KEY(`expenseId`) REFERENCES `expenses`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_splits_expenseId` ON `expense_splits` (`expenseId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_splits_userId` ON `expense_splits` (`userId`)")
            }
        }

        // 6 → 7 : nickname + photoUri
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `users` ADD COLUMN `nickname` TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE `users` ADD COLUMN `photoUri` TEXT DEFAULT NULL")
            }
        }

        // 7 → 8 : isArchived on expenses
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `expenses` ADD COLUMN `isArchived` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        // 8 → 9 : isArchived on tricounts
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `tricounts` ADD COLUMN `isArchived` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        // 9 → 10 : Recreate tricounts table with exact Room-expected schema
        // This fixes the "Migration didn't properly handle: tricounts" error
        // caused by schema mismatch between the old table and Room's entity definition.
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. Create new tricounts table with exact Room schema
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `tricounts_new` (
                        `id`          INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name`        TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `creatorId`   INTEGER NOT NULL,
                        `joinCode`    TEXT NOT NULL,
                        `createdAt`   INTEGER NOT NULL,
                        `isArchived`  INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`creatorId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                // 2. Copy existing data (isArchived defaults to 0 for old rows)
                database.execSQL("""
                    INSERT INTO `tricounts_new` (`id`,`name`,`description`,`creatorId`,`joinCode`,`createdAt`,`isArchived`)
                    SELECT `id`,`name`,`description`,`creatorId`,`joinCode`,`createdAt`,
                           COALESCE(`isArchived`, 0)
                    FROM `tricounts`
                """)
                // 3. Drop old table
                database.execSQL("DROP TABLE `tricounts`")
                // 4. Rename new table
                database.execSQL("ALTER TABLE `tricounts_new` RENAME TO `tricounts`")
                // 5. Recreate index
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_tricounts_creatorId` ON `tricounts` (`creatorId`)")
            }
        }

        fun clearInstance() {
            INSTANCE = null
        }
    }
}
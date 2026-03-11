package com.example.tricount.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.tricount.data.dao.TricountDao
import com.example.tricount.data.dao.UserDao
import com.example.tricount.data.dao.PaymentDao
import com.example.tricount.data.entity.ExpenseEntity
import com.example.tricount.data.entity.ExpenseSplitEntity
import com.example.tricount.data.entity.TricountEntity
import com.example.tricount.data.entity.TricountMemberCrossRef
import com.example.tricount.data.entity.UserEntity
import com.example.tricount.data.entity.TricountFavorite
import com.example.tricount.data.entity.PaymentEntity
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserEntity::class,
        TricountEntity::class,
        TricountMemberCrossRef::class,
        ExpenseEntity::class,
        TricountFavorite::class,
        ExpenseSplitEntity::class,
        PaymentEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class TricountDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun tricountDao(): TricountDao
    abstract fun paymentDao(): PaymentDao

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
                        MIGRATION_9_10,
                        MIGRATION_10_11
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

        // 6 → 7 : nickname + photoUri (kept as-is for devices already on v7)
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `users` ADD COLUMN `nickname` TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE `users` ADD COLUMN `photoUri` TEXT DEFAULT NULL")
            }
        }

        // 7 → 8 : isArchived on expenses
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `expenses` ADD COLUMN `isArchived` INTEGER NOT NULL DEFAULT 0")
            }
        }

        // 8 → 9 : isArchived on tricounts
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `tricounts` ADD COLUMN `isArchived` INTEGER NOT NULL DEFAULT 0")
            }
        }

        // 9 → 10 : Recreate users + tricounts with exact Room-expected schema.
        // Fixes "Migration didn't properly handle: tricounts / users":
        //   - users: nickname/photoUri had DEFAULT 'NULL' string instead of real NULL
        //   - tricounts: foreign key was missing ON UPDATE NO ACTION clause
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {

                // ── Recreate users ──────────────────────────────────────────
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `users_new` (
                        `id`        INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name`      TEXT NOT NULL,
                        `email`     TEXT NOT NULL,
                        `password`  TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `nickname`  TEXT DEFAULT NULL,
                        `photoUri`  TEXT DEFAULT NULL
                    )
                """)
                // Convert stored 'NULL' strings back to real SQL NULL
                database.execSQL("""
                    INSERT INTO `users_new` (`id`,`name`,`email`,`password`,`createdAt`,`nickname`,`photoUri`)
                    SELECT `id`,`name`,`email`,`password`,`createdAt`,
                           CASE WHEN `nickname` = 'NULL' THEN NULL ELSE `nickname` END,
                           CASE WHEN `photoUri` = 'NULL' THEN NULL ELSE `photoUri` END
                    FROM `users`
                """)
                database.execSQL("DROP TABLE `users`")
                database.execSQL("ALTER TABLE `users_new` RENAME TO `users`")

                // ── Recreate tricounts ──────────────────────────────────────
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `tricounts_new` (
                        `id`          INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name`        TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `creatorId`   INTEGER NOT NULL,
                        `joinCode`    TEXT NOT NULL,
                        `createdAt`   INTEGER NOT NULL,
                        `isArchived`  INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`creatorId`) REFERENCES `users`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                database.execSQL("""
                    INSERT INTO `tricounts_new`
                        (`id`,`name`,`description`,`creatorId`,`joinCode`,`createdAt`,`isArchived`)
                    SELECT `id`,`name`,`description`,`creatorId`,`joinCode`,`createdAt`,
                           COALESCE(`isArchived`, 0)
                    FROM `tricounts`
                """)
                database.execSQL("DROP TABLE `tricounts`")
                database.execSQL("ALTER TABLE `tricounts_new` RENAME TO `tricounts`")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_tricounts_creatorId` ON `tricounts` (`creatorId`)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `payments` (
                        `id`           INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `tricountId`   INTEGER NOT NULL,
                        `fromUserId`   INTEGER NOT NULL,
                        `fromUserName` TEXT NOT NULL,
                        `toUserId`     INTEGER NOT NULL,
                        `toUserName`   TEXT NOT NULL,
                        `amount`       REAL NOT NULL,
                        `note`         TEXT NOT NULL DEFAULT 'Settlement payment',
                        `paidAt`       INTEGER NOT NULL,
                        FOREIGN KEY(`tricountId`) REFERENCES `tricounts`(`id`) ON DELETE CASCADE ON UPDATE NO ACTION,
                        FOREIGN KEY(`fromUserId`) REFERENCES `users`(`id`)     ON DELETE CASCADE ON UPDATE NO ACTION,
                        FOREIGN KEY(`toUserId`)   REFERENCES `users`(`id`)     ON DELETE CASCADE ON UPDATE NO ACTION
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_tricountId` ON `payments` (`tricountId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_fromUserId` ON `payments` (`fromUserId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_toUserId`   ON `payments` (`toUserId`)")
            }
        }

        fun clearInstance() {
            INSTANCE = null
        }
    }
}
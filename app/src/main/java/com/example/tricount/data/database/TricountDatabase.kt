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
        ExpenseSplitEntity::class   // ← NEW
    ],
    version = 6,                    // ← bumped from 5
    exportSchema = false
)
abstract class TricountDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun tricountDao(): TricountDao

    companion object {
        @Volatile
        private var INSTANCE: TricountDatabase? = null

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

        // ← NEW migration: adds expense_splits table
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

        fun getDatabase(context: Context): TricountDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TricountDatabase::class.java,
                    "tricount_database"
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6)  // ← added new migration
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun clearInstance() {
            INSTANCE = null
        }
    }
}
package com.example.tricount.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.tricount.data.dao.PaymentDao
import com.example.tricount.data.dao.TricountDao
import com.example.tricount.data.dao.UserDao
import com.example.tricount.data.entity.ExpenseEntity
import com.example.tricount.data.entity.ExpenseSplitEntity
import com.example.tricount.data.entity.PaymentEntity
import com.example.tricount.data.entity.TricountEntity
import com.example.tricount.data.entity.TricountFavorite
import com.example.tricount.data.entity.TricountMemberCrossRef
import com.example.tricount.data.entity.UserEntity

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
    version = 15,
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
                    .fallbackToDestructiveMigrationFrom(1,2,3,4,5,6,7,8,9,10,11,12)
                    .fallbackToDestructiveMigration()
                    .addMigrations(MIGRATION_13_14, MIGRATION_14_15)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `tricount_favorites`")
                db.execSQL("DROP TABLE IF EXISTS `expense_splits`")
                db.execSQL("DROP TABLE IF EXISTS `payments`")
                db.execSQL("DROP TABLE IF EXISTS `expenses`")
                db.execSQL("DROP TABLE IF EXISTS `tricount_members`")
                db.execSQL("DROP TABLE IF EXISTS `tricounts`")
                db.execSQL("DROP TABLE IF EXISTS `users`")
                db.execSQL("""CREATE TABLE `users` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,`name` TEXT NOT NULL,`email` TEXT NOT NULL,`password` TEXT NOT NULL,`createdAt` INTEGER NOT NULL,`nickname` TEXT,`photoUri` TEXT)""")
                db.execSQL("""CREATE TABLE `tricounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,`name` TEXT NOT NULL,`description` TEXT NOT NULL,`creatorId` INTEGER NOT NULL,`joinCode` TEXT NOT NULL,`createdAt` INTEGER NOT NULL,`isArchived` INTEGER NOT NULL DEFAULT 0,`emoji` TEXT NOT NULL DEFAULT '',FOREIGN KEY(`creatorId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX `index_tricounts_creatorId` ON `tricounts` (`creatorId`)")
                db.execSQL("""CREATE TABLE `tricount_members` (`userId` INTEGER NOT NULL,`tricountId` INTEGER NOT NULL,PRIMARY KEY(`userId`,`tricountId`),FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON DELETE CASCADE,FOREIGN KEY(`tricountId`) REFERENCES `tricounts`(`id`) ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX `index_tricount_members_userId` ON `tricount_members` (`userId`)")
                db.execSQL("CREATE INDEX `index_tricount_members_tricountId` ON `tricount_members` (`tricountId`)")
                db.execSQL("""CREATE TABLE `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,`tricountId` INTEGER NOT NULL,`name` TEXT NOT NULL,`description` TEXT NOT NULL,`amount` REAL NOT NULL,`paidBy` INTEGER NOT NULL,`createdAt` INTEGER NOT NULL,`category` TEXT NOT NULL DEFAULT 'General',`isArchived` INTEGER NOT NULL DEFAULT 0,FOREIGN KEY(`tricountId`) REFERENCES `tricounts`(`id`) ON DELETE CASCADE,FOREIGN KEY(`paidBy`) REFERENCES `users`(`id`) ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX `index_expenses_tricountId` ON `expenses` (`tricountId`)")
                db.execSQL("CREATE INDEX `index_expenses_paidBy` ON `expenses` (`paidBy`)")
                db.execSQL("""CREATE TABLE `expense_splits` (`expenseId` INTEGER NOT NULL,`userId` INTEGER NOT NULL,`shares` INTEGER NOT NULL DEFAULT 1,PRIMARY KEY(`expenseId`,`userId`),FOREIGN KEY(`expenseId`) REFERENCES `expenses`(`id`) ON DELETE CASCADE,FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX `index_expense_splits_expenseId` ON `expense_splits` (`expenseId`)")
                db.execSQL("CREATE INDEX `index_expense_splits_userId` ON `expense_splits` (`userId`)")
                db.execSQL("""CREATE TABLE `tricount_favorites` (`userId` INTEGER NOT NULL,`tricountId` INTEGER NOT NULL,`favoritedAt` INTEGER NOT NULL,PRIMARY KEY(`userId`,`tricountId`),FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON DELETE CASCADE,FOREIGN KEY(`tricountId`) REFERENCES `tricounts`(`id`) ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX `index_tricount_favorites_userId` ON `tricount_favorites` (`userId`)")
                db.execSQL("CREATE INDEX `index_tricount_favorites_tricountId` ON `tricount_favorites` (`tricountId`)")
                db.execSQL("""CREATE TABLE `payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,`tricountId` INTEGER NOT NULL,`fromUserId` INTEGER NOT NULL,`fromUserName` TEXT NOT NULL,`toUserId` INTEGER NOT NULL,`toUserName` TEXT NOT NULL,`amount` REAL NOT NULL,`note` TEXT NOT NULL DEFAULT 'Settlement payment',`paidAt` INTEGER NOT NULL,FOREIGN KEY(`tricountId`) REFERENCES `tricounts`(`id`) ON DELETE CASCADE ON UPDATE NO ACTION,FOREIGN KEY(`fromUserId`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE NO ACTION,FOREIGN KEY(`toUserId`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE NO ACTION)""")
                db.execSQL("CREATE INDEX `index_payments_tricountId` ON `payments` (`tricountId`)")
                db.execSQL("CREATE INDEX `index_payments_fromUserId` ON `payments` (`fromUserId`)")
                db.execSQL("CREATE INDEX `index_payments_toUserId` ON `payments` (`toUserId`)")
            }
        }

        // 14 → 15: no schema change, photoUri already in users table
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) { /* no-op */ }
        }

        fun clearInstance() { INSTANCE = null }


    }
}
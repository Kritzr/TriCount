package com.example.tricount.data.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ExpenseEntity::class],
    version = 1
)
abstract class TricountDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao

    companion object {
        @Volatile
        private var INSTANCE: TricountDatabase? = null

        fun getDatabase(context: Context): TricountDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TricountDatabase::class.java,
                    "tricount_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

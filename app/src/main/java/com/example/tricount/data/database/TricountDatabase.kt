package com.example.tricount.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.tricount.data.dao.ExpenseDao
import com.example.tricount.data.dao.TricountDao
import com.example.tricount.data.entity.ExpenseEntity
import com.example.tricount.data.entity.TricountEntity

@Database(
    entities = [TricountEntity::class, ExpenseEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TricountDatabase : RoomDatabase() {

    abstract fun tricountDao(): TricountDao
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

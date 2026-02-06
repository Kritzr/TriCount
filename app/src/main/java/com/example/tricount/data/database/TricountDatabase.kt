package com.example.tricount.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.tricount.data.dao.ExpenseDao
import com.example.tricount.data.dao.TricountDao
import com.example.tricount.data.dao.TricountMemberDao
import com.example.tricount.data.dao.UserDao
import com.example.tricount.data.entity.ExpenseEntity
import com.example.tricount.data.entity.TricountEntity
import com.example.tricount.data.entity.TricountMemberEntity
import com.example.tricount.data.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        TricountEntity::class,
        TricountMemberEntity::class,
        ExpenseEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class TricountDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun tricountDao(): TricountDao
    abstract fun tricountMemberDao(): TricountMemberDao
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
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
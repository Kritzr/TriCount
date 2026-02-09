package com.example.tricount.data.database
//this is the database layer

import android.content.Context //// this is to create the database
import androidx.room.Database //tells the room that this is the basic database
import androidx.room.Room
import androidx.room.RoomDatabase //base class that the db extends
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
    ], //lists all the tables in the database
    version = 2,  // Changed version to 2
    exportSchema = false
)
abstract class TricountDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun tricountDao(): TricountDao
    abstract fun tricountMemberDao(): TricountMemberDao
    abstract fun expenseDao(): ExpenseDao

    companion object {// for the singleton database instance - that is it ensure only a single databse exists to prevent memory leaks and data corruption
        @Volatile //makes the writes visisble accross the threads
        private var INSTANCE: TricountDatabase? = null

        fun getDatabase(context: Context): TricountDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TricountDatabase::class.java,
                    "tricount_database_v2"  // Changed database name
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
package com.example.tricount.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tricount.data.entity.UserEntity

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long

    @Query("SELECT * FROM users WHERE email = :email AND password = :password")
    suspend fun login(email: String, password: String): UserEntity?

    @Query("SELECT * FROM users WHERE email = :email")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: Int): UserEntity?

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUser(userId: Int)

    // ── Nickname ──────────────────────────────────────────────────────────────

    @Query("UPDATE users SET nickname = :nickname WHERE id = :userId")
    suspend fun updateNickname(userId: Int, nickname: String)

    /** Returns the stored nickname (null if never set). Used by FirestoreSync. */
    @Query("SELECT nickname FROM users WHERE id = :userId")
    suspend fun getUserNickname(userId: Int): String?

    // ── Photo URI ─────────────────────────────────────────────────────────────

    @Query("UPDATE users SET photoUri = :photoUri WHERE id = :userId")
    suspend fun updatePhotoUri(userId: Int, photoUri: String): Int

    @Query("SELECT photoUri FROM users WHERE id = :userId")
    suspend fun getUserPhotoUri(userId: Int): String?

    // ── Firebase UID ──────────────────────────────────────────────────────────

    @Query("UPDATE users SET firebaseUid = :firebaseUid WHERE id = :userId")
    suspend fun updateFirebaseUid(userId: Int, firebaseUid: String)

    @Query("UPDATE users SET firebaseUid = :firebaseUid WHERE email = :email")
    suspend fun updateFirebaseUidByEmail(email: String, firebaseUid: String)

    @Query("SELECT firebaseUid FROM users WHERE id = :userId")
    suspend fun getFirebaseUid(userId: Int): String?
}
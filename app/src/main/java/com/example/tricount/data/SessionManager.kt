package com.example.tricount.data

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // ── Auth ──────────────────────────────────────────────────────────────────

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    fun saveSession(userId: Int, name: String, email: String, firebaseUid: String = "") {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putInt(KEY_USER_ID, userId)
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_FIREBASE_UID, firebaseUid)
            .apply()
    }

    fun clearSession() {
        // FIX: preserve photo + nickname across logout so they survive re-login
        val savedPhoto    = getProfilePhotoUri()
        val savedNickname = getNickname()
        val savedEmail    = getUserEmail()

        prefs.edit().clear().apply()

        prefs.edit()
            .putString(KEY_PROFILE_PHOTO_URI, savedPhoto ?: "")
            .putString(KEY_NICKNAME, savedNickname)
            .putString(KEY_USER_EMAIL, savedEmail ?: "")
            .apply()
    }

    // ── User identity ─────────────────────────────────────────────────────────

    fun getUserId(): Int? {
        val id = prefs.getInt(KEY_USER_ID, -1)
        return if (id == -1) null else id
    }

    fun getUserName(): String? = prefs.getString(KEY_USER_NAME, null)

    fun setUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name).apply()
    }

    fun getUserEmail(): String? = prefs.getString(KEY_USER_EMAIL, null)

    // ── Firebase UID ──────────────────────────────────────────────────────────

    fun getFirebaseUid(): String? = prefs.getString(KEY_FIREBASE_UID, null)

    fun setFirebaseUid(uid: String) {
        prefs.edit().putString(KEY_FIREBASE_UID, uid).apply()
    }

    // ── Nickname ──────────────────────────────────────────────────────────────

    fun getNickname(): String = prefs.getString(KEY_NICKNAME, "") ?: ""

    fun setNickname(nickname: String) {
        prefs.edit().putString(KEY_NICKNAME, nickname).apply()
    }

    // ── Profile photo ─────────────────────────────────────────────────────────

    fun getProfilePhotoUri(): String? = prefs.getString(KEY_PROFILE_PHOTO_URI, null)

    fun setProfilePhotoUri(uri: String) {
        prefs.edit().putString(KEY_PROFILE_PHOTO_URI, uri).apply()
    }

    fun clearProfilePhotoUri() {
        prefs.edit().remove(KEY_PROFILE_PHOTO_URI).apply()
    }

    // ── Preferences ───────────────────────────────────────────────────────────

    fun getDarkMode(): Boolean = prefs.getBoolean(KEY_DARK_MODE, false)

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }

    fun getLanguage(): String = prefs.getString(KEY_LANGUAGE, "English") ?: "English"

    fun setLanguage(language: String) {
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
    }

    // ── Keys ──────────────────────────────────────────────────────────────────

    companion object {
        private const val PREF_NAME             = "tricount_session"
        private const val KEY_IS_LOGGED_IN      = "is_logged_in"
        private const val KEY_USER_ID           = "user_id"
        private const val KEY_USER_NAME         = "user_name"
        private const val KEY_USER_EMAIL        = "user_email"
        private const val KEY_FIREBASE_UID      = "firebase_uid"
        private const val KEY_NICKNAME          = "nickname"
        private const val KEY_PROFILE_PHOTO_URI = "profile_photo_uri"
        private const val KEY_DARK_MODE         = "dark_mode"
        private const val KEY_LANGUAGE          = "language"
    }
}
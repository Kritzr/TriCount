package com.example.tricount.data

import android.content.Context
import android.content.SharedPreferences
import kotlin.apply

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // ── Core auth fields ─────────────────────────────────────────────────────

    fun getUserId(): Int? {
        val id = prefs.getInt(KEY_USER_ID, -1)
        return if (id == -1) null else id
    }

    fun setUserId(id: Int) = prefs.edit().putInt(KEY_USER_ID, id).apply()

    fun getUserName(): String? = prefs.getString(KEY_USER_NAME, null)

    fun setUserName(name: String) = prefs.edit().putString(KEY_USER_NAME, name).apply()

    fun getUserEmail(): String? = prefs.getString(KEY_USER_EMAIL, null)

    fun setUserEmail(email: String) = prefs.edit().putString(KEY_USER_EMAIL, email).apply()

    fun isLoggedIn(): Boolean = getUserId() != null

    /**
     * Convenience method called by AuthViewModel on login / signup.
     * Saves the three core auth fields atomically in one editor commit.
     */
    fun saveSession(userId: Int, email: String, name: String) {
        prefs.edit()
            .putInt(KEY_USER_ID, userId)
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_USER_NAME, name)
            .apply()
    }

    /** Wipes everything — called on logout and account deletion. */
    fun clearSession() = prefs.edit().clear().apply()

    // ── Profile extras ───────────────────────────────────────────────────────

    /** Public-facing nickname shown to other members (e.g. @krithika) */
    fun getNickname(): String = prefs.getString(KEY_NICKNAME, "") ?: ""

    fun setNickname(nickname: String) =
        prefs.edit().putString(KEY_NICKNAME, nickname).apply()

    /** URI string of the user's chosen profile photo (persisted across restarts) */
    fun getProfilePhotoUri(): String? = prefs.getString(KEY_PROFILE_PHOTO_URI, null)

    fun setProfilePhotoUri(uri: String) =
        prefs.edit().putString(KEY_PROFILE_PHOTO_URI, uri).apply()

    fun clearProfilePhotoUri() =
        prefs.edit().remove(KEY_PROFILE_PHOTO_URI).apply()

    // ── Preferences ──────────────────────────────────────────────────────────

    /** UI language label, e.g. "English", "Spanish" */
    fun getLanguage(): String = prefs.getString(KEY_LANGUAGE, "English") ?: "English"

    fun setLanguage(language: String) =
        prefs.edit().putString(KEY_LANGUAGE, language).apply()

    /** Dark-mode flag */
    fun getDarkMode(): Boolean = prefs.getBoolean(KEY_DARK_MODE, false)

    fun setDarkMode(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()

    fun saveFirebaseUid(uid: String) = prefs.edit().putString("firebase_uid", uid).apply()
    fun getFirebaseUid(): String? = prefs.getString("firebase_uid", null)

    // ── Constants ────────────────────────────────────────────────────────────

    companion object {
        private const val PREF_NAME             = "tricount_session"
        private const val KEY_USER_ID           = "user_id"
        private const val KEY_USER_NAME         = "user_name"
        private const val KEY_USER_EMAIL        = "user_email"
        private const val KEY_NICKNAME          = "nickname"
        private const val KEY_PROFILE_PHOTO_URI = "profile_photo_uri"
        private const val KEY_LANGUAGE          = "language"

        private const val KEY_DARK_MODE         = "dark_mode"
    }
}
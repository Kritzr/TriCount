package com.example.tricount.data

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // ── Auth ──────────────────────────────────────────────────────────────────

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    fun saveSession(userId: Int, name: String, email: String, firebaseUid: String = "") {
        // IMPORTANT: userId must be written FIRST so that any subsequent
        // getNickname() / getProfilePhotoUri() calls immediately resolve to
        // the correct per-user key.
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putInt(KEY_USER_ID, userId)
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_FIREBASE_UID, firebaseUid)
            .commit()  // commit() (synchronous) instead of apply() so getUserId()
        // is never stale when called immediately after saveSession().
    }

    fun clearSession() {
        val savedDarkMode = getDarkMode()
        val savedLanguage = getLanguage()

        prefs.edit().clear().apply()

        prefs.edit()
            .putBoolean(KEY_DARK_MODE, savedDarkMode)
            .putString(KEY_LANGUAGE, savedLanguage)
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

    // ── Nickname (per-user) ───────────────────────────────────────────────────
    // BUG FIX: getNickname / setNickname now REQUIRE a valid userId. If called
    // before saveSession(), they return "" / no-op rather than silently writing
    // to a shared bare key that would bleed across accounts.

    fun getNickname(): String {
        val key = userKeyStrict(KEY_NICKNAME) ?: return ""
        return prefs.getString(key, "") ?: ""
    }

    fun setNickname(nickname: String) {
        val key = userKeyStrict(KEY_NICKNAME) ?: return
        prefs.edit().putString(key, nickname).apply()
    }

    // ── Profile photo (per-user) ──────────────────────────────────────────────

    fun getProfilePhotoUri(): String? {
        val key = userKeyStrict(KEY_PROFILE_PHOTO_URI) ?: return null
        return prefs.getString(key, null)
    }

    fun setProfilePhotoUri(uri: String) {
        val key = userKeyStrict(KEY_PROFILE_PHOTO_URI) ?: return
        prefs.edit().putString(key, uri).apply()
    }

    fun clearProfilePhotoUri() {
        val key = userKeyStrict(KEY_PROFILE_PHOTO_URI) ?: return
        prefs.edit().remove(key).apply()
    }

    // ── Pending signup (used during email OTP verification flow) ──────────────

    fun setPendingSignupName(name: String) {
        prefs.edit().putString(KEY_PENDING_NAME, name).apply()
    }

    fun getPendingSignupName(): String? = prefs.getString(KEY_PENDING_NAME, null)

    fun setPendingSignupEmail(email: String) {
        prefs.edit().putString(KEY_PENDING_EMAIL, email).apply()
    }

    fun getPendingSignupEmail(): String? = prefs.getString(KEY_PENDING_EMAIL, null)

    fun clearPendingSignup() {
        prefs.edit()
            .remove(KEY_PENDING_NAME)
            .remove(KEY_PENDING_EMAIL)
            .apply()
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

    // ── Per-user key helpers ──────────────────────────────────────────────────

    /**
     * Returns "${base}_${userId}" when a user is logged in, otherwise falls
     * back to the bare key. Use for legacy reads only.
     */
    private fun userKey(base: String): String {
        val uid = getUserId()
        return if (uid != null) "${base}_$uid" else base
    }

    /**
     * Returns "${base}_${userId}" ONLY when a valid user ID is present.
     * Returns null if not logged in — callers must handle the null case
     * instead of accidentally writing to a shared bare key.
     */
    private fun userKeyStrict(base: String): String? {
        val uid = getUserId() ?: return null
        return "${base}_$uid"
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
        private const val KEY_PENDING_NAME      = "pending_signup_name"
        private const val KEY_PENDING_EMAIL     = "pending_signup_email"
    }
}
package com.example.tricount.data

import android.content.Context
import android.content.SharedPreferences

class ThemePreferenceManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val THEME_SYSTEM = "system"
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
    }

    fun getThemeMode(): String {
        return prefs.getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM
    }

    fun isLightMode(): Boolean {
        return getThemeMode() == THEME_LIGHT
    }

    fun isDarkMode(): Boolean {
        return getThemeMode() == THEME_DARK
    }

    fun isSystemMode(): Boolean {
        return getThemeMode() == THEME_SYSTEM
    }
}
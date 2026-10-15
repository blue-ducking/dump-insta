package com.meta.instagram.nodoomscroll.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Instagram web session lives in the WebView cookie jar; we only persist the
 * fact that the user logged in + last path, in device-encrypted storage.
 * No IG password is ever touched by our code (login happens on instagram.com).
 */
class SessionStore(context: Context) {

    private val prefs by lazy {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "ig_session", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_LOGGED_IN, false)
        set(v) { prefs.edit().putBoolean(KEY_LOGGED_IN, v).apply() }

    var lastPath: String
        get() = prefs.getString(KEY_PATH, "/direct/") ?: "/direct/"
        set(v) { prefs.edit().putString(KEY_PATH, v).apply() }

    fun clear() = prefs.edit().clear().apply()

    companion object { private const val KEY_LOGGED_IN = "logged_in"; private const val KEY_PATH = "last_path" }
}

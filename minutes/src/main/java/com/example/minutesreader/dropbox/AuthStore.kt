package com.example.minutesreader.dropbox

import android.content.Context

/** Dropbox の App key とトークンの保存先（端末内のみ）。 */
class AuthStore(context: Context) {
    private val sp = context.getSharedPreferences("dropbox_auth", Context.MODE_PRIVATE)

    val appKey: String? get() = sp.getString(KEY_APP, null)
    val accessToken: String? get() = sp.getString(KEY_ACCESS, null)
    val refreshToken: String? get() = sp.getString(KEY_REFRESH, null)
    val expiresAt: Long get() = sp.getLong(KEY_EXPIRES, 0L)
    val accountName: String? get() = sp.getString(KEY_ACCOUNT, null)
    val isLoggedIn: Boolean get() = !refreshToken.isNullOrEmpty() && !appKey.isNullOrEmpty()

    var pendingVerifier: String?
        get() = sp.getString(KEY_VERIFIER, null)
        set(v) { sp.edit().putString(KEY_VERIFIER, v).apply() }

    fun saveAppKey(key: String) { sp.edit().putString(KEY_APP, key).apply() }

    fun saveTokens(access: String, refresh: String?, expiresInSec: Long) {
        sp.edit()
            .putString(KEY_ACCESS, access)
            .apply { if (!refresh.isNullOrEmpty()) putString(KEY_REFRESH, refresh) }
            .putLong(KEY_EXPIRES, System.currentTimeMillis() + expiresInSec * 1000)
            .remove(KEY_VERIFIER)
            .apply()
    }

    fun saveAccountName(name: String) { sp.edit().putString(KEY_ACCOUNT, name).apply() }

    /** ログアウト（App key は再ログイン用に残す）。 */
    fun clearTokens() {
        sp.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).remove(KEY_EXPIRES)
            .remove(KEY_ACCOUNT).remove(KEY_VERIFIER).apply()
    }

    private companion object {
        const val KEY_APP = "app_key"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES = "expires_at"
        const val KEY_ACCOUNT = "account_name"
        const val KEY_VERIFIER = "pkce_verifier"
    }
}

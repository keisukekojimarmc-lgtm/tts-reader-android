package com.example.minutesreader.dropbox

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit

class DropboxException(message: String, val status: Int = 0) : IOException(message)

/**
 * Dropbox HTTP API の最小限のクライアント。
 * 認証は PKCE（クライアントシークレット不要）+「コードを貼り付ける」方式。
 * すべてブロッキング呼び出しなので IO スレッドから使うこと。
 */
class DropboxClient(private val auth: AuthStore) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // ---- 認証 ----

    /** 新しい PKCE verifier を作って保存し、ブラウザで開く認可 URL を返す。 */
    fun beginAuth(appKey: String): String {
        val bytes = ByteArray(48).also { SecureRandom().nextBytes(it) }
        val verifier = b64url(bytes)
        val challenge = b64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        auth.saveAppKey(appKey)
        auth.pendingVerifier = verifier
        return "https://www.dropbox.com/oauth2/authorize" +
            "?client_id=" + URLEncoder.encode(appKey, "UTF-8") +
            "&response_type=code&token_access_type=offline" +
            "&code_challenge=$challenge&code_challenge_method=S256"
    }

    fun finishAuth(code: String) {
        val appKey = auth.appKey ?: throw DropboxException("App key が未設定です")
        val verifier = auth.pendingVerifier
            ?: throw DropboxException("先に「Dropboxで許可する」を押してください")
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code.trim())
            .add("client_id", appKey)
            .add("code_verifier", verifier)
            .build()
        val json = tokenRequest(body)
        auth.saveTokens(json.getString("access_token"), json.optString("refresh_token"), json.optLong("expires_in", 14400))
        runCatching { auth.saveAccountName(accountName()) }
    }

    @Synchronized
    private fun accessToken(force: Boolean = false): String {
        val current = auth.accessToken
        if (!force && current != null && System.currentTimeMillis() < auth.expiresAt - 60_000) return current
        val refresh = auth.refreshToken ?: throw DropboxException("ログインが必要です", 401)
        val appKey = auth.appKey ?: throw DropboxException("ログインが必要です", 401)
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .add("client_id", appKey)
            .build()
        val json = tokenRequest(body)
        val token = json.getString("access_token")
        auth.saveTokens(token, null, json.optLong("expires_in", 14400))
        return token
    }

    private fun tokenRequest(body: FormBody): JSONObject {
        val req = Request.Builder().url("https://api.dropboxapi.com/oauth2/token").post(body).build()
        http.newCall(req).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val err = runCatching { JSONObject(s) }.getOrNull()
                val msg = when (err?.optString("error")) {
                    "invalid_grant" -> "コードが無効か期限切れです。もう一度「Dropboxで許可する」からやり直してください。"
                    "invalid_client", "invalid_client_id" -> "App key が正しくありません。"
                    else -> err?.optString("error_description")?.takeIf { it.isNotBlank() } ?: "認証に失敗しました (${r.code})"
                }
                throw DropboxException(msg, r.code)
            }
            return JSONObject(s)
        }
    }

    // ---- API 呼び出し ----

    private fun call(build: (String) -> Request): Response {
        val first = http.newCall(build(accessToken())).execute()
        if (first.code != 401) return first
        first.close()
        return http.newCall(build(accessToken(force = true))).execute()
    }

    private fun rpc(endpoint: String, args: String): JSONObject {
        val res = call { token ->
            Request.Builder()
                .url("https://api.dropboxapi.com/2/$endpoint")
                .header("Authorization", "Bearer $token")
                .post(args.toRequestBody(JSON))
                .build()
        }
        res.use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw apiError(r.code, s)
            return JSONObject(s)
        }
    }

    fun accountName(): String =
        rpc("users/get_current_account", "null").optJSONObject("name")?.optString("display_name").orEmpty()

    /** root 以下（サブフォルダ含む）の .md ファイルをすべて返す。root は "" か "/..."。 */
    fun listMarkdown(root: String): List<RemoteFile> {
        val out = ArrayList<RemoteFile>()
        var json = rpc(
            "files/list_folder",
            JSONObject()
                .put("path", root)
                .put("recursive", true)
                .put("include_deleted", false)
                .put("limit", 2000)
                .toString(),
        )
        while (true) {
            val entries = json.getJSONArray("entries")
            for (i in 0 until entries.length()) {
                val e = entries.getJSONObject(i)
                if (e.optString(".tag") != "file") continue
                val name = e.optString("name")
                if (!name.endsWith(".md", ignoreCase = true)) continue
                out += RemoteFile(
                    id = e.getString("id"),
                    name = name,
                    pathDisplay = e.optString("path_display", name),
                    rev = e.optString("rev"),
                    serverModified = e.optString("server_modified"),
                )
            }
            if (!json.optBoolean("has_more")) break
            json = rpc("files/list_folder/continue", JSONObject().put("cursor", json.getString("cursor")).toString())
        }
        return out
    }

    /** ファイル本文を UTF-8 文字列で取得する。pathOrId は "id:xxx" 推奨。 */
    fun download(pathOrId: String): String {
        val arg = asciiJson(JSONObject().put("path", pathOrId).toString())
        val res = call { token ->
            Request.Builder()
                .url("https://content.dropboxapi.com/2/files/download")
                .header("Authorization", "Bearer $token")
                .header("Dropbox-API-Arg", arg)
                .post(ByteArray(0).toRequestBody(null))
                .build()
        }
        res.use { r ->
            if (!r.isSuccessful) throw apiError(r.code, r.body?.string().orEmpty())
            return r.body?.bytes()?.toString(Charsets.UTF_8).orEmpty()
        }
    }

    private fun apiError(code: Int, body: String): DropboxException {
        val summary = runCatching { JSONObject(body).optString("error_summary") }.getOrNull().orEmpty()
        val msg = when {
            code == 401 -> "Dropbox の認証が切れました。設定画面から再ログインしてください。"
            code == 409 && summary.contains("not_found") -> "フォルダが見つかりません。設定画面で議事録フォルダのパスを確認してください。"
            code == 400 && body.contains("scope") -> "Dropbox アプリの権限が不足しています（files.metadata.read / files.content.read）。"
            code == 429 -> "Dropbox が混み合っています。少し待ってから更新してください。"
            else -> "Dropbox エラー ($code) ${summary.ifBlank { body.take(120) }}"
        }
        return DropboxException(msg, code)
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()

        fun b64url(b: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(b)

        /** HTTP ヘッダーに入れる JSON は非 ASCII 文字を \\uXXXX にする必要がある。 */
        fun asciiJson(s: String): String = buildString {
            for (c in s) if (c.code < 0x80) append(c) else append(String.format("\\u%04x", c.code))
        }
    }
}

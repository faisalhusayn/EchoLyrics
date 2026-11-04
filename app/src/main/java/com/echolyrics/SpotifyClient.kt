package com.echolyrics

import android.net.Uri
import android.util.Base64
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

class SpotifyClient(private val prefs: Prefs) {
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
    private val scope = "user-read-currently-playing user-read-playback-state user-modify-playback-state user-library-read user-library-modify"
    private var verifier: String? = null
    fun authUrl(): String { val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }; val flags = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING; verifier = Base64.encodeToString(bytes, flags); val challenge = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier!!.toByteArray()), flags); return Uri.parse("https://accounts.spotify.com/authorize").buildUpon().appendQueryParameter("client_id", BuildConfig.SPOTIFY_CLIENT_ID).appendQueryParameter("response_type", "code").appendQueryParameter("redirect_uri", REDIRECT).appendQueryParameter("code_challenge_method", "S256").appendQueryParameter("code_challenge", challenge).appendQueryParameter("scope", scope).build().toString() }
    fun exchange(code: String): Boolean = tokenRequest("grant_type" to "authorization_code", "code" to code, "redirect_uri" to REDIRECT, "code_verifier" to verifier.orEmpty())
    fun refresh(): Boolean { val token = prefs.refreshToken ?: return false; return tokenRequest("grant_type" to "refresh_token", "refresh_token" to token) }
    private fun tokenRequest(vararg values: Pair<String, String>): Boolean {
        val body = FormBody.Builder().add("client_id", BuildConfig.SPOTIFY_CLIENT_ID).apply { values.forEach { add(it.first, it.second) } }.build()
        return try {
            http.newCall(Request.Builder().url("https://accounts.spotify.com/api/token").post(body).build()).execute().use { response ->
                if (!response.isSuccessful) { DebugLog.add("token status=${response.code}"); false } else {
                    val obj = Json.parseToJsonElement(response.body!!.string()).jsonObject
                    prefs.accessToken = obj["access_token"]?.jsonPrimitive?.content ?: return false
                    prefs.expiresAt = System.currentTimeMillis() + (obj["expires_in"]?.jsonPrimitive?.long ?: 3600) * 1000
                    obj["refresh_token"]?.jsonPrimitive?.content?.let { prefs.refreshToken = it }
                    obj["scope"]?.jsonPrimitive?.content?.let { prefs.grantedScope = it }
                    prefs.needsReauth = !prefs.grantedScope.split(" ").containsAll(listOf("user-modify-playback-state", "user-library-read", "user-library-modify"))
                    DebugLog.add("token refresh success")
                    true
                }
            }
        } catch (_: Exception) { DebugLog.add("token refresh network error"); false }
    }
    fun current(): ApiResult<Track?> = request("GET", "/v1/me/player/currently-playing") { obj -> parseTrack(obj) }
    fun queue(): ApiResult<List<Track>> = request("GET", "/v1/me/player/queue") { obj -> obj["queue"]?.jsonArray?.mapNotNull { parseTrack(it.jsonObject, true) } ?: emptyList() }
    fun saved(id: String): ApiResult<Boolean> = request("GET", "/v1/me/tracks/contains?ids=${Uri.encode(id)}") { obj -> obj["0"]?.jsonPrimitive?.boolean ?: false }
    fun save(id: String, value: Boolean): ApiResult<Boolean> = control("/v1/me/tracks?ids=${Uri.encode(id)}", if (value) "PUT" else "DELETE")
    fun control(path: String, method: String, deviceId: String? = null): ApiResult<Boolean> { if (prefs.accessToken == null && prefs.refreshToken == null) return ApiResult(401, error = "login required"); val separator = if (path.contains("?")) "&" else "?"; val full = if (deviceId == null) path else "$path${separator}device_id=${Uri.encode(deviceId)}"; return request(method, full) { true } }
    fun seek(positionMs: Long, deviceId: String? = null): ApiResult<Boolean> = control("/v1/me/player/seek?position_ms=${positionMs.coerceAtLeast(0)}", "PUT", deviceId)
    fun shuffle(value: Boolean, deviceId: String? = null): ApiResult<Boolean> = control("/v1/me/player/shuffle?state=$value", "PUT", deviceId)
    fun repeat(value: String, deviceId: String? = null): ApiResult<Boolean> = control("/v1/me/player/repeat?state=$value", "PUT", deviceId)
    private fun parseTrack(obj: JsonObject, direct: Boolean = false): Track? { val item = if (direct) obj else obj["item"]?.jsonObject ?: return null; val album = item["album"]?.jsonObject ?: return null; val artists = item["artists"]?.jsonArray?.joinToString(", ") { it.jsonObject["name"]!!.jsonPrimitive.content } ?: ""; return Track(item["id"]!!.jsonPrimitive.content, item["name"]!!.jsonPrimitive.content, artists, album["name"]!!.jsonPrimitive.content, item["duration_ms"]!!.jsonPrimitive.int, obj["progress_ms"]?.jsonPrimitive?.long ?: 0, obj["is_playing"]?.jsonPrimitive?.boolean ?: false, album["images"]?.jsonArray?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content, obj["device"]?.jsonObject?.get("id")?.jsonPrimitive?.content, obj["shuffle_state"]?.jsonPrimitive?.boolean ?: false, obj["repeat_state"]?.jsonPrimitive?.content ?: "off") }
    private fun <T> request(method: String, path: String, parse: (JsonObject) -> T): ApiResult<T> {
        val blocked = AppState.apiBlockedUntil - System.currentTimeMillis()
        if (blocked > 0) return ApiResult(429, retryAfterMs = blocked, error = "rate limited")
        if (prefs.accessToken == null || prefs.expiresAt < System.currentTimeMillis() + 30000) if (!refresh()) return ApiResult(401, error = "logged out")
        fun call(): Response = http.newCall(Request.Builder().url("https://api.spotify.com$path").method(method, if (method == "GET" || method == "DELETE") null else "{}".toRequestBody(JSON)).header("Authorization", "Bearer ${prefs.accessToken}").build()).execute()
        var response = call()
        if (response.code == 401 && refresh()) { response.close(); response = call() }
        response.use {
            if (it.code == 204) return ApiResult(204, null)
            if (!it.isSuccessful) { val retry = ((it.header("Retry-After")?.toLongOrNull() ?: 15) * 1000).coerceAtLeast(10000); if (it.code == 429) synchronized(AppState) { AppState.apiBlockedUntil = maxOf(AppState.apiBlockedUntil, System.currentTimeMillis() + retry) }; DebugLog.add("api ${it.code} ${path.substringBefore('?')}"); return ApiResult(it.code, retryAfterMs = retry, error = it.message) }
            val body = it.body?.string().orEmpty().trim()
            val element = if (body.isEmpty()) null else runCatching { Json.parseToJsonElement(body) }.getOrNull()
            val objectBody = when (element) { is JsonObject -> element; is JsonArray -> buildJsonObject { element.forEachIndexed { index, value -> put(index.toString(), value) } }; else -> buildJsonObject { } }
            return ApiResult(it.code, parse(objectBody))
        }
    }
    companion object { private const val REDIRECT = "echolyrics://callback"; private val JSON = "application/json".toMediaType() }
}

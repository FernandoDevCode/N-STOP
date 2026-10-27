package com.stash.desktop

import com.stash.core.auth.model.UserInfo
import com.stash.core.auth.youtube.YouTubeCookieHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest

/** A syntactically valid cookie alone is insufficient: require an authenticated account header. */
class YouTubeSessionValidator(private val client: OkHttpClient) {
    suspend fun validate(cookie: String): UserInfo = withContext(Dispatchers.IO) {
        val helper = YouTubeCookieHelper()
        val sid = helper.extractSapiSid(cookie) ?: error("A sessão não contém SAPISID ou __Secure-3PAPISID.")
        val body = buildJsonObject { putJsonObject("context") { putJsonObject("client") {
            put("clientName", "WEB_REMIX"); put("clientVersion", "1.20251001.01.00"); put("hl", "en")
        } } }
        val request = Request.Builder().url("https://music.youtube.com/youtubei/v1/account/account_menu")
            .header("Origin", "https://music.youtube.com").header("X-Origin", "https://music.youtube.com")
            .header("X-YouTube-Client-Name", "67").header("X-YouTube-Client-Version", "1.20251001.01.00")
            .header("Cookie", cookie).header("Authorization", helper.generateAuthHeader(sid))
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "O YouTube não validou esta sessão. Entre novamente no navegador." }
            val root = Json.parseToJsonElement(response.body?.string() ?: error("Resposta vazia do YouTube."))
            parseYouTubeAccount(root) ?: error("O YouTube respondeu sem uma conta autenticada. Verifique a sessão copiada.")
        }
    }
}
internal fun parseYouTubeAccount(root: JsonElement): UserInfo? {
    fun find(element: JsonElement): JsonObject? = when (element) {
        is JsonObject -> element["activeAccountHeaderRenderer"] as? JsonObject ?: element.values.firstNotNullOfOrNull(::find)
        is JsonArray -> element.firstNotNullOfOrNull(::find)
        else -> null
    }
    val header = find(root) ?: return null
    fun text(key: String): String? {
        val value = header[key] as? JsonObject ?: return null
        return (value["simpleText"] as? JsonPrimitive)?.contentOrNull
            ?: (value["runs"] as? JsonArray)?.joinToString("") { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty() }
    }
    val name = text("accountName")?.takeIf { it.isNotBlank() } ?: return null
    val identity = text("channelHandle") ?: text("email") ?: name
    val id = MessageDigest.getInstance("SHA-256").digest(identity.encodeToByteArray()).joinToString("") { "%02x".format(it) }
    val image = (header["accountPhoto"] as? JsonObject)?.get("thumbnails")?.jsonArray?.lastOrNull()
        ?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
    return UserInfo(id, name, image)
}

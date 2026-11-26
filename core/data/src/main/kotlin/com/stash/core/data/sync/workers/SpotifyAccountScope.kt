package com.stash.core.data.sync.workers

import java.security.MessageDigest

/** Stable local namespace; never store a cookie, token or raw account ID in a collection ID. */
object SpotifyAccountScope {
    private const val PREFIX = "sonora:spotify:"
    fun prefix(accountId: String): String {
        require(accountId.isNotBlank())
        val digest = MessageDigest.getInstance("SHA-256").digest(accountId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "$PREFIX$digest:"
    }
    fun scoped(prefix: String, rawId: String): String = prefix + rawId
    fun isScoped(sourceId: String): Boolean = sourceId.startsWith(PREFIX) &&
        sourceId.length > PREFIX.length + 65 && sourceId[PREFIX.length + 64] == ':'
    fun raw(sourceId: String): String =
        if (isScoped(sourceId)) sourceId.substring(PREFIX.length + 65) else sourceId
}

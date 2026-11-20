package com.stash.core.network.di

import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.Interceptor

/**
 * Deny access to infrastructure belonging to the upstream project. Also protects against
 * accidentally reactivated persisted jobs or retired call sites using shared OkHttp.
 */
internal object LegacyStashNetworkGuard : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        if (isLegacyStashDestination(chain.request().url)) {
            throw IOException("Retired Stash network destination blocked")
        }
        return chain.proceed(chain.request())
    }
}

internal fun isLegacyStashDestination(url: HttpUrl): Boolean {
    val host = url.host.lowercase()
    val path = url.encodedPath.lowercase()
    return host == "stashfm.app" ||
        host.endsWith(".stashfm.app") ||
        host == "rawnaldclark.workers.dev" ||
        host.endsWith(".rawnaldclark.workers.dev") ||
        (host == "raw.githubusercontent.com" && path.startsWith("/rawnaldclark/stash/")) ||
        (host == "github.com" && (path == "/rawnaldclark/stash" || path.startsWith("/rawnaldclark/stash/"))) ||
        (host == "api.github.com" && path.startsWith("/repos/rawnaldclark/stash"))
}

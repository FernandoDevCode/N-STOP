package com.stash.feature.home.podcasts

import java.net.InetAddress
import java.net.URI
import java.util.Locale
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * One bounded, HTTPS-only policy for feed fetches, audio streams and downloads.
 * Validate EVERY redirect before following it; also reject private DNS answers
 * at connection time so public hostnames cannot point at LAN services.
 */
internal object PodcastHttp {
    private const val MAX_REDIRECTS = 5

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(java.time.Duration.ofSeconds(20))
            .readTimeout(java.time.Duration.ofSeconds(40))
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    check(validPodcastUrl("https://$hostname/")) { "Invalid podcast host" }
                    return Dns.SYSTEM.lookup(hostname).also { addresses ->
                        check(addresses.isNotEmpty() && addresses.all(::isPublicAddress)) {
                            "Podcast host resolves to a restricted address"
                        }
                    }
                }
            })
            .addInterceptor(Interceptor { chain ->
                var request = chain.request()
                repeat(MAX_REDIRECTS + 1) { redirects ->
                    check(validPodcastUrl(request.url.toString())) { "Unsafe podcast URL" }
                    val response = chain.proceed(request)
                    if (response.code !in listOf(301, 302, 303, 307, 308)) {
                        return@Interceptor response
                    }
                    val destination = response.header("Location")
                    if (destination == null || redirects == MAX_REDIRECTS) {
                        response.close()
                        error("Podcast redirect missing destination or exceeded limit")
                    }
                    val next = request.url.resolve(destination)
                    response.close()
                    check(next != null && validPodcastUrl(next.toString())) {
                        "Podcast redirect points to an unsafe URL"
                    }
                    request = request.newBuilder().url(next).build()
                }
                error("Podcast redirect limit exceeded")
            })
            .build()
    }

    private fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress ||
            address.isLinkLocalAddress || address.isSiteLocalAddress ||
            address.isMulticastAddress) return false
        val bytes = address.address
        if (bytes.size == 4) {
            val a = bytes[0].toInt() and 255
            val b = bytes[1].toInt() and 255
            if (a == 0 || a >= 224 || (a == 100 && b in 64..127) ||
                (a == 192 && b == 0) || (a == 198 && b in 18..19)) return false
        } else if (bytes.size == 16) {
            // Unique-local IPv6 fc00::/7 (java.net.isSiteLocalAddress omits this).
            if ((bytes[0].toInt() and 254) == 252) return false
        }
        return true
    }
}

/** Permit only public-hostname HTTPS resources with ordinary TLS port. */
internal fun validPodcastUrl(raw: String): Boolean {
    val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return false
    if (!uri.scheme.equals("https", ignoreCase = true) ||
        uri.host.isNullOrBlank() || uri.userInfo != null ||
        uri.port !in listOf(-1, 443)) return false
    val host = uri.host.lowercase(Locale.ROOT)
    if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") ||
        host.endsWith(".internal") || host.startsWith("127.") ||
        host.contains(':') || host.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) return false
    return true
}

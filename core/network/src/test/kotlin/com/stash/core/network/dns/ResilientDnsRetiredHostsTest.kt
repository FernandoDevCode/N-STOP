package com.stash.core.network.dns

import okhttp3.Dns
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException

class ResilientDnsRetiredHostsTest {
    @Test fun retiredHostsAreRejectedBeforeDelegateLookup() {
        var delegated = false
        val dns = ResilientDns(delegate = Dns {
            delegated = true
            listOf(InetAddress.getLoopbackAddress())
        }, maxAttempts = 1)
        listOf("last.fm", "api.last.fm", "lastfm.freetls.fastly.net",
            "lastfm-img.freetls.fastly.net").forEach { host ->
            try {
                dns.lookup(host)
                throw AssertionError("Retired host was resolved: " + host)
            } catch (expected: UnknownHostException) {
                assertTrue(expected.message!!.contains("Retired provider"))
            }
        }
        assertFalse("delegate resolver must never run", delegated)
    }
}

package org.holululu.proxythemall.services

import org.holululu.proxythemall.models.ProxyInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Tests the JSON payload that gets stored in PasswordSafe.
 *
 * PasswordSafe itself needs a running application, so only the serialization is covered here -
 * that is where a silent data loss (dropped credentials, lost SOCKS type) would occur.
 */
class ProxyCredentialsStorageTest {

    private val storage = ProxyCredentialsStorage()

    @Test
    fun `a full configuration survives a round trip`() {
        val original = ProxyInfo(
            host = "proxy.example.com",
            port = 8080,
            username = "user",
            password = "p@ss:word/with\\specials",
            type = "http",
            nonProxyHosts = setOf("localhost", "build.example.com")
        )

        assertEquals(original, storage.deserialize(storage.serialize(original)))
    }

    @Test
    fun `a configuration without credentials survives a round trip`() {
        val original = ProxyInfo(
            host = "proxy.example.com",
            port = 3128,
            nonProxyHosts = emptySet()
        )

        val restored = storage.deserialize(storage.serialize(original))

        assertEquals(original, restored)
        assertFalse(restored.hasCredentials)
    }

    @Test
    fun `the socks proxy type is preserved`() {
        val original = ProxyInfo(
            host = "socks.example.com",
            port = 1080,
            type = "socks5",
            nonProxyHosts = setOf("localhost")
        )

        val restored = storage.deserialize(storage.serialize(original))

        assertEquals("socks5", restored.type)
        assertEquals(true, restored.isSocks)
    }

    @Test
    fun `unknown fields in a stored payload are ignored`() {
        // Forward compatibility: a payload written by a newer version must still load
        val payload = """
            {"host":"proxy.example.com","port":8080,"username":null,"password":null,
             "type":"http","nonProxyHosts":["localhost"],"futureField":"value"}
        """.trimIndent()

        val restored = storage.deserialize(payload)

        assertEquals("proxy.example.com", restored.host)
        assertEquals(8080, restored.port)
        assertEquals(setOf("localhost"), restored.nonProxyHosts)
    }
}

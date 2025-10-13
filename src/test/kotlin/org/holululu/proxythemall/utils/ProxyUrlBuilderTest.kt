package org.holululu.proxythemall.utils

import org.holululu.proxythemall.models.ProxyInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for ProxyUrlBuilder
 */
class ProxyUrlBuilderTest {

    private lateinit var builder: ProxyUrlBuilder

    @BeforeEach
    fun setUp() {
        builder = ProxyUrlBuilder.instance
    }

    @Test
    fun testBuildProxyUrlWithoutCredentials() {
        // Given
        val host = "proxy.example.com"
        val port = 8080
        val type = "http"

        // When
        val result = builder.buildProxyUrl(host, port, null, null, type)

        // Then
        assertEquals("http://proxy.example.com:8080", result)
    }

    @Test
    fun testBuildProxyUrlWithSimpleCredentials() {
        // Given
        val host = "proxy.example.com"
        val port = 8080
        val type = "http"
        val username = "user"
        val password = "pass"

        // When
        val result = builder.buildProxyUrl(host, port, username, password, type)

        // Then
        assertEquals("http://user:pass@proxy.example.com:8080", result)
    }

    @Test
    fun testBuildProxyUrlWithSpecialCharactersInCredentials() {
        // Given
        val host = "proxy.example.com"
        val port = 8080
        val type = "http"
        val username = "user@domain"
        val password = "p@ss:word"

        // When
        val result = builder.buildProxyUrl(host, port, username, password, type)

        // Then
        assertEquals("http://user%40domain:p%40ss%3Aword@proxy.example.com:8080", result)
    }

    @Test
    fun testBuildProxyUrlWithSocks5Type() {
        // Given
        val host = "proxy.example.com"
        val port = 1080
        val type = "socks5"
        val username = "user"
        val password = "pass"

        // When
        val result = builder.buildProxyUrl(host, port, username, password, type)

        // Then
        assertEquals("socks5://user:pass@proxy.example.com:1080", result)
    }

    @Test
    fun testBuildProxyUrlWithProxyInfoObject() {
        // Given
        val proxyInfo = ProxyInfo(
            host = "proxy.example.com",
            port = 8080,
            type = "http",
            username = "user",
            password = "pass",
            nonProxyHosts = emptySet()
        )

        // When
        val result = builder.buildProxyUrl(proxyInfo)

        // Then
        assertEquals("http://user:pass@proxy.example.com:8080", result)
    }

    @Test
    fun testBuildProxyUrlWithProxyInfoObjectWithoutCredentials() {
        // Given
        val proxyInfo = ProxyInfo(
            host = "proxy.example.com",
            port = 8080,
            type = "http",
            username = null,
            password = null,
            nonProxyHosts = emptySet()
        )

        // When
        val result = builder.buildProxyUrl(proxyInfo)

        // Then
        assertEquals("http://proxy.example.com:8080", result)
    }

    @Test
    fun testBuildProxyUrlWithNullCredentials() {
        // Given
        val host = "proxy.example.com"
        val port = 8080
        val type = "http"

        // When
        val result = builder.buildProxyUrl(host, port, null, null, type)

        // Then
        assertEquals("http://proxy.example.com:8080", result)
    }

    @Test
    fun testBuildProxyUrlWithEmptyCredentials() {
        // Given
        val host = "proxy.example.com"
        val port = 8080
        val type = "http"
        val username = ""
        val password = ""

        // When
        val result = builder.buildProxyUrl(host, port, username, password, type)

        // Then
        assertEquals("http://proxy.example.com:8080", result)
    }

    @Test
    fun testBuildProxyUrlWithOnlyUsernameProvided() {
        // Given
        val host = "proxy.example.com"
        val port = 8080
        val type = "http"
        val username = "user"
        val password = null

        // When
        val result = builder.buildProxyUrl(host, port, username, password, type)

        // Then
        assertEquals("http://proxy.example.com:8080", result)
    }

    @Test
    fun testBuildProxyUrlWithOnlyPasswordProvided() {
        // Given
        val host = "proxy.example.com"
        val port = 8080
        val type = "http"
        val username = null
        val password = "pass"

        // When
        val result = builder.buildProxyUrl(host, port, username, password, type)

        // Then
        assertEquals("http://proxy.example.com:8080", result)
    }

    @Test
    fun testBuildProxyUrlWithPercentInCredentials() {
        // Given
        val host = "proxy.example.com"
        val port = 8080
        val type = "http"
        val username = "user%name"
        val password = "pass%word"

        // When
        val result = builder.buildProxyUrl(host, port, username, password, type)

        // Then
        assertEquals("http://user%25name:pass%25word@proxy.example.com:8080", result)
    }

    @Test
    fun testBuildProxyUrlWithSpacesInCredentials() {
        // Given
        val host = "proxy.example.com"
        val port = 8080
        val type = "http"
        val username = "user name"
        val password = "pass word"

        // When
        val result = builder.buildProxyUrl(host, port, username, password, type)

        // Then
        assertEquals("http://user+name:pass+word@proxy.example.com:8080", result)
    }

    @Test
    fun testBuildProxyUrlWithUnicodeInCredentials() {
        // Given
        val host = "proxy.example.com"
        val port = 8080
        val type = "http"
        val username = "usér"
        val password = "pássword"

        // When
        val result = builder.buildProxyUrl(host, port, username, password, type)

        // Then
        assertEquals("http://us%C3%A9r:p%C3%A1ssword@proxy.example.com:8080", result)
    }

    @Test
    fun testBuildProxyUrlWithComplexSpecialCharacters() {
        // Given
        val host = "proxy.example.com"
        val port = 8080
        val type = "http"
        val username = "user!@#$%^&*()"
        val password = "pass!@#$%^&*()"

        // When
        val result = builder.buildProxyUrl(host, port, username, password, type)

        // Then
        assertEquals(
            "http://user%21%40%23%24%25%5E%26*%28%29:pass%21%40%23%24%25%5E%26*%28%29@proxy.example.com:8080",
            result
        )
    }

    @Test
    fun testBuildProxyUrlWithSocks5TypeAndSpecialCharacters() {
        // Given
        val host = "proxy.example.com"
        val port = 1080
        val type = "socks5"
        val username = "user@domain"
        val password = "p@ss:word"

        // When
        val result = builder.buildProxyUrl(host, port, username, password, type)

        // Then
        assertEquals("socks5://user%40domain:p%40ss%3Aword@proxy.example.com:1080", result)
    }
}

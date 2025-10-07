package org.holululu.proxythemall.utils

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.holululu.proxythemall.models.ProxyInfo

/**
 * Test suite for ProxyUrlBuilder with URL encoding functionality
 */
class ProxyUrlBuilderTest : BasePlatformTestCase() {

    private val proxyUrlBuilder = ProxyUrlBuilder.instance

    fun testBuildProxyUrlWithoutCredentials() {
        val result = proxyUrlBuilder.buildProxyUrl(
            host = "proxy.example.com",
            port = 8080
        )
        assertEquals("http://proxy.example.com:8080", result)
    }

    fun testBuildProxyUrlWithSimpleCredentials() {
        val result = proxyUrlBuilder.buildProxyUrl(
            host = "proxy.example.com",
            port = 8080,
            username = "user",
            password = "pass"
        )
        assertEquals("http://user:pass@proxy.example.com:8080", result)
    }

    fun testBuildProxyUrlWithSpecialCharactersInCredentials() {
        val result = proxyUrlBuilder.buildProxyUrl(
            host = "proxy.example.com",
            port = 8080,
            username = "user@domain.com",
            password = "p@ss:w0rd"
        )
        // @ should be encoded as %40, : should be encoded as %3A
        assertEquals("http://user%40domain.com:p%40ss%3Aw0rd@proxy.example.com:8080", result)
    }

    fun testBuildProxyUrlWithPercentInCredentials() {
        val result = proxyUrlBuilder.buildProxyUrl(
            host = "proxy.example.com",
            port = 8080,
            username = "user%name",
            password = "pass%word"
        )
        // % should be encoded as %25
        assertEquals("http://user%25name:pass%25word@proxy.example.com:8080", result)
    }

    fun testBuildProxyUrlWithSpacesInCredentials() {
        val result = proxyUrlBuilder.buildProxyUrl(
            host = "proxy.example.com",
            port = 8080,
            username = "user name",
            password = "pass word"
        )
        // Space should be encoded as %20
        assertEquals("http://user+name:pass+word@proxy.example.com:8080", result)
    }

    fun testBuildProxyUrlWithUnicodeInCredentials() {
        val result = proxyUrlBuilder.buildProxyUrl(
            host = "proxy.example.com",
            port = 8080,
            username = "üser",
            password = "pässwörd"
        )
        // Unicode characters should be properly encoded
        assertEquals("http://%C3%BCser:p%C3%A4ssw%C3%B6rd@proxy.example.com:8080", result)
    }

    fun testBuildProxyUrlWithEmptyCredentials() {
        val result = proxyUrlBuilder.buildProxyUrl(
            host = "proxy.example.com",
            port = 8080,
            username = "",
            password = ""
        )
        // Empty credentials should be treated as no credentials
        assertEquals("http://proxy.example.com:8080", result)
    }

    fun testBuildProxyUrlWithNullCredentials() {
        val result = proxyUrlBuilder.buildProxyUrl(
            host = "proxy.example.com",
            port = 8080,
            username = null,
            password = null
        )
        assertEquals("http://proxy.example.com:8080", result)
    }

    fun testBuildProxyUrlWithSocks5Type() {
        val result = proxyUrlBuilder.buildProxyUrl(
            host = "proxy.example.com",
            port = 1080,
            username = "user",
            password = "pass",
            type = "socks5"
        )
        assertEquals("socks5://user:pass@proxy.example.com:1080", result)
    }

    fun testBuildProxyUrlWithSocks5TypeAndSpecialCharacters() {
        val result = proxyUrlBuilder.buildProxyUrl(
            host = "proxy.example.com",
            port = 1080,
            username = "user@domain",
            password = "p@ss:w0rd",
            type = "socks5"
        )
        assertEquals("socks5://user%40domain:p%40ss%3Aw0rd@proxy.example.com:1080", result)
    }

    fun testBuildProxyUrlWithProxyInfoObject() {
        val proxyInfo = ProxyInfo(
            host = "proxy.example.com",
            port = 8080,
            username = "user@domain.com",
            password = "p@ss:w0rd",
            nonProxyHosts = emptySet()
        )
        val result = proxyUrlBuilder.buildProxyUrl(proxyInfo)
        assertEquals("http://user%40domain.com:p%40ss%3Aw0rd@proxy.example.com:8080", result)
    }

    fun testBuildProxyUrlWithProxyInfoObjectWithoutCredentials() {
        val proxyInfo = ProxyInfo(
            host = "proxy.example.com",
            port = 8080,
            nonProxyHosts = emptySet()
        )
        val result = proxyUrlBuilder.buildProxyUrl(proxyInfo)
        assertEquals("http://proxy.example.com:8080", result)
    }

    fun testBuildProxyUrlWithComplexSpecialCharacters() {
        val result = proxyUrlBuilder.buildProxyUrl(
            host = "proxy.example.com",
            port = 8080,
            username = "user+name@domain.com",
            password = "p@ss#w0rd&more"
        )
        // Test various special characters that need encoding
        assertEquals("http://user%2Bname%40domain.com:p%40ss%23w0rd%26more@proxy.example.com:8080", result)
    }

    fun testBuildProxyUrlWithOnlyUsernameProvided() {
        val result = proxyUrlBuilder.buildProxyUrl(
            host = "proxy.example.com",
            port = 8080,
            username = "user",
            password = null
        )
        // Should not include credentials if password is missing
        assertEquals("http://proxy.example.com:8080", result)
    }

    fun testBuildProxyUrlWithOnlyPasswordProvided() {
        val result = proxyUrlBuilder.buildProxyUrl(
            host = "proxy.example.com",
            port = 8080,
            username = null,
            password = "pass"
        )
        // Should not include credentials if username is missing
        assertEquals("http://proxy.example.com:8080", result)
    }
}

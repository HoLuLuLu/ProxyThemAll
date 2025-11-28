package org.holululu.proxythemall.services

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.holululu.proxythemall.models.ProxyInfo

/**
 * Service responsible for securely storing and retrieving proxy configuration using PasswordSafe.
 *
 * This service stores ALL proxy data (including credentials) in IntelliJ's PasswordSafe,
 * which uses the OS-native secure storage (macOS Keychain, Windows Credential Manager, etc.).
 */
@Service
class ProxyCredentialsStorage {

    companion object {
        private val LOG = Logger.getInstance(ProxyCredentialsStorage::class.java)

        private const val SERVICE_NAME = "ProxyThemAll"
        private const val PROXY_BACKUP_KEY = "proxy.backup"

        @JvmStatic
        fun getInstance(): ProxyCredentialsStorage {
            return ApplicationManager.getApplication().getService(ProxyCredentialsStorage::class.java)
        }
    }

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    /**
     * Serializable data class for storing proxy configuration
     */
    @Serializable
    private data class StoredProxyConfig(
        val host: String,
        val port: Int,
        val username: String?,
        val password: String?,
        val type: String,
        val nonProxyHosts: List<String>
    )

    /**
     * Saves the complete proxy configuration to PasswordSafe
     */
    fun saveProxyConfiguration(proxyInfo: ProxyInfo) {
        try {
            LOG.info("Saving proxy configuration to PasswordSafe: host=${proxyInfo.host}, port=${proxyInfo.port}")

            val storedConfig = StoredProxyConfig(
                host = proxyInfo.host,
                port = proxyInfo.port,
                username = proxyInfo.username,
                password = proxyInfo.password,
                type = proxyInfo.type,
                nonProxyHosts = proxyInfo.nonProxyHosts.toList()
            )

            val jsonString = json.encodeToString(storedConfig)

            val credentialAttributes = createCredentialAttributes()
            val credentials = Credentials(PROXY_BACKUP_KEY, jsonString)

            PasswordSafe.instance.set(credentialAttributes, credentials)

            LOG.info("Proxy configuration saved successfully to PasswordSafe")
        } catch (e: Exception) {
            LOG.error("Failed to save proxy configuration to PasswordSafe", e)
        }
    }

    /**
     * Loads the proxy configuration from PasswordSafe
     *
     * @return ProxyInfo object if configuration exists, null otherwise
     */
    fun loadProxyConfiguration(): ProxyInfo? {
        try {
            LOG.debug("Loading proxy configuration from PasswordSafe")

            val credentialAttributes = createCredentialAttributes()
            val credentials = PasswordSafe.instance.get(credentialAttributes)

            if (credentials == null) {
                LOG.debug("No stored proxy configuration found in PasswordSafe")
                return null
            }

            val jsonString = credentials.getPasswordAsString()
            if (jsonString.isNullOrBlank()) {
                LOG.debug("Empty proxy configuration in PasswordSafe")
                return null
            }

            val storedConfig = json.decodeFromString<StoredProxyConfig>(jsonString)

            val proxyInfo = ProxyInfo(
                host = storedConfig.host,
                port = storedConfig.port,
                username = storedConfig.username,
                password = storedConfig.password,
                type = storedConfig.type,
                nonProxyHosts = storedConfig.nonProxyHosts.toSet()
            )

            LOG.info("Proxy configuration loaded successfully from PasswordSafe: host=${proxyInfo.host}, port=${proxyInfo.port}")
            return proxyInfo
        } catch (e: Exception) {
            LOG.error("Failed to load proxy configuration from PasswordSafe", e)
            return null
        }
    }

    /**
     * Checks if a stored proxy configuration exists in PasswordSafe
     *
     * @return true if configuration exists, false otherwise
     */
    fun hasStoredConfiguration(): Boolean {
        try {
            val credentialAttributes = createCredentialAttributes()
            val credentials = PasswordSafe.instance.get(credentialAttributes)
            val hasConfig = credentials != null && !credentials.getPasswordAsString().isNullOrBlank()
            LOG.debug("Checking for stored proxy configuration: $hasConfig")
            return hasConfig
        } catch (e: Exception) {
            LOG.error("Failed to check for stored proxy configuration", e)
            return false
        }
    }

    /**
     * Clears the stored proxy configuration from PasswordSafe
     */
    fun clearStoredConfiguration() {
        try {
            LOG.info("Clearing stored proxy configuration from PasswordSafe")
            val credentialAttributes = createCredentialAttributes()
            PasswordSafe.instance.set(credentialAttributes, null)
            LOG.info("Stored proxy configuration cleared successfully")
        } catch (e: Exception) {
            LOG.error("Failed to clear stored proxy configuration", e)
        }
    }

    /**
     * Creates credential attributes for PasswordSafe storage
     */
    private fun createCredentialAttributes(): CredentialAttributes {
        return CredentialAttributes(
            generateServiceName(SERVICE_NAME, PROXY_BACKUP_KEY)
        )
    }
}

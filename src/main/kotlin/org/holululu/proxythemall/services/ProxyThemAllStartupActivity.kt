package org.holululu.proxythemall.services

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Startup activity that initializes ProxyThemAll plugin components
 *
 * This activity runs once per opened project; the setup itself is application wide and
 * guarded against repeated execution by ProxyThemAllStartupService.
 */
class ProxyThemAllStartupActivity : ProjectActivity {

    companion object {
        private val LOG = Logger.getInstance(ProxyThemAllStartupActivity::class.java)
    }

    override suspend fun execute(project: Project) {
        try {
            LOG.info("Initializing ProxyThemAll plugin via startup activity")

            ProxyThemAllStartupService.getInstance().performInitialSetup()

            LOG.info("ProxyThemAll plugin initialized successfully via startup activity")
        } catch (e: Exception) {
            LOG.warn("Failed to initialize ProxyThemAll plugin via startup activity", e)
        }
    }
}

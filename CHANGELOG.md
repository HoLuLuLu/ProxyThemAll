<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# ProxyThemAll Changelog

## [Unreleased]

### Fixed

- **No more balloon on every IDE startup**
  - Startup reused the same notifying code path as a manual toggle, so every launch showed a proxy balloon — once per
    open project. Users who had never configured a proxy were told "Proxy Disabled — You are now not using any proxy" at
    every launch. Startup now reconciles silently; a real toggle still announces itself

- **SOCKS proxies now honour non-proxy hosts**
  - `systemProp.socksNonProxyHosts` is written for SOCKS proxies. Only `http.nonProxyHosts` was written before, which
    the JDK consults for the `http` scheme but not for the socket-level connection a SOCKS proxy uses — so a SOCKS
    user's exception list was silently ignored and builds were routed through the proxy even for excluded hosts. The
    plugin's built-in local bypasses were unaffected; what was lost was the user's own exception list

- **Proxy settings edits are applied without toggling the proxy**
  - Changing host, port, protocol or exceptions while the proxy stays enabled is now detected. Previously the periodic
    check only reacted to enabled/disabled transitions, so an `ENABLED → ENABLED` edit was ignored:
    neither the PasswordSafe backup nor the Git and Gradle configuration updated until the user toggled the proxy off
    and on again. Switching HTTP → SOCKS was affected in the same way
  - The detection compares the platform's `ProxyConfiguration`, so an unchanged proxy still causes no work on the 2 s
    tick

- **gradle.properties data loss**
  - Lines a user added *inside* the managed block are preserved instead of deleted. Removal now drops only the lines the
    plugin itself wrote, comparing against the same keys and comments it emits
  - When the file is open in an editor the plugin now reads and writes its document, so unsaved edits are neither missed
    nor overwritten. Previously the read used `File.readText()` while the write went through the VFS, making the outcome
    timing dependent
    - The managed section is no longer duplicated on every toggle when it starts on the file's first line
    - A user property directly above the section markers is no longer deleted during removal
    - An existing `org.gradle.jvmargs` is preserved instead of being overridden by a duplicate key, which previously
      discarded custom `-Xmx` settings
    - Values are escaped for `.properties` syntax, so passwords containing backslashes are no longer corrupted
    - Original line separators and trailing newline are preserved, avoiding whole-file diffs on CRLF checkouts
    - Files are written through the VFS with the file's own charset instead of raw UTF-8 bytes

- **Git configuration no longer clobbered**
    - Disabling the proxy no longer removes a global Git proxy that the plugin never set. `git config --unset`
      exits with code 5 for a missing key, which previously aborted the project-level removal and fell through to
      `--global`
    - Removal now uses `--unset-all` and treats "key does not exist" as success

- **Credentials no longer leak into idea.log**
    - The authenticated proxy URL (`http://user:password@host`) is no longer logged

- **Restored proxy password now persists**
    - Credentials are stored with `remember = true`; previously the restored password was memory-only and was lost again
      on the next restart, defeating the purpose of the backup

- **ProxyThemAll changelist**
    - Changes are filed into the changelist after `ChangeListManager` has processed them, instead of by swapping the
      user's active changelist around the write — a race that filed the change in the user's own list
    - The changelist is deleted once it is actually empty, checked after a VCS refresh rather than immediately
    - Leftover changes are moved to the default changelist instead of being reverted, which risked destroying concurrent
      user edits
  - Replaced the retry-with-delay loop with `ChangeListManager.invokeAfterUpdate`

- **Memory leaks and duplicated work**
    - The status bar widget and its project are no longer retained after the widget is removed
    - The proxy state listener is registered once per application instead of once per open project, so a single state
      change no longer triggers N git invocations and gradle.properties rewrites
    - The periodic state check is now tied to the application lifetime and stops on plugin unload
    - Removed a dead `MessageBusConnection` that subscribed to no topic and was never disposed
    - Startup no longer performs the same cleanup and reapplication twice

- **Notifications**
    - Errors and the "configuration required" prompt are no longer suppressed by the "show notifications" setting
    - Failures are reported as errors instead of an informational balloon titled "Proxy Disabled"
    - Fixed a race where the state change balloon could be lost or duplicated

- **Status bar widget visibility** now takes effect immediately; the restart prompt is gone

- **Toggling applies to all open projects immediately** instead of relying on the periodic check to catch up

- **SOCKS proxies** are written as `systemProp.socksProxyHost`/`Port` instead of being written as an HTTP proxy

- **Proxy exceptions no longer mutated**: the plugin's own bypass hosts are written to Git and Gradle but are no longer
  written back into your IDE exception list on backup and restore

- **Non-proxy hosts for Git** now omit glob patterns such as `127.*`, which `http.noproxy` cannot match

- **Credential URL encoding** follows RFC 3986, so a space in a password encodes as `%20` rather than `+`

- Changing only the notification setting no longer rewrites Git and Gradle configuration
- Expected environment failures (Git missing, key absent, locked keychain) log warnings instead of raising
  "IDE fatal error" reports
- A warning is logged when PasswordSafe is in memory-only mode, where the backup cannot survive a restart. It now also
  fires on the read paths, so the warning appears in the session that finds the backup missing rather than only in the
  one that wrote it

### Changed

- Removed the unused hard dependency on the Gradle plugin; Gradle support only reads and writes
  `gradle.properties` and needs no Gradle plugin API
- The Tools menu entry now shows the action it will perform (Enable/Disable/Configure Proxy)
- Replaced hand-rolled `ProxyConfiguration` implementations with the platform factories
- Dropped an undeclared `commons-lang3` usage in favour of the Kotlin standard library
- Removed the duplicate `ProxyThemAllSettings` service registration
- Removed ~10 placeholder tests that asserted `true`; added real coverage for the gradle.properties handling, the proxy
  toggle state machine, credential serialization, listener bookkeeping, and URL encoding
- Added a Kover coverage floor so coverage regressions fail the build

## [0.0.6] 2025-12-01

### Added

- **Proxy Settings Backup and Restore**
    - Secure backup of proxy settings using IntelliJ's PasswordSafe API
    - All proxy data (host, port, credentials, type, nonProxyHosts) stored in OS-native secure storage
    - Automatic backup on startup when proxy is configured
    - Automatic backup when proxy settings change
    - Automatic restore on startup if IntelliJ forgets proxy settings
    - User-triggered restore via notification when trying to activate proxy without configuration
    - New ProxyCredentialsStorage service for secure credential management
    - New ProxyRestoreService for restoring proxy settings to IntelliJ
    - Addresses IntelliJ's known issue of losing proxy settings after restart

- **Enhanced Notification Actions**
    - "Restore Last Known Proxy Settings" button added to configuration required notification
    - Restore action shown prominently as primary button when backup exists
    - Conditional display: restore action only shown when stored configuration exists
    - Improved action ordering for better user experience
    - Fallback to HTTP Proxy Settings dialog when no backup available

- **Manual Backup Management**
    - New "Clear Stored Proxy Configuration" button in Settings → Tools → ProxyThemAll
    - Allows users to manually delete backed-up proxy settings from secure storage
    - Includes confirmation dialog to prevent accidental deletion
    - Automatically resets lastKnownProxyEnabled flag when cleared

- **Gradle Global Fallback Setting**
    - New setting to control Gradle proxy behavior for non-Gradle projects
    - When enabled (default): applies proxy to global ~/.gradle/gradle.properties for non-Gradle projects
    - When disabled: skips Gradle proxy configuration entirely for non-Gradle projects
    - Configurable in Settings → Tools → ProxyThemAll

- **ProxyThemAll Changelist Management**
    - Gradle proxy file modifications now automatically go to dedicated "ProxyThemAll" changelist
    - Prevents accidental commit of proxy credentials and configuration
    - Changelist is automatically created with descriptive message
    - User's active changelist is preserved during proxy operations

### Changed

- **Smart Gradle Project Detection**
    - Gradle proxy configuration now only applies to actual Gradle projects
    - Detects Gradle projects by presence of build.gradle, build.gradle.kts, settings.gradle, or settings.gradle.kts
    - Non-Gradle projects use global fallback setting (if enabled) or skip Gradle configuration
    - Prevents unnecessary proxy configuration in non-Gradle projects

- **Enhanced VCS Integration**
    - File modifications now use WriteCommandAction + VirtualFile for proper VCS notification
    - Both proxy addition and removal properly tracked by version control
    - Improved changelist management with EDT-safe operations

### Fixed

- **Changelist Timing Issue on Project Open**
    - Fixed issue where gradle.properties changes would appear in default changelist instead of ProxyThemAll changelist
      when opening a new project
    - Added VCS readiness checks before performing changelist operations
    - Added retry mechanism with delay if VCS isn't ready immediately
    - Fallback to direct file modification if VCS remains unavailable
  - (Superseded in Unreleased: the readiness check was ineffective and has been replaced by
    `ChangeListManager.invokeAfterUpdate`)
    - Ensures consistent changelist behavior across project opens and IDE restarts

- **Automatic Changelist Cleanup**
    - ProxyThemAll changelist is automatically deleted when proxy is disabled (if empty)
    - Safety check prevents deletion if changelist contains other changes
    - Proper VCS notification ensures changelist state is correctly updated
    - Delayed cleanup allows VCS to process file changes before changelist removal

## [0.0.5] 2025-10-22

(includes v0.0.4)
### Added

- **Automatic Proxy Reset on Settings Changes**
    - Automatic cleanup and reapplication of proxy settings when plugin settings are modified
    - Cleanup and reapplication on IDE startup to ensure consistent proxy state
    - HttpProxySettingsChangeListener to monitor IntelliJ's built-in proxy settings changes
    - ProxyThemAllStartupService and ProxyThemAllStartupActivity for initialization

- **Multi-Project Support**
    - Proxy configuration changes now apply to all open projects simultaneously
  - Separated global cleanup (IDE proxy) from project-specific cleanup (Git, Gradle)
  - Enhanced error handling with per-project failure isolation

### Changed

- **ProxyController Enhancements**
    - Added `cleanupAndReapplyProxySettingsForAllProjects()` method for multi-project support
  - Added cleanup methods: `performGlobalCleanup()` and `performProjectSpecificCleanup()`
  - Enhanced logging for troubleshooting and monitoring

- **Settings Integration**
    - ProxyThemAllConfigurable now triggers cleanup when Git integration, Gradle integration, or notification settings
      change
    - Immediate effect of setting changes across all open projects
  - Automatic cleanup of proxy settings when individual features (Git/Gradle) are disabled in settings

### Fixed

- **Feature Disabling Issue**
    - Fixed issue where disabling Git or Gradle proxy support in settings would not remove existing proxy configurations
    - GitProxyService and GradleProxyService now automatically clean up proxy settings when their respective features
      are disabled
    - Users no longer need to disable the entire proxy to clean up individual feature settings

### Technical Details

- New HttpProxySettingsChangeListener for monitoring proxy settings changes
- New ProxyThemAllStartupService for handling initialization tasks
- New ProxyThemAllStartupActivity for startup registration
- Enhanced ProxyController with multi-project processing capabilities
- Improved service initialization and listener registration

## [0.0.3] - 2025-10-13

### Added

- **Gradle Integration**
    - Gradle proxy configuration support using JVM system properties and gradle.properties file
    - Automatic Gradle proxy synchronization with IDE proxy settings
    - GradleProxyService for managing Gradle-specific proxy configurations
    - GradleProxyConfigurer for handling gradle.properties file operations
    - Support for HTTP and HTTPS Gradle proxy configuration
    - Integration with existing proxy toggle functionality

- **Non-Proxy Hosts Support**
    - Added support for nonProxyHosts configuration in both Git and Gradle
    - Enhanced ProxyInfo model to include non-proxy hosts information
    - Automatic handling of hosts that should bypass proxy settings

### Changed

- **Service Layer Refactoring**
    - Enhanced ProxyController with improved service integration and multi-service support
    - Updated ProxyService with better error handling and validation
    - Refactored ProxyInfoExtractor for simplified credential handling
    - Improved Git proxy configuration with direct credential support and URL encoding
    - Removed GitCredentialHelper in favor of direct credential management
    - Enhanced ProxyUrlBuilder with better URL encoding and validation

- **Configuration Management**
    - Improved gradle.properties file handling for proxy configuration
    - Enhanced Git configuration with proper URL encoding for credentials
    - Better integration between different proxy service types

### Technical Details

- New GradleProxyConfigurer for gradle.properties file management
- Enhanced ProxyController to support multiple proxy service types
- Simplified Git proxy configuration without credential helper
- Added URL encoding for Git proxy credentials to handle special characters
- Extended test coverage for Gradle and Git functionality with comprehensive test suites
- Added TestUtils for better test infrastructure
- Improved notification messages and error handling
- Enhanced plugin dependencies with Gradle plugin support

## [0.0.2] - 2025-10-02

### Added

- **Git Integration**
    - Automatic Git proxy configuration synchronization
    - Git proxy settings management alongside IDE proxy settings
    - Support for HTTP and HTTPS Git proxy configuration
    - Seamless integration with existing proxy toggle functionality
    - Git-specific proxy configuration validation and error handling

- **Visual Enhancements**
    - Added custom plugin icon for better visual identification
    - Enhanced plugin branding and user experience

### Technical Details

- New GitProxyService for managing Git proxy configurations
- GitProxyConfigurer for handling Git config file operations
- Enhanced ProxyController to support Git proxy synchronization
- Updated ProxyInfo model to include Git proxy state
- Extended notification system for Git-related operations
- Additional test coverage for Git functionality

## [0.0.1] - 2025-09-30
### Added

- **Core Proxy Management**
    - One-click proxy toggle functionality via Tools menu action
    - Smart proxy state detection (enabled, disabled, not configured)
    - Automatic preservation of proxy configuration when toggling
    - Support for IntelliJ IDEA's built-in proxy configuration system

- **Status Bar Integration**
    - Interactive status bar widget showing current proxy state
    - Visual proxy state indicators with distinct icons for each state
    - Click-to-toggle functionality directly from status bar
    - Real-time status updates when proxy state changes
    - Contextual tooltips with current state and available actions

- **User Notifications**
    - Balloon notifications for proxy state changes
    - Configuration validation alerts
    - Helpful guidance when proxy settings need to be configured
    - User-configurable notification preferences

- **Settings & Configuration**
    - Dedicated settings panel in IDE preferences (Settings > Other > ProxyThemAll)
    - Option to enable/disable notifications
    - Option to show/hide status bar widget
    - Persistent settings storage across IDE sessions

- **State Management**
    - Robust proxy state change listener system
    - Automatic widget updates when proxy state changes
    - Thread-safe singleton pattern for core services
    - Proper resource cleanup and disposal

- **User Experience**
    - Seamless integration with IntelliJ IDEA interface
    - Lightweight and non-intrusive design
    - Error handling for edge cases and configuration issues
    - Support for all IntelliJ-based IDEs

- **Architecture & Code Quality**
    - Clean separation of concerns with dedicated packages
    - Comprehensive test coverage for core functionality
    - Type-safe data models and enums
    - Centralized notification message management
    - Modern Kotlin implementation following IntelliJ Platform best practices

### Technical Details

- Built on IntelliJ Platform Plugin SDK
- Kotlin-based implementation
- Uses IntelliJ's ProxySettings and ProxyConfiguration APIs
- Implements StatusBarWidget and NotificationGroup extensions
- Follows plugin development best practices with proper service registration

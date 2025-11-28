<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# ProxyThemAll Changelog

## [Unreleased]

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

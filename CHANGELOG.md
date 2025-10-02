<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# ProxyThemAll Changelog

## [0.0.3] - 2025-10-07

### Added

- **Gradle Integration**
    - Gradle proxy configuration support using JVM system properties
    - Automatic Gradle proxy synchronization with IDE proxy settings
    - GradleProxyService for managing Gradle-specific proxy configurations
    - GradleProxyConfigurer for handling gradle.properties file operations
    - Support for HTTP and HTTPS Gradle proxy configuration
    - Integration with existing proxy toggle functionality

### Changed

- **Service Layer Refactoring**
    - Enhanced ProxyController with improved service integration
    - Updated ProxyService with better error handling and validation
    - Refactored ProxyInfoExtractor for simplified credential handling
    - Improved Git proxy configuration with direct credential support
    - Removed GitCredentialHelper in favor of direct credential management

### Technical Details

- New GradleProxyConfigurer for gradle.properties file management
- Enhanced ProxyController to support multiple proxy service types
- Simplified Git proxy configuration without credential helper
- Extended test coverage for Gradle and Git functionality
- Removed phase implementation summary files (cleanup)

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

# ProxyThemAll
![Build](https://github.com/HoLuLuLu/ProxyThemAll/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/28611-proxythemall.svg)](https://plugins.jetbrains.com/plugin/28611-proxythemall)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/28611-proxythemall.svg)](https://plugins.jetbrains.com/plugin/28611-proxythemall)

![Plugin Icon](src/main/resources/META-INF/pluginIcon.svg)

## Description

<!-- Plugin description -->
__ProxyThemAll__ is an IntelliJ IDEA plugin that provides one-click proxy management with automatic synchronization
across development tools.

__Key Features:__

- __Secure Proxy Backup & Restore__: Automatically backs up your proxy settings using OS-native secure
  storage (macOS Keychain, Windows Credential Manager, etc.) and restores them when IntelliJ forgets them after restart.
  Includes both automatic restore on startup and one-click manual restore via notification.
- __One-click proxy toggle__: Enable or disable proxy settings instantly through the Tools menu or status bar widget
- __Git integration__: Automatically configures Git proxy settings (http.proxy, https.proxy) with support for
  authentication and non-proxy hosts
- __Gradle integration__: Configures Gradle proxy settings via JVM system properties and gradle.properties files with
  smart project detection (optional, disabled by default)
- __Multi-project support__: Applies proxy configuration changes to all open projects simultaneously
- __Intelligent cleanup__: Automatically removes proxy settings when disabled and reapplies them during IDE startup
- __Status bar widget__: Optional clickable widget showing current proxy state with visual indicators
- __Settings management__: Configurable options for notifications, status bar widget, Git integration, and Gradle
  support
- __Non-proxy hosts support__: Automatically handles hosts that should bypass proxy settings for both Git and Gradle
- __Authentication support__: Handles proxy credentials with proper URL encoding for Git configuration

__Configuration Options:__

- Enable/disable notifications for proxy state changes
- Show/hide status bar widget
- Enable/disable Git proxy synchronization
- Enable/disable Gradle proxy support (experimental)
- Configure Gradle global fallback behavior for non-Gradle projects

__Gradle-Specific Features:__

- __Smart project detection__: Automatically detects Gradle projects and only applies Gradle proxy to actual Gradle
  projects
- __Global fallback setting__: Configurable behavior for non-Gradle projects (apply to global ~
  /.gradle/gradle.properties
  or skip)
- __ProxyThemAll changelist__: Gradle configuration changes are automatically placed in a dedicated VCS changelist to
  prevent accidental commits of proxy credentials
- __Automatic cleanup__: ProxyThemAll changelist is automatically removed when proxy is disabled (if empty)

__Target Users:__

- Developers switching between office (corporate proxy) and home (direct connection) environments
- Teams working with corporate proxies who need frequent proxy toggling
- Developers wanting consistent proxy configuration across IntelliJ IDEA, Git, and Gradle
- Users managing multiple projects who need synchronized proxy settings

The plugin uses IntelliJ IDEA's existing proxy configuration and adds toggle functionality accessible through the Tools
menu and an optional status bar widget. Git integration is enabled by default, while Gradle integration is optional and
can be enabled in settings.
<!-- Plugin description end -->

## Project Structure

```text
src/main/kotlin/org/holululu/proxythemall/
├── actions/
│   └── ProxyThemAllAction.kt          # Main toggle action in Tools menu
├── core/
│   └── ProxyController.kt             # Central controller for proxy operations
├── listeners/
│   ├── HttpProxySettingsChangeListener.kt # Monitors IntelliJ's built-in proxy settings
│   ├── ProxyStateChangeListener.kt    # Handles proxy state change events
│   ├── ProxyStateChangeManager.kt     # Manages state change notifications
│   └── WidgetStateChangeListener.kt   # Updates widget when state changes
├── models/
│   ├── NotificationData.kt            # Data structure for notifications
│   ├── ProxyInfo.kt                   # Proxy information data model
│   └── ProxyState.kt                  # Proxy state enumeration
├── notifications/
│   └── NotificationService.kt         # User notification management
├── services/
│   ├── ProxyCredentialsStorage.kt     # Secure proxy backup using PasswordSafe API
│   ├── ProxyInfoExtractor.kt          # Extracts proxy information from IDE settings
│   ├── ProxyRestoreService.kt         # Restores proxy settings from backup
│   ├── ProxyService.kt                # Core proxy management logic
│   ├── ProxyThemAllStartupActivity.kt # Startup activity for plugin initialization
│   ├── ProxyThemAllStartupService.kt  # Handles plugin initialization, backup, and auto-restore
│   ├── git/
│   │   ├── GitProxyConfigurer.kt      # Git proxy configuration management
│   │   └── GitProxyService.kt         # Git-specific proxy operations
│   └── gradle/
│       ├── GradleProxyConfigurer.kt   # Gradle proxy config with VCS changelist management
│       └── GradleProxyService.kt      # Gradle project detection and proxy operations
├── settings/
│   ├── ProxyThemAllConfigurable.kt    # Settings UI configuration
│   └── ProxyThemAllSettings.kt        # Settings persistence (includes global fallback option)
├── utils/
│   ├── NotificationMessages.kt        # Notification message templates
│   └── ProxyUrlBuilder.kt             # Utility for building proxy URLs
└── widgets/
    ├── ProxyIcons.kt                  # Status bar icons
    ├── ProxyStatusBarWidget.kt        # Status bar widget implementation
    └── ProxyStatusBarWidgetFactory.kt # Widget factory for IDE integration
```

## Installation

- Using the IDE built-in plugin system:
  
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "ProxyThemAll"</kbd> >
  <kbd>Install</kbd>
  
- Using JetBrains Marketplace:

  Go to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/28611-proxythemall) and install it by clicking
  the <kbd>Install to ...</kbd> button in case your IDE is running.

  You can also download the [latest release](https://plugins.jetbrains.com/plugin/28611-proxythemall/versions) from
  JetBrains Marketplace and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

- Manually:

  Download the [latest release](https://github.com/HoLuLuLu/ProxyThemAll/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation

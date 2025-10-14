# ProxyThemAll
![Build](https://github.com/HoLuLuLu/ProxyThemAll/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/28611-proxythemall.svg)](https://plugins.jetbrains.com/plugin/28611-proxythemall)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/28611-proxythemall.svg)](https://plugins.jetbrains.com/plugin/28611-proxythemall)

![Plugin Icon](src/main/resources/META-INF/pluginIcon.svg)

## Description

<!-- Plugin description -->
__ProxyThemAll__ is an IntelliJ IDEA plugin that makes it easy to toggle proxy settings on and off with just one click,
including automatic Git and Gradle proxy synchronization with multi-project support and intelligent state management.

__Key Features:__

- __Quick proxy toggle__: Switch proxy on/off instantly from the Tools menu or status bar
- __Git integration__: Automatically synchronizes Git proxy settings with IDE proxy configuration, including URL
  encoding for credentials and non-proxy hosts support
- __Gradle integration (Experimental)__: Automatically configures Gradle proxy settings using JVM system properties and
  gradle.properties file
- __Multi-project support__: Proxy configuration changes apply to all open projects simultaneously
- __Non-proxy hosts support__: Automatically handles hosts that should bypass proxy settings for both Git and Gradle
- __Visual status indicator__: See your current proxy state at a glance in the status bar
- __Smart memory__: Remembers your proxy settings when you turn them off, so you can easily turn them back on
- __Helpful notifications__: Get notified when proxy state changes or when configuration is needed
- __Easy setup__: Works with your existing IDE proxy settings - no additional configuration required

__Perfect for developers who:__

- Work from different locations (office with corporate proxy vs. home without)
- Need to quickly test applications with and without proxy
- Want Git and Gradle operations to work seamlessly with their proxy setup across multiple projects
- Want to avoid manually configuring proxy settings in multiple places (IDE, Git, Gradle)
- Have complex proxy setups with non-proxy hosts or special characters in credentials
- Work with multiple projects simultaneously and need consistent proxy configuration
- Prefer visual indicators and one-click solutions

The plugin adds a simple toggle action to your Tools menu and an optional status bar widget, making proxy management
effortless and keeping you focused on your development work.
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
│   ├── ProxyInfoExtractor.kt          # Extracts proxy information from IDE settings
│   ├── ProxyService.kt                # Core proxy management logic
│   ├── ProxyThemAllStartupService.kt  # Handles plugin initialization and cleanup
│   ├── git/
│   │   ├── GitProxyConfigurer.kt      # Git proxy configuration management
│   │   └── GitProxyService.kt         # Git-specific proxy operations
│   └── gradle/
│       ├── GradleProxyConfigurer.kt   # Gradle proxy configuration management
│       └── GradleProxyService.kt      # Gradle-specific proxy operations
├── settings/
│   ├── ProxyThemAllConfigurable.kt    # Settings UI configuration
│   └── ProxyThemAllSettings.kt        # Settings persistence
├── utils/
│   ├── NotificationMessages.kt        # Notification message templates
│   └── ProxyUrlBuilder.kt             # Utility for building proxy URLs
└── widgets/
    ├── ProxyIcons.kt                  # Status bar icons
    ├── ProxyStatusBarWidget.kt        # Status bar widget implementation
    └── ProxyStatusBarWidgetFactory.kt # Widget factory for IDE integration
```

The project also includes comprehensive test coverage in `src/test/kotlin/` with:

- `TestUtils.kt` - Common test utilities and helper functions
- Test classes mirroring the main source structure for all core components
- Dedicated test suites for Git and Gradle proxy configuration functionality

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

# ProxyThemAll

![Build](https://github.com/HoLuLuLu/ProxyThemAll/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/28611-proxythemall.svg)](https://plugins.jetbrains.com/plugin/28611-proxythemall)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/28611-proxythemall.svg)](https://plugins.jetbrains.com/plugin/28611-proxythemall)

<iframe width="245px" height="48px" src="https://plugins.jetbrains.com/embeddable/install/28611"></iframe>

## Description

<!-- Plugin description -->
__ProxyThemAll__ is an IntelliJ IDEA plugin that provides a convenient way to toggle proxy settings on and off directly
from within the IDE.

__Key Features:__

- __One-click proxy toggle__: Adds a "ProxyThemAll" action to the Tools menu that allows you to quickly enable or
  disable proxy settings
- __Smart state detection__: Automatically detects whether proxy is currently enabled, disabled, or not configured
- __Configuration preservation__: When you disable the proxy, it remembers your previous proxy settings so you can
  easily re-enable them later
- __User notifications__: Shows helpful balloon notifications to inform you of the current proxy state and any actions
  taken
- __Configuration validation__: Alerts you if proxy settings need to be configured before the toggle functionality can
  work

__Use Case:__ This plugin is particularly useful for developers who frequently need to switch between using a corporate
proxy (when at work) and direct internet connection (when working from home or other locations), without having to
manually navigate through IDE settings each time.

The plugin integrates seamlessly into IntelliJ IDEA's interface and uses the IDE's built-in proxy configuration system,
making it a lightweight and reliable solution for proxy management.
<!-- Plugin description end -->

## Project Structure

```text
src/main/kotlin/org/holululu/proxythemall/
├── actions/
│   └── ProxyThemAllAction.kt          # Simplified entry point that delegates to ProxyController
├── core/
│   └── ProxyController.kt             # Orchestrates the proxy toggle workflow and user notifications
├── models/
│   ├── NotificationData.kt            # Type-safe data structure for notification information
│   └── ProxyState.kt                  # Enum defining the three possible proxy states
├── notifications/
│   └── NotificationService.kt         # Handles all user notifications with consistent formatting
├── services/
│   └── ProxyService.kt                # Manages all proxy-related operations (enable/disable/state detection)
└── utils/
    └── NotificationMessages.kt        # Centralized, reusable notification messages
```
## Installation

- Using the IDE built-in plugin system:
  
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "ProxyThemAll"</kbd> >
  <kbd>Install</kbd>
  
- Using JetBrains Marketplace:

  Go to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID) and install it by clicking the <kbd>Install to ...</kbd> button in case your IDE is running.

  You can also download the [latest release](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID/versions) from JetBrains Marketplace and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

- Manually:

  Download the [latest release](https://github.com/HoLuLuLu/ProxyThemAll/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation

# ProxyThemAll
![Build](https://github.com/HoLuLuLu/ProxyThemAll/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/28611-proxythemall.svg)](https://plugins.jetbrains.com/plugin/28611-proxythemall)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/28611-proxythemall.svg)](https://plugins.jetbrains.com/plugin/28611-proxythemall)

![Plugin Icon](src/main/resources/META-INF/pluginIcon.svg)

## Description

<!-- Plugin description -->
__ProxyThemAll__ turns your IDE's proxy into a one-click switch — and keeps Git and Gradle in sync with it. Built for
developers who move between a corporate proxy at the office and a direct connection at home.

__Toggle the proxy in one click__

- Tools menu entry that names what it will do: __Enable Proxy__, __Disable Proxy__, or __Configure Proxy__ when nothing
  is set up yet.
- Optional status bar widget with a distinct icon per state — click it to toggle, hover to see the current state and
  what a click will do. Hiding or showing it takes effect immediately, no restart.
- Disabling remembers your configuration, so enabling again needs no retyping.

__Your proxy survives an IDE restart__

IntelliJ is known to forget proxy settings after a restart. ProxyThemAll backs them up — host, port, type, credentials,
and exception list — in OS-native secure storage (macOS Keychain, Windows Credential Manager, Linux keyring) through the
IDE's own PasswordSafe API, restores them automatically on startup, and offers a one-click __Restore Last Known Proxy
Settings__ action if you try to enable a proxy the IDE has lost.

__Git stays in sync (on by default)__

Sets `http.proxy` — which Git uses for HTTPS too — plus `http.noproxy` for your exception list, with proxy credentials
correctly URL-encoded. Project-level where a project is open, global otherwise. Disabling the proxy removes what the
plugin set and leaves the rest of your Git config untouched.

__Gradle stays in sync (opt-in)__

Writes `systemProp.*` proxy properties into a clearly marked, managed section of `gradle.properties`
— HTTP, HTTPS, and SOCKS, with the matching non-proxy host lists. Your own properties in that file are preserved, an
existing `org.gradle.jvmargs` is never overridden, and line endings and file encoding stay as they were. Gradle projects
are detected automatically; for non-Gradle projects you choose whether to fall back to the global `gradle.properties` or
skip it. Edits are filed into a dedicated __ProxyThemAll__ changelist, removed again once it is empty.

__Note:__ if your proxy requires authentication, Gradle needs the username and password in __plain text__ in
`gradle.properties` — Gradle offers no encrypted alternative. The changelist reduces the risk of committing them but is
not a security boundary. Leave Gradle integration off if that is unacceptable; without credentials only host, port, and
exceptions are written.

__Works the way you already work__

- One toggle configures every open project at once.
- Changes you make in __Settings > HTTP Proxy__ are picked up automatically. The IntelliJ Platform publishes no event
  for proxy changes, so a lightweight periodic check catches them within about two seconds; changes made through the
  plugin apply instantly.
- Startup reconciles quietly — no notification unless you toggled something yourself.
- HTTP, HTTPS, and SOCKS proxies, with or without authentication.
- The proxy URL is never written to the log, because it embeds your credentials. No telemetry, no network calls of its
  own.

__Settings > Tools > ProxyThemAll__

Notifications on state change · status bar widget · Git synchronization (on) · Gradle synchronization (off) · global
`gradle.properties` fallback (off) · clear the stored proxy backup.

Works in all IntelliJ-based IDEs from 2024.3 onward. Uses the IDE's own proxy configuration — there is no separate proxy
setup to maintain.
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
│       ├── GradlePropertiesText.kt    # Pure gradle.properties section handling
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

---
type: component
status: current
tags: [threading, polling, lifecycle, platform-api]
verified: 2026-08-12
verified-against: efb35cc
---

# ProxyStateChangeManager

`src/main/kotlin/org/holululu/proxythemall/listeners/ProxyStateChangeManager.kt`

Polling engine and listener registry. The IntelliJ platform exposes no message bus topic for proxy
configuration changes, so changes made in Settings are found by polling; changes made through the
plugin notify directly (`ProxyStateChangeManager.kt:16-18`).

## The poll

| Property | Value | Cite |
|---|---|---|
| Interval constant | `STATE_CHECK_INTERVAL = 2L` seconds | `:29` |
| Executor | `AppExecutorUtil.getAppScheduledExecutorService()` | `:160` |
| Scheduling | `scheduleWithFixedDelay` | `:160` |
| Initial delay / delay | both `STATE_CHECK_INTERVAL`, `TimeUnit.SECONDS` | `:170-172` |

`scheduleWithFixedDelay` means a fixed **delay between the end of one run and the start of the
next**, not a fixed rate. A slow tick therefore pushes the following tick out rather than queueing a
backlog — relevant because `checkForStateChanges()` synchronously invokes every listener
(`:123`, `:140-153`), and listeners reapply Git and Gradle configuration.

The task only starts when the first listener registers (`:66-69`) and is cancelled when the last one
leaves (`:81-83`). `startPeriodicStateCheck()` guards on `stateCheckTask?.isCancelled != false`
(`:159`) so it cannot schedule twice.

## Change detection

`checkForStateChanges()` — `:94-124`. Two reads per tick: the state and the raw configuration
(`:98-99`). It notifies when **either** the enum changed **or** the configuration changed:

```
stateChanged         = lastKnownProxyState  != currentState          // :107
configurationChanged = lastKnownConfiguration != currentConfiguration // :108
if (!stateChanged && !configurationChanged) return                    // :109-111
```

The configuration comparison is what catches host/port/protocol/exception edits made while the proxy
stays `ENABLED` (`:88-93`). It relies on the platform configuration objects implementing `equals`
(see [[Platform API Constraints]] and `ProxyService.kt:57-60`).

The two reads are deliberately not atomic: an edit landing between them pairs a stale state with a
fresh configuration, which self-heals on the next tick — at worst one extra tick, never a lost
change (`:95-97`).

While `ENABLED`, each tick refreshes the restorable configuration via
`proxyService.rememberActiveConfiguration()` (`:103-105`), so toggling off/on works even if the
plugin never observed the original enable. See [[ProxyService]].

## CRITICAL — the loop guard

`lastKnownProxyState` and `lastKnownConfiguration` are written **before** `notifyListeners()`:

```
lastKnownProxyState = currentState        // :121
lastKnownConfiguration = currentConfiguration // :122
notifyListeners(currentState)            // :123
```

The ordering is load-bearing, not stylistic (`:119-120`). Listeners reapply the proxy configuration
and finish by calling `notifyStateChanged()` (`:130-135`), which itself re-reads and re-notifies. If
the remembered fields were still stale when the listeners ran, the next tick would see the same
"change" again and reapply — Git `config` writes and a `gradle.properties` rewrite every 2 seconds,
indefinitely. Updating first makes the notification a one-shot per real change.

`notifyStateChanged()` (`:130-135`) applies the same discipline: it sets both remembered fields
before notifying.

`notifyListeners()` (`:140-153`) snapshots the list under the lock (`:141`), rethrows
`ProcessCanceledException` (`:146-147`) and swallows-with-warning anything else (`:148-151`) so one
broken listener cannot block the rest.

## Registration

`addListener()` is idempotent by **identity** — `listeners.any { it === listener }` returns early
(`:55-71`). Duplicates would multiply the per-change work by the number of open projects
(`:52-53`). `removeListener()` uses `removeIf { it === listener }` (`:78`). Both bodies are
`synchronized(listeners)` (`:56`, `:77`).

Known registrants:

| Listener | Registered at |
|---|---|
| `HttpProxySettingsChangeListener` | `HttpProxySettingsChangeListener.kt:123` |
| `WidgetStateChangeListener` (per widget) | `ProxyStatusBarWidget.kt:64`, removed in `dispose()` at `:69` |

## Lifecycle

Implements `Disposable` (`:22`). `dispose()` clears the listener list and cancels the scheduled task,
under the same lock (`:188-193`), so nothing survives a plugin unload.

The singleton is tied to the application in `ProxyThemAllStartupService.performInitialSetup()`:
`Disposer.register(ApplicationManager.getApplication(), ProxyStateChangeManager.instance)` —
`ProxyThemAllStartupService.kt:52`. That call is guarded by an `AtomicBoolean` CAS
(`ProxyThemAllStartupService.kt:35`, `:43-46`) because the startup activity runs once per project
while this setup is application-wide. See [[Lifecycle and Leaks]].

`ProxyService` is resolved through a provider lambda (`:20-21`, `:34`) so the manager can be
constructed without a running application — the testability seam.

## See also

[[Change Detection]] · [[ProxyService]] · [[Lifecycle and Leaks]] · [[ProxyController]]

---
type: concept
status: current
tags: [change-detection, polling, platform-api, threading, equality]
verified: 2026-08-12
verified-against: efb35cc
---

# Change Detection

The central mechanism of the plugin. Everything else — Git config, `gradle.properties`, the
PasswordSafe backup, the status bar icon — is downstream of "did the IDE proxy change?".

The answer is produced by a **2-second poll**, not by an event.

## Why polling

The IntelliJ Platform 2024.3 exposes **no message-bus topic for proxy configuration changes**.

✅ verified against `ideaIC-2024.3.6-aarch64`:

- `javap com.intellij.util.net.ProxySettings` → the whole interface is three members:
  `getProxyConfiguration()`, `setProxyConfiguration(ProxyConfiguration)`, static `getInstance()`.
  No `Topic`, no listener parameter, no `addListener`.
- Scanning every class in `com.intellij.util.net` for a field or method of type
  `com.intellij.util.messages.Topic` returns nothing. (The single `Topic` hit in the package is
  `CertificateConfigurable.getHelpTopic()`, a UI help id.)

So there is nothing to subscribe to. Stated in the code at
`listeners/ProxyStateChangeManager.kt:16-17` and again at
`listeners/HttpProxySettingsChangeListener.kt:122`.

See [[Polling Over Events]] for the decision record and [[Platform API Constraints]] for the wider
set of platform facts.

## The poll

`listeners/ProxyStateChangeManager.kt:158-175`.

| Property | Value | Cite |
|---|---|---|
| Interval | `STATE_CHECK_INTERVAL = 2L` seconds | `:29` |
| Executor | `AppExecutorUtil.getAppScheduledExecutorService()` | `:160` |
| Scheduling | `scheduleWithFixedDelay`, initial delay = period = 2 s | `:160`, `:170-172` |
| Starts | when the **first** listener registers | `:66-69` |
| Stops | when the **last** listener leaves | `:81-83` |

Fixed *delay*, not fixed rate: `checkForStateChanges()` invokes every listener synchronously
(`:123`, `:140-153`) and those listeners shell out to `git` and rewrite `gradle.properties`, so a
slow tick pushes the next tick out instead of queueing a backlog.

## Two comparisons per tick, not one

`checkForStateChanges()` — `:94-124`:

```kotlin
val currentState = proxyService.getCurrentProxyState()          // :98
val currentConfiguration = proxyService.getCurrentConfiguration() // :99
...
val stateChanged = lastKnownProxyState != currentState              // :107
val configurationChanged = lastKnownConfiguration != currentConfiguration // :108
if (!stateChanged && !configurationChanged) return                  // :109-111
```

The **enum** comparison catches on/off. The **configuration** comparison catches everything else:
host, port, protocol, exception list — all of which collapse into `ProxyState.ENABLED`
(`models/ProxyState.kt:6-9`). Comparing only the enum was the [[Change Detection Enum Only]] defect.

The two reads are deliberately non-atomic (`:95-97`): an edit landing between them pairs a stale
state with a fresh configuration; the pair is stored, the next tick sees the mismatch and notifies.
At worst one extra tick, never a lost change.

## Why equality works at all

`configurationChanged` is a plain `!=` on a platform interface type. That only works because the
platform's *implementations* are Kotlin data classes.

✅ verified with `javap`:

```
final class ProxyConfiguration$StaticProxyConfigurationData
      implements ProxyConfiguration$StaticProxyConfiguration {
  ...
  public java.lang.String toString();
  public int hashCode();
  public boolean equals(java.lang.Object);
}

final class ProxyConfiguration$DirectProxyData implements ProxyConfiguration$DirectProxy {
  public static final ProxyConfiguration$DirectProxyData INSTANCE;   // object singleton
  public boolean equals(java.lang.Object);
}
```

`StaticProxyConfiguration` itself is a bare interface — four abstract getters, no `equals`:

```
public interface ProxyConfiguration$StaticProxyConfiguration extends ProxyConfiguration {
  getProtocol(); getHost(); getPort(); getExceptions();
}
```

**Consequence, and it is load-bearing:** a hand-rolled `object : StaticProxyConfiguration { ... }`
inherits `Object.equals` — identity. It would never compare equal to anything, including itself
across two reads if a fresh instance were produced. Every tick would look like a change and reapply
Git + Gradle every 2 seconds forever.

That is why the plugin only ever constructs configurations through the platform factories:

- `ProxyConfiguration.proxy(protocol, host, port, exceptions)` —
  `services/ProxyRestoreService.kt:143-145`, with the reason written at `:140-141`
- `ProxyConfiguration.direct` — `services/ProxyService.kt:165` (reason at `:163-164`),
  `core/ProxyController.kt:293`

Both are `static` members on the `ProxyConfiguration` interface (✅ `javap`), and `proxy(...)`
returns the data-class-backed `StaticProxyConfigurationData`.

The assumption is pinned by a test rather than a comment: `ProxyConfigurationEqualityTest` asserts
identical configurations compare equal, that a changed host/port/exception list compares unequal, and
that `direct` is a stable singleton.

## Ordering requirement — remember before notify

`:119-123`:

```kotlin
lastKnownProxyState = currentState          // :121
lastKnownConfiguration = currentConfiguration // :122
notifyListeners(currentState)               // :123
```

This order is a **loop guard**, not style. The comment at `:119-120` says so.

The cycle it prevents: `HttpProxySettingsChangeListener.onProxyStateChanged()` reapplies the
configuration (`HttpProxySettingsChangeListener.kt:60`), and `ProxyController` ends its toggle path
with `stateChangeManager.notifyStateChanged()` (`ProxyController.kt:82`, `:185`). If the remembered
fields were still stale while listeners ran, the next tick would see the same "change" again → a
`git config` write and a `gradle.properties` rewrite every 2 seconds, indefinitely, in every open
project.

`notifyStateChanged()` (`:130-135`) applies the same discipline: both fields are set before
notifying.

Both remembered fields are `@Volatile` (`:38-39`, `:42-43`) — written by the polling thread, read
elsewhere.

Pinned by `ProxyStateChangeManagerTest`:

- `an unchanged configuration never notifies again` — five idle ticks, zero notifications
- `a poll tick right after notifyStateChanged does not notify twice`

## Two latencies, and the user can tell them apart

| Trigger | Path | Latency |
|---|---|---|
| Change made in **Settings → HTTP Proxy** | poll observes it | up to ~2 s, average ~1 s |
| Change made **through the plugin** (widget click, action, restore) | direct `notifyStateChanged()` | immediate |

The plugin-initiated path does not wait for the poll: `ProxyController.toggleProxyTo` applies to all
projects and *then* notifies (`ProxyController.kt:81-82`), and the comment at `:80` states the
intent — "apply to all open projects directly rather than waiting for the polling listener".

So a user clicking the status bar widget sees Git and Gradle updated at once; a user editing the port
in Settings waits for the next tick. That asymmetry is visible behaviour, and it is the price of the
missing topic.

## While ENABLED, every tick refreshes the restorable configuration

`:103-105` calls `proxyService.rememberActiveConfiguration()` on every tick where the state is
`ENABLED`, so toggling off and back on works even if the plugin never observed the original enable.
That is a *write* on the poll path — the reason `getCurrentProxyState()` itself is kept a pure query
(`ProxyService.kt:37-41`), since it also runs on every status bar repaint.

## Downstream listeners

`notifyListeners()` (`:140-153`) snapshots under the lock (`:141`), rethrows
`ProcessCanceledException` (`:146-147`), and warns-and-continues on anything else (`:148-151`) so one
broken listener cannot block the rest.

| Listener | Does | Registered |
|---|---|---|
| `HttpProxySettingsChangeListener` | PasswordSafe backup + reapply to all projects | `HttpProxySettingsChangeListener.kt:123` |
| `WidgetStateChangeListener` (one per widget) | repaint the status bar icon on the EDT | `ProxyStatusBarWidget.kt:64`, removed at `:69` |

`HttpProxySettingsChangeListener` keeps its *own* state+configuration comparison
(`:45-54`), retitled at `:37-38` as what it actually is: an idempotency guard against a redundant
`notifyStateChanged()`. It is **not** the change detector — placing the detector there was the
unreachable first fix described in [[Change Detection Enum Only]].

## See also

[[ProxyStateChangeManager]] · [[ProxyService]] · [[Platform API Constraints]] ·
[[Change Detection Enum Only]] · [[Polling Over Events]] · [[Lifecycle and Leaks]]

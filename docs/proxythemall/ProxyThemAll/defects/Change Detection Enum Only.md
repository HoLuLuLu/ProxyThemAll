---
type: defect
status: fixed
severity: high
tags: [change-detection, polling, listeners, unreachable-code]
verified: 2026-08-12
verified-against: efb35cc
---

# Change Detection Enum Only

The poll only reacted to `ProxyState` *enum* transitions, so editing the proxy's host, port or protocol
while it stayed ENABLED changed nothing anywhere.

## Symptom

Proxy enabled and working. User edits it in **Settings > HTTP Proxy** — new port, different host, or
switches HTTP to SOCKS — and clicks OK.

Nothing happens. Git still points at the old proxy. `gradle.properties` still holds the old host. The
PasswordSafe backup still holds the old configuration, so a later restore restores the *stale* proxy.

Workaround the user eventually discovers: toggle the proxy off, then on. Which is exactly the shape of a
bug report that never gets filed, because the workaround is one click.

## Root cause

`ProxyState` has three values (`src/main/kotlin/org/holululu/proxythemall/models/ProxyState.kt:6-9`):
`ENABLED`, `DISABLED`, `NOT_CONFIGURED`. It is a coarse projection of `ProxyConfiguration` — host, port,
protocol and the exception list all collapse into `ENABLED`.

The poll in `233c282` compared only that projection
(`233c282:.../listeners/ProxyStateChangeManager.kt`, around `:70`): read state, compare to
`lastKnownProxyState`, notify if different. `ENABLED -> ENABLED` is not different, so no notification, so
no listener ran — not the backup, not Git, not Gradle.

The polling design itself is forced: the IntelliJ platform exposes no message bus topic for proxy
configuration changes (`ProxyStateChangeManager.kt:16-17`). Given a 2-second poll, the *only* thing that
can be observed is a value comparison — and the wrong value was being compared.

## Why it survived

**The first fix was written in the wrong class, and was therefore unreachable.**

At `75dd04d`, `HttpProxySettingsChangeListener` gained a full configuration comparison
(`75dd04d:.../listeners/HttpProxySettingsChangeListener.kt:40-54`):

```kotlin
private var lastProcessedConfiguration: ProxyConfiguration? = null
...
val configurationChanged = currentConfiguration != lastProcessedConfiguration
if (lastProcessedState == newState && !configurationChanged) { return }
```

That code is correct. It is also **downstream of the gate it was meant to open**. The call chain is:

```
poll tick -> ProxyStateChangeManager.checkForStateChanges()   <- the enum gate
          -> notifyListeners()
          -> HttpProxySettingsChangeListener.onProxyStateChanged()   <- the new comparison
```

`onProxyStateChanged` is only ever invoked *if the manager decided to notify*. With the enum gate still
in place upstream, a port edit never produced a notification, so the new comparison was never evaluated.
The fix was real, tested by reading, and dead.

Why that mistake is easy to make:

- The listener is *named* for the thing being fixed — `HttpProxySettingsChangeListener` — so "detect a
  settings change" reads like its job. The class that actually decides *whether anything changed* is
  called a `Manager`.
- Reviewing the diff in isolation, the code is unambiguously correct. Nothing in the diff shows the
  caller. Verifying it required walking *up* the call chain, which a diff review does not prompt.
- The listener-level comparison is not useless — it is a genuine idempotency guard against a redundant
  `notifyStateChanged()`. So it looks right, *is* partly right, and masks the fact that the real gate is
  elsewhere.

The general lesson for this codebase: a filter placed below a coarser filter is dead code. Fix the
coarsest gate first, then decide whether the finer ones are still needed.

## Fix

The comparison moved **into the manager**, above the notification:
`src/main/kotlin/org/holululu/proxythemall/listeners/ProxyStateChangeManager.kt:94-124`.

```kotlin
val currentState = proxyService.getCurrentProxyState()
val currentConfiguration = proxyService.getCurrentConfiguration()
...
val stateChanged = lastKnownProxyState != currentState
val configurationChanged = lastKnownConfiguration != currentConfiguration
if (!stateChanged && !configurationChanged) {
    return
}
```

Supporting pieces:

- `ProxyService.getCurrentConfiguration()` (`ProxyService.kt:61-66`) exposes the raw
  `ProxyConfiguration` for comparison. It relies on the platform's `equals` — which is why the plugin
  must use `ProxyConfiguration.proxy(...)` factories rather than hand-rolled implementations
  (`ProxyRestoreService.kt:140-145`); a hand-rolled instance has no `equals` and never compares equal.
  `ProxyConfigurationEqualityTest` pins that platform assumption.
- `lastKnownConfiguration` is `@Volatile` (`ProxyStateChangeManager.kt:42-43`) — written by the polling
  thread, read elsewhere.
- Both remembered values are written **before** notifying (`:119-122`). Listeners reapply the
  configuration and finish by calling `notifyStateChanged()`; leaving the remembered values stale would
  cause a reapply on every 2-second tick forever — Git shelled out and `gradle.properties` rewritten
  30 times a minute.
- The two reads are not atomic, and the comment at `:95-97` states the consequence honestly: a stale
  state can pair with a fresh configuration, which self-corrects on the next tick. At worst one extra
  tick, never a lost change.
- The listener-level guard was kept, retitled as what it actually is
  (`HttpProxySettingsChangeListener.kt:37-40`): "ProxyStateChangeManager decides when to notify; this is
  an idempotency guard".

## Regression guard

`ProxyStateChangeManagerTest` — these three exist specifically for this defect:

- `changing the port while enabled notifies once` — the core case. Establishes a baseline tick, clears,
  changes only the port, asserts exactly one notification and that the state is still `ENABLED` (so the
  test proves detection happened *without* an enum flip).
- `switching protocol from http to socks while enabled notifies` — HTTP to SOCKS at the same host and
  port.
- `an unchanged configuration never notifies again` — five idle ticks, zero notifications. Guards the
  opposite failure the fix could have introduced: continuous reapplication.
- `a poll tick right after notifyStateChanged does not notify twice` — pins the write-before-notify
  ordering.

Testable because the manager takes an injectable provider — `ProxyStateChangeManager { proxyService }`
(`ProxyStateChangeManager.kt:20-22`) over a `FakeProxySettings`, so the tests need no running
application.

Manual: `docs/VERIFICATION.md` §7.2 "Backup refreshes on edit" and §7.2b "An idle proxy causes no
repeated work".

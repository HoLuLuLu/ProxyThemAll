---
type: component
status: current
tags: [state-machine, platform-api, threading]
verified: 2026-08-12
verified-against: efb35cc
---

# ProxyService

`src/main/kotlin/org/holululu/proxythemall/services/ProxyService.kt`

The plugin's state machine over the platform's `ProxySettings` / `ProxyConfiguration` API. Everything
else (widget, controller, poller) reads state from here.

## States

Three states, `ProxyState.kt:6-16`:

| State | Meaning | Predicate |
|---|---|---|
| `ENABLED` | IDE holds a non-direct configuration | `isProxyEnabled()` — `ProxyService.kt:148-156` |
| `DISABLED` | IDE holds `DirectProxy`, but a configuration is known | `isProxyConfigured()` — `ProxyService.kt:200-215` |
| `NOT_CONFIGURED` | direct, and nothing known to restore | fallthrough — `ProxyService.kt:47` |

`ProxyState.isProxyActive` is the single helper callers use instead of comparing to `ENABLED`
themselves — `ProxyState.kt:14-15`. It is true only for `ENABLED`. Used by
[[ProxyController]] to decide what to apply (`ProxyController.kt:81`, `:202`).

## Query vs. mutation split

The important invariant of this class: **reading state never writes state.**

- `getCurrentProxyState()` is a pure query — `ProxyService.kt:42-53`. It is called on every status bar
  repaint, both from `getTooltipText()` (`ProxyStatusBarWidget.kt:37`) and `getIcon()`
  (`ProxyStatusBarWidget.kt:45`), so any mutation here would fire at repaint frequency. The doc
  comment states the constraint explicitly (`ProxyService.kt:38-41`). ✅ verified by reading both call
  sites.
- `rememberActiveConfiguration()` is the only mutating path for `lastProxyConfiguration` —
  `ProxyService.kt:71-81`. It stores the current configuration unless it is a `DirectProxy`
  (`ProxyService.kt:74-77`).

Callers of the mutating path, exhaustively (grep over `src/main/kotlin`):

| Caller | Condition |
|---|---|
| [[ProxyStateChangeManager]] poll | only when `currentState == ENABLED` — `ProxyStateChangeManager.kt:103-105` |
| `toggleProxy()` | on the `ENABLED → DISABLED` branch, before disabling — `ProxyService.kt:92-96` |

`getCurrentConfiguration()` (`ProxyService.kt:61-66`) exists for [[Change Detection]]: it exposes the
raw `ProxyConfiguration` so the poller can spot edits that leave the enum unchanged.

## Transitions

`toggleProxy()` — `ProxyService.kt:87-112`:

- `ENABLED` → remember, then `disableProxy()`, return `DISABLED` (`:92-96`)
- `DISABLED` → `enableProxy()` restores `lastProxyConfiguration`, return `ENABLED` (`:98-101`)
- `NOT_CONFIGURED` → no-op, logged (`:103-106`)
- any exception → returns `getCurrentProxyState()` rather than a guess (`:108-111`)

`forceEnableProxy()` (`ProxyService.kt:119-143`) is the non-toggling variant used by
`ProxyController.ensureProxyEnabled()` (`ProxyController.kt:277`).

## Platform API constraints

- Disabling uses the platform factory `ProxyConfiguration.direct` — `ProxyService.kt:165`. An
  anonymous `DirectProxy` implementation has no `equals`/`hashCode` and would never compare equal to
  the platform's own instances, which breaks both the `!is DirectProxy` checks and the
  configuration-equality comparison in [[Change Detection]]. The comment at
  `ProxyService.kt:163-164` records this. See [[Platform API Constraints]].
- `ProxySettings.setProxyConfiguration` is platform-experimental; the whole class carries
  `@Suppress("UnstableApiUsage")` (`ProxyService.kt:20`) because it is the only way to change the IDE
  proxy (`ProxyService.kt:14-15`).

## Testability

`ProxySettings` arrives by constructor injection, defaulting to the platform singleton —
`ProxyService.kt:21-23`. Tests pass a fake; production uses the lazy `instance`
(`ProxyService.kt:26-27`).

## Known limitation — DISABLED is not durable

`lastProxyConfiguration` is an in-memory `@Volatile` field (`ProxyService.kt:33-34`) with no
persistence. `isProxyConfigured()` returns true only if that field is populated or the IDE currently
holds a non-direct configuration (`ProxyService.kt:203-210`).

Consequence: a user who deliberately disables the proxy sees `DISABLED` for the rest of the session,
but after an IDE restart the field is null and the IDE configuration is direct, so the same setup
reports `NOT_CONFIGURED`. The widget then shows the not-configured icon
(`ProxyStatusBarWidget.kt:48`) and a click cannot toggle (`ProxyController.kt:55`). ⚠️ inferred from
the code paths, not executed.

Startup handling of that case lives in `ProxyThemAllStartupService.handleProxyBackupAndRestore()`,
which can auto-restore from PasswordSafe when `lastKnownProxyEnabled` was true
(`ProxyThemAllStartupService.kt:97-112`) — that path covers a previously *enabled* proxy, not a
deliberately disabled one.

## See also

[[ProxyStateChangeManager]] · [[Change Detection]] · [[Platform API Constraints]] ·
[[ProxyController]]

---
type: component
status: current
tags: [orchestration, threading, notifications, multi-project]
verified: 2026-08-13
verified-against: efb35cc
---

# ProxyController

`src/main/kotlin/org/holululu/proxythemall/core/ProxyController.kt`

Orchestrates a toggle: flips [[ProxyService]], applies the result to every open project's Git and
Gradle configuration, updates the widgets, and emits exactly one balloon. Entry point is
`handleProxyToggle(project)` (`:47-66`), called from the widget click consumer
(`ProxyStatusBarWidget.kt:54`).

## Toggle path

`handleProxyToggle` dispatches on current state (`:52-56`):

| Current | Action |
|---|---|
| `ENABLED` | `toggleProxyTo(DISABLED, project)` — `:53` |
| `DISABLED` | `toggleProxyTo(ENABLED, project)` — `:54` |
| `NOT_CONFIGURED` | `showConfigurationRequiredNotification(project)` — `:55` |

`toggleProxyTo` (`:73-84`) calls `proxyService.toggleProxy()` and bails with a warning if the result
is not the expected state (`:75-78`) — no partial application on a surprising outcome. Otherwise it
applies to all projects directly rather than waiting for the poll (`:80-81`) and then calls
`stateChangeManager.notifyStateChanged()` (`:82`), see [[ProxyStateChangeManager]].

`showConfigurationRequiredNotification` hops to a pooled thread first (`:93`) because PasswordSafe
access must not run on the EDT (`:92`); it offers a restore when a backup exists (`:95`, `:104`).

## The two-callback join

`configureProxyServices(project, isEnabled, showNotification)` — `:115-162`. Git and Gradle each
complete on their own background thread, so results are collected through atomics (`:112-114`):

```kotlin
val gitStatus    = AtomicReference("")   // :116
val gradleStatus = AtomicReference("")   // :117
val remaining    = AtomicInteger(2)      // :118

val onComplete = { if (remaining.decrementAndGet() == 0) { ...balloon... } }  // :120-139
```

The balloon fires **exactly once**, in the `== 0` branch (`:121`), assembled from whichever statuses
are non-empty (`:122-126`) and chosen by direction (`:129-133`). See [[Notifications]].

The counter always reaches zero because the exception paths call `onComplete()` too: Git at `:149`
after setting `"Git configuration failed"` (`:148`), Gradle at `:160` after `:159`. Combined with
[[GitProxyConfigurer]] invoking `onComplete` on every internal path
(`GitProxyConfigurer.kt:82`, `:86`, `:131`, `:134`, `:138`), a thrown service never strands the join
at 1 and suppresses the notification.

## applyToAllProjects

`:212-253`. Signature: `(targetEnabled, showNotifications, notificationProject = null)`.

Order of operations:

1. snapshot `ProjectManager.getInstance().openProjects.toList()` (`:220`)
2. if enabling → `ensureProxyEnabled()` (`:224`, defined `:271-283`, uses
   `proxyService.forceEnableProxy()` at `:277`)
3. if disabling → per-project cleanup first, then the IDE proxy itself (`:226-230`)
4. for **every** open project: `configureProxyServices(...)` + `updateStatusBarWidget(...)`
   (`:233-241`)

**The toggle applies to all open projects.** `notificationProject` does not narrow the set of projects
configured — it only decides which one shows the balloon:

```kotlin
val notify = showNotifications && (notificationProject == null || notificationProject == project)  // :237
configureProxyServices(project, targetEnabled, notify)   // :238
```

`:236` states the intent: only the project the user acted in shows the balloon. Every other open
project is still reconfigured, silently. See [[Multi-Project Behaviour]].

⚠️ Note the short-circuit: when `notificationProject` is `null`, `notify` is true for **every** project,
so the guard suppresses nothing. That is what made startup emit a balloon per project —
[[Startup Balloon]]. Callers with no acting project must pass `showNotifications = false`; naming no
project is broadcast, not silence.

Also note what is *not* gated by `notify`: `updateStatusBarWidget(project)` (`:239`) runs
unconditionally, and `configureProxyServices` itself always runs (`:238`) — `notify` reaches only the
balloon at `:128-135`. So going silent costs no widget refresh and no Git/Gradle work.

Each project's work is wrapped in `runForProject` (`:258-266`), which skips disposed projects (`:259`)
and isolates failures so one project cannot break the others (`:262-265`). A failure of the whole
loop produces a single error balloon attached to the first open project (`:244-252`).

## Cleanup

- `performGlobalCleanup()` (`:289-298`) sets `ProxySettings.getInstance().setProxyConfiguration(
  ProxyConfiguration.direct)` at `:293` — the platform factory, for the same `equals` reason as
  [[ProxyService]] (`ProxyService.kt:163-165`); the method carries `@Suppress("UnstableApiUsage")`
  (`:288`)
- `performProjectSpecificCleanup(project)` (`:303-321`) removes Git (`:307`) and Gradle (`:315`)
  settings, each in its own try/catch, logging status at debug only

## Public entry points

| Method | Notifications | Line |
|---|---|---|
| `handleProxyToggle(project)` | yes, one project | `:47` |
| `cleanupAndReapplyProxySettingsForAllProjects(targetEnabled)` | yes, all projects | `:183-186` |
| `cleanupAndReapplyProxySettingsForAllProjectsSilently(targetEnabled)` | no | `:194-196` |
| `cleanupAndReapplyProxySettings()` | yes; target from `isProxyActive` | `:201-203` |

The silent variant exists for callers that already own the balloon, or have none to show (`:190-193`).
Its production callers, all verified by grep:

| Caller | Why silent |
|---|---|
| `HttpProxySettingsChangeListener.kt:60` | the triggering operation already showed the balloon |
| `ProxyRestoreService.kt:78` | restore shows its own single *Proxy Restored* balloon (`:79`) |
| `ProxyThemAllStartupService.kt:140` | **startup announces nothing** — see [[Startup Balloon]] |

The noisy convenience overload `cleanupAndReapplyProxySettings()` (`:201-203`) derives the target from
`proxyService.getCurrentProxyState().isProxyActive` (`:202`). ✅ Its only remaining production caller is
the settings panel (`ProxyThemAllConfigurable.kt:148`, gated on `proxySettingsChanged` at `:138-140`).
**Startup used to call it** — that was the defect, and the citation `ProxyThemAllStartupService.kt:135`
that this page previously carried is superseded by `:140`, now the silent call.

`updateStatusBarWidget` (`:167-176`) hops to the EDT via `invokeLater` (`:171`) because callers include
the polling thread (`:170`), and re-checks `project.isDisposed` inside the lambda (`:172`).

## See also

[[Multi-Project Behaviour]] · [[Notifications]] · [[ProxyService]] · [[ProxyStateChangeManager]] ·
[[GitProxyConfigurer]] · [[Startup Balloon]]

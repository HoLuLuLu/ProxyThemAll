---
type: concept
status: current
tags: [multi-project, notifications, isolation, lifecycle]
verified: 2026-08-13
verified-against: efb35cc
---

# Multi-Project Behaviour

The IDE proxy is **application-level**; Git and Gradle configuration are **per-project**. So one click
in one window must fan out to every open project, while the feedback stays where the user clicked.

## The toggle applies to all open projects, immediately

`ProxyController.applyToAllProjects` — `core/ProxyController.kt:212-253`.

```kotlin
val openProjects = ProjectManager.getInstance().openProjects.toList()   // :220
```

Snapshotted once per invocation (`.toList()`), so a project opening mid-run does not mutate the loop.

Ordering differs by direction, and it matters:

**Enabling** (`:223-224`): `ensureProxyEnabled()` first — the IDE proxy must hold a real configuration
before any per-project writer reads it, because `GitProxyService` / `GradleProxyService` both read
`ProxySettings.getProxyConfiguration()` themselves (`GitProxyService.kt:49`,
`GradleProxyService.kt:44`).

**Disabling** (`:226-231`): per-project cleanup **first**, then `performGlobalCleanup()`. Reversed, the
IDE proxy would already be `direct` when the per-project cleanup ran and the writers would have nothing
to identify. The comment at `:226` states the intent.

Then one pass over all projects for service configuration and widget repaint (`:233-241`).

The toggle path does **not** wait for the poll: `toggleProxyTo` calls `applyToAllProjects` and then
`stateChangeManager.notifyStateChanged()` (`:81-82`), with the reason at `:80` — "apply to all open
projects directly rather than waiting for the polling listener". Contrast a change made in
Settings → HTTP Proxy, which is only seen on the next 2-second tick. See [[Change Detection]].

## One balloon, for the invoking project only

`applyToAllProjects` takes a `notificationProject` (`:215`) and computes, per project:

```kotlin
val notify = showNotifications && (notificationProject == null || notificationProject == project)  // :237
```

Comment at `:236`: *"Only the project the user acted in should show the balloon."*

| Entry point | `notificationProject` | Result |
|---|---|---|
| Widget click / Tools action | the acting project (`:81`, from `handleProxyToggle(project)`, `:47`) | exactly one balloon |
| `cleanupAndReapplyProxySettingsForAllProjects(target)` | `null` (default, `:215`) → `:184` | one balloon **per open project** |
| `...ForAllProjectsSilently(target)` | irrelevant, `showNotifications = false` (`:195`) | none |

**The `null` case is a trap, and it was a live defect.** Read `:237` carefully: with
`notificationProject == null` the first disjunct short-circuits, so `notify` is true for *every*
project. The guard only limits anything when a project was actually named. So the comment at `:236`
("only the project the user acted in") describes the *named* case; the `null` case is fan-out
broadcast, not suppression.

Startup used to land in exactly that case — `performStartupCleanup()` called the notifying wrapper, so
every launch emitted N balloons with N projects open. **Fixed**: startup now uses the silent variant
(`ProxyThemAllStartupService.kt:140` → `ProxyController.kt:194-196`). See [[Startup Balloon]] and
[[Notifications]].

Manual restore has always used the silent variant (`ProxyRestoreService.kt:78`) because it shows its
own single balloon (`:79`). With startup silent too, **exactly one production caller still reaches the
noisy `null` case**: the settings panel, when a proxy-relevant option changes
(`ProxyThemAllConfigurable.kt:148` → `ProxyController.kt:201-203` → `:183-186`). That one is arguably
correct — the user *did* just act, by clicking Apply — but it still fans out N balloons with N projects
open, because the panel is application-level and has no acting project to name.

⚠️ Not filed as a defect: ticking a Gradle/Git checkbox is a deliberate action with a visible result,
unlike a launch, and the panel already gates on `proxySettingsChanged` (`:138-140`) so it does not fire
on the cosmetic notification toggle. Worth revisiting if the N-balloon fan-out is ever reported.

## Per-project failure isolation — `runForProject`

`:258-266`:

```kotlin
private fun runForProject(project: Project, action: () -> Unit) {
    if (project.isDisposed) return
    try { action() }
    catch (e: Exception) { LOG.warn("Proxy operation failed for project ${project.name}", e) }
}
```

Every per-project step goes through it — cleanup (`:228`) and configuration (`:234`). One project with
a broken git executable, a read-only `gradle.properties`, or a disposed state cannot abort the fan-out
for the others. The project name is in the warning so the failing project is identifiable.

Isolation is *not* silent: the failure is logged as a warning. What it does not do is surface a balloon
per failing project — an outer failure in `applyToAllProjects` shows one error notification attached to
`openProjects.firstOrNull()` (`:244-252`), which is a deliberately arbitrary anchor.

## `project.isDisposed` guards

A project can close at any point during an asynchronous fan-out, and every one of these paths is
asynchronous. The guards:

| Location | Cite |
|---|---|
| `runForProject`, before the action | `ProxyController.kt:259` |
| Widget repaint, inside `invokeLater` | `ProxyController.kt:172` |
| Gradle write, inside `invokeLater` | `GradleProxyConfigurer.kt:251` |
| Changelist move, inside `invokeAfterUpdate` | `GradleProxyConfigurer.kt:316` |
| Changelist cleanup, inside `invokeAfterUpdate` | `GradleProxyConfigurer.kt:358` |
| Settings-panel widget refresh loop | `ProxyThemAllConfigurable.kt:164` |

The pattern is consistent: **re-check inside the callback, not only before scheduling it.** The
changelist callbacks are the clearest case — they wait for a VCS refresh of unbounded duration
(see [[Changelist Integration]]).

## What is shared and what is not

| Thing | Scope | Cite |
|---|---|---|
| IDE proxy configuration | application | `ProxySettings.getInstance()` |
| `ProxyStateChangeManager` + its poll | application singleton | `ProxyStateChangeManager.kt:26` |
| `HttpProxySettingsChangeListener` | application singleton | `HttpProxySettingsChangeListener.kt:25` |
| PasswordSafe backup | application | `ProxyCredentialsStorage.kt:22` (`@Service`) |
| Settings (`ProxyThemAllSettings`) | application | `ProxyThemAllSettings.kt:13-18` |
| Status bar widget | **one per project** | `ProxyStatusBarWidgetFactory.kt:23-25` |
| Git config, `gradle.properties`, ProxyThemAll changelist | **per project** | `GitProxyConfigurer.kt:61`, `GradleProxyConfigurer.kt:388-392` |

The application/project split is exactly what makes registration a hazard: the startup activity is a
`ProjectActivity` and runs once per project, while nearly everything it sets up is application-wide.
Registering the singleton listener once per project produced N git invocations and N
`gradle.properties` rewrites per change — [[Per Project Listener Registration]]. Guarded now by an
`AtomicBoolean` CAS (`ProxyThemAllStartupService.kt:43-46`) plus identity-idempotent
`addListener` (`ProxyStateChangeManager.kt:55-71`). See [[Lifecycle and Leaks]].

There is one shared *file* despite the per-project framing: several projects can write the same
`~/.gradle/gradle.properties`, which is why `GradleProxyConfigurer` holds a `gradleFileLock`
(`:40-41`).

## Guard

`ProxyStateChangeManagerTest.registering the same listener twice notifies it once` covers the
duplicate-registration half. The fan-out itself has no automated guard — `ProxyController.kt` is at
**0 of 116 lines covered** and needs multiple live projects. Manual only.

## See also

[[ProxyController]] · [[Change Detection]] · [[Notifications]] · [[Lifecycle and Leaks]] ·
[[Per Project Listener Registration]] · [[Changelist Integration]] · [[Startup Balloon]]

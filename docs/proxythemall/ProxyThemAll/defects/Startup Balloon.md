---
type: defect
status: fixed
severity: high
tags: [notifications, startup, multi-project, ux, framing]
verified: 2026-08-13
verified-against: efb35cc
---

# Startup Balloon

Every IDE launch produced a proxy balloon — one per open project — because startup reconciliation
reused the code path built for a deliberate user action.

> Fixed on `main` in `efb35cc`.

## Symptom

Launch the IDE. A balloon appears announcing the proxy state, and with two projects open, two
balloons. Nothing was toggled; the user only started the IDE.

The worst case is the user who has **never configured a proxy at all**: `NOT_CONFIGURED` yields
`isProxyActive == false` (`models/ProxyState.kt`), so the fan-out ran the disable path and told them

```
Proxy Disabled — You are now not using any proxy.
```

at every single launch. And there is no opting out by accident: `showNotifications` defaults to
`true` (`settings/ProxyThemAllSettings.kt:27`), so this is the out-of-the-box behaviour, not a
setting a user talked themselves into. See [[Notifications]].

## Root cause

`ProxyThemAllStartupService.performStartupCleanup()` called the **notifying** convenience method:

```
performStartupCleanup()                                    // ProxyThemAllStartupService.kt:131
  → ProxyController.cleanupAndReapplyProxySettings()        // ProxyController.kt:201-203
  → cleanupAndReapplyProxySettingsForAllProjects(active)    // ProxyController.kt:183-186
  → applyToAllProjects(target, showNotifications = true)     // :184  — notificationProject omitted
```

`applyToAllProjects` (`ProxyController.kt:212-253`) defaults `notificationProject` to `null`
(`:215`) and then decides per project:

```kotlin
val notify = showNotifications && (notificationProject == null || notificationProject == project)  // :237
```

With `notificationProject == null` the disjunct short-circuits **true for every project**, so the
guard whose comment reads *"Only the project the user acted in should show the balloon"* (`:236`)
degenerates into "every project shows the balloon". The one-balloon rule is not a rule about
notifications; it is a rule about *which project the balloon attaches to*, and it only fires when a
project was named. Startup names none, because at startup there is no acting project.

⚠️ Not executed — the whole path needs a live application (`ProxyController.kt` is at 0 % coverage,
see [[Test Suite]]). Verified by reading the call chain end to end.

## Why it survived

**Startup reused the user-action code path.** `cleanupAndReapplyProxySettings()` is a convenience
wrapper that reads "do the normal thing", and the normal thing includes announcing itself. Nothing
in the name says *noisy*.

The audit that preceded this checked **whether notifications worked** — the group id, the
suppression gate, the six message bodies, the atomic two-callback join. It never asked the inverse
question: *do balloons fire when nothing has happened?* A notification test suite built around "does
the balloon appear" is structurally blind to a spurious balloon.

### And the framing was wrong first

This was originally filed as *"startup auto-restore uses the noisy reapply variant"* — an
**inconsistency between two restore paths**, manual restore being silent while startup restore was
noisy. Both the [[Defect Register]] and [[Notifications]] carried it that way, marked "cosmetic,
minor".

That framing was wrong twice over, and the wrongness is the interesting part:

1. **Wrong scope.** `performStartupCleanup()` runs on *every* startup
   (`ProxyThemAllStartupService.kt:62`), unconditionally — it is not inside the restore branch. Only
   `handleProxyBackupAndRestore()` (`:73-122`) deals with restore. So startup was noisy whether or
   not a restore happened, which means the defect affected every user on every launch, not the
   narrow set who had a backup to restore.
2. **Wrong severity, as a consequence.** Framed as an inconsistency between two rare paths, it read
   as cosmetic. Framed as "every launch, every project, including users with no proxy", it is a
   high-severity UX defect.

The lesson: **an inconsistency framing hides scope.** "A and B disagree" invites you to compare A
and B and stop there; it does not prompt the question *how often does A even run?* When a defect is
described as a mismatch between two paths, re-derive the trigger condition of each path from the
code before accepting the severity. Compare [[Change Detection Enum Only]], where the mis-framing
was vertical (a fix below the gate) rather than lateral.

## Fix

`performStartupCleanup()` now calls the silent variant
(`ProxyThemAllStartupService.kt:140`):

```kotlin
val targetEnabled = ProxyService.instance.getCurrentProxyState().isProxyActive
ProxyController.instance.cleanupAndReapplyProxySettingsForAllProjectsSilently(targetEnabled)
```

The rationale is recorded at the function's KDoc (`:124-130`): *"Deliberately silent: startup only
reconciles the existing configuration, the user did not toggle anything, so there is nothing to
announce."*

**Nothing is lost by going silent**, and this is the part worth checking rather than assuming — the
silent variant differs from the noisy one in exactly two ways
(`ProxyController.kt:183-186` vs `:194-196`):

| Difference | Consequence |
|---|---|
| `showNotifications = false` | the balloon, which is the defect |
| no trailing `stateChangeManager.notifyStateChanged()` (`:185`) | re-derived on the next tick — the 2 s poll compares state *and* configuration and notifies on its own (`ProxyStateChangeManager.kt:29`, and [[Change Detection]]) |

The **widget update is unaffected**: `updateStatusBarWidget(project)` sits outside the `notify`
condition inside the same per-project block (`ProxyController.kt:238-239`), so the status bar still
shows the correct icon at startup. That is the behaviour the manual step asserts explicitly.

Git and Gradle configuration are likewise unaffected — `configureProxyServices(project,
targetEnabled, notify)` (`:238`) always runs; `notify` only gates the balloon at `:128-135`.

## Regression guard

**None automated.** The path needs a live application, an open project and a real startup sequence;
`ProxyController.kt` and `ProxyThemAllStartupService.kt` are both at 0 % line coverage
([[Test Suite]]).

Manual: `docs/VERIFICATION.md` **§2.5 "No balloon on IDE startup"**, three restart cases —
configured+enabled, configured+disabled, and never configured. The third is the one that used to be
worst. The step also re-asserts §2.4 afterwards, so a fix that silenced *all* balloons rather than
just the startup ones would still fail. See [[Manual Verification]].

## See also

[[Notifications]] · [[Multi-Project Behaviour]] · [[ProxyController]] · [[Defect Register]] ·
[[Change Detection]]

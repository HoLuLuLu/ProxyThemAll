---
type: concept
status: current
tags: [notifications, ux, settings]
verified: 2026-08-13
verified-against: efb35cc
---

# Notifications

One balloon group, one gate, six messages.

Group id: `ProxyThemAll.Notifications`, declared as
`<notificationGroup id="ProxyThemAll.Notifications" displayType="BALLOON"/>`
(`src/main/resources/META-INF/plugin.xml:14`) and referenced at
`notifications/NotificationService.kt:18`.

## The gate: INFORMATION is suppressible, WARNING and ERROR are not

`NotificationService.showNotification` — `:28-48`:

```kotlin
val isInformational = notificationData.type == NotificationType.INFORMATION   // :29
if (isInformational && !ProxyThemAllSettings.getInstance().showNotifications) {
    return                                                                    // :30-32
}
```

The rule is stated at `:23-26`: *"The 'show notifications' setting only suppresses informational state
change balloons. Warnings and errors are always shown - silently swallowing a failure would leave the
user with a broken proxy setup and no indication why."*

So the setting is not "be quiet"; it is "stop telling me things worked". `showNotifications` defaults to
`true` (`settings/ProxyThemAllSettings.kt:27`), exposed as *"Show notifications when proxy state
changes"* with the comment *"Display balloon notifications when proxy is enabled or disabled"*
(`ProxyThemAllConfigurable.kt:40-45`).

Actions attached to a `NotificationData` are added verbatim (`:43-45`), then `notification.notify(project)`
(`:47`). A `null` project makes it application-level.

## Every notification the plugin can emit

Titles are exact strings.

| Title | Type | Suppressible | Source |
|---|---|---|---|
| **Proxy Enabled** | INFORMATION | yes | `NotificationMessages.kt:17-24` |
| **Proxy Disabled** | INFORMATION | yes | `NotificationMessages.kt:26-33` |
| **Proxy Restored** | INFORMATION | yes | `ProxyRestoreService.kt:173-179` |
| **Proxy Configuration Required** | WARNING | **no** | `NotificationMessages.kt:45-74` |
| **ProxyThemAll Failed** | ERROR | **no** | `NotificationMessages.kt:39-43` |
| **Proxy Restore Failed** | ERROR | **no** | `ProxyRestoreService.kt:185-197` |

Bodies:

- *Proxy Enabled* — `"You are now using a proxy."` plus the tools status (`:19-23`)
- *Proxy Disabled* — `"You are now not using any proxy."` plus the tools status (`:28-32`)
- *Proxy Configuration Required* — `"You have to configure a proxy first."` (`:70`)
- *ProxyThemAll Failed* — the caller's reason verbatim (`:41`)
- *Proxy Restored* — `"Proxy settings have been restored and activated: <host>:<port>"` (`:176`)
- *Proxy Restore Failed* — `"Failed to restore proxy settings. <reason>"` (`:188`)

### The tools status suffix

`Proxy Enabled` / `Proxy Disabled` carry a combined Git + Gradle status assembled by
`ProxyController.configureProxyServices` (`core/ProxyController.kt:115-162`):

```kotlin
gitStatus.get().takeIf { it.isNotEmpty() }?.let { append(MESSAGE_SEPARATOR).append("Git: $it") }
gradleStatus.get().takeIf { it.isNotEmpty() }?.let { append(MESSAGE_SEPARATOR).append("Gradle: $it") }
// :123-125, MESSAGE_SEPARATOR = "; " at NotificationMessages.kt:15
```

Producing e.g. `You are now using a proxy.; Git: configured for project with authentication; Gradle: configured for project`.

The join is atomic across two background callbacks: `AtomicReference` per service plus an
`AtomicInteger(2)` countdown, and the balloon fires only when `decrementAndGet() == 0`
(`:116-121`). This is why every Git and Gradle path must call `onComplete` on **all** branches — a
missed callback means the balloon never appears. Status strings never contain credential values
(see [[Credential Handling]]).

## The config-required notification's conditional restore action

`NotificationMessages.proxyConfigurationRequired(project, hasStoredConfig)` — `:45-74`. Action order is
deliberate:

```kotlin
if (hasStoredConfig) {
    actions.add(NotificationAction.createSimple("Restore Last Known Proxy Settings") {
        ApplicationManager.getApplication().executeOnPooledThread {      // :54
            ProxyRestoreService.getInstance().restoreAndActivateProxy(project)
        }
    })
}
actions.add(NotificationAction.createSimple("Open HTTP Proxy Settings") { ... })   // :62-66
```

- **Restore is added first**, and only when a backup exists, so it is the primary/most visible action
  (`:48-49`)
- **Open HTTP Proxy Settings** is always present (`:61`), opening the platform's own dialog via
  `ShowSettingsUtil.showSettingsDialog(project, "HTTP Proxy")` (`:64`)

So the balloon has one action or two depending on whether PasswordSafe holds a backup. That flag is
computed **off the EDT before the balloon is built**, because reading PasswordSafe blocks:
`ProxyController.showConfigurationRequiredNotification` wraps the `hasStoredConfiguration()` call and
the `showNotification` call in `executeOnPooledThread` (`ProxyController.kt:93-106`, reason at `:92`),
defaulting to `false` if the read throws (`:96-99`).

The action body itself re-dispatches to a pooled thread (`:54`) because notification actions run on the
EDT — see [[Lifecycle and Leaks]].

`Proxy Restore Failed` carries a single `Open HTTP Proxy Settings` action
(`ProxyRestoreService.kt:190-195`).

## Who shows what

| Trigger | Notification | Cite |
|---|---|---|
| Toggle succeeds | Proxy Enabled / Disabled, **one project only** | `ProxyController.kt:128-135`, `:237` |
| Toggle attempted while NOT_CONFIGURED | Proxy Configuration Required | `ProxyController.kt:55` → `:89-107` |
| Toggle throws | ProxyThemAll Failed | `ProxyController.kt:59-64` |
| Fan-out throws | ProxyThemAll Failed, anchored to `openProjects.firstOrNull()` | `ProxyController.kt:246-252` |
| Manual restore succeeds | Proxy Restored (restore uses the **silent** reapply so there is no duplicate) | `ProxyRestoreService.kt:78-79` |
| Restore fails at any stage | Proxy Restore Failed | `ProxyRestoreService.kt:56`, `:67`, `:83-86`, `:93` |
| Settings change listener reapplies | **none** — silent variant | `HttpProxySettingsChangeListener.kt:59-60`, `ProxyController.kt:194-196` |
| **IDE startup** | **none** — silent variant | `ProxyThemAllStartupService.kt:140` → `ProxyController.kt:194-196` |

Two suppression mechanisms exist and should not be confused:

1. the **user setting** (`showNotifications`), which gates INFORMATION at
   `NotificationService.kt:30`
2. the **silent code path** (`cleanupAndReapplyProxySettingsForAllProjectsSilently`,
   `ProxyController.kt:194-196`), used where the caller already owns the balloon —
   `showNotifications = false` at `:195`, rationale at `:190-193`

## Startup is silent; only user actions announce

The governing rule: **a balloon reports something the user did.** Startup reconciles the configuration
that already existed — the user only launched the IDE — so it says nothing.

`performStartupCleanup()` calls `cleanupAndReapplyProxySettingsForAllProjectsSilently(isProxyActive)`
(`ProxyThemAllStartupService.kt:140`), with the rationale in its KDoc (`:124-130`). The status bar
widget still updates, because `updateStatusBarWidget(project)` sits outside the `notify` condition
(`ProxyController.kt:238-239`), and Git/Gradle configuration still runs (`:238`) — `notify` gates only
the balloon at `:128-135`.

This used to be the opposite. Startup called the **notifying** wrapper
(`ProxyController.kt:201-203` → `:183-186`) with `notificationProject = null`, and with a null project
the per-project guard at `:237` is true for *every* project — a balloon on every launch, once per open
project, including "Proxy Disabled" to users who had never configured a proxy. Now fixed:
[[Startup Balloon]].

**Superseded claim, kept per [[CLAUDE]]:** this page previously described the defect as a *cosmetic
inconsistency between startup restore and manual restore*. That framing was wrong — the noisy call sat
in `performStartupCleanup()`, which runs on every startup unconditionally
(`ProxyThemAllStartupService.kt:62`), not inside the restore branch. The mis-framing hid both the scope
(every user, every launch) and the severity. See [[Startup Balloon]] for the lesson.

See [[Multi-Project Behaviour]].

## Not a notification channel

Failures that are logged only, by design:

- per-project cleanup failures inside `performProjectSpecificCleanup` — `LOG.debug` / `LOG.warn`
  (`ProxyController.kt:303-321`)
- per-project failures caught by `runForProject` — `LOG.warn` (`:263-265`)
- changelist tidy-up failures — `LOG.warn` (`GradleProxyConfigurer.kt:335-337`, `:375-377`)
- PasswordSafe memory-only mode — `LOG.warn`, once per session (`ProxyCredentialsStorage.kt:178-185`),
  now reached from **every** storage path because the call moved into the shared
  `createCredentialAttributes()` (`:194-200`) — save, load, existence check and clear all route through
  it, so a session that only *reads* the backup warns too

The last is arguably a case for a balloon: it means the backup will not survive a restart. It is not
one. Noted, not filed.

## Guards

`NotificationService.kt` and `NotificationMessages.kt` are both at **0 % line coverage** (15 and 32
lines missed) — `NotificationGroupManager` needs a live application. Titles and the suppression rule are
verified by reading only. Manual coverage lives in `docs/VERIFICATION.md`.

## See also

[[ProxyController]] · [[Multi-Project Behaviour]] · [[Credential Handling]] ·
[[Lifecycle and Leaks]] · [[Defect Register]] · [[Startup Balloon]]

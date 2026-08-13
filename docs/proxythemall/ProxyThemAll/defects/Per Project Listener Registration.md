---
type: defect
status: fixed
severity: high
tags: [listeners, lifecycle, duplication, disposer, startup]
verified: 2026-08-12
verified-against: efb35cc
---

# Per Project Listener Registration

A per-project startup activity registered the same application-level singleton listener once per
project, into a list with no duplicate check. N open projects meant N× the work per state change — and
the poll could never be stopped.

## Symptom

With three projects open, one proxy toggle produced:

- three `git config` invocations per key, in parallel, against the same repository — serialized only by
  `GitProxyConfigurer`'s internal lock (`GitProxyConfigurer.kt:44`), so it manifested as latency rather
  than corruption;
- three read-modify-write cycles over the same `gradle.properties`;
- three PasswordSafe writes;
- three balloons, or one per project depending on the path.

Growing linearly with projects opened *in that session* — closing a project did not reduce it, because
nothing ever unregistered.

Second, quieter symptom: the 2-second polling task kept running after the plugin was unloaded or
disabled.

## Root cause

`HttpProxySettingsChangeListener.instance` is an application-level singleton
(`HttpProxySettingsChangeListener.kt:24-25`). Its `register()` was called from
`ProxyThemAllStartupActivity.execute(project)`, a `ProjectActivity` — which the platform runs **once per
opened project**
(`233c282:src/main/kotlin/.../services/ProxyThemAllStartupActivity.kt`):

```kotlin
override suspend fun execute(project: Project) {
    // Register the HTTP proxy settings change listener
    HttpProxySettingsChangeListener.instance.register()
    val startupService = ProxyThemAllStartupService.getInstance()
    startupService.performInitialSetup()
```

`register()` calls `ProxyStateChangeManager.addListener(this)`, and that was an unconditional append
(`233c282:.../listeners/ProxyStateChangeManager.kt:37-40`):

```kotlin
fun addListener(listener: ProxyStateChangeListener) {
    synchronized(listeners) {
        listeners.add(listener)
```

So the *same object* was appended once per project. `notifyListeners` iterates the list and calls each
element, with no identity check — the singleton got called N times, and it does the full
cleanup-and-reapply-for-all-projects each time. The work is therefore **N² in effect**: N notifications ×
N projects reconfigured.

**The unreachable-stop bug follows from the same list.** Removal used `listeners.remove(listener)`
(`233c282:ProxyStateChangeManager.kt:55`), which removes *one* occurrence. With N duplicates, N−1 remained,
so `if (listeners.isEmpty()) stopPeriodicStateCheck()` (`:58-59`) never became true. The polling task was
unstoppable, and the manager had no `dispose()` and no `Disposer` registration, so nothing tied its
lifetime to the application — it outlived plugin unload.

## Why it survived

- **`ProjectActivity` reads like "on startup".** The interface name says nothing about cardinality; the
  `project` parameter is the only clue, and it is easy to read as "here is a project, in case you need
  one" rather than "you will be called once for each of these". The registration line even sat under the
  comment "Register the HTTP proxy settings change listener" — singular, and wrong.
- **Singleton + collection is a mismatch nothing flags.** `addListener` is a textbook-shaped method. The
  fact that its only caller passes a process-wide singleton — making duplicates meaningless rather than
  merely wasteful — is only visible if you look at the call site *and* the field declaration together.
- **Correct output masked wrong behaviour.** Doing the same idempotent reconfiguration three times
  produces the same final state as doing it once. Functional testing cannot see this; only a log line
  count or a stopwatch can.
- **Almost everyone develops with one project open.** N=1 is indistinguishable from correct, for both the
  duplicate work and the unreachable stop (one add, one remove, list empties, poll stops). The bug needs
  two windows to exist at all.
- **The unreachable stop is a second-order consequence.** Even a reviewer who noticed the duplicate
  registration would likely file it as "wasteful"; connecting it to `remove()`'s single-occurrence
  semantics and then to a never-cancelled `ScheduledFuture` is a three-step chain.

## Fix

Three independent layers, deliberately: any one of them prevents the bug, and the redundancy is the
point, since the failure is silent.

**1. Application-level, once-only init.** The activity now does nothing but delegate
(`ProxyThemAllStartupActivity.kt:19-29`, documented at `:10-11`), and the service guards with an
`AtomicBoolean` (`ProxyThemAllStartupService.kt:34-46`):

```kotlin
// The startup activity runs once per project, but this setup is application wide
private val initialized = AtomicBoolean(false)

fun performInitialSetup() {
    if (!initialized.compareAndSet(false, true)) {
        LOG.debug("ProxyThemAll initial setup already performed, skipping")
        return
    }
```

`compareAndSet` rather than a plain flag: `ProjectActivity` is a coroutine and two projects can open
concurrently.

**2. Identity-idempotent `addListener`** (`ProxyStateChangeManager.kt:55-70`):

```kotlin
if (listeners.any { it === listener }) {
    LOG.debug("Listener already registered: ${listener::class.simpleName}")
    return
}
```

Reference identity (`===`), not `equals` — two distinct widget listeners must both register, while the
same singleton must not register twice. `removeListener` matches with `removeIf { it === listener }`
(`:78`) so add and remove use the same notion of sameness.

**3. Lifetime tied to the application** (`ProxyThemAllStartupService.kt:51-52`):

```kotlin
// Tie the polling task's lifetime to the application so it cannot outlive the plugin
Disposer.register(ApplicationManager.getApplication(), ProxyStateChangeManager.instance)
```

The manager implements `Disposable` (`ProxyStateChangeManager.kt:22`) and its `dispose()`
(`:188-193`) clears the list and cancels the task under the same lock, so unload cannot leave a running
poll.

## Regression guard

`ProxyStateChangeManagerTest`:

- `registering the same listener twice notifies it once` — the core case, with the assertion message
  naming the consequence ("a duplicate registration must not double the work").
- `a removed listener is no longer notified`
- `dispose drops all listeners` — covers layer 3.
- `a failing listener does not prevent the others from being notified` — a related hardening in
  `notifyListeners` (`ProxyStateChangeManager.kt:143-152`), which rethrows `ProcessCanceledException` and
  logs anything else.

⚠️ Not covered: that `performInitialSetup` is called once per *application* rather than per project.
That is a platform-lifecycle property with no unit test.

Manual: `docs/VERIFICATION.md` §8.1 "Both projects updated at once", §8.2 "No duplicated work per
project", §8.3 "No listener growth over time", §10.2 "Clean shutdown".

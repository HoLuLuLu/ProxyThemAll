---
type: concept
status: current
tags: [lifecycle, disposer, threading, edt, memory-leak, platform-api]
verified: 2026-08-12
verified-against: efb35cc
---

# Lifecycle and Leaks

What owns what, when it dies, and which thread it may run on. The plugin declares itself
dynamically loadable — the verifier prints *"Plugin can probably be enabled or disabled without IDE
restart"* on all three checked IDEs ([[Build Gates]]) — which makes every registration a potential
leak.

## Ownership: the manager is tied to the Application

`ProxyStateChangeManager` implements `Disposable` (`listeners/ProxyStateChangeManager.kt:22`) and is
registered against the application:

```kotlin
Disposer.register(ApplicationManager.getApplication(), ProxyStateChangeManager.instance)
// ProxyThemAllStartupService.kt:52, intent at :51
```

`dispose()` (`:188-193`) clears the listener list **and** cancels the scheduled task, both under
`synchronized(listeners)`. Without it the 2-second poll would outlive a plugin unload — a thread
holding a reference to unloaded classes.

Note the coupling: the poll's lifetime is *also* driven by listener count — started when the first
listener registers (`:66-69`), cancelled when the last leaves (`:81-83`). So there are two independent
stop paths, and `startPeriodicStateCheck` guards against double-scheduling with
`if (stateCheckTask?.isCancelled != false)` (`:159`).

## Listener registration is idempotent by identity

`addListener` — `:55-71`:

```kotlin
synchronized(listeners) {
    if (listeners.any { it === listener }) { LOG.debug(...); return }   // :57-60
    listeners.add(listener)
    if (listeners.size == 1) startPeriodicStateCheck()                 // :66-69
}
```

`===`, not `==`. `removeListener` matches with `removeIf { it === listener }` (`:78`).

Identity is the right relation here because the two listener types differ:
`HttpProxySettingsChangeListener` is an application singleton (`HttpProxySettingsChangeListener.kt:25`)
that must be registered exactly once, while `WidgetStateChangeListener` is one instance per widget per
project (`ProxyStatusBarWidget.kt:30`) and all of them are legitimately distinct. Structural equality
would be wrong for the second case; identity is correct for both.

Why it matters: duplicates multiply the work per state change by the number of registrations
(`:52-53`). That is not hypothetical — [[Per Project Listener Registration]] shipped it, producing N
git invocations and N `gradle.properties` rewrites per change.

`notifyListeners` snapshots the list under the lock before iterating (`:141`), so a listener that
registers or removes during notification cannot cause a `ConcurrentModificationException`.

## Two-level initialisation guard

The platform gives no usable `StartupManager` (all members are `@ApiStatus.Internal` or `@Deprecated` —
✅ verified in [[Platform API Constraints]] §2), so the plugin registers a `<postStartupActivity>`
(`plugin.xml:22`) implemented as a `ProjectActivity`
(`services/ProxyThemAllStartupActivity.kt:13`, `suspend fun execute(project)` at `:19`).

`ProjectActivity` runs **once per opened project**. The setup is **application-wide**. The bridge:

```kotlin
private val initialized = AtomicBoolean(false)                    // :35, intent at :34
fun performInitialSetup() {
    if (!initialized.compareAndSet(false, true)) { ...; return }   // :43-46
    ...
}
```

`ProxyThemAllStartupService.kt`. A CAS, not a `if (!flag) { flag = true }` — projects can open
concurrently.

So there are two independent defences against per-project duplication: this CAS, and the identity check
in `addListener`. Belt and braces, and the reason is that one of them once failed. See
[[Multi-Project Behaviour]].

Work done under the guard (`:48-64`): register the manager with the Disposer (`:52`), register the
settings listener (`:55`), backup/restore (`:58`), startup cleanup (`:62`).

## Why the widget factory must NOT override `disposeWidget`

`widgets/ProxyStatusBarWidgetFactory.kt:27-28`:

```kotlin
// disposeWidget is intentionally not overridden: the interface default calls
// Disposer.dispose(widget), which is what removes the widget's state change listener.
```

✅ verified with `javap -c com.intellij.openapi.wm.StatusBarWidgetFactory` — the default body is
literally `invokestatic Disposer.dispose(Disposable)` then `return`. **The default is the disposal.**

Overriding it with an empty body discards that call, so `ProxyStatusBarWidget.dispose()` never runs,
so `stateChangeManager.removeListener(stateChangeListener)` (`ProxyStatusBarWidget.kt:69`) never runs.
The listener stays registered against the application-level manager, holding the widget, which holds
its `Project` (via `EditorBasedWidget`) — forever, per hidden widget and per closed project. That was
[[Widget Project Leak]].

The comment is the guard. There is no test that fails if someone adds the override back — the failure
is a leak, not an assertion.

The rest of the widget lifecycle is symmetric: register in `install(statusBar)` (`:60-65`), remove in
`dispose()` before `super.dispose()` (`:67-72`).

Related, and a different method: `StatusBar.updateWidget()` only repaints an **existing** widget. Adding
or removing one when `isAvailable()` changes needs
`StatusBarWidgetsManager.updateWidget(ProxyStatusBarWidgetFactory::class.java)` —
`ProxyThemAllConfigurable.kt:169-170`, with the explanation at `:166-168` ("which is why hiding it used
to require an IDE restart").

## EDT rules

Two constraints pulling in opposite directions: Swing must be on the EDT, blocking work must not be.

### Off the EDT

| Work | Mechanism | Cite |
|---|---|---|
| PasswordSafe read before the config-required balloon | `executeOnPooledThread` | `ProxyController.kt:93-95`, reason `:92` |
| PasswordSafe write on enable + credential extraction | `executeOnPooledThread` | `HttpProxySettingsChangeListener.kt:86-101`, reason `:84-85` |
| Restore triggered from a notification action | `executeOnPooledThread` | `NotificationMessages.kt:53-56`, reason `:53` |
| Clear stored configuration from Settings | `executeOnPooledThread` | `ProxyThemAllConfigurable.kt:96`, reason `:95` |
| `git config` invocations | `Task.Backgroundable` | `GitProxyConfigurer.kt:56`, `:112`, reason `:55`, `:111` |
| Gradle file I/O, VFS refresh, file creation | `Task.Backgroundable` | `GradleProxyConfigurer.kt:65`, `:130`, `:288-299`, reason `:224` |
| Action `update()` — reads the proxy state | `ActionUpdateThread.BGT` | `ProxyThemAllAction.kt:21`, reason `:20` |
| The poll itself | `AppExecutorUtil.getAppScheduledExecutorService()` | `ProxyStateChangeManager.kt:160` |

Notification actions are the subtle one: they *run on the EDT*, so a handler that touches PasswordSafe
must re-dispatch — `NotificationMessages.kt:53-56` does exactly that.

### On the EDT

| Work | Mechanism | Cite |
|---|---|---|
| Status bar repaint | `invokeLater` + `isDisposed` | `ProxyController.kt:171-175`, reason `:170` |
| Widget repaint from the poll | `invokeLater` | `WidgetStateChangeListener.kt:14-16` |
| Document/VFS write command | `invokeLater` + `WriteCommandAction` | `GradleProxyConfigurer.kt:250`, `:262`, `:270` |
| Changelist move/cleanup | `invokeAfterUpdate(..., ModalityState.defaultModalityState())` | `GradleProxyConfigurer.kt:339-341`, `:379-381` |
| `Messages.showInfoMessage` / `showErrorDialog` after a pooled clear | `invokeLater` | `ProxyThemAllConfigurable.kt:105-117` |

`WidgetStateChangeListener` is the crossing point in miniature: it is invoked **from the polling
thread** and does nothing but `invokeLater { widget.updateWidget() }` (`:12-17`).

Modality: the Gradle paths consistently pass `ModalityState.defaultModalityState()`
(`GradleProxyConfigurer.kt:280`, `:341`, `:381`) so a background rewrite is not blocked behind a modal
dialog the user happens to have open.

## Volatile fields — the polling thread is a second writer

Every field the poll touches is `@Volatile`:

| Field | Cite |
|---|---|
| `ProxyStateChangeManager.lastKnownProxyState` | `:38-39` |
| `ProxyStateChangeManager.lastKnownConfiguration` | `:42-43` |
| `ProxyStateChangeManager.stateCheckTask` | `:46-47` |
| `ProxyService.lastProxyConfiguration` | `ProxyService.kt:33-34` |
| `HttpProxySettingsChangeListener.lastProcessedState` | `:34-35` |
| `HttpProxySettingsChangeListener.lastProcessedConfiguration` | `:39-40` |

Each has a comment naming the two threads (e.g. `ProxyService.kt:32` "read from the EDT and the polling
thread"). The write ordering of the first two is a correctness requirement, not just visibility — see
the loop guard in [[Change Detection]].

## Exception discipline

`ProcessCanceledException` is **rethrown**, never swallowed — `ProxyStateChangeManager.kt:146-147`
(listener dispatch) and `:164-165` (the poll body). Swallowing it breaks the platform's cancellation
contract.

Everything else is caught and logged at `warn` so one failure cannot cascade: per listener
(`:148-151`), per poll tick (`:166-168`), per project ([[Multi-Project Behaviour]]), per changelist
callback (`GradleProxyConfigurer.kt:335-337`, `:375-377`).

## Guards

`ProxyStateChangeManagerTest`:

- `registering the same listener twice notifies it once`
- `a removed listener is no longer notified`
- `a failing listener does not prevent the others from being notified`
- `dispose drops all listeners`

Testable because the manager takes a provider lambda for `ProxyService`
(`ProxyStateChangeManager.kt:20-22`), so it can be constructed without a running application. The
Disposer registration, the `AtomicBoolean` CAS, the widget lifecycle and every EDT rule above have
**no** automated guard — they need a live IDE. See [[Coverage Floor Not Target]].

## See also

[[ProxyStateChangeManager]] · [[Change Detection]] · [[Multi-Project Behaviour]] ·
[[Widget Project Leak]] · [[Per Project Listener Registration]] · [[Platform API Constraints]] ·
[[Credential Handling]]

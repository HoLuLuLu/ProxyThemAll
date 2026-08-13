---
type: defect
status: fixed
severity: high
tags: [memory-leak, disposer, platform-api, widget]
verified: 2026-08-12
verified-against: efb35cc
---

# Widget Project Leak

`disposeWidget` was overridden as an empty method, which discarded the interface default that performs
the disposal. The widget, its listener and its `Project` were retained forever.

## Symptom

Mostly invisible, which is the nature of a leak:

- Hide the status bar widget, or close the project. The widget's `ProxyStateChangeListener` stays
  registered with the app-level `ProxyStateChangeManager` and keeps firing on every proxy change.
- Each firing calls `myStatusBar?.updateWidget(...)` on a status bar belonging to a closed project.
- The `Project` instance itself is retained through the widget, so every open-close cycle leaks a whole
  project: its module graph, its VFS caches, its indices. On an IDE that gets used all day this is
  hundreds of MB.
- Downstream nuisance: exceptions from the stale listener show up in the IDE's fatal-error dialog with no
  action the user can take.

## Root cause

`233c282:src/main/kotlin/.../widgets/ProxyStatusBarWidgetFactory.kt`:

```kotlin
override fun disposeWidget(widget: StatusBarWidget) {
    // Disposal is handled by the widget itself
}
```

`StatusBarWidgetFactory.disposeWidget` is an interface method **with a default body**, and that body is
`Disposer.dispose(widget)`. It is the platform's only call into the widget's disposal — the widget is not
registered with any other `Disposable` parent.

Overriding it with an empty body means `Disposer.dispose(widget)` never runs, so
`ProxyStatusBarWidget.dispose()` never runs, so this never executes
(`src/main/kotlin/.../widgets/ProxyStatusBarWidget.kt:67-72`):

```kotlin
override fun dispose() {
    // Remove the state change listener from the manager
    stateChangeManager.removeListener(stateChangeListener)
    super.dispose()
}
```

The listener was added in `install()` (`ProxyStatusBarWidget.kt:60-65`) and never removed. Add without
remove, one per widget instance.

The comment is the whole defect in one sentence. "Disposal is handled by the widget itself" is true — the
widget *does* have a correct `dispose()` — and the override is precisely what prevents it from being
called. The author had the right model of the widget and the wrong model of who invokes it.

## Why it survived

- **An empty override reads as a decision, not an omission.** A reviewer sees a comment explaining why
  the method is empty and moves on. There is nothing to be suspicious of; the code *answers* the obvious
  question, just with a wrong answer.
- **Kotlin's `override` keyword gives no signal about defaults.** `override fun disposeWidget` looks
  identical whether the supertype member is abstract or has a body. Nothing at the override site hints
  that there was behaviour to lose. In Java the same mistake is equally invisible.
- **The two halves are in different files.** The correct `dispose()` is in `ProxyStatusBarWidget.kt`; the
  override that strips it is in `ProxyStatusBarWidgetFactory.kt`. Reading either file alone, both look
  right.
- **This is the class of bug the vault's own rule targets** — see the domain rule in `CLAUDE.md`:
  *never state a platform API behaves a certain way without checking the bytecode.* The correct
  disposal semantics are discoverable in seconds with `javap` against the resolved `ideaIC` jar, and were
  instead assumed. See [[Platform API Constraints]].
- **Leaks do not fail tests.** `ProxyStatusBarWidgetFactoryTest` asserts the widget ID and display name.
  Detecting this needs `LeakHunter` or a heap dump after closing a project, which no test does.

## Fix

Delete the override. `src/main/kotlin/.../widgets/ProxyStatusBarWidgetFactory.kt:27-28` now carries a
comment recording *why there is no override*, so it does not get "helpfully" reinstated:

```kotlin
// disposeWidget is intentionally not overridden: the interface default calls
// Disposer.dispose(widget), which is what removes the widget's state change listener.
```

The fix is a deletion. The comment is the durable part — a missing override is invisible in a diff, so
without it the next reader has no way to know the absence is deliberate.

## Regression guard

**None automated.** No test asserts disposal, and `ProxyStatusBarWidgetFactoryTest` covers only ID and
display name.

Manual: `docs/VERIFICATION.md` §3.4 "Hide/show without restart" (exercises the widget lifecycle),
§8.3 "No listener growth over time", and §9.3 / §10.2 (no IDE fatal-error reports; clean shutdown).

⚠️ A `LeakHunter`-based test on project close would be the real guard. Not present.

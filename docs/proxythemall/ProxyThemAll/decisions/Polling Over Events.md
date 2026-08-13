---
type: decision
status: current
tags: [change-detection, polling, platform-api, threading]
verified: 2026-08-12
verified-against: efb35cc
---

# Polling Over Events

**Detect IDE proxy changes with a 2-second poll instead of a platform event.**

## Context

The plugin's whole job is to mirror the IDE proxy into Git and Gradle. That requires knowing when the
IDE proxy changed — including changes the user makes directly in **Settings → HTTP Proxy**, which the
plugin never sees.

The IntelliJ Platform normally exposes such things as a message-bus `Topic`. Here it does not.

✅ verified against `ideaIC-2024.3.6-aarch64`:

- `javap com.intellij.util.net.ProxySettings` — the entire interface is `getProxyConfiguration()`,
  `setProxyConfiguration(ProxyConfiguration)` and static `getInstance()`. No listener, no topic, no
  `addChangeListener`.
- No class in `com.intellij.util.net` declares a field or method of type
  `com.intellij.util.messages.Topic`. (The one `Topic` hit in the package is
  `CertificateConfigurable.getHelpTopic()` — a UI help id.)

So there is nothing to subscribe to. Recorded in code at `ProxyStateChangeManager.kt:16-17` and
`HttpProxySettingsChangeListener.kt:122`.

## Decision

Poll `ProxySettings.getProxyConfiguration()` every **2 seconds** on the application scheduled executor,
and compare **both** the derived `ProxyState` enum and the raw `ProxyConfiguration`
(`ProxyStateChangeManager.kt:94-124`, interval constant at `:29`, scheduling at `:158-175`).

Changes the plugin makes itself bypass the poll and notify directly
(`ProxyController.kt:82`, `:185`; `ProxyStateChangeManager.notifyStateChanged()` at `:130-135`).

Full mechanism in [[Change Detection]].

## Consequences

**Accepted:**

- **~2 s user-visible latency** for changes made in Settings; average ~1 s, worst case 2 s plus the
  duration of the previous tick's listener work. Plugin-initiated changes are immediate, so the user
  experiences two different latencies depending on where they clicked.
- **A permanently scheduled task** while any listener is registered. Mitigated: it only starts with the
  first listener (`:66-69`), stops with the last (`:81-83`), and is `Disposable`-registered against the
  application (`ProxyThemAllStartupService.kt:52`) so it cannot outlive a plugin unload.
  See [[Lifecycle and Leaks]].
- **Correctness now depends on `equals`** of a platform type. Comparing configurations only works
  because `StaticProxyConfigurationData` and `DirectProxyData` are Kotlin data classes / objects with
  real `equals`/`hashCode` (✅ `javap`). A hand-rolled `object : StaticProxyConfiguration` would make
  every tick look like a change and reapply Git + Gradle every 2 seconds. Hence the hard rule: only
  construct configurations via `ProxyConfiguration.proxy(...)` / `.direct`
  (`ProxyRestoreService.kt:143-145`, `ProxyService.kt:165`), and the assumption is pinned by
  `ProxyConfigurationEqualityTest` rather than a comment.
- **Ordering becomes load-bearing.** The remembered values must be written *before* notifying
  (`:119-123`), or listeners' own `notifyStateChanged()` calls turn the poll into an infinite reapply
  loop.
- **Two non-atomic reads per tick** (`:98-99`): a stale state can pair with a fresh configuration. Self
  corrects on the next tick — at worst one extra tick, never a lost change (`:95-97`).

**Bonus, not planned:** the poll is trivially testable. `ProxyStateChangeManager` takes a provider
lambda for `ProxyService` (`:20-22`) and `checkForStateChanges()` is public, so a test drives ticks
synchronously over a `FakeProxySettings` with no running application — 8 tests in
`ProxyStateChangeManagerTest`. An event-based design would have needed a live message bus.

## Alternatives rejected

**A platform message-bus topic.** Not rejected — **not available**. This is the only reason the page
exists.

**`HttpConfigurable` / the legacy settings component.** `com.intellij.util.net.HttpConfigurable` still
exists in the platform jars and, being a `PersistentStateComponent`, could plausibly be observed. It is
the deprecated pre-2023 API that `ProxySettings` replaced; building change detection on it would mean
depending on a legacy component's persistence lifecycle to learn about the modern one. Rejected as
strictly more fragile than reading the current API on a timer.

**A `Configurable`/settings-dialog hook.** Would only catch changes made through *that* dialog. Misses
programmatic changes by other plugins, by the platform's own auto-detect, and by anything else calling
`setProxyConfiguration`. The poll catches all of them because it observes the state, not the actor.

**A shorter interval (e.g. 500 ms).** Rejected: each tick synchronously invokes every listener
(`:123`, `:140-153`), and those listeners shell out to `git` and rewrite `gradle.properties`. 2 s is
already fast enough to feel immediate for a settings edit while keeping the idle cost near zero — the
comparison short-circuits at `:109-111` when nothing changed.

**A longer interval (e.g. 30 s).** Rejected: a user who edits the proxy and immediately runs a Gradle
build would build against the old proxy with no indication why.

**File-watching `ide.general.xml` / the proxy settings storage.** Rejected: couples the plugin to an
undocumented on-disk format and still misses in-memory changes before the platform flushes.

## See also

[[Change Detection]] · [[Change Detection Enum Only]] · [[Platform API Constraints]] ·
[[ProxyStateChangeManager]] · [[Lifecycle and Leaks]]

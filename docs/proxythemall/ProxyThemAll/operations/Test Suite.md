---
type: operation
status: current
tags: [testing, junit, coverage]
verified: 2026-08-13
verified-against: efb35cc
---

# Test Suite

**68 tests, 10 files, 0 failures, 0 errors, 0 skipped.** ✅ Counted two independent ways: `grep -c
@Test` per source file, and the `tests`/`failures` attributes of the 10
`build/test-results/test/TEST-*.xml` files. Both agree, per class and in total.

Was 65 — the three new tests all landed in `GradlePropertiesTextTest` with
[[SOCKS Bypass Hosts Ignored]]; no new test file, and no new test class.

## Per class

| Test class | Tests | Subject |
|---|---|---|
| `utils/ProxyUrlBuilderTest` | 15 | URL assembly + RFC 3986 encoding |
| `services/gradle/GradlePropertiesTextTest` | **16** | [[GradlePropertiesText]] — markers, owned keys, foreign lines, SOCKS bypass list |
| `services/ProxyServiceTest` | 10 | [[ProxyService]] state machine |
| `listeners/ProxyStateChangeManagerTest` | 8 | [[ProxyStateChangeManager]] poll + notify |
| `services/gradle/GradleProxyConfigurerTest` | 5 | Gradle write orchestration |
| `services/git/GitProxyConfigurerTest` | 4 | [[GitProxyConfigurer]] arg building |
| `services/ProxyCredentialsStorageTest` | 4 | credential round-trip |
| `services/ProxyConfigurationEqualityTest` | 3 | [[Change Detection Enum Only]] guard |
| `widgets/ProxyStatusBarWidgetFactoryTest` | 2 | factory identity/availability |
| `widgets/ProxyIconsTest` | 1 | icon loading |

## Framework

JUnit 5 Jupiter **only** in test sources. ✅ `grep -rn "org.junit.Test\|junit.framework\|
org.junit.Assert" src/test/` → 0 hits.

`junit4` 4.13.2 is a declared dependency but `testRuntimeOnly` (`build.gradle.kts:42`) and is used
by **no test**. It exists because `com.intellij.tests.JUnit5TestSessionListener` — a
`LauncherSessionListener` that `testFramework(Platform)` registers automatically — needs
`junit.framework.TestCase` on the runtime classpath or the test JVM refuses to start. The reason is
written down at `build.gradle.kts:39-41` and in `gradle/libs.versions.toml:4`. Do not "clean up"
this dependency.

✅ No mocking framework and no platform test fixtures at all: `grep -rn
"mockk\|Mockito\|BasePlatformTestCase" src/` → 0 hits. Every test is a plain JVM unit test.

### Hand-written fakes

Both platform-touching tests use a hand-rolled `FakeProxySettings`, and it is **duplicated, not
shared**:

- `src/test/kotlin/org/holululu/proxythemall/services/ProxyServiceTest.kt:20`
- `src/test/kotlin/org/holululu/proxythemall/listeners/ProxyStateChangeManagerTest.kt:21`

Both are `private class`, so neither can see the other. Small cleanup opportunity: hoist one
internal `FakeProxySettings` into a shared test-support file. Not urgent — the two copies are
short and no test has yet diverged — but the second copy is where the next drift will happen.

## Placeholder tests: removed

History: roughly 10 tests whose whole body was `assertTrue(true)` existed before the audit and were
deleted rather than fixed — a test that cannot fail is worse than no test, because it makes the
count lie. ✅ `grep -rn "assertTrue(true)" src/` → **0 hits**. Zero remain.

## Coverage

Total LINE coverage **26.54 %** (`covered=298`, `missed=825`), from
`build/reports/kover/report.xml`. Kover floor is 20 (see [[Build Gates]]). The three new tests moved it
by ~0.1 point — they cover branches inside an already-100 % file.

Kover reports 27 source files; `src/main` contains 28 `.kt` files. The missing one is
`listeners/ProxyStateChangeListener.kt` — a `fun interface` with no executable body, so it has no
lines to instrument. Of the 27 reported, **16 are at 0 %**.

### The 0 % list

| File | Missed lines | Why untested |
|---|---|---|
| `core/ProxyController.kt` | 116 | needs `ProjectManager.getInstance().openProjects` and live `Project`s |
| `settings/ProxyThemAllConfigurable.kt` | 81 | Kotlin UI DSL `DialogPanel`, needs an app + EDT |
| `services/ProxyRestoreService.kt` | 73 | app service + `PasswordSafe` + `ProxySettings` |
| `services/ProxyThemAllStartupService.kt` | 45 | app-level service lifecycle |
| `services/ProxyInfoExtractor.kt` | 41 | reads platform `ProxyConfiguration` instances |
| `listeners/HttpProxySettingsChangeListener.kt` | 37 | app service singleton, calls into the controller |
| `utils/NotificationMessages.kt` | 32 | reached only from notification paths |
| `widgets/ProxyStatusBarWidget.kt` | 23 | `StatusBarWidget` needs a `StatusBar` |
| `services/gradle/GradleProxyService.kt` | 21 | `@Service(PROJECT)` façade |
| `services/git/GitProxyService.kt` | 20 | `@Service(PROJECT)` façade |
| `notifications/NotificationService.kt` | 15 | `NotificationGroupManager` |
| `settings/ProxyThemAllSettings.kt` | 10 | `PersistentStateComponent` |
| `actions/ProxyThemAllAction.kt` | 9 | needs `AnActionEvent` |
| `services/ProxyThemAllStartupActivity.kt` | 6 | `ProjectActivity`, suspend |
| `models/NotificationData.kt` | 4 | data holder, only constructed on notification paths |
| `listeners/WidgetStateChangeListener.kt` | 3 | trivial forwarder |

Highest-risk gaps, in order:

1. **`ProxyController`** (116 lines, 0 %) — the largest untested class *and* the orchestrator that
   every toggle routes through. It owns the two-callback atomic join, the one-balloon rule and
   apply-to-all-open-projects. [[Per Project Listener Registration]] and the multi-project section
   of [[Manual Verification]] both live here. `ProxyController.kt:47` `handleProxyToggle(project:
   Project?)` and `ProxyController.kt:212` `applyToAllProjects(...)` are the two entry points with
   no automated coverage at all.
2. **`ProxyRestoreService`** (73 lines) — guards [[Restored Password Not Persisted]], a defect that
   only manifests across a process restart. `ProxyRestoreService.kt:47`
   `restoreAndActivateProxy(project: Project?)`.
3. **`ProxyThemAllConfigurable`** (81 lines) — holds the plaintext-credential warning that the
   [[Defect Register]] lists as the mitigation for an accepted risk. If the warning silently
   disappears, nothing fails.
4. **`HttpProxySettingsChangeListener`** (37 lines) — the bridge from the poll to the writers. A
   regression here breaks every downstream feature while the 68 unit tests stay green.

Partially covered, worth noting because their absolute miss counts are the two largest in the
codebase after `ProxyController`: `GradleProxyConfigurer.kt` 9.8 % (157 missed) and
`GitProxyConfigurer.kt` 13.9 % (62 missed). The *pure* logic was extracted out of them —
[[GradlePropertiesText]] is at 100 % — and what remains uncovered is exactly the VFS / EDT /
`ProcessBuilder` shell around it.

### Why, and what would close it

The pattern is consistent: **everything at 0 % needs a live IDE.** Concretely one of

- a real `Project` (`ProxyController`, the two `@Service(PROJECT)` façades, the startup activity),
- an application container for `service<…>()` / `PasswordSafe` / `NotificationGroupManager`
  (`ProxyRestoreService`, `ProxyThemAllStartupService`, `NotificationService`,
  `ProxyThemAllSettings`),
- the EDT and Swing (`ProxyThemAllConfigurable`, `ProxyStatusBarWidget`),
- or a process restart (password persistence).

None of that is reachable from a plain JUnit test, and the project deliberately has **no**
`BasePlatformTestCase` and no mocking library, so there is currently no mechanism to fake it either.

To close the gaps, cheapest first:

1. **Constructor injection, already the established pattern.** `ProxyService` reaches 77.6 % purely
   because it takes `ProxySettings` as a parameter and `FakeProxySettings` can be handed in. The
   same trick applied to `ProxyController` (inject the project list and the two configurers instead
   of reaching for `ProjectManager` and services) would make its orchestration logic — the join,
   the single balloon, the ordering — testable with no platform at all. This is the highest
   value-per-line change available.
2. **Continue the extract-pure-logic move.** `GradlePropertiesText` is the proof: pulled out of
   `GradleProxyConfigurer`, it went to 100 %. `NotificationMessages` and `ProxyInfoExtractor` are
   nearly pure already and could be tested today with modest signature changes.
3. **Add `testFramework(TestFrameworkType.Platform)` fixture tests** for what genuinely needs a
   container (`ProxyThemAllConfigurable`, the widget, the startup service). This is the expensive
   option — slow tests, a heavier CI — and would be the first platform fixtures in the project.
4. **Not closable by unit tests at all:** restart-persistence, real `git` subprocess behaviour, and a
   live IDE launch. Those stay in [[Manual Verification]] (§7.5, §4.4, §2.5) permanently. Seven of the
   twelve fixed defects have no automated guard for exactly this reason — see [[Defect Register]].
   [[Startup Balloon]] is the newest of them: it needs a real startup sequence, so §2.5 is its entire
   safety net.

## Related

[[Build Gates]] · [[Manual Verification]] · [[Defect Register]] · [[Session History]]

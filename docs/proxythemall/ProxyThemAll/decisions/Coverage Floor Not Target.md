---
type: decision
status: current
tags: [testing, coverage, kover, ci, honesty]
verified: 2026-08-13
verified-against: efb35cc
---

# Coverage Floor Not Target

**Kover's `minValue` is 20 % LINE while actual coverage is 26.4 %. The number is a regression floor,
not a goal.**

## Context

`build.gradle.kts:127-146`:

```kotlin
kover {
    reports {
        total {
            xml { onCheck = true }

            // Floor, not a target: keeps coverage from silently regressing. Raise it as the
            // platform-coupled classes (widget, listeners, startup) gain tests.
            verify {
                rule {
                    bound {
                        minValue = 20
                        coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE
                    }
                }
            }
        }
    }
}
```

The intent is written in the build file itself (`:134-135`). `xml.onCheck = true` regenerates the report
on every `check`, and `koverVerify` is wired into `check`
(`:test → :koverGenerateArtifact → :koverXmlReport → :koverCachedVerify → :koverVerify → :check`) —
see [[Build Gates]].

The tension: this plugin is thin glue over IntelliJ Platform APIs. Large parts of it cannot be unit
tested without a live application — `NotificationGroupManager`, `ChangeListManager`, `StatusBar`,
`PasswordSafe`, `Task.Backgroundable`, `ProjectManager`. A coverage target that ignores that either
blocks all work or invites fake tests that assert nothing.

## Decision

Set the bound at **20 %**, below the actual **26.4 %**, and treat the gap as headroom rather than debt to
close on a schedule. Raise the floor when the platform-coupled classes genuinely gain tests.

✅ measured from `build/reports/kover/report.xml` at `efb35cc`:

| Unit | Covered | Missed | % |
|---|---|---|---|
| LINE | 298 | 825 | **26.54** |
| INSTRUCTION | 2002 | 3974 | 33.5 |
| BRANCH | 128 | 331 | 27.9 |
| METHOD | 75 | 195 | 27.8 |
| CLASS | 16 | 33 | 32.7 |

6.5 points of headroom over the floor. 68 tests, 0 failures.

## Consequences — stated honestly

**16 of the 27 files Kover reports have zero line coverage.** That is the number that matters, and it
is worse than "26 %" sounds. (`src/main` holds 28 `.kt` files; `listeners/ProxyStateChangeListener.kt`
is a body-less `fun interface` with no executable lines, so Kover emits no `sourcefile` entry for it —
it appears only as a method signature in the report. ✅ verified by parsing
`build/reports/kover/report.xml`.) ✅ from the same report:

| Zero-coverage file | Lines missed |
|---|---|
| `ProxyController.kt` | 116 |
| `ProxyThemAllConfigurable.kt` | 81 |
| `ProxyRestoreService.kt` | 73 |
| `ProxyThemAllStartupService.kt` | 45 |
| `ProxyInfoExtractor.kt` | 41 |
| `HttpProxySettingsChangeListener.kt` | 37 |
| `NotificationMessages.kt` | 32 |
| `ProxyStatusBarWidget.kt` | 23 |
| `GradleProxyService.kt` | 21 |
| `GitProxyService.kt` | 20 |
| `NotificationService.kt` | 15 |
| `ProxyThemAllSettings.kt` | 10 |
| `ProxyThemAllAction.kt` | 9 |
| `ProxyThemAllStartupActivity.kt` | 6 |
| `NotificationData.kt` | 4 |
| `WidgetStateChangeListener.kt` | 3 |

✅ denominator checked: 28 `.kt` files exist under `src/main/kotlin`, 27 appear in the Kover report, and
16 of those have `covered == 0`. The 28th, `listeners/ProxyStateChangeListener.kt`, is a 14-line
interface with no executable lines and so appears in no report at all — it is neither covered nor
missed.

And the 11 with some coverage:

| File | Covered / total lines |
|---|---|
| `GradlePropertiesText.kt` | **89 / 89 — 100 %** |
| `ProxyStateChangeManager.kt` | 55 / 58 |
| `ProxyService.kt` | 52 / 67 |
| `ProxyCredentialsStorage.kt` | 30 / 72 |
| `ProxyUrlBuilder.kt` | 23 / 26 |
| `GradleProxyConfigurer.kt` | 17 / 174 |
| `ProxyInfo.kt` | 10 / 11 |
| `GitProxyConfigurer.kt` | 10 / 72 |
| `ProxyIcons.kt` | 6 / 6 |
| `ProxyStatusBarWidgetFactory.kt` | 3 / 7 |
| `ProxyState.kt` | 3 / 4 |

The pattern is not random. **The pure logic is near-fully covered; the platform-coupled classes are
untouched.** `GradlePropertiesText` sits at 100 % *because* it was deliberately extracted with no
IntelliJ imports (`GradlePropertiesText.kt:5-10`) — the extraction was the testability decision, and the
coverage is its receipt. `ProxyService` is at 78 % because `ProxySettings` is an interface, so a
hand-written fake suffices; see [[No Mocking Library]].

Other consequences:

- **Seven of twelve fixed defects have no automated regression guard** ([[Defect Register]]) — all seven
  need a live IDE, a real `git` process, or a process restart. Those are the same classes shown above
  with zero coverage. The floor does not pretend otherwise. [[Startup Balloon]] is the newest example:
  a balloon on every IDE launch, and nothing short of launching an IDE can catch it.
- **A large refactor of the covered files could regress coverage without tripping the gate**, since
  there are 6.5 points of slack. Accepted: the alternative is a gate that fires on unrelated churn.
- **The floor drifts upward only manually.** Nothing raises `minValue` automatically, so the headroom
  can silently grow — coverage improves, the gate does not tighten, and a later regression can eat the
  whole gain. That is a real weakness of "floor, not target"; the mitigation is the lint pass in
  `CLAUDE.md`, not automation.
- **Codecov reports the real number to CI**, `codecov/codecov-action` on
  `build/reports/kover/report.xml` (`.github/workflows/build.yml`, job `test`) — so the honest figure is
  visible even though the gate is lenient.

## Alternatives rejected

**A high bound (80 %).** Rejected: unreachable without integration tests for the 16 files above, so it
would fail `check` permanently. A permanently red gate is ignored, and an ignored gate is worse than a
lenient one.

**A bound equal to current coverage (26 %).** Tempting and rejected: it fails on trivially legitimate
changes — add 20 lines of platform-coupled code with 5 lines of tests and the build breaks even though
absolute coverage went up. The gate would be reporting churn, not risk.

**No bound at all.** Rejected: nothing then stops a silent slide to zero. The 20 % floor is cheap and
catches the case that actually matters — someone deletes tests, or adds a large untested subsystem.

**Integration tests via `BasePlatformTestCase`** to lift the platform-coupled classes. Not rejected in
principle — deferred. Note the JUnit 4 machinery for it was *removed* at `9e11a0e`, leaving `junit4` as
`testRuntimeOnly` only (needed by the platform's own `JUnit5TestSessionListener`), so picking this up
means re-adding a dependency. See [[No Mocking Library]] and [[Build Gates]].

**Excluding platform-coupled classes from the coverage denominator.** Rejected: it would make the
percentage look good while hiding exactly the code that has caused every defect in
[[Defect Register]]. The honest low number is more useful than a flattering filtered one — and this page
exists to keep it visible.

## See also

[[Build Gates]] · [[No Mocking Library]] · [[Defect Register]] · [[GradlePropertiesText]] ·
[[ProxyService]] · [[Changelist Integration]]

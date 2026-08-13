---
type: operation
status: current
tags: [build, gradle, ci, verifier, coverage]
verified: 2026-08-13
verified-against: efb35cc
---

# Build Gates

What stops a bad change, and where. Everything below was **✅ executed** (`./gradlew check`,
`./gradlew verifyPlugin`, both `BUILD SUCCESSFUL`) — `check` most recently on the current working tree
on `efb35cc`, the squash-merge commit now on `main`.

## Local run configurations

Three, all `GradleRunConfiguration`, all in `.run/`:

| Config | Task | File |
|---|---|---|
| Run Tests | `check` | `.run/Run Tests.run.xml` |
| Run Verifications | `verifyPlugin` | `.run/Run Verifications.run.xml` |
| Run Plugin | `runIde` | `.run/Run Plugin.run.xml` |

`verifyPlugin` is **not** wired into `check` — running "Run Tests" alone tells you nothing about
platform compatibility. Two separate gates by design.

## Gate 1 — `check`

✅ 27 task lines in the executed graph (1 of them `SKIPPED`,
`:checkKotlinGradlePluginConfigurationErrors`), reported by Gradle as "20 actionable tasks".
The gate-relevant tail:

```
:test → :koverGenerateArtifact → :koverXmlReport → :koverCachedVerify → :koverVerify → :check
```

Also pulled in: `instrumentCode`, `instrumentedJar`, `composedJar`, `prepareTestSandbox`,
`prepareTest`, `instrumentTestCode` — i.e. tests run against the *instrumented* plugin, not raw
classes.

Result: **68 tests**, 0 failures, 0 errors, 0 skipped. See [[Test Suite]]. (65 before the final four fixes; the three
new ones arrived with [[SOCKS Bypass Hosts Ignored]].)

### Kover

`build.gradle.kts:127-146`:

| Setting | Value |
|---|---|
| `xml.onCheck` | `true` — report regenerated on every `check` |
| `verify.rule.bound.minValue` | `20` |
| `coverageUnits` | `CoverageUnit.LINE` |

✅ Actual total from `build/reports/kover/report.xml`: LINE `covered=298 missed=825` → **26.54 %**.
6.5 points of headroom over the floor. The comment at `build.gradle.kts:134-135` states the intent
explicitly: *"Floor, not a target"*.

## Gate 2 — `verifyPlugin`

✅ Plugin Verifier 1.409, three IDEs, all **Compatible**:

| IDE build | Verdict | Experimental API | Internal API |
|---|---|---|---|
| IC-243.28141.41 | Compatible | 5 usages | 0 |
| IC-251.29188.72 | Compatible | 5 usages | 0 |
| IC-252.28539.97 | Compatible | 5 usages | 0 |

All three also print: *"Plugin can probably be enabled or disabled without IDE restart"* — the
dynamic-plugin eligibility that [[Widget Project Leak]] and
[[Per Project Listener Registration]] exist to keep honest.

The 5 experimental usages are **one method from five call sites**, not five different risks:
`com.intellij.util.net.ProxySettings.setProxyConfiguration(ProxyConfiguration)`, invoked from

- `ProxyService.disableProxy(ProxySettings)`
- `ProxyService.enableProxy(ProxySettings)`
- `ProxyService.forceEnableProxy()`
- `ProxyRestoreService.restoreProxyToIntelliJ(ProxyInfo)`
- `ProxyController.performGlobalCleanup()`

Zero internal API usages — the verifier prints no internal-API section at all. Single point of
API risk; see [[ProxyService]].

Reports land in `build/reports/pluginVerifier/IC-*/` (`verification-verdict.txt`,
`experimental-api-usages.txt`, `report.html`).

## Gate 3 — CI

`.github/workflows/build.yml`, 5 jobs. **CI is a superset of the local run configs, not an equal
set** — `inspectCode` and `releaseDraft` have no local equivalent, and `buildPlugin` runs on its
own before anything else.

| Job | Needs | Runs | Local equivalent |
|---|---|---|---|
| `build` | — | `./gradlew buildPlugin` + artifact upload | none (implied by others) |
| `test` | build | `./gradlew check`, then `codecov/codecov-action@v7` on `build/reports/kover/report.xml` | Run Tests |
| `inspectCode` | build | `JetBrains/qodana-action@v2026.2.0` — **no `gradlew` at all** | none |
| `verify` | build | `./gradlew verifyPlugin` + report upload | Run Verifications |
| `releaseDraft` | build, test, inspectCode, verify | `./gradlew properties --property version`, `./gradlew getChangelog --unreleased`, `gh release create --draft` | none |

`releaseDraft` is skipped on pull requests (`if: github.event_name != 'pull_request'`). Triggers:
push to `main`, and any pull request.

Consequence worth remembering: **Qodana findings only ever appear in CI.** The
[[Non Compiling Commit]] defect was the inverse case — an IDE inspection suggested a rewrite that
no local gate caught because it never compiled locally either.

## Versions

`gradle/libs.versions.toml`:

| Dependency | Version | Note |
|---|---|---|
| kotlin | 2.4.10 | also the `kotlinxSerialization` plugin version |
| junit (Jupiter) | 6.1.3 | `testImplementation`, `build.gradle.kts:37` |
| junit4 | 4.13.2 | `testRuntimeOnly` **only**, `build.gradle.kts:42` — no test uses it |
| kotlinxSerialization | 1.11.0 | `implementation` |
| intelliJPlatform | 2.18.1 | |
| kover | 0.9.9 | |
| qodana | 2026.2.0 | matches the pinned action tag |
| changelog | 2.5.0 | |

`gradle.properties`:

| Property | Value |
|---|---|
| `platformType` | `IC` |
| `platformVersion` | `2024.3.6` |
| `pluginSinceBuild` | `243` |
| `pluginVersion` | `0.0.6` |
| `gradleVersion` | `9.7.0` |
| `platformBundledPlugins` | `Git4Idea` |
| `platformPlugins` / `platformBundledModules` | empty |

Plus `jvmToolchain(21)` (`build.gradle.kts:19-21`), `kotlin.stdlib.default.dependency=false`,
configuration cache and build cache both on.

The compile-time platform is 2024.3.6 while the verifier checks 243/251/252 — the plugin compiles
against the oldest supported build and is *verified* against the newest. That asymmetry is why
"compiles" is never sufficient evidence here; see the bytecode rule in `CLAUDE.md`.

## Related

[[Test Suite]] · [[Manual Verification]] · [[Session History]] · [[Defect Register]]

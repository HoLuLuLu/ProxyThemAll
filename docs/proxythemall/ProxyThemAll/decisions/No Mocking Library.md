---
type: decision
status: current
tags: [testing, dependencies, mockk, testability, lessons]
verified: 2026-08-12
verified-against: efb35cc
---

# No Mocking Library

**MockK was removed. `ProxySettings` is an interface, so a hand-written fake is enough.**

## Context

The project depended on MockK. It also carried, in its own test sources, the claim that the core
services were **untestable without mocks** — and that mocks did not work either.

The claim, at `233c282:src/test/.../services/ProxyServiceTest.kt:6-65`:

> ## Why ProxyService Cannot Be Tested with Pure JUnit 5
> ### Technical Challenge: ProxyConfiguration Types Cannot Be Mocked
> The ProxyConfiguration types (StaticProxyConfiguration, DirectProxy) cannot be mocked with MockK due
> to bytecode instrumentation conflicts:
> ```
> java.lang.UnsupportedOperationException: class redefinition failed:
> attempted to change the schema (add/remove fields)
> ```
> **Root Cause:**
> 1. IntelliJ Platform pre-instruments these classes during platform initialization
> 2. MockK attempts to re-instrument them for mocking purposes
> 3. Java's instrumentation API prevents modifying already-instrumented class schemas
> …
> This allows ProxySettings to be mocked, but ProxyConfiguration return types still cannot be mocked,
> preventing comprehensive unit testing.

The file's entire content was one placeholder test asserting `true`
(`233c282:...ProxyServiceTest.kt:68-80`):

```kotlin
@Test
fun testProxyServiceRequiresPlatformIntegrationTests() {
    // This test documents that ProxyService cannot be tested with pure JUnit 5 + MockK
    assertTrue(true, "ProxyService requires platform integration tests - see class documentation")
}
```

Same shape in `GitProxyServiceTest.kt`, `GradleProxyServiceTest.kt` and `ProxyControllerTest.kt` at that
commit. `6ad6eb9` "Refactor test suite (#41)" had converted them to "documentation placeholders" and
added the JUnit Vintage engine in anticipation of platform integration tests.

**The claim was wrong**, and the error is instructive: it correctly diagnosed that *MockK* could not
instrument the platform's configuration classes, then concluded that the *code* was untestable. It never
questioned whether mocking was needed at all.

## Decision

Remove MockK. Write fakes by hand.

The removal is `9e11a0e` "Cleanup dependencies", which dropped from `gradle/libs.versions.toml` and
`build.gradle.kts`:

- `io.mockk:mockk` 1.14.11 (`testImplementation`)
- `org.opentest4j:opentest4j` 1.3.0
- `junit4` demoted from `testImplementation` back to `testRuntimeOnly`
- the explicit `org.junit.vintage:junit-vintage-engine` runtime dependency, deleted

`junit4` survives as `testRuntimeOnly` for one reason, now documented in the build file: the platform's
`com.intellij.tests.JUnit5TestSessionListener` (a `LauncherSessionListener` registered by
`testFramework(Platform)`) needs `junit.framework.TestCase` on the runtime classpath or the test JVM
fails to start. No test uses JUnit 4.

## Why a fake suffices — the fact that was missed

✅ verified with `javap com.intellij.util.net.ProxySettings`:

```java
public interface com.intellij.util.net.ProxySettings {
  public abstract ProxyConfiguration getProxyConfiguration();
  public abstract void setProxyConfiguration(ProxyConfiguration);
  public static ProxySettings getInstance();
}
```

**An interface with two methods.** Implementing it takes six lines
(`ProxyServiceTest.kt:20-30`, duplicated at `ProxyStateChangeManagerTest.kt:21-29`):

```kotlin
private class FakeProxySettings(
    initial: ProxyConfiguration = ProxyConfiguration.direct
) : ProxySettings {
    var configuration: ProxyConfiguration = initial
    override fun getProxyConfiguration(): ProxyConfiguration = configuration
    override fun setProxyConfiguration(configuration: ProxyConfiguration) {
        this.configuration = configuration
    }
}
```

And the return type never needs mocking, because the platform provides **real factories** —
`ProxyConfiguration.proxy(protocol, host, port, exceptions)` and `ProxyConfiguration.direct`, both
`static` members on the interface (✅ `javap`). The instances they return are Kotlin data
classes/objects with real `equals`/`hashCode`, which is what makes assertions work at all
(see [[Change Detection]]).

So the original diagnosis was inverted: the platform types were not obstacles to testing, they were the
**test fixtures**. MockK was the obstacle.

The injection seams were already there, or trivially added:

| Seam | Cite |
|---|---|
| `ProxyService(proxySettings: ProxySettings = ProxySettings.getInstance())` | `ProxyService.kt:21-23` |
| `ProxyStateChangeManager(proxyServiceProvider: () -> ProxyService = { ProxyService.instance })` | `ProxyStateChangeManager.kt:19-22` |
| `GitProxyService(settings, extractor, configurer, proxySettings)` — all four defaulted | `GitProxyService.kt:22-27` |

The provider *lambda* on the manager (rather than a plain parameter) matters: it defers
`ProxyService.instance` so constructing the manager does not require a running application (`:20`).

The header comment now says the opposite of the old one — `ProxyServiceTest.kt:9-13`:

> ProxySettings is an interface and ProxyConfiguration provides real factories, so no mocking library is
> needed: a hand written in-memory implementation is enough.

## Consequences

- **10 tests now exist for `ProxyService`** where there was one `assertTrue(true)`: state detection
  (ENABLED / NOT_CONFIGURED), toggle round-trip, toggle with nothing configured, the purity of
  `getCurrentProxyState()`, `rememberActiveConfiguration`, both `forceEnableProxy` branches, and
  `getCurrentConfiguration` including its null-on-throw path. Coverage: **52 of 67 lines**.
- **8 tests for `ProxyStateChangeManager`** over the same fake, including the four that guard
  [[Change Detection Enum Only]]. Coverage: **55 of 58 lines**.
- **One dependency fewer**, and no bytecode instrumentation in the test JVM — which also removes the
  class-redefinition failure mode the old comment documented.
- **Fakes are duplicated** across two test files (`ProxyServiceTest.kt:20-30`,
  `ProxyStateChangeManagerTest.kt:21-29`). Deliberate: a shared `TestUtils.kt` existed and was deleted at
  `6ad6eb9` as "non-functional mock infrastructure". Six duplicated lines beat a shared test-utility
  module.
- **Platform-coupled classes stay untested** — `ProxyController`, `NotificationService`,
  `GradleProxyConfigurer`'s VFS/changelist paths. Removing MockK did not change that, and it was never
  a mocking problem: those classes call `getInstance()` on platform singletons with no seam. See
  [[Coverage Floor Not Target]].
- **Re-adding integration tests now costs a dependency again**, since the Vintage engine was removed.
  Accepted: pay it when the tests are actually written, not before.

## The lesson

A comment asserting "X cannot be tested" is a **claim about the code**, and it decays exactly like any
other. This one was:

- specific and technically accurate about MockK's instrumentation failure
- accompanied by a real stack trace
- carried in the test file itself, where a reader trusts it most
- and **wrong about the conclusion**, because it never asked whether mocking was required

Two false beliefs collapsed together: "the platform types are unmockable" (true) and "therefore the code
is untestable" (false). `javap` on the interface would have refuted the second in one command.

This is the same failure mode as every entry in [[Platform API Constraints]]: a plausible statement about
a platform API, believed rather than checked. The difference is that this one cost 18 months of "deferred
to Phase 4" instead of a broken build.

## Alternatives rejected

**Keep MockK for the classes it can handle.** Rejected: it earned nothing. Once the fake exists, MockK's
remaining value is stubbing platform singletons — which it demonstrably could not do here, and which
`mockkStatic` on `getInstance()` had already failed at
(`233c282:...GradleProxyServiceTest.kt:22-28`).

**Switch to a different mocking library (Mockito, MockK inline).** Rejected before trying: the problem
was never the library. Substituting one would have preserved the assumption that mocking was needed.

**Keep the placeholder tests as documentation.** Rejected. A test that asserts `true` is a lie in the
test count — a suite where some tests assert nothing is a worse signal than a smaller one where all of
them do.
Documentation belongs in this vault, not in a green checkmark.

**A shared test-fixtures module.** Rejected — see the duplication note above. `TestUtils.kt` was tried
and deleted.

**Go straight to `BasePlatformTestCase` integration tests.** Not rejected, deferred. It requires the
JUnit 4 machinery back and a live platform per test. The hand-written fake covers the state machine,
which is where the defects were.

## See also

[[Coverage Floor Not Target]] · [[Change Detection]] · [[Change Detection Enum Only]] ·
[[Platform API Constraints]] · [[ProxyService]] · [[ProxyStateChangeManager]] · [[Build Gates]]

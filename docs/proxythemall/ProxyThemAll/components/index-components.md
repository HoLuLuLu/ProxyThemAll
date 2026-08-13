---
type: index
status: current
tags: [index]
verified: 2026-08-12
verified-against: efb35cc
---

# Index — components/

One page per production class or subsystem. All claims cite `file:line` against `efb35cc`.

| Page | Source | One-liner |
|---|---|---|
| [[ProxyService]] | `services/ProxyService.kt` | The 3-state machine over the platform proxy API; pure query vs. mutating remember split. |
| [[ProxyStateChangeManager]] | `listeners/ProxyStateChangeManager.kt` | 2-second `scheduleWithFixedDelay` poll, listener registry, and the remember-before-notify loop guard. |
| [[GradlePropertiesText]] | `services/gradle/GradlePropertiesText.kt` | Dependency-free text layer for the managed `gradle.properties` section: markers, 12 owned keys, foreign-line rescue, escaping. |
| [[GitProxyConfigurer]] | `services/git/GitProxyConfigurer.kt` | `git config http.proxy` / `http.noproxy` only; exit-5 tolerance, scope selection, glob filtering that drops `127.*`. |
| [[ProxyController]] | `core/ProxyController.kt` | Toggle orchestration: two-callback atomic join, one balloon, apply-to-all-open-projects. |

## Reading order

[[ProxyService]] → [[ProxyStateChangeManager]] → [[ProxyController]] → the two writers
([[GitProxyConfigurer]], [[GradlePropertiesText]]).

## Related concepts

[[Change Detection]] · [[Platform API Constraints]] · [[Gradle Properties Management]] ·
[[Git Proxy Integration]] · [[Credential Handling]] · [[Multi-Project Behaviour]] ·
[[Notifications]] · [[Lifecycle and Leaks]] · [[Defect Register]]

---
type: concept
status: current
tags: [overview, architecture]
verified: 2026-08-13
verified-against: efb35cc
---

# Overview

**ProxyThemAll** is an IntelliJ Platform plugin that turns the IDE's proxy configuration into a
one-click toggle and mirrors it into Git and Gradle. Marketplace ID `org.holululu.proxythemall`,
version `0.0.6`, `pluginSinceBuild` 243, built against IC 2024.3.6.

Catalog of every page: [[index]]. Maintenance rules: [[CLAUDE]].

## What it actually does

| Capability | Where | Notes |
|---|---|---|
| Toggle the IDE proxy | [[ProxyService]] | 3 states; toggling off remembers the configuration so it can be restored |
| Mirror to Git | [[GitProxyConfigurer]] | `http.proxy` + `http.noproxy` only — git has no `https.*` equivalents |
| Mirror to Gradle | [[GradlePropertiesText]] | A marked, managed section in `gradle.properties` (opt-in, off by default) |
| Back up / restore | [[Credential Handling]] | JSON payload in PasswordSafe; survives IntelliJ forgetting the proxy after restart |
| Status bar widget | [[Notifications]], [[Lifecycle and Leaks]] | Three states, click to toggle, hideable without restart |
| Apply across projects | [[Multi-Project Behaviour]] | A toggle configures every open project |

## The shape of the thing

```
Tools menu / status bar widget
        │
        ▼
   ProxyController ──────────► GitProxyService ──► GitProxyConfigurer ──► `git config`
        │  (all open projects)   GradleProxyService ─► GradleProxyConfigurer ─► gradle.properties
        │                                                     │
        ▼                                                     └─► GradlePropertiesText (pure text)
    ProxyService ◄──── polls ──── ProxyStateChangeManager
   (IDE ProxySettings)                    │
                                          ├─► HttpProxySettingsChangeListener ─► backup + reapply
                                          └─► WidgetStateChangeListener ─► repaint
```

Two entry paths, and the difference matters:

- **Plugin-initiated** (menu, widget) — applies immediately.
- **User edits the IDE's HTTP Proxy dialog** — picked up by a 2 s poll, so up to ~2 s of latency.
  This is not a design preference; see [[Polling Over Events]].

## The single most important thing to know

The IntelliJ Platform 2024.3 publishes **no event** when proxy settings change. Everything about this
plugin's architecture follows from that one constraint: the poll, the configuration comparison, the
remember-before-notify ordering, the ~2 s latency. Read [[Change Detection]] before changing anything
in the listener layer.

The second most important: **do not trust assumptions about platform APIs.** This project has been
burned repeatedly by plausible-but-wrong beliefs — an inspection that suggests non-compiling code, an
`@Internal` API that fails the verifier, an async changelist treated as synchronous. Every one is
recorded in [[Platform API Constraints]] with the bytecode evidence.

## State of the code

All of the work below is on `main`, squash-merged as `efb35cc`. The hardening pass **shrank** the
plugin: net **−60 lines** under `src/` (35 files, +1899 / −1959), while the whole-tree diff is +64
because of the CHANGELOG and README additions. See [[Session History]].

| Gate | Status |
|---|---|
| `Run Tests` → `gradlew check` | ✅ 68 tests, 0 failures, Kover floor holds |
| `Run Verifications` → `gradlew verifyPlugin` | ✅ Compatible with IC-243, IC-251, IC-252; zero internal-API usage |

**Twelve defects** were found and fixed, including two that destroyed user data and two that leaked
credentials — [[Defect Register]] has the full table plus the risks that remain open. The last four to
land were [[Startup Balloon]] (a proxy balloon on every launch, per project),
[[SOCKS Bypass Hosts Ignored]] (a SOCKS user's own exception list silently ignored by Gradle), and the
`warnIfMemoryOnly` and `detectLineSeparator` cleanups — all found while writing this wiki, by
fact-checking claims against the code rather than by testing it.

What remains genuinely open is smaller than it was: plaintext Gradle credentials (accepted by design),
`DISABLED` not surviving a restart, and one ⚠️ unconfirmed measurement suggesting git may ignore
`http.noproxy` entirely.

Coverage is 26.5 % line, with 16 of 27 reported files at zero. That is deliberate and honest rather
than accidental — see [[Coverage Floor Not Target]] and [[Test Suite]] — but it means the manual plan
in [[Manual Verification]] is load-bearing, not optional.

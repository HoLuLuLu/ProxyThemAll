---
type: index
verified: 2026-08-13
verified-against: efb35cc
---

# Index

Catalog of every page in this vault. Read this first when answering a question, then drill into the
pages it points at. Maintenance rules live in [[CLAUDE]].

Start here: **[[Overview]]** · timeline: **[[log]]** · highest-value page: **[[Platform API Constraints]]** · what broke and
why: **[[Defect Register]]**

## Concepts — mechanisms and platform knowledge

| Page | Summary |
|---|---|
| [[Git Proxy Integration]] | What git actually supports, scope selection, exit-5, dropped globs |
| [[Change Detection]] | Why detection is a 2 s poll, what it compares, and the remember-before-notify loop guard |
| [[Platform API Constraints]] | Hard-won IntelliJ Platform facts: asymmetric accessors, `@Internal` APIs, async changelists, git exit codes |
| [[Credential Handling]] | Where credentials live, how they are encoded, and where they can leak |
| [[Gradle Properties Management]] | The managed-section contract: markers, ownership, escaping, read/write symmetry |
| [[Changelist Integration]] | Filing `gradle.properties` edits into the ProxyThemAll changelist without racing VCS |
| [[Multi-Project Behaviour]] | Toggles fan out to all open projects; only one balloon |
| [[Lifecycle and Leaks]] | Disposables, idempotent registration, app-level init, EDT rules |
| [[Notifications]] | Which notifications the settings toggle may suppress, and which always show |

## Components — one page per production class

| Page | Summary |
|---|---|
| [[ProxyService]] | The 3-state machine; pure-query rule; platform factories |
| [[ProxyStateChangeManager]] | The polling engine, listener bookkeeping, Disposable lifetime |
| [[GradlePropertiesText]] | Pure text layer for the managed section — no IntelliJ or IO deps, hence testable |
| [[GitProxyConfigurer]] | `http.proxy` / `http.noproxy` writes, scope selection, exit-5 tolerance |
| [[ProxyController]] | Orchestration, the atomic two-callback join, all-projects fan-out |
| [[index-components]] | Folder index for `components/` |

## Defects — what broke, why, and whether a test now catches it

Full table with severity and guard status: **[[Defect Register]]** — **12 defects**, all fixed.

| Page | Severity |
|---|---|
| [[Global Git Config Deleted]] | critical |
| [[Credentials In Log]] | critical |
| [[Gradle Block Duplication]] | high |
| [[JvmArgs Clobbered]] | high |
| [[Restored Password Not Persisted]] | high |
| [[Foreign Lines Destroyed]] | high |
| [[Change Detection Enum Only]] | high |
| [[Widget Project Leak]] | high |
| [[Per Project Listener Registration]] | high |
| [[Startup Balloon]] | high |
| [[SOCKS Bypass Hosts Ignored]] | medium |
| [[Non Compiling Commit]] | medium |

## Decisions — and their rationale

| Page | Decision |
|---|---|
| [[Polling Over Events]] | Forced: the platform publishes no proxy-change topic |
| [[Plaintext Gradle Credentials]] | Keep writing them; warn loudly. Gradle offers no encrypted alternative |
| [[Coverage Floor Not Target]] | Kover floor at 20 % LINE to stop regressions, not to claim quality |
| [[No Mocking Library]] | `ProxySettings` is an interface; a hand-written fake beats MockK here |

## Operations — how to build, test and verify

| Page | Summary |
|---|---|
| [[Build Gates]] | `check` and `verifyPlugin`, what each covers, CI parity, dependency versions |
| [[Test Suite]] | 68 tests in 10 files, per-class counts, and the honest coverage gaps |
| [[Manual Verification]] | The 48-step manual plan and which defects only it guards |
| [[Session History]] | How the code reached its current state (historical: pre-squash commits) |

## Conventions

- ✅ = verified by running it or reading bytecode · ⚠️ = inferred, not executed
- Every behavioural claim carries a `file:line` citation
- `verified-against` in frontmatter names the commit the claims were checked against — if it is far
  behind `HEAD`, re-check before trusting the page

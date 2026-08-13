---
type: index
status: current
tags: [defects, register, audit]
verified: 2026-08-13
verified-against: efb35cc
---

# Defect Register

Every defect found in the ProxyThemAll audit, all real, all fixed unless marked otherwise.
**12 defects.**

All twelve fixes are on `main` as of `efb35cc`, which passes `./gradlew check` (68 tests). Line
citations below are against that commit.

Note on the shas in these pages: pre-fix citations mostly use `233c282`, which is still reachable from
`main` and can be inspected with `git show`. A few pages cite `75dd04d`, `f850ec0` or `b8beb7a` — those
were the original branch commits and the squash merge discarded them, so a fresh clone cannot resolve
them. They are kept because the history they document is real and instructive.

## Fixed

| Defect | Severity | Status | One line | Regression guard |
|---|---|---|---|---|
| [[Global Git Config Deleted]] | critical | fixed | `git config --unset` exits 5 for a missing key → threw → `removedAny` false → fell through to wiping the user's `--global http.proxy`. The common case. | no |
| [[Credentials In Log]] | critical | fixed | `LOG.info(... $proxyUrl)` wrote `http://user:password@host:port` into `idea.log`, which users attach to bug reports. | no (manual §4.3) |
| [[Gradle Block Duplication]] | high | fixed | Two near-identical parsers; the one without `maxOf(0, …)` computed `startIndex = -1` for a marker on line 0, silently no-oped, and every toggle appended another block. | yes |
| [[JvmArgs Clobbered]] | high | fixed | Plugin appended its own `org.gradle.jvmargs`; last duplicate key wins in `.properties`, so a user's `-Xmx4g` was silently dropped → OOM. | yes |
| [[Restored Password Not Persisted]] | high | fixed | `setCredentials(..., remember = false)` made the restored password memory-only, so it vanished on restart — defeating the backup feature. | no (manual §7.5) |
| [[Foreign Lines Destroyed]] | high | fixed | (a) removal deleted every line between the markers; (b) read from disk, write to VFS, so an unsaved editor document was invisible then overwritten. | partial — (a) yes, (b) no |
| [[Change Detection Enum Only]] | high | fixed | The poll compared only the `ProxyState` enum, so a host/port/protocol edit while ENABLED updated nothing. First fix landed *below* the enum gate and was unreachable. | yes |
| [[Widget Project Leak]] | high | fixed | `disposeWidget` overridden as a no-op, discarding the interface default `Disposer.dispose(widget)` — the widget, its listener and its `Project` were retained forever. | no |
| [[Per Project Listener Registration]] | high | fixed | A per-project `ProjectActivity` registered the app-level singleton listener once per project into a plain list → N git invocations and N `gradle.properties` rewrites per change; and the poll could never be stopped. | yes |
| [[Startup Balloon]] | high | fixed | Startup reconciliation called the *notifying* reapply with `notificationProject = null`, so the per-project guard evaluated true for every project — a balloon on every launch, per project, including "Proxy Disabled" to users with no proxy. | no (manual §2.5) |
| [[SOCKS Bypass Hosts Ignored]] | medium | fixed | The bypass list was written outside the `isSocks` branch as `http.nonProxyHosts`; the JDK consults a separate `socksNonProxyHosts` for socket connections, so a SOCKS user's own exceptions were silently ignored. | yes |
| [[Non Compiling Commit]] | medium | fixed | `document.text = newContent` cannot compile (asymmetric accessors → `val`). Committed twice; the IDE's default-on property-access inspection suggests the broken rewrite. | compiler + `@Suppress` |

Regression-guard column: **yes** = a named unit test fails if it regresses; **no** = manual verification
only. Seven of twelve have no automated guard, all of them because they need a live IDE, a real `git`
process, or a process restart.

## Fixed most recently

Four fixes live only in the working tree. Two got their own pages; two were small enough to record here:

| Fix | Where | Note |
|---|---|---|
| [[Startup Balloon]] | `ProxyThemAllStartupService.kt:140` | now calls `...ForAllProjectsSilently(isProxyActive)` |
| [[SOCKS Bypass Hosts Ignored]] | `GradlePropertiesText.kt:35`, `:44`, `:185-192` | new key **and** its `OWN_KEYS` registration |
| `warnIfMemoryOnly` on every path | `ProxyCredentialsStorage.kt:194-200` | moved into `createCredentialAttributes()`, which save/load/has/clear all route through — so the warning now fires on reads too |
| `detectLineSeparator` KDoc | `GradlePropertiesText.kt:236-244` | doc-only: the KDoc now states the actual any-CRLF rule and why counting would not help |

The `warnIfMemoryOnly` move is the shape of fix this codebase keeps rewarding: the call went into the
one private function all four public entry points already shared (`:194-200`), rather than being
copy-pasted into each. See [[Credential Handling]].

## Open / accepted risks

### Gradle credentials in plaintext — accepted, user decision

`GradlePropertiesText.buildSection` writes the proxy username and password as
`systemProp.http.proxyUser` / `...proxyPassword` in cleartext (`GradlePropertiesText.kt:195-201`). This is
how Gradle consumes proxy credentials; there is no encrypted alternative.

Accepted deliberately, with mitigations rather than a fix:

- Warning at the write site (`GradlePropertiesText.kt:196`) and in the emitted file itself —
  `# Proxy Authentication (stored in plain text - do not commit)` (`GradlePropertiesText.kt:57`).
- Settings-panel warning under "Apply proxy settings to Gradle"
  (`ProxyThemAllConfigurable.kt:68-73`), which states explicitly that the ProxyThemAll changelist
  "is not a security boundary - a commit of all changes will include them".
- Gradle support is **off by default** (`ProxyThemAllSettings.kt:36`,
  `enableGradleProxySupport = false`).

### `DISABLED` is not durable across restart — OPEN, by design limitation

`ProxyThemAllSettings` persists a single boolean, `lastKnownProxyEnabled`
(`ProxyThemAllSettings.kt:42`), while `ProxyState` has three values
(`ProxyState.kt:6-9`). `DISABLED` (configured but off) and `NOT_CONFIGURED` (nothing there) both persist as
`false` (`ProxyThemAllStartupService.kt:105-117`).

On the next startup, `NOT_CONFIGURED` + a stored backup + `lastKnownProxyEnabled == true` triggers
auto-restore (`:98-107`). A user who *deliberately* disabled the proxy is not distinguished from one whose
IDE lost the setting — the plugin can only tell them apart while the process lives.

Accepted: making it durable means persisting a tri-state and deciding what to do when the IDE's own
configuration and the plugin's memory disagree. See [[Change Detection Enum Only]] for the related
coarseness of `ProxyState`.

### ⚠️ git may ignore `http.noproxy` entirely — UNCONFIRMED, needs a second git build

Measurement during verification suggested git may not honour `http.noproxy` at all, rather than merely
being glob-blind to entries like `10.*`. If true, the [[GitProxyConfigurer]] filtering at
`GitProxyConfigurer.kt:98-102` is moot and the git bypass list never applies.

⚠️ Tested against **one** git build only (Apple Git-155 / 2.50.1). One measurement on one build is not
enough to file this as a defect — reproduce on a second build (a Homebrew or Linux git) before acting.
Deliberately left unresolved rather than half-asserted.

## Documented but harmless

Not defects, and not risks — behaviours that look wrong until you know why they are there. Recorded so a
future pass does not "fix" them.

### `systemProp.https.nonProxyHosts` is inert

`buildSection` writes it alongside the http key in the non-SOCKS branch
(`GradlePropertiesText.kt:191`). The JDK maps the `https` scheme onto the **http** bypass list — there is
no separate `https.nonProxyHosts` in `DefaultProxySelector` — so the key has no effect whatsoever.

Kept deliberately, for symmetry with `systemProp.https.proxyHost` / `...proxyPort` immediately above it:
a reader scanning the section sees a complete `https.*` group rather than an apparent omission. The
comment at `:190` says so: *"https reuses the http list in the JDK; written for readability, not
effect."* It is also in `OWN_KEYS` (`:46`), so it is removed cleanly.

Contrast [[SOCKS Bypass Hosts Ignored]], where a *missing* key was a real defect: an inert key costs a
line of noise, a missing one costs the user's exception list.

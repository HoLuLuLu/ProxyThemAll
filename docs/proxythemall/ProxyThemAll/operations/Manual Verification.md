---
type: operation
status: current
tags: [testing, manual, security, sandbox]
verified: 2026-08-13
verified-against: efb35cc
---

# Manual Verification

The human-facing test contract: **`docs/VERIFICATION.md`** — path relative to the repo root, i.e.
*outside this vault* (the vault itself lives at `docs/proxythemall/ProxyThemAll/`). 611 lines.

Per `CLAUDE.md`: when this wiki and `docs/VERIFICATION.md` disagree about expected behaviour, the
manual plan wins — it is what a human signs off against.

## Shape

✅ Counted from the file (`grep -c '^### '`): **11 numbered sections** (§0 setup + §1–§10) plus an
unnumbered *Result sheet*, and **53 `###` steps** total — 5 of them setup (§0.1–§0.5), so **48
executable test steps**. Steps use `x.y` numbering, not a flat 1..n list, and two carry a letter
suffix (§5.8b, §7.2b) because they were inserted beside an existing step rather than appended.

Was 46. Two steps were added with the 2026-08-13 fixes:

| Step | Guards |
|---|---|
| **§2.5** No balloon on IDE startup | [[Startup Balloon]] — three restart cases: configured+enabled, configured+disabled, never configured |
| **§5.8b** SOCKS bypass hosts use the SOCKS key | [[SOCKS Bypass Hosts Ignored]] — the key is written *and* removed again on disable |

Every step is `**Do**` (your actions) / `**Expect**` (what must happen), with `❌` marking the
specific bug the step guards. Two more markers are used: `⚠️ key fix` (**13** steps: §2.5, §3.4, §4.4,
§5.2, §5.3, §5.4, §5.8b, §6.1, §6.3, §7.2, §7.5, §8.1, §9.2) and `🔒` (security/robustness, 6 steps:
§4.3, §6.4, §7.2b, §8.3, §9.3, §10.3). No step carries both.

## Fixtures

Two throwaway projects under `~/IdeaProjects/`, both pre-existing — the plan says "nothing to
create":

| Project | Contents | Exercises |
|---|---|---|
| `pta-gradle` | `build.gradle`, `settings.gradle`, `gradle.properties`, git repo | the Gradle and Git paths |
| `pta-plain` | `notes.txt` only — neither Gradle nor git | the graceful-degradation paths |

Both must be open **simultaneously** (second one in a New Window) because §8 tests multi-project
behaviour.

`pta-gradle` starts from a clean committed tree with exactly two properties:

```properties
org.gradle.caching=true
my.last.property=keep-me
```

These two lines are the payload for §5.3 ("your content survives"). Reset between runs:
`git checkout -- . && git clean -qfd` in `pta-gradle`.

Fixed fake proxy — nothing needs to listen on the port:

| Field | Value | Why this value |
|---|---|---|
| Host | `proxy.test.local` | |
| Port | `8080` | |
| User | `test\user` | backslash — domain-qualified user, must survive encoding |
| Password | `p@ss word/1` | `@`, space and `/` — all three must be percent-encoded (§4.2, §5.5) |
| Exceptions | `localhost,build.example.com,10.*` | mixes literal, FQDN and glob (§4.1 drops globs git can't express) |

Everything runs in the Gradle sandbox: `.intellijPlatform/sandbox/ProxyThemAll/IC-*/`. Log tail:
`tail -f .intellijPlatform/sandbox/.../log/idea.log | grep -i proxythemall`.

## The "wait ~3 s" rule

§0.5 explains it and it applies to roughly every §2–§7 step. The platform publishes **no event**
when proxy settings change, so [[ProxyStateChangeManager]] polls instead:
`STATE_CHECK_INTERVAL = 2L` seconds, `ProxyStateChangeManager.kt:29`, used as both initial delay and
period of a `scheduleWithFixedDelay` at `ProxyStateChangeManager.kt:160-170`. ✅ Code and plan agree
on 2 s.

So the rule is:

- change made **in the HTTP Proxy dialog** → wait ~3 s (one poll period plus slack) before judging;
- change made **through the plugin** (Tools menu, status bar widget) → applies immediately, no wait;
- still wrong after ~10 s (five poll periods) → real failure, not a timing artifact.

Judging a polled result too early is the single most likely way to record a false ❌. See
[[Change Detection]].

## Coverage map

| § | Steps | Feature area | Wiki page |
|---|---|---|---|
| 0 | 5 | setup: build, projects, proxy details, reference points, the 3 s rule | — |
| 1 | 2 | settings panel — six controls, defaults, persistence, credential warning | — |
| 2 | 5 | state detection, enable, toggle off/on, exactly one balloon, **no balloon on startup** | [[ProxyService]], [[ProxyController]], [[Startup Balloon]] |
| 3 | 4 | status bar widget — 3 icons, tooltip, click, hide/show without restart | [[Widget Project Leak]] |
| 4 | 6 | git — credentials, RFC 3986, log hygiene, **global config**, cleanup, non-git | [[GitProxyConfigurer]] |
| 5 | 10 | gradle — properties, duplication, content survival, jvmargs, specials, non-gradle, global fallback, SOCKS, **SOCKS bypass keys**, CRLF | [[GradlePropertiesText]], [[SOCKS Bypass Hosts Ignored]] |
| 6 | 5 | ProxyThemAll changelist — filing, active list, auto-removal, foreign changes in/outside the block | [[Foreign Lines Destroyed]] |
| 7 | 7 | backup & restore via secure storage — backup, refresh, idle no-op, manual + startup restore, password persistence, clear | [[Restored Password Not Persisted]] |
| 8 | 3 | multi-project — both updated at once, no duplicated work, no listener growth | [[Per Project Listener Registration]] |
| 9 | 3 | notifications — setting respected, errors always shown, no IDE fatal-error reports | [[Notifications]] |
| 10 | 3 | robustness sweep — no UI freezes, clean shutdown, whole-log final scan | [[Lifecycle and Leaks]] |

§5 (10 steps) and §7 (7 steps) are the heaviest — the two subsystems that write to disk. ✅ Per-section
counts sum to 53, matching the total.

## Steps that are the ONLY guard for a defect

Seven of the twelve fixed defects have no automated regression test ([[Defect Register]]). These five
steps are the entire safety net for theirs — if the step is skipped, the regression ships:

| Step | Defect | Why no unit test is possible |
|---|---|---|
| §4.3 Credentials never reach the log 🔒 | [[Credentials In Log]] | asserts on `idea.log` contents produced by a running IDE; the check is a `grep -iE "p@ss\|password@\|:.*@proxy"` over the sandbox log, expecting no output |
| §4.4 Global git config is never touched ⚠️ | [[Global Git Config Deleted]] | needs a real `git` process, since the root cause is `git config --unset` **exiting 5** for a missing key |
| §6.4 Foreign changes are moved, never reverted 🔒 | [[Foreign Lines Destroyed]] | needs the VCS changelist machinery and an open editor document; the disk-vs-VFS half of that defect is invisible without one |
| §7.5 Restored password persists across restart ⚠️ | [[Restored Password Not Persisted]] | the bug *is* a process restart — `remember = false` looks perfectly correct in-process |
| §2.5 No balloon on IDE startup ⚠️ | [[Startup Balloon]] | needs a real launch of a real IDE with a real project open; `ProxyController` and `ProxyThemAllStartupService` are both at 0 % coverage and nothing can fake a startup sequence |

§6.4 is partly covered: the "removal deleted every line between the markers" half has a
[[GradlePropertiesText]] unit test; the "read from disk, write to VFS" half does not.

§5.8b is *not* in that list, deliberately: [[SOCKS Bypass Hosts Ignored]] has three unit tests, so the
manual step is confirmation rather than the only net. It still earns its place — the unit tests operate
on strings, while §5.8b checks a real `gradle.properties` written by the real writer.

§2.5 is also the step that catches the *opposite* over-correction: it re-asserts §2.4 afterwards, so a
fix that silenced every balloon rather than only the startup ones fails here.

## Steps that mutate state outside the sandbox

Everything else is confined to the sandbox config directory or the two throwaway projects. **Two
steps reach outside it.** Both ship their own cleanup — do not abandon either half-finished:

| Step | What it mutates | Cleanup, in the step |
|---|---|---|
| §4.4 | your **real global git config** — `git config --global http.proxy "http://manual.example.com:3128"` | `git config --global --unset http.proxy` |
| §7.4 | deletes a sandbox options file — `rm -f .intellijPlatform/sandbox/ProxyThemAll/IC-*/config/options/proxy.settings.xml` | none needed: the following `./gradlew runIde` restores it from secure storage, which is the assertion |

§4.4 is the one to be careful with: it deliberately plants a value in a **global, real** git config
to prove the plugin does not delete it. Abort mid-step and you leave a bogus proxy in your own git
setup. §7.4 is destructive but bounded — the deleted file is sandbox-only and being restored *is*
the expected result, so it self-heals.

The plan's own preamble to §0.1 is the gate: `./gradlew test verifyPlugin` must both succeed before
any manual testing starts — see [[Build Gates]].

## Related

[[Build Gates]] · [[Test Suite]] · [[Defect Register]] · [[Session History]]

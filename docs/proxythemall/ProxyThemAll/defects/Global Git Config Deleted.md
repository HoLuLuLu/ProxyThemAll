---
type: defect
status: fixed
severity: critical
tags: [git, data-loss, exit-code, process-execution]
verified: 2026-08-12
verified-against: efb35cc
---

# Global Git Config Deleted

Disabling the proxy deleted the user's `--global http.proxy` — a value the plugin never wrote.

## Symptom

User has a hand-configured global proxy:

```bash
git config --global http.proxy "http://manual.example.com:3128"
```

Toggle the IDE proxy off. The global value is gone. No error, no notification — the plugin reports
"proxy removed globally" as a success.

## Root cause

Two facts combined.

**Fact 1 — `git config --unset` exits 5 when the key does not exist.** ✅ verified empirically:

```
$ git init -q /tmp/ptatest && cd /tmp/ptatest
$ git config --unset http.proxy;     echo $?   # 5
$ git config --unset-all http.proxy; echo $?   # 5
```

**Fact 2 — `executeGitCommand` threw on any non-zero exit.** In `233c282` the project-level removal
sat inside a `try` whose `catch` merely logged at debug and left `removedAny = false`
(`233c282:src/main/kotlin/.../git/GitProxyConfigurer.kt:120-140`). The very next block read:

```kotlin
// If no project-level settings were removed, try global settings
if (!removedAny) {
    executeGitCommand(null, listOf("config", GLOBAL_FLAG, UNSET_FLAG, HTTP_PROXY))
```

So: no local `http.proxy` → exit 5 → exception → `removedAny` false → fall through → wipe `--global`.

This was the **common** path, not an edge case. Most projects have no local `http.proxy` key, so the
`--global` branch was the *default* behaviour of "disable proxy", and it targeted whatever the user
had configured themselves.

## Why it survived

- The fall-through was written as a *feature*: "if there's nothing local, the plugin must have
  configured globally, so clean up there." That reasoning is only sound if failure-to-remove and
  nothing-to-remove are distinguishable — and exit 5 made them identical.
- Exit code 5 is not documented in the `git-config` synopsis most people read; it lives in the EXIT
  STATUS section. Nobody checked it, and the `catch` block's debug message
  ("Project-level proxy settings not found") *asserted* the wrong interpretation in prose, which
  reads as if it had been verified.
- There were no tests around `removeGitProxySettings` at all — it needs a real `git` process, so it
  fell into the "integration test, later" gap.
- Manual testing was done on a machine with **no** global proxy set. Destroying a key that isn't
  there is invisible.

## Fix

`src/main/kotlin/org/holululu/proxythemall/services/git/GitProxyConfigurer.kt`

1. Exit 5 is tolerated instead of thrown, with the reason stated at the constant
   (`GitProxyConfigurer.kt:23-24`, `GitProxyConfigurer.kt:177-181`):

```kotlin
if (processOutput.exitCode != 0 && processOutput.exitCode != EXIT_CODE_KEY_MISSING) { ... throw ... }
```

2. `--unset` became `--unset-all` (`GitProxyConfigurer.kt:19`), so a multi-valued key is fully
   removed rather than erroring.

3. **The scope is chosen once, and never as a fallback** (`GitProxyConfigurer.kt:116-127`):

```kotlin
// Only ever touch the scope we wrote to. Falling back to --global here
// would delete a proxy the user configured themselves.
val scope = if (projectDir != null) emptyList() else listOf(GLOBAL_FLAG)
```

The same expression picks the scope on write (`GitProxyConfigurer.kt:61`), so remove can never reach
a scope that write could not have reached. `--global` is now unreachable whenever a project
directory exists.

4. Success is decided by exit code, not by exception control flow
   (`GitProxyConfigurer.kt:122-128`): `removedProxy = ...exitCode == 0`.

Also dropped in the same change: `https.proxy` and `https.noproxy` were being written and unset, but
git has no such keys — HTTPS routes through `http.proxy` (`GitProxyConfigurer.kt:15`).

## Regression guard

**None automated** — this path shells out to `git`. `GitProxyConfigurerTest` covers only the pure
`gitNoProxyHosts` helper.

Manual: `docs/VERIFICATION.md` §4.4 "Global git config is never touched", which plants a global
value, disables the proxy, and asserts the global value survives while the local one is gone.

⚠️ This is the highest-value missing test in the project: a critical, silent, user-data-destroying
path guarded only by a human remembering to run a checklist.

---
type: concept
status: current
tags: [git, credentials, proxy]
verified: 2026-08-12
verified-against: efb35cc
---

# Git Proxy Integration

How the plugin mirrors the IDE proxy into Git. Implementation: [[GitProxyConfigurer]]. Enabled by
default (`applyProxyToGit = true`, `ProxyThemAllSettings.kt:33`).

## What git actually supports

This is where the original implementation went wrong, so it is worth stating precisely.

| Key | Real? | Used by the plugin |
|---|---|---|
| `http.proxy` | ✅ yes | ✅ yes |
| `http.noproxy` | ✅ yes (git ≥ 2.9) | ✅ yes |
| `https.proxy` | ❌ **not a git config key** | ❌ removed |
| `https.noproxy` | ❌ **not a git config key** | ❌ removed |

Git routes **both** HTTP and HTTPS through `http.proxy`. There is no separate HTTPS proxy key — the
`https.*` writes an earlier version emitted were inert noise in the user's config. Contrast Gradle,
which genuinely does need `systemProp.https.*` ([[Gradle Properties Management]]).

## Scope selection

One decision, made once (`GitProxyConfigurer.kt:61` for writes, `:118` for removal):

```kotlin
val scope = if (projectDir != null) emptyList() else listOf(GLOBAL_FLAG)
```

`--global` is therefore **unreachable whenever a project directory exists**. That is not a style
preference — the absence of a fall-through is the fix for [[Global Git Config Deleted]], which
destroyed proxy settings the plugin never set.

## Exit code 5

`git config --unset` and `--unset-all` both exit **5** when the key is absent (✅ verified
empirically, not from documentation). This is the *normal* case when disabling a proxy that was never
applied to a given repo, so it must not be treated as failure:

```kotlin
if (processOutput.exitCode != 0 && processOutput.exitCode != EXIT_CODE_KEY_MISSING) { ... throw }
```

`GitProxyConfigurer.kt:24` (the constant) and `:177` (the tolerance).

## Non-proxy hosts, and what gets silently dropped

`gitNoProxyHosts()` (`GitProxyConfigurer.kt:95-102`) reads **`proxyInfo.bypassHosts`** — the user's
exceptions *plus* `ProxyInfo.ESSENTIAL_BYPASS_HOSTS` (`localhost`, `127.*`, `[::1]`) — then:

```kotlin
.filter { it.isNotEmpty() && !it.contains('*') }
```

Consequence worth knowing: git's `http.noproxy` matches plain host and domain names, not globs, so
**the plugin's own `127.*` default is dropped from git config** while `localhost` and `[::1]`
survive. Loopback by IP is therefore not bypassed for git. Whether that matters in practice is
debatable — git rarely proxies `127.0.0.1` — but it is a real asymmetry with Gradle, which keeps the
globs.

⚠️ **Unconfirmed, stronger concern:** one measurement pass suggested git may ignore `http.noproxy`
altogether rather than merely being glob-blind (an exact-host entry still went through the proxy,
while the `NO_PROXY` environment variable bypassed correctly). Tested on Apple Git-155 / 2.50.1 only.
If it reproduces on another git build, the `http.noproxy` writes are dead weight and should be
reconsidered. Not filed as a defect until confirmed — see [[Defect Register]].

## Credentials

The proxy URL is built by `ProxyUrlBuilder` with RFC 3986 userinfo encoding, **not** `URLEncoder`
form encoding — a space must become `%20`, never `+`. Details and rationale in
[[Credential Handling]].

Logging discipline: no `LOG.*` call in `GitProxyConfigurer` interpolates the URL, username or
password (✅ audited, all 7 call sites). One residual channel remains — git's `stderr` is logged
verbatim on non-tolerated failure (`:179`) and reused as the thrown exception message (`:180`). In
practice git does not echo the value argument of `config`, so this is a footnote, not a leak. See
[[Credentials In Log]].

## Cleanup

Disabling the proxy, or unticking "Apply proxy settings to Git", removes both keys from the scope the
plugin wrote to. `GitProxyService.kt` performs the removal even when the feature is disabled, so
turning the feature off cleans up rather than orphaning config.

## Related

[[GitProxyConfigurer]] · [[Credential Handling]] · [[Global Git Config Deleted]] ·
[[Gradle Properties Management]] · [[Multi-Project Behaviour]]

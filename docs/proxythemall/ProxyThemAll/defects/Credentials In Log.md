---
type: defect
status: fixed
severity: critical
tags: [security, logging, credentials, git]
verified: 2026-08-12
verified-against: efb35cc
---

# Credentials In Log

The proxy password was written to `idea.log` in cleartext.

## Symptom

Nothing, to the user. That is the problem. `idea.log` contained lines like:

```
INFO - GitProxyConfigurer - Git proxy configured globally: http://user:p%40ss%20word%2F1@proxy.corp:3128
```

`idea.log` is what users attach to bug reports, paste into issue trackers, and hand to support. The
credential leaves the machine without anyone noticing it was ever in the file.

## Root cause

`233c282:src/main/kotlin/.../git/GitProxyConfigurer.kt:95` logged the constructed proxy URL directly:

```kotlin
LOG.info("Git proxy configured globally: $proxyUrl")
```

`proxyUrl` comes from `ProxyUrlBuilder.buildProxyUrl(proxyInfo)`, whose entire job is to embed
percent-encoded credentials: `http://user:password@host:port`. Percent-encoding is not obfuscation —
`p%40ss` is trivially `p@ss`.

The variable name is the trap. `proxyUrl` reads as an address; nothing at the log site says "this
string contains a secret". The author of the log line and the author of the builder were reasoning
about different objects with the same name.

## Why it survived

- The log line was almost certainly added while debugging the URL-encoding work, where seeing the
  exact URL is precisely what you want. It then shipped, because a debug aid that is spelled
  `LOG.info` looks like intentional production logging.
- No log-scanning step existed in the review or test process. Nothing greps the sandbox log, so a
  secret in a log is invisible to every automated gate.
- Unit tests never observe logging. `ProxyUrlBuilderTest` asserts the URL is built *correctly* —
  which makes the leak worse, not better: the tests confirm the credential is in the string.

## Fix

`src/main/kotlin/org/holululu/proxythemall/services/git/GitProxyConfigurer.kt:80-81` — log host and
port only, with the reason stated at the line so it does not regress:

```kotlin
// Never log proxyUrl - it embeds the credentials
LOG.info("Git proxy configured ($target): ${proxyInfo.host}:${proxyInfo.port}")
```

The removal path logs no URL at all (`GitProxyConfigurer.kt:130`). Other sites were audited and
already log host:port only — `GradleProxyConfigurer.kt:83`, `ProxyRestoreService.kt:60`,
`ProxyRestoreService.kt:130`, `ProxyCredentialsStorage.kt:92`.

## Residual risk — still present on `main`

`GitProxyConfigurer.kt:177-181` logs the raw stderr of a failed git command:

```kotlin
val errorMessage = "Git command failed with exit code ${processOutput.exitCode}: ${processOutput.stderr}"
LOG.warn(errorMessage)
```

The failing command may be `git config http.proxy http://user:pass@host:port`, and git echoes the
offending argument in several of its error messages. ⚠️ inferred — not reproduced, but the argument
list is definitely in scope for git's own diagnostics. A failing `git config` is rare, which is why
this was accepted rather than fixed; it is listed in [[Defect Register]] under open risks.

## Regression guard

**None automated.** Manual: `docs/VERIFICATION.md` §4.3 "Credentials never reach the log"

```bash
grep -iE "p@ss|password@|:.*@proxy" .intellijPlatform/sandbox/ProxyThemAll/IC-*/log/idea.log
```

and a whole-log final sweep in §10.3 (`grep -iE "p@ss|password@"`). The test password in §0.3 is
deliberately `p@ss word/1` — chosen so a single grep catches both a leak and a mis-encoding.

This is greppable and cheap; the reason it isn't in CI is that the sandbox log only exists after a
`runIde` session. A CI job that boots the sandbox headlessly and greps the log would close it.

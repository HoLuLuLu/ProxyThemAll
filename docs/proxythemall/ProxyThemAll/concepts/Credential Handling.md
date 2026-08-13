---
type: concept
status: current
tags: [security, credentials, passwordsafe, logging, encoding]
verified: 2026-08-13
verified-against: efb35cc
---

# Credential Handling

Where the proxy username and password live, how they are encoded, and every place they can leak.
Per the vault rules, every claim below cites the exact line that writes, encodes or logs.

## The four places credentials exist

| Location | Encrypted? | Written by |
|---|---|---|
| IDE credential store (`ProxyCredentialStore`) | yes, platform-managed | `ProxyRestoreService.kt:154-159` |
| PasswordSafe backup (JSON blob) | yes, OS-native store | `ProxyCredentialsStorage.kt:101` |
| `git config http.proxy` (URL userinfo) | **no** — plain text in `.git/config` | `GitProxyConfigurer.kt:62` |
| `gradle.properties` | **no** — plain text on disk | `GradlePropertiesText.kt:181-184` |

Two of four are cleartext. One is accepted deliberately; see below and
[[Plaintext Gradle Credentials]].

## PasswordSafe backup

`services/ProxyCredentialsStorage.kt`. The whole proxy configuration — not just the secret — is
serialised to JSON and stored **in the password field** of a single credential entry.

```kotlin
val credentials = Credentials(PROXY_BACKUP_KEY, jsonString)   // :99
PasswordSafe.instance.set(credentialAttributes, credentials)  // :101
```

- payload shape: `StoredProxyConfig(host, port, username, password, type, nonProxyHosts)` — `:48-56`,
  serialised at `:61-70`, parsed at `:75-85`
- `Json { prettyPrint = false; ignoreUnknownKeys = true }` (`:40-43`) — forward-compatible reads
- service name: `generateServiceName("ProxyThemAll", "proxy.backup")` — `:197-199`, constants at
  `:28-29`

✅ verified with `javap`: `generateServiceName` is pure concatenation with the constant
`"IntelliJ Platform  — "`, so the keychain entry name is the fixed, guessable string
`IntelliJ Platform ProxyThemAll — proxy.backup`. No salt. The **contents** are protected by the OS
store (macOS Keychain / Windows Credential Manager); the *name* is not a secret and was never meant
to be.

**Username and password go into the JSON**, so the backup is a second copy of the credential, not a
pointer to the first. That is the point of the feature — surviving the IDE forgetting its own proxy —
but it means clearing the IDE proxy does not clear the backup. `clearStoredConfiguration()` (`:161-170`)
is the only deletion path, reachable from Settings (`ProxyThemAllConfigurable.kt:85-120`), and the
listener deliberately never deletes on disable (`HttpProxySettingsChangeListener.kt:105-109`:
"never delete from PasswordSafe").

### `remember = true` is required, twice over

Two different `remember`-style parameters, both load-bearing:

1. **Restoring into the IDE credential store** — `ProxyRestoreService.kt:154-159` passes `true` as the
   fourth argument of `setCredentials(host, port, credentials, remember)`. With `false` the password
   is memory-only and vanishes on restart, defeating the entire backup feature. That was
   [[Restored Password Not Persisted]]; the reason is now written at `:152-153`.

2. **PasswordSafe's own memory-only mode** — if the user configured *"do not save, forget passwords
   after restart"*, the backup silently disappears anyway. `warnIfMemoryOnly()` (`:178-185`) logs a
   one-shot warning, guarded by an `AtomicBoolean` (`:46`, `:179`).

### The warning now fires on reads too

It used to be called only from `saveProxyConfiguration`, so a session that *only read* the backup —
the startup auto-restore path — stayed silent. That is precisely the session where "your backup did
not survive the restart" is the useful diagnosis, so the warning was missing exactly when it mattered.

Fixed by moving the call into `createCredentialAttributes()` (`:194-200`), the one private function
that **all four** public entry points already route through: `saveProxyConfiguration` (`:96`),
`loadProxyConfiguration` (`:116`), `hasStoredConfiguration` (`:147`) and `clearStoredConfiguration`
(`:164`). One line, four paths covered, and no way for a future fifth path to forget it — the KDoc at
`:187-193` records that as the reason the call belongs there rather than at each caller.

The `AtomicBoolean` guard (`:179`) keeps it one warning per IDE session despite the extra call sites.

⚠️ Not verified by execution — `PasswordSafe.instance.isMemoryOnly` needs a live application
(`ProxyCredentialsStorage.kt` is at 30/72 lines covered). Verified by reading the four call paths.

## RFC 3986 userinfo encoding — and why `URLEncoder` is wrong

Git receives credentials inside the proxy URL, so they must be percent-encoded as **userinfo**, not as
a form body.

`utils/ProxyUrlBuilder.kt:62-77`:

```kotlin
private fun urlEncode(value: String): String = buildString {
    for (byte in value.toByteArray(StandardCharsets.UTF_8)) {   // :63
        val char = byte.toInt().toChar()
        if (char.isUnreservedUserInfo()) append(char)            // :65-66
        else append('%').append("%02X".format(byte.toInt() and 0xFF))  // :68
    }
}

private fun Char.isUnreservedUserInfo(): Boolean =
    this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this in "-._~"  // :76-77
```

The reason `java.net.URLEncoder` cannot be used is stated at `:58-60`: **it is form encoding**. A
space becomes `+`, and `+` in a proxy URL authenticates as a literal plus sign, not a space — a
password with a space would silently fail authentication with no error the user can interpret.

Two further correctness points:

- encoding is per **UTF-8 byte** (`:63`), not per char, so `usér` → `us%C3%A9r`
- the allowed set is RFC 3986 *unreserved* only (`-._~`), which is conservative: `:` and `@` are
  escaped, and they are exactly the characters that would otherwise break the
  `type://user:pass@host:port` shape assembled at `:43`

Pinned by `ProxyUrlBuilderTest` — 15 tests including `testBuildProxyUrlWithSpacesInCredentials`
(`user%20name`, not `user+name`), percent, unicode, and the full punctuation sweep.

Credentials are only embedded when **both** username and password are non-blank
(`hasCredentials`, `:52-54`; `ProxyInfo.hasCredentials`, `models/ProxyInfo.kt:35-36`) — a
username-only proxy gets a bare `type://host:port` and git prompts for the password itself.

## Gradle writes credentials in plaintext — accepted risk

`services/gradle/GradlePropertiesText.kt:178-184`:

```kotlin
if (proxyInfo.hasCredentials) {
    // WARNING: these land in plain text on disk. See the Gradle section of the README.
    appendLine(COMMENT_AUTH)
    appendProperty(HTTP_USER, proxyInfo.username)
    appendProperty(HTTP_PASSWORD, proxyInfo.password)
    appendProperty(HTTPS_USER, proxyInfo.username)
    appendProperty(HTTPS_PASSWORD, proxyInfo.password)
}
```

Four cleartext properties. **This is a user decision, not an oversight** — Gradle consumes proxy
credentials as `systemProp.*` properties and offers no encrypted alternative. Full rationale in
[[Plaintext Gradle Credentials]].

Mitigations, all warnings rather than fixes:

| Mitigation | Cite |
|---|---|
| Gradle support **off by default** | `ProxyThemAllSettings.kt:36` (`enableGradleProxySupport = false`) |
| Warning in the generated file itself: `# Proxy Authentication (stored in plain text - do not commit)` | `GradlePropertiesText.kt:49`, emitted at `:180` |
| Warning comment at the write site | `GradlePropertiesText.kt:179` |
| Settings-panel warning | `ProxyThemAllConfigurable.kt:68-74` |
| README warning block | `README.md:50-55` |

**The changelist is not a security boundary.** Both the settings comment
(`ProxyThemAllConfigurable.kt:71-73`) and the README (`README.md:53-54`) say so explicitly: the
ProxyThemAll changelist keeps `gradle.properties` out of *your usual* commits, but "a commit of all
changes will include them". See [[Changelist Integration]].

## Never log the proxy URL

The URL embeds the credentials, so it is never a log argument.

`GitProxyConfigurer.kt:80-81`:

```kotlin
// Never log proxyUrl - it embeds the credentials
LOG.info("Git proxy configured ($target): ${proxyInfo.host}:${proxyInfo.port}")
```

Host and port only. The same discipline elsewhere: `ProxyCredentialsStorage.kt:92` and `:134` log
`host=`/`port=`; `ProxyRestoreService.kt:130`, `:149`, `:176` log host and port; the notification text
says "with authentication" without values (`GitProxyConfigurer.kt:74-78`).

That rule exists because it was once broken — `idea.log` contained
`http://user:p%40ss...@proxy.corp:3128`, and `idea.log` is what users attach to bug reports. See
[[Credentials In Log]].

## Residual leak: git stderr is logged verbatim

`GitProxyConfigurer.kt:177-181`:

```kotlin
if (processOutput.exitCode != 0 && processOutput.exitCode != EXIT_CODE_KEY_MISSING) {
    val errorMessage = "Git command failed with exit code ${processOutput.exitCode}: ${processOutput.stderr}"
    LOG.warn(errorMessage)
    throw RuntimeException(errorMessage)
}
```

The proxy URL is a **command-line argument** (`:62`), so any git failure that echoes the offending
argument back on stderr puts the credential into `idea.log` — and into the thrown exception's
message, which propagates further up. This is the one remaining channel.

⚠️ inferred: whether git echoes the value depends on the failure mode; not reproduced. Mitigating it
would mean redacting `stderr` against the known password before logging. Not done.

Adjacent, same shape: the URL is visible in the OS process table for the duration of the
`git config` invocation, and lands in `.git/config` in cleartext (that is git's storage format, not
the plugin's choice).

## Threading — PasswordSafe must not touch the EDT

Every credential-store access is pushed off the EDT, because the OS keychain call can block:

| Call site | Dispatch | Cite |
|---|---|---|
| `hasStoredConfiguration()` before the config-required balloon | `executeOnPooledThread` | `ProxyController.kt:93-95` |
| backup on enable | `executeOnPooledThread` | `HttpProxySettingsChangeListener.kt:86-101` |
| restore from the notification action | `executeOnPooledThread` | `NotificationMessages.kt:53-56` |
| clear from Settings | `executeOnPooledThread`, result back via `invokeLater` | `ProxyThemAllConfigurable.kt:96-118` |

`ProxyInfoExtractor` reads the credential store while extracting
(`ProxyInfoExtractor.kt:82-84`), which is why extraction is inside the pooled block too
(`HttpProxySettingsChangeListener.kt:84-89`). See [[Lifecycle and Leaks]].

## Round-trip guard

`ProxyCredentialsStorageTest` — 4 tests over `serialize`/`deserialize` only (no PasswordSafe, so they
run without a platform): full configuration, no-credentials, SOCKS type preserved, unknown fields
ignored. The store call itself (`:101`) has no automated guard.

## See also

[[Credentials In Log]] · [[Gradle Properties Management]] · [[Plaintext Gradle Credentials]] ·
[[Restored Password Not Persisted]] · [[Changelist Integration]] · [[Platform API Constraints]] ·
[[GitProxyConfigurer]]

---
type: defect
status: fixed
severity: high
tags: [credentials, passwordsafe, restore, platform-api]
verified: 2026-08-12
verified-against: efb35cc
---

# Restored Password Not Persisted

The restored proxy password was stored memory-only, so it vanished on the next IDE restart —
defeating the entire point of the backup feature.

## Symptom

1. Proxy stops working / IntelliJ forgets it. User invokes **Restore Last Known Proxy Settings**.
2. Balloon: "Proxy Restored". HTTP Proxy settings show host, port **and** a filled password field.
   Everything works.
3. Restart the IDE. Host and port are still there. **The password field is empty.** Authentication
   fails, and the user has to restore again — every single session.

The feature appears to work on the happy path and fails only across the boundary it exists to cross.

## Root cause

`src/main/kotlin/org/holululu/proxythemall/services/ProxyRestoreService.kt:154-159` calls

```kotlin
credentialStore.setCredentials(proxyInfo.host, proxyInfo.port, credentials, /* remember = */ ...)
```

`ProxyCredentialStore.setCredentials` takes a trailing `remember: Boolean`. It was passed `false`.

`remember = false` maps onto PasswordSafe's transient store: the credential lives for the session and
is never written to the persistent backend. Which is precisely the condition
`ProxyRestoreService` exists to repair — the plugin restored the proxy from *its own* durable
PasswordSafe backup and then handed the platform a non-durable copy.

The restore was therefore a strict downgrade of durability: the plugin's own backup
(`ProxyCredentialsStorage`) persists, but what it wrote back into the IDE did not.

## Why it survived

- **The parameter is a bare positional boolean.** At the call site it reads
  `setCredentials(host, port, credentials, false)` — a `false` with no name attached to it. Nothing in
  the expression says what is being denied. This is the whole bug: one unlabelled boolean, four
  arguments deep.
- **`false` is the safe-looking default.** "Don't remember the password" reads as the privacy-conscious,
  conservative choice. In a plugin whose settings panel warns about writing credentials to disk, `false`
  looks like it belongs. The semantics are inverted here: not remembering is the *failure*, because the
  user already consented by asking for a restore.
- **The verification path was too short.** Restore was tested by clicking restore and looking at the
  settings dialog — where the field *is* populated, because the in-memory credential is perfectly
  readable. A restart was needed to see the bug, and a restart is the one step a manual tester skips
  when the feature already visibly works.
- **No test can reach it.** `ProxyCredentialStore` is a platform service; asserting persistence requires
  a real IDE and a restart. `ProxyCredentialsStorageTest` covers the plugin's own serialization
  round-trip and stops at the platform boundary — the exact boundary where this bug lives.

## Fix

`ProxyRestoreService.kt:152-159` — `remember = true`, with the reason written at the call site so the
next reader does not "fix" it back:

```kotlin
// remember = true, otherwise the password is memory-only and the next restart
// loses it again - which is exactly what this backup feature exists to prevent
credentialStore.setCredentials(
    proxyInfo.host,
    proxyInfo.port,
    credentials,
    true
)
```

Related hardening in the same area: `ProxyCredentialsStorage.warnIfMemoryOnly()`
(`ProxyCredentialsStorage.kt:180-187`) logs once per session when PasswordSafe itself is in
memory-only mode, since in that configuration `remember = true` still cannot persist. See
[[Defect Register]] — it currently only fires on save, not on load.

## Regression guard

**None automated.** Persistence across a process restart is not unit-testable here.

Manual: `docs/VERIFICATION.md` §7.5 "Restored password persists across restart" — restore, restart the
sandbox IDE, assert the password field is still filled. §7.4 covers the auto-restore path that feeds it
(delete `proxy.settings.xml`, relaunch).

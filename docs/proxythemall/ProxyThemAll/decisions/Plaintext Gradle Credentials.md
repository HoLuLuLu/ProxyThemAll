---
type: decision
status: current
tags: [security, credentials, gradle, accepted-risk]
verified: 2026-08-12
verified-against: efb35cc
---

# Plaintext Gradle Credentials

**Keep writing the proxy username and password in cleartext to `gradle.properties`. Add warnings.
Do not mitigate.**

This is a **user decision**, taken explicitly after the risk was raised. Recorded here so nobody
"fixes" it later without knowing it was chosen.

## Context

Gradle consumes proxy credentials as JVM system properties in `gradle.properties`:

```properties
systemProp.http.proxyUser=...
systemProp.http.proxyPassword=...
systemProp.https.proxyUser=...
systemProp.https.proxyPassword=...
```

`GradlePropertiesText.buildSection` writes all four (`GradlePropertiesText.kt:181-184`) whenever
`proxyInfo.hasCredentials` (`:178`, defined at `models/ProxyInfo.kt:35-36`).

**Gradle offers no encrypted alternative for these properties.** There is no keystore hook, no
credential-provider indirection for proxy authentication, and no way to point the property at an
external secret. The plaintext form is the interface.

The alternative to writing them is not writing them — which means an authenticated proxy silently fails
for every Gradle build, with a network error the user cannot map back to a cause.

Meanwhile the plugin *does* have a secure store: PasswordSafe holds the same credentials in the OS
keychain (`ProxyCredentialsStorage.kt:101`). So the risk is specifically the **second, cleartext copy**
on disk in a working tree that is usually under version control.

## Decision

Keep writing them. Add warnings at every layer where a user could notice, and leave the feature off by
default.

| Mitigation | Where | Cite |
|---|---|---|
| Gradle support **off by default** | `enableGradleProxySupport = false` | `settings/ProxyThemAllSettings.kt:36` |
| Global fallback also off by default | `enableGradleGlobalFallback = false` | `ProxyThemAllSettings.kt:39` |
| Header in the **generated file itself** | `# Proxy Authentication (stored in plain text - do not commit)` | `GradlePropertiesText.kt:49`, emitted at `:180` |
| Comment at the write site | `// WARNING: these land in plain text on disk. See the Gradle section of the README.` | `GradlePropertiesText.kt:179` |
| Settings-panel warning | *"**Warning:** when the proxy requires authentication, the username and password are written in plain text to gradle.properties. The ProxyThemAll changelist reduces the risk of committing them, but it is not a security boundary - a commit of all changes will include them."* | `settings/ProxyThemAllConfigurable.kt:68-74` |
| README warning block | `> [!WARNING]` … *"Gradle offers no encrypted alternative for these properties … Leave Gradle integration disabled if that is unacceptable; without credentials the plugin writes only host, port and non-proxy hosts."* | `README.md:50-55` |
| Dedicated changelist | keeps the file out of "commit my changes" | `GradleProxyConfigurer.kt:37`, `:305-343` |

Note the three warnings say the same thing in three registers: the generated file warns the person
reading the file, the settings panel warns the person enabling the feature, the README warns the person
evaluating the plugin. That redundancy is intentional — each audience sees exactly one of them.

## Consequences

- **A documented risk, not a mitigated one.** Anyone with read access to the working tree — a backup, a
  sync service, a shared machine, a container mount, a CI cache — reads the proxy password. The
  keychain copy provides no protection to the file copy.
- **`git commit -a` includes the credentials.** The changelist is a speed bump, and both the settings
  text (`ProxyThemAllConfigurable.kt:71-73`) and the README (`README.md:53-54`) say so in those words.
  See [[Changelist Integration]].
- **The global fallback widens the blast radius when enabled**: credentials land in
  `~/.gradle/gradle.properties` (`GradleProxyConfigurer.kt:400-404`), which is outside any repo and
  therefore outside the changelist protection entirely — no VCS, no warning at commit time. Off by
  default (`ProxyThemAllSettings.kt:39`).
- **Escaping becomes a security-adjacent concern.** A password containing a backslash or a trailing
  backslash would corrupt the file and swallow the next key, so `escapePropertyValue`
  (`GradlePropertiesText.kt:210-217`) is on the credential path. Guarded by
  `GradlePropertiesTextTest.backslash and leading space in credentials survive a properties round trip`.
- **Values are still never logged.** The cleartext-on-disk decision does not relax the logging rule —
  see [[Credential Handling]] and [[Credentials In Log]].
- **Users who cannot accept it have a working option**: leave Gradle integration off. Without
  credentials the plugin writes only host, port and non-proxy hosts, plus
  `org.gradle.jvmargs=-Djava.net.useSystemProxies=true` so Gradle picks up the IDE's own
  ProxySelector/Authenticator (`GradlePropertiesText.kt:185-188`). That path is credential-free by
  construction.

Listed as an accepted risk in [[Defect Register]] → *"Gradle credentials in plaintext — accepted, user
decision"*.

## Alternatives rejected

**Write only host and port; rely on the IDE's ProxySelector/Authenticator.** This is exactly what the
no-credentials branch does (`:185-188`), and it is the *default*. Rejected as the only behaviour because
the fallback does not always reach the Gradle daemon — a daemon started outside the IDE, or one that
outlives the IDE session, has no access to the IDE's authenticator, and the build fails on a
407 with nothing pointing at the cause. The credential branch exists for users who hit that.

Note the two are mutually exclusive by construction (`if` / `else if`, `:178` / `:185`): an
authenticated proxy never gets the `useSystemProxies` arg.

**Encrypt the values in `gradle.properties`.** Gradle would read the ciphertext verbatim and send it as
the password. Not an option — the consumer defines the format.

**Store them in the OS keychain and inject at build time.** Would require intercepting Gradle
invocations to add `-DsystemProp...` arguments. Rejected: the plugin does not own the build launch path
(users run Gradle from the IDE, the terminal, and CI), and a partial interception is worse than a
consistent file — some invocations would authenticate and some would not.

**Add the file to `.gitignore` automatically.** Rejected: `gradle.properties` is a legitimate,
usually-committed project file. Ignoring it would hide the user's *own* Gradle settings from VCS —
trading a security warning for silent data loss in a file the plugin does not own.

**Prompt before each write.** Rejected: the write happens on every proxy toggle and on every startup
reapply, across every open project. A modal per write is unusable; the one-time settings warning
carries the same information at the point of consent.

**Refuse to write credentials and show an error.** Rejected by the user: it turns a documented risk into
a broken feature. The warnings make the trade-off explicit and leave the choice with the person who owns
the machine.

## See also

[[Credential Handling]] · [[Gradle Properties Management]] · [[Changelist Integration]] ·
[[Defect Register]] · [[Credentials In Log]]

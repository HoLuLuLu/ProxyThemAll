---
type: defect
status: fixed
severity: medium
tags: [gradle, socks, jdk, proxy-selector, test-quality]
verified: 2026-08-13
verified-against: efb35cc
---

# SOCKS Bypass Hosts Ignored

With a SOCKS proxy, the user's non-proxy host exceptions had no effect on Gradle builds. The plugin
wrote a bypass list the JDK never consults for socket connections.

> Fixed on `main` in `efb35cc`.

## Symptom

SOCKS proxy active, Gradle integration on, and the user has `repo.internal.corp` in the IDE's proxy
exception list. A build that resolves from that internal repository should go **direct**. Instead it
is routed through the SOCKS proxy — and since the proxy has no route to an internal host, the build
either hangs on connect or fails outright with a refused connection.

The user's mental model ("I excluded it, so it goes direct") and the observable behaviour disagree,
with no error message naming the exception list.

## Root cause

The bypass list was written **outside** the `isSocks` branch of `buildSection`, unconditionally
emitting the two HTTP-family keys:

```kotlin
// before the fix
val nonProxyHosts = proxyInfo.bypassHosts.joinToString(HOSTS_SEPARATOR)
appendLine(COMMENT_NON_PROXY)
appendProperty(HTTP_NON_PROXY_HOSTS, nonProxyHosts)    // systemProp.http.nonProxyHosts
appendProperty(HTTPS_NON_PROXY_HOSTS, nonProxyHosts)   // systemProp.https.nonProxyHosts
```

So a SOCKS section carried `socksProxyHost` / `socksProxyPort` plus an `http.nonProxyHosts` list.

**The JDK keeps a separate bypass list for socket-level connections.** ✅ Verified by reading the JDK
source, not inferred from property names:

```
$JAVA_HOME/lib/src.zip → java.base/sun/net/spi/DefaultProxySelector.java

:137   static NonProxyInfo socksNonProxyInfo = new NonProxyInfo("socksNonProxyHosts", null, null, defStringVal);
:85    {"socket", "socksProxy"}
:200-201   } else if ("socket".equalsIgnoreCase(protocol)) {
               pinfo = NonProxyInfo.socksNonProxyInfo;
```

`select(URI)` dispatches on the URI scheme: `http` → `httpNonProxyInfo`, `socket` →
`socksNonProxyInfo` (`:200-201`). The lists are unrelated. `http.nonProxyHosts` is therefore
**never** consulted for a socket connection, which is what a SOCKS proxy is.

Note on evidence: `$JAVA_HOME/conf/net.properties` documents `http.nonProxyHosts` and friends but
✅ does **not** mention `socksNonProxyHosts` at all (`grep -c socksNonProxyHosts` → 0). The JDK
source is the only documentation, which is a large part of why the key is easy to miss.

## The two-layer mechanism — why this is real, not theoretical

An objection worth answering: Gradle uses Apache HttpClient, which handles proxying itself, so does
the JDK's `ProxySelector` even matter? It does, because **there are two layers and both consult it**.

Gradle's `HttpClientConfigurer` installs `SystemDefaultRoutePlanner(ProxySelector.getDefault())` —
i.e. it delegates routing decisions to the same JDK selector read above.

| Layer | Queries | With only `http.nonProxyHosts` set |
|---|---|---|
| 1. Apache route planner | `http://host` → `httpNonProxyInfo` | ✅ correctly decides **DIRECT** |
| 2. plain `Socket.connect` underneath | `socket://host` → `socksNonProxyInfo` | ❌ **re-proxied** through SOCKS |

Layer 1 does the right thing and layer 2 undoes it. The route planner says "no proxy for this host",
hands back a direct route, and then the socket that actually carries that direct route asks the
selector again under the `socket` scheme, gets the SOCKS proxy, and tunnels through it anyway. The
bug is invisible at the layer where you would look for it.

✅ **Reproduced against a real server** with a deliberately dead SOCKS proxy configured:

| Configuration | Result |
|---|---|
| `socksProxyHost` set, only `http.nonProxyHosts` listing the host | `SocketException: Can't connect to SOCKS proxy` |
| same, plus `socksNonProxyHosts` listing the host | **HTTP 200**, connection direct |

That is the whole defect in two runs: the exception list only takes effect once the socket-level key
is present.

## Scope — stated honestly

**localhost and friends still bypassed even with the bug present.** `NonProxyInfo.defStringVal` is
`"localhost|127.*|[::1]|0.0.0.0|[::0]"` (`DefaultProxySelector.java:129`) and the JDK **appends its
own defaults**, so local traffic was never routed through the SOCKS proxy regardless.

What was lost is exactly **the user's own exception list** — the entries they configured in the IDE
because they know something about their network that the JDK does not. That is the part with no
substitute and no workaround.

This also corrects the earlier register entry, which claimed "traffic to `localhost` and friends
goes through the proxy anyway". That was wrong in the user-visible direction — it overstated the
symptom while understating what actually mattered.

## Fix

Two parts, and the second is not optional.

**1. Write the key in the `isSocks` branch** (`GradlePropertiesText.kt:183-193`):

```kotlin
val nonProxyHosts = proxyInfo.bypassHosts.joinToString(HOSTS_SEPARATOR)
appendLine(COMMENT_NON_PROXY)
if (proxyInfo.isSocks) {
    // The JDK reads socksNonProxyHosts for socket connections; the http key would be ignored
    appendProperty(SOCKS_NON_PROXY_HOSTS, nonProxyHosts)          // :187
} else {
    appendProperty(HTTP_NON_PROXY_HOSTS, nonProxyHosts)           // :189
    // https reuses the http list in the JDK; written for readability, not effect
    appendProperty(HTTPS_NON_PROXY_HOSTS, nonProxyHosts)          // :191
}
```

`SOCKS_NON_PROXY_HOSTS = "systemProp.socksNonProxyHosts"` (`:35`), with the reason recorded at
`:31-34`.

**2. Register it in `OWN_KEYS`** (`:43-48`) — this is a removal-correctness fix, not tidiness:

`isOwnLine` (`:141-155`) drives deletion, and its final decision is `OWN_KEYS.contains(key)`
(`:154`). A key the plugin *writes* but does not *claim* is classified as a foreign user line — and
`removeManagedSection` **rescues** foreign lines by design, re-inserting them where the block was
(`:122`, `:125`). So without the registration, disabling the proxy would leave
`systemProp.socksNonProxyHosts=...` behind in `gradle.properties`, and it would survive every
subsequent disable, forever, looking exactly like a line the user wrote.

The two-part shape is the invariant the file states in a comment at `:41-42`: *"every key
buildSection writes must be listed here - an unlisted key is treated as a user's own line and
preserved on removal."* See [[Gradle Properties Management]] §2.

## Why it survived — a test that looked like coverage

`GradlePropertiesTextTest.socks proxy is written as socks system properties` existed and passed. It
asserts:

- `systemProp.socksProxyHost` == the host
- `systemProp.socksProxyPort` == the port
- `systemProp.http.proxyHost` is **absent** ("SOCKS must not be written as an HTTP proxy")

It never inspects the bypass list. **It passed with the bug present**, and it passes after the fix,
unchanged — it is insensitive to the defect in both directions.

This is the instructive failure mode: the test is *about SOCKS*, it is named for SOCKS, it lives
where you would look for SOCKS coverage, and it even checks for HTTP-key leakage — which reads as
thoroughness. What it actually pins is one half of the emission (host/port) while the other half
(the bypass list) was outside its assertions entirely. A green test in the right file is not
evidence that the file's behaviour is covered; only the assertions are evidence, and only for what
they name.

Compare [[Change Detection Enum Only]]: there, correct code sat below the gate that swallowed the
event. Here, a correct test sat beside the property that was wrong. Both look right in isolation.

## Regression guard

**Yes** — three new tests in
`src/test/kotlin/org/holululu/proxythemall/services/gradle/GradlePropertiesTextTest.kt`
(✅ names verified present in the file):

| Test | Guards |
|---|---|
| `socks proxy writes the socks bypass list` (`:111`) | the key is emitted, and contains both the user's exception and the essential hosts |
| `the socks bypass list is removed again on disable` (`:125`) | the `OWN_KEYS` registration — asserts `removeManagedSection` round-trips a SOCKS section byte for byte, so an unregistered key fails here |
| `an http proxy does not write the socks bypass list` (`:141`) | the branch does not leak the SOCKS key into an HTTP section |

The second test is the one that would have caught part 2 of the fix being forgotten, and it is
deliberately written as a byte-for-byte round trip rather than a `contains` check.

Manual: `docs/VERIFICATION.md` **§5.8b "SOCKS bypass hosts use the SOCKS key"**, which also checks
the removal half against a real file. See [[Manual Verification]].

## See also

[[GradlePropertiesText]] · [[Gradle Properties Management]] · [[Defect Register]] ·
[[Change Detection Enum Only]] · [[GitProxyConfigurer]]

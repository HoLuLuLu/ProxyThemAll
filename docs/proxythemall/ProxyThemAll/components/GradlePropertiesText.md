---
type: component
status: current
tags: [gradle, text-processing, data-loss, testability]
verified: 2026-08-13
verified-against: efb35cc
---

# GradlePropertiesText

`src/main/kotlin/org/holululu/proxythemall/services/gradle/GradlePropertiesText.kt`

Pure text layer for the managed `gradle.properties` section. A Kotlin `object` (`:11`) with **no
IntelliJ and no file system imports** — the only import is `ProxyInfo` (`:3`). That is the entire
point: the data-loss scenarios (marker on the first line, repeated applies, user properties adjacent
to the markers) become directly unit-testable (`:5-10`). File I/O, VFS and changelist handling live in
`GradleProxyConfigurer`, which calls in at `GradleProxyConfigurer.kt:191`, `:210`, `:418`, `:434`.

## Markers

```
# === ProxyThemAll Managed Proxy Settings - START ===   // :13
# === ProxyThemAll Managed Proxy Settings - END ===     // :14
```

Both are `const val` and public (`:13-14`). Section detection is anchored strictly on these, compared
after `trim()` (`:111`, `:113`).

## The 13 OWN_KEYS

`OWN_KEYS` — `:43-48`, built from the constants at `:22-39`:

| Key | Const | Line |
|---|---|---|
| `systemProp.socksProxyHost` | `SOCKS_HOST` | `:22` |
| `systemProp.socksProxyPort` | `SOCKS_PORT` | `:23` |
| `systemProp.socksNonProxyHosts` | `SOCKS_NON_PROXY_HOSTS` | `:35` |
| `systemProp.http.proxyHost` | `HTTP_HOST` | `:24` |
| `systemProp.http.proxyPort` | `HTTP_PORT` | `:25` |
| `systemProp.https.proxyHost` | `HTTPS_HOST` | `:26` |
| `systemProp.https.proxyPort` | `HTTPS_PORT` | `:27` |
| `systemProp.http.nonProxyHosts` | `HTTP_NON_PROXY_HOSTS` | `:28` |
| `systemProp.https.nonProxyHosts` | `HTTPS_NON_PROXY_HOSTS` | `:29` |
| `systemProp.http.proxyUser` | `HTTP_USER` | `:36` |
| `systemProp.http.proxyPassword` | `HTTP_PASSWORD` | `:37` |
| `systemProp.https.proxyUser` | `HTTPS_USER` | `:38` |
| `systemProp.https.proxyPassword` | `HTTPS_PASSWORD` | `:39` |

`systemProp.socksNonProxyHosts` is the newest entry (`:35`), added with the fix for
[[SOCKS Bypass Hosts Ignored]]. Its constant carries the reason inline (`:31-34`): the JDK consults a
**separate** bypass list for socket-level connections, so the http key is ignored for SOCKS.

`org.gradle.jvmargs` (`JVM_ARGS_KEY`, `:16`) is deliberately **not** in the set — it is handled by a
conditional rule below. Eight own comment lines are tracked separately in `OWN_COMMENTS`
(`:60-63`).

Both `buildSection` and `isOwnLine` read the same constants, so ownership detection cannot drift from
what is actually written (`:20-21`).

### The registration rule — writing a key is only half of adding one

Stated in the code at `:41-42`:

> *"Every key here must also be written by buildSection, and every key buildSection writes must be
> listed here - an unlisted key is treated as a user's own line and preserved on removal."*

Both directions bite, and the second is the dangerous one. `isOwnLine` (`:141-155`) drives **removal**,
and `removeManagedSection` *rescues* anything it does not recognise (`:122`, `:125`). So a key the
plugin writes but does not claim is misclassified as a foreign user line and survives disabling the
proxy — **forever**, since every subsequent removal rescues it again. It ends up looking exactly like a
line the user wrote by hand.

That is precisely the second half of the [[SOCKS Bypass Hosts Ignored]] fix, and the reason
`the socks bypass list is removed again on disable` is written as a byte-for-byte round trip rather
than a `contains` check.

## Ownership rules — `isOwnLine`

`:141-155`. A line may be deleted only if it is one the plugin writes:

| Rule | Line |
|---|---|
| blank (after trim) → **OWNED** | `:145` |
| equals `SECTION_START` / `SECTION_END` → owned | `:146` |
| in `OWN_COMMENTS` → owned | `:147` |
| key `org.gradle.jvmargs` → owned **only if** value trims to `-Djava.net.useSystemProxies=true` | `:152` |
| key in `OWN_KEYS` → owned | `:154` |
| anything else → foreign, rescued | — |

**Document this:** blank lines count as owned (`:145`), so a stray blank line a user leaves inside the
managed block *is* eaten on the next rewrite. The comment at `:143-144` records the trade-off as
deliberate: blank lines are formatting, not content worth rescuing.

The `jvmargs` rule (`:150-152`) exists because a user setting such as `-Xmx4g` must never be treated
as the plugin's. `PROXY_SELECTOR_ARG` is `-Djava.net.useSystemProxies=true` (`:17`).

## Foreign-line rescue

`removeManagedSection` — `:99-133`. Per marker pair:

1. locate `START` (`:111`), then `END` searched from `start` forward (`:113`)
2. widen the range upward by one line if the line above `START` is blank (`:119`) — that is the
   separator `withProxySection` inserts (`:82-83`), and only when it really is blank
3. `foreign = lines.subList(from, to + 1).filterNot { isOwnLine(it) }` (`:122`)
4. remove the whole range (`:124`), then re-insert `foreign` at the same index (`:125`)
5. loop until no `START` remains (`:110`), which handles duplicated sections from earlier bugs

Anything the user added inside the block — a property or a comment of their own — therefore survives,
in place of the block (`:89-94`). `if (!changed) return content` (`:129`) makes a no-op byte-exact.
Trailing-newline handling: recorded at `:103`, the trailing empty element is dropped at `:107` and the
separator restored at `:132`.

The KDoc also now records the normalisation side effect (`:96-97`): when this rewrites the file it
joins every line with **one** separator, so a file with mixed endings comes back uniform. That note is
what makes the `detectLineSeparator` behaviour below a deliberate choice rather than an oversight.

## Escaping

`escapePropertyValue` — `:227-234`. Order matters:

```
"\\" → "\\\\"   // :229  backslash FIRST, so later escapes are not double-escaped
"\n" → "\\n"    // :230
"\r" → "\\r"    // :231
"\t" → "\\t"    // :232
leading " "     → prefix with "\"   // :233
```

Without the backslash-first rule a password containing a backslash loses characters, and one ending
in a backslash line-continues and swallows the following key (`:223-225`). All values go through
`appendProperty` (`:217-219`), so hosts, ports, host lists and credentials are all escaped.

## Line separator detection — any CRLF, deliberately

```kotlin
private fun detectLineSeparator(content: String): String =
    if (content.contains("\r\n")) "\r\n" else "\n"   // :245-246
```

**Any** single `\r\n` anywhere in the file switches the whole rewrite to CRLF. The KDoc (`:236-244`)
now states that rule as written and explains why it is not a "dominant separator" vote:
`removeManagedSection` rebuilds the file with `joinToString(separator)` (`:131`), so a mixed-ending
file is normalised to one separator **whichever way this decides**. Counting would only change *which*
lines get rewritten, not whether the file gets normalised — so it would not fix the mixed case either.
Genuinely preserving mixed endings needs per-line tracking, which is more machinery than a cosmetic
diff is worth.

The code is unchanged; only the doc was wrong before, and it claimed "dominant". Resolved as doc-only
in [[Defect Register]]. `GradlePropertiesTextTest.crlf line separators are preserved` still covers the
uniform-CRLF case only.

## Section content

`buildSection(proxyInfo, includeJvmArgs)` — `:160-209`:

- SOCKS branch writes `socksProxyHost` + `socksProxyPort` only (`:166-170`) — no `http.*` /
  `https.*` keys at all
- HTTP branch writes host+port under both `http.` and `https.` prefixes (`:171-181`)
- **the bypass list is now inside the same branch split** (`:183-193`), joined with
  `HOSTS_SEPARATOR = "|"` (`:18`) and sourced from `proxyInfo.bypassHosts` — user exceptions **plus**
  `ESSENTIAL_BYPASS_HOSTS` (`ProxyInfo.kt:23`, `:29-30`):

  | Proxy | Bypass key(s) written |
  |---|---|
  | SOCKS | `systemProp.socksNonProxyHosts` only (`:187`) |
  | HTTP | `systemProp.http.nonProxyHosts` (`:189`) + `systemProp.https.nonProxyHosts` (`:191`) |

  It used to be emitted unconditionally as the two http-family keys, which meant a SOCKS user's
  exceptions were silently ignored — [[SOCKS Bypass Hosts Ignored]]. The `https` key is inert (the JDK
  maps https onto the http list) and kept only for readability, per the comment at `:190`.

  Gradle keeps globs like `127.*` / `10.*` verbatim; contrast [[GitProxyConfigurer]], which filters
  them out
- credentials branch writes user+password for both prefixes, in plain text on disk (`:195-201`,
  warning at `:196`)
- otherwise, when `includeJvmArgs`, writes `org.gradle.jvmargs=-Djava.net.useSystemProxies=true`
  (`:202-204`)

`withProxySection` (`:71-84`) sets `includeJvmArgs = !hasOwnJvmArgs(base)` (`:77`) — the plugin
manages that key only when the user has not set it themselves, because a duplicate key in a
`.properties` file wins and would silently drop their `-Xmx` settings (`:75-76`). `hasOwnJvmArgs`
scans with `trimStart()` (`:214-215`), checked against the content *after* section removal (`:73`,
`:77`).

Note credentials and jvmargs are mutually exclusive (`if/else if`, `:195`/`:202`): an authenticated
proxy never gets the `useSystemProxies` arg.

## See also

[[Gradle Properties Management]] · [[Defect Register]] · [[GitProxyConfigurer]] ·
[[SOCKS Bypass Hosts Ignored]]

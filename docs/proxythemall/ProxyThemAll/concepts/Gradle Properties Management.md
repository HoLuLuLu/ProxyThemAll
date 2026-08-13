---
type: concept
status: current
tags: [gradle, properties, data-loss, vfs, document, ownership]
verified: 2026-08-13
verified-against: efb35cc
---

# Gradle Properties Management

The contract governing the plugin's managed section in `gradle.properties`. `gradle.properties` is a
**user-owned file the plugin edits**, which makes every rule here a data-loss rule.

Two layers, deliberately separated:

| Layer | File | Knows about |
|---|---|---|
| Text | `services/gradle/GradlePropertiesText.kt` | strings only — no IntelliJ, no file system (only import is `ProxyInfo`, `:3`) |
| I/O | `services/gradle/GradleProxyConfigurer.kt` | files, VFS, documents, changelists, threads |

The split exists so the data-loss scenarios are unit-testable without a platform
(`GradlePropertiesText.kt:5-10`). See [[GradlePropertiesText]] for the per-line reference.

## The managed-section contract

### 1. Markers delimit, and nothing else does

```
# === ProxyThemAll Managed Proxy Settings - START ===   // :13
# === ProxyThemAll Managed Proxy Settings - END ===     // :14
```

Public `const val`, matched after `trim()` (`:111`, `:113`). No heuristics, no key sniffing outside
the markers. `removeManagedSection` loops until no `START` remains (`:110`), which cleans up duplicated
sections left by the earlier [[Gradle Block Duplication]] bug.

The section is always **appended at the end** (`withProxySection`, `:71-84`): remove first (`:73`),
then append (`:82-83`). It never tries to edit in place.

### 2. Ownership is per line, not per range

The rule, stated in the code at `:89-94`: *silently discarding a user's edit is worse than an untidy
file.*

`isOwnLine` (`:141-155`) decides deletability:

| Line | Owned? |
|---|---|
| blank after trim | **yes** — treated as formatting (`:145`) |
| `SECTION_START` / `SECTION_END` | yes (`:146`) |
| one of the 8 `OWN_COMMENTS` | yes (`:147`) |
| `org.gradle.jvmargs=` | **only if** the value trims to `-Djava.net.useSystemProxies=true` (`:152`) |
| key in the 13 `OWN_KEYS` | yes (`:154`) |
| anything else | **no — foreign** |

#### The invariant: every key written must be in `OWN_KEYS`

`OWN_KEYS` and `OWN_COMMENTS` are built from the same constants `buildSection` writes
(`:20-21`, `:43-48`, `:60-63`), so ownership cannot drift from emission. The code states the rule in
both directions at `:41-42`: *"Every key here must also be written by buildSection, and every key
buildSection writes must be listed here."*

The consequence of breaking it is not cosmetic. `isOwnLine` drives **removal**, and
`removeManagedSection` *rescues* whatever it does not recognise (`:122`, `:125`) — so a key the plugin
writes but does not claim is classified as a **foreign user line and preserved**. Disabling the proxy
leaves it behind, and every later removal rescues it again: it survives forever, indistinguishable
from a line the user typed.

That is exactly the second half of the [[SOCKS Bypass Hosts Ignored]] fix — adding
`systemProp.socksNonProxyHosts` to `buildSection` (`:187`) was useless, and actively harmful, without
also adding it to `OWN_KEYS` (`:44`). A dedicated test pins it:
`the socks bypass list is removed again on disable`, written as a byte-for-byte round trip.

Note the one deliberate loss: a stray blank line a user leaves inside the block is eaten
(`:145`, rationale `:143-144`).

### 3. Foreign-line rescue

`removeManagedSection` (`:99-133`), per marker pair:

1. find `START` (`:111`), then `END` from `start` forward (`:113`)
2. widen upward by one line if the line above `START` is blank (`:119`) — that is the separator
   `withProxySection` inserts (`:82-83`), and **only** when it really is blank, never a property
3. `foreign = lines.subList(from, to + 1).filterNot { isOwnLine(it) }` (`:122`)
4. delete the range (`:124`), re-insert `foreign` at the same index (`:125`)

So a user property or comment written *inside* the block survives, in place of the block. Deleting
the whole range instead was [[Foreign Lines Destroyed]] (a).

`if (!changed) return content` (`:129`) makes a no-op byte-exact — no gratuitous rewrite, no spurious
VCS change.

### 4. Escaping is mandatory and ordered

`escapePropertyValue` (`:227-234`), applied to every emitted value via `appendProperty` (`:217-219`):

```
"\\" → "\\\\"    // :229  FIRST — otherwise later escapes get double-escaped
"\n" → "\\n"     // :230
"\r" → "\\r"     // :231
"\t" → "\\t"     // :232
leading " " → prefixed with "\"   // :233
```

The failure modes it prevents (✅ executed against `java.util.Properties`, see
[[Platform API Constraints]] §7): a value ending in `\` **line-continues and swallows the next key**;
an unescaped leading space is stripped; a duplicate key later in the file **wins**.

### 5. The user's `org.gradle.jvmargs` is never touched

`withProxySection` sets `includeJvmArgs = !hasOwnJvmArgs(base)` (`:77`), where `base` is the content
*after* section removal (`:73`) and `hasOwnJvmArgs` scans with `trimStart()` (`:214-215`).

Because a later duplicate key wins, appending the plugin's own `org.gradle.jvmargs` silently dropped a
user's `-Xmx4g` → OOM. That was [[JvmArgs Clobbered]]. Two independent guards now: the write-side
condition (`:77`) and the value-gated ownership rule (`:152`).

Note credentials and jvmargs are mutually exclusive (`if` / `else if`, `:195` / `:202`): an
authenticated proxy never gets the `useSystemProxies` argument.

### 6. CRLF is preserved

`detectLineSeparator` (`:245-246`) picks the separator, the section is built with `\n` and rewritten
(`:78`), the rebuild joins with it (`:131`), and the trailing newline is recorded (`:103`) and restored
(`:132`). A CRLF checkout does not become a whole-file diff (`:68-70`).

The rule is **any CRLF**, not a dominant-separator vote: one stray `\r\n` makes the whole rewrite
CRLF. That is deliberate and the KDoc now says so (`:236-244`) — because the rebuild joins every line
with one separator (`:131`), a mixed-ending file is normalised whichever way the detection goes, so
counting would change only *which* lines get rewritten, not the normalisation. `removeManagedSection`
documents the same side effect (`:96-97`). Previously the KDoc claimed "dominant" and contradicted the
code; resolved as **doc-only**, code unchanged — see [[Defect Register]].

### 7. SOCKS and HTTP write different keys

`buildSection` (`:160-209`), and the split covers the **bypass list too**:

| Proxy | Address keys | Bypass key(s) |
|---|---|---|
| SOCKS (`ProxyInfo.isSocks`, `models/ProxyInfo.kt:41-42`) | `systemProp.socksProxyHost`, `systemProp.socksProxyPort` **only** (`:166-170`) | `systemProp.socksNonProxyHosts` (`:187`) |
| HTTP | host + port under **both** `http.` and `https.` prefixes (`:171-181`) | `systemProp.http.nonProxyHosts` (`:189`) + `systemProp.https.nonProxyHosts` (`:191`) |

**SOCKS and HTTP need different bypass keys, and this is the non-obvious part.** ✅ The JDK keeps two
unrelated lists: `DefaultProxySelector` dispatches the `http` scheme to `httpNonProxyInfo` and the
`socket` scheme to `socksNonProxyInfo` (`socksNonProxyHosts`) — verified in
`$JAVA_HOME/lib/src.zip`, `java.base/sun/net/spi/DefaultProxySelector.java:137`, `:200-201`. So
`http.nonProxyHosts` is *never* consulted for a SOCKS connection.

Emitting the bypass list unconditionally as the http-family keys meant a SOCKS user's own exception
list had no effect at all — [[SOCKS Bypass Hosts Ignored]], fixed by moving the write into the branch
and registering the new key in `OWN_KEYS` (see §2).

One wrinkle worth not "fixing": `systemProp.https.nonProxyHosts` (`:191`) is **inert** — the JDK maps
https onto the http list. It is written for readability only (comment at `:190`) and is in `OWN_KEYS`,
so it is removed cleanly. Logged under *Documented but harmless* in [[Defect Register]].

### 8. Globs are allowed here, unlike git

The bypass value is `proxyInfo.bypassHosts` joined with `|` (`HOSTS_SEPARATOR`, `:18`, `:183`) —
the user's exceptions **plus** `ESSENTIAL_BYPASS_HOSTS = setOf("localhost", "127.*", "[::1]")`
(`models/ProxyInfo.kt:23`, `:29-30`).

The JVM's `nonProxyHosts` accepts `*` wildcards, so `127.*` and a user's `*.internal` are written
verbatim. [[GitProxyConfigurer]] filters exactly those entries out
(`GitProxyConfigurer.kt:98-102`) because git cannot match them. **The two integrations diverge here
by design** — do not "unify" the two host lists.

## The read/write symmetry rule

This is the I/O-layer invariant, and it is the one that caused silent data loss.

`GradleProxyConfigurer.writeThroughChangelist` — `:229-281`:

```kotlin
val document = documentManager.getCachedDocument(virtualFile)   // :255

if (document != null) {
    val newContent = transform(document.text)                    // :261  read: Document
    WriteCommandAction.runWriteCommandAction(project) {
        document.setText(newContent)                             // :263  write: Document
        documentManager.saveDocument(document)                   // :264
    }
} else {
    val newContent = transform(VfsUtilCore.loadText(virtualFile)) // :269  read: VFS
    WriteCommandAction.runWriteCommandAction(project) {
        VfsUtil.saveText(virtualFile, newContent)                // :271  write: VFS
    }
}
```

**Read and write must use the same layer.** Reading the file from disk and writing through the VFS
loses unsaved editor edits twice over: the read misses them, and the write is then overwritten when
the still-dirty document is saved. That was [[Foreign Lines Destroyed]] (b); the reason is now stated
at `:258-260`.

Both branches funnel through the single `transform: (String) -> String` parameter (`:233`), so the add
and remove paths cannot diverge in how they read — `withProxySection` at `:210`,
`removeManagedSection` at `:191`.

Supporting facts:

- `VfsUtilCore.loadText` replaced `File.readText()` so read and write agree on **charset** as well as
  content (`:268`). It needs no read action — ✅ verified in [[Platform API Constraints]] §5.
- `document.setText(...)` must be a method call, not `document.text = ...` — see
  [[Non Compiling Commit]] and the `@Suppress` at `:225-228`.

## Which file, and the no-project fallback

| Situation | Target | Cite |
|---|---|---|
| Gradle project (any of `build.gradle{,.kts}`, `settings.gradle{,.kts}` present) | `<basePath>/gradle.properties` | `:46-57`, `:388-392` |
| Gradle project, file missing | created, then written | `:86-96`, `:289-295` |
| Not a Gradle project, `enableGradleGlobalFallback` on | `$GRADLE_USER_HOME/gradle.properties` else `~/.gradle/gradle.properties` | `:400-404` |
| Not a Gradle project, fallback off (**default**) | skipped entirely | `:111-115`, `ProxyThemAllSettings.kt:39` |
| `project == null` | direct `File` I/O, no VFS, no changelist | `:181-185`, `:199-204` |

`getGlobalGradlePropertiesFile()` deliberately does not create the directory — it is also the read
path (`:394-399`).

## Threading and serialisation

- Both entry points run in `Task.Backgroundable(project, ..., false)` — `:65`, `:130` — so blocking
  file and VFS work stays off the EDT (`:64`, `:129`, `:224`)
- File creation + `refreshAndFindFileByIoFile` happen on that background thread (`:288-299`)
- Only the write command is dispatched to the EDT via `invokeLater(..., ModalityState.defaultModalityState())`
  (`:250`, `:280`), with a `project.isDisposed` guard (`:251`)
- `synchronized(gradleFileLock)` (`:41`) serialises read-modify-write, because several projects can
  share one `~/.gradle/gradle.properties` (`:40`)
- Every path calls `onComplete` — success (`:84`, `:91`, `:110`, `:114`, `:144`, `:146`) and failure
  (`:94`, `:119`, `:171`) — which keeps [[ProxyController]]'s two-callback join from stalling

⚠️ Note the fallback at `:244-248`: when there is no VFS entry the file is written directly and the
function **returns before any changelist handling**. Correct, but it means the change is not filed.

## Guards

`GradlePropertiesTextTest` (**16 tests**, pure text) plus `GradleProxyConfigurerTest` (5 tests over a
real temp file, exercising the no-project direct-I/O path). Notably:

- `add then remove restores newline terminated content byte for byte`
- `managed section starting at line 0 is removed` — the off-by-one from [[Gradle Block Duplication]]
- `repeated applies leave exactly one managed section`
- `a user property inside the managed block survives removal` (+ comment and jvmargs variants)
- `existing org gradle jvmargs is not overridden`
- `backslash and leading space in credentials survive a properties round trip`
- `crlf line separators are preserved` — uniform CRLF only; the mixed case is unguarded
- `socks proxy writes the socks bypass list` — the key is emitted with the user's exception **and** the
  essential hosts
- `the socks bypass list is removed again on disable` — the `OWN_KEYS` registration; a byte-for-byte
  round trip, so an unregistered key fails here
- `an http proxy does not write the socks bypass list` — the branch does not leak the SOCKS key

The last three were added with [[SOCKS Bypass Hosts Ignored]]. The pre-existing
`socks proxy is written as socks system properties` asserts the SOCKS host/port and the absence of HTTP
proxy keys — but nothing about the bypass list, so **it passed with the bug present**. A test named for
a feature is not coverage of that feature; only its assertions are.

`GradlePropertiesText.kt` is the only production file at **100 % line coverage** (0 missed, 89
covered). `GradleProxyConfigurer.kt` is at 17/174 — the document/VFS branch needs a live platform and
is manual-only (`docs/VERIFICATION.md` §6.4, §6.5). See [[Coverage Floor Not Target]].

## See also

[[GradlePropertiesText]] · [[Foreign Lines Destroyed]] · [[Changelist Integration]] ·
[[JvmArgs Clobbered]] · [[Gradle Block Duplication]] · [[Credential Handling]] ·
[[Plaintext Gradle Credentials]] · [[Platform API Constraints]] · [[SOCKS Bypass Hosts Ignored]]

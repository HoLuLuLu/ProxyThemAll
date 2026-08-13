---
type: concept
status: current
tags: [platform-api, bytecode, kotlin, vcs, git, properties, lessons]
verified: 2026-08-12
verified-against: efb35cc
---

# Platform API Constraints

Hard-won facts about APIs this plugin sits on. Every entry here cost either a broken build, a data
loss, or a wrong code review. All were checked against bytecode or executed — none are recalled.

Bytecode source for all `javap` claims:

```
D=~/.gradle/caches/9.4.1/transforms/ee1fd16e27b6a5ecc3239ac9dec03c6e/transformed/ideaIC-2024.3.6-aarch64/lib
# ProxyConfiguration/ProxySettings/StatusBarWidgetFactory/PasswordSafe → $D/app-client.jar
# Document/StartupManager/VfsUtilCore                                  → $D/util-8.jar
# ChangeListManager/InvokeAfterUpdateMode                              → $D/app.jar
```

---

## 1. `Document.text` is a `val` — the IDE's own inspection suggests uncompilable code

✅ verified, `javap com.intellij.openapi.editor.Document`:

```
public default java.lang.String getText();
public abstract void setText(java.lang.CharSequence);
```

Asymmetric accessors. Kotlin's synthetic-property rule requires the setter parameter type to equal
the getter return type; `String` != `CharSequence`, so Kotlin exposes `text` as a **`val`**.

- `transform(document.text)` — compiles (getter)
- `document.text = newContent` — `error: val cannot be reassigned`

And IntelliJ ships *"Use of setter method instead of property access syntax"* **enabled by default**.
It sees `document.setText(x)` and offers to rewrite it to `document.text = x`. The inspection reasons
from the setter alone; it does not check that a synthetic property exists. Accepting the quick-fix —
or a bulk "cleanup" sweep that applies it — produces code that cannot compile.

That happened **twice** during the hardening work. See [[Non Compiling Commit]].

Live form, `services/gradle/GradleProxyConfigurer.kt:225-228` and `:263`:

```kotlin
// Document.setText must be called as a method: getText returns String while setText takes
// CharSequence, so Kotlin exposes `text` as a val and the IDE's property-access suggestion
// does not compile.
@Suppress("UsePropertyAccessSyntax")
```

The `@Suppress` *is* the fix. The comment stops a human reverting it; the annotation stops the
inspection proposing the revert.

**Generalisation:** any platform API with asymmetric accessors (`String get` / `CharSequence set`)
is unreachable via property syntax. Do not trust the inspection on platform types.

---

## 2. `StartupManager` is effectively unusable from a plugin

✅ verified, `javap -v com.intellij.openapi.startup.StartupManager` — every member is either
`@ApiStatus.Internal` or `@Deprecated`:

| Member | Annotation |
|---|---|
| `registerStartupActivity(Runnable)` | `org.jetbrains.annotations.ApiStatus$Internal` |
| `runAfterOpened(Runnable)` | `ApiStatus$Internal` |
| `getAllActivitiesPassedFuture()` | `ApiStatus$Internal` |
| `registerPostStartupActivity(Runnable)` | `java.lang.Deprecated` (+ `Deprecated: true` attribute) |
| `runWhenProjectIsInitialized(Runnable)` | `java.lang.Deprecated` |
| `postStartupActivityPassed()` | (unannotated, query only) |

There is no non-internal, non-deprecated way to register work. And internal API usage is a **build
failure**, not a warning: `verifyPlugin` currently reports *0 internal API usages* across three IDEs
(see [[Build Gates]]) — introducing one breaks the gate.

So the plugin uses the extension point instead: `<postStartupActivity>` in
`src/main/resources/META-INF/plugin.xml:22` → `services/ProxyThemAllStartupActivity.kt`, a
`ProjectActivity` with `suspend fun execute(project: Project)`.

Consequence: the activity runs **once per opened project** while the setup is application-wide,
which is why `ProxyThemAllStartupService` guards with an `AtomicBoolean` CAS
(`ProxyThemAllStartupService.kt:35`, `:43-46`). See [[Lifecycle and Leaks]] and
[[Per Project Listener Registration]].

---

## 3. `ProxySettings.setProxyConfiguration` is Experimental and unavoidable

✅ verified, `javap -v com.intellij.util.net.ProxySettings`:

```
public abstract void setProxyConfiguration(ProxyConfiguration);
  RuntimeInvisibleAnnotations:
    0: org.jetbrains.annotations.ApiStatus$Experimental
```

`getProxyConfiguration()` carries only `@NotNull`. The whole interface is those two methods plus
`getInstance()` — see [[Change Detection]] for why that also means "no change topic".

It is the **only** way to change the IDE proxy, which is the plugin's entire purpose. Suppressed
deliberately at class level: `@Suppress("UnstableApiUsage")` on `ProxyService`
(`services/ProxyService.kt:20`, rationale at `:14-15`), on
`ProxyRestoreService.restoreProxyToIntelliJ` (`:127`) and on
`ProxyController.performGlobalCleanup` (`:288`).

Five call sites, one method:

| Call site | Line |
|---|---|
| `ProxyService.enableProxy` | `ProxyService.kt:179` |
| `ProxyService.disableProxy` | `ProxyService.kt:165` |
| `ProxyService.forceEnableProxy` | `ProxyService.kt:131` |
| `ProxyRestoreService.restoreProxyToIntelliJ` | `ProxyRestoreService.kt:143` |
| `ProxyController.performGlobalCleanup` | `ProxyController.kt:293` |

Experimental API usage is *reported* by the verifier but does not fail it — exactly 5 usages on all
three verified IDEs. Internal API usage would fail. The distinction matters: this is a tolerated
risk, item 2 is not.

---

## 4. `ChangeListManager` assigns changes asynchronously — `invokeAfterUpdate` is the barrier

✅ verified, `javap com.intellij.openapi.vcs.changes.ChangeListManager`:

```
public abstract void invokeAfterUpdate(Runnable, InvokeAfterUpdateMode, String, ModalityState);
public abstract boolean areChangeListsEnabled();
public abstract Change getChange(VirtualFile);
public abstract LocalChangeList findChangeList(String);
public abstract void scheduleAutomaticEmptyChangeListDeletion(LocalChangeList);
```

`InvokeAfterUpdateMode` values (✅ `javap`): `SILENT`, `SILENT_CALLBACK_POOLED`,
`BACKGROUND_CANCELLABLE`, `BACKGROUND_NOT_CANCELLABLE`, `SYNCHRONOUS_CANCELLABLE`,
`SYNCHRONOUS_NOT_CANCELLABLE`.

Writing a file does not synchronously produce a `Change`. Two consequences the plugin encodes:

**(a) You cannot move a change you just created without waiting.**
`GradleProxyConfigurer.moveToProxyChangelist` wraps the `getChange` → `findChangeList`/`addChangeList`
→ `moveChangesTo` sequence in `invokeAfterUpdate(..., SILENT, null, modality)` — `:305-343`, barrier
comment at `:313`.

**(b) Checking `changelist.changes.isEmpty()` immediately after a write is a race.**
`cleanupProxyThemAllChangelist` therefore also runs inside `invokeAfterUpdate` (`:352-383`), with the
reason at `:345-348`: *"Emptiness is only meaningful after a ChangeListManager refresh"*. An
un-refreshed list looks empty, gets deleted, and the change lands in the user's default list.

The rejected alternative is recorded in the code as well (`:216-223`): switching the user's *default*
changelist around the write is a race that files the change in the user's own list.

`areChangeListsEnabled()` guards both entry points (`:308`, `:354`) — the VCS may have changelists
switched off entirely. See [[Changelist Integration]].

---

## 5. `VfsUtilCore.loadText` needs NO read action — and a review said otherwise from memory

✅ verified two ways, `javap -v com.intellij.openapi.vfs.VfsUtilCore`:

1. `grep -ci RequiresReadLock` over the whole disassembled class → **0**. No
   `@RequiresReadLock`, no `@RequiresEdt`.
2. The implementation is plain I/O. `loadText(VirtualFile)` delegates to `loadText(VirtualFile, int)`
   with `getLength()`, and that method's entire body is:

```
new InputStreamReader(vf.getInputStream(), vf.getCharset())
  → FileUtilRt.loadText(reader, len)
  → new String(char[])
```

`getInputStream()` and `getCharset()` — nothing that touches PSI, the model, or the read lock.

**The lesson, recorded because it is the point of this page:** a code review on this codebase claimed
`loadText` requires a read action, from memory, and was **wrong**. The claim was plausible — most
`VfsUtil`-adjacent things do want a lock — and it would have added a `runReadAction` wrapper that
does nothing but widen a lock scope on a background thread.

The call site is `GradleProxyConfigurer.kt:269`, deliberately outside any read action; only the
*write* is wrapped, in `WriteCommandAction.runWriteCommandAction` (`:270-272`). See
[[Foreign Lines Destroyed]] for why `loadText` replaced `File.readText()` in the first place
(charset agreement).

---

## 6. `git config --unset` / `--unset-all` exits **5** when the key is absent

✅ executed, git 2.50.1 (Apple Git-155), in a fresh repo with no `http.proxy` set:

```
$ git config --unset http.proxy      ; echo $?
5
$ git config --unset-all http.proxy  ; echo $?
5
```

Not 0, not 1. Treating a non-zero exit as failure here is what produced
[[Global Git Config Deleted]] — the *common* case (no proxy configured yet) threw, the
`removedAny` flag stayed false, and control fell through to wiping the user's `--global http.proxy`.

Encoded as `EXIT_CODE_KEY_MISSING = 5` (`services/git/GitProxyConfigurer.kt:24`), whitelisted in
`executeGitCommand` which throws only when the code is neither 0 nor 5 (`:177-181`). The status
message then uses `exitCode == 0` per call to distinguish "removed" from "was never set"
(`:122-135`).

---

## 7. `.properties` semantics that bite

✅ executed against `java.util.Properties` (JDK, `load(Reader)`):

| Input | Parsed as | Consequence |
|---|---|---|
| `k=first` then `k=second` | `k` → `second` | **A later duplicate key wins.** Appending `org.gradle.jvmargs` silently replaced a user's `-Xmx4g` → [[JvmArgs Clobbered]] |
| `b=a\\c` | `b` → `a\c` | Backslash is an escape; a password containing `\` loses characters unless doubled |
| `cont=end\` + newline + `next=1` | `cont` → `endnext=1`, `next` → **null** | A value ending in `\` line-continues and **swallows the following key** |
| `lead=\ x` | `lead` → `" x"` | A leading space must be escaped or it is stripped |
| `  spaced = v` | `spaced` → `v` | Surrounding whitespace around key and value is ignored |

All five are why `escapePropertyValue` exists and why its order is fixed —
backslash **first**, then `\n`/`\r`/`\t`, then the leading-space prefix
(`services/gradle/GradlePropertiesText.kt:210-217`). Every emitted value goes through it
(`appendProperty`, `:200-202`).

Guarded by `GradlePropertiesTextTest.backslash and leading space in credentials survive a properties
round trip` and `existing org gradle jvmargs is not overridden`.

---

## 8. Git has no `https.proxy` / `https.noproxy`, and `http.noproxy` does not glob

**No `https.*` proxy keys exist.** ✅ verified by dumping the config-key string table out of
`git-remote-http` (git 2.50.1): the complete `http.*` set is

```
http.cookiefile http.curloptresolve http.delegation http.emptyauth http.extraheader
http.followredirects http.keepalive* http.lowspeed* http.maxrequests http.minsessions
http.noepsv http.pinnedpubkey http.postbuffer http.proactiveauth http.proxy
http.proxyauthmethod http.proxyssl{cainfo,cert,certpasswordprotected,key}
http.savecookies http.schannel* http.ssl* http.useragent http.version
```

No `https.proxy`, no `https.noproxy` — and note `http.noproxy` is **not in that list either**.
`man git-config` documents `http.proxy` (which explicitly covers HTTPS via curl's
`http_proxy`/`https_proxy`/`all_proxy`) and never mentions `noproxy`.

Recorded at `GitProxyConfigurer.kt:15`. Scope the claim carefully: the plugin *does* write
`systemProp.https.proxyHost` / `...proxyPort` for Gradle
(`GradlePropertiesText.kt:166-168`) — those are JVM system properties, a different namespace.

**`http.noproxy` does not match glob patterns.** The plugin filters any entry containing `*` out of
the git value (`gitNoProxyHosts`, `:98-102`), so `127.*` from `ESSENTIAL_BYPASS_HOSTS`
(`models/ProxyInfo.kt:23`) never reaches git. That filter is correct and conservative.

⚠️ **Stronger finding, newly measured, not yet reflected in the code.** Against a local listener
acting as the proxy, git 2.50.1 (Apple Git-155) routed the request through the proxy in *every*
`http.noproxy` case tried, including an exact host match, while the `NO_PROXY` **environment
variable** bypassed correctly:

| Setup | Proxy hit? |
|---|---|
| `http.proxy` only (control) | yes |
| `http.proxy` + `http.noproxy=<exact host>` | **yes** |
| `http.proxy` + `git -c http.noproxy=<exact host>` | **yes** |
| `http.proxy` + `NO_PROXY=<exact host>` env | no |

Consistent with `http.noproxy` being absent from the binary's key table: on this build the key
appears to be **ignored entirely**, not merely glob-blind. Two independent lines of evidence agree
(string table + behaviour). ⚠️ measured on one git build on macOS only; other builds/vendors may
differ, and the write itself is harmless. If confirmed broadly, `http.noproxy` writes
(`GitProxyConfigurer.kt:66-71`, `:125-127`) are dead weight and bypass for git would need
`NO_PROXY`. Not filed as a defect yet — it needs a second git build to confirm.

---

## 9. `StatusBarWidgetFactory.disposeWidget` has a real default — do not override it

✅ verified, `javap -c com.intellij.openapi.wm.StatusBarWidgetFactory`:

```
public default void disposeWidget(StatusBarWidget);
  Code:
    ...
    9: invokestatic  // Method com/intellij/openapi/util/Disposer.dispose:(LDisposable;)V
   12: return
```

The default *is* the disposal. Overriding it with an empty body silently discards
`Disposer.dispose(widget)` and leaks the widget, its listener and its `Project` —
[[Widget Project Leak]]. The current factory documents the omission instead of overriding:
`widgets/ProxyStatusBarWidgetFactory.kt:27-28`.

Related: `StatusBar.updateWidget()` only repaints an existing widget; adding or removing one requires
`StatusBarWidgetsManager.updateWidget(factoryClass)`, which re-evaluates `isAvailable()`
(`settings/ProxyThemAllConfigurable.kt:166-170`).

---

## 10. Credential-store shapes

✅ verified with `javap`:

```
public interface ProxyCredentialStore {
  Credentials getCredentials(String host, int port);
  void setCredentials(String host, int port, Credentials, boolean remember);
  boolean areCredentialsRemembered(String, int);
}

public abstract class PasswordSafe implements CredentialStore, PasswordStorage {
  boolean isMemoryOnly();
  void set(CredentialAttributes, Credentials, boolean);
  ...
}
public interface CredentialStore {          // PasswordSafe's 2-arg set/get come from here
  Credentials get(CredentialAttributes);
  void set(CredentialAttributes, Credentials);
}
```

`generateServiceName(subsystem, key)` is pure string concatenation — the bytecode's
`makeConcatWithConstants` recipe is literally `"IntelliJ Platform  — "` (em dash), so
`generateServiceName("ProxyThemAll", "proxy.backup")` = `IntelliJ Platform ProxyThemAll — proxy.backup`.
No hashing, no per-install salt: the service name is a stable, guessable keychain entry name. See
[[Credential Handling]].

The `remember` boolean on `setCredentials` defaults to nothing useful — passing `false` makes the
value memory-only, which was [[Restored Password Not Persisted]].

---

## The rule

**Never assert platform behaviour from memory. Check the bytecode or run it.**

This page exists because each numbered item above was, at some point, believed to be otherwise by
someone reading plausibly. The scoreboard:

| Wrong belief | Cost |
|---|---|
| "`document.text = x` is the idiomatic form" (IDE said so) | two non-compiling commits |
| "`git config --unset` fails only on real errors" | wiped the user's global git proxy |
| "a later duplicate key is ignored" | dropped a user's `-Xmx4g` |
| "`loadText` needs a read action" | a code review finding that was simply false |
| "overriding `disposeWidget` empty is harmless" | a `Project` leak |
| "the platform must have a proxy-change topic" | would have shipped a listener that never fires |

`javap` against the resolved `ideaIC` jars, or an executed command, is the arbiter. A plausible
memory is not evidence — and note that four of the six above were *plausible*.

## See also

[[Change Detection]] · [[Credential Handling]] · [[Gradle Properties Management]] ·
[[Changelist Integration]] · [[Lifecycle and Leaks]] · [[Non Compiling Commit]] ·
[[Global Git Config Deleted]] · [[JvmArgs Clobbered]] · [[Widget Project Leak]] · [[Build Gates]]

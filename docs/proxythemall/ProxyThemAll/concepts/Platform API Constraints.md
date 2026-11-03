---
type: concept
status: current
tags: [platform-api, bytecode, kotlin, vcs, git, properties, lessons]
verified: 2026-11-03
verified-against: e6c7763
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
failure**, not a warning: `verifyPlugin` now checks seven IDEs and reports *0 internal usages outside
the 5 grandfathered `setProxyConfiguration` calls* (§3) — any other internal usage, on any IDE, fails
the hand-written allowlist guard (see [[Build Gates]]). Calling a `StartupManager` member would be
exactly such a usage.

So the plugin uses the extension point instead: `<postStartupActivity>` in
`src/main/resources/META-INF/plugin.xml:23` → `services/ProxyThemAllStartupActivity.kt`, a
`ProjectActivity` with `suspend fun execute(project: Project)`.

Consequence: the activity runs **once per opened project** while the setup is application-wide,
which is why `ProxyThemAllStartupService` guards with an `AtomicBoolean` CAS
(`ProxyThemAllStartupService.kt:35`, `:43-46`). See [[Lifecycle and Leaks]] and
[[Per Project Listener Registration]].

---

## 3. `ProxySettings.setProxyConfiguration` is Internal (2026.3+) and unavoidable

✅ verified, `javap -v com.intellij.util.net.ProxySettings` against the 2024.3.6 jar — kept as
history, the annotation it names is no longer current:

```
public abstract void setProxyConfiguration(ProxyConfiguration);
  RuntimeInvisibleAnnotations:
    0: org.jetbrains.annotations.ApiStatus$Experimental
```

✅ That annotation changed upstream. intellij-community commit `61904c9bed` (2026-08-03),
"refactoring [platform]: protecting proxy settings modification API", moved the same method from
`@ApiStatus.Experimental` to `@ApiStatus.Internal`. Plugin Verifier 1.411 confirms it on the 2026.3
EAP build now in the default `recommended()` selection (`IU-263.7307.112`, see [[Build Gates]]):

```
Internal method com.intellij.util.net.ProxySettings.setProxyConfiguration(ProxyConfiguration arg0) : void
is invoked in org.holululu.proxythemall.services.ProxyService.disableProxy(ProxySettings) : void.
This method is marked with @org.jetbrains.annotations.ApiStatus.Internal annotation ...
```
(`build/reports/pluginVerifier/IU-263.7307.112/plugins/org.holululu.proxythemall/0.0.7/internal-api-usages.txt`)

`getProxyConfiguration()` carries only `@NotNull`. The whole interface is those two methods plus
`getInstance()` — see [[Change Detection]] for why that also means "no change topic".

It is the **only** way to change the IDE proxy, which is the plugin's entire purpose. Three
alternatives were considered and rejected:

- ✅ **`HttpConfigurable` fields directly.** In 2024.3 bytecode
  (`OverrideCapableProxySettingsImpl` → `ProxySettingsCompatibilityImpl` →
  `HttpConfigurableMigrationUtilsKt.setFromProxyConfiguration`) and in 2026.3 source
  (`OverrideCapableProxySettings.setProxyConfiguration` → `httpConfigurable.setFromProxyState`), the
  setter *itself* writes `HttpConfigurable` fields under the hood either way. Those fields are
  `@Deprecated(forRemoval = true)` — calling them directly trades one unstable annotation for one
  scheduled for removal. Rejected by the user.
- ✅ **`ProxySettingsOverrideProvider`.** `shouldUserSettingsBeOverriden` is cached once (an extension
  point `computeIfAbsent`), and overriding it locks the user out of their own settings entirely — it
  solves "enforce an admin proxy," not "change the proxy on the user's behalf." Doesn't fit.
- Reflection. JetBrains staff describe it as a "road to ban" under the Marketplace ToS
  (platform.jetbrains.com topics 4272, 4692). Not used.

Five call sites, one method, unaffected by the reclassification:

| Call site | Line |
|---|---|
| `ProxyService.enableProxy` | `ProxyService.kt:179` |
| `ProxyService.disableProxy` | `ProxyService.kt:165` |
| `ProxyService.forceEnableProxy` | `ProxyService.kt:131` |
| `ProxyRestoreService.restoreProxyToIntelliJ` | `ProxyRestoreService.kt:143` |
| `ProxyController.performGlobalCleanup` | `ProxyController.kt:293` |

Suppressed deliberately at class level: `@Suppress("UnstableApiUsage")` on `ProxyService`
(`services/ProxyService.kt:20`, rationale at `:14-15`), on
`ProxyRestoreService.restoreProxyToIntelliJ` (`:127`) and on
`ProxyController.performGlobalCleanup` (`:288`).

**Internal API usage fails `verifyPlugin` by default — and `-ignoredProblemsFile` cannot rescue it.**
`IgnoredProblemsFilter` only matches `CompatibilityProblem`; an internal-API-usage report is a
different type and never reaches that filter, so the ignore-file mechanism used for item 11 below
cannot tolerate these five lines. Instead, `build.gradle.kts:120` sets
`failureLevel = listOf(FailureLevel.COMPATIBILITY_PROBLEMS, FailureLevel.OVERRIDE_ONLY_API_USAGES)` —
deliberately *excluding* `FailureLevel.INTERNAL_API_USAGES` (confirmed present in the enum via
`javap` against `intellij-platform-gradle-plugin-2.19.0.jar`) — and a `doLast` on `tasks.verifyPlugin`
(`build.gradle.kts:168-202`) re-implements the gate by hand: it walks every `internal-api-usages.txt`
under the verification reports directory and throws `Unexpected internal API usages (only the 5
grandfathered setProxyConfiguration call sites are allowed): ...` (message at `build.gradle.kts:197`)
unless every line names one of the five callers in the `allowedCallers` list
(`build.gradle.kts:173-179`). A sixth usage anywhere, or a changed caller name, fails the build.
**Before Task 1 added this guard, the IntelliJ Platform Gradle Plugin's own default `failureLevel`
(`ALL`) would have failed the build on the first internal usage** — that is the belief the old
wording of this section recorded; it was correct until the override above replaced it.

⚠️ **Marketplace policy — JetBrains staff forum statements, not written policy** (all
platform.jetbrains.com, staff member yuriy.artamonov):

- topic 4895: *"Most likely we will never approve such new usages in a plugin and the version will
  not be published."*
- topic 4692: *"we try to approve all existing usages till next Major release"*, conditioned on
  "[not blocked] until number of usages changes / new files with errors appear" — surfaced to the
  author as an "Ignored Internal API usages" group in the plugin check results.
- topic 4628: *"will continue publishing automatically till 2026.3 at least ... We will enforce it
  only once we have full one Major release covered."*
- topic 4169: once an API is promoted *to* Experimental, *"it will not be a problem for approvals
  anymore"* — the inverse direction of what just happened here.

Approval Guidelines §2.1.d ("The Plugin does not violate JetBrains' internal API usage") is the
written rule the forum posts are explaining around it. Marketplace approved plugin version 0.0.7
(id 28611) with exactly these 5 usages, consistent with the "existing usages tolerated" reading. See
the open risk in [[Defect Register]] for what happens when the grandfathering window ends.

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

## 11. 2026.3 splits VCS into a bundled plugin — `com.intellij.modules.vcs` is the alias, and Plugin Verifier 1.411 cannot see through it yet

✅ JetBrains "api-changes-list-2026": "VCS moved to a bundled plugin (2026.3)", plugin id
`com.intellij.platform.vcs`; "A dependency on the `com.intellij.modules.vcs` plugin alias is
sufficient."

✅ Confirmed against the resolved `263.7307.112` jars: `ChangeListManager` now lives in
`plugins/platform-vcs-plugin/lib/modules/intellij.platform.vcs.jar`. The alias
`com.intellij.modules.vcs` is declared by content module `intellij.platform.vcs.impl`, which itself
depends on `intellij.platform.vcs`. `Git4Idea`'s main module depends only on
`intellij.platform.ide.impl` — the VCS dependency sits on *its* content module, not the main one — so
without an explicit dependency on the alias, there is no classloader path from this plugin to
`ChangeListManager` on 2026.3.

Fix: `src/main/resources/META-INF/plugin.xml:8` adds `<depends>com.intellij.modules.vcs</depends>`,
alongside the pre-existing `<depends>com.intellij.modules.platform</depends>` (`:7`) and
`<depends>Git4Idea</depends>` (`:12`).

✅ The alias is present in the 2024.3.6 `app.jar` too, so the `<depends>` is harmless there. ⚠️
`intellij.platform.vcs` is **absent** from the 243/251/252 product-info layout, so the stronger form,
`<dependencies><module name="intellij.platform.vcs"/></dependencies>`, would break those three IDEs —
not used; the plugin-alias `<depends>` is the form that works across 243 and 263 at once.

⚠️ **Open risk, inferred not executed.** Runtime classloading goes through
`MultiParentClassLoaderHelper`, which "resolves the transitive closure of parent classloaders" — by
source reading, the dependency above should make `ChangeListManager` resolvable from this plugin's
classloader on 2026.3. This has **not** been exercised in an actual 2026.3 sandbox; see the matching
open risk in [[Defect Register]] and the mitigation step at `docs/VERIFICATION.md` §6.6.

**Plugin Verifier 1.411 still reports it as broken anyway — a verifier false positive, not a real
problem.** Even with the `<depends>` in place, the verifier emits 6 "Access to unresolved class"
problems for `ChangeListManager` and `InvokeAfterUpdateMode` against `IU-263.7307.112`. Same symptom
reported independently on platform.jetbrains.com topic 5250 (2026-10-14); JetBrains staff response:
"new version of IntelliJ Plugin Verifier very soon." Worked around via `verifier-ignored-problems.txt`
(repo root, 2 lines, matched with `.matches()` against each problem's `shortDescription`):

```
org.holululu.proxythemall::Access to unresolved class com\.intellij\.openapi\.vcs\.changes\.ChangeListManager
org.holululu.proxythemall::Access to unresolved class com\.intellij\.openapi\.vcs\.changes\.InvokeAfterUpdateMode
```

wired in at `build.gradle.kts:123`
(`ignoredProblemsFile = layout.projectDirectory.file("verifier-ignored-problems.txt")`). This is a
workaround for a verifier bug, not evidence the classes actually resolve — **remove both lines the
day the verifier stops reporting them**, and do not add further entries to that file without the same
kind of evidence (a specific verifier version plus an upstream issue, not "it's probably fine").

Affected code: `GradleProxyConfigurer.moveToProxyChangelist`, `cleanupProxyThemAllChangelist` (plus
their `invokeAfterUpdate` lambdas) — see item 4 above, which this item does not change, only adds a
module-resolution precondition in front of.

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
[[Global Git Config Deleted]] · [[JvmArgs Clobbered]] · [[Widget Project Leak]] · [[Build Gates]] ·
[[Defect Register]]

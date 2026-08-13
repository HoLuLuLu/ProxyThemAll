# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Start here: the knowledge base

This repo carries an LLM-maintained wiki at **`docs/proxythemall/ProxyThemAll/`**. It is not
decoration — it exists because most of this plugin's defects came from plausible-but-wrong
assumptions about IntelliJ Platform APIs, and the wiki records the evidence.

Before changing behaviour, read in this order:

| File | Why |
|---|---|
| `docs/proxythemall/ProxyThemAll/index.md` | Catalog of every page. Start here to find the relevant one. |
| `docs/proxythemall/ProxyThemAll/Overview.md` | What the plugin is and how the pieces connect. |
| `docs/proxythemall/ProxyThemAll/concepts/Platform API Constraints.md` | **Highest-value page.** Platform behaviours that already cost real debugging time, each with bytecode evidence. |
| `docs/proxythemall/ProxyThemAll/defects/Defect Register.md` | All 12 fixed defects plus the open risks. Check before "fixing" something. |
| `docs/proxythemall/ProxyThemAll/CLAUDE.md` | The vault's own schema — read it before editing any wiki page. |

If you change plugin behaviour, update the affected wiki pages and append to
`docs/proxythemall/ProxyThemAll/log.md`. The vault schema explains the conventions
(`file:line` citations, ✅ verified vs ⚠️ inferred, `verified-against` frontmatter).

`docs/VERIFICATION.md` is the manual test plan — 48 steps. Several defects have **no** automated
guard and only that plan catches them.

## Commands

```bash
./gradlew check                       # "Run Tests" run config: test + koverXmlReport + koverVerify
./gradlew verifyPlugin                # "Run Verifications": IntelliJ Plugin Verifier, 3 IDE versions
./gradlew runIde                      # "Run Plugin": launches a sandbox IDE
./gradlew test --tests '*ProxyUrlBuilderTest*'   # a single test class
./gradlew buildPlugin                 # distributable zip
```

`check` is the gate that matters — `verifyPlugin` is **not** part of it and must be run separately.
CI (`.github/workflows/build.yml`) runs `buildPlugin`, `check`, Qodana and `verifyPlugin`.

When testing in the sandbox IDE, its log is at
`.intellijPlatform/sandbox/ProxyThemAll/IC-*/log/idea.log` — most manual verification steps read it.

Targets: `platformVersion` 2024.3.6, `pluginSinceBuild` 243, JVM toolchain 21, Kotlin 2.4.10
(`gradle.properties` and `gradle/libs.versions.toml`).

Kover has a **floor, not a target**: `minValue = 20` LINE against ~26% actual. It exists to stop
regressions; see `decisions/Coverage Floor Not Target.md` before raising it.

## Architecture

The plugin mirrors the IDE's proxy setting into Git and Gradle. Two entry paths, and the difference
matters:

```
Tools menu / status bar widget  ──►  ProxyController  ──►  GitProxyService   ──► GitProxyConfigurer  ──► `git config`
                                     (all open projects)   GradleProxyService ──► GradleProxyConfigurer ──► gradle.properties
                                            │                                             │
                                            ▼                                             └──► GradlePropertiesText (pure text)
                                       ProxyService  ◄── polls ──  ProxyStateChangeManager
                                    (IDE ProxySettings)                    │
                                                                           ├──► HttpProxySettingsChangeListener ──► backup + reapply
                                                                           └──► WidgetStateChangeListener ──► repaint
```

- **Plugin-initiated** changes (menu, widget) apply immediately.
- **User edits in Settings → HTTP Proxy** are detected by a **2-second poll**, so up to ~2s latency.

### The constraint everything follows from

The IntelliJ Platform publishes **no event** for proxy configuration changes. The poll, the
configuration comparison, and the ordering rules all exist because of that single fact. Read
`concepts/Change Detection.md` before touching the listener layer.

Two rules in that layer are load-bearing:

1. `ProxyStateChangeManager.checkForStateChanges()` compares **both** the `ProxyState` enum **and**
   the `ProxyConfiguration`. Enum-only comparison misses edits that keep the proxy enabled (a port
   change, HTTP→SOCKS).
2. Remembered state is updated **before** `notifyListeners`. Listeners reapply and end by calling
   `notifyStateChanged()`; stale values there cause a reapply on every tick, forever.

### Layers worth knowing

- **`GradlePropertiesText`** is deliberately free of IntelliJ and file-system imports. That is why it
  is at 100% coverage while the platform-coupled classes are near zero. Keep it pure.
- **Ownership invariant:** every key `buildSection` writes must be listed in `OWN_KEYS`. `isOwnLine`
  drives removal, so an unlisted key is treated as a *user's* line and preserved forever.
- **Read/write symmetry:** read and write `gradle.properties` through the same layer. When the file is
  open in an editor, use its `Document`; reading disk while writing the VFS silently discards unsaved
  edits.
- **Notifications:** the "show notifications" setting suppresses only `INFORMATION`. Warnings and
  errors always show. Startup reconciliation is silent — only user actions announce themselves.

## Conventions specific to this codebase

- **Never assert platform API behaviour from memory.** Check the bytecode — unzip the class out of the
  resolved platform jars and run `javap` (add `-v` to see annotations such as `@ApiStatus.Internal`):

  ```bash
  D=$(ls -d ~/.gradle/caches/*/transforms/*/transformed/ideaIC-*/lib | head -1)
  cd $(mktemp -d) && for j in $D/*.jar; do unzip -o -q "$j" 'com/intellij/openapi/editor/Document.class' 2>/dev/null; done
  javap -classpath . com.intellij.openapi.editor.Document
  ```

  Three separate defects came from skipping this. A code review once claimed
  `VfsUtilCore.loadText` needs a read action; the bytecode says otherwise.
- **`LOG.warn`, not `LOG.error`,** for expected environment failures (git missing, config key absent,
  locked keychain). `LOG.error` raises "IDE fatal error" reports and is a marketplace-review
  rejection.
- **Never log the proxy URL** — it embeds credentials. Log `host:port`.
- `ProxySettings.setProxyConfiguration` is `@ApiStatus.Experimental` and unavoidable (5 call sites,
  suppressed). Do not introduce `@ApiStatus.Internal` APIs — `verifyPlugin` fails the build on them,
  which is why `StartupManager` is not used.
- Use the platform factories `ProxyConfiguration.proxy(...)` / `.direct`, never anonymous
  implementations — those lack `equals`/`hashCode` and break the change detection above.
- Gradle credentials are written in **plain text** to `gradle.properties` by design (Gradle offers no
  encrypted alternative). This is an accepted, documented risk — see
  `decisions/Plaintext Gradle Credentials.md`. Do not "fix" it without reading that page.

## Watch out for

`Document.getText()` returns `String` while `setText()` takes `CharSequence`, so Kotlin exposes
`text` as a **`val`**. IntelliJ's default-on *"Use of setter method instead of property access
syntax"* inspection suggests `document.text = x`, **which cannot compile**. The call site carries
`@Suppress("UsePropertyAccessSyntax")` for exactly this reason — leave it. This broke the build twice;
see `defects/Non Compiling Commit.md`.

Related process rule: **run the build gate after the last edit.** Both occurrences shipped because a
"trivially safe" edit was made after `check` had already passed.
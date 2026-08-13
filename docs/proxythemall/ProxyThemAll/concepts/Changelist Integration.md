---
type: concept
status: current
tags: [vcs, changelist, gradle, threading, platform-api]
verified: 2026-08-12
verified-against: efb35cc
---

# Changelist Integration

The plugin files its `gradle.properties` edit into a dedicated VCS changelist named **`ProxyThemAll`**
(`GradleProxyConfigurer.kt:37`).

Only Gradle uses this. Git configuration is written with `git config`, which touches `.git/config` —
not a tracked file, so there is nothing to file.

## Why — and what it is not

The proxy section contains, when the proxy authenticates, the username and password in cleartext
(`GradlePropertiesText.kt:178-184`). Separating that change from the user's own work means
"commit all my changes" in the default list does not sweep it up.

**It is not a security boundary.** The plugin says so in two user-visible places:

- Settings, under "Apply proxy settings to Gradle" — *"The ProxyThemAll changelist reduces the risk of
  committing them, but it is not a security boundary - a commit of all changes will include them"*
  (`ProxyThemAllConfigurable.kt:70-73`)
- `README.md:53-54`, same wording

The changelist description says the same to anyone who opens it: *"Proxy configuration changes managed
by ProxyThemAll plugin. Do not commit these changes."* (`GradleProxyConfigurer.kt:326-327`).

So: a **speed bump against accident**, not a control. Anything stronger would mean not writing the
credentials at all — see [[Plaintext Gradle Credentials]] and [[Credential Handling]].

## How — write, wait, then move

The sequence is forced by the platform: `ChangeListManager` assigns changes to changelists
**asynchronously**, so a change does not exist to be moved the instant the write returns. Verified
API surface in [[Platform API Constraints]] §4.

`moveToProxyChangelist` — `GradleProxyConfigurer.kt:305-343`:

```kotlin
changeListManager.invokeAfterUpdate(
    {
        if (project.isDisposed) return@invokeAfterUpdate            // :316
        val change = changeListManager.getChange(virtualFile)        // :319
        if (change == null) { LOG.debug(...) }                       // :320-321
        else {
            val proxyChangelist = changeListManager.findChangeList(CHANGELIST_NAME)
                ?: changeListManager.addChangeList(CHANGELIST_NAME, "...")   // :323-328
            changeListManager.moveChangesTo(proxyChangelist, change)  // :330
        }
        afterMove()                                                  // :334
    },
    InvokeAfterUpdateMode.SILENT, null, ModalityState.defaultModalityState()  // :339-341
)
```

Order: **write → `invokeAfterUpdate` → `moveChangesTo`**. The barrier comment is at `:313`.

`InvokeAfterUpdateMode.SILENT` — no progress UI for a background housekeeping action.

The changelist is created **lazily** (`findChangeList` ?: `addChangeList`, `:323-328`), so a user who
never enables Gradle support never sees it.

`getChange == null` is treated as normal, not an error (`:320-321`): the file may be untracked, or
outside a VCS root, in which case there is simply nothing to file.

### The rejected alternative, recorded in the code

`:216-223`:

> The change is moved after ChangeListManager's refresh rather than by switching the user's default
> changelist: change-to-list assignment happens asynchronously, so switching the default list around
> the write is a race that files the change in the user's own list.

Switching the default list is the obvious approach and it is wrong twice: it races, and it mutates a
global user setting for the duration of a background write.

## Cleanup — delete only when genuinely empty, after a refresh

`cleanupProxyThemAllChangelist` — `:352-383`, wired in as the `afterMove` callback of the *removal*
path only (`:192`). Adding the section does not clean up; removing it does.

```kotlin
changeListManager.invokeAfterUpdate({
    if (project.isDisposed) return@invokeAfterUpdate                 // :358
    val changelist = changeListManager.findChangeList(CHANGELIST_NAME)
        ?: return@invokeAfterUpdate                                  // :361
    val changes = changelist.changes.toList()                        // :362

    if (changes.isEmpty()) {
        changeListManager.removeChangeList(changelist)               // :365
        return@invokeAfterUpdate
    }

    changeListManager.moveChangesTo(changeListManager.defaultChangeList, changes)  // :371
    changeListManager.scheduleAutomaticEmptyChangeListDeletion(changelist)         // :374
}, InvokeAfterUpdateMode.SILENT, null, ModalityState.defaultModalityState())
```

Three rules encoded here:

**1. Emptiness is only meaningful after a refresh.** Hence the second `invokeAfterUpdate`
(`:345-348`). Checking `changelist.changes.isEmpty()` straight after a write is a race — the list
looks empty because the update has not landed, gets deleted, and the change ends up in the user's
default list.

**2. Foreign changes are MOVED to the default list, never reverted** (`:370-371`). The comment at
`:349-350` records why: *"reverting risked destroying concurrent user edits"*. If a user manually
dragged one of their own files into the ProxyThemAll list — or edited `gradle.properties` themselves —
a revert would discard their work. Moving is always safe.

**3. Deletion is then handed to the platform.** After the move,
`scheduleAutomaticEmptyChangeListDeletion(changelist)` (`:374`) lets the platform drop the list once
the move has been processed, instead of racing a second explicit `removeChangeList`.

## `areChangeListsEnabled()` guard

Both entry points bail out early when the VCS has changelists disabled:

```kotlin
if (!changeListManager.areChangeListsEnabled()) { ...; return }   // :308-311 (move), :354 (cleanup)
```

The write itself still happens — only the filing is skipped. Without the guard, every
`gradle.properties` write on such a project would log a failure for something the user turned off
deliberately.

## Paths that bypass the changelist entirely

| Path | Why | Cite |
|---|---|---|
| `project == null` | no VCS context; direct `File` I/O | `:181-185`, `:199-204` |
| Global `~/.gradle/gradle.properties` | outside any project's VCS root | `:410-412` → `:417-421` |
| No VFS entry for the file | falls back to `file.writeText` and **returns before filing** | `:244-248` |

The third is worth flagging: it is a silent divergence — the file is updated, the change is not filed.
⚠️ inferred consequence from the early `return` at `:247`; not reproduced.

## Threading

Everything above runs on the EDT: `moveToProxyChangelist` is called from inside the
`invokeLater` block that performs the write (`:276`), and both `invokeAfterUpdate` calls pass
`ModalityState.defaultModalityState()` (`:341`, `:381`). `project.isDisposed` is re-checked inside
**each** callback (`:316`, `:358`) because an arbitrary amount of time passes while waiting for the
refresh — the project can close in between.

Failures are caught and warned, never rethrown (`:335-337`, `:375-377`): failing to tidy a changelist
must not fail the proxy operation.

## Guards

**None automated.** Every line above needs a live `ChangeListManager`, a VCS root and a real project.
Manual only: `docs/VERIFICATION.md` §6.4 covers the file being dragged into the changelist. The
enclosing class sits at 17 of 174 lines covered — see [[Coverage Floor Not Target]].

## See also

[[Gradle Properties Management]] · [[Platform API Constraints]] · [[Credential Handling]] ·
[[Plaintext Gradle Credentials]] · [[Multi-Project Behaviour]] · [[Foreign Lines Destroyed]]

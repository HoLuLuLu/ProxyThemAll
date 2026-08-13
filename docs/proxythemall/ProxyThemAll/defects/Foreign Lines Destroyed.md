---
type: defect
status: fixed
severity: high
tags: [gradle, data-loss, vfs, document, platform-api]
verified: 2026-08-12
verified-against: efb35cc
---

# Foreign Lines Destroyed

Two independent mechanisms destroyed user content in `gradle.properties`: indiscriminate block removal,
and a read/write asymmetry between the filesystem and the VFS.

Found by the **user during manual testing** (`docs/VERIFICATION.md` §6.4), not by the audit. Worth
stating plainly: the audit read the removal code and approved it.

## Symptom

**(a)** User adds a line *inside* the managed block — between the START and END markers — because that
is where the proxy settings are and it looks like the right neighbourhood:

```properties
# === ProxyThemAll Managed Proxy Settings - START ===
systemProp.http.proxyHost=proxy.corp
my.note=1
# === ProxyThemAll Managed Proxy Settings - END ===
```

Disable the proxy. `my.note=1` is gone. The block header says "Manual changes to this section will be
overwritten", but *overwritten* and *deleted without trace* are different promises.

**(b)** The file is open in an editor with unsaved changes. The plugin writes it. The user's unsaved
edits are gone — and worse, the edits they *had* saved a moment ago may reappear, because the plugin
wrote content computed from a stale read.

## Root cause

### (a) Removal deleted everything between the markers

The pre-fix removers walked from the END marker down to the START index and removed every line
(`233c282:.../gradle/GradleProxyConfigurer.kt:547` and `:624`):

```kotlin
for (i in endIndex downTo startIndex) {
    lines.removeAt(i)
}
```

Positional, not content-aware. The markers were treated as delimiters of a region the plugin owns
outright, when in fact the plugin only owns *the lines it wrote*.

### (b) Read from disk, write to the VFS

The write path read the file with `File.readText()` and wrote it back through
`VfsUtil.saveText(virtualFile, ...)`. Two different views of the same file:

- `File.readText()` reads bytes on disk. It cannot see an unsaved editor `Document`.
- `VfsUtil.saveText()` writes the VFS. If a dirty `Document` exists for that file, the platform will
  later save the document over the VFS content.

So with the file open and modified: the plugin computes `transform(stale_disk_content)`, writes it, and
either loses the user's unsaved edits, or gets its own write clobbered when the document is saved —
depending on ordering. Non-deterministic in either direction.

## Why it survived

- **(a) The header comment functioned as consent.** The block says "Manual changes to this section will
  be overwritten". Both the author and the reviewer read that as licence to delete the region. It is
  not: a user who adds a line does not read that as "this line will be silently destroyed", and the
  plugin cannot distinguish "manual change to our settings" from "unrelated property the user parked
  here". The comment made a wrong behaviour feel authorised, which is the most durable kind of defect —
  a reviewer who notices it concludes it is intentional.
- **(a) Marker-delimited region is the obvious mental model.** START and END *look* like ownership
  boundaries. Nothing in the code prompts the question "what if there is something in here that isn't
  ours?" — you have to have been burned to think of it.
- **(b) The asymmetry was two lines ~60 lines apart.** `File.readText()` in one place, `VfsUtil.saveText`
  in another. Each is individually correct and idiomatic. Only the *pair* is wrong, and there is no
  reading order in which the pair is adjacent.
- **(b) Requires a specific live state to reproduce**: file open, dirty, and a proxy toggle at that
  moment. Not reachable by any unit test, and not a state a manual tester naturally sets up — you have
  to be *editing* `gradle.properties` while toggling the proxy. The user hit it because they were doing
  exactly the realistic thing.
- The audit read this code and passed it. Both defects are omissions rather than wrong statements —
  nothing on screen is false, something is simply absent — and omissions are what review is worst at.

## Fix

### (a) Preserve unowned lines

`src/main/kotlin/org/holululu/proxythemall/services/gradle/GradlePropertiesText.kt:110-114` — rescue
foreign lines, then reinsert them where the block was:

```kotlin
// Rescue everything inside the block that is not ours before dropping it
val foreign = lines.subList(from, to + 1).filterNot { isOwnLine(it) }

repeat(to - from + 1) { lines.removeAt(from) }
lines.addAll(from, foreign)
```

Ownership is decided by `isOwnLine` (`GradlePropertiesText.kt:130-144`) against the *same* constants
`buildSection` writes — `OWN_KEYS` (`:35-40`) and `OWN_COMMENTS` (`:52-55`) — so detection cannot drift
away from emission (`:20-21` states this intent). Blank lines count as ours (`:134`); `org.gradle.jvmargs`
is value-gated (`:141`, see [[JvmArgs Clobbered]]).

The rule is written down at `GradlePropertiesText.kt:80-84`: *silently discarding a user's edit is worse
than an untidy file.* That is a deliberate reversal of the priority the old header comment implied.

### (b) One authoritative source per write

`src/main/kotlin/org/holululu/proxythemall/services/gradle/GradleProxyConfigurer.kt:253-274` — the
document, when one exists, is read **and** written; otherwise the VFS is read **and** written. Never
mixed:

```kotlin
val document = documentManager.getCachedDocument(virtualFile)

if (document != null) {
    // The file is open in an editor. Its document is the authoritative content -
    // reading the VFS would miss unsaved edits, and writing the VFS would later be
    // overwritten when the still-dirty document is saved.
    val newContent = transform(document.text)
    WriteCommandAction.runWriteCommandAction(project) {
        document.setText(newContent)
        documentManager.saveDocument(document)
    }
} else {
    // Read through the VFS so read and write agree on content and charset
    val newContent = transform(VfsUtilCore.loadText(virtualFile))
    WriteCommandAction.runWriteCommandAction(project) {
        VfsUtil.saveText(virtualFile, newContent)
    }
}
```

`VfsUtilCore.loadText` replaced `File.readText()` in the no-document branch so read and write agree on
charset as well as content — a second latent bug in the same asymmetry.

Both branches funnel through the single `transform: (String) -> String` parameter
(`GradleProxyConfigurer.kt:233`), so add and remove cannot diverge in how they read. The `document.setText`
call is the one that keeps recurring as a compile break — see [[Non Compiling Commit]].

## Regression guard

For (a) — `GradlePropertiesTextTest`:

- `a user property inside the managed block survives removal`
- `a user comment inside the managed block survives removal`
- `user jvmargs inside the block survive but ours are removed`
- `reapplying after tampering keeps the foreign line and one block`

and over a real file, `GradleProxyConfigurerTest.a foreign line inside the managed block survives a real
removal`.

For (b) — **none.** The document/VFS branch needs a live platform with an open editor. Manual only:
`docs/VERIFICATION.md` §6.4 (foreign line inside the block, file dragged into the changelist) and §6.5
(foreign line below the END marker).

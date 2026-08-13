---
type: defect
status: fixed
severity: high
tags: [gradle, data-loss, off-by-one, duplication]
verified: 2026-08-12
verified-against: efb35cc
---

# Gradle Block Duplication

Two copies of the same section parser, differing by one `maxOf`, and the buggy copy no-oped silently
so every toggle appended another block.

## Symptom

Toggle the proxy off and on a few times in a Gradle project. `gradle.properties` grows a new
`# === ProxyThemAll Managed Proxy Settings - START ===` block on every cycle — five toggles, five
blocks. The last block wins in `.properties` semantics so the build still works, which is why it went
unnoticed until the file was several hundred lines long.

Separately: the line **directly above** the START marker disappears (see also
[[Foreign Lines Destroyed]]).

## Root cause

`233c282` contained two near-identical section removers:

| Function | Line | Start index |
|---|---|---|
| `removeProxyThemAllSection` | `GradleProxyConfigurer.kt:527` | `startIndex = maxOf(0, i - 1)` (`:538`) |
| `removeProxyThemAllManagedSettings` | `GradleProxyConfigurer.kt:601` | `startIndex = i - 1` (`:614`) |

Both widen the deletion one line above the marker, to swallow the blank separator line that the
append path inserts. Only one of them clamps.

When the START marker is on line 0 — which is exactly the shape of a `gradle.properties` the plugin
created itself — `removeProxyThemAllManagedSettings` computed `startIndex = -1`. The guard below then
read (`:622`):

```kotlin
if (startIndex != -1 && endIndex != -1) {
```

`-1` is the sentinel for *not found*. So a section that was found perfectly well was treated as
absent: no removal, no log line, no error. The subsequent append then added a second block, and so on.

The clamped sibling did not have this bug, which is why the same operation appeared to work depending
on which call site you exercised (`:203` and `:510` used the clamped one; `:568` and `:591` used the
broken one).

**Second defect in the same two lines:** the `i - 1` widening was *unconditional*. It deleted the
line above the marker whether or not that line was blank. With

```properties
org.gradle.caching=true
my.last.property=keep-me
# === ProxyThemAll Managed Proxy Settings - START ===
```

`my.last.property=keep-me` was deleted along with the block. Silent user-data loss on every disable.

## Why it survived

- **The duplication hid the divergence.** Two functions, ~30 lines each, 70 lines apart, doing the
  same thing with different names (`...Section` vs `...ManagedSettings`). Nothing in either name
  suggests they are the same operation, so no reviewer diffed them against each other. The `maxOf(0,`
  in one and its absence in the other is a 9-character difference across a 70-line gap.
- **`-1` served double duty** as both "not found" and a legitimate arithmetic result. The guard
  `startIndex != -1` looks defensive; it is the bug.
- **The failure mode was additive, not destructive-looking.** A duplicated block does not break the
  build, because a later duplicate key wins. The symptom presented as untidiness, and untidiness does
  not get filed.
- **Nothing was testable.** Both functions took a `File`, read it, and wrote it. Testing the off-by-one
  meant creating temp files and a project; there were no tests for either.
- The marker-on-line-0 case is the *plugin's own* output shape, and only occurs in a project that had
  no `gradle.properties` before. Every manual test was run against a project that already had one.

## Fix

Both functions deleted and replaced by one pure function:
`src/main/kotlin/org/holululu/proxythemall/services/gradle/GradlePropertiesText.kt:88` —
`removeManagedSection(content: String): String`.

Design points that make the class of bug unreachable:

- **Pure `String -> String`**, no `File`, no VFS, no IntelliJ API (`GradlePropertiesText.kt:5-11`).
  The data-loss cases are now unit-testable in isolation, which is the actual fix.
- **Anchored strictly on markers**, located by value rather than tracked by index
  (`GradlePropertiesText.kt:100-103`); a not-found marker `break`s the loop instead of producing a
  negative index.
- **Widening is conditional** (`GradlePropertiesText.kt:108`):

```kotlin
if (from > 0 && lines[from - 1].isBlank()) from--
```

  `from > 0` handles line 0; `isBlank()` ensures only a genuinely blank separator is absorbed, never a
  user property.

- **A `while (true)` loop** removes *every* managed section (`GradlePropertiesText.kt:99`), so a file
  already corrupted by the old bug is repaired on the next toggle rather than accumulating further.

## Regression guard

`GradlePropertiesTextTest`:

- `managed section starting at line 0 is removed` — the exact `-1` case; asserts the marker really is
  at index 0 first, so the test cannot silently stop covering it.
- `repeated applies leave exactly one managed section` — five applies, asserts one block and byte-exact
  restoration afterwards.
- `user property directly above the start marker survives removal` — the unconditional widening.
- `add then remove restores newline terminated content byte for byte`.
- `crlf line separators are preserved`.

Plus `GradleProxyConfigurerTest.repeated configuration does not accumulate sections` and
`configuring then removing restores the original file byte for byte` over a real temp file.

Manual: `docs/VERIFICATION.md` §5.2 (no duplicate block), §5.3 (your content survives).

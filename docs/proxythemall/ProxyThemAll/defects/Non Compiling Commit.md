---
type: defect
status: fixed
severity: medium
tags: [kotlin, build, ide-inspection, process, platform-api]
verified: 2026-08-12
verified-against: efb35cc
---

# Non Compiling Commit

`document.text = newContent` does not compile. It was committed once and then survived a commit that
looked like a fix, so the branch carried a non-building revision across three commits.

## Symptom

The build fails outright:

```
error: val cannot be reassigned
```

Nobody could build the branch. No plugin, no tests, no sandbox.

## Root cause

`com.intellij.openapi.editor.Document` has **asymmetric accessors**:

- `String getText()`
- `void setText(CharSequence)`

Kotlin's synthetic-property rule requires the setter parameter type to match the getter return type. It
does not here (`String` vs `CharSequence`), so Kotlin exposes `text` as a **`val`**. The getter is
reachable as `document.text`; the setter is only reachable as `document.setText(...)`.

Hence `transform(document.text)` compiles and `document.text = newContent` cannot.

**The reason it recurred is an IDE inspection.** IntelliJ ships *"Use of setter method instead of property
access syntax"* enabled by default. It sees `document.setText(x)`, offers to rewrite it as
`document.text = x`, and that suggestion is **wrong for every asymmetric-accessor API**. The inspection
reasons from the setter alone and does not check that a synthetic property actually exists. Accept the
quick-fix — or an "optimise/cleanup" sweep that applies it in bulk — and you get code that cannot compile.

This is the second-order defect worth recording: the tool that produced the break is on by default and
presents its output as a cleanup.

## History

Committed **once**, and it then survived a commit that looked like a fix.

- `75dd04d` "Improve Architecture, Harden mechanisms" — introduced the broken line. No comment yet.
- `f850ec0` "Fix for new main" — **added the explanatory comment above the still-broken line**, and
  removed an unrelated dead function. It did *not* fix the line and did *not* reintroduce it: the
  line was already there and stayed byte-identical.
- `b8beb7a` "New fix" — replaced comment + broken line with `@Suppress` + `document.setText(...)`.

✅ verified by counting the line in each commit's blob rather than reading the diff:

```
$ P=src/main/kotlin/org/holululu/proxythemall/services/gradle/GradleProxyConfigurer.kt
$ for c in 75dd04d f850ec0 b8beb7a; do
    printf '%s broken=%s comment=%s\n' "$c" \
      "$(git show $c:$P | grep -c 'document.text = newContent')" \
      "$(git show $c:$P | grep -c 'setText, not the')"; done
75dd04d broken=1 comment=0
f850ec0 broken=1 comment=1
b8beb7a broken=0 comment=0
```

An earlier version of this page (and of [[Session History]]) claimed `f850ec0` "kept the comment
while reverting the code" / "removed the comment and reintroduced the broken form". Both were wrong,
and they contradicted each other — a reminder that a narrative reconstructed from memory of a diff is
not evidence. The blob counts above are.

The diff that misled the reconstruction, for the record:

```
// overwritten when the still-dirty document is saved.
val newContent = transform(document.text)
...
document.text = newContent
documentManager.saveDocument(document)
```

A merge or an IDE cleanup preserved the prose and undid the fix. The comment then actively lied about the
code beneath it — worse than no comment, because it tells a reader the case was already considered.

- `b8beb7a` "New fix" — fixed again, this time with a `@Suppress` that stops the inspection from
  suggesting it a third time.

## Why it survived

Honestly: **a final edit was made after the build gate had already been run, and the gate was not
re-run.** The build had passed earlier in the session on earlier content; the last change was small and
"obviously safe" (a property-access cleanup the IDE proposed), so it went in unverified. Nothing about the
process was subtle — the verification order was simply wrong.

Two contributing factors:

- **The suggestion came from the IDE**, which lends it authority. A rewrite proposed by the same tool that
  compiles the code does not feel like something that needs re-checking.
- **A comment survived where the code did not.** In `f850ec0` the surrounding explanation was intact, so a
  quick eyeball of the region looked like the considered version. Comments are not verified by anything.

The stated fix is procedural, not technical: **the build gate runs after the last edit, or it did not
run.** This is exactly the failure mode
`superpowers:verification-before-completion` exists to prevent — evidence before assertions.

## Fix

`src/main/kotlin/org/holululu/proxythemall/services/gradle/GradleProxyConfigurer.kt`

Method call form at `:263`:

```kotlin
document.setText(newContent)
```

plus a suppression and an explanation on the enclosing function (`:225-228`):

```kotlin
// Document.setText must be called as a method: getText returns String while setText takes
// CharSequence, so Kotlin exposes `text` as a val and the IDE's property-access suggestion
// does not compile.
@Suppress("UsePropertyAccessSyntax")
private fun writeThroughChangelist(
```

The `@Suppress` is the actual fix. The comment stops a *human* from reverting it; the annotation stops the
*inspection* from proposing the revert in the first place — and it was the inspection, not a human, that
caused the recurrence both times.

Related: the `document.setText` branch exists because of the read/write asymmetry described in
[[Foreign Lines Destroyed]].

## Regression guard

The Kotlin compiler. Any recurrence fails `./gradlew build` immediately — this bug cannot reach a green
build.

The real guard is `@Suppress("UsePropertyAccessSyntax")` at `GradleProxyConfigurer.kt:228`, which removes
the tooling nudge that caused both occurrences.

**Not guarded:** the process failure (editing after the gate). No hook or CI check on this repo enforces
"build after last edit" locally; only a CI run on push would catch it, after the fact.

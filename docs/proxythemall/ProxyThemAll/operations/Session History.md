---
type: operation
status: current
tags: [history, git, audit, process]
verified: 2026-08-13
verified-against: efb35cc
---

# Session History

How the tree at `b8beb7a` came to look the way it does. Factual record, not a narrative — every
number below was read out of git.

> **This page is a historical record.** All of the work below was squash-merged into `main` on
> 2026-08-13 as a single commit, `efb35cc` ("Fix data-loss, credential-leak and stale-state defects
> across the proxy sync layer (#79)"). The squash means `75dd04d`, `f850ec0`, `b8beb7a`, `ef3341b` and
> `d14c799` are **not reachable from `main`** — a fresh clone cannot `git show` them. `233c282` still
> is. The commit-by-commit detail is kept because the sequence explains how the defects arose.
> ✅ `git diff --stat d14c799 efb35cc` is empty: the squash preserved the tree exactly.

## Branch position, as it was before the merge

`feature/update`, HEAD `b8beb7a`. `main` was at `233c282` ("Update dependencies (#78)", 2026-08-11).

✅ `git rev-list --count main..HEAD` → **7**, of which 6 non-merge and 1 merge. The brief listed 6
commits "ahead", counting the non-merge commits; the merge commit `ef3341b` is the seventh.

`git log --oneline main..HEAD`, oldest last:

| Commit | Date | Subject | Files | +/− |
|---|---|---|---|---|
| `b8beb7a` | 08-12 | New fix | 1 | +5 / −3 |
| `f850ec0` | 08-12 | Fix for new main | 1 | +2 / −8 |
| `ef3341b` | 08-12 | Merge branch 'main' into feature/update | 0 | — |
| `75dd04d` | 08-12 | Improve Architecture, Harden mechanisms | 39 | +1948 / −1963 |
| `9e11a0e` | 08-11 | Cleanup dependencies | 2 | +6 / −12 |
| `b32505c` | 08-11 | Update remaining actions versions too | 3 | +8 / −8 |
| `2cb7d98` | 08-11 | Update to the latest versions | 6 | +11 / −11 |

`233c282` reaches HEAD via the second parent of `ef3341b` — it is on both `main` and
`feature/update` (`git branch --contains 233c282`), so it is *not* in `main..HEAD`.

## Sequence

1. **Dependency and toolchain refresh** — `2cb7d98`, `b32505c`, `9e11a0e`. Version catalog, GitHub
   Actions tags, dependency cleanup. Small, mechanical. See the versions table in [[Build Gates]].
2. **Full audit against the claims in `README.md` and `CHANGELOG.md`.** Every advertised feature was
   traced to the code that implements it. Where the code did not do what the docs said, that became
   a defect entry rather than a doc edit.
3. **Fixes, in 5 phases**, landing as the single large commit `75dd04d` ("Improve Architecture,
   Harden mechanisms"): 39 files, +1948/−1963. This is where 7 of the 10 entries in the
   [[Defect Register]] were fixed, where [[GradlePropertiesText]] was extracted as a pure text layer,
   and where the placeholder `assertTrue(true)` tests were deleted (see [[Test Suite]]).
4. **Manual verification by the user**, following `docs/VERIFICATION.md`. Four `==>` findings were
   marked, which resolved to **2 distinct defects** — [[Change Detection Enum Only]] accounted for
   three of the four findings (§5.8, §7.1, §7.2), [[Foreign Lines Destroyed]] for the fourth (§6.4).
   Both had been missed by the audit *and* by the unit tests: the value of the manual plan in one
   sentence. See [[Manual Verification]].
5. **Those 2 fixed**, folded into `75dd04d`. With the 7 from the audit that makes 9; the tenth,
   [[Non Compiling Commit]], was self-inflicted later and fixed in `b8beb7a`.
6. **Merge of `main`** — `ef3341b`. ✅ `git diff --stat 75dd04d ef3341b` is **empty**: the merge
   changed nothing on the branch side. `main`'s dependency updates (`233c282`) were already present
   on the branch, having been done independently in steps 1. A no-op merge that only moves the graph.
7. **Two rounds of fixing one non-compiling line** — `f850ec0` then `b8beb7a`, both touching only
   `services/gradle/GradleProxyConfigurer.kt`. The line is `document.text = newContent`:
   `Document.getText()` returns `String` while `setText` takes `CharSequence`, so Kotlin exposes
   `text` as a `val` and the property-access form cannot compile. `f850ec0` *added* the explanatory
   comment above the still-broken line without fixing it (the line came from `75dd04d` and was never
   repaired in between); `b8beb7a` settled it with `document.setText(newContent)`
   plus `@Suppress("UsePropertyAccessSyntax")` and a comment saying why the IDE's suggestion is
   wrong. Recorded as [[Non Compiling Commit]] — the only defect in the register whose regression
   guard is "the compiler".

## Branch totals

✅ `git diff --stat main..HEAD`:

```
39 files changed, 1946 insertions(+), 1965 deletions(-)
```

Net **−19 lines** across the branch. Confirmed independently by summing `git diff --numstat` →
`1946 1965`.

Note the near-identical but distinct figure for `75dd04d` alone: **+1948 / −1963** (net −15). The
two differ by the four lines that `f850ec0` and `b8beb7a` net out to. The whole point: a hardening
pass that fixed **9** of the register's first 10 defects, added 65 tests' worth of structure and *shrank*
the codebase. (Not 10: [[Non Compiling Commit]] was *introduced* by `75dd04d` and only fixed in
`b8beb7a`, so it cannot be counted among that pass's fixes.)

## What was uncommitted at the time

All of this has since landed in `efb35cc`. ✅ At the moment this page was first written,
`git status --porcelain` had five entries:

| Path | `git diff --numstat` |
|---|---|
| `CHANGELOG.md` | +16 / −1 |
| `services/ProxyCredentialsStorage.kt` | +7 / −3 |
| `services/ProxyThemAllStartupService.kt` | +7 / −2 |
| `services/gradle/GradlePropertiesText.kt` | +27 / −4 |
| `services/gradle/GradlePropertiesTextTest.kt` | +37 / −0 |
| `docs/` | untracked |

Those four source files were the four fixes of 2026-08-13: [[Startup Balloon]],
[[SOCKS Bypass Hosts Ignored]], the `warnIfMemoryOnly` move and the `detectLineSeparator` KDoc. They
are now part of `efb35cc`, which passes `./gradlew check` (68 tests).

✅ Net effect of the whole squash, measured `233c282..efb35cc`: **−60 lines under `src/`**
(35 files, +1899 / −1959). The whole-tree figure is **+64** (39 files, +2032 / −1968), the difference
being CHANGELOG and README prose. The load-bearing claim survives: the hardening pass shrank the
plugin's actual code.

**`docs/` is still not committed either.** That covers both `docs/VERIFICATION.md` (the manual test
contract, referenced from [[Manual Verification]]) and this entire vault at
`docs/proxythemall/ProxyThemAll/`. `git ls-files docs/` returns nothing.

## Related

[[Build Gates]] · [[Test Suite]] · [[Manual Verification]] · [[Defect Register]]

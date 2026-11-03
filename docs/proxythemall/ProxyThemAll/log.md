---
type: log
verified: 2026-11-03
verified-against: 1885550
---

# Log

Append-only. Newest last. Entry prefix is fixed so the log stays greppable:

```bash
grep "^## \[" log.md | tail -5
```

## [2026-08-11] ingest | Full audit of plugin vs its own docs

Traced every feature claimed in `README.md` and `CHANGELOG.md` through the code and against the
2024.3 platform bytecode. Feature set broadly present, but several headline features rested on
mechanisms that did not do what the docs said, and four defects could destroy data or leak
credentials.

Filed: [[Global Git Config Deleted]], [[Credentials In Log]], [[Gradle Block Duplication]],
[[JvmArgs Clobbered]], [[Restored Password Not Persisted]], [[Widget Project Leak]],
[[Per Project Listener Registration]].

Corrected doc claims: no `StartupManager` integration existed; "monitors IntelliJ's proxy settings"
was a `MessageBusConnection` subscribed to no topic; git `https.proxy`/`https.noproxy` are not real
config keys; "comprehensive test coverage" was ~10 tests asserting `assertTrue(true)`.

## [2026-08-11] ingest | Fixes in five phases

Data loss and credential leaks first, then leaks/races, then making advertised behaviour real, then
hygiene, then tests and docs. Test suite went from 44 tests (~10 fake) to 54 real. Added a Kover
floor — see [[Coverage Floor Not Target]].

Notable: the "ProxyService is untestable without mocking" comment in the codebase was wrong;
`ProxySettings` is an interface. See [[No Mocking Library]].

## [2026-08-12] ingest | Manual verification by the user found 2 more defects

The user executed `docs/VERIFICATION.md` end to end and marked four `==>` findings, which resolved to
two root causes: [[Change Detection Enum Only]] (three of the four findings) and
[[Foreign Lines Destroyed]].

Both were cases where the previous fix was *almost* right — the change-detection comparison had been
written one level below the gate that swallowed the event, so correct code was unreachable. Lesson
recorded in [[Change Detection]].

## [2026-08-12] ingest | Merge of main, then a non-compiling line

Merged `main` into the branch; the merge diff was **empty** — main's dependency updates were already
present. The reported "failing tests" were in fact a compile error, so the test task never ran.

[[Non Compiling Commit]]: `document.text = newContent` cannot compile because `Document.getText()`
returns `String` while `setText()` takes `CharSequence`. Committed once in `75dd04d`; `f850ec0`
then *added* an explanatory comment above the still-broken line without fixing it. `b8beb7a` fixed it
with `setText(...)` plus `@Suppress("UsePropertyAccessSyntax")` — the suppression matters because
IntelliJ's default-on "use property access syntax" inspection actively suggests the broken form.

Both run configurations now pass — see [[Build Gates]].

## [2026-08-12] ingest | Vault created

Instantiated this wiki on the LLM Wiki pattern: schema in [[CLAUDE]], catalog in [[index]], this log.
38 pages across `concepts/`, `components/`, `defects/`, `decisions/`, `operations/`.

Every behavioural claim was independently fact-checked against `b8beb7a` by four parallel agents
before being written, not transcribed from session memory. That pass produced corrections and new
findings, recorded in the next entry.

## [2026-08-12] lint | Fact-check pass produced corrections and new open risks

Corrections applied to claims that had been believed true earlier in the session:

| Believed | Actual |
|---|---|
| Git no-proxy filters `nonProxyHosts` | It filters `bypassHosts`, so the plugin's own `127.*` default is silently dropped from git config |
| "No `https.*` keys anywhere" | True for *git* only — Gradle deliberately writes `systemProp.https.*` |
| 16 of 28 files at zero coverage | 16 of the **27** files Kover reports (`ProxyStateChangeListener` is a body-less `fun interface`) |
| Branch is 6 commits ahead | **7** (six non-merge plus the merge); `233c282` is main's tip, not a branch commit |
| `VfsUtilCore.loadText` needs a read action | It does not — a code review asserted this from memory and was wrong |

New open risks found during verification, all filed in [[Defect Register]]:

- **SOCKS bypass hosts are ineffective** — written as `systemProp.http.nonProxyHosts` even for a SOCKS
  proxy. The existing SOCKS test passes with the bug present, because it only asserts the SOCKS host
  and port keys.
- `warnIfMemoryOnly` fires only on save, never on load — silent in exactly the startup restore session
  where the diagnosis would matter.
- Startup auto-restore uses the noisy reapply variant while manual restore uses the silent one.
- `detectLineSeparator` takes the first CRLF sighting; its KDoc claims "dominant".
- ⚠️ Unconfirmed: measurement suggests git may ignore `http.noproxy` entirely, not merely glob-blindly.
  Tested on Apple Git-155 / 2.50.1 only — needs a second git build before being filed as a defect.

## [2026-08-12] lint | Adversarial audit of the vault itself

A fifth agent was pointed at the finished vault with instructions to find errors, not confirm them:
~30 sampled `file:line` citations reopened against the code, every named test checked for existence,
every numeric claim recomputed. It confirmed the bulk (all per-class test counts, coverage figures,
version numbers, the 5 experimental-API usages, all 30+ test names, every frontmatter field) and
found **6 real errors**, all now fixed:

| # | Error | Correction |
|---|---|---|
| 1 | `libs.versions.toml:5` cited for the junit4 rationale | It is on `:4`; `:5` is `kotlinxSerialization` |
| 2 | [[Notifications]] said "five messages" | Six exist — the page's own table already listed six |
| 3 | [[Non Compiling Commit]] said `f850ec0` "kept the comment while reverting the code" | False. `f850ec0` *added* the comment above an already-broken line |
| 4 | [[Session History]] said `f850ec0` "removed the comment and reintroduced the broken form" | Also false, and it contradicted #3 — two pages, two incompatible stories, neither matching git |
| 5 | "a hardening pass that fixed 10 defects" | 9. [[Non Compiling Commit]] was *introduced* by that pass |
| 6 | `log.md` and `CLAUDE.md` missing `verified-against` | Added |

Errors 3 and 4 are the instructive ones. Both were narratives reconstructed from a *recollection of a
diff* rather than from the repository, and a diff read from memory is not evidence. The settled method
is to count the artefact in each commit's blob:

```
$ for c in 75dd04d f850ec0 b8beb7a; do git show $c:$FILE | grep -c 'document.text'; done
1  1  0
```

Lesson for future ingests, now also a rule in [[CLAUDE]]: **historical claims need blob-level
evidence, exactly as behavioural claims need `file:line`.** The wiki caught its own errors only
because a pass was run whose explicit job was to disbelieve it.
## [2026-08-13] ingest | Four fixes for the risks the previous lint pass had left open

All four applied and green — `./gradlew check` → **68 tests** (was 65), 0 failures. ⚠️ **Uncommitted**:
they landed on `main` in the squash-merge `efb35cc`, so pages describing them carry
`verified-against: efb35cc`. Two got their own pages; the register now lists **12
defects**.

| Fix | Page | Guard |
|---|---|---|
| Startup emitted a balloon on every launch, once per open project | [[Startup Balloon]] | none automated — manual §2.5 (new) |
| A SOCKS proxy ignored the user's own bypass list in Gradle | [[SOCKS Bypass Hosts Ignored]] | 3 new unit tests + manual §5.8b (new) |
| `warnIfMemoryOnly` fired only on save | — | none; moved into `createCredentialAttributes()` (`ProxyCredentialsStorage.kt:194-200`), which all four storage paths already shared |
| `detectLineSeparator` KDoc said "dominant", code was any-CRLF | — | resolved **doc-only** (`GradlePropertiesText.kt:236-244`); the code is deliberately unchanged, because `removeManagedSection` normalises separators anyway (`:96-97`) so counting would not fix a mixed file either |

The SOCKS fix needed **two** parts, and the second is the one that is easy to miss: writing
`systemProp.socksNonProxyHosts` in the `isSocks` branch (`:187`) *and* registering it in `OWN_KEYS`
(`:44`). `isOwnLine` drives removal, so a key the plugin writes but does not claim is classified as a
foreign user line and **rescued** by `removeManagedSection` — the bypass list would have survived
disabling the proxy forever. Evidence for the key itself is the JDK source, ✅ read at
`$JAVA_HOME/lib/src.zip` → `sun/net/spi/DefaultProxySelector.java:137` and `:200-201` (the `socket`
scheme dispatches to `socksNonProxyInfo`); `conf/net.properties` does **not** document it, which is
much of why it was missed. ✅ Also reproduced end to end against a real server with a dead SOCKS proxy:
without the key `SocketException: Can't connect to SOCKS proxy`, with it HTTP 200 direct.

### Two lessons, both about how the defects hid rather than what they were

**1. A mis-framing hid the real scope.** [[Startup Balloon]] was originally filed as *"startup restore
is noisy vs manual restore silent"* — an **inconsistency between two paths**, logged as cosmetic. That
framing was wrong: the noisy call sat in `performStartupCleanup()`, which runs on **every** startup
unconditionally (`ProxyThemAllStartupService.kt:62`), not inside the restore branch. So startup was
noisy whether or not a restore happened, and the worst case was a user who had *never configured a
proxy* being told "Proxy Disabled — You are now not using any proxy" at every launch, with
`showNotifications` defaulting to `true`. An "A and B disagree" framing invites you to compare A and B
and stop; it never prompts *how often does A even run?* Re-derive each path's trigger condition from
the code before accepting a severity.

**2. A passing test that asserted the wrong thing.**
`GradlePropertiesTextTest.socks proxy is written as socks system properties` existed, was named for
SOCKS, lived exactly where SOCKS coverage belongs, and even checked that no HTTP proxy keys leak —
which reads as thoroughness. It never inspected the bypass list, so **it passed with the bug present**,
and it passes unchanged after the fix. Only assertions are evidence, and only for what they name; a
green test in the right file proves nothing about the rest of that file's behaviour. Compare
[[Change Detection Enum Only]], where correct code sat *below* the gate that swallowed the event — same
shape of blindness, different axis.

Also corrected while ingesting: the earlier claim that SOCKS bypass failure meant "traffic to
`localhost` and friends goes through the proxy anyway" was **wrong in the user-visible direction**. The
JDK appends its own defaults (`DefaultProxySelector.java:129`), so local hosts always bypassed. What
was lost was exactly the user's *own* exception list — less dramatic, more consequential.

Found stale during this pass and fixed, though not part of the four: [[Session History]]'s "Uncommitted"
section still claimed a single `?? docs/` entry with clean source; the coverage figures in
[[Coverage Floor Not Target]] and [[Build Gates]] (26.43 % → 26.54 %, 87 → 89 covered lines in
`GradlePropertiesText.kt`); and [[Multi-Project Behaviour]]'s implication that no caller still reaches
the noisy fan-out — ✅ the settings panel does (`ProxyThemAllConfigurable.kt:148`), which is why that
page now names it explicitly instead of calling the method dead.

## [2026-08-13] ingest | Code merged to main as efb35cc; wiki re-stamped

The hardening work was squash-merged into `main` as a single commit, `efb35cc` ("Fix data-loss,
credential-leak and stale-state defects across the proxy sync layer (#79)"). The vault was updated to
match:

- All 40 pages' `verified-against` re-stamped from `b8beb7a` / `b8beb7a+working-tree` to `efb35cc`.
- Every "not yet committed" / "on disk but uncommitted" statement removed — the four final fixes
  ([[Startup Balloon]], [[SOCKS Bypass Hosts Ignored]], the `warnIfMemoryOnly` move, the
  `detectLineSeparator` KDoc) are now on `main`.
- ✅ `git diff --stat d14c799 efb35cc` is empty, so the squash preserved the tree exactly and **no
  behavioural claim in the vault was invalidated** — only claims about *where the code lives*.

Two numbers were corrected in the process, both of which had been wrong before the merge:

| Claim | Corrected |
|---|---|
| "net −19 lines" | **−60 under `src/`** (+1899 / −1959); whole-tree is **+64**, the difference being CHANGELOG and README prose |
| Coverage "measured at `b8beb7a`" | 26.54 % is the post-fix tree; `b8beb7a` itself was 26.43 % |

**On the dead shas.** The squash discarded `75dd04d`, `f850ec0`, `b8beb7a`, `ef3341b` and `d14c799` —
a fresh clone cannot `git show` them. `233c282`, which most pre-fix citations use, *is* still reachable.
The unreachable shas are kept in [[Non Compiling Commit]] and [[Session History]] because the history
they document is real and explains how the defects arose; both pages now say so explicitly rather than
leaving a reader to discover the sha does not resolve.

## [2026-11-03] ingest | IPGP 2.19.0 exposes setProxyConfiguration as Internal on 2026.3

Bumping the IntelliJ Platform Gradle Plugin from 2.18.1 to 2.19.0 (branch
`fix/verify-plugin-internal-api`, `e000edb..e6c7763`) widened the verifier's default `recommended()`
IDE selection from 3 IDEs to 7 — the 2.19.0 changelog calls the old selection a regression. The new
IDE, `IU-263.7307.112` (2026.3 EAP), exposed two findings:

1. **`ProxySettings.setProxyConfiguration` is `@ApiStatus.Internal` on 2026.3**, not
   `@ApiStatus.Experimental` as on every IDE back to 243. Upstream commit `61904c9bed` (2026-08-03)
   made the change. `-ignoredProblemsFile` cannot filter internal-API-usage reports (it only matches
   `CompatibilityProblem`), so the fix narrows `failureLevel` (`build.gradle.kts:120`) to exclude
   `INTERNAL_API_USAGES` and adds a hand-written allowlist guard on `tasks.verifyPlugin`
   (`build.gradle.kts:168-202`) that still fails the build if a sixth usage, or any usage outside the
   5 grandfathered call sites, appears anywhere.
2. **2026.3 moved VCS into a bundled plugin** behind the `com.intellij.modules.vcs` alias.
   `Git4Idea`'s main module no longer pulls in `ChangeListManager` transitively, so
   `plugin.xml:8` now declares `<depends>com.intellij.modules.vcs</depends>` directly. Plugin
   Verifier 1.411 still reports 6 unresolved-class problems against 263 regardless (a known verifier
   false positive, platform.jetbrains.com topic 5250), ignored via `verifier-ignored-problems.txt`
   (`build.gradle.kts:123`).

Both findings are documented in [[Platform API Constraints]] (§3 rewritten, §11 new), [[Build Gates]]
Gate 2 (7-IDE table), [[ProxyService]], and two new open risks in [[Defect Register]]: the
grandfathering end-date (⚠️ inferred from a JetBrains forum post, no fix possible yet) and the
2026.3 classloader-resolution path (⚠️ inferred from source, not executed — mitigated by a new manual
step, `docs/VERIFICATION.md` §6.6). `CLAUDE.md` updated to match: the stale "Experimental" convention
note, the verifier IDE count, the manual-plan step count (48 → 49), and a new note on
`verifier-ignored-problems.txt` as a verifier-false-positive workaround only.

Pages touched: `concepts/Platform API Constraints.md`, `operations/Build Gates.md`,
`operations/Manual Verification.md`, `components/ProxyService.md`, `defects/Defect Register.md`,
`index.md`, root `CLAUDE.md`, `docs/VERIFICATION.md`. All re-stamped `verified-against: e6c7763`.

## [2026-11-03] lint | Whole-branch review findings: wrong failureLevel default, overclaimed guard guarantee, untestable manual step

A final whole-branch review of `fix/verify-plugin-internal-api` (HEAD at the time `1885550`) found
several claims in the pages this entry's predecessor touched that were wrong or untestable, plus
drift unrelated to the 2.19.0 bump that a citation grep caught along the way.

1. **§2 said the Plugin Gradle Plugin's own default `failureLevel` is `ALL`. It is not.** ✅ Read
   straight from upstream source,
   `IntelliJPlatformExtension.kt`'s `failureLevel.convention(listOf(FailureLevel.COMPATIBILITY_PROBLEMS,
   FailureLevel.INTERNAL_API_USAGES, FailureLevel.OVERRIDE_ONLY_API_USAGES))` — no `ALL` value appears
   anywhere in that convention. [[Platform API Constraints]] §3 corrected; the false `ALL` claim is
   kept nowhere else, there was nothing to mark superseded. Also removed: every `Task 1`/`Task 2`/
   `Task 3` reference to this work's own plan ([[Platform API Constraints]] §3, [[Defect Register]]'s
   `setProxyConfiguration` grandfathering risk) — the wiki describes the code, not a plan's task
   numbers.
2. **The log entry above (this file, line 219) overclaims the allowlist guard: "a sixth usage,
   or any usage outside the 5 grandfathered call sites, appears anywhere" fails the build.** That is
   wrong, and is corrected here rather than by editing that entry. ⚠️ By reviewer `javap` against
   Plugin Verifier 1.411, `InternalMethodUsage` equality covers only the API element and the *usage
   location* — a calling method, with no line number (the collection it is deduplicated through is
   inferred to be `Set`-like from the output shape, not executed). A second call to
   `setProxyConfiguration` added inside one of the five already-allowed methods produces no new report
   line, so it is invisible to the guard — and, since Marketplace review runs the same verifier, also
   invisible to Marketplace. The real guarantee: a usage reported from any **new or renamed calling
   method** fails. Controller ruling: `build.gradle.kts`'s `lines.size > 5` check
   (`build.gradle.kts:186-188`) stays as a backstop against the inferred dedup-by-method behaviour
   turning out wrong, or a future verifier release reporting per-call-site instead of per-method — it
   was not touched. [[Platform API Constraints]] §3 and [[Build Gates]] Gate 2 reworded to the narrower
   claim.
3. **`docs/VERIFICATION.md` §6.6 used `./gradlew runIde -PplatformVersion=263.7307.112`, which
   recompiles against 263 and never tests the artifact Marketplace would receive.** Controller ruling:
   rewritten to `./gradlew buildPlugin`, then install the resulting
   `build/distributions/ProxyThemAll-<version>.zip` from disk into a real IntelliJ IDEA 2026.3 EAP via
   Settings → Plugins → ⚙ → Install Plugin from Disk…, restart, then exercise 6.1/6.3's changelist
   move and cleanup. Expected result narrowed to the concrete class,
   `com.intellij.openapi.vcs.changes.ChangeListManager`, and the log path corrected to the installed
   IDE's own log (macOS: `~/Library/Logs/JetBrains/IntelliJIdea2026.3/idea.log`), not the sandbox's.
   [[Defect Register]]'s matching mitigation description updated to match.
4. **§3's quoted verifier line abbreviated `com.intellij.util.net.ProxyConfiguration arg0` to
   `ProxyConfiguration arg0`.** Made verbatim against
   `build/reports/pluginVerifier/IU-263.7307.112/.../internal-api-usages.txt`.
5. **`build.gradle.kts` citations drifted, not all from this branch's own edits.** Re-grepped every
   `build.gradle.kts:<line>` citation in the vault against the file on disk at `1885550`.
   `verifyPlugin`/allowlist/message citations (`:168-202`, `:173-179`, `:197`, `:120`, `:123`) were
   already correct — no change. Genuinely stale, predating this branch: the `kover {}` block citation
   (`:127-146` → `:133-153`), its "Floor, not a target" comment (`:134-135` → `:141-142`, both in
   [[Build Gates]] and [[Coverage Floor Not Target]]), and the junit/junit4 dependency lines in
   [[Build Gates]] and [[Test Suite]] (`:37` → `:38`, `:42`/`:39-41` → `:43`/`:40-42`). Not restamped:
   [[Test Suite]] and [[Coverage Floor Not Target]] keep `verified-against: efb35cc` — only their
   citations were checked, not their `efb35cc`-era coverage/test-count claims.
6. **[[Build Gates]] claimed all seven IDEs print "Plugin can probably be enabled or disabled without
   IDE restart."** That line is Plugin Verifier console output, written to no report file, so it
   cannot be re-checked after the fact for `IU-263.7307.112` specifically. Marked ⚠️ for that one IDE
   in [[Build Gates]] and [[Lifecycle and Leaks]] rather than asserted as checked.
7. **[[Build Gates]]'s Versions table was stale against `gradle.properties` and
   `gradle/libs.versions.toml`** at `1885550`: `pluginVersion` `0.0.6` → `0.0.7`, `kotlin` `2.4.10` →
   `2.4.21`, `kotlinxSerialization` `1.11.0` → `1.12.0`, `kover` `0.9.9` → `0.9.11`, `qodana` `2026.2.0`
   → `2026.2.2`. `intelliJPlatform`, `changelog`, `junit`, `junit4` were already current.

**Found, not fixed — out of this pass's scope.** [[Build Gates]] Gate 3's CI table still cites
`JetBrains/qodana-action@v2026.2.0`; the workflow pins `v2026.2.2`. That table wasn't named by this
review and isn't a `build.gradle.kts` citation, so it was left for a future pass rather than fixed
on spec.

Pages touched: `concepts/Platform API Constraints.md`, `operations/Build Gates.md`,
`defects/Defect Register.md`, `concepts/Lifecycle and Leaks.md`, `operations/Test Suite.md`,
`decisions/Coverage Floor Not Target.md`, `docs/VERIFICATION.md`. Re-stamped
`verified-against: 1885550`: `Platform API Constraints.md`, `Build Gates.md`, `Defect Register.md`,
`Lifecycle and Leaks.md`.

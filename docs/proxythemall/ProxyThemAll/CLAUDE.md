---
type: schema
verified: 2026-08-12
verified-against: efb35cc
---

# Wiki Schema — how to maintain this vault

This vault is an LLM-maintained knowledge base about the **ProxyThemAll** IntelliJ plugin, built on
the [LLM Wiki](https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f) pattern. Read this
file first in any session that touches the vault.

Working on the *plugin* rather than the vault? Start at the repository root `CLAUDE.md`, which covers
build commands, architecture and the codebase-specific rules, and links back here.

## Three layers

| Layer | Location | Who owns it |
|---|---|---|
| **Raw sources** | the plugin's own `src/`, `docs/VERIFICATION.md`, git history, platform bytecode | Immutable. Read, never rewrite from here. |
| **The wiki** | every `.md` in this vault | The LLM owns it entirely. |
| **The schema** | this file | Co-evolved by human + LLM. |

The source of truth is **always the code on disk**, never a wiki page. Pages go stale; the compiler
does not.

## Directory layout

```
.
├── CLAUDE.md              # this schema
├── index.md               # catalog of every page (read this first when answering)
├── log.md                 # append-only chronological record
├── Overview.md            # entry point: what the plugin is, how it hangs together
├── concepts/              # mechanisms and platform knowledge (transferable)
├── components/            # one page per production class / subsystem
├── defects/               # one page per defect, plus the register
├── decisions/             # design decisions and their rationale
└── operations/            # build gates, testing, manual verification
```

## Page conventions

Every page starts with YAML frontmatter so Dataview can query it:

```yaml
---
type: component | concept | defect | decision | operation | schema | index | log
status: current | fixed | open | superseded
tags: [git, gradle, threading, ...]
verified: YYYY-MM-DD        # when a human/LLM last checked this against the code
verified-against: <git sha>  # the commit the claims were checked against
---
```

Rules:

1. **Cite file:line for every factual claim** about behaviour. A claim without a citation is a
   guess and must be marked as such.
2. **Link liberally** with `[[Page Name]]`. Cross-references are the point of the wiki.
3. **Never delete a superseded claim** — mark it `status: superseded` and say what replaced it.
   The history of a wrong belief is often more useful than the correction alone.
4. **Distinguish verified from inferred.** Use ✅ verified (ran it / read the bytecode), ⚠️ inferred
   (reasoned but not executed). Say which.
5. Prefer short pages that link, over long pages that repeat.

## Operations

**Ingest.** A new source (code change, review, test run, upstream release) →
read it, update the affected `components/` and `concepts/` pages, add or update a `defects/` page,
update `index.md`, append to `log.md`.

**Query.** Read `index.md` → drill into pages → answer with citations. If the answer is durable,
**file it back** as a new page rather than leaving it in chat.

**Lint.** Periodically check for: contradictions between pages, claims whose `verified-against` sha
is far behind `HEAD`, orphan pages with no inbound links, concepts referenced but with no page, and
defects listed as `open` that are actually fixed.

The single highest-value lint for this vault: **re-run the fact-check.** Every behavioural claim
here was verified against a specific commit; code moves.

## Domain-specific rules for this vault

- **Never state a platform API behaves a certain way without checking the bytecode.** This project
  has been burned three times by plausible-but-wrong API assumptions (see
  [[Platform API Constraints]]). `javap` against the resolved `ideaIC` jars is the arbiter.
- **Security claims get their own scrutiny.** Anything about credentials, logging, or files written
  to disk must cite the exact line that writes or logs.
- **Record defect *root causes*, not symptoms.** A page that says "gradle.properties got corrupted"
  is useless; the page must say which function, which off-by-one, and why the sibling function
  differed.
- **Historical claims need blob-level evidence, just as behavioural claims need `file:line`.** Do not
  describe what a commit did from a recollection of its diff. Count the artefact in each commit:
  `git show <sha>:<path> | grep -c '<thing>'`. Two pages in this vault once told contradictory
  stories about the same commit, and neither matched the repository — see the audit entry in [[log]].
- When the wiki and `docs/VERIFICATION.md` disagree about expected behaviour, the manual test plan
  is the human-facing contract — reconcile deliberately, do not silently pick one.
- **Run an adversarial pass before trusting a batch of new pages.** An agent instructed to *find
  errors* rather than confirm them found six that four careful authoring passes had missed.

---
type: component
status: current
tags: [git, credentials, security, threading]
verified: 2026-08-12
verified-against: efb35cc
---

# GitProxyConfigurer

`src/main/kotlin/org/holululu/proxythemall/services/git/GitProxyConfigurer.kt`

Shells out to `git config` to write and remove the proxy. Reached through `GitProxyService`
(`GitProxyService.kt:39-76`), never directly from [[ProxyController]].

## Keys written

Exactly two, and only these:

| Key | Const | Line |
|---|---|---|
| `http.proxy` | `HTTP_PROXY` | `:16` |
| `http.noproxy` | `HTTP_NO_PROXY` | `:17` |

There is no `https.proxy` git config key — git routes HTTPS through `http.proxy` as well
(`:15`). Scope this claim carefully: the plugin **does** write `systemProp.https.proxyHost` /
`systemProp.https.proxyPort` for Gradle (`GradlePropertiesText.kt:166-168`). What it does not write is
git `https.*` keys, because they do not exist.

Writes happen at `:62` (proxy) and `:67-70` (noproxy, only when non-empty, `:66`).

## Removal and exit code tolerance

`removeGitProxySettings` — `:108-143`. Two `--unset-all` calls (`UNSET_ALL_FLAG`, `:19`) at `:122-127`.

`git config` exits **5** when the key to unset does not exist. `EXIT_CODE_KEY_MISSING = 5` (`:24`) is
whitelisted in `executeGitCommand`, which throws only when the exit code is neither 0 nor 5
(`:177-181`). So removing a key that was never set is not an error (`:121`, `:158-160`).

The status message distinguishes the two cases by checking `exitCode == 0` on each call
(`:124`, `:127`, `:129-135`): a 5 from both means "no proxy settings found".

## Scope logic — `--global` is unreachable with a project

Both write and remove compute the scope identically:

```kotlin
val scope = if (projectDir != null) emptyList() else listOf(GLOBAL_FLAG)  // :61 (set), :118 (remove)
```

`GLOBAL_FLAG = "--global"` (`:18`). `projectDir` comes from `getProjectDirectory()`, which returns the
project `basePath` as a `File` only if it exists and is a directory (`:148-154`).

Consequences:

- with a project open and a valid base path, the plugin writes **repo-local** config
  (`commandLine.workDirectory = projectDir`, `:173`) and `--global` is never passed
- `--global` is only reachable when `project == null`, `basePath == null`, or the base path is not an
  existing directory
- the remove path uses the same expression on purpose: falling back to `--global` there would delete a
  proxy the user configured themselves (`:116-117`)

The `target` string in notifications follows the same branch (`:73`, `:119`).

## noproxy filtering drops the plugin's own `127.*`

`gitNoProxyHosts` — `:98-102`:

```kotlin
proxyInfo.bypassHosts
    .map { it.trim() }
    .filter { it.isNotEmpty() && !it.contains('*') }
    .joinToString(GIT_HOSTS_SEPARATOR)   // "," at :21
```

Two facts worth stating precisely:

1. It reads `bypassHosts`, **not** `nonProxyHosts`. `bypassHosts` is `ESSENTIAL_BYPASS_HOSTS +
   nonProxyHosts` (`ProxyInfo.kt:29-30`), so the plugin's own essential entries are included before
   filtering.
2. Any entry containing `*` is dropped, because git's `http.noproxy` matches plain host and domain
   names only — passing globs through would be silently ineffective (`:95-96`, `:64`).

`ESSENTIAL_BYPASS_HOSTS = setOf("localhost", "127.*", "[::1]")` — `ProxyInfo.kt:23`. Therefore
**`127.*` is silently dropped from git config**, while `localhost` and `[::1]` survive. Any
user-configured glob (`10.*`, `*.internal`) is dropped too. Gradle keeps all of them
(`GradlePropertiesText.kt:172`) — the two integrations diverge here by design. ⚠️ the "silently
dropped" consequence is inferred from the filter plus the constant; the filter itself is ✅ verified.

Loopback traffic still bypasses via `localhost`, but a literal `127.0.0.1` URL does not match the
dropped glob and will be sent to the proxy. Practical impact depends on the proxy honouring the
CONNECT.

## Credential handling

`proxyUrl` is built by `ProxyUrlBuilder.buildProxyUrl(proxyInfo)` (`:52`,
`ProxyUrlBuilder.kt:19-28`) and embeds URL-encoded credentials when present
(`ProxyUrlBuilder.kt:40-48`).

Logging discipline:

- the success log deliberately prints host and port only, never `proxyUrl` — `:80-81`
- notification status text mentions "with authentication" but no values — `:74-78`
- failures log the exception, not the URL — `:84`, `:137`

**Residual channel:** on a non-tolerated exit code, git's stderr is logged verbatim:

```kotlin
val errorMessage = "Git command failed with exit code ${processOutput.exitCode}: ${processOutput.stderr}"
LOG.warn(errorMessage)      // :179
throw RuntimeException(errorMessage)   // :180
```

`:178-180`. The `proxyUrl` is an argument on the command line, so anything git echoes back into stderr
(and the message then also propagates through the thrown exception) can carry it. This is the one
place where a credential-bearing string could reach the log. ⚠️ inferred — whether git echoes the
value depends on the failure mode. See [[Credential Handling]].

## Threading

Both operations run inside `Task.Backgroundable(project, ..., false)` (`:56`, `:112`) to avoid EDT
violations (`:55`, `:111`), and both bodies hold `synchronized(gitOperationLock)` (`:44`, `:58`,
`:114`) so concurrent git invocations cannot interleave. Each terminates by calling `onComplete` on
every path — success (`:82`, `:131`/`:134`) and failure (`:86`, `:138`) — which is what keeps
[[ProxyController]]'s two-callback join from stalling.

The git executable comes from `GitExecutableManager.getInstance().pathToGit`, falling back to the
literal `"git"` on failure (`:164-169`). Timeout is 10 s (`:175`).

## See also

[[Git Proxy Integration]] · [[Credential Handling]] · [[GradlePropertiesText]] · [[ProxyController]]

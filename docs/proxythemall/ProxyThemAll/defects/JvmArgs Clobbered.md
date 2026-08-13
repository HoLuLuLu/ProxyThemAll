---
type: defect
status: fixed
severity: high
tags: [gradle, data-loss, properties-semantics]
verified: 2026-08-12
verified-against: efb35cc
---

# JvmArgs Clobbered

The plugin appended its own `org.gradle.jvmargs`, and in a `.properties` file the later duplicate key
wins — silently discarding the user's heap settings.

## Symptom

User has:

```properties
org.gradle.jvmargs=-Xmx4g
```

Enable the proxy. The file now ends with:

```properties
# === ProxyThemAll Managed Proxy Settings - START ===
...
# JVM arguments for IDE ProxySelector/Authenticator fallback
org.gradle.jvmargs=-Djava.net.useSystemProxies=true
# === ProxyThemAll Managed Proxy Settings - END ===
```

The user's line is still visibly there. The next build OOMs anyway, because Gradle now runs with the
default heap and no `-Xmx4g`. The user looks at the file, sees their setting present, and does not
suspect the plugin.

## Root cause

`java.util.Properties` has last-key-wins semantics: on duplicate keys, `load()` overwrites, so the
*last* occurrence is the effective value. The plugin always appended its section at the end of the
file, therefore its `org.gradle.jvmargs` was always the last one, therefore it always won.

In `233c282` the key was emitted unconditionally from `buildProxySettingsContent`
(constants at `233c282:.../gradle/GradleProxyConfigurer.kt:49-50`), with no inspection of existing
content. There was no notion of "the user might already own this key".

Note the asymmetry with the rest of the section: every other key the plugin writes
(`systemProp.http.proxyHost` and friends) is one a user has essentially no reason to set by hand.
`org.gradle.jvmargs` is the single most commonly hand-set property in a `gradle.properties`, and it was
treated identically to the rest.

## Why it survived

- **The corruption is invisible in the file.** The user's line is not deleted, not commented out, not
  moved. Reading the file tells you nothing is wrong; you have to know `.properties` duplicate-key
  semantics to see the bug. A reviewer scanning the diff sees "we add a jvmargs line" and nothing
  screams.
- **The symptom lands far away in time and space** — a Gradle OOM in a build, minutes later, with a
  stack trace that has nothing to do with proxies. Nobody attributes it to the proxy plugin.
- **The key was cargo-culted as a fallback.** `-Djava.net.useSystemProxies=true` exists so Gradle picks
  up the IDE's ProxySelector when credentials are unavailable. That rationale is about *whether* to set
  the flag, and it never prompted the question *what if the key is taken*.
- No test parsed the output with `java.util.Properties`. Tests that assert on substrings
  (`content.contains("jvmargs")`) pass happily on a duplicated key — only a real `Properties.load()`
  exposes which value wins.

## Fix

Two halves — write side and remove side — because ownership must be consistent in both directions.

**Write:** the key is only emitted when the file has no other `jvmargs`
(`src/main/kotlin/org/holululu/proxythemall/services/gradle/GradlePropertiesText.kt:67-70`):

```kotlin
// Only manage the JVM args key when the user has not set it themselves - a duplicate key
// wins in a .properties file and would silently drop their -Xmx settings.
val includeJvmArgs = !hasOwnJvmArgs(base)
```

`hasOwnJvmArgs` (`GradlePropertiesText.kt:197-198`) scans the content **after** the managed section was
removed, so the plugin's own previous line is not mistaken for the user's — that ordering is what makes
repeated applies stable.

`includeJvmArgs` is an explicit parameter of `buildSection` (`GradlePropertiesText.kt:149`,
consumed at `:185-188`), not a hidden field, so a caller cannot forget it.

**Remove:** ownership is **value-gated**, not key-gated (`GradlePropertiesText.kt:138-141`):

```kotlin
val key = trimmed.substringBefore('=').trim()
// The plugin only manages org.gradle.jvmargs when it holds exactly our own value; a user
// setting such as -Xmx4g must never be treated as ours
if (key == JVM_ARGS_KEY) return trimmed.substringAfter('=', "").trim() == PROXY_SELECTOR_ARG
```

Every other managed key is matched by key alone via `OWN_KEYS` (`GradlePropertiesText.kt:35-40`);
`jvmargs` is the one exception, and the reason is stated inline. Without this, a user's `-Xmx4g` line
that happened to sit inside the block would be deleted on removal — the write-side fix alone would have
left that hole.

## Regression guard

`GradlePropertiesTextTest`:

- `existing org gradle jvmargs is not overridden` — asserts exactly one `org.gradle.jvmargs` line **and**
  parses with `java.util.Properties` to assert the effective value is `-Xmx4g`. The `Properties.load()`
  assertion is the load-bearing part; a `contains` check would not have caught the original bug.
- `user jvmargs inside the block survive but ours are removed` — covers the value-gated removal, and
  first asserts the plugin's own `jvmargs` line is present so the test cannot pass vacuously.

Manual: `docs/VERIFICATION.md` §5.4 "Your JVM args survive" (`grep -c` expects 1, `grep` expects
`-Xmx4g`).

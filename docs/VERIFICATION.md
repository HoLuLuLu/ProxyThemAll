# ProxyThemAll — Manual Verification Plan

Every advertised feature, one test each. Work top to bottom; later sections assume the setup from
Section 0.

Legend: **Do** = your actions · **Expect** = what must happen · ❌ = known bug this guards against

---

## 0. Setup (once, ~10 min)

### 0.1 Build and launch

```bash
cd ~/IdeaProjects/plugins/ProxyThemAll
./gradlew test verifyPlugin      # must both succeed before manual testing
./gradlew runIde                 # sandbox IDE, your real settings untouched
```

### 0.2 Open the two test projects

Both already exist under `~/IdeaProjects/`. Nothing to create — just open them.

| Project      | Contents                                                          | Purpose                    |
|--------------|-------------------------------------------------------------------|----------------------------|
| `pta-gradle` | `build.gradle`, `settings.gradle`, `gradle.properties`, git repo  | Gradle + Git paths         |
| `pta-plain`  | `notes.txt` only — no gradle, no git                              | non-Gradle / non-Git paths |

In the sandbox IDE: <kbd>File</kbd> > <kbd>Open</kbd> > `~/IdeaProjects/pta-gradle`, then repeat for
`pta-plain` choosing **New Window** so **both are open at once** (Section 8 needs this).

If the IDE offers to import/sync the Gradle project, accept — not required, but harmless.

`pta-gradle` starts with a committed, clean tree and this baseline `gradle.properties`:

```properties
org.gradle.caching=true
my.last.property=keep-me
```

Those two lines are the ones Section 5.3 checks survive intact.

**Reset between runs** (returns both projects to their starting state):

```bash
cd ~/IdeaProjects/pta-gradle && git checkout -- . && git clean -qfd
```

### 0.3 Fake proxy details

Nothing needs to listen on this port. Use throughout:

```
Host: proxy.test.local     Port: 8080
User: test\user            Password: p@ss word/1
Exceptions: localhost,build.example.com,10.*
```

### 0.4 Reference points

- Settings: <kbd>Settings</kbd> > <kbd>Tools</kbd> > <kbd>ProxyThemAll</kbd>
- IDE proxy: <kbd>Settings</kbd> > <kbd>Appearance & Behavior</kbd> > <kbd>System Settings</kbd> > <kbd>HTTP Proxy</kbd>
- Action: <kbd>Tools</kbd> menu, bottom
- Log: `.intellijPlatform/sandbox/ProxyThemAll/IC-*/log/idea.log`

Keep a terminal open on the log:

```bash
tail -f .intellijPlatform/sandbox/ProxyThemAll/IC-*/log/idea.log | grep -i proxythemall
```

### 0.5 Why some steps say "wait ~3 s"

The IntelliJ Platform publishes no event when proxy settings change, so edits you make in the
**HTTP Proxy** dialog are picked up by a check that runs every 2 s. Changes triggered **through the
plugin** (Tools menu, widget) apply immediately.

So: after clicking Apply in the HTTP Proxy dialog, wait ~3 s before judging the result. A step that
still fails after ~10 s is a real failure.

---

## 1. Settings panel

### 1.1 All options present and persist

**Do** Open Settings > Tools > ProxyThemAll. Note defaults, then restart the IDE and reopen.

**Expect** Six controls, defaults exactly as below, unchanged after restart:

| Control                                     | Default    |
|---------------------------------------------|------------|
| Show notifications when proxy state changes | ✅ on      |
| Show status bar widget                      | ✅ on      |
| Apply proxy settings to Git                 | ✅ on      |
| Apply proxy settings to Gradle              | ⬜ **off** |
| Allow global Gradle configuration fallback  | ⬜ **off** |
| "Clear Stored Proxy Configuration" button   | —          |

### 1.2 Credential warning is visible

**Expect** Under "Apply proxy settings to Gradle": a **Warning** that credentials are written in
plain text to `gradle.properties` and that the changelist is not a security boundary.

---

## 2. Proxy state detection & toggle

### 2.1 NOT_CONFIGURED state

**Do** IDE proxy = "No proxy". <kbd>Tools</kbd> menu — read the entry, then click it.

**Expect**
- Menu reads **"Configure Proxy (ProxyThemAll)"**
- Balloon **"Proxy Configuration Required"** with button **Open HTTP Proxy Settings**
- No restore button yet (nothing backed up)

### 2.2 Enable proxy in the IDE

**Do** HTTP Proxy > Manual > HTTP, fill in host/port/exceptions from 0.3, tick
"Proxy authentication" and enter user/password. Apply. Wait ~3 s.

**Expect** Widget switches to the enabled icon; log shows `backed up successfully`.

### 2.3 Toggle off, then on

**Do** <kbd>Tools</kbd> menu (read text) > click. Then again.

**Expect**

| Step | Menu text before click           | Result                                                          |
|------|----------------------------------|-----------------------------------------------------------------|
| 1st  | **Disable Proxy (ProxyThemAll)** | HTTP Proxy shows "No proxy"; balloon "Proxy Disabled"           |
| 2nd  | **Enable Proxy (ProxyThemAll)**  | Host/port/credentials **all restored**; balloon "Proxy Enabled" |

❌ Menu text used to be the static string "ProxyThemAll".

### 2.4 Exactly one balloon per toggle

**Do** Toggle off. Count balloons.

**Expect** **One** balloon. Not two, not zero.
❌ A lost/duplicated counter could produce zero or two.

### 2.5 No balloon on IDE startup ⚠️ key fix

**Do** Three restarts, checking for a balloon each time:

1. proxy configured and **enabled** → restart
2. proxy configured and **disabled** → restart
3. proxy **never configured** (HTTP Proxy = "No proxy") → restart

**Expect** **No balloon in any of the three cases.** The widget must still show the correct icon, and
a manual toggle afterwards must still produce exactly one balloon (2.4) — the announcement is for user
actions, not for startup reconciliation.
❌ Startup used the same notifying path as a manual toggle, so every launch produced a balloon — once
per open project. Case 3 was the worst: users who had never configured a proxy were told
"Proxy Disabled — You are now not using any proxy" at every single launch.

---

## 3. Status bar widget

### 3.1 Three distinct icons

**Do** Compare the widget in each state (no proxy configured / configured+off / on).

**Expect** Three visibly different icons.

### 3.2 Tooltip matches state

**Do** Hover in each state.

**Expect** `Proxy is enabled - Click to disable` / `Proxy is disabled - Click to enable` /
`Proxy is not configured`.

### 3.3 Click toggles

**Do** Click the widget twice.

**Expect** Toggles both ways, same as the menu; icon updates immediately.

### 3.4 Hide/show without restart ⚠️ key fix

**Do** Settings > untick "Show status bar widget" > OK.

**Expect** Widget disappears **immediately**. **No** "restart required" dialog. Re-tick → reappears.
❌ Previously demanded an IDE restart.

---

## 4. Git integration

Run in `pta-gradle` (it has git). Terminal:

```bash
cd ~/IdeaProjects/pta-gradle
```

### 4.1 Proxy written with credentials

**Do** Enable proxy (Section 2.2), then:

```bash
git config --local --get-regexp '^http'
```

**Expect**
- `http.proxy http://test%5Cuser:p%40ss%20word%2F1@proxy.test.local:8080`
- `http.noproxy` present, contains `localhost` and `build.example.com`
- **No `https.proxy`** and **no `https.noproxy`** ❌ (not real git keys; used to be written)
- **No `10.*`** in noproxy ❌ (git cannot match globs)

### 4.2 Password encoding is RFC 3986

**Expect** in the value above: space is **`%20`**, not `+`. Backslash is `%5C`.
❌ Form encoding made `p@ss word` authenticate as `p@ss+word`.

### 4.3 Credentials never reach the log 🔒

```bash
grep -iE "p@ss|password@|:.*@proxy" .intellijPlatform/sandbox/ProxyThemAll/IC-*/log/idea.log
```

**Expect** **No output.** Any hit is a security regression.

### 4.4 Global git config is never touched ⚠️ key fix

**Do**

```bash
git config --global http.proxy "http://manual.example.com:3128"   # pretend it's yours
```

Now **disable** the proxy in the IDE, wait 3 s, then:

```bash
git config --global --get http.proxy    # must still print manual.example.com:3128
git config --local  --get http.proxy    # must print nothing
git config --global --unset http.proxy  # cleanup
```

**Expect** Global value **survives**; local one is gone.
❌ This was the worst bug: `git config --unset` exits 5 for a missing key, which aborted the local
removal and fell through to wiping your `--global` proxy.

### 4.5 Disabling Git integration cleans up

**Do** Enable proxy. Settings > untick "Apply proxy settings to Git" > OK. Wait 3 s.

```bash
git config --local --get-regexp '^http'   # expect nothing
```

**Expect** Keys removed without disabling the whole proxy. Re-tick to continue.

### 4.6 Non-git project degrades quietly

**Do** With the proxy on, look at `pta-plain`.

**Expect** No error balloon, no `LOG.error`/fatal-error dialog. Log may show a warning.

---

## 5. Gradle integration

Enable it first: Settings > tick **Apply proxy settings to Gradle** > OK.

### 5.1 Properties written correctly

**Do** Enable the proxy. Open `~/IdeaProjects/pta-gradle/gradle.properties`.

**Expect** One block between
`# === ProxyThemAll Managed Proxy Settings - START ===` and `... - END ===`, containing:
- `systemProp.http.proxyHost` / `Port` and `systemProp.https.proxyHost` / `Port`
- `systemProp.http.nonProxyHosts` pipe-separated, includes `10.*` (Gradle *does* support globs)
- `systemProp.http.proxyUser` / `proxyPassword` (+ https variants) — **plain text, expected**
- Backslash escaped: `test\\user`

### 5.2 No duplicate block after repeated toggles ⚠️ key fix

**Do** Toggle the proxy off/on **five times**. Then:

```bash
grep -c "ProxyThemAll Managed Proxy Settings - START" ~/IdeaProjects/pta-gradle/gradle.properties
```

**Expect** **`1`**.
❌ Used to stack a new block on every toggle when the marker sat on line 1.

### 5.3 Your content survives ⚠️ key fix

**Do** With the proxy **off**, make `gradle.properties`:

```properties
org.gradle.caching=true
my.last.property=keep-me
```

Enable proxy. Confirm the block was appended **and** both lines are intact. Disable proxy.

**Expect** File back to exactly the two lines above — byte for byte, including the final newline.
❌ The line directly above the START marker used to be deleted.

### 5.4 Your JVM args survive ⚠️ key fix

**Do** With the proxy off, set `gradle.properties` to:

```properties
org.gradle.jvmargs=-Xmx4g
```

Enable the proxy. Then:

```bash
grep -c "^org.gradle.jvmargs" ~/IdeaProjects/pta-gradle/gradle.properties   # expect 1
grep    "^org.gradle.jvmargs" ~/IdeaProjects/pta-gradle/gradle.properties   # expect -Xmx4g
```

**Expect** Exactly one such line, still `-Xmx4g`.
❌ A second key was appended and won, silently dropping your heap setting → OOM on next build.

### 5.5 Password with special characters round-trips

**Do** Set the IDE proxy password to `back\slash\` and re-enable the proxy. Inspect the file.

**Expect** Backslashes doubled (`back\\slash\\`). The key **after** the password line is still a
separate, readable key.
❌ A trailing backslash used to line-continue and swallow the next key.

### 5.6 Non-Gradle project skipped by default

**Do** With "Allow global Gradle configuration fallback" **off** and the proxy on, check:

```bash
ls ~/IdeaProjects/pta-plain/gradle.properties     # must not exist
```

**Expect** No file created. Log: `not a Gradle project - skipped`.

### 5.7 Global fallback when enabled

**Do** Tick "Allow global Gradle configuration fallback". Toggle the proxy off then on.

```bash
grep -A3 "ProxyThemAll Managed" ~/.gradle/gradle.properties
```

**Expect** Block present in the **global** file (honours `GRADLE_USER_HOME` if you set it).
**Then** disable the proxy → block removed. Untick the setting afterward.

### 5.8 SOCKS proxy uses SOCKS properties

**Do** IDE HTTP Proxy > Manual > select **SOCKS**, host/port as in 0.3. Apply. **Wait ~3 s** (the
periodic check picks the edit up; see 0.5). Check `~/IdeaProjects/pta-gradle/gradle.properties`.

**Expect** `systemProp.socksProxyHost` and `systemProp.socksProxyPort`, **without** toggling the
proxy off and on. **No** `systemProp.http.proxyHost`.
Switch back to HTTP afterward — the HTTP properties must return, again without a toggle.
❌ SOCKS used to be written as an HTTP proxy.
❌ The switch used to require a manual off/on toggle: the periodic check only reacted to
enabled/disabled transitions, so an edit that left the proxy enabled was ignored.

### 5.8b SOCKS bypass hosts use the SOCKS key ⚠️ key fix

**Do** With the SOCKS proxy from 5.8 still active, check the bypass keys:

```bash
grep "nonProxyHosts" ~/IdeaProjects/pta-gradle/gradle.properties
```

**Expect**
- `systemProp.socksNonProxyHosts=...` containing `build.example.com` and `localhost`
- **No** `systemProp.http.nonProxyHosts` while SOCKS is active

Then disable the proxy and re-check: **no `socksNonProxyHosts` line remains**.
❌ Only `http.nonProxyHosts` used to be written, which the JDK ignores for socket connections — so a
SOCKS user's exception list had no effect and builds were routed through the proxy anyway.
❌ The removal check matters separately: a key the plugin writes but does not claim ownership of would
be treated as a user's own line and survive disabling forever.

### 5.9 Line endings preserved (Windows / CRLF only)

**Do** Save `gradle.properties` with CRLF endings, toggle the proxy, then `git diff`.

**Expect** Only the managed block appears in the diff — not the whole file.

---

## 6. ProxyThemAll changelist

`pta-gradle` must be a git repo with `gradle.properties` **committed** first:

```bash
cd pta-gradle && git add -A && git commit -qm "baseline"
```

### 6.1 Change is filed under the changelist ⚠️ key fix

**Do** Enable the proxy. Open the <kbd>Commit</kbd> tool window. Wait up to ~5 s.

**Expect** A changelist named **ProxyThemAll** containing `gradle.properties`. The file is **not** in
"Default Changelist".
❌ The old code swapped your active changelist around the write — a race that filed it in your list.

### 6.2 Your active changelist is untouched

**Do** Before toggling, create a changelist "My Work" and make it active. Toggle the proxy.

**Expect** "My Work" is **still active** afterward, and does not contain `gradle.properties`.

### 6.3 Changelist removed when empty ⚠️ key fix

**Do** Disable the proxy. Wait ~5 s, watch the Commit window.

**Expect** `gradle.properties` returns to unmodified; the **ProxyThemAll changelist disappears**.
❌ It previously lingered forever because emptiness was checked before the VCS refresh.

### 6.4 Foreign changes are moved, never reverted 🔒

**Do** Put the proxy on (changelist exists). Add `my.note=1` to `gradle.properties` **inside the
managed block** (between the START and END markers), save, and **drag the file into** the
ProxyThemAll changelist. Disable the proxy.

**Expect** `my.note=1` is **still in the file** (the managed block is gone, your line remains), and
the file is in the Default Changelist.
❌ The old code reverted such changes, destroying concurrent edits.
❌ Removal then deleted *every* line between the markers, so a line placed inside the block was lost.

### 6.5 A foreign line outside the block also survives

**Do** Same as 6.4 but put `my.note=2` **below** the END marker. Disable the proxy.

**Expect** `my.note=2` still present. (Regression check for the ordinary case.)

---

## 7. Backup & restore (secure storage)

### 7.1 Automatic backup on change

**Do** Enable the proxy. Watch the log.

**Expect** `Proxy configuration backed up successfully`.

### 7.2 Backup refreshes on edit ⚠️ key fix

**Do** With the proxy **still enabled**, change only the **port** to `9090`. Apply. Wait ~3 s.
Do **not** toggle the proxy.

**Expect**
- Log shows another `backed up successfully`
- Log shows `Proxy configuration changed while ENABLED`
- `~/IdeaProjects/pta-gradle/gradle.properties` now shows `systemProp.http.proxyPort=9090`

❌ Only on/off transitions used to trigger this, so neither the backup nor the Gradle file updated
until the proxy was toggled off and on — and a restore could resurrect an old proxy.

### 7.2b An idle proxy causes no repeated work 🔒

**Do** Leave the proxy enabled and untouched. Count, wait ~60 s, count again:

```bash
grep -c "reapplying configuration" .intellijPlatform/sandbox/ProxyThemAll/IC-*/log/idea.log
```

**Expect** The count does **not** grow. Guards against the new change detection firing on every
2 s tick and rewriting Git and Gradle continuously.

### 7.3 Manual restore from notification

**Do** IDE proxy > "No proxy" > Apply. Wait 3 s. <kbd>Tools</kbd> > click the action.

**Expect** Balloon **"Proxy Configuration Required"** now offers **Restore Last Known Proxy
Settings** *first*. Click it → balloon **"Proxy Restored"**, and HTTP Proxy shows host, port **and**
credentials again.

### 7.4 Automatic restore on startup

**Do** With the proxy enabled and working, **close the sandbox IDE**. Simulate IntelliJ forgetting:

```bash
rm -f .intellijPlatform/sandbox/ProxyThemAll/IC-*/config/options/proxy.settings.xml
./gradlew runIde
```

**Expect** After startup, HTTP Proxy is populated again from secure storage.

### 7.5 Restored password persists across restart ⚠️ key fix

**Do** With a restored proxy, restart the sandbox IDE once more. Open HTTP Proxy.

**Expect** The **password field is still filled**.
❌ It was stored memory-only (`remember = false`), so it vanished on every restart — defeating the
whole feature.

### 7.6 Clear stored configuration

**Do** Settings > **Clear Stored Proxy Configuration**.

**Expect** Confirmation dialog → confirm → success message. UI stays responsive (no freeze).
Then set proxy to "No proxy" and invoke the action: the restore button is **gone**.

---

## 8. Multi-project behavior

### 8.1 Both projects updated at once ⚠️ key fix

**Do** Both projects open, Git + Gradle on. Toggle the proxy in `pta-gradle` and **immediately**
(within 1 s) check both.

**Expect** Both projects reflect the change right away.
❌ Only the active project was configured; others caught up ~2 s later via the poll.

### 8.2 No duplicated work per project

```bash
grep -c "Git proxy configured" .intellijPlatform/sandbox/ProxyThemAll/IC-*/log/idea.log
```

**Do** Note the count, toggle once, re-count.

**Expect** Increase of **one per open project** (2 here) — not 4, not 8.
❌ Per-project listener registration multiplied every state change by the project count.

### 8.3 No listener growth over time 🔒

**Do** Open and close a third project **five times**. Then:

```bash
grep "Total listeners" .intellijPlatform/sandbox/ProxyThemAll/IC-*/log/idea.log | tail -5
```

**Expect** The number does **not** climb with each open. No `AlreadyDisposedException` anywhere in
the log.
❌ Widget + Project were retained forever (`disposeWidget` no-op).

---

## 9. Notifications

### 9.1 Informational balloons obey the setting

**Do** Untick "Show notifications when proxy state changes". Toggle the proxy.

**Expect** No "Proxy Enabled"/"Proxy Disabled" balloon.

### 9.2 Errors are shown anyway ⚠️ key fix

**Do** With notifications still **off**, break git: Settings > Version Control > Git > set "Path to
Git executable" to `/nonexistent/git`. Toggle the proxy.

**Expect** The failure is still surfaced (error balloon and/or a clear log warning) — it is **not**
silently swallowed. Restore the git path afterward.
❌ The cosmetic toggle used to hide errors too.

### 9.3 No IDE fatal-error reports 🔒

```bash
grep -c "ERROR - " .intellijPlatform/sandbox/ProxyThemAll/IC-*/log/idea.log
```

**Expect** No ProxyThemAll entries at `ERROR` level, and no "IDE Internal Error" popup during any
test above. Expected environment problems must be `WARN`.

---

## 10. Robustness sweep (do last)

### 10.1 No UI freezes

**Do** Repeat: toggle proxy, open Settings, click "Clear Stored Proxy Configuration", toggle again —
quickly, ~10 times.

**Expect** No freeze longer than ~1 s. No "IDE is not responding" banner.
Log must not contain `Slow operations are prohibited` for `org.holululu`.

### 10.2 Clean shutdown

**Do** Close the IDE. Check the tail of the log.

**Expect** No ProxyThemAll exception during shutdown.

### 10.3 Whole-log final scan 🔒

```bash
L=$(ls .intellijPlatform/sandbox/ProxyThemAll/IC-*/log/idea.log)
grep -iE "p@ss|password@" "$L"                        # expect: nothing (credentials)
grep -i  "org.holululu.*ERROR"           "$L"         # expect: nothing
grep -i  "AlreadyDisposed\|Slow operations" "$L"      # expect: nothing for org.holululu
```

---

## Result sheet

| §  | Feature                                                                           | Pass |
|----|-----------------------------------------------------------------------------------|------|
| 1  | Settings: all options, defaults, persistence, credential warning                  | ☐   |
| 2  | State detection, dynamic menu text, toggle, single balloon                        | ☐   |
| 3  | Widget: icons, tooltips, click, hide without restart                              | ☐   |
| 4  | Git: proxy + noproxy, encoding, no leak, **global untouched**, cleanup            | ☐   |
| 5  | Gradle: properties, no duplicates, **content + jvmargs survive**, SOCKS, fallback | ☐   |
| 6  | Changelist: filed, active list kept, auto-removed, foreign changes safe           | ☐   |
| 7  | Backup/restore incl. **password persists across restart**                         | ☐   |
| 8  | Multi-project: immediate, no duplicated work, no leaks                            | ☐   |
| 9  | Notifications: setting respected, errors always shown                             | ☐   |
| 10 | No freezes, clean shutdown, clean log                                             | ☐   |

**Any ❌ item that reproduces is a regression — capture the file or log line before changing state.**

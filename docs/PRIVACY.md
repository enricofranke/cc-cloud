# Crash reports, usage stats and feedback

CC: Cloud can send three kinds of report to its developer. Two need the server owner's yes, one needs a person to confirm
every single message. This file lists everything that can leave a server. If a field is not listed here, the mod does not
send it and the report service refuses it. Players are often minors and the operator is a private person in Germany, so
the rules follow the GDPR and § 25 TDDDG: nothing leaves a server, and nothing is read for a report, before an explicit
yes.

| Channel | Needs | How often | Off switch |
|---|---|---|---|
| Crash reports | the server owner's yes (`crashReports`) | at most 5 per server run, 1 per bug per UTC day | any time |
| Usage stats | the server owner's yes (`usageStats`) | at most once per UTC day | any time |
| Feedback | the writer confirms that one message | at most 3 per person in 10 minutes, 10 per server in an hour | never sent on its own |

## Consent

The decision belongs to the server owner: an operator of level 4, the console, or the player whose game hosts a
singleplayer world.

```toml
[reports]
crashReports = "ask"   # ask, on, off
usageStats = "ask"     # ask, on, off
consentVersion = 0     # set by the mod
```

- **Default:** `ask`, which sends nothing, reads no crash file, starts no thread, writes no file and opens no connection.
- **Asking:** one prompt, in the dashboard (only for a browser of a level-4 operator or the singleplayer host) and on the
  mainframe screen. Each channel has its own Yes and No, equal in size and style, and nothing is preselected.
- **Once:** closing the prompt answers nothing and it does not come back. After a No nobody asks again, also not in a new
  world. `/cccloud reports` and the dashboard's settings stay available for a decision at any time.
- **Preview:** "Show exactly what would be sent" and `/cccloud reports show` show the real payloads: the stats of this
  server right now, and for crashes the last report or, without one, an example error through the real pipeline, marked as
  an example.
- **Withdrawal:** `/cccloud reports off [crash|stats]`, the dashboard's settings or `off` in the config file. It works at
  once: the engine asks for consent again when a report is queued, before it is built and right before it leaves.
- **A file is not a yes:** `on` in the config needs a decision stamped in `<server folder>/cccloud/reports/state.json`, next
  to the server and outside the world. So a copied world or a modpack's `defaultconfigs` cannot agree for an owner. A hand
  edit to `on` stays `ask` until the owner runs `/cccloud reports on`. `off` in the file always works.
- **A new version:** if what is sent changes, the mod raises its consent version. An earlier yes then counts as undecided
  and the owner is asked again. A No stays a No.
- **Pack makers:** `crashReports = "off"` and `usageStats = "off"` in `defaultconfigs` switch both off for a whole pack.

`state.json` holds the decisions, the version a prompt was put aside for, the day stats were last sent, the fingerprints
sent today and the time of the newest crash file looked at. It holds no report text and no id.

## What is sent

Every report is an HTTPS POST to `https://reports.cccloud.dev/v1/<channel>` with `Content-Type: application/json` and
`User-Agent: CC-Cloud`, through the mod's SSRF-safe client (checked public addresses, TLS with host name check, no
redirects, 5 second timeouts), on one background thread with a queue of 8. A failed report is dropped, never repeated and
never written to disk. It cannot affect the game.

### Environment block (`env`)

| Field | Example | Note |
|---|---|---|
| `mod` | `0.17.0` | CC: Cloud version |
| `neoforge` | `26.1.2.100` | NeoForge version |
| `mc` | `26.1.2` | Minecraft version |
| `java` | `25` | Java major version |
| `os` | `linux` | `windows`, `macos`, `linux` or `other`, nothing finer |

### Crash report (`/v1/crash`)

| Field | Example | Note |
|---|---|---|
| `v` | `1` | format version |
| `fingerprint` | `0fd3acc38171e4ea` | 16 hex digits of a SHA-256 over code names only: exception class and up to 12 of the mod's frames, no line numbers, no message. The same bug has the same value everywhere; it says nothing about a server. |
| `where` | `device_card` | `crash_file`, `device_card`, `web` or `websocket` |
| `env` | | the block above |
| `causes[].class` | `java.lang.IllegalStateException` | exception class, at most 4 causes |
| `causes[].message` | `Could not save <name> to <path>` | scrubbed, at most 300 characters; left out when empty. Never kept for Lua and ComputerCraft exceptions, which quote programs. |
| `causes[].frames[]` | `ai.ezfox.cccloud.web.WebHub.tick:120` | at most 48 per cause. Class, method and line for this mod and the mods it integrates with (`ReportStack.KNOWN_PACKAGES`); every other frame is only its class name. |

Only failures with this mod's code on the stack are reported. Sources:

- Failures the mod catches: a device card provider it switches off, an error in the web layer. Dropped connections are not
  failures.
- Crash files: at each start the mod looks in `crash-reports/` for files newer than the last look and never older than the
  yes. Only the stack trace at the top is read; reading stops at Minecraft's "detailed walkthrough", so system details, mod
  lists and player data are never touched. This is how a crash that took the whole game down is reported on the next start.
  At most 16 files, 256 KB each, no symbolic links. Native JVM logs (`hs_err_pid*.log`) are not read: they hold environment
  variables and paths.

### Usage stats (`/v1/stats`)

| Field | Example | Note |
|---|---|---|
| `v` | `1` | format version |
| `env` | | the block above |
| `mainframes` | `1` | standing mainframes, as a range: `0`, `1`, `2-5`, `6-20`, `21-100`, `101-500`, `500+` |
| `uplinks` | `6-20` | loaded uplinks, same ranges |
| `cards` | `101-500` | connected device cards and bridges, same ranges |
| `internetAccess` | `connect` | `off`, `http` or `connect` |
| `integrations[]` | `ae2` | mod ids of active integrations, only from the mod's own list in `Integrations`, never the mod list of the pack |

**There is no install id.** The service counts values per day and keeps no report as a row, and every server sends at most
one report per UTC day, so "servers on that day" is a count of reports. Nothing can link two days or two values of one
server. Days with a restart at midnight may count one server in two days, which is good enough for numbers of this kind.

### Feedback (`/v1/feedback`)

| Field | Example | Note |
|---|---|---|
| `v` | `1` | format version |
| `category` | `bug` | `bug`, `idea` or `other` |
| `text` | `The map of <player> flickers.` | the message, scrubbed, at most 1500 characters |
| `contact` | `steve#1` | only if the writer filled it in, for an answer; at most 120 characters. The one field that is not scrubbed. |
| `env` | | the block above, only if the writer ticked the box. Off by default. |

In the dashboard's sheet (**Settings → Send feedback**) the writer picks a category, writes, sees the exact message and
presses Send. With `/cccloud feedback <text>` the chat shows the exact message with [Send] and [Discard]; the category is
`other`, and there is no contact and no `env`. Both warn against personal data and ask anyone under 16 to ask a parent
first. A message that waits in chat is forgotten after 5 minutes.

## Never sent

Player names, UUIDs, IP addresses, the server's address, Connect name or keys, world and file names, home folders,
coordinates, chat, Lua code, items and inventories, tokens, PINs. The only exception is what somebody types into the
feedback `contact` field themselves.

## How it is kept out

1. **Allowlist:** `ReportPayload` builds each payload field by field. What is not named there does not exist.
2. **Fail closed:** if anything throws while a payload is built, even a stack overflow, there is no report.
3. **Scrubber:** `ReportScrubber` is the one place that cleans text. It removes URLs, e-mail addresses, bearer and prefixed
   tokens, JWTs, `password=…`-style values, PINs, pairing codes, UUIDs, Windows, macOS, Linux, UNC and relative paths,
   home folders, IPv4, IPv6 and MAC addresses, host names, coordinates in every form Minecraft prints them, namespaced ids
   (items, blocks), file names, long numbers and long random strings. It also knows the names of this server: online,
   whitelisted and operator players, mainframe owners and names, the world, the MOTD, the configured addresses, the Connect
   name and the system account. It errs on the side of removing too much.
4. **Last gate:** a finished payload must come out of the scrubber unchanged, and frames and classes must match a strict
   pattern. Otherwise it is dropped.
5. **Second net:** the service refuses every unknown field and replaces addresses, URLs, UUIDs and home paths that an old or
   modified client might still send.

## The service

`reports.cccloud.dev` is a Cloudflare Worker (source in the website repository, folder `reports/`) with a D1 database in
Cloudflare's EU jurisdiction.

- **Kept:** per crash fingerprint the first and last day, a count, the versions and the newest cleaned report; per day and
  value a count of stats; feedback with its day. Times are days, not moments.
- **Not kept:** the IP address, the user agent or anything else about a request. The IP is only the key of the rate limit,
  which counts in memory at the edge. Invocation logs are off.
- **Retention:** a daily job deletes everything older than 90 days. A crash that keeps happening stays until 90 days after
  it was last seen.
- **Notices:** new crash fingerprints and feedback can go to a private Discord channel, with mentions off. The contact
  field is never part of that message.
- **Access:** only the operator, with a bearer token, through `scripts/reports.mjs`.

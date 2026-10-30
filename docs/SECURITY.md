# CC: Cloud – Security model and threat model

This document covers the in-mod web server (dashboard, HTTP API, WebSocket). It also covers the rights model behind it (v0.6 roles) and the in-game screens.
It is reviewed before every release.

## Assets

| Asset | Why it matters |
|---|---|
| Control over machines | Dashboard actions can do the following: toggle redstone, switch MFFS fields, start crafting jobs, trigger backups, and unlink devices. |
| Information about a base | Coordinates and network topology show where a base is. Storage contents and claims show what is in it. |
| Device tokens | 256-bit bearer tokens. Each one acts with the role of the player who linked the browser. |
| Share-link secrets | 128 bits each. They give read-only access to selected cards. |
| Mainframe PIN | A second factor for `opts.pin` widgets. |
| Server availability | The web server runs inside the Minecraft server process. It must never stall the server thread. |
| API keys (v0.10) | 256-bit bearer keys for external tools. Each one acts for one player on one mainframe, within its scopes. |
| Scenes and schedules (v0.10) | One press or one clock tick runs several actions; a scene must never do more than its presser (or owner) may. |

## Attackers

1. **Other players on the server.** They may try to see or control someone else's mainframe, raise their own role, or abuse team membership.
2. **Internet scanners and random clients.** These matter as soon as the port is reachable, for example with `webAllowInternet=true` or behind a proxy.
3. **Share-link holders.** Anyone the link was forwarded to, including people who find it later in a chat log.
4. **Malicious mod clients.** Modified game clients sending crafted GUI payloads, such as screen actions or Link Card use.
5. **A stolen browser or token.** For example a lost phone.
6. **Cross-site attackers.** Web pages that try to use a victim's browser: CSRF, WebSocket hijacking, Referer leaks.
7. **Leaked API keys and third-party tools (v0.10).** A key copied into a bot, a Home Assistant config or a script repository can leak; the tool itself may be buggy or hostile.

## Mitigations

### Rights: one decision point
- `AccessResolver` (in `access/`) is the only code that answers "which role does player X have on mainframe Y". The sources are:
  - the owner;
  - direct grants;
  - team ranks through a `TeamProvider`;
  - the PermissionAPI nodes `cccloud.admin.*`.

  The highest role wins. Permissions are the union over the sources, but a **deny on any source** (the player's own grant or their team rank) removes the permission from the result, so a team role cannot bring back what the owner denied directly. Read-only devices are always capped at the viewer preset.
- Banned players get `none`. So do players without `cccloud.use`.
- The resolver runs:
  - on every WebSocket login;
  - on every action, query, layout save and unlink, on the server thread;
  - on every HTTP API call;
  - every 5 seconds for every open session.

  The 5-second re-check pushes `{"t":"role"}` or closes the session with 4403 `no_access`. Team changes from FTB Teams events trigger it at once.
- `AccessGate` maps a role and a message kind to allow or deny:
  - viewer: read and item search;
  - operator: actions;
  - admin: layout, unlink, access, shares, devices.

  The WebSocket layer checks it first, with the cached role. The game bridge checks it again on the server thread, with the live role.
- Escalation rules: **one function, `AccessRules`, used by every path that changes access** – the Access API, the mainframe screen (add player, remove, role) and the commands (`/cccloud role`, `trust`, `untrust`). A GameTest walks the screen and command paths.
  - Moderators (the owner, or the server node `cccloud.admin.manage`) may change everyone.
  - Everybody else needs `access.manage`, can only change players whose role is below their own, never themselves (except leaving) and never the owner.
  - Nobody grants a role above their own, and nobody grants a permission they do not hold: the check covers the role's whole preset plus extra allows, not only the allow list.
  - Team rank mappings follow the same rule; assigning a team counts its default mapping as granted, and replacing or removing a team needs every mapped role below the caller.
  - `trust` (screen: operator, command: admin) never grants more than the caller's own role.
  - `owner` and `share` can never be granted. A team can only be assigned by one of its members, or by a server moderator.
- Link Cards: a device that is linked to another mainframe can only be re-linked by someone with `network.link` on that old mainframe too (or when the old mainframe no longer exists). This covers uplinks, bridges, Ender Relays, Ender Fiber Links and Cloud Displays.
- In-game screens show the network map only with `view.positions` and each management part only with its own permission (`devices.manage`, `access.manage`, `pin.manage`); uplink and bridge screens hide where their mainframe is without `view.positions` on it.
- Server admins are visible: their dashboard views and screen opens through the `cccloud.admin.*` nodes are written to the mainframe's audit log. That log is shown in the Security tab of the in-game screen.
- In-game screens never trust the client:
  - every screen action re-checks the role on the server;
  - the menu must be open (matching container id and `stillValid`);
  - every argument is validated (`GuiActions`, unit tested).

### Tokens, linking and devices
- Pairing and account codes:
  - 8 characters from a 31-character alphabet;
  - single use;
  - valid for 10 minutes;
  - compared in constant time;
  - 5 failures per IP per minute, then HTTP 429.
- Every new browser needs **in-game approval**:
  - mainframe codes: by an admin of that mainframe;
  - account codes: only by the player the code belongs to.
- Only the SHA-256 of a token is stored. The token is handed out exactly once, on the pairing poll. A token unused for 30 days expires.
- v0.3 tokens keep working, limited to their mainframe. Read-only devices are capped at viewer.
- Revoking a device (in-game, `/cccloud devices revoke`, or `DELETE /api/devices/<id>`) closes its sockets with 4401 `revoked`. A mainframe-limited token can only revoke itself.

### Share links
- The secret lives in the URL fragment. Browsers never send the fragment in requests, logs or Referer.
  - The dashboard removes it with `history.replaceState`.
  - It sends the secret only in the first WebSocket message (`auth_share`).
- The secret is 128 bits of `SecureRandom`. Only its SHA-256 is stored, and it is compared in constant time. The API returns the secret exactly once.
- Login limits:
  - 5 wrong secrets per IP per minute;
  - 30 logins per link per minute;
  - `maxViewers` (1–50) concurrent viewers.
- Expiry is 1 h, 24 h, 7 days or never. Expired and revoked links close their open sessions within 5 seconds, and revocation through the API closes them immediately.
- Share sessions are **hard-denied** everything except `pong`, centrally in `WebSocketSession` before any handler runs.
  - Requests with a `reqId` get their result type with `"error":"forbidden"`. Everything else gets `{"t":"error","error":"forbidden"}`.
  - `ShareSessionTest` walks every client message type, checked against the sealed `ClientMessages.Message` interface, so a new type cannot be forgotten.
- What a share viewer sees is filtered server side (`ShareFilter`, `WebHub`):
  - only the shared cards and pages;
  - no `item_search`;
  - no network map, no positions;
  - no script or bridge details;
  - no restricted (server/claims/backups) sections;
  - with `hideNumbers`, no label, text or number widgets.

### Web hardening
- The server is LAN-only by default (`webAllowInternet=false`). IP bans are honoured. `X-Forwarded-For` and `CF-Connecting-IP` are only trusted from configured proxies.
- Deny by default:
  - Every HTTP route declares its access level in `Router` (public, device token, or API key) and is checked centrally (`RouteAuthTest` walks every route, `ApiHttpTest` the public API).
  - WebSocket messages other than `auth`, `auth_share` and `pong` are rejected before authentication.
  - Authentication must happen within 5 seconds.
- Size limits:
  - 2 KB WebSocket frames before authentication;
  - 8 KB per message after authentication, 256 KB only for `layout_set` (64 KB until 0.10; raised for big bases, see
    docs/SPEC.md "Scaling": a page of 500 cards; saves stay limited to 5 per 10 s per mainframe and need `layout.edit`);
  - 4 KB HTTP bodies.
  - Layout placement in the dashboard is bounded for any saved document: overlapping boxes beyond 64 per grid are
    resolved in one pass and cards without a box beyond a work budget are packed below (2048 cards saved on one spot
    took minutes in every viewer's browser before this was fixed).
- Parsing:
  - Strict JSON schemas; unknown fields are rejected in the v0.6 APIs.
  - Bounded numbers and lengths.
  - Ids are checked against patterns before they are used.
- Rate limits:
  - 10 actions per second per socket;
  - 5 item searches per second;
  - 5 layout saves per 10 seconds per mainframe;
  - 5 production requests per second per socket; scene saves like actions;
  - 120 API requests per minute per IP (a voxel batch of up to 64 sections is one request);
  - voxel sections: a budget of 2048 per device, refilled at 256 per second, counted per section for the batch and
    the single-section route (until 0.9.0: 40 requests per second, each also one of the 120 API requests, which made
    loading a base take minutes and starved the map tiles afterwards);
  - public API (v0.10): per key and per IP from the config, item searches 5 per second per key;
  - icons: 200 per minute per IP;
  - map tiles: 60 per minute per device, at most 256 tiles waiting for the server thread (more get 503);
  - live recipes (`GET /api/recipes`, public like the docs: game data only): 60 per minute per IP, built once per recipe reload off the server thread.
- Connections are limited per IP and in total. Slow consumers are disconnected and never buffered without limit: a
  browser may fall behind by at most 4 MB beyond the 1 MB write buffer, or for at most 10 seconds (a big base's snapshot
  is over 1 MB), then it is closed with 1013 `too slow`. In-game tablet sessions queue at most 4096 messages, then they
  get a fresh snapshot.
  - 24 open connections per IP (behind a trusted proxy: per forwarded client IP) and 256 in total. Until 0.9.0 the
    limits were 8 and 64, but a single browser tab already opens 7–8 connections (6 pooled HTTP/1.1 connections per
    host and credentials mode, the WebSocket, preconnects), and the socket over the limit was closed without a
    response, which Chrome showed as an empty page. The ratio of total to per-IP limit is unchanged.
  - Up to 8 connections over a limit are answered on their first request with `503 too_many_connections`,
    `Retry-After: 1` and `Connection: close`, and get a 2-second read timeout instead of 10 s; anything further over a
    limit is closed at once, as before. So a flood still holds at most 8 extra sockets per IP for at most 2 seconds.
- WebSocket compression (permessage-deflate, `webCompression`, on by default) only when the browser offers it. Incoming
  compressed frames are size-checked before they are inflated (the raw-frame guard sees the compressed size) and inflate to
  at most the 257 KB frame limit, so a small frame cannot expand into a large message. Before authentication the inflated
  message is limited to 2 KB as well (the raw-frame guard only sees the compressed size). Memory: about 300 KB of deflate
  state per open browser.
- No CSRF surface:
  - authentication uses bearer tokens in headers or the first WebSocket message, never cookies;
  - the WebSocket `Origin` must match `Host`.
- Response headers:
  - a strict CSP (`default-src 'self'`, no inline script);
  - `X-Frame-Options: DENY`;
  - `Referrer-Policy: no-referrer`;
  - `X-Robots-Tag: noindex, nofollow`;
  - `nosniff`;
  - COOP/CORP;
  - HSTS with TLS.
- The server thread is protected:
  - All network I/O runs on Netty threads.
  - Server-thread work is bounded: a 5-second timeout and no blocking lookups.
  - Player names for the web Access API come only from online players and names the mod already knows. There are no Mojang profile lookups from web input.
  - PIN hashing (PBKDF2, 120k iterations) runs on its own bounded executor.

### Removed mainframes: retention and ownership
- Breaking a mainframe deletes nothing at once: devices, accounts, PIN, layout, rules, scenes, API keys and push targets are kept for `mainframeRetentionDays` (default 7) and then deleted; `/cccloud mainframe purge <id>` (owner or `cccloud.admin.manage`) deletes them earlier. While a mainframe is removed, its devices stay paired but cannot act (it is offline), and relinking its devices elsewhere still needs `network.link` on it.
- Ownership is stored on the server. The dropped item only names the mainframe; whoever places it restores the original owner's mainframe and gets no rights from that. A second item of a mainframe that stands is refused.
- `/cccloud mainframe recover`: owners get one item per removal, and only when the break dropped none; otherwise only server admins (`cccloud.admin.manage`) can, so the command cannot duplicate mainframes.
- Misclick and grief guard (`mainframeBreakProtection`, default on): in survival only the owner, the mainframe's admins and server admins can break it; in creative it only breaks while sneaking.

### No code execution from the web
- The browser only sends data that is validated against schemas. There is no eval, no scripting endpoint and no command passthrough.
- The v0.5 terminal and editor (admin only) will reach only the CC computer's own Lua sandbox:
  - CC's HTTP rules keep blocking private IPs;
  - file paths are canonicalised inside the computer root;
  - `..`, absolute paths, symlinks, `rom/` and disk mounts are rejected;
  - size and quota limits apply.

### Scenes, schedules and production statistics (v0.10)
- **No escalation through scenes.** A scene press needs `act.buttons`; every step is checked again **when it runs**, with the live rights of the player behind the pressing device (read-only devices capped as always): widget steps need the widget's own action permission, redstone `act.redstone`, crafting `storage.craft`, the dashboard switch `power.toggle`. A refused step fails with `not_allowed` and the others still run. Saving a scene needs `rules.edit` and every permission its steps need, so nobody can store a scene that does more than they could do by hand.
- **Scheduled scenes** (a rule's `scene` action) run with the live rights of the scene's owner (the player who saved it last), re-checked for every step; a banned owner or one without a role makes the run fail. Rules never enter a PIN: a scene that needs the PIN cannot be put into a rule (`pin_widget`) and is refused at run time.
- **PIN:** a scene asks for the mainframe PIN when it is marked so or when any step targets a PIN-protected widget; steps on PIN widgets re-check that the press carried a verified PIN.
- **Loops and load:** a scene cannot run a scene or press scene buttons; at most 4 runs at once and 30 runs per minute per mainframe; the same scene never runs twice at once; delays are server ticks (no threads, no blocking), at most 10 minutes per scene. Schedules are limited per mainframe (`maxSchedulesPerMainframe`) with a minimum interval; after a restart at most one missed run per schedule is caught up (no catch-up storm); schedules of unloaded mainframes are skipped and logged once per outage.
- **Production statistics** are only sent for storages the session may see (restricted sections need `view.positions`) and with `storage.search`; item details need `storage.search`. Pinning a chart needs `layout.edit` (+ `storage.search`); a pinned rate is visible to everyone who sees the mainframe's cards, like watchlist rows. Sampling reads the bridge's existing item index snapshot at most once per storage per interval (one storage per tick) with bounded memory.

### Public API (v0.10)
- **Off by default** (`[api] apiEnabled=false`): the `/api/v1` routes do not exist (404); key management answers `disabled`.
- **Keys:** `cck_` + 32 random bytes; only the SHA-256 is stored, compared in constant time; the key is shown exactly once and never logged. A key belongs to one player and one mainframe, has scopes, an optional expiry and an optional IP/CIDR allow-list (checked against the client IP after the trusted-proxy rules). At most `apiKeysPerPlayer` per player. Revocable by its player and by players with `access.manage` on that mainframe; deleted with the mainframe. Creating keys needs `api.keys` (admin and owner presets) and the base permission of every scope; the server node `cccloud.api.keys` (default true) switches keys off for a player, existing ones included. A key ends with the browser that created it: revoking that browser (in game, `/cccloud devices revoke`, the Account page) revokes its keys too and writes an audit line, so a stolen phone cannot keep control through a key it minted before it was revoked (a browser's 30-day expiry is not a revocation and keeps the keys). Moderators see other players' keys without their last IP and allow-list.
- **Scope ∩ live rights:** every request re-resolves the key owner's rights (bans, `cccloud.use`, roles, team ranks) like a device token and allows only what both the scope and the rights allow. Scopes never include management, access, devices, pairing, key management, terminal, files, scripts, layout, rules, push, map or voxels.
- **No PIN through the API:** PIN-protected widgets and scenes that need the PIN are refused (`pin_required`), so a leaked key never reaches the second factor. Structured operations (crafting, watchlists, redstone sides) are not available.
- **Transport:** `Authorization: Bearer` only; a key in the URL is refused (`key_in_query`) before it is looked up, so keys do not end up in logs or browser history. Device tokens are not accepted on `/api/v1` and API keys nowhere else. Per-key and per-IP rate limits with `Retry-After`, bodies ≤ `apiMaxBody`, strict JSON. The same LAN-only rule, IP bans, Host allow-list, connection limits and security headers as the dashboard.
- **CORS** is off unless an origin is listed exactly in `apiCorsOrigins`; then only that origin gets `Access-Control-Allow-Origin` (and `Cross-Origin-Resource-Policy: cross-origin`). Since keys are never sent automatically by browsers (no cookies), CORS cannot be used for CSRF.
- **Event stream (SSE):** read-only, same filtering as a dashboard with the owner's rights (restricted sections only with `view.positions`), rights re-checked every 5 s (revoked, expired, banned or a lost role end the stream), slow readers disconnected, at most `apiStreamsPerKey` streams per key and 32 in total; streams do not take a browser slot.
- **Audit:** every write (widget value, scene run), key creation and revocation is written to the mainframe's audit log and the server log with the key's name and player.
- Residual: anyone holding a key can do what its scopes and its owner's rights allow until it is revoked or expires. Prefer short expiries, narrow scopes and an IP allow-list; serve the API over HTTPS when it leaves the LAN.

### Cloud Probe and Cloud Redstone devices (v0.10)
- **Cloud Probe** (reads and controls one machine up to `probeRange` blocks away, default 8, at most 16 by the config's range): picking the machine is a real block interaction (claim mods cancel it, spawn protection, adventure mode); a pick is valid for 10 minutes and only for the picking player. Linking needs `network.link` on the probe's mainframe and checks spawn protection and FTB Chunks claims of other teams again. Every read and action checks the target again: the current `probeRange`, loaded, not a CC: Cloud block, not in a chunk claimed by another team for the player who set it, and the probe still standing where (and in the dimension where) it was set; a probe moved with its data forgets the target. Nothing is force-loaded.
- **Cloud Redstone devices**: dashboard controls need `act.redstone` (every control of a Redstone card, "All on/off" included) and are limited to 4 changes per second per device (dashboard, rules, scenes and API alike); PIN and confirmation per device, "All on/off" asks for the PIN when any device of the card has one. Cards and groups belong to one mainframe (groups never merge across mainframes); linking needs `network.link` and the relink rule; rename and regroup from the dashboard check that the device belongs to that mainframe. The device screen needs the menu open and the player near the device; its settings need `network.link`, switching it by hand is like a lever.
- Residual: claim mods other than FTB Chunks are only asked when the machine is picked; a sensor reads the redstone signal at its own position, which its placer could read anyway.

### Map, voxels and X-ray
- The surface map shows what is visible from above. Cave **slices** are configurable (`mapSlices`: auto = on in singleplayer and for LAN-only dashboards, off when `webAllowInternet` is set) and limited to ±32 blocks (Y) around the mainframe's own nodes.
- Slices and **voxels** never include chunks claimed by another team (FTB Chunks; members and allies count as the same team). A slice tile with such a chunk is never served from the memory or disk cache: it is drawn again with that chunk empty, so a claim also hides what was cached before it.
- Ores (`#c:ores`) show as stone in slices and voxels unless they touch air within 16 blocks of the requesting mainframe's own nodes. Voxels only contain blocks with a face to air or a see-through block.
- Residual: within `voxelRadius` of the network, caves and structures are visible to everyone with `view.positions`. Turn `voxelView` off on servers where that is too much.
- The voxel batch route (2026-10-30) applies every rule above per section (radius, claims, ore rule, `view.positions`) and only changes how many sections travel per request. Its body is strict JSON (≤ 64 distinct sections, optional 16-hex-digit hashes, nothing else); the hashes only let the server answer "same" and never select other data.
- The dashboard keeps voxel sections in the browser's IndexedDB (`cccloud-voxels`) so the 3D view opens at once. That is data this browser was already allowed to load; it stays in the dashboard's origin, is deleted when the mainframe is removed from the browser or the server answers 401/403 for voxels, and is checked against the server (hashes) on every visit, so sections that became out of range or claimed are dropped. A browser whose access was revoked still has what it had loaded before, like a screenshot.

### Rate limits and resources (in-game)
- Map waypoint subscriptions rebuild only on a change and at most once a second per player; opening the tablet at most once a second per player.
- Screen actions (mainframe, uplink and redstone device screens): 20 at once, then 10 per second per player; a rejected action without an open screen is logged at debug level only.
- Block entity data of CC: Cloud blocks (creative pick-block with data, items with `block_entity_data`) is only applied for operators (`onlyOpCanSetNbt`), so a creative player cannot place a probe, uplink, redstone device or mainframe with a made-up target, link or id.
- Player-name lookups from the mainframe screen: one per player and second, four at a time.
- Item icons and block textures rendered by clients: a quota per player and hour; something already stored for the current version is only replaced by a player who owns or has a role on a mainframe (first writer wins otherwise).

### Dependencies
- The mod uses only Minecraft, NeoForge, Netty and Gson.
- Integrations are soft dependencies (`compileOnly`), loaded only when the mod is present. They are reached through neutral interfaces (`TeamProvider`, `TeamSource`, `StorageAdapter`).
- The web bundle keeps a short, audited dependency list (`pnpm audit` in CI).

## Security review 2026-10-23 (main@aa4557f)
An independent review found 1 high, 6 medium and about 18 low issues. Fixed with tests: H1 (privilege escalation through the screen and command paths), M1 (team mappings and denies), M2 (`CF-Connecting-IP` from any trusted proxy; now only with `webTrustCloudflareHeader`, otherwise the right-most untrusted `X-Forwarded-For` entry), M3 (Link Card takeover), M4 (slices/voxels as X-ray), M5 (node coordinates in the screen), M6 (packet rate limits); lows: read-only device cap, share layout filtering, shares end with their creator's `shares.create`, PIN lockout per mainframe across devices with escalating locks (5 min, doubling, up to a day), Host header allow-list against DNS rebinding (`webAllowedHosts`), body limits before aggregation (4 KB, 256 KB only for authenticated script uploads), connection limit per forwarded client IP, ntfy token dropped when the host changes, render upload quotas, per-section screen rights, mainframe position hidden without `view.positions`, bounded profile lookups, the dev `test-token` command from the server console only, team-change rank checks, and an NPE when approving an account request from the screen. GitHub Actions are pinned to commit SHAs and the Gradle wrapper is validated in CI.

Known items:
- **Store index signing (L9): done.** The store index is signed with Ed25519 (`index.json.sig`); the server verifies the signature with `scriptStoreKey` and the file's SHA-256 against the signed index before writing a store install (the dashboard's own check is a convenience, the server is the authority). The private key lives only in the website build's CI secret (the index is signed when the site is built). Residual: a leaked signing key – rotate it as described in the CONTRIBUTING.md of [cc-cloud-scripts](https://github.com/enricofranke/cc-cloud-scripts). The website serves `/store/*` with `Access-Control-Allow-Origin: *` (no credentials), because dashboards on any server origin fetch the index from the browser; the files are public and their integrity rests on the signature, not on the origin.
- **Supply chain (L16):** Actions are pinned; dependency updates are still manual reviews (no automated provenance checks).

## Security review 2026-10-30 (main@4a8f2de, branch security-v010)
Scope: what was added since 0.9.0 (public API, scenes and schedules, production statistics, Cloud Probe, Cloud Redstone devices, live recipes, voxel batch, map tile sampling, WebSocket compression, layout limits, big-screen views, the Discord bot). Fixed with tests:
- **Medium – API keys outlived a revoked browser:** a stolen browser could create a key and keep control after the owner revoked it. Keys now end with the browser that created them (`ApiKeysTest`).
- **Medium – Cloud Probe rules only checked when linking:** a lowered `probeRange` was not applied to probes already linked (the card was rebuilt for the old target, the Computer and Storage cards ignored the range); a probe moved with its data read the same coordinates at its new place (in another dimension, too); claims were only checked when picking, so a pick made as a team member (or a card made in creative) kept reading and controlling a machine in a foreign claim. Now one check on every read, origin and dimension stored, claims checked when linking and reading, picks expire after 10 minutes (`ProbeReadCheckTest`, GameTest `probe_guards`).
- **Medium – a saved layout could freeze every dashboard:** placing overlapping boxes item by item was cubic in the browser (2048 cards on one spot: minutes, the editor included). Now bounded (`layout-limits.test.ts`).
- **Medium – Discord bot:** a crafted log made the log check run for seconds per log (quadratic patterns); version texts from a log went into the public reply unchanged (masked links, phishing). Patterns bounded, versions only as version text in inline code (`mclogs.test.ts`).
- **Low:** block entity data of CC: Cloud blocks from creative non-operators (made-up probe targets, links, device ids); map slices kept pixels of chunks claimed later (memory and disk cache) and the tile queue had no limit (GameTest `map_tiles`, `MapTileQueueTest`); the 2 KB pre-auth limit only applied to the compressed WebSocket frame (`PreAuthSizeTest`); screen action packets had no rate limit and logged every rejection (`PacketLimiterTest`); the voxel cache stayed in the browser after the role lost `view.positions`, and a refusal cleared only the current dimension; moderators saw other players' key IPs; bot log downloads followed redirects and had no global limit, and the bot namespace did not enforce the restricted Pod Security Standard.

Checked and found sound: scope ∩ live rights on every API route and in the event stream (per-session filtering of restricted sections, no other mainframes), CORS only for exact origins, key hashing and constant-time compare, the API switched off (404, streams end within 5 s); scenes (rights of the presser or the scene owner per step at run time, PIN re-checked in every action path, no scene in a scene, run limits); production statistics (same mainframe, `storage.search`, `view.positions` for restricted sections, memory bounded per storage); live recipes (built once per reload off the server thread, one shared build, only our items); voxel batch (every rule per section, strict bounded body, bounded output); map tile copies (deep copies on the server thread, the background never touches live chunks); permessage-deflate inflate bounds; big-screen view decoding (vanilla string bounds, no reassembly).

Open (low, recommendations): `hideNumbers` share links still send the values of charts and gauges (the dashboard hides gauge digits; consider dropping chart values too); production statistics map a storage to its mainframe from a cache refreshed every second; every per-IP limiter map is cleared once it holds 10 000 IPs (an attacker with that many addresses resets them); in a scene, the PIN flag of a step on a redstone device that moved to another card is decided by its old card (the step then fails with `pin_required` instead of asking first; no bypass, the action re-checks).

Follow-ups done after the review (2026-10-30, branch `followups`):
- **Slice tiles on disk:** a purged mainframe's slice folders are deleted in every dimension (memory cache too, and tiles
  still being drawn are not written); all tiles are capped by age (`mapTileMaxAgeDays`, default 30 days without being drawn
  or read; 0 keeps them). The sweep never follows symbolic links (`MapTileDiskTest`, GameTest `mainframe_retention_purge`).
- **Production statistics, large stock changes:** new items are picked with a heap of at most `trackedItemsPerStorage`
  entries in one pass over the changes; a change of 50 000 types no longer allocates an entry with two rings for each of
  them before trimming (`ProductionTrackerTest`).
- **CC wired networks (new):** an uplink on a computer shows cards for machines the computer reaches through its wired
  modems. They expose nothing the computer could not already read and control from Lua over the same cable; the cards
  are published like device cards, so every action needs the same permission as on an uplink attached to the machine
  (`act.buttons`, `act.redstone` for redstone controls, PIN/confirm as the card says), and the uplink must be linked
  with `network.link`. The listener node puts no peripheral on the network. Residual: like CC itself, claims are only
  enforced when the modem and cable are placed and switched on (in-world interactions), not checked again on every read
  as for the Cloud Probe.
- **Discord bot pod** (the bot lives in the website repository): a NetworkPolicy (`discord-bot/deploy/networkpolicy.yaml`) denies all ingress (the kubelet's probe
  comes from the node, which policies never block) and allows egress only to the cluster DNS and TCP 443 on public IPv4
  addresses (private, link-local/metadata, loopback and CGNAT ranges excluded). Pinning the image by digest is documented
  in `discord-bot/README.md` and the image workflow prints the digest; the Deployment still names the tag until the first
  image is checked and pinned. Checked with `test/deploy.test.ts` and a strict schema validation (no cluster).

## Residual risks and operator advice
- Without TLS, tokens travel in plain text on the LAN. For internet access, use HTTPS: the built-in TLS, a reverse proxy, or Tailscale (docs/HOSTING.md).
- Server admins with `cccloud.admin.*` can see every mainframe by design (moderation). This is logged, but not prevented.
- Anyone a share link is forwarded to can view it until it expires or is revoked. Prefer short expiries and low `maxViewers`.
- A player who gets someone else's in-game account can approve new browsers for it. That is outside the scope of this mod.
- The mainframe-wide PIN lockout can be triggered by anyone with a device and the right to use a PIN-protected widget: 20 wrong PINs lock the PIN for everyone for a while (owners can still set a new PIN in game). This is the trade-off against guessing with many devices.
- KubeJS: `ctx.user` in a section's action handler is the **device name** (chosen by the browser's user at pairing; `rule:<name>` for rules), not a verified identity – do not use it for decisions. The action already passed the permission check before it reaches the script; the `CCCloudEvents.action` event additionally carries the device account's player UUID and name. Reading files through the script import API and the editor is not PIN-gated (only writes, deletes, moves and runs are).

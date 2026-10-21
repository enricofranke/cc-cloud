# CC: Cloud – Spec (v0.1)

NeoForge 26.1.2 addon for CC: Tweaked 1.120.0. Mod id `cccloud`, Java package `ai.ezfox.cccloud`.
Goal: control CC: Tweaked computers and machines in-game from a mobile-first web dashboard.

CC: Tweaked 26.1 source for reference (read-only):
`~/ref/cct-26.1`

## Gameplay

### Cloud Mainframe – `cccloud:cloud_mainframe`
- 2 blocks tall (lower + upper, like a door). Horizontal facing.
- One per base/team. The mainframe is the gate between the in-game uplinks and the dashboard that the mod hosts on the Minecraft server.
- Dashboard ON/OFF: sneak + right-click with empty hand toggles it. Right-click shows status + pairing code in chat (a proper GUI comes later).
- Redstone signal on either half = kill switch: the mainframe goes offline.
- Status shown in the blockstate: `offline` | `connecting` | `online`.
- Also a CC peripheral `cloud_mainframe`: `isEnabled()`, `isOnline()`, `setEnabled(bool)`, `getUplinks()`.

### Cloud Uplink – `cccloud:cloud_uplink` and `cccloud:advanced_cloud_uplink`
- Small module that sticks to a block face (6 facings, like a CC modem). `facing` = direction from the uplink towards the block it is attached to.
- Range to the linked mainframe: normal = 128 blocks in the same dimension; advanced = unlimited and across dimensions (config values).
- It is a CC peripheral `cloud_uplink`. A computer next to it (or connected over a wired modem network) uses it to build its dashboard section.
- Status in the blockstate: `offline` | `connecting` | `online`. Boolean `direct` (true when attached to a non-computer block; phase 2 shows that block's data read-only, v0.1 only sets the flag).

### Items
- `cccloud:cloud_core`: crafting intermediate.
- `cccloud:cloud_link_card`: right-click a mainframe to store it (shows "Linked to Mainframe @ x y z"), then right-click an uplink to bind it. Sneak + right-click in the air clears the card.

### Recipes (shaped, CC style)
```
cloud_core (x2)          cloud_mainframe          cloud_uplink (x2)
 n P n                    G K G                    S K S
 P E P                    G A G                    S M S
 n P n                    G D G
n=#c:nuggets/gold P=minecraft:prismarine_crystals E=#c:ender_pearls
G=#c:ingots/gold K=cccloud:cloud_core A=computercraft:computer_advanced D=computercraft:disk_drive
S=minecraft:stone M=computercraft:wired_modem

advanced_cloud_uplink    cloud_link_card (shapeless)
 G G G                    paper, #c:nuggets/gold, #c:dusts/redstone, prismarine_crystals
 G U G
 G E G   U=cccloud:cloud_uplink E=#c:ender_pearls
```

## Lua API (`cloud_uplink` peripheral)
```lua
local cloud = peripheral.find("cloud_uplink")
cloud.setTitle("Fission Reactor")                       -- section title on the dashboard
cloud.button(id, label, {color="red", confirm=true})
cloud.toggle(id, label, {value=false})
cloud.slider(id, label, {min=0, max=100, step=1, value=0, unit="%"})
cloud.gauge(id, label, {min=0, max=1200, unit="K", warn=900, crit=1100})
cloud.label(id, text)
cloud.set(id, value)          -- update gauge/label/toggle/slider value
cloud.remove(id); cloud.clear()
cloud.alert(text, level)      -- level: "info"|"warn"|"crit" -> push/toast on the dashboard
cloud.isOnline()              -- true if the mainframe is online and the dashboard is enabled
-- events: os.pullEvent("cloud_action") -> "cloud_action", id, value, user, side
```
- ids: `[a-z0-9_]{1,32}`, at most 32 widgets per uplink. Colors: `cyan|green|yellow|red|gray`.
- The widget list lives in the uplink BlockEntity (saved), so the dashboard survives reloads.
- Actions from the web are queued as a `cloud_action` event on every computer attached to that uplink.

## Architecture change (2026-10-21): the web server runs INSIDE the mod
There is no external relay. The mod itself hosts the dashboard on the Minecraft server (dedicated server, or the integrated server in singleplayer/LAN): whoever runs the MC server automatically has the dashboard.
- One embedded HTTP + WebSocket server per Minecraft server (not per mainframe), started on ServerStarted, stopped on ServerStopping. Implementation: Netty (already on the MC classpath, CC: Tweaked uses netty-codec-http too) – verify availability; fall back to the JDK HttpServer + a minimal WebSocket handshake only if Netty's http codec is not available.
- Config (server config): `webEnabled` (true), `webBind` ("0.0.0.0"), `webPort` (8787), `publicUrl` ("" → shown in chat as http://<server-ip>:<port>), `allowCloud`, `uplinkRange`, `maxWidgetsPerUplink`.
- The built web app (static files) is bundled in the jar under `assets/cccloud/web/` … or better `/cccloud_web/` at the jar root (not a resource pack path) and served from the classpath. Unknown paths fall back to index.html (SPA).
- Persistence: pairing codes and client tokens (hashed, SHA-256) live in world SavedData (`cccloud_web`), keyed by mainframeId. No SQLite.
- Mainframe: `enabled` = dashboard on/off for that mainframe. Each mainframe has its own pairing code (8 chars, unambiguous alphabet, shown as XXXX-XXXX, rotates after use). Right-click shows the dashboard URL + pairing code. Kill switch/redstone as before.
- Thread safety: Netty threads never touch the world. Commands go to the server thread via server.execute(); state for the browser is published from the server thread into a thread-safe snapshot/broadcast hub.
- Singleplayer: the server starts with the integrated server, so a phone on the same Wi-Fi can open http://<pc-ip>:8787. Remote access on a hosted server needs the port to be open (document it); an optional external relay can come later.

## Browser protocol (same origin as the dashboard)
- `POST /api/pair {"code":"XXXX-XXXX","name":"Enricos iPhone"}` → `{"token":"...","mainframeId":"...","name":"..."}`. Wrong code → 403; max 5 wrong attempts per IP per minute.
- `GET /api/mainframes` with `Authorization: Bearer <token>` (a browser may hold several tokens, one per mainframe) → basic info.
- `GET /ws/client?token=...` (WebSocket). Server → client:
  - `{"t":"snapshot","mainframe":{"id","name","online","enabled"},"uplinks":[{"uplinkId","title","online","widgets":[{"id","type","label","opts","value"}]}]}`
  - deltas: `{"t":"value","uplinkId","id","value"}`, `{"t":"uplink",...full uplink...}`, `{"t":"uplink_removed","uplinkId"}`, `{"t":"state","online","enabled"}`, `{"t":"alert","uplinkId","text","level","ts"}`, `{"t":"action_result","reqId","ok","error?"}`
  - `{"t":"ping"}` every 20 s.
- Client → server: `{"t":"action","reqId":"...","uplinkId","id","value"}`, `{"t":"pong"}`. The server rejects actions while the mainframe is offline or disabled, and limits each client to 10 actions per second. `online` here = the mainframe exists, is loaded and is not redstone-killed.
- Tokens can be revoked: sneak + right-click the mainframe with a Link Card → revoke all tokens (later a GUI).

## Repo layout
- `src/main/java/ai/ezfox/cccloud/**` – mod code (Java 25)
- `src/main/resources/assets/cccloud/**` – blockstates, models, item model definitions (`items/`), textures, lang
- `src/main/resources/data/cccloud/**` – recipes, loot tables, tags
- `tools/` – texture/model generator scripts (Python)
- `preview/` – art preview (viewer.html, contact sheets)
- `cloud/` – web dashboard source (Vite) + a dev-only mock server for testing; `pnpm build` writes into `src/main/resources/cccloud_web/`

## Security & stability requirements (must-have for v0.1)
The dashboard can be reachable from the internet and can trigger things in-game (e.g. a reactor), so treat it like a real web service. It must never crash or lag the Minecraft server.

### Ownership & permissions
- The player who places a mainframe is its owner (UUID saved). Only the owner, players the owner trusts, and server ops (permission level 2+) can see the pairing code, toggle the dashboard or revoke tokens. Everyone else gets "You do not own this mainframe". Trust list for v0.1: `/cccloud trust <player>` / `untrust` while looking at the mainframe.
- Link Card binding: only the owner or a trusted player can bind an uplink to a mainframe.
- Pairing code: valid for 10 minutes, single use. Each pairing creates a named device token.
- Tokens: 32 random bytes (SecureRandom), stored only as SHA-256, compared in constant time. Commands: `/cccloud devices` (list), `/cccloud revoke <name|all>`, `/cccloud status`.
- Optional read-only devices (`/cccloud pair readonly`) can come later; the token model must already carry a `canControl` flag.

### Web hardening
- WebSocket auth: the token is sent in the first message (`{"t":"auth","token":"..."}` within 5 s), not in the URL, so it does not end up in logs. Check the `Origin` header (must match the Host) to block cross-site WebSocket hijacking.
- No CORS. Headers on every response: strict Content-Security-Policy (self only, no inline scripts), X-Content-Type-Options nosniff, X-Frame-Options DENY, Referrer-Policy no-referrer, Cache-Control no-store for /api.
- Limits: HTTP body ≤ 4 KB, WebSocket frame ≤ 8 KB, ≤ 8 connections per IP, ≤ 64 total, idle/read timeouts (handshake 10 s, idle 60 s without pong), 5 wrong pair attempts per IP per minute (then 429), 10 actions/s per client.
- Plain HTTP by default. Optional TLS via config (`tlsCertPath`, `tlsKeyPath`, PEM). Document that internet-facing servers should use TLS or a reverse proxy/tunnel; show a warning in the UI when the page is not served over HTTPS and the host is not a LAN address.
- The UI renders all Lua-supplied text as text, never as HTML (no innerHTML). Server-side length limits: title 48, label 48, string values 256, alert text 200, opts object ≤ 1 KB.
- Audit log: every action and pairing goes to the server log (`[cccloud] <device> pressed <uplink>/<id> = <value>`).
- Server admins: `webEnabled=false` turns everything off; `allowCloud=false` disables the blocks' cloud function.

### Stability
- The web server runs on its own small Netty event loop (2 threads). Netty threads never touch the world; everything in-game goes through `server.execute()`. The server thread only enqueues outgoing updates into a non-blocking hub and never blocks on the network.
- Backpressure: values are coalesced per widget and broadcast at most 10 times per second per uplink. Each client has a bounded outbound queue; slow clients get disconnected instead of piling up memory.
- Lua side: `set()` is cheap (it only marks the value dirty). At most 64 pending `cloud_action` events per computer, extra ones are dropped with a log line. Rate-limit `alert()` to 1 per second per uplink.
- Chunk unloads: unloaded uplinks/mainframes show as offline. Never force-load chunks.
- If the port is already in use or the server fails to start: log a clear error and keep the game running without the web server. All handler exceptions are caught and logged, never propagated into the tick.
- Clean start/stop on every server start/stop (also when switching singleplayer worlds), with no thread or port leaks.
- Tests: unit tests for token hashing, pairing expiry, rate limits, message validation and size limits; GameTests for placement and ownership; a small load test (e.g. 50 clients, 4 uplinks × 32 widgets updating every tick) that reports the average tick time impact (target < 0.5 ms/tick).

### Additions (2026-10-21, learned from Dynmap, BlueMap, Plan, LuckPerms, Cloud Catcher)
- **Server-side action validation:** every action is checked against the widget definition (exists; button = true only; toggle = boolean; slider = number in min..max snapped to step; gauge/label = no actions).
- **LAN-only by default:** `webAllowInternet=false` → only loopback and private ranges (10/8, 172.16/12, 192.168/16, 100.64/10, fc00::/7, fe80::/10) may connect; others get 403 with a message explaining the config switch. `webBind` can be set to 127.0.0.1 for reverse-proxy setups.
- **In-game device approval (like LuckPerms' trust prompt):** after a correct pairing code, the device is `pending`. The player who issued the code gets a clickable chat message "[device] wants to connect from [ip] – [Allow] [Deny]" (valid 2 minutes). Only then is the token activated. The browser shows "Waiting for approval in-game…" (the WS/HTTP flow must support this pending state; `POST /api/pair` returns `{"status":"pending","pairingId"}` and the client polls `GET /api/pair/<pairingId>` or waits on a short-lived WS until it gets the token).
- **Devices belong to a player:** each token stores the approving player's UUID. Effective rights = that player's current rights on the mainframe (owner, trusted or op). If the player is banned, untrusted or loses op, their devices stop working immediately (checked on every action and on connect).
- **PIN for critical widgets:** `opts.pin = true` on button/toggle/slider. The owner sets a 4–8 digit PIN per mainframe with `/cccloud pin set` (stored as PBKDF2 or another slow hash with salt). Actions on PIN widgets must include `pin`; 5 wrong PINs per device → that device is locked for 5 minutes and the owner is notified in-game.
- **Deny-by-default routing (Dynmap CVE lesson):** one central auth layer in front of every route and WebSocket message type. Only `/`, static assets, `POST /api/pair` and `GET /api/pair/<id>` are public. A test walks every registered route without a token and expects 401/403.
- **Trusted proxies:** `webTrustedProxies` (default empty). X-Forwarded-For / CF-Connecting-IP are only honored when the direct peer is in that list; otherwise the socket address is used for rate limits and the LAN check.
- **Bans:** connections from IPs banned on the MC server are rejected; banned players' devices are dead.
- **Token expiry:** devices unused for 30 days expire.
- **Cloud fallback for Lua:** `cloud.isOnline()` already exists; also add `cloud.getLastClientSeen()` (seconds since any authorized dashboard client was connected, or nil) so scripts can implement "shut down if nobody is watching".
- **Docs:** docs/HOSTING.md with LAN setup, opening the port on a hosted server, Caddy reverse proxy with HTTPS, and Cloudflare Tunnel.
- **Soak test (after v0.1 works):** a dedicated ATM11 server with all its mods plus CC: Tweaked and cccloud, 24 h, with simulated dashboard clients and a Lua script updating widgets every tick; record tick time, heap and thread count over time.

## v0.2 – Cloud Bridges for AE2 and Refined Storage (soft dependencies)
ATM11 ships Applied Energistics 2 (`ae2`, 26.1.13-beta) and Refined Storage 2 (`refinedstorage`, neoforge 3.2.1). Advanced Peripherals (ME/RS Bridge) does not exist for 26.1, so this fills a real gap.

### Blocks
- `cccloud:me_cloud_bridge` – registered only when `ae2` is loaded. A network device: it joins the AE2 grid like any other ME device (cable-connected grid node, uses 1 channel, idle power ~1 AE/t).
- `cccloud:rs_cloud_bridge` – registered only when `refinedstorage` is loaded. Joins the RS2 network as a network node (small energy usage).
- Both are full blocks in CC style (CC casing language, with an ME-fluix-purple / RS-themed accent in the port; see art brief) and have `status=offline|connecting|online` (online = connected to a powered network).
- Recipes (with `neoforge:mod_loaded` conditions), CC style:
```
me_cloud_bridge          rs_cloud_bridge
 G K G                    G K G
 M I M                    M I M
 G G G                    G G G
G=#c:ingots/gold K=cccloud:cloud_core M=computercraft:wired_modem
I=ae2:interface (ME)  /  I=refinedstorage:interface (RS)
```

### CC peripheral (types `me_cloud_bridge` / `rs_cloud_bridge`, same method set)
- `isConnected()`, `getEnergy()`, `getMaxEnergy()`, `getEnergyUsage()`
- `getStorage()` → `{itemsUsed, itemsTotal, typesUsed?, fluidsUsed?, fluidsTotal?}` (whatever the mod exposes; document units)
- `listItems([filter], [limit=256])` → `{ {name="minecraft:iron_ingot", displayName="Iron Ingot", count=1234, craftable=true}, ... }`
- `getItem(name)`, `findItems(query, [limit=50])` (case-insensitive match on id and display name)
- `isCraftable(name)`, `craft(name, count)` → `{ok, jobId?|error}`, `getCraftingJobs()`, `cancelCraft(jobId)` if supported
- `listFluids([limit])`
- No item export/import in v0.2 (that can move items out of the network; later, behind canControl + config).
- Heavy calls (listItems) run on the server thread through CC's main-thread mechanism (`@LuaFunction(mainThread = true)`), with limits so they cannot lag the server.

### Direct mode (zero Lua)
An uplink attached directly to a bridge (`direct=true`) publishes a ready-made **Storage** section, no computer needed:
- gauges: storage used %, energy %; labels: total items, item types, energy usage
- **watchlist**: items with a minimum amount; when an item drops below its minimum → `warn` alert (once per crossing). The watchlist is stored in the bridge BlockEntity and edited from the dashboard.
- **search + craft**: search the whole network from the phone, see counts and whether an item is craftable, start a craft with an amount (canControl only, count 1..`maxCraftAmount` config, default 4096).
- recent crafting jobs with status.

### Protocol additions (browser ↔ mod)
New widget types (also usable from Lua later):
- `item_search` – client → `{"t":"query","reqId","uplinkId","id","q"}`; server → `{"t":"query_result","reqId","rows":[{"name","displayName","count","craftable"}],"truncated":bool}` (max 50 rows, 5 queries/s per client).
- `watchlist` – value = `[{"name","displayName","count","min"}]`; actions: `{"op":"add","name","min"}`, `{"op":"remove","name"}`, `{"op":"set_min","name","min"}`.
- `craft` actions go through the `item_search` widget: `{"op":"craft","name","count"}` → action_result with `jobId` or error (`not_craftable`, `no_cpu`, `missing_resources`, `too_many`).
- `jobs` – value = `[{"jobId","name","count","status":"queued|running|done|failed","progress":0..1}]`, read-only.
All of these are validated server-side like the other widgets. Item icons: show the display name plus the mod id as a small tag (no texture icons in v0.2).

## v0.2 – Both kinds of players: ready-made AND custom dashboards
Three levels, all on the same widget system:
1. **Zero code** – direct mode templates (uplink on a bridge → Storage; later: uplink on an energy block → Energy, on a redstone-controlled machine → On/Off).
2. **Low code (in the browser)** – an *edit mode* in the dashboard: reorder and resize cards and widgets (drag on desktop, move up/down on the phone), rename and hide, choose an accent colour and icon per card, create **pages/tabs** and move cards between them. The layout is stored in the mod per mainframe (SavedData, max 64 KB) and is the same for all devices. Only devices with canControl can edit. Lua-defined widgets keep their ids, so a layout survives script restarts; unknown ids are simply skipped.
3. **Full scripting (Lua)** – a richer API for custom dashboards.

### Lua API v2 (`cloud_uplink` peripheral; v1 calls keep working)
- Layout: `cloud.page(id, title, {icon=})`, `cloud.group(id, title, {page=, cols=1..4})`; every widget accepts `opts.page`, `opts.group`, `opts.span` (1–4 columns), `opts.order`, `opts.color`, `opts.icon` (from a fixed bundled icon set, e.g. "bolt", "flame", "drop", "box", "fan", "lock", "warning").
- New widgets:
  - `chart(id, label, {min, max, unit, points=60, interval=1})` – the mod keeps a ring buffer of the values set via `set()` and the dashboard draws a line chart or sparkline
  - `progress(id, label, {min, max, unit})`
  - `indicator(id, label, {value=false, onColor, offColor})` – an LED, read-only
  - `select(id, label, {options={"low","mid","high"}, value})`
  - `number(id, label, {min, max, step, value})` and `text(id, label, {maxLength=64, value})` – inputs (validated server-side, text is plain text)
  - `buttons(id, label, {options={"A","B","C"}})` – a segmented button group
  - `item_search`, `watchlist`, `jobs` (from the bridges) – in Lua they are bound to a bridge peripheral by name: `cloud.itemSearch(id, label, {bridge="me_cloud_bridge_0"})`
- `cloud.setMany({id=value, ...})` for batching, `cloud.notify(text, level)` = alert alias, `cloud.getClients()` → number of connected dashboards.
- A helper library shipped in the CC ROM via the mod's datapack (`data/computercraft/lua/rom/modules/main/cloud/ui.lua`, checked against how CC loads mod ROM content): a declarative builder plus an event loop, so a full dashboard fits in a few lines:
```lua
local ui = require("cloud.ui")
local dash = ui.connect()                       -- finds the uplink
dash:page("reactor", "Reactor", {icon="flame"})
local temp = dash:gauge("temp", "Temperature", {max=1200, unit="K", warn=900, crit=1100, page="reactor"})
dash:button("scram", "SCRAM", {color="red", confirm=true, pin=true, page="reactor"},
  function(user) reactor.scram(); dash:notify(user.." pressed SCRAM", "crit") end)
dash:every(1, function() temp:set(reactor.getTemperature()) end)
dash:run()
```
- In-game help: `help cloud` plus example programs `cloud-demo` (all widgets) and `cloud-storage` (custom storage dashboard via a bridge), shipped in the ROM `programs/` folder.
- Limits stay: 32 widgets per uplink → raised to 64 when pages are used; chart history is kept server-side and capped (60–300 points per chart).

## Layout schema
The layout of the dashboard is stored per mainframe (SavedData, max **64 KB** as UTF-8 JSON) and is the same for all devices. Only `canControl` devices may change it. The mod only stores and validates it; the browser merges it with what the Lua scripts declare. Reference implementation: `cloud/dev/protocol.ts` (`layoutDoc`, zod) and `cloud/web/src/lib/layout.ts` (`resolveLayout`, `snapshotLayout`).

### Messages
- Server → client: the `snapshot` gains `"layout": {...} | null` (null = never edited, the defaults apply).
- Client → server: `{"t":"layout_set","reqId":"...","layout":{...}}`.
- Server → client: `{"t":"layout_result","reqId":"...","ok":true}` or `{"t":"layout_result","reqId":"...","ok":false,"error":"read_only|invalid_layout|too_large|rate_limited"}`. The server may include `"layout"` (its normalised copy) in an ok result.
- After a successful save the server sends `{"t":"layout","layout":{...}}` to **every** connected client of that mainframe (including the sender).
- Suggested limit: 5 saves per 10 s per mainframe. Since a WebSocket frame is limited to 8 KB, the server must accept larger `layout_set` frames up to 64 KB, or the client must keep layouts under 8 KB. **Decision for v0.2: the mod raises its frame limit to 64 KB, but only for `layout_set` (or for all frames once the client is authenticated).** The other rules stay the same.

### Document
```json
{
  "v": 1,
  "pages": [
    {"id": "main",  "title": "Overview", "icon": "home"},
    {"id": "power", "title": "Power",    "icon": "bolt"}
  ],
  "cards": {
    "<cardKey>": {"page": "power", "order": 0, "span": 2, "title": "Reactor", "hidden": false, "color": "red", "icon": "flame"}
  },
  "widgets": {
    "<uplinkId>/<widgetId>": {"order": 0, "span": 1, "label": "Core temp", "hidden": false}
  }
}
```
Validation (reject the whole document with `invalid_layout` if any rule fails; unknown keys are an error):
- `v` = `1`.
- `pages`: 0–12 entries, `id` matches `[A-Za-z0-9_-]{1,32}` and is unique, `title` 1–48 chars, `icon` optional, from the icon set below. The array order is the tab order. Empty `pages` = use the defaults.
- `cards`: object, ≤ 256 keys, key length 1–160. Values: `page` ≤ 32 chars (an id from `pages`; unknown → the client falls back to the first page), `order` int 0–10000, `span` int 1–4, `title` ≤ 48, `hidden` bool, `color` ∈ `gold|cyan|green|yellow|red|purple|orange|gray`, `icon` from the icon set. All fields optional.
- `widgets`: object, ≤ 2048 keys, key length 1–160. Values: `order` int 0–10000, `span` int 1–4, `label` ≤ 48, `hidden` bool. All optional.
- Text values are plain text and are always rendered as text.
- Ids that no longer exist (removed uplinks/widgets) are kept as they are and skipped when rendering, so a layout survives script restarts.

### Card keys
A card is one of:
- `<uplinkId>` – the uplink's widgets that have neither `opts.group` nor `opts.page`
- `<uplinkId>/<groupId>` – a Lua group (`cloud.group(id, title, {page=, cols=})`)
- `<uplinkId>~<pageId>` – ungrouped widgets of that uplink that set `opts.page`

### Icon set (bundled, 8×8 pixel icons; the same names work for `opts.icon` in Lua)
`home bolt flame drop box fan lock warning gear chart star leaf heart cpu shield pick`

### Defaults when there is no layout
- Pages: `main` ("Overview", icon `home`) followed by the pages the scripts declared (`uplink.pages`, first one wins on duplicate ids). Cards whose page is unknown go to the first page.
- Card span: `group.cols`, else 2 when the card contains an `item_search` or a `chart`, else 1. Card colour `gold` (or the widget's `opts.color`), icon from `opts.icon`.
- Widget order: `opts.order`, else by type (gauge, indicator, chart, progress, label, item_search, watchlist, jobs, inputs, buttons group, buttons), then declaration order. Widget span: `opts.span`, else 1 for gauge/indicator/button, 2 for label/watchlist/jobs, otherwise full width. The card body has 2 columns (1-column cards, phones) or 4 columns (wider cards).

### Uplink snapshot additions for Lua API v2
- `uplink.pages`: `[{"id","title","icon"}]` from `cloud.page()`, `uplink.groups`: `[{"id","title","page","cols"}]` from `cloud.group()`.
- `chart` widgets: the snapshot carries `"history":[numbers…]` (oldest first, ≤ `opts.points`, max 300). A `value` delta with a number appends one point; a `value` delta with an array replaces the history.
- Action values: `select`/`buttons` → one of `opts.options` (string); `number` → number in `min..max`, snapped to `step`; `text` → string ≤ `opts.maxLength` (default 64, max 256), control characters stripped. `indicator`, `progress`, `chart` are read-only (`not_interactive`).
- `query_result` may carry `"error"` (`rate_limited`, `offline`, `unknown_widget`, `uplink_offline`) with empty `rows`.

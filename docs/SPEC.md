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

## v0.2 – Implementation notes (mod side)
What the Java side actually does, where the sections above leave room. Packages: `uplink` (widget model, validation), `layout` (layout validation + SavedData), `bridge` (mod-independent bridge core), `compat/ae2` and `compat/rs` (the only code that touches AE2/RS classes).

### Soft dependencies
- `CCCloud` calls `Ae2Compat.init` / `RsCompat.init` only if `ModList.get().isLoaded("ae2" | "refinedstorage")`. Blocks, items, block entity types, capabilities (CC peripheral, AE2 `IN_WORLD_GRID_NODE_HOST`, RS `NetworkNodeContainerProvider`) and the bridge GameTests are registered inside these classes. Without the mods, nothing in `compat/` is class-loaded.
- Recipes, loot tables and the bridge test instances carry `neoforge:conditions` with `neoforge:mod_loaded`; the pickaxe tag lists the bridges with `"required": false`.
- `neoforge.mods.toml` declares both mods as `optional` (AE2 ≥ 26.1.13-beta, RS ≥ 3.2.1).

### Bridges
- ME Cloud Bridge: in-world AE2 grid node, `REQUIRE_CHANNEL`, idle 1 AE/t, connects on all sides. RS Cloud Bridge: RS2 `SimpleNetworkNode`, 2 FE/t, active while the network has energy.
- `status`: `online` = node active (powered, AE2: with channel), `connecting` = part of a network that is not (yet) active, `offline` = alone.
- Peripheral methods all run with `mainThread = true`. Listings are served from an index snapshot rebuilt at most every 10 ticks (max 50 000 item types) and limited to 20 listings/searches per second per bridge (Lua and dashboard together; Lua gets an error, the dashboard `rate_limited`). Items are aggregated per item id (component variants are summed); `craft` always requests the plain item.
- Limits: `listItems` default 256 / max 1024, `findItems` default 50 / max 256, `listFluids` default/max 256/1024, filters ≤ 64 chars. Search: case-insensitive substring on id and display name, all words must match, `@modid` filters by namespace; results sorted by count (craftable items with 0 stored are included).
- `getStorage()`: `itemsUsed` (items), `typesUsed`, `fluidsUsed` (mB), and when known `itemsTotal`, `typesTotal`, `fluidsTotal`. AE2: totals are the byte capacity of basic cells in drives/chests × 8 items/byte (approximate, ignores the per-type overhead). RS: `itemsTotal` = capacity of storage blocks and disk drives (`StorageNetworkNode`); RS does not separate item and fluid capacity, so fluid storage counts into it; no `typesTotal`/`fluidsTotal`.
- `getEnergyUsage()`: AE2 average usage (AE/t); RS sum of the active nodes' usage (FE/t).
- Crafting: `craft(name, count)` validates (`offline`, `unknown_item`, `too_many` (> `maxCraftAmount`, default 4096), `not_craftable`, `busy` = more than 4 requests planning at once) and returns `{ok=true, jobId}` right away; planning happens asynchronously (AE2 `beginCraftingCalculation` + `submitJob`; RS `getPreview` off-thread, then `startTask` on the server thread with a 250 ms planning budget) and is polled every tick. Plan errors: `missing_resources`, `no_cpu` (AE2), `timeout` (30 s). Jobs (16 per bridge, newest first, not persisted) have `status` `queued|running|done|failed|cancelled`, `progress` 0..1, `error?`, `user`. Jobs started elsewhere on the network (terminals) are appended as `running`.
- Dashboard craft actions answer with `action_result` once the job is planned (`ok` + `jobId`, or the plan error); if planning takes longer than 5 s the answer is `ok` + `jobId` and the jobs widget shows the outcome. Lua-bound item_search widgets also queue `cloud_action` with `{op="craft", name, count, jobId}`.
- Watchlist: ≤ 32 entries per bridge, stored in the bridge block entity; errors `already_watched`, `too_many`, `unknown_item` (not a registered item). Checked every second; an item dropping below `min` raises a `warn` alert once per crossing, sent by every uplink that shows that bridge's watchlist.
- Direct mode: an uplink whose `facing` block is a bridge switches to the Storage template (title "ME Network"/"RS Network", widgets `storage`, `energy`, `items`, `types`, `usage`, `search`, `watch`, `jobs`; snapshot has `"template":"storage","bridge":"me"|"rs"`). Values refresh every second. Lua calls that change widgets fail on such an uplink. Removing the bridge clears the section.

### Lua API v2 details
- `cloud.page(id, title, {icon})`, `cloud.group(id, title, {page, cols})`: ≤ 8 pages and ≤ 16 groups per uplink; ids like widget ids. Declaring a page raises the widget limit from `maxWidgetsPerUplink` (32) to `maxWidgetsWithPages` (64). `cloud.clear()` also removes pages and groups.
- Common widget options: `page`, `group` (ids), `span` 1–4, `order` 0–10000, `color` (cyan, green, yellow, red, gray), `icon` (the icon set above). `label(id, text, [opts])` accepts them too.
- `chart`: `points` 10–300 (default 60), `interval` 0.5–3600 s (default 1), `style` `line|sparkline`, optional `min/max/unit`. `set()` only stores the current value; the uplink samples it every `interval` (game time) into the ring buffer and broadcasts each sample as a `value` delta (= one appended point). The history is saved with the uplink.
- `select`/`buttons`: `options` is a list of 1–16 unique, non-empty strings ≤ 32 chars; `select` value defaults to the first option. `number`: `min/max` default to the 32-bit int range, `step` optional (snapping only with a step). `text`: `maxLength` 1–256 (default 64), optional `placeholder`; values from Lua must be plain text, values from the dashboard have control characters stripped. `pin`/`confirm` work on every interactive widget.
- `itemSearch/watchlist/jobs(id, label, {bridge=name})`: `name` must be a bridge peripheral the computer can reach (a side or a wired-network name); the widget remembers the bridge's position. `itemSearch` also takes `max` (≤ `maxCraftAmount`) and `pin`/`confirm`.
- `cloud.setMany(t)`: ≤ 64 entries, all validated before any is applied. `cloud.notify` = `cloud.alert`. `cloud.getClients()` = connected dashboards of the uplink's mainframe.

### Protocol details
- Frame size: a raw-byte guard in front of Netty's frame decoder reads each frame header and closes the connection with 1009 before the payload is buffered if a frame (or a fragmented message) is larger than the current limit: **2 KB before authentication**, 64 KB + 1 KB after. After authentication every message except `layout_set` is still limited to 8 KB (checked after parsing).
- `layout_set` → `layout_result`: checks in this order: `read_only`, `rate_limited` (5 per 10 s per mainframe), `too_large`, `invalid_layout`; rights are re-checked on the server thread. Layouts live in `data/cccloud/layouts.dat`, are loaded into the hub on server start and deleted with the mainframe.
- `query` needs an authenticated device but not `canControl` (read-only search). Besides the listed errors, `query_result.error` can be a rights error (`no_rights`, `banned`).
- `action_result` for craft actions carries `jobId`. Possible bridge errors: `unavailable` (bridge not loaded/not found), plus the crafting and watchlist errors above.

## v0.3 – Less setup, in-game GUI (feedback from the first in-game test, 2026-10-23)

### Auto mode: uplink on a computer works without any Lua
Problem: after linking an uplink the dashboard said "No uplinks yet" plus Lua code to type. A player should see something useful immediately.
- An uplink attached to a CC computer (normal/advanced/command computer, turtles excluded for now) whose attached computers have not defined any widget gets an automatic **Computer card**: title = computer label or "Computer #<id>", an indicator on/off, buttons Turn on / Reboot / Shut down (canControl; Shut down and Reboot ask for confirmation), and the six redstone sides (top/bottom/left/right/front/back) each with an output toggle and an input indicator, plus a 0–15 slider for analog output (collapsed under "Advanced" in the UI).
- As soon as a Lua program defines a widget, the auto card is replaced by the Lua widgets. When the program stops and clears its widgets (or the computer reboots), the auto card comes back.
- Config `autoComputerCard` (default true).
- The redstone values are read and written through CC's API on the server thread (no Lua involved), so this works even when the computer is off.

### In-game GUI
- **Mainframe screen** (right-click, owner/trusted/op; others see a read-only status screen without the code):
  - big status (online/connecting/offline/redstone-killed) and an on/off toggle for the dashboard
  - dashboard URL(s): every non-loopback LAN address of the host plus `publicUrl` if set, each with a copy-to-clipboard button
  - pairing code with a "new code" button and a countdown (10 min)
  - pending approvals with Allow / Deny
  - devices: name, player, last seen, revoke; "revoke all"
  - trusted players: add (name field) / remove
  - PIN: set/change/clear (masked input)
  - LAN-only / internet status (read-only, explains the config switch)
- **Uplink screen** (right-click): status, linked mainframe (position, distance, range OK), title, widget count, mode (auto / Lua / direct), and an "unlink" button (owner/trusted).
- Style: CC: Tweaked look (dark terminal panel with the CC border, silkscreen-like pixel headings, gold for advanced, cyan accents). GUI textures are drawn by the art generator (`textures/gui/mainframe.png`, `textures/gui/uplink.png`, widgets in `textures/gui/widgets.png`); the Java side draws text and dynamic parts.
- All actions go through custom payload packets with server-side permission checks (never trust the client). The screen gets a snapshot when opened and live updates while it is open (pending approvals appear without reopening).
- Chat stays as a fallback: the clickable Allow/Deny chat message still appears for players who do not have the screen open.

### Web empty state
- Replace the "No uplinks yet" Lua wall with a 3-step setup guide using the in-game names (Place Cloud Uplink on a computer → link with the Link Card → it shows up here automatically as a Computer card). Below it, a collapsible "Build your own dashboard" with a **copy button** for the starter script and the hint "or run `cloud-demo` on the computer".
- Clipboard: `navigator.clipboard` only works on HTTPS/localhost, so fall back to a hidden textarea + `document.execCommand("copy")` on plain HTTP; show "Copied".
- Also add a copy button to every code block in the app.

### Web protocol for the auto Computer card (new widget type `redstone_sides`)
The existing widgets cannot show six sides with output + input compactly, so the auto card uses one new widget type. The Java side sends the auto card as a normal uplink snapshot:
```json
{"t":"uplink","uplinkId":"…","title":"Computer #7","online":true,"mode":"auto","widgets":[
  {"id":"power",    "type":"indicator", "label":"Computer on", "opts":{"onColor":"green","offColor":"gray","span":2}, "value":true},
  {"id":"turn_on",  "type":"button",    "label":"Turn on",   "opts":{"color":"green"}},
  {"id":"reboot",   "type":"button",    "label":"Reboot",    "opts":{"color":"yellow","confirm":true}},
  {"id":"shutdown", "type":"button",    "label":"Shut down", "opts":{"color":"red","confirm":true}},
  {"id":"redstone", "type":"redstone_sides", "label":"Redstone", "value":[
    {"side":"top","out":0,"in":0}, {"side":"bottom","out":0,"in":0}, {"side":"left","out":0,"in":15},
    {"side":"right","out":0,"in":0}, {"side":"front","out":0,"in":0}, {"side":"back","out":15,"in":0}]}
]}
```
- `uplink.mode` (new, optional): `"auto"` (automatic Computer card), `"lua"` (widgets from a program), `"direct"` (bridge template). The dashboard shows an "auto" tag on auto cards.
- `redstone_sides` value: exactly the six sides `top|bottom|left|right|front|back` (relative to the computer, like CC's `redstone` API), each with `out` (the computer's output level, 0–15) and `in` (the input level, 0–15). Send the whole array as a `value` delta whenever any level changes (coalesced like other values).
- Action: `{"t":"action","reqId","uplinkId","id":"redstone","value":{"op":"set_output","side":"front","value":15}}`. `value` is an int 0–15. The UI toggles between 0 and 15 when a side is tapped; the "Advanced" sliders send any level. Validation errors: unknown side, non-integer or out of range → `invalid_value`; read-only device → `read_only`. Applied on the server thread with `redstone.setAnalogOutput` semantics, and works while the computer is off.
- Buttons `turn_on`/`reboot`/`shutdown` are ordinary button actions (`value: true`). Turn on is a no-op when the computer is already on.
- Like all action values, the `op` object is validated server-side (`set_output` is the only op for this widget type).

### Power (v0.3)
The mainframe needs electricity, like other tech blocks in ATM11.
- Unit: FE (Forge Energy). On NeoForge, FE = what most mods call RF; Powah, Ender IO, Oritech, Energized Power, AE2 (accepts FE), Refined Storage and AllTheModium machines all use it. ATM11 ships no mod with EU (IC2/GregTech) or Mekanism Joules, so FE covers every power mod in the pack. Mods with other units can convert to FE through their own converters; we do not add EU/J support.
- Expose `Capabilities.Energy.BLOCK` on both mainframe halves (all sides) using NeoForge 26.1's transfer API (`net.neoforged.neoforge.transfer.energy.EnergyHandler`, e.g. a `SimpleEnergyHandler`-style buffer); check the 26.1 sources for how legacy `IEnergyStorage` providers interoperate and make sure both cable styles can insert. Receive-only.
- Default consumption (server config, all 0 = free): `mainframeBaseFE` 40 FE/t, `mainframePerUplinkFE` 5 FE/t per linked loaded uplink, `mainframePerClientFE` 2 FE/t per connected dashboard client. Buffer `mainframeBufferFE` 200 000 FE, max input 2 000 FE/t. `requirePower` (default true).
- Out of power → status `offline` with reason `no_power` (shown in the web app as "Mainframe has no power", in the in-game GUI as a red "NO POWER" badge with buffer bar), uplinks report offline. Power returns → reconnects automatically. A brownout keeps going for 5 s on the buffer before cutting, to avoid flicker.
- Uplinks and bridges need no FE (bridges use the AE2/RS network power).
- Lua (`cloud_mainframe`): `getEnergy()`, `getEnergyCapacity()`, `getEnergyUsage()`. The browser snapshot gets `power: {stored, capacity, usage, ok}` on the mainframe object so the dashboard can show a small power bar in the header.
- Creative-mode convenience: a creative-only "Creative Power" toggle is NOT added; use a creative energy cell from another mod.

### Item icons (v0.3)
Show real item icons in the dashboard (search results, watchlist, jobs, craft sheet).
- Server-side icon resolver, no client needed (works on dedicated servers): read item model definitions (`assets/<ns>/items/<id>.json`) and block/item models from the mod jars and vanilla on the server's classpath/resource manager, follow parents.
  - `item/generated`/`item/handheld` style models → compose `layer0..n` textures (first animation frame for animated textures; apply known tints where cheap: e.g. leather/potion/spawn egg default colors via the item's tint source if resolvable, otherwise untinted).
  - block models (full cubes and most cube-like models) → render a small isometric icon from the top, north and east face textures with inventory-style shading (top 100 %, left 80 %, right 60 %), 32×32 output, nearest neighbor.
  - special/builtin models (chests, shields, heads, banners) → best-effort fallback (particle texture), else none.
  - The vanilla client jar is not on a dedicated server: vanilla item textures must still work. Check what 26.1 dedicated servers ship (server jar has no textures). Options: bundle nothing from Mojang (we may not redistribute it); instead let the dashboard fall back to a letter tile for vanilla items on dedicated servers, OR read textures from the integrated client in singleplayer/LAN where `Minecraft` assets are available. Document the behavior.
- Endpoint `GET /api/icon/<namespace>/<path>.png` – public route (explicitly allow-listed; still subject to the LAN-only check, rate limit 200/min per IP and the security headers), `Cache-Control: public, max-age=86400`, ETag. Unknown item → 404 (the UI shows a letter tile). Only resolves registered item ids (no path traversal; validate `[a-z0-9_.-]+` per segment).
- Cache: in memory (LRU, ~2000 icons) plus on disk under `<world or server>/cccloud/icon-cache/` keyed by item id + mod version hash. Rendering happens off the server thread (ImageIO on a small executor); resource reading happens in a thread-safe way.
- The web app shows 24 px icons (32 px source, image-rendering: pixelated) next to item names, with lazy loading and a letter-tile fallback.
- **Update (client-rendered icons, preferred):** every player has the mod installed on their game client, and the client can render the real inventory icon of any item (tints, 3D block models, chests, enchant glint excluded). So icons are rendered by a game client and cached on the server:
  - Singleplayer/LAN host: render directly in the client JVM (offscreen render target, 32×32 or 64×64, transparent background) on the render thread, in small batches per frame.
  - Dedicated server: when the dashboard requests an icon that is not cached, the server queues it and asks one connected client (round-robin, opt-out via client config `shareIconRenders`) via a custom payload to render a batch (max 32 per request, max 1 request per second per client). The client sends back PNG bytes (validated by the server: PNG signature, ≤ 16 KB, exact size, decodes cleanly). The disk cache persists across restarts, so after a while every common item has an icon even when nobody is online.
  - While no client is available: mod-jar texture reading as described above, else the letter tile. The response carries `Cache-Control: no-cache` for fallback images so the real icon replaces them later.
  - Server admins can optionally set `vanillaAssetsJar` (path to a Minecraft client jar they own) so the server can read vanilla textures itself; nothing from Mojang is bundled.
- **Update 2 (pre-render everything once):** do not wait for dashboard requests. The server keeps a persistent icon store (`<world or server>/cccloud/icons/`, one PNG per item or packed into a few zip/atlas files) with a manifest of item id → hash of (mod id + mod version). Missing or outdated entries are queued.
  - The first client with the mod that joins (or the singleplayer client on world load) gets the queue and renders **all registered items** in the background (ATM11 has ~36,000), throttled so it never costs noticeable FPS (e.g. ≤ 2 ms per frame of render time), and uploads them in batches (up to 256 icons per batch while the player is idle, 32 while active; server-side PNG validation unchanged). Several online clients split the queue. Progress is shown in the mainframe GUI ("Item icons: 12,400 / 36,715") and logged; a client can pause it via client config.
  - After a modpack update only new or changed items are rendered again.
  - Admin commands: `/cccloud icons status`, `/cccloud icons rebuild` (re-render all), `/cccloud icons clear`.
  - Storage size target: 32×32 PNG ≈ 1–2 KB → about 40–70 MB for 36k items; pack them into zip files per namespace to avoid 36k small files, and serve from memory-mapped/LRU cache.

### Linked Cloud Bridges (v0.3, from the second in-game test)
An ME/RS Cloud Bridge on its own is a dashboard member; no extra uplink is needed.
- The **Cloud Link Card** binds directly to a bridge (right-click the mainframe, then the bridge). Same rules as uplinks: only the owner, trusted players or ops of the mainframe; the range is unlimited and works across dimensions (like an advanced uplink).
- A linked bridge publishes the ready-made **Storage section** itself (same widgets as direct mode: `storage`, `energy`, `items`, `types`, `usage`, `search`, `watch`, `jobs`). The section id (`uplinkId` in the protocol) is a stable UUID stored in the bridge, so layouts and bookmarks survive restarts. Snapshot extras: `"mode":"direct","template":"storage","bridge":"me"|"rs","source":"bridge"`. `online` follows the mainframe; the storage network's own state shows in the values.
- An uplink attached to a bridge keeps working as before (direct mode). If the bridge is linked as well, only the **bridge's section** is shown: the uplink's section is removed from the dashboard while the bridge is linked and comes back when the bridge is unlinked.
- Right-click on a bridge opens the uplink-style screen: section status, storage network status, linked mainframe (position, distance, unlimited range), title, mode and **Unlink** (owner/trusted/op).
- Breaking a linked bridge removes its section; an unloaded one shows as offline.

### v0.3 – Implementation notes (mod side)
- **Auto card:** implemented exactly as "Web protocol for the auto Computer card" (`power`, `turn_on`, `reboot`, `shutdown`, `redstone` of type `redstone_sides`; action op `set_output`). `redstone_sides` cannot be created from Lua. Inputs and outputs are refreshed every 10 ticks. Power and redstone go through CC: Tweaked's `ServerComputer`; the outputs need CC's internal `RedstoneState`, reached by reflection; if a future CC version changes it, outputs become read-only (`unsupported`) instead of breaking. Limitation: CC itself clears all outputs while a computer is off, so `set_output` on a switched-off computer is stored but only emitted once it is on; inputs are read while it is off.
- **Mode switching:** the card appears when a computer (not a turtle) is the block the uplink sticks to and its programs defined no widgets; the first Lua call that adds a widget, page, group or title replaces it; `cloud.clear()` or the last computer detaching (shutdown/reboot) brings it back. The snapshot carries `mode`.
- **Screens:** real menus (`cccloud:mainframe`, `cccloud:uplink`, the latter also for bridges). The view is JSON (≤ 32 KB) sent on open and refreshed every 10 ticks while it changes; actions are payloads `cccloud:mainframe_action {containerId, action, arg}` and `cccloud:uplink_action {containerId, action}`. Server checks per packet: the menu with that container id is open and in range (8 blocks), the player's rights are re-checked live, and the argument is validated (`GuiActions`, unit tested for every action). Read-only viewers get a view without code, approvals, devices, trust list and PIN state. Approval requests go to the screen for players who have it open, otherwise to chat as before.
- **URL list:** IPv4 only; down/loopback interfaces and 169.254/16 are skipped; private ranges first (including Tailscale's 100.64/10), then public addresses; `publicUrl` first if set; a specific `webBind` address replaces the list.
- **Dev runs:** `runServer`/`runGameTestServer` use web port 18787 (`-Dcccloud.devWebPort`) so they do not collide with a game on the same machine.
- **Power (as implemented):** NeoForge 26.1 has no legacy energy capability any more; every 26.1 mod reaches the mainframe through `Capabilities.Energy.BLOCK`, and code written against `IEnergyStorage` wraps it with `IEnergyStorage.of(handler)` (tested). The 2 000 FE/t input limit is per tick, not per call. Usage counts linked uplinks *and* linked bridges that are loaded. The snapshot's `mainframe` object and `state` messages carry `power {stored, capacity, usage, ok}` and `reason` (`no_power`, `redstone`, `cloud_disabled` or absent); power numbers are sent at most once per second, a change of `ok` immediately. Existing worlds: a mainframe placed before this version starts with an empty buffer and goes offline after 5 s until it is powered (or `requirePower=false`).
- **Item icons (as implemented):**
  - Where icons come from: the game client renders them with the game's own inventory renderer (3D blocks, tints, mod models), so the singleplayer/LAN host and every player with the mod contribute; the integrated server uses the same packets as a dedicated server. A dedicated server has no Minecraft textures (the server jar ships none), so until a client has rendered them, vanilla items get 404 (the UI shows the letter tile) unless `vanillaAssetsJar` points to a client jar the admin owns; modded items fall back to the flat `textures/item|block/<path>.png` from the mod jar. Fallback responses are `no-cache`, rendered icons `max-age=86400` with an ETag.
  - Store: `<world>/cccloud/icons/<namespace>.zip` + `manifest.json`; icons are 32×32 RGBA PNGs (≈ 0.9 KB each). Measured with `runClient -PiconBenchmark` (vanilla + CC + AE2 + RS = 2 305 items): 2 301 rendered, 4 not renderable, 0.33 ms GPU-submission time per item, 2.1 MB on disk, 80 s wall time end to end in singleplayer with the 2 ms/frame budget (dominated by the one-batch-per-second hand-out and frame pacing). Extrapolated to ATM11 (~36 000 items): ≈ 12 s of render time spread over ≈ 2–4 min of play, ≈ 33 MB on disk.
  - Upload checks: only ids of the batch assigned to that client, at most one outstanding batch and one request per second per client, ≤ 64 icons and ≤ 24 KB per packet, strict PNG validation (signature, CRCs, IHDR 32×32 8-bit RGBA, IDAT/IEND only, exact inflated size, valid filters, ≤ 16 KB). Anything else is discarded.

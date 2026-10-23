# CC: Cloud – Spec (v0.1)

NeoForge 26.1.2 addon for CC: Tweaked 1.120.0. Mod id `cccloud`, Java package `ai.ezfox.cccloud`.
Goal: control CC: Tweaked computers and machines in-game from a mobile-first web dashboard.

CC: Tweaked 26.1 source for reference (read-only): a local checkout of https://github.com/cc-tweaked/CC-Tweaked (branch for MC 26.1), e.g. in `$CCT_SRC`.

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

## v0.4 – Network: range, relays, power scaling, network map (requested 2026-10-23)
The cloud network should feel like a real infrastructure (think UniFi): devices have range, relays extend it, the whole thing costs more power the bigger it gets, and there is a live map of everything that is connected.

### Range and topology
- Every device connects to the nearest reachable **node** (the mainframe or a relay) – not necessarily directly to the mainframe. The server computes the network as a graph and routes every device to the mainframe over the best path (fewest hops, then best signal). Max 8 hops.
- Default ranges (config, in blocks, same dimension, spherical distance):
  - Cloud Uplink: 48 · Advanced Cloud Uplink: 128
  - Cloud Bridges: 48 (they are network devices like uplinks)
  - Cloud Relay: 96 to other nodes, 48 for devices · Advanced Cloud Relay: 256 to other nodes, 128 for devices
  - Ender Relay: links to other Ender Relays across any distance and across dimensions (pairs/groups via the Link Card)
  - Mainframe: acts as a node with 96 range.
- Signal quality per link = 1 − (distance / range), shown as 0–100 % with bars. Below 15 % the link is "weak" (still works, shown yellow).
- When a relay is broken, unloaded or out of power, everything behind it re-routes over another path if one exists, else it goes offline (status `no_route`). Chunk loading: relays never force-load chunks; unloaded parts show as `unloaded`.
- Linking stays with the Link Card (the device joins the mainframe's network); range is checked against the whole network, not only the mainframe. Error message names the nearest node and how far it is.
- Migration: existing worlds keep working – if a v0.3 device is out of range under the new rules, it stays connected but is flagged `legacy_out_of_range` on the map until it is re-linked or a relay is placed (config `legacyRangeGrace`, default true).

### Power scaling
- The mainframe's usage grows with the network: base 40 FE/t + 4 per uplink + 8 per advanced uplink + 10 per bridge + 2 per dashboard client + 1 per hop beyond the first for each routed device (config).
- Relays need their own FE: Cloud Relay 20 FE/t, Advanced 60 FE/t, Ender Relay 120 FE/t (config, 0 = free), buffer 50k–200k. Out of power → relay offline → re-routing as above.
- The mainframe screen and the dashboard show a power breakdown (what costs how much).

### New blocks (art in CC style; see art brief)
- `cccloud:cloud_relay` / `cccloud:advanced_cloud_relay`: a slim relay mast on a CC casing base (antenna allowed here, slender, 1 block tall, max ~4 px wider than a CC modem plate at the top), status LEDs, slowly rotating emitter ring (BER).
- `cccloud:ender_relay`: gold, with a floating ender-eye core like the advanced uplink but bigger.
- Recipes (CC style): relay = stone + wired modem + cloud core + redstone; advanced = relay + gold + ender pearl; ender relay = advanced relay + ender eyes + gold block (exact grids in the recipe JSON, documented in /docs).

### Network map
- Browser protocol: snapshot gets `network: {nodes:[{id,type,name,pos:{x,y,z},dim,status,power?,range,deviceRange}], links:[{a,b,signal,dist,kind:"radio"|"ender"}], routes:{deviceId:[nodeIds...]}}`, plus `network` deltas (rate-limited to 2/s). Positions are only sent to devices with canControl (read-only devices get the topology without coordinates).
- Web: a **Network** page (always present, own tab with icon) with two views:
  1. **Topology** (UniFi-style): mainframe at the root, relays as branches, devices as leaves, link lines colored by signal, status dots, click → detail panel (type, position, dimension, status, signal, hops, power, uplink title/widgets count, last seen, actions: open its card, unlink if canControl).
  2. **Map**: top-down x/z map per dimension (tabs), grid with coordinates, range circles (toggle), links, pan/zoom (touch-friendly), auto-fit, the player can tap a node for the same detail panel.
- In-game: a **Network** tab in the mainframe screen: a simple tree list (indent per hop, status dot, signal bars, distance) and a small top-down mini map (pixel grid, nodes as colored dots, lines for links), plus a hint for out-of-range devices.
- Lua (`cloud_mainframe`): `getNetwork()` returns nodes/links/routes like the snapshot (with positions; it is in-world access).

### Decision 2026-10-23: no particle effects
The `data_packet` particle (packets flying between uplink and mainframe) is removed, and v0.4 adds no particles between relays/devices – they cost performance under heavy traffic. Block animations (fan, hologram, rings, cores) stay, are distance-culled (~32 blocks) and can be turned off with the client config `animations`.

### v0.4 – Implementation notes (mod side)
- **Routing core** `network/graph/NetworkGraph` (pure, unit tested): a radio link exists when either side reaches the other (distance ≤ the larger of the two ranges, same dimension); signal = 1 − distance / that range, weak < 15 %. Ender relays linked to the same mainframe are connected across any distance/dimension (kind `ender`, signal 1). Routes: fewest hops, then the strongest weakest link, max 8 hops (the device link included). Devices attach to the best reachable node. Unavailable nodes (unloaded, out of power, mainframe offline) carry no traffic.
- **Relays** are shared infrastructure: any mainframe's network may route through any relay in range; no linking needed. **Ender Relays** join a mainframe's ender group with the Link Card (owner/trusted/op); they also work as normal radio nodes. Relay blockstate `status=offline|connecting|online|no_power`.
- **NetworkService** recomputes only on topology events (blocks loaded/unloaded/placed/broken, power flips of relays and mainframes, links, ender links, config reload) plus a cheap safety pass every 10 s; devices only look up their route. The load test stays at ~0.25 ms/tick on a quiet machine.
- **Linking** checks the whole network geometrically (power is ignored for the check). The error names the nearest node, its distance, the max range and its position. Devices linked before v0.4 load without the `net_v` marker → `legacy`; with `legacyRangeGrace` they stay connected directly to the mainframe when out of range (`flags:["legacy_out_of_range"]` on the map, the uplink screen says so) until re-linked. Re-linking clears the flag.
- **Device status:** `offline` with reason `no_route` when there is no path; the uplink screen shows `routeState` and `hops`.
- **Power:** mainframe usage = `mainframeBaseFE` + per connected uplink / advanced uplink / bridge + per dashboard client + per extra hop (`mainframePerUplinkFE`, `…PerAdvancedUplinkFE`, `…PerBridgeFE`, `…PerClientFE`, `…PerHopFE`). Relays: `relayFE`/`advancedRelayFE`/`enderRelayFE` with buffers `…BufferFE`; FE via `Capabilities.Energy.BLOCK` from every side; same brownout grace as the mainframe. Breakdown: in-game Network tab, view `powerBreakdown`, Lua `getPowerBreakdown()`.
- **Protocol:** snapshot `network` and `{"t":"network","network":{…}}` deltas (only on change, at most 2/s). Nodes: mainframe, relays (`relay`, `advanced_relay`, `ender_relay`), devices (`uplink`, `advanced_uplink`, `bridge`, `device` for unloaded ones); fields `id,type,name,pos?,dim,status,power?,range,deviceRange?,parent?,hops?,flags?`; `status` ∈ `online|offline|no_power|no_route|unloaded|redstone|cloud_disabled`. Links `a,b,signal,dist?,kind,weak?`. `pos` and `dist` only for canControl devices. Relays near the network that cannot carry traffic (e.g. no power) are included so the map can show them.
- **Lua** `cloud_mainframe.getNetwork()` (with positions) and `getPowerBreakdown()`.
- **Recipes:** relay = stone + wired modem + cloud core + redstone (`SMS/RKR/SSS`); advanced = relay + gold ingots + ender pearl (`GEG/GXG/GGG`); ender = advanced relay + 3 ender eyes + gold block (` Y /YXY/ B `).

## v0.5 – Integrations, rules, push, browser terminal (approved 2026-10-23)
Everything here follows the existing security model (deny-by-default, canControl, validation, rate limits) and is optional per server config. Mod integrations are soft dependencies in `compat/<modid>` packages, loaded only when the mod is present (ATM11 mod ids to verify from the jars: powah, enderio, oritech, energizedpower, energymeter, generatorgalore, mysticalagriculture, productivebees, hostilenetworks, mffs, merequester, extendedae, spark, ftbteams, ftbchunks, journeymap, simplebackups).

### A. Ready-made cards (zero code, like the bridges' Storage card)
An uplink attached directly to a supported block (direct mode) publishes a template card:
- **Energy** (any block with the FE capability, plus richer data for Powah energy cells/reactor, Ender IO capacitor banks, Oritech, Generator Galore generators, Energized Power): stored/capacity gauge, in/out FE/t where the mod exposes it, a history chart (last 60 min, 1 point per 10 s, server-side ring buffer), reactor status/temperature/fuel for the Powah reactor, on/off via the block's own redstone mode if exposed.
- **Energy Meter** (energymeter): live flow FE/t + history chart, mode, transferred total.
- **Farms**: Mystical Agriculture growth accelerators/crops are too simple – focus on machines with state: Hostile Neural Networks simulation chamber (model, data, progress, output), Productive Bees hives/centrifuges (honey level, bees, output inventory fullness). Alert when an output is full or a machine is blocked (once per transition).
- **MFFS** force field projector: field on/off (canControl + PIN by default), power, status.
- **ME Requester / ExtendedAE**: list the requests with their target amounts, edit amount/enable (canControl).
Every template is a normal set of widgets, so the user can rearrange it in edit mode.

### B. Server & team
- **Server card** on the mainframe (canControl only): TPS/MSPT (from the server tick times; spark's API if present for per-dimension and lag sources), players online, uptime, memory; history chart.
- **FTB Teams**: if present, members of the owner's team count as trusted automatically (config `ftbTeamsTrust`, default true); team changes apply live.
- **FTB Chunks / JourneyMap**: claimed chunks of the owner's team as an overlay on the network map (FTB Chunks API); JourneyMap is client-only → skip unless trivial.
- **Simple Backups**: card with the last backup time and a "Backup now" button (canControl + confirm), if the mod exposes an API/command; otherwise run its command server-side.

### A + B – Implementation notes (mod side)
No new browser message types: every card is an ordinary section (`uplink` snapshot + `value` deltas + `alert`) built from the existing widget types, so it can be rearranged in edit mode. Code: `compat/card` (framework + generic FE card), `compat/server` (mainframe cards), `compat/team` (team abstraction), one `compat/<modid>` package per mod. `CCCloud` calls `compat.Integrations.init` once; each mod package is only class-loaded when `ModList` reports the mod, and a failing integration (missing class, changed method, exception) disables just that card with one log line – the card simply does not appear (the generic FE card may take over).

**Config:** own file `serverconfig/cccloud-integrations-server.toml`: `cards.deviceCards` (true), `cards.mffsRequirePin` (true), `server.serverCard` (true), `server.ftbTeamsTrust` (true), `server.ftbChunksClaims` (true), `server.backupCard` (true).

**Device cards (A):**
- An uplink shows the card of the block it sticks to when its section is free (like the Computer card): a Lua program's first widget replaces the card, `cloud.clear()` brings it back. Detection runs once per second; mod-specific providers win over the generic FE card; this mod's own blocks are excluded. Uplinks may now also stick to any block with a block entity (machines with non-full models such as the MFFS projector), not only to sturdy faces.
- Snapshot: `"mode":"direct","template":"<kind>"`. Kinds: `energy` (generic FE), `powah_cell`, `powah_generator`, `powah_reactor`, `enderio_machine`, `enderio_solar`, `oritech_storage`, `oritech_generator`, `energizedpower_machine`, `generatorgalore_generator`, `energymeter`, `hostilenetworks_chamber`, `productivebees_hive`, `productivebees_centrifuge`, `mffs_projector`, `merequester`. The kind is saved with the uplink; chart history survives reloads.
- Energy card widgets: `stored` (gauge %), `energy` (label "x / y FE"), `input` + `output` (labels, when the mod reports rates: Oritech storage statistics, Powah reactor generation) or `flow` (net FE/t measured from the stored amount), `history` (chart of stored %, 300 points × 12 s = 60 min; the chart cap is 300 points), `redstone` (select, confirm) when the block has its own redstone mode (Powah, Ender IO, Energized Power). Generators add `running` (indicator) and `generation`. Powah reactor: `running`, `built`, `temperature` (0–1000 °C, warn 750, crit 900), `fuel`, `carbon`, `redstone_fuel`, `consumption`, `gen_mode` (toggle, confirm); alerts when it stops or runs out of fuel.
- Energy Meter: `flow`, `status`, `history` (FE/t, 60 min), `total`, `limit`, `mode` (select Split/Transfer/Consume, confirm).
- HNN simulation chamber: `status`, `running`, `output_full`, `model`, `data`, `progress`, `energy`; alert once when it gets blocked (output full, no power, no matrix, faulty tier) and when it runs again. Productive Bees advanced hive (`honey`, `bees`, `output`, `output_full`) and centrifuge (`running`, `output`, `output_full`, `fluid`, `energy` on powered ones); alert once when an output becomes full.
- MFFS projector: `field` toggle (confirm, plus `pin` while `mffsRequirePin`), `status`, `fortron` (%), `fortron_text` (mB), `blocks` (field blocks counted on the calculated shape, max 8192), `redstone_held` (a redstone signal keeps the projector on); crit alert when a projected field goes down.
- ME Requester: per configured request (max 10): `req<i>_on` (toggle), `req<i>_amount` (number ≥ 1), `req<i>_status`; which item is requested is chosen in-game – the card rebuilds itself when that changes (card revision). Locked requests (crafting/exporting) answer `busy`. ExtendedAE has nothing requester-like, so there is no ExtendedAE card.
- Actions are validated like any widget action (type, range, options, `pin`, read-only devices refused by the web layer) and logged (`[cccloud] <device> set ...`). Action errors are existing codes (`not_interactive`, `invalid_value`, `unavailable`, `busy`).

**Mainframe cards (B):** sections owned by the mainframe, not by an uplink: section id = name-based UUID of `cccloud:<kind>:<mainframeId>` (stable), snapshot `"mode":"direct","template":"server|claims|backups","source":"mainframe","restricted":true`. **`restricted`** (new snapshot flag): the hub sends the section, its value deltas and alerts only to devices with canControl; read-only devices never see it. They cost no mainframe power and are refreshed once per second by a server tick listener.
- **Server** (`server`): `tps`, `mspt` (gauge, warn 40, crit 50 ms), `mspt_history` (60 min), `players`, `uptime`, `memory` (MB), `dimensions` (average tick time per dimension from NeoForge's per-level tick times). With spark: `spark_tps` (10 s / 1 m / 5 m / 15 m), `spark_mspt` (last minute mean/max), `spark_cpu` (process CPU %). spark's API has no per-dimension data or lag sources.
- **Team claims** (`claims`, FTB Chunks): `team`, `claimed` ("n / max"), `forced`, `dimensions`. Overlay data for the network map in the snapshot: `"claims":{"team","color":"#rrggbb","chunks":[{"dim","x","z","forced"?}],"truncated"}` (chunk coordinates, max 2048 chunks, re-read every 10 s, re-sent only when they change). Restricted because it carries coordinates.
- **Backups** (`backups`, Simple Backups): `last` (start time of the newest backup chain entry + age), `size`, `running`, `paused`, `backup_now` (button, confirm; runs `/simplebackups backup start` as the server – Simple Backups has no API; `busy` while a backup runs); info alert when a backup finished.
- **FTB Teams trust:** `compat/team.TeamSource` is a neutral view of a team mod (team of a player, rank OWNER/OFFICER/MEMBER/ALLY/NONE, members, change events); `compat/ftbteams` is a thin adapter over FTB Teams' API and makes no permission decisions. The v0.5 rule lives in `compat.TeamTrust`: with `ftbTeamsTrust`, players with at least member rank in the owner's team count as trusted. It is consulted live by `AuthStore.Acl.allows` and `CloudMainframeBlockEntity.canManage`, so joining/leaving applies on the next action, GUI check or the periodic session re-check. v0.6 replaces the rule with role mapping; the adapter stays.

### C. Rules (no-code automation, server-side)
- Per mainframe, a list of rules: **When** (trigger) → **If** (optional conditions) → **Then** (actions).
  - Triggers: a widget value crosses a threshold (any numeric widget incl. template cards), item count in a bridge below/above X, mainframe power below X %, a device goes offline, a time interval (every N minutes, min 1), an in-game time of day.
  - Conditions: same comparisons, AND only in v0.5.
  - Actions: set a redstone output on an auto-card computer side, press a button / set a toggle / set a slider on a widget (same validation as a human action, user = "rule:<name>"), request a craft on a bridge (with max amount), send an alert (level) incl. push, enable/disable the dashboard.
  - Safety: each rule has a cooldown (min 5 s), max 32 rules per mainframe, max 10 actions per rule run, loop protection (a rule cannot trigger itself or more than 20 rule runs per second per mainframe), and an audit log entry per run. Rules run on the server thread in a cheap evaluation step (only re-evaluate on relevant changes or interval ticks).
  - Rules are edited in the dashboard (canControl), stored in SavedData, validated server-side; Lua can list/enable/disable rules via `cloud_mainframe` (`getRules()`, `setRuleEnabled(id, bool)`).

### D. Push notifications
- Targets per mainframe (canControl to edit, secrets stored server-side, never sent back to the browser in full): **Discord webhook**, **ntfy** (topic URL, optional token), generic **webhook** (JSON POST).
- What is sent: alerts (by min level), rule actions with `notify`, device approvals pending, mainframe offline/no power.
- Outgoing HTTP from the server: async, timeouts 5 s, retries with backoff, max 30 messages/minute per mainframe, URL allowlist by scheme (https only, except ntfy on LAN), never follow redirects to private IPs (SSRF protection), config `pushEnabled` and `pushAllowedHosts`.
- A "Send test notification" button.

### E. Browser terminal + Lua editor (for scripters)
- **Terminal**: live view of a CC computer's terminal (attached to an uplink) with keyboard input from the browser (canControl only), like standing in front of it. Server streams terminal changes (diffed, max 10 fps, colour palette), input events are rate-limited and go through CC's event queue (`char`, `key`, `key_up`, `paste`, `mouse_click` optional). Uses CC's server-side terminal state (check the CC API for reading a ServerComputer's terminal).
- **Lua editor**: file tree of the computer's filesystem (only that computer's own drive, no disk drives, no rom writes), open/edit/save files with syntax highlighting (lightweight editor bundled, e.g. CodeMirror 6 core with the Lua mode, lazy-loaded chunk), "Save & run" (runs `shell.run(file)` via a queued event the cloud.ui/startup helper listens to, or via the terminal by typing the command). Limits: file size ≤ 256 KB, path validation, quota respected, audit log.
- Both are behind a per-mainframe setting `allowRemoteTerminal` (default true for owner/trusted, PIN-protected if a PIN is set).

### F. HTTPS via Tailscale + statistics
- Docs page: "Install as an app with HTTPS via Tailscale" (`tailscale serve --https=443 http://localhost:8787`, or `tailscale cert`) – the mod only needs `publicUrl` set and trusted proxy 127.0.0.1. Detect `Tailscale-User-Login` headers? No – keep auth as is.
- **Long-term stats**: any numeric widget can opt in (`opts.history="1h"|"24h"|"7d"`); the mod downsamples (1 min / 5 min / 30 min buckets) and stores compactly per mainframe (cap 2 MB). Charts in the dashboard with range switcher; CSV export (canControl).

## v0.6 – Accounts, permissions, roles, teams, share links (approved 2026-10-23)
"Device" always means any browser – phone, tablet or PC. The UI and docs must never assume a phone.

### Player accounts (one login per player)
- `/cccloud link` (or the "Link this browser" button in the mainframe screen) shows a one-time code (10 min). Entering it in the browser + in-game approval (same flow as today) links the browser to the **player account**, not to one mainframe. The browser then sees every mainframe where that player has a role (own, trusted, team, admin permission), switching between them in the header.
- Existing per-mainframe device tokens keep working (migration: they become account tokens limited to that one mainframe).
- `/cccloud devices` and the web "Devices" page list all browsers of a player with name, type (phone/desktop from the user agent, informational only), last IP, last seen; revoke from either side.

### Roles per mainframe
- `viewer` – sees everything except coordinates and secrets, cannot act.
- `operator` – viewer + buttons, toggles, sliders, crafting, rules enable/disable.
- `admin` – operator + devices, roles, PIN, rules editing, push targets, terminal/editor, layout editing, share links.
- `owner` – the placer; admin + transfer ownership + delete.
- Assign per player (`/cccloud role <player> <role>`, mainframe screen, web "Access" page) and per group (see teams). The highest role from all sources wins. Every check in the code goes through one `AccessResolver` (player, mainframe) → role; canControl is derived from it.

### Server permissions (works with the permission mods hosts use)
- Use NeoForge's **PermissionAPI** (`PermissionNode`s registered in `PermissionGatherEvent.Nodes`), so **LuckPerms** (NeoForge) and **FTB Ranks** (ships with ATM11) control them without extra integration; without a permission mod the default is vanilla op level.
- Nodes (default → op level 2 unless noted):
  - `cccloud.use` (default true) – may place/use the blocks and link browsers
  - `cccloud.admin.view` – view any mainframe's dashboard as viewer
  - `cccloud.admin.operate` – operate any mainframe
  - `cccloud.admin.manage` – manage devices/roles of any mainframe (moderation)
  - `cccloud.command.icons`, `cccloud.command.reload`, `cccloud.bypass.limits` (op 4)
  - `cccloud.limit.mainframes` (integer node, default 3) – max mainframes per player
- Admin access is visible: the owner sees "Viewed by admin <name>" entries in the audit log.

### Teams / clans
- Providers (auto-detected, config to choose/disable): **FTB Teams** (ATM11), **Open Parties and Claims** parties, **vanilla scoreboard teams** (fallback).
- A mainframe can be assigned to a team (owner/admin). Team rank → role mapping per mainframe, defaults: FTB Teams owner/officer → admin, member → operator, ally → viewer; OPAC party leader → admin, member → operator, ally → viewer; scoreboard team member → operator.
- Membership changes apply live (re-check on connect and every 5 s, like bans).

### Share links (read-only, opt-in)
- Admin creates a link for selected cards/pages: `https://host/s/<id>#<secret>`. The secret lives in the URL fragment, so it is never sent in requests, logs or Referer; the client sends it in the first WS message (`{"t":"auth_share","id","secret"}`). 128-bit random secret, stored hashed.
- Options: expiry (1 h / 24 h / 7 days / never), max concurrent viewers, cards/pages to include, hide exact numbers (show only gauges) – default: no coordinates, no item search, no network map, no history export.
- Share viewers are a separate role `share` with a hard deny on every action, query and file/terminal API (enforced in the central auth layer, plus a test that walks every message type with a share session). Rate limits per share link; revocable instantly (open sessions are closed).
- `Referrer-Policy: no-referrer`, `X-Robots-Tag: noindex` on /s/ routes.

### Security baseline (re-stated for everything above and v0.5)
- No server-side code execution from web input, ever: the browser only sends data that is validated against schemas; there is no eval, no scripting endpoint, no command passthrough. The Lua terminal/editor (v0.5 E) only reach the CC computer's own sandbox (CC Lua runs in its own VM; CC's HTTP rules still block private IPs by default – do not loosen them) and are admin-only.
- File API: canonicalize paths inside the computer's root, reject `..`, absolute paths, symlinks, rom/ and disk mounts; size and quota limits.
- All JSON parsed with size limits and strict schemas; unknown fields rejected; numbers bounded.
- Dependencies: keep the web bundle dependency list short and audited (`pnpm audit` in CI); the mod uses only MC/NeoForge/Netty/Gson.
- Add a `docs/SECURITY.md` threat model (assets, attackers: other players, internet scanners, share-link holders, malicious mod clients; mitigations) and run a dedicated security review before each release.

### v0.6 web protocol (accounts, roles, access, devices, shares)
Implemented by the dev mock (`cloud/dev/mock-server.ts`) and the dashboard; all bodies ≤ 4 KB, strict schemas, unknown fields rejected.

**Account linking** – same two-step flow as pairing, on the same endpoints:
- `POST /api/pair {"code","name"}` accepts both kinds of codes: a mainframe pairing code (as before) or an **account code** from `/cccloud link` / "Link this browser". → `202 {"status":"pending","pairingId"}`.
- `GET /api/pair/<pairingId>` → when approved:
  - mainframe code: `{"status":"approved","token","mainframeId","name","canControl","role"}` (as before + `role`)
  - account code: `{"status":"approved","token","account":{"player","uuid"},"mainframes":[{"id","name","role"}]}`
- `GET /api/mainframes` (Bearer token) → `{"mainframes":[{"id","name","online","enabled","role","canControl"}],"account":{"player","uuid"}|null}`. Account tokens list every mainframe where the player has a role; legacy device tokens list their one mainframe.
- WebSocket auth gains an optional mainframe: `{"t":"auth","token","mainframeId?"}`. Account tokens **must** send `mainframeId` (else close 4401 `mainframe_required`); a mainframe the player has no role on → close 4403 `no_access`.
- The `snapshot` gains `"role":"viewer|operator|admin|owner|share"` and `"account":{"player"}|null`. `canControl` stays (= role ≥ operator). Role changes are pushed as `{"t":"role","role","canControl"}`.

**Role checks in the protocol** (central auth layer): actions, queries' crafting, toggles etc. need `operator` (`action_result.error = "read_only"` for viewers); `layout_set`, `net_unlink`, the access/shares/devices APIs need `admin` (`error = "not_allowed"`, HTTP 403 `{"error":"not_allowed"}`). Ownership transfer and delete need `owner`.

**Devices** (all browsers of a player; Bearer = account token, or a device token for its own entry only):
- `GET /api/devices` → `{"devices":[{"id","name","type":"desktop|phone|tablet|other","lastIp","lastSeen","created","current","mainframes":[ids]|null}]}`. `type` is derived from the User-Agent at pairing time and is informational only.
- `DELETE /api/devices/<id>` → `{"ok":true}`; the device's sockets close with 4401 `revoked`.

**Access** (per mainframe, admin+): `GET /api/mainframes/<id>/access` →
```json
{"you":{"player","role"},
 "players":[{"uuid","player","role","source":"owner|direct|team|server_admin","teamRank?"}],
 "team":{"provider":"ftbteams|opac|scoreboard","id","name","mapping":{"<rank>":"viewer|operator|admin|none"}}|null,
 "teams":[{"provider","id","name","ranks":["owner","officer","member","ally"]}],
 "providers":["ftbteams","opac","scoreboard"]}
```
`POST /api/mainframes/<id>/access` with one op:
`{"op":"set_role","player","role"}` (role `viewer|operator|admin`; owner cannot be changed here) · `{"op":"remove","player"}` · `{"op":"set_team","provider","teamId"}` (`teamId:null` unassigns) · `{"op":"set_mapping","rank","role"}`. → the new access document, or 400/403 `{"error"}` (`unknown_player`, `not_allowed`, `invalid`). Role changes apply live (`role` push to that player's open sessions).

**Share links** (admin+):
- `GET /api/mainframes/<id>/shares` → `{"shares":[{"id","label","createdBy","created","expires"|null,"maxViewers","viewers","pages":[pageIds],"cards":[cardKeys],"hideNumbers"}]}` (never the secret).
- `POST /api/mainframes/<id>/shares {"label","pages":[…],"cards":[…],"expiresIn":3600|86400|604800|null,"maxViewers":1..50,"hideNumbers":bool}` → `{"id","secret","url":"/s/<id>#<secret>"}` – the only time the secret is returned. `pages`/`cards` use the layout ids (card keys as in the Layout schema); at least one of them.
- `DELETE /api/mainframes/<id>/shares/<shareId>` → `{"ok":true}`; open share sessions close with 4401 `share_revoked`.
- Viewer: page `/s/<id>` (SPA route; headers `Referrer-Policy: no-referrer`, `X-Robots-Tag: noindex`). The browser takes the secret from `location.hash`, removes it with `history.replaceState`, and authenticates with `{"t":"auth_share","id","secret"}` (WebSocket, first message). Errors: close 4401 with `share_invalid | share_expired | share_revoked`, 4429 `share_full` (maxViewers reached).
- Share snapshot: `{"t":"snapshot","role":"share","share":{"id","label","sharedBy","expires","hideNumbers"},"mainframe":{"id","name","online","enabled"},"uplinks":[only the shared cards' uplinks and widgets],"layout":{…}}` – no `network`, no positions, no `item_search` widgets, no history export. With `hideNumbers` the server drops `label`/`text`/`number` widgets and the dashboard hides gauge digits.
- A share session may only send `pong`. Every other message gets a hard deny: requests with a `reqId` are answered with their result type and `"error":"forbidden"` (`action_result`, `query_result`, `layout_result`, `net_result`), everything else with `{"t":"error","error":"forbidden"}`.

### v0.4 network map – how the dashboard reads it
The dashboard follows the format in "v0.4 – Implementation notes" (mod side) exactly; the dev mock (`cloud/dev/mock-server.ts`, `network-sim.ts`) emits the same shape.
- Tree and hops come from each node's `parent` / `hops`. `routes` is only a fallback (relays' paths include themselves, devices' paths are the nodes before them – the client accepts both).
- Device node ids are uplink ids; the dashboard uses them to find the card ("Open card"). `type:"device"` with `status:"unloaded"` is a linked device in an unloaded chunk.
- `flags:["legacy_out_of_range"]` is shown as its own state ("Out of range (legacy)") although the device is connected; `weak:true` on a link (or signal < 0.15) colours it yellow.
- `pos` and `dist` only arrive for devices with canControl; without them the Map view explains that positions are hidden and the topology still works.
- Statuses shown: `online | offline | connecting | no_power | no_route | unloaded | redstone | cloud_disabled` (+ the legacy flag).
- Snapshots with large networks are fine: the client parses any size (browsers reassemble fragmented frames) and is tested with 500 nodes; panning re-uses the rendered SVG layers.
- Unlink from the map (admin): client → `{"t":"net_unlink","reqId","nodeId"}`, server → `{"t":"net_result","reqId","ok","error?"}` (`read_only | not_allowed | unknown_node`), then a `network` delta. (Optional; the button is hidden when the mod does not support it – the dashboard shows it to admins only.)
- Power breakdown in the header popover: the mainframe's `power` object may carry `breakdown: [{key, count?, fe}]` (keys `base | uplinks | advanced_uplinks | bridges | clients | hops`, matching `powerBreakdown` / `getPowerBreakdown()`); relays' own power comes from their `power` in the graph.

### v0.6 – Implementation notes (mod side)
- **One decision point:** `access/AccessResolver` (pure Java, unit tested) resolves (player, mainframe) → role from the owner, direct grants, team ranks and the PermissionAPI nodes. The highest role wins. Banned players and players without `cccloud.use` get `none`.
  - In the game, use `access/Access` (`role`, `canManage` = admin, `auditAdmin`).
  - The web layer turns roles into allow/deny with `web/AccessGate`:
    - viewer: query;
    - operator: action;
    - admin: layout_set, net_unlink, the access/shares/devices APIs.
  - The gate runs once on the Netty thread (cached role) and again on the server thread (live role).
  - Viewers get `read_only` for actions, layout and unlink. Operators get `not_allowed` for layout and unlink.
- **Data:** `access/AccessStore` lives in the same SavedData as the auth store (`data/cccloud/web.dat`, keys `access`, `shares`, `audit`). It holds:
  - owner and direct grants (never `owner`, never more than 256 per mainframe);
  - the team binding with its rank → role mapping;
  - share links (SHA-256 of the secret);
  - an audit log (50 entries per mainframe, shown in the in-game Security tab).
- **Migration from v0.3:**
  - The old `acls` (owner + trusted) and the block entity's `trusted` list become access entries; trusted players become **admins**. This happens once per mainframe, and later edits are kept.
  - Old device tokens keep their mainframe. They are account tokens limited to that mainframe, with role = the approving player's role (capped at viewer for read-only devices).
  - `acls` is read but never written again. The GameTest `access_migration_v03` decodes v0.3 data.
- **Accounts:**
  - `/cccloud link` and the "Link this browser" button in the mainframe screen (every player with `cccloud.use`) show an account code: same alphabet, 10 minutes, single use, memory only.
  - `POST /api/pair` accepts both kinds of code. Only the code's own player can approve an account code (chat prompt `cccloud.approval.account_request`).
  - `Device.mainframeId == null` marks an account token. `type` comes from the User-Agent. `lastIp` is updated on use.
- **Commands:**
  - `/cccloud role <player> <viewer|operator|admin|none>` acts on the mainframe you look at. Escalation rules as for the API.
  - `/cccloud devices` lists all of the player's browsers, with a clickable revoke (`/cccloud devices revoke <id>`).
  - The old per-mainframe list is now `/cccloud devices here`.
  - `/cccloud trust|untrust` remain and mean admin / remove.
  - `/cccloud icons` uses `cccloud.command.icons`. There is no reload command yet; `cccloud.command.reload` is registered for it.
- **Escalation rules (Access API and `/cccloud role`):**
  - Admins may only change players whose current role is below their own, and may grant at most their own role.
  - The owner and server moderators (`cccloud.admin.manage`) may change everyone except the owner.
  - `set_team` needs the caller to be in that team (rankOf ≠ null); moderators may assign any team.
  - Players are looked up by name only among online players and names the mod already knows (grants, owners, devices). The web API never triggers a blocking Mojang lookup; unknown names → `unknown_player` (use `/cccloud role`, which resolves profiles in-game).
- **Limits and use:**
  - `cccloud.limit.mainframes` is checked in `CloudMainframeBlock#getStateForPlacement`, counting mainframes owned in the AccessStore; `cccloud.bypass.limits` skips the check.
  - `cccloud.use` blocks placing and using all `cccloud:` blocks (`access/UseGuard`).
- **Live checks:**
  - `CloudWebServer.serverTick` (every 5 s) re-checks every session. It sends `{"t":"role","role","canControl"}`, followed by the network view for the new role (and a new snapshot if restricted sections appear or disappear), or closes with 4403 `no_access`.
  - Role changes from the API, the screen or commands, and FTB Teams change events, run the check at once.
- **Server admins** (node `cccloud.admin.*`, not a role on the mainframe): dashboard logins, Access-page opens and screen opens are written to the audit log as "… by admin <name>", once per 10 minutes.
- **Share links:**
  - `auth_share` is checked off the server thread; the AccessStore is thread-safe.
  - Limits: 5 wrong secrets per IP per minute (then 4429 `rate_limited`), 30 logins per link per minute, at most 16 links per mainframe.
  - The viewer limit is checked atomically in `WebHub.tryAddSession`.
  - Filtering is done by `web/ShareFilter`: card keys and page resolution as in the dashboard's `resolveLayout`. Shared uplinks only carry `uplinkId, title, online, pages, groups, widgets`.
  - `X-Robots-Tag: noindex, nofollow` and `Referrer-Policy: no-referrer` are set on every response, `/s/` included.
- **Restricted sections (v0.5 mainframe cards):** shown from the **operator** role up (`WebHub.seesRestricted`), never to viewers or share links.
- **HTTP API:**
  - `web/AccessApi` (pure Java with injected hooks, unit tested) runs on the server thread and allows 120 requests per minute per IP.
  - `GET /api/pair/<id>` adds `role` (mainframe codes) or `account` + `mainframes` (account codes).
  - `GET /api/mainframes` lists the device's mainframe (mainframe tokens) or every mainframe with a role (account tokens).
  - The access document's `team` carries `"implicit":true` for the owner's-team fallback described below.
- **Not done yet:** ownership transfer and deletion from the web (owner-only operations).

### TeamProvider (for integrations)
Team mods plug into the resolver through `ai.ezfox.cccloud.access.TeamProvider`. Register it with `TeamProviders.register(provider)` during mod construction, and only when the team mod is loaded. Server config `teams.disabledTeamProviders` can switch providers off.

```java
public interface TeamProvider {
    record Team(String id, String name) {}            // id stable across restarts (e.g. team UUID)
    record Member(UUID uuid, String name, String rank) {}
    String id();                                        // "ftbteams" | "opac" | "scoreboard" | …
    default boolean available() { return true; }       // false while the mod's data is not loaded
    List<String> ranks();                               // highest first, e.g. owner, officer, member, ally
    Map<String, Role> defaultMapping();                 // rank → role for a newly assigned team (Role.NONE = no access)
    @Nullable String rankOf(String teamId, UUID player); // null = not in the team
    @Nullable Team team(String teamId);
    List<Team> teamsOf(UUID player);                    // offered when assigning a team
    default List<Member> members(String teamId) { return List.of(); } // display only
}
```
- All methods run on the server thread, on every rights check. They must be cheap (in-memory data, no I/O) and must not throw (return null or empty instead).
- Built-in providers:
  - `access/ScoreboardTeams`: vanilla `/team`, rank `member` → operator. Members are listed only while online.
  - `compat/team/TeamSourceProvider`: wraps the v0.5 `TeamSource` adapters. FTB Teams: owner/officer → admin, member → operator, ally → viewer.
- **Owner's-team fallback (v0.5 `ftbTeamsTrust`):** a mainframe without an explicit team uses its owner's FTB team with the default mapping (`compat/TeamTrust.ownerTeam`). An explicit `set_team` replaces it, and `ftbTeamsTrust=false` turns it off.
- After membership changes, adapters call `Teams.fireChanged(player)`. That triggers the immediate session re-check; otherwise changes apply within 5 s.

## v0.7 – Upgrades, wired links, wireless redstone, tablet, display, 3D map, quests (approved 2026-10-23)

### Mainframe upgrades (ATM progression)
The mainframe gets 4 upgrade slots (in its screen; hoppers cannot insert). Base limits without upgrades (config):
| Limit | Base |
|---|---|
| connected devices (uplinks + bridges + relays) | 16 |
| concurrent browsers | 4 |
| rules | 8 |
| history retention | 1 h |
| node range (mainframe as node) | 96 |
Upgrade cards (stack effects by tier, one card per type counts – the highest tier of each type wins):
| Card | Allthemodium | Vibranium | Unobtainium |
|---|---|---|---|
| Capacity (devices) | 48 | 128 | 512 |
| Bandwidth (browsers, widget update rate) | 8 browsers | 16 | 64 |
| Range (node range ×) | ×1.5 | ×2 | ×3 |
| Efficiency (mainframe FE ×) | ×0.8 | ×0.65 | ×0.5 |
| Memory (rules / history) | 24 / 24 h | 64 / 7 d | 128 / 30 d |
- **Quantum Core** (ATM Star tier, one per mainframe): unlimited devices and browsers, the mainframe acts as an Ender node (cross-dimension), FE ×0.25.
- Recipes use the pack's metals by tag (`#c:ingots/allthemodium`, `#c:ingots/vibranium`, `#c:ingots/unobtainium`, `allthetweaks:atm_star` – verify ids in the ATM11 jars); without AllTheModium present, fallback recipes with diamond/netherite/nether star (conditional recipes).
- Exceeding a limit: new links are refused with a clear message; existing extra devices (after removing a card) go `over_capacity` and stop (highest-hop/most-recent first) until capacity returns.

### Wired links (like AE2 cables)
- Devices can join the network **by cable** instead of radio: use CC: Tweaked's own wired network (networking cable + wired modems). The mainframe and relays expose a CC `WiredElement`; any uplink/bridge connected to the same CC wired network is a wired member.
- Wired links: no range limit, 100 % signal, no per-hop FE, shown as solid lines on the map ("cable"); radio links dashed.
- A device can have both; wired is preferred.

### Wireless redstone over the network
- New mode on uplinks (set in the uplink screen): **Redstone transmitter** / **receiver** on a channel name (per mainframe network). Receivers output the max signal of all transmitters on that channel (analog 0–15). Works over relays, wired links and across dimensions via ender relays. Latency: next tick after the change; max 64 channels per network, change rate limited (redstone clocks faster than 2 ticks are smoothed).
- Channels are shown in the dashboard (indicator per channel; operators can override a channel for N seconds or until reset – optional).

### Cloud Tablet (item)
- Handheld item, opens the dashboard **in-game** (a native screen that renders the same cards/widgets with MC widgets – not a browser). Works within network range of any node (or anywhere in Ender range with an advanced tablet). Uses the player's role on that mainframe. Keybind to open (default unbound) when the tablet is in the inventory/curios slot.
- Normal and advanced tier; recipe from a CC pocket computer + cloud core (+ gold/ender pearl for advanced).

### Cloud Display (block)
- A multiblock-capable display (like CC monitors: forms rectangles up to 8×6) that renders one dashboard card or page in-world (read-only, chosen in its screen; operators can make buttons clickable in-world with right-click). Rendered with a BER using cached textures, updated at most 4×/s, culled beyond 48 blocks.

### 3D map in the web dashboard
- A third view on the Network page: **3D**. three.js (lazy chunk, pinned version, bundled – no CDN at runtime). Devices are rendered with their real block models (reuse the model JSONs + textures from `/assets/...` served by the mod or bundled), placed at real coordinates; links as lines/tubes; ranges as translucent spheres (toggle).
- Optional coarse terrain: the server samples a heightmap with map colors (`MapColor`) around nodes (radius 96, 2-block grid, only loaded chunks, cached, ≤ 200 KB per request, canControl only, rate-limited) so the base is recognizable.
- Interaction: orbit/zoom/pan (touch + mouse), click a device → its detail panel with sub-menus (card, widgets, power, routes, actions per role); click a relay → highlight everything routed through it; dimension switcher; "fly to" from search.

### v0.7 web protocol (wired links, terrain, 3D view)
Implemented by the dev mock (`cloud/dev/mock-server.ts`, `network-sim.ts`) and the dashboard.

**Wired links** in the `network` payload: `{"a","b","kind":"wired","signal":1}` (no `dist`, never `weak`). `a` is the mainframe or relay that owns the CC wired network, `b` the cabled member; the member's `parent` points at `a` when the cable route is used (wired is preferred over radio). Wired hops cost no FE, so they are not in the power breakdown's `hops`. Radio links for a pair that is cabled are not sent. The dashboard draws cables solid, radio dashed (coloured by signal), ender links dotted – in the topology, the map and the 3D view.

**Coarse terrain** (optional, for the 3D view):
`GET /api/mainframes/<id>/terrain?dim=<dimension id>` (Bearer token, role ≥ operator, i.e. canControl; else 403 `{"error":"not_allowed"}`) →
```json
{"dim":"minecraft:overworld","step":2,
 "patches":[{"x0":-84,"z0":-126,"w":97,"h":97,
             "heights":"<base64: int16 LE per cell, row-major (z rows, x columns); -32768 = unknown/unloaded>",
             "colors":"<base64: 3 bytes RGB per cell, the block's MapColor>"}],
 "truncated":false}
```
- Cells are `step` blocks apart; cell `(i, j)` is at `x = x0 + i*step`, `z = z0 + j*step`; `heights` is the Y of the top non-air block (`Heightmap.Types.WORLD_SURFACE`), water's colour is the water MapColor.
- The server builds one patch per cluster of nodes in that dimension (radius 96 around each mainframe/relay/device with a position; overlapping squares are merged into their bounding box), samples only **loaded chunks** (never loads or generates chunks), caches per chunk for 60 s and stops at **200 KB** of JSON – nodes nearest the mainframe first, then `"truncated":true`. When the budget is tight it may raise `step` to 4.
- Rate limit: 1 request per 5 s per token → 429 `{"error":"rate_limited","retryAfter":s}`. Unknown dimension → `{"dim","step":2,"patches":[]}`. Servers without the endpoint (404) simply show no terrain; the dashboard falls back to a flat grid.
- `Cache-Control: private, max-age=60`.

**3D view (client only, no new messages):** three.js `0.186.1` bundled in a lazy chunk (`Network3D`), loaded only when the 3D tab opens. Block models are converted at build time from `src/main/resources/assets/cccloud/{blockstates,models,textures}` by `cloud/dev/build-models.ts` (same rules as `preview/build_viewer.py`) into `web/src/network/models.gen.json` – geometry per node type, status variants as texture swaps. Without WebGL the dashboard shows the 2D map with a notice.

### FTB Quests chapters
- Two chapters for ATM11: **CC: Tweaked** (computers, turtles, monitors, modems, peripherals, first programs) and **CC: Cloud** (mainframe, power, uplinks, link card, pairing a browser, bridges, relays, upgrades, tablet, display, rules). Format: the pack's `config/ftbquests/quests/chapters/*.json5` + lang files; place them in a chapter group that fits ATM11's structure, rewards in ATM style (modest). Ship them in the repo under `integration/ftbquests/` with an install script; contribution to ATM via their process (see docs/ATM_SUBMISSION.md).

### Map tiles for the 2D map (requested 2026-10-23, moved up before v0.5 C–F)
The 2D network map gets a real top-down map of the world as background, like a Minecraft map / JourneyMap / Dynmap "flat":
- Server renders tiles of 128×128 blocks at 1 px per block (PNG, map colors from `BlockState.getMapColor` with vanilla-map-style height shading; water depth tint). Only already-generated, **loaded or saved** chunks are read – never generate chunks, never force-load; read saved chunks off-thread via the region storage if feasible, otherwise only loaded chunks.
- Two layers: **Surface** (top block, heightmap `WORLD_SURFACE`) and **Slice** (a horizontal cut at a chosen Y, e.g. around an underground base like the user's mainframe at y −60: first non-air block scanning down from Y over 16 blocks, darker shading = deeper; like JourneyMap cave mode). The slice Y defaults to the median Y of the visible nodes.
- Endpoint `GET /api/map/<dim>/<layer>/<y?>/<tileX>/<tileZ>.png` – operator role (positions are sensitive), rate-limited (60 tiles/min per session), `Cache-Control: private, max-age=60` + ETag, 204 for tiles with no data. Only tiles within 512 blocks of a node of that mainframe's network may be requested (no free world scanning).
- Rendering off the server thread on a small executor, results cached on disk per world (`cccloud/maptiles/`), invalidated when chunks in the tile change (chunk save/unload events, debounced 30 s). Budget: ≤ 2 ms main-thread per tick for chunk snapshotting, else defer.
- Web: tiles as the map background with zoom levels (downsampled client-side for far zoom), a layer switch (Surface / Slice + Y slider), node labels with collision avoidance (labels must not overlap like "Computer #0" / "RS Network" did), and the 3D view reuses the same height data.

### "Crazy 3D": voxel view of the base (requested 2026-10-23)
On top of the 3D network view: render the actual blocks around the network as voxels.
- Endpoint `GET /api/voxels/<dim>/<cx>/<cy>/<cz>` returns one 16³ section around network nodes: palette (block id → map color + flags: transparent/fluid/emissive) + run-length encoded indices. Only **exposed** blocks (at least one face touching air/transparent) are sent, so the view shows the base and caves but cannot be used as an ore X-ray.
- Limits: operator role; only sections within 64 blocks (config `voxelRadius`, max 128) of a node of that mainframe's network; loaded or saved chunks only, never generate/force-load; 40 sections/s per session; server-side cache invalidated on block changes (debounced); config `voxelView` (default true).
- Stage 1 (now): colored voxels with map colors, greedy meshing in a Web Worker, ambient occlusion, emissive blocks glow, water translucent, day/night toggle; our devices drawn with their real models on top; clickable as in the 3D view; clipping plane / Y-slider to cut through floors of a base.
- Stage 2 (later): real block textures – the game client uploads a block texture atlas the same way it uploads item icons (validated PNG, size caps), and the web renders textured voxels.
- Performance budget: ≤ 150k triangles on phones, LOD by distance, frustum culling, 60 fps target; falls back to the plain 3D/2D map without WebGL2.

### In-game network visualization: Cloud Analyzer (requested 2026-10-23)
Players must be able to plan relays without opening the web dashboard.
- New item **`cccloud:cloud_analyzer`** (handheld tool in CC style; recipe: CC pocket computer + cloud core + redstone + glass pane). Also: holding a **Link Card** or any **cloud device item** (relay/uplink/bridge/mainframe) shows the same overlay.
- While held (client-side rendering; server sends the geometry of networks the player has at least viewer role on, within 128 blocks, on change + every 2 s):
  - **Range bubbles** of nodes (mainframe/relays) as translucent CC-cyan wireframe spheres / ground rings (toggle with sneak + scroll or a keybind: rings ↔ spheres ↔ off), device ranges in a dimmer tone.
  - **Links** as lines between devices through walls (depth-test off, thin), colored by signal (green ok / yellow weak / red broken), dashed for ender links, solid for cable.
  - **Placement preview**: when holding a relay/uplink item and looking at a block, a ghost of the block plus its future range bubble, and a label "would connect to North Relay · 68 % · 2 hops" or "out of range – nearest node: Mainframe 143 blocks (range 96)".
  - **HUD line** (top center, small): "Signal here: 72 % via North Relay" / "No coverage", and an arrow toward the nearest node when out of coverage.
  - Right-click a device with the analyzer: chat/hover summary (status, signal, route, power, hops); sneak-right-click: highlight its whole route to the mainframe for 10 s.
- Jade tooltip on every cloud device additionally shows: signal %, route ("→ North Relay → Mainframe"), hops, status reason (no_route/no_power/out of range).
- Performance: only render within 128 blocks, max 256 lines/spheres, sphere LOD by distance, no allocation per frame; config `analyzerOverlay` (client).
- Security: positions only for networks where the player has a role; the server never sends other players' networks.

### Cables (decided 2026-10-23): A = CC cables first, B = our own Fiber Cable as an extra
**A – CC: Tweaked cables (wired links, see v0.7 "Wired links")**
- Mainframe, relays, uplinks and bridges join CC's wired network as `WiredElement`s (via CC's wired API: `WiredNode`, `WiredElement`, capability on the block faces), so CC Networking Cable + Wired Modems connect them. Devices on the same CC wired network as a node are wired members of that node's network.
- Wired: unlimited length, 100 % signal, no per-hop FE, preferred over radio. The network graph treats a CC wired network as one "bus" node set; recompute on CC network change events only.
- Side effect (documented): CC computers on the same cable also see our blocks as peripherals (`cloud_mainframe`, bridges) – intended.

**B – Fiber Cable (`cccloud:fiber_cable`, ships with CC: Cloud)**
- Thin cable like CC's (4×4 px core), CC-grey jacket with a glowing cyan core line (emissive), connects to our devices and to itself only (no CC peripheral traffic – a clean, separate network). Junction box block `cccloud:fiber_junction` for splits/wall pass-through.
- Better than CC cable on purpose: wired members via fiber do **not** count against the mainframe's device capacity tiers at half weight (config `fiberCapacityWeight` 0.5) and reduce the device's FE share by 50 %.
- **Ender Fiber Link** (`cccloud:ender_fiber_link`, endgame): a pair of blocks linked with the Link Card that bridges two fiber networks across any distance/dimension (expensive recipe: ender relay core + eyes of ender + unobtainium/netherite fallback).
- Recipes: fiber cable ×8 from glass panes + prismarine crystals + cloud core + string/wool jacket; junction from fiber + stone + redstone.
- Rendering: cable connections like CC cables (multipart blockstate with 6 directions), no BER; the cyan core is an emissive element.

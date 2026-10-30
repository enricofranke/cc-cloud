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
- Shape (`mount` in the blockstate, docs/MODELS.md "Compact uplink"): the 14x14 plate on faces that carry it (sturdy, or flush and covering the plate or its rim); on smaller side faces an 8x6 puck on the visible part of the face (`bottom|low|center|high|top`: solar panel / carpet side → `bottom`, slab side → `low`). Top and bottom faces only take the plate: a new uplink on a top / bottom face where it does not fit (top of a slab or solar panel) is refused with the action bar hint `cccloud.uplink.side_only`; uplinks older versions let sit there stay and keep the plate. Computed by the server from the attached block's outline shape on placement, when that block changes and once after loading (older worlds, `/setblock`, rotated structures, unknown saved values → `full`); the selection shape matches the model. Survival rules are unchanged (sturdy face or a block entity).

### Cloud Probe – `cccloud:cloud_probe`
Uplinks only read the block they stick to. A machine built into a wall, or one whose every face is taken by pipes and cables, needs the **Cloud Probe**: a separate, expensive uplink that reads **exactly one** machine at a distance. Normal uplinks stay adjacent-only.
- **Linking** with the Link Card: sneak + right-click the machine (stores it on the card with the clicked face, the player and the time), then right-click the probe within 10 minutes (an older pick, or a card without a pick time, says "pick it again"). The probe reads the machine exactly as if an uplink were attached to that face: device cards, the bridge Storage template, the Computer card. A Link Card holding a target says so in its tooltip; sneak + right-click in the air clears it with the mainframe.
- **Limits:** same dimension, within `probeRange` blocks (server config, default 8, 1–16, block-centre distance), the machine loaded and a block entity, no CC: Cloud block, one target per probe. Only the player who picked the machine can hand it to a probe. A linked probe needs `network.link` on its mainframe to change the target (an unlinked one: anyone, like binding).
- **Permission checks** (when picking): the sneak + right-click is a real block interaction – NeoForge fires `PlayerInteractEvent.RightClickBlock` before the item's `onItemUseFirst`, so claim mods that protect blocks this way (FTB Chunks, Open Parties and Claims, …) cancel it in claims the player may not use; the server's spawn protection / world border (`Level.mayInteract`) and adventure mode (`Player.mayUseItemAt`) are checked again in the item. **When linking** (security review 2026-10-30) spawn protection / world border are checked again, and a chunk claimed by another team for that player (FTB Chunks, members and allies count as the own team) is refused, also for a card a creative client made without picking. **On every read and action** the probe checks its target again (`readPos()`, used by the machine card, the Computer card, the Storage card and the screens): within the current `probeRange`, loaded, not a CC: Cloud block, the probe still standing where and in the dimension where the target was set (it stores its own position, the dimension and the player; a probe moved with its data by a contraption or block mover forgets the target), and the chunk not claimed by another team for the player who set it. Claim mods other than FTB Chunks are only asked when picking.
- **Target removed:** a machine whose block entity disappears clears the target like an uplink falling off its block (section emptied, `direct` off), and so does moving the probe; an unloaded machine, one outside a reduced `probeRange` or one in a chunk claimed later just reads nothing for now (its card goes, actions answer `unavailable`).
- **Costs:** recipe Advanced Cloud Uplink + Cloud Core + an eye of ender + 6 vibranium ingots (`#c:ingots/vibranium`; without Allthemodium: 4 diamonds + 2 netherite scrap, `cloud_probe_fallback`). In the network it is device type `probe`: radio range of the advanced uplink, `probeCapacityUnits` (default 2) against the device capacity, `mainframePerProbeFE` (default 24 FE/t, breakdown key `probes`). Network map (dashboard 2D/3D, in-game mainframe screen, `cloud_mainframe.getNetwork()`; follow-up 2026-10-30, before it showed as an advanced uplink): node type `probe` with its card title, its own icon and label "Cloud Probe", and with positions `target:{x,y,z}` = the machine it reads (`readPos()`, so a target out of range, unloaded or in a foreign claim is left out; part of the map's publish key, and setting or clearing a target marks the network dirty). The 2D map and the 3D view draw a thin purple line to it (3D: plus the block's outline), the detail panel says "Reads the machine at x y z · N blocks away"; the in-game screen's compact network keeps `target:{x,z}`, the mini map draws the line and the tree marks probes with a purple ring.
- **Look:** the uplink plate in slate blue with a raised gold-rimmed glass lens, no moving part, no antenna; it needs a face that carries the plate (any direction). Screen: "Reads <machine>, N blocks away (remote, through its <face> side)", its position for players who may change it, and **Clear target**. Cloud Analyzer: a thin purple line from each probe to its machine.

### Side handling, hints and multiblocks (zero-code cards)
- **Sides:** the capability-based cards (generic Energy, Tank, Item storage controllers, Generator Galore, Ender IO solar) look up the capability on the touched face first and fall back to the side-less view (`null`), in detection and refresh, so side-configured machines (I/O set to "none" on that face) still show their card. Mod-specific cards read the block entity directly (side irrelevant). When no card is found but another face of the block exposes energy, items or fluids, the uplink screen says "This side exposes nothing – attach to another side (e.g. top) or use a Cloud Probe" (`exposedElsewhere` in the view).
- **Multiblocks** (an uplink on any part shows the controller's card): Powah reactor parts → core (`ReactorPartBlockEntity.core()`), Advanced AE quantum computer (every block is a cluster member), Railcraft steam boiler (firebox / tank parts → master), Railcraft iron/steel tanks, coke ovens and blast furnaces (any wall, gauge, valve or brick → `getMasterBlockEntity()`; follow-up 2026-10-30), Productive Metalworks foundry tank / drain / capacitor → controller (productivelib `IMultiBlockPeripheralBlockEntity.getMultiblockController()`), Productive Farming hatches and other auxiliary parts → farm controller (`AuxiliaryFarmBlockEntity.getControllerPos()`). Not covered: AE2 multiblocks (no uplink card; the ME Cloud Bridge covers the grid), our own Cloud Display (no cards on CC: Cloud blocks).
- **CC wired networks (implemented 2026-10-30, `cable/WiredReach`, `uplink/WiredMachines`, `uplink/WiredMachineSection`, `uplink/WiredCards`):** an uplink on a CC computer also shows the cards of the machines the computer reaches through its wired modems (CC networking cable, modems switched on).
  - **Reach:** for every wired modem next to the computer (a neighbour that gives the computer a `modem` peripheral and is a `WiredElement`), a listener node of ours is connected to the modem's node; CC reports the network's peripherals to it (`WiredElement.networkChanged`) by the names the computer uses. Public CC API only; the listener puts no peripheral on the network (computers see nothing new), follows modems that come and go once a second and leaves the networks when the uplink unloads. An uplink that is itself cabled to the mainframe (cables, part A) does **not** show the machines of that cable network: routing cables would otherwise repeat every machine on every uplink.
  - **Cards:** every peripheral whose `getTarget()` is a loaded block entity in the uplink's level (CC's generic inventory / tank / energy peripherals, most mod peripherals; not CC: Cloud or CC: Tweaked blocks) is looked up with `DeviceCards.detect` like a block an uplink sticks to (side = the face a CC block touches, else the top; the capability cards fall back to the side-less view). One card per block (the first name of a block with two modems wins), names in order, at most `wiredMachineCards` per uplink (integrations config `[cards]`, default 8, 0–32, 0 = off; also off with `deviceCards=false`). Blocks without a card are looked at again after 10 s. No card for a block that another uplink or probe of the same mainframe reads, or that another uplink's computer already shows (one card per machine and mainframe).
  - **Section:** `WiredMachineSection`, stable id = name UUID of `cccloud:wired:<uplink>:<dim>:<x,y,z>`, snapshot like a device card (`"mode":"direct"`, `"template":<card kind>`) plus `"source":"wired"` and `"peripheral":<the computer's name>`; title = the card's title, cards of one uplink with the same title get the computer's name without the namespace ("Energy Cell (energy_cell_2)"). Online = the uplink is online. So permissions are exactly those of the same card on an uplink (`AccessGate.actionPerm` by widget, mode and template), and the layout editor, rules, scenes, history, the public API, the tablet and the display work unchanged. Not a network device (no capacity or FE of its own: the uplink is the device).
  - **Lifecycle:** cards follow the uplink's link (unlinked or linked elsewhere: removed). On chunk unload they stay greyed out like the uplink's card (section cache, also at server stop); the uplink saves their ids (`wired_cards`) and removes the ones whose machine did not come back 3 s after loading. Breaking the uplink removes them.
  - **Lua:** `cloud.itemSearch/watchlist/jobs(..., {bridge=name})` resolve `name` through the uplink's attachment first (sides, or the wired network when the uplink sits behind a modem), then through the computer's wired modems, so `cloud-storage` works with a bridge on the computer's cable network and the uplink directly on the computer (before: "no Cloud Bridge named …").
  - **Tests:** unit `WiredCardsTest` (one candidate per block in name order, taken blocks skipped, titles, name resolution order); GameTest `wired_machine_cards` (computer + full wired modem on a GameTest-only energy block: the card appears as a device card with the same permissions, the name resolves, a chest gets none, an uplink on the machine takes it over, switching the modem off removes it).

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
- Config (server config): `webEnabled` (true), `webBind` ("0.0.0.0"), `webPort` (8787), `publicUrl` ("" → shown in chat as http://<server-ip>:<port>), `allowCloud`, `uplinkRange`, `maxWidgetsPerUplink`. All of them apply while the server runs (see "Live config" at the end).
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
  - deltas: `{"t":"value","uplinkId","id","value"}` (since 0.10 the hub sends all values of a flush as one `{"t":"values","s":{uplinkId:{widgetId:value}}}`, see "Scaling"), `{"t":"uplink",...full uplink...}`, `{"t":"uplink_removed","uplinkId"}`, `{"t":"state","online","enabled"}`, `{"t":"alert","uplinkId","text","level","ts"}`, `{"t":"action_result","reqId","ok","error?"}`
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
- Limits: HTTP body ≤ 4 KB, WebSocket frame ≤ 8 KB, ≤ 24 connections per IP, ≤ 256 total (8/64 until 0.9.0, but one browser tab alone uses 7–8; up to 8 connections over a limit get `503 too_many_connections` with `Retry-After: 1` on their first request and a 2 s read timeout, further ones are closed at once), idle/read timeouts (handshake 10 s, idle 60 s without pong), 5 wrong pair attempts per IP per minute (then 429), 10 actions/s per client.
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
The layout of the dashboard is stored per mainframe (SavedData, max **64 KB** as UTF-8 JSON; 256 KB since 0.10, see "Scaling") and is the same for all devices. Only `canControl` devices may change it. The mod only stores and validates it; the browser merges it with what the Lua scripts declare. Reference implementation: `cloud/dev/protocol.ts` (`layoutDoc`, zod) and `cloud/web/src/lib/layout.ts` (`resolveLayout`, `snapshotLayout`).

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
`home bolt flame drop box fan lock warning gear chart star leaf heart cpu shield pick lever button lamp sensor`

### Defaults when there is no layout
- Pages: `main` ("Overview", icon `home`) followed by the pages the scripts declared (`uplink.pages`, first one wins on duplicate ids). Cards whose page is unknown go to the first page.
- Card span: `group.cols`, else 2 when the card contains an `item_search` or a `chart`, else 1. Card colour `gold` (or the widget's `opts.color`), icon from `opts.icon`.
- Widget order: `opts.order`, else by type (gauge, indicator, chart, progress, label, item_search, watchlist, jobs, inputs, buttons group, buttons), then declaration order. Widget span: `opts.span`, else 1 for gauge/indicator/button, 2 for label/watchlist/jobs, otherwise full width. The card body has 2 columns (1-column cards, phones) or 4 columns (wider cards).

### Layout schema v2 (WYSIWYG editor, 2026-10-23)
The dashboard now writes `"v": 2` documents (it still reads v1 and migrates it on the first save). Cards sit on a **12-column page grid**, widgets on a **12-column grid inside their card**; both are placed with `x`/`y`/`w`/`h` in grid units (a row is 32 px including the gap; rows grow when content is taller). Widgets can be moved into other cards, including **custom cards** the user creates (`"c:<id>"`). Reference: `cloud/dev/protocol.ts` (`layoutDoc`, zod, accepts v1 and v2) and `cloud/web/src/lib/layout.ts` + `grid.ts`.
```json
{
  "v": 2,
  "pages": [{"id": "main", "title": "Overview", "icon": "home"}],
  "cards": {
    "<cardKey>":   {"page": "main", "x": 0, "y": 0, "w": 3, "h": 10, "title": "Reactor", "hidden": false, "color": "red", "icon": "flame", "style": "compact"},
    "c:card1":     {"page": "main", "x": 3, "y": 0, "w": 6, "h": 4, "title": "My overview"}
  },
  "widgets": {
    "<uplinkId>/<widgetId>": {"card": "c:card1", "x": 0, "y": 0, "w": 6, "h": 5, "label": "Core temp", "hidden": false, "color": "red", "icon": "flame", "display": "bar"}
  }
}
```
Validation (reject the whole document with `invalid_layout`; unknown keys are an error; sizes as in v1: ≤ 64 KB, ≤ 12 pages, ≤ 256 cards, ≤ 2048 widgets – since 0.10 ≤ 256 KB, ≤ 2048 cards, ≤ 8192 widgets and `y` ≤ 99 999, see "Scaling"):
- `v` = `1` (old rules above) or `2`.
- `pages`: as in v1.
- `cards` keys: a card key (1–160 chars) or a custom card `c:` + `[A-Za-z0-9_-]{1,24}`. Values (all optional): `page` ≤ 32, `x` int 0–11, `y` int 0–999, `w` int 1–12, `h` int 1–60, `x + w ≤ 12` when both are set, `title` ≤ 48, `hidden` bool, `color` ∈ accents, `icon` ∈ icon set, `style` ∈ `comfortable|compact`. v1's `order`/`span` are not allowed in v2.
- `widgets` keys as in v1. Values (all optional): `card` (a card key or custom card key, 1–160 chars), `x` 0–11, `y` 0–999, `w` 1–12, `h` 1–40, `x + w ≤ 12`, `label` ≤ 48, `hidden` bool, `color` ∈ accents, `icon` ∈ icon set, `display` ∈ `dial|bar|number|row|big|line|sparkline|chip|lamp` (gauge: dial/bar/number, progress: bar/number, label: row/big, chart: line/sparkline, indicator: chip/lamp; others ignore it).
- Overlaps are not an error (the dashboard resolves them by pushing items down; beyond 64 boxes in one grid in one pass in reading order, and items without a box beyond a work budget of a few hundred are packed below in their order, so no saved document can make placement slow – security review 2026-10-30); unknown card/widget keys are kept and skipped when rendering, as in v1. A `card` pointing at a card that does not exist (any more) puts the widget back into its own card.
- Custom cards hold widgets of any uplink; actions still go to the widget's own uplink (`uplinkId` is part of the widget key). Share links may list custom card keys in `cards`.

**Migration v1 → v2 (dashboard):** cards keep their page and reading order and get `w = span × 3`, packed left to right into rows; widgets get `w = span × 6` in 1-column cards and `span × 3` in wider ones, packed in their order; heights come from the widget types (gauge 5, chart 5, label 1, buttons 2, …) and the card height is header (2) + its widgets. The first save writes v2.

**Responsive rendering:** ≥ 1100 px the page grid uses the exact positions; 720–1099 px six columns in reading order (y, then x) with `w ≤ 4 → half width, else full`; < 720 px two columns (`w ≤ 2 → half, else full`). Widget grids inside cards always use their exact positions.

**Server side (mod):** `LayoutValidator` accepts v1 and v2 with exactly the rules above. Minimum sizes: a v2 card needs `w ≥ 3`; on `layout_set` a visible widget of a published section also needs `w ≥` its type's minimum (`item_search`/`watchlist`/`jobs`/`redstone_sides` 6, `chart` 4, `gauge` 2, everything else 3, as `minWidgetW` in layout.ts), else `invalid_layout`. Hidden widgets and widgets of sections that are not published are not checked. At server start a stored v1 document is migrated to v2 with the dashboard's algorithm (`LayoutMigration`, a port of `migrateLayout`; the shared fixture `src/test/resources/layout/migration_fixture.json` is generated from layout.ts and checked by the Java tests) once the mainframe's sections are known (cached sections count); without known sections the v1 document stays and the dashboard migrates it on its first save. Unlike the dashboard, the server keeps entries of sections it does not know, without `order`/`span`. Share links: a widget counts for the card it sits in (`card`, if that card exists), custom cards are shared by key or by their page.

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
- Frame size: a raw-byte guard in front of Netty's frame decoder reads each frame header and closes the connection with 1009 before the payload is buffered if a frame (or a fragmented message) is larger than the current limit: **2 KB before authentication**, 64 KB + 1 KB after (256 KB + 1 KB since 0.10). After authentication every message except `layout_set` is still limited to 8 KB (checked after parsing).
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
- **Uplink screen** (right-click): status, linked mainframe (position, distance, range OK), title, widget count, mode (auto / Lua / direct), and an "unlink" button (owner/trusted). Redesigned in plain language on 2026-10-30, see "Device screens in plain words" below.
- Style: CC: Tweaked look (dark terminal panel with the CC border, silkscreen-like pixel headings, gold for advanced, cyan accents). GUI textures are drawn by the art generator (`textures/gui/mainframe.png`, `textures/gui/uplink.png`, widgets in `textures/gui/widgets.png`); the Java side draws text and dynamic parts.
- All actions go through custom payload packets with server-side permission checks (never trust the client). The screen gets a snapshot when opened and live updates while it is open (pending approvals appear without reopening).
- Chat stays as a fallback: the clickable Allow/Deny chat message still appears for players who do not have the screen open.

### Device screens in plain words (2026-10-30)
Feedback from the showcase world: on a reactor it was hard to see what an uplink is and does. The uplink, bridge and relay screens (one screen class, `UplinkScreen`, view by `GuiViews.uplink/bridge/relay`) now answer in this order:
- **Header** (fixed): the attached block's icon and "Connected to: <block> (<mod>)" (computers: "Computer #7 “label”"; bridges: "Reads: ME system (Applied Energistics 2)"; relays: what they do) and a status pill in words ("Online – live on the dashboard", "Online – but the signal is weak", "Offline – the mainframe has no power", "Paused – the mainframe is full", "Not linked yet").
- **The fix first**: when something is wrong, one warning line with what to do (not linked, no route, over capacity, old link out of range, mainframe without power / killed by redstone / dashboard off / not loaded / cloud disabled, weak signal below 15 %).
- **On the dashboard**: what kind of card it is (ready-made machine card, Storage card, automatic computer card, Lua card, none yet), what a player can use on it, and a **live preview** of the card as the dashboard shows it (title incl. the layout's custom title, widgets as small read-only pixel widgets; layout-hidden widgets left out, layout labels used). The view carries `preview` (`CardPreview`: ≤ 24 widgets, texts ≤ 40 chars, ≤ 32 chart points, a few KB).
- **Connection**: "Linked to <mainframe name>" (the default name already contains its position; it is never appended again; without `view.positions` just "Mainframe") and a signal bar with "72 % · direct / via 1 relay / by cable".
- **Actions**: Rename (the card's `title` in the mainframe's dashboard layout, needs `layout.edit`, empty = the original name, 5 saves / 10 s per mainframe like `layout_set`), Show in dashboard (a chat link to `<dashboard>/#/m/<mainframe>?card=<section>`, needs `view`; the dashboard opens the card's page and scrolls to it), Unlink (needs `network.link`, asks once more).
- **Advanced** (collapsed, remembered per player in `config/cccloud-screens.properties`): wireless redstone, card kind/template/mode, widget and computer counts, id, positions, distance, reach, hops and signal numbers.
- The relay opens this screen on right-click (before: chat lines): what it does (its ranges), the networks it carries (devices routed through it, its own signal; names only where the player has `view`, others counted) and its power. The mainframe's overview starts with what it is, "6 of 7 linked devices connected · 1 relay" and the fix when it is off.

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

### C. Rules – web protocol and implementation notes (mod side)
Implemented in `rules/` (`Rule`, `RuleCodec` = the strict schema, `RuleEngine` = pure evaluation, `RuleService` = game side, `RuleData` = SavedData `data/cccloud/rules.dat`). Rules are evaluated every 10 ticks on the server thread, only while their mainframe is loaded and online; config `rulesEnabled` (default true) switches all rules off.

**Rule document** (`rule_set` sends it; unknown keys are an error, ≤ 4 KB; `lastRun`/`lastResult`/`runs` are accepted and ignored so the client may send back what it got):
```json
{"id": "0a1b2c3d", "name": "Cool the reactor", "enabled": true, "cooldown": 30, "notify": false,
 "when": {"type": "widget", "uplinkId": "<uuid>", "widgetId": "temp", "op": ">", "value": 90},
 "if":   [{"type": "power", "op": ">=", "value": 20}, {"type": "online", "uplinkId": "<uuid>"}],
 "then": [{"type": "redstone", "uplinkId": "<uuid>", "side": "top", "value": 15},
          {"type": "alert", "text": "Reactor hot", "level": "warn"}]}
```
- `id`: 8 hex chars; omit it for a new rule (the server assigns one, answered in `rule_result.id`); an existing id replaces that rule. `name` 1–48 chars; `enabled` default true; `cooldown` seconds 5–86400, default 30; `notify` (push the run result, v0.5 D).
- **when** (exactly one): `widget` {uplinkId, widgetId, op, value} (numeric widgets; booleans count as 0/1), `items` {uplinkId, item `ns:path`, op, value ≥ 0} (storage behind a bridge/uplink card), `power` {op, value 0–100} (mainframe FE buffer in %), `offline` {uplinkId}, `interval` {minutes 1–1440}, `time` {time 0–23999, in-game time of day}. `op` ∈ `< <= > >= == !=`.
- **if** (≤ 8, all must hold): the `widget`/`items`/`power` checks plus `online` {uplinkId}.
- **then** (1–10): `redstone` {uplinkId, side top|bottom|left|right|front|back, value 0–15} (auto-card computers), `widget` {uplinkId, widgetId, value number|string ≤ 256|bool} (button/toggle/slider/select/number/text – same validation as a human action, user `rule:<name>`; widgets with a PIN are refused: `pin_widget` on save, `pin_required` at run time), `craft` {uplinkId, item, amount 1–`maxCraftAmount`} (via the card's item search), `alert` {text ≤ 120, level info|warn|error}, `dashboard` {enabled}.
- **Loop protection**: a rule whose actions write the widget its trigger watches (or the `redstone` widget of the same uplink with a redstone action) is refused (`invalid_rule`). At most 20 rule runs per second per mainframe; the first throttled run in a second is logged as `rate_limited`, further ones are dropped.
- **Semantics**: check/offline triggers fire on the false → true edge only (the first evaluation after a load only records the state; an unknown value keeps it); `interval` fires every N minutes after the rule was loaded; `time` fires when the day time passes the given tick (also across midnight). After the trigger: cooldown since the last run, then the conditions. Actions run in order; the run's result is the first error (later actions still run).
- **Limit**: rules per mainframe = the Memory upgrade's value (base 8, tiers 24/64/128) capped by `maxRulesPerMainframe` (default 128, hard cap 128); more → `too_many`.

**Messages** (rate limit as actions; answered with `{"t":"rule_result","reqId","ok","error?","detail?","id?"}`):
- `{"t":"rule_set","reqId","rule":{…}}` – needs `rules.edit`; errors `invalid_rule` (+ `detail`: the first schema problem), `too_many`, `pin_widget`.
- `{"t":"rule_delete","reqId","id"}` – `rules.edit`; `unknown_rule`.
- `{"t":"rule_enable","reqId","id","enabled"}` – `rules.toggle`; `unknown_rule`.
- `{"t":"rule_run","reqId","id"}` – `rules.toggle` ("Run now": ignores trigger, conditions and cooldown, not the run limit); `unknown_rule`, `offline`, `rate_limited`, or the first action error.
- Permission errors as everywhere (`forbidden`/`read_only`/`not_allowed`).

**Rules document** (snapshot field `rules`, delta `{"t":"rules", …}` after every change and every run; never sent to share sessions):
`{"rules":[<rule> + "lastRun": ms|null, "lastResult": "ok"|error|null, "runs": n], "log":[{"time","rule","name","result","manual"}] (last 50 runs), "limit": n}`. The run state (`lastRun`, `runs`) lives in memory and starts fresh after a restart; the log is stored.

Lua (`cloud_mainframe`): `getRules()` and `setRuleEnabled(id, enabled)`.

### D. Push notifications
- Targets per mainframe (canControl to edit, secrets stored server-side, never sent back to the browser in full): **Discord webhook**, **ntfy** (topic URL, optional token), generic **webhook** (JSON POST).
- What is sent: alerts (by min level), rule actions with `notify`, device approvals pending, mainframe offline/no power.
- Outgoing HTTP from the server: async, timeouts 5 s, retries with backoff, max 30 messages/minute per mainframe, URL allowlist by scheme (https only, except ntfy on LAN), never follow redirects to private IPs (SSRF protection), config `pushEnabled` and `pushAllowedHosts`.
- A "Send test notification" button.

### D. Push – web protocol and implementation notes (mod side)
Implemented in `push/` (`PushTarget`, `UrlPolicy` = SSRF rules, `PushFormat`, `PushHttp` = the HTTP client, `PushService`, `PushStore`/`PushData` = SavedData `data/cccloud/push.dat`, `PushApi`). New permission **`push.manage`** (group `manage`, admin and owner presets).

**API** (`/api/mainframes/<id>/push`, needs `push.manage`; 403 `forbidden`/`not_allowed` like the other mainframe APIs):
- `GET` → the push document: `{"enabled": bool (config), "targets":[{"id","type":"discord|ntfy|webhook","name","url":"https://discord.com/…a1b2","hasToken","minLevel":"info|warn|error","events":["alerts","rules","approvals","power"],"enabled","lastSent": ms|null,"lastResult":"ok"|"http_404"|"timeout"|…|null}], "limit": 5, "perMinute": 30, "allowedHosts":[…]}`. The URL is masked (scheme + host + last 4 characters) and the ntfy token is never returned (`hasToken`).
- `POST {"op":"add","target":{"type","name?","url","token?","minLevel?","events?","enabled?"}}` – defaults: name = type, `minLevel` `warn`, events alerts + rules + power, enabled. Max 5 targets per mainframe (`too_many`).
- `POST {"op":"update","id","target":{…}}` – only the given fields change; without `url`/`token` the stored secret stays (`"token":""` removes it). Changing the type needs a new `url`.
- `POST {"op":"delete","id"}`, `POST {"op":"test","id"}` (queues "Test notification"; the result shows up as `lastResult`; 409 `disabled`, 429 `rate_limited`).
- Every answer is the new push document; errors 400 `invalid`, `invalid_url`, `https_only`, `host_not_allowed`, `too_many`, 404 `unknown_target`. Changes are written to the mainframe's audit log.

**What is sent:** `alerts` – every alert of a script, card, craft job or rule alert action from `minLevel` up (Lua `crit` counts as `error`), titled with the section's title; `rules` – runs of rules with `notify` (result `ok` or the error); `approvals` – a browser waits for in-game approval on the mainframe (device name only); `power` – "No power" (`error`) / "Power restored" (`info`), at most once a minute per mainframe.

**Formats:** Discord `{"content","username":"CC: Cloud","allowed_mentions":{"parse":[]}}` (markdown and mentions escaped, ≤ 1900 chars); ntfy plain-text body with `Title` (control characters removed, non-ASCII as RFC 2047), `Priority` 3/4/5, `Tags`, optional `Authorization: Bearer <token>`; webhook `{"source":"cccloud","event","mainframeId","mainframe","title","text","level","time"}`.

**Safety:** https only (plain http only for an ntfy target whose host is in `pushAllowedHosts`); no user info or fragment; Discord only `https://discord.com|discordapp.com|canary.discord.com|ptb.discord.com/api/webhooks/…` on the default port; `localhost`, `*.local`, `*.internal`, IP literals that are not public and numeric host tricks are refused unless allow-listed. At send time the host is resolved once and **every** address must be public (loopback, RFC 1918, link-local incl. 169.254.169.254, CGNAT 100.64/10, multicast, reserved, unique-local fc00::/7, IPv4-mapped/NAT64/6to4 forms of those, documentation ranges); the connection then goes to that checked address (no second lookup, no DNS rebinding), TLS checks certificate and host name, redirects are never followed (a 3xx counts as failed). Timeouts 5 s (connect, answer), responses ≤ 64 KB. Async on 2 worker threads, 30 deliveries per minute per mainframe (token bucket), queue ≤ 200, 3 attempts with 2 s / 6 s backoff for network errors, 429 and 5xx. Targets that no longer pass the rules (e.g. after the allowlist changed) are dropped on load.

**Config** (`[push]`): `pushEnabled` (default true), `pushAllowedHosts` (default empty = any public host; `host` or `*.domain`).

### E. Browser terminal + Lua editor (for scripters)
- **Terminal**: live view of a CC computer's terminal (attached to an uplink) with keyboard input from the browser (canControl only), like standing in front of it. Server streams terminal changes (diffed, max 10 fps, colour palette), input events are rate-limited and go through CC's event queue (`char`, `key`, `key_up`, `paste`, `mouse_click` optional). Uses CC's server-side terminal state (check the CC API for reading a ServerComputer's terminal).
- **Lua editor**: file tree of the computer's filesystem (only that computer's own drive, no disk drives, no rom writes), open/edit/save files with syntax highlighting (lightweight editor bundled, e.g. CodeMirror 6 core with the Lua mode, lazy-loaded chunk), "Save & run" (runs `shell.run(file)` via a queued event the cloud.ui/startup helper listens to, or via the terminal by typing the command). Limits: file size ≤ 256 KB, path validation, quota respected, audit log.
- Both are behind a per-mainframe setting `allowRemoteTerminal` (default true for owner/trusted, PIN-protected if a PIN is set).

### E. Browser terminal + Lua editor – web protocol and implementation notes (mod side)
**Editor:** uses the script import API (section "Script import – HTTP API"): `?tree=true` listing, 8-level paths, folders (`?dir=true`), `POST …/move`, save with `?ifMatch=<hash>` (conflict check), "Save & run" = `PUT` then `POST …/run`. Permissions `scripts.upload` / `scripts.delete` / `scripts.run`, PIN, rate limit and audit as there.

**Terminal** (`terminal/`: `TerminalFrame` = picture + diff, `TerminalInput` = strict input, `TerminalService` = streaming on the server thread; config `[terminal] terminalEnabled`, default true). Permission **`terminal`** (admin/owner presets; removed by `cccloud.feature.scripts=false`); the mainframe PIN when one is set. The computer is the one behind a Cloud Uplink (`uplinkId`), like the script import. The picture comes from CC's public `ServerComputer.getTerminalState()` (a copy), so CC internals are not touched.
- `{"t":"term_open","reqId","uplinkId","pin?"}` → `{"t":"term_result","reqId","ok","error?"}`. Errors: `forbidden`/`not_allowed`/`read_only` (permission), `pin_required`, `wrong_pin` (+ `attemptsLeft`), `pin_locked` (+ `retryAfter`), `unknown_computer`, `disabled`, `too_many` (2 open terminals per session, 8 viewers per computer), `rate_limited`. Opening is written to the audit log. Sending `term_open` again re-sends the full picture.
- Server → client, up to 10 per second, only when something changed: `{"t":"term","uplinkId","on":true,"full":bool,"w","h","colour" (full only),"cursor":{"x","y","blink"},"palette":["#rrggbb"×16] (full or changed; indexed by blit digit, "0" = white by default),"rows":[{"y","text","fg","bg"}]}` – `full:true` replaces everything, otherwise only the listed rows changed. `text` is CC's 8-bit charset (control characters as spaces), `fg`/`bg` are blit strings (`0`-`f`). While the computer is off: `{"t":"term","uplinkId","on":false}`.
- `{"t":"term_closed","uplinkId","reason"}`: `forbidden` (rights lost – checked every frame), `unknown_computer` (uplink/computer gone), `disabled`, `unsupported`.
- `{"t":"term_input","uplinkId","events":[…],"reqId?"}` (20 messages/s, ≤ 32 events each; answered with `term_result` only when a `reqId` is given): `{"type":"char","c":"a"}` (one printable character), `{"type":"key","key":257,"held":false}` / `{"type":"key_up","key":257}` (GLFW key codes like CC's `keys` API, 1–348), `{"type":"paste","text"}` (≤ 512, first line only), `{"type":"terminate"}`, `{"type":"mouse_click|mouse_up|mouse_drag","button":1-3,"x","y"}` and `{"type":"mouse_scroll","dir":-1|1,"x","y"}` (advanced computers, 1-based inside the terminal), and the power controls `turn_on`, `reboot`, `shutdown`. Errors: `invalid_input` (the whole message is refused), `not_open`, `computer_off` (only `turn_on` works while off).
- `{"t":"term_close","uplinkId"}`; closing the socket closes every terminal of the session. Share sessions are always refused.

### F. HTTPS via Tailscale + statistics
- Docs page: "Install as an app with HTTPS via Tailscale" (`tailscale serve --https=443 http://localhost:8787`, or `tailscale cert`) – the mod only needs `publicUrl` set and trusted proxy 127.0.0.1. Detect `Tailscale-User-Login` headers? No – keep auth as is.
- **Long-term stats**: any numeric widget can opt in (`opts.history="1h"|"24h"|"7d"`); the mod downsamples (1 min / 5 min / 30 min buckets) and stores compactly per mainframe (cap 2 MB). Charts in the dashboard with range switcher; CSV export (canControl).

### F. Long-term statistics – implementation notes (mod side)
Implemented in `history/` (`HistorySeries` = tiers + storage form, `HistoryStore` = per mainframe with the cap, `HistoryService` = sampling, `HistoryData` = SavedData `data/cccloud/history.dat`, `HistoryApi`).
- **Opt-in:** numeric widgets (`gauge`, `progress`, `chart`, `slider`, `number`, `indicator` as 0/1) accept `opts.history = "1h"|"24h"|"7d"|"30d"` (Lua: `cloud.gauge("temp","Core",{history="24h"})`). Sampled every second; averaged into 1-minute buckets (kept up to 24 h), 5-minute buckets (7 d) and 30-minute buckets (30 d) – only the tiers the range needs are allocated (`1h` = 60 points, `24h` = 1440, `7d` adds 2016, `30d` adds 1440 half-hour points). Gaps are `null`.
- **Retention:** the range is limited by the mainframe's history hours (Memory upgrade: base 1 h, then 24 h / 7 d / 30 d). Inserting or removing the upgrade resizes series and keeps the data that fits.
- **Items:** the count of every watched or favourite item of a storage card (max 64 per bridge) is sampled once a minute with range 24 h; the storage `item` details return it as `history` (5-minute averages).
- **Limits:** at most 2 MB per mainframe (a 30 d series is ≈ 20 KB, so about 100 series); new series beyond the cap are not recorded (server log once). Series without samples for longer than their range are dropped.
- **API:** `GET /api/mainframes/<id>/history/<1h|24h|7d|30d>/<json|csv>/<w|i>/<uplinkId>/<widgetId or item>` (Bearer token).
  - Needs `view`; restricted sections also `view.positions`; items `storage.search`; CSV export `act.buttons` (403 `not_allowed`). Share links never get history.
  - JSON: `{"key","range","kept":"1h|24h|7d|30d","retentionHours","step":60000|300000|1800000,"points":[[timeMs, value|null], …]}` (oldest first; the last point is the running bucket). Requests longer than what is kept return what is kept (`kept`).
  - CSV: `time,value` with ISO-8601 UTC times, empty value for gaps (`text/csv`).
  - `404 no_history` when the widget has no `opts.history` and nothing is stored.

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
- `owner` – who placed the mainframe first (a restored mainframe keeps its owner, see "Mainframe removal"); admin + transfer ownership + delete.
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
- Device node ids are uplink ids; the dashboard uses them to find the card ("Open card"). `type:"device"` with `status:"unloaded"` is a linked device in an unloaded chunk. `type:"probe"` is a Cloud Probe; with positions it carries `target:{x,y,z}` (see "Cloud Probe").
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
  - `/cccloud icons` uses `cccloud.command.icons`; `/cccloud config reload|show` uses `cccloud.command.reload` (see "Live config").
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

### Chunk Anchor upgrade card (optional, decided 2026-10-23)
- `cccloud:upgrade_chunk_anchor` goes into one of the 4 card slots. While installed, enabled on the server and the mainframe is online with power, it keeps the mainframe's chunk and the chunks of its linked devices loaded (ticking), across dimensions.
- **Disabled by default**: server config `upgrades.chunkAnchor=false`. Cost: `anchorBaseFE` 200 FE/t + `anchorPerChunkFE` 20 FE/t per chunk (breakdown key `anchor`).
- Limit per player (the mainframe owner, counted over all of their mainframes): permission node `cccloud.limit.anchorChunks` (integer, default 9; `cccloud.bypass.limits` = unlimited). The mainframe's own chunk comes first, then devices in link order.

### v0.7 – Implementation notes (mod side): upgrades, chunk anchor, wireless redstone
**Upgrades** (`upgrade/*`)
- Items: `upgrade_<capacity|bandwidth|range|efficiency|memory>_<allthemodium|vibranium|unobtainium>`, `quantum_core`, `upgrade_chunk_anchor` (stack size 1, tooltips list the effect).
- Mainframe inventory: 4 card slots + 1 Quantum Core slot (`upgrades` in the block entity NBT, dropped when the mainframe breaks). No item capability, so hoppers/pipes cannot insert. The mainframe screen has an **Upgrades** tab (all roles see it; changing slots needs `devices.manage`, checked on the server for every click) with the slots (GUI_LAYOUT slot regions), the player inventory and the effective limits.
- Effects (`UpgradeEffects`, unit tested): base limits from config `upgrades.baseDevices` 16, `baseBrowsers` 4, `baseRules` 8, `baseHistoryHours` 1; highest tier per type wins; Quantum Core = unlimited devices/browsers, FE ×0.25 (or lower), the mainframe is an Ender node of its own group (its Ender Relays link to it directly, any distance/dimension).
- Applied: node range × factor (mainframe node), FE (breakdown item `efficiency` = −saved FE, so the items still sum to the usage), browsers (WebSocket close `4429 browser_limit`; share viewers do not count), rules (`min(rules.maxRulesPerMainframe = 128 cap, Memory)`; rules document `limit`), history hours (exposed in the screen and to the web; enforced by the long-term statistics, v0.5 F).
- Capacity (`network/graph/Capacity`, unit tested): relays on device routes count 1, routed devices 1 (fiber `fiberCapacityWeight`). New links beyond the limit are refused (`cccloud.network.capacity_full`). Above the limit (e.g. after removing a card) devices with the most hops, then the most recently linked, get `status:"over_capacity"` on the map, `routeState:"over_capacity"` on the uplink screen, and stop (no route) until capacity returns. The mainframe node's `capacity` object: `{devices, used, limit (null = unlimited), over, wired, fiber}`.
- Recipes: tier 1 card = ` M /MTM/ C ` (M = metal, T = type catalyst: chest / ender modem / ender pearl / redstone block / disk drive, C = cloud core); tier 2/3 = ` M /MPM/ M ` with P = previous tier card. With AllTheModium (`neoforge:mod_loaded allthemodium`) M = `#c:ingots/allthemodium|vibranium|unobtainium`, else (`*_fallback`) diamond / netherite ingot / nether star. Quantum Core: `UUU/UAU/UCU` with `allthetweaks:atm_star` and unobtainium when All the Tweaks is loaded, else `NSN/SCS/NSN` (netherite blocks, nether stars, cloud core). Chunk Anchor: `OEO/EAE/OCO` (obsidian, ender pearls, lodestone, cloud core).

**Chunk Anchor** (`upgrade/ChunkAnchor`): NeoForge `TicketController` `cccloud:chunk_anchor`, tickets owned by the mainframe id (ticking). The mainframe saves its anchored chunks (`anchor_chunks`) and reconciles every 5 s and on changes, so stale tickets are removed after restarts; with the config off the validation callback drops all saved tickets on load.

**Wireless redstone** (`redstone/*`)
- Uplink screen section "Wireless redstone": Off / Transmitter / Receiver and a channel name (`[A-Za-z0-9_.-]{1,32}`); needs `network.link` on the linked mainframe (screen action `redstone`, arg `<off|transmit|receive>:<channel>`). Bridges have no redstone mode.
- Transmitters read the strongest neighbour signal (e.g. a computer's redstone output into the uplink) on every neighbour update and once on their first tick after loading (the input is not saved, and a lever that stays on sends no update after a world load or when it was placed before the transmitter), receivers emit their channel's level on every side (weak power, analog 0–15). Only uplinks with a route to their mainframe take part, so it works over radio, relays, CC cables, fiber and ender links (also across dimensions). Max 64 channels per network (alphabetically first ones), next tick after a change, receivers change at most every 2 ticks.

### v0.7 web protocol: wireless redstone channels (for the dashboard)
- Snapshot field `redstone` (not for share viewers) and delta `{"t":"redstone","channels":[…],"limit":64}` whenever a channel changes:
  `{"name":"door","level":15,"signal":15,"transmitters":1,"receivers":2,"override?":{"level":4,"until":<ms epoch>|null}}` – `level` is what receivers output (the override wins), `signal` the transmitters' max. Channels with no members disappear; the list is sorted by name.
- Override (needs `act.redstone`): client → `{"t":"rs_override","reqId","channel","level":0-15,"seconds":1-86400|null}` (null = until reset); `level:null` clears the override. Server → `{"t":"rs_result","reqId","ok","error?"}` (`not_allowed | read_only | forbidden | invalid_channel | invalid_argument | too_many_channels | rate_limited | offline`), then a `redstone` delta. An override may create a channel that has no transmitter yet. Every override is written to the mainframe's audit log.
- Power breakdown items may now include `anchor` (Chunk Anchor FE) and `efficiency` (negative, saved FE); unknown keys should be shown with their key as label.

### Cloud Tablet (item)
- Handheld item, opens the dashboard **in-game** (a native screen that renders the same cards/widgets with MC widgets – not a browser). Works within network range of any node (or anywhere in Ender range with an advanced tablet). Uses the player's role on that mainframe. Keybind to open (default unbound) when the tablet is in the inventory/curios slot.
- Normal and advanced tier; recipe from a CC pocket computer + cloud core (+ gold/ender pearl for advanced).

### Cloud Display (block)
- A multiblock-capable display (like CC monitors: forms rectangles up to 8×6) that renders one dashboard card or page in-world (read-only, chosen in its screen; operators can make buttons clickable in-world with right-click). Rendered with a BER using cached textures, updated at most 4×/s, culled beyond 48 blocks.

### Tablet + Display – Implementation notes (mod side)
Code: `ingame/` (shared: access helper, coverage, hub session, payloads, registry, dashboard model), `tablet/`, `display/`, client in `client/gui/{TabletScreen,DisplayScreen}` and `client/display/`. `CCCloud` calls `InGameRegistry.init` once. No new browser message types: in-game clients use the browser protocol inside custom payloads.

**Rights** (all through `ingame.InGameAccess` → the AccessResolver's fine-grained permissions): the tablet needs `view`; every action is checked live by the bridge with the same per-widget-kind permission as a browser action (`act.buttons`, `act.redstone`, `storage.*` via `AccessGate.actionPerm`); restricted sections reach the tablet only with the permissions the hub requires (filtered by the session's permissions, changes are pushed); binding a display needs `network.link`, choosing what it shows `layout.edit`, in-world clicks `act.buttons` of the clicking player. Share-link viewers never get anything: in-game clients are always a real player. Label widgets follow the web rule: long (> 32 characters) or multi-entry values (" · ", ";", newlines, e.g. tick time per dimension) get the caption on its own row and one line per entry below (with the TPS a tick time allows); otherwise the caption, not the value, is cut with an ellipsis. Cached sections of unloaded chunks (`loaded:false`, `lastSeen`) are shown greyed with the time they were last seen (tablet and display); their controls are disabled.

**Cloud Tablet** (`cccloud:cloud_tablet`, `cccloud:advanced_cloud_tablet`, stack size 1):
- Link: right-click a mainframe (viewer and up). Open: right-click, or the key "Open Cloud Tablet" (unbound by default) – the first linked tablet in the hands, inventory or Curios slots. Curios: the tablets are in the `curios:belt` item tag and `data/cccloud/curios/entities/cloud_tablet.json` gives players a belt slot (data only; the Curios API is only used to find a tablet, `compile/runtime` via `top.theillusivec4.curios:curios-neoforge:15.0.0+26.1.2`, soft).
- Coverage (normal tablet): the player must be within the device range of an available node of the mainframe's network (mainframe 96, relays per type) in the same dimension, from the topology the network service computes. The advanced tablet works anywhere and across dimensions.
- Server: the open tablet is a slot-less menu plus an `InGameSession` registered with the hub like a browser (synthetic device `ingame:<uuid>`, limited to the mainframe, never stored; its role is the player's role). The hub's messages (snapshot, deltas, role pushes) are sent to the player each tick as `cccloud:tablet_data` (a JSON array of hub messages, ≤ 1 MB per packet; a client that falls behind gets a fresh snapshot). When the web server is disabled the tablet service flushes the hub itself.
- Every second: the tablet must still be carried and linked to that mainframe, the player in coverage (normal tablet) and still have a role – otherwise the screen closes with a message.
- Requests: `cccloud:tablet_request` carries a browser-format `action` or `query` message (≤ 8 KB), parsed by `ClientMessages` and run by the same bridge as browsers (live role check, uplink check, widget validation, audit log "<player> (tablet)"). Rate limits: 10 actions/s, 5 queries/s. PIN widgets: the screen asks for the PIN; it is checked off the server thread with the same per-device lockout. Results come back as `action_result`/`query_result` messages in `tablet_data`.
- Screen: all widget types (gauges/progress bars, labels, indicators, charts, buttons with two-step confirm, toggles, sliders as −/+, select/buttons, number/text inputs, item search, watchlist, jobs, redstone sides) plus an Alerts tab; layout card order and hidden cards/widgets are respected.

**Cloud Display** (`cccloud:cloud_display`, CC-monitor blockstate: `facing` × `orientation` × 16 edge states):
- Forming exactly like CC: Tweaked monitors (expand into compatible neighbours, contract/split when a block is removed, validate after loading), up to 8×6 blocks. The configuration (mainframe, section, in-world clicks, configuring player) is copied to every block, so it survives growing and splitting.
- Saved per block: `index_x`, `index_y`, `width`, `height` and the configuration. (Until 0.9.0 the index was saved as `x`/`y`, which the chunk save overwrites with the block position, so every reload found the wall "malformed" and reset it. Such saves start as 1×1 displays and join their neighbours again on the first tick, without a warning.) Validation after loading only checks parts in loaded chunks; parts in unloaded chunks never make a wall malformed.
- Setup: right-click with a linked Cloud Link Card (admin) binds it; sneak + right-click opens its screen (status; for admins the card picker, which never offers restricted sections, the "in-world clicks" switch and unlink). The display only shows a section while the player who chose it still has at least viewer on the mainframe, and never a restricted one (also re-checked when reading).
- Content: the origin block reads the section snapshot from the hub at most 4×/s and sends it in the block entity update packet when it changed (≤ 48 KB, else "too large"). The client paints it on the CPU into a cached `DynamicTexture` (64 px per block, Minecraft's `font/ascii.png` glyphs) at most 4×/s and draws one full-bright quad over the rectangle (same face transform as CC monitors); culled beyond 48 blocks, idle textures are freed after 30 s.
- In-world clicks (if enabled): a right-click on the front is mapped to the display (CC's monitor hit mapping) and hit-tested against the same layout (`DisplayLayout`, deterministic, shared by client and server); only buttons and toggles without `pin`/`confirm` react, and only for players with operator.

**Recipes:** Cloud Tablet = pocket computer + cloud core (shapeless); Advanced Cloud Tablet = advanced pocket computer + cloud core + gold + ender pearl, or a Cloud Tablet + gold + ender pearl; 4 Cloud Displays = advanced monitor + cloud core + 7 gold.

### 3D map in the web dashboard
- A third view on the Network page: **3D**. three.js (lazy chunk, pinned version, bundled – no CDN at runtime). Devices are rendered with their real block models (reuse the model JSONs + textures from `/assets/...` served by the mod or bundled), placed at real coordinates; links as lines/tubes; ranges as translucent spheres (toggle).
- Optional coarse terrain: the server samples a heightmap with map colors (`MapColor`) around nodes (radius 96, 2-block grid, only loaded chunks, cached, ≤ 200 KB per request, canControl only, rate-limited) so the base is recognizable.
- Interaction: orbit/zoom/pan (touch + mouse), click a device → its detail panel with sub-menus (card, widgets, power, routes, actions per role); click a relay → highlight everything routed through it; dimension switcher; "fly to" from search.

### v0.7 web protocol (wired links, terrain, 3D view)
Implemented by the dev mock (`cloud/dev/mock-server.ts`, `network-sim.ts`) and the dashboard.

**"Pair in browser" chat link:** the mod links to `<dashboard-url>/pair#code=XXXX-XXXX` (account codes: `#code=XXXX-XXXX&account=1`). The web server must serve `index.html` for `/pair` (SPA route, like `/s/<id>`; `Referrer-Policy: no-referrer` recommended). The dashboard reads the code from the fragment at start-up, replaces the URL with `/#/pair` via `history.replaceState` before any request, prefills the form (device name defaults to e.g. "Enrico's Mac – Chrome") and sends the code only in the `POST /api/pair` body. Invalid codes are ignored (empty form).

**Optimistic values (dashboard behaviour, 2026-10-23):** selecting the value a widget already has sends nothing. After `action_result ok` the dashboard waits up to 8 s for the widget's value; `action_result` may carry `"value"` (the widget's value after the handler ran – recommended: echo it even when `set()` did not change it), which confirms immediately. Without either, the control falls back to the server value with an info toast "No answer from the computer yet – is the program running?".

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
- Two chapters for ATM11: **CC: Tweaked** (computers, turtles, monitors, modems, peripherals, first programs) and **CC: Cloud** (mainframe, power, uplinks, link card, pairing a browser, bridges, relays, upgrades, tablet, display, rules). Format: the pack's `config/ftbquests/quests/chapters/*.json5` + lang files; place them in a chapter group that fits ATM11's structure, rewards in ATM style (modest). Ship them in the repo under `integration/ftbquests/` with an install script; contribution to ATM via their process (submission notes live with the website, repository `enricofranke/cccloud-website`).

### Map tiles for the 2D map (requested 2026-10-23, moved up before v0.5 C–F)
The 2D network map gets a real top-down map of the world as background, like a Minecraft map / JourneyMap / Dynmap "flat":
- Server renders tiles of 128×128 blocks at 1 px per block (PNG, map colors from `BlockState.getMapColor` with vanilla-map-style height shading; water depth tint). Only already-generated, **loaded or saved** chunks are read – never generate chunks, never force-load; read saved chunks off-thread via the region storage if feasible, otherwise only loaded chunks.
- Two layers: **Surface** (top block, heightmap `WORLD_SURFACE`) and **Slice** (a horizontal cut at a chosen Y, e.g. around an underground base like the user's mainframe at y −60: first non-air block scanning down from Y over 16 blocks, darker shading = deeper; like JourneyMap cave mode). The slice Y defaults to the median Y of the visible nodes.
- Endpoint `GET /api/map/<dim>/<layer>/<y?>/<tileX>/<tileZ>.png` – operator role (positions are sensitive), rate-limited (60 tiles/min per session), `Cache-Control: private, max-age=60` + ETag, 204 for tiles with no data. Only tiles within 512 blocks of a node of that mainframe's network may be requested (no free world scanning).
- Rendering off the server thread on a small executor, results cached on disk per world (`cccloud/maptiles/`), invalidated when chunks in the tile change (chunk save/unload events, debounced 30 s). Budget: ≤ 2 ms main-thread per tick for chunk snapshotting, else defer.
- Web: tiles as the map background with zoom levels (downsampled client-side for far zoom), a layer switch (Surface / Slice + Y slider), node labels with collision avoidance (labels must not overlap like "Computer #0" / "RS Network" did), and the 3D view reuses the same height data.

#### Map tiles – HTTP API (mod side, implemented)
- **Routes:**
  - Surface: `GET /api/mainframes/<id>/map/<dim>/surface/<tileX>/<tileZ>.png`
  - Slice: `GET /api/mainframes/<id>/map/<dim>/slice/<y>/<tileX>/<tileZ>.png`
  - `<dim>` is the dimension id, e.g. `minecraft:overworld`. Ids that contain a `/` are not supported.
  - Tile `(tx, tz)` covers blocks `x = tx·128 … tx·128+127`, `z = tz·128 … tz·128+127` (floor division). Pixel `(px, pz)` = block `(tx·128+px, tz·128+pz)`, north up.
- **Access:**
  - Bearer token, permission `view.positions` (else `403 {"error":"not_allowed"}`).
  - Only tiles within 512 blocks of a node or device of that mainframe's network in that dimension (else `403 {"error":"out_of_range"}`).
  - Unknown dimension → `404 {"error":"unknown_dimension"}`.
  - 60 tiles per minute per device → `429`.
- **Responses:**
  - `200 image/png` (128×128 RGBA, transparent where unknown), `Cache-Control: private, max-age=60`, `ETag` (and `304` with `If-None-Match`).
  - `204` when no data exists yet (no chunk of the tile was loaded since the server started and nothing is cached).
- **Rendering:**
  - Surface: vanilla map colors at 1:1, shaded by the height difference to the north, water by depth.
  - Slice: the first non-air block from the cut downwards (≤ 16 blocks). Cave floors are bright near the cut and darker deeper; rock at the cut is darkest; nothing within 16 blocks is transparent.
  - Only chunks that are loaded right now are sampled (never loaded or generated). Other pixels come from the last render, cached in memory and in `<world>/cccloud/maptiles/`.
  - A tile is re-rendered on request when its chunks were saved or unloaded at least 30 s ago (debounced).
  - The server thread only copies the block states of the tile's loaded chunks (`PalettedContainer.copy()` of the
    sections up to the highest block for the surface, of the sections around the cut – also of the chunks around the
    tile, for the ore rule – for a slice; air-only sections are not copied), within ≤ 2 ms per tick. Sampling (map
    colours with an empty world around the block, water depth, slice depth, ore rule), rendering and PNG encoding run
    on the background thread; so does reading a cached tile from disk (2026-10-30, before: sampling and disk reads on
    the server thread).
  - Reading chunks that are only on disk is not done yet; those areas fill in as players load them.
- **Disk cache housekeeping (follow-up 2026-10-30; before, tiles on disk were never deleted):** files are
  `<world>/cccloud/maptiles/<dim>/surface/<tx>_<tz>.png` and `<dim>/slice_<y>_<mainframe>_<player>/<tx>_<tz>.png`.
  - Purging a mainframe (`/cccloud mainframe purge`, the end of `mainframeRetentionDays`) deletes its slice folders in every
    dimension (and its slices in memory); tiles of it that are still being drawn are not written any more. Surface tiles are
    shared by all mainframes and stay.
  - Config `map.mapTileMaxAgeDays` (default 30, 0 = keep forever): a tile file that was neither drawn nor read for that many
    days is deleted, then empty folders (checked at server start and once a day, on the tile thread; symbolic links are
    never followed, tiles in the memory cache stay). Reading a tile from disk counts as use: its modification time moves to
    now at most once a day, and only when no chunk of the tile changed since it was drawn (the time is also the render time
    the freshness check compares against). A deleted tile is drawn again from the loaded chunks when it is asked for.
  - Tests: `MapTileDiskTest` (purge per mainframe and dimension, links not followed, age sweep, memory-cache tiles kept),
    GameTest `mainframe_retention_purge` (the slice folders go with the purge, surface tiles stay).

### "Crazy 3D": voxel view of the base (requested 2026-10-23)
On top of the 3D network view: render the actual blocks around the network as voxels.
- Endpoint `GET /api/voxels/<dim>/<cx>/<cy>/<cz>` returns one 16³ section around network nodes: palette (block id → map color + flags: transparent/fluid/emissive) + run-length encoded indices. Only **exposed** blocks (at least one face touching air/transparent) are sent, so the view shows the base and caves but cannot be used as an ore X-ray.
- Limits: operator role; only sections within 64 blocks (config `voxelRadius`, max 128) of a node of that mainframe's network; loaded or saved chunks only, never generate/force-load; a section budget per device (see "Voxel view – batch API"); server-side cache validated by the sections' content; config `voxelView` (default true).
- Stage 1 (now): colored voxels with map colors, greedy meshing in a Web Worker, ambient occlusion, emissive blocks glow, water translucent, day/night toggle; our devices drawn with their real models on top; clickable as in the 3D view; clipping plane / Y-slider to cut through floors of a base.
- Stage 2: real block textures – game clients upload block face textures the same way they upload item icons (validated PNG, size caps), the server builds an atlas, and the web renders textured voxels (see "Voxel view – stage 2").
- Performance budget: ≤ 150k triangles on phones, LOD by distance, frustum culling, 60 fps target; falls back to the plain 3D/2D map without WebGL2.

#### Voxel view – HTTP API (mod side, stage 1, implemented)
- **Route:** `GET /api/mainframes/<id>/voxels/<dim>/<cx>/<cy>/<cz>`. These are section coordinates: `cx = floor(x/16)`, `cy = floor(y/16)`, `cz = floor(z/16)`.
- **Access:**
  - Bearer token, permission `view.positions` (else `403 not_allowed`).
  - Only sections within config `voxelRadius` blocks (default 64, 16–128) of a node or device of the network, measured box to point (else `403 out_of_range`).
  - Config `voxelView=false` → `404 disabled`.
  - Sections count against the device's section budget (2048, refilled at 256 per second, shared with the batch API)
    → `429 {"error":"rate_limited","retryAfter":s}`. (Until 0.9.0: 40 requests per second, and every request also
    counted against the 120 API requests per minute per IP, so a base of ~500 sections took minutes.)
- **Response:** `200` with
  ```json
  {"dim","cx","cy","cz",
   "palette":[{"id":"minecraft:stone","color":ARGB int,"flags":1=transparent|2=fluid|4=emissive}],
   "runs":"<base64>","exposed":n}
  ```
  - `runs` are unsigned LEB128 varint pairs `(runLength, paletteIndex)` covering all 4096 cells in the order `x + 16·z + 256·y`.
  - Index 0 = nothing (air or hidden); index n = `palette[n-1]`.
  - `204` when the section's chunk is not loaded.
  - `Cache-Control: private, max-age=5`.
- **Anti X-ray:** only exposed blocks are sent: at least one face touches air or a non-occluding block (glass, leaves, fluids…). Neighbors in chunks that are not loaded count as solid, so chunk borders do not leak either.
- **Server cost and cache** (same for the batch API):
  - On the server thread only the block-state containers of the section and its six neighbors are copied
    (`PalettedContainer.copy()`, a few microseconds each; air-only sections are not copied), from loaded chunks only (no
    loading or generating).
  - Everything else runs on 2 background threads: a hash of the section's states, the touching layer of each neighbor
    (only that layer decides exposure and the ore rule) and the mainframe's nodes within the ore radius is compared
    with the cached section's; only when it differs is the section rebuilt (exposed-only
    filter, X-ray guard, encoding). So any block change – by players, machines, commands or block movers, with or
    without neighbor updates – shows on the next request, and unchanged sections are never rebuilt. 8192 sections per
    server are kept (per mainframe, because of the ore rule).
  - There is no disk cache and no background pre-warming: building a whole base (~500 sections) takes well under a
    second of background CPU (140 ms for the showcase base, see "Voxel view – loading measurements"), and an unread
    cache would only cost memory.

#### Voxel view – batch API (mod side, implemented 2026-10-30)
Loading a base one section per request was slow: ~500 requests, rate limited to 120 per minute per IP (shared with the
map tiles and every other API call). The dashboard now asks for up to 64 sections per request and keeps sections in the
browser.
- **Route:** `POST /api/mainframes/<id>/voxels/<dim>` with `Authorization: Bearer`, body (strict, ≤ 4 KB)
  `{"sections":[[cx,cy,cz],[cx,cy,cz,"<hash>"],…]}` – 1 to 64 distinct sections; the optional 16-hex-digit hash is
  what the browser already has. Anything else → `400 {"error":"invalid"}`.
- **Access** as for single sections, checked per section: `view.positions` (else `403 not_allowed` for the whole
  request), `voxelView` (`404 disabled`), unknown dimension `404`; per section `out_of_range` (beyond `voxelRadius`)
  and `claimed` (another team's claim) are statuses in the answer.
- **Limits:** one request of the 120 API requests per minute per IP, and as many sections of the device's section
  budget as it asks for (2048, refilled at 256 per second; `429 {"error":"rate_limited","retryAfter":s}` when the
  batch does not fit). Loading a whole base therefore never waits, while a client cannot make the server build more
  than ~256 sections per second for long.
- **Answer:** `200 application/octet-stream`, gzip (`Content-Encoding: gzip`) when the request accepts it,
  `Cache-Control: no-store`. Layout:
  ```
  "CCVB"  version (1 byte, 1)  header length (u32 big endian)  header (UTF-8 JSON)  runs (bytes)
  ```
  The header is `{"dim","palette":[{"id","color","flags","tex"?,"tint"?}],"atlas"?,"sections":[…]}` – one palette for
  the whole batch, with the stage-2 texture fields as in the single-section answer. Every section entry has
  `"c":[cx,cy,cz]` and `"s"`:
  - `ok`: `"h"` (content hash, 16 hex digits), `"n"` (exposed blocks), `"p"` (the section's palette as indices into
    the batch palette) and `"l"` (byte length of its runs). The runs of the `ok` sections follow the header in order;
    they are the LEB128 pairs `(run length, index)` of the single-section format, index n = `palette[p[n-1]]`.
  - `same`: the hash the browser sent is current; `"h"` and `"p"` only (the palette refreshes colours and textures).
  - `empty`: the chunk is not loaded (or the section is outside the world) – try again later; a cached copy may stay.
  - `out_of_range`, `claimed`: refused for this section; the browser drops its copy.
  The content hash covers the palette and the runs, so it changes exactly when what the browser would draw changes.
- **Server work:** the access checks and copying the containers on the server thread (see above), building, the
  batch and gzip on the background threads. `/cccloud map stats [reset]` (server admins) prints requests, sections,
  cache hits, server-thread time (also per tick since the last reset), background time, bytes and 429s for the map
  tiles and the voxels.

#### Voxel view – loading in the dashboard (implemented 2026-10-30)
- **Order:** sections within 40 blocks of the dimension's nodes, nearest to the mainframe first, in batches of 64, two
  batches in flight.
- **Browser cache:** sections are kept in IndexedDB (`cccloud-voxels`, key `<mainframe>|<dim>|<cx>,<cy>,<cz>`, with
  hash, palette and runs). Reopening the 3D view draws the cached sections at once and then sends their hashes with
  the batches, so the server answers `same` for everything that did not change. Entries outside the current area are
  dropped on load; a mainframe's entries are deleted when it is removed from the browser or the server answers 401/403.
- **Meshing:** a pool of Web Workers (cores − 1, at most 4; 2 on phones). Every worker decodes every section (meshing
  needs the neighbours) and greedy-meshes the 32³ regions it owns (`regionOwner`); meshes come back as transferables.
  The main thread merges region meshes per 2×2×2 regions (64³ blocks) and material, once per frame, so the scene has
  up to 8× fewer draw calls; three.js culls each merged mesh against the view frustum.
- **Indicator** (honest about the phase): "Loading blocks n %" (server answers so far), "Updating blocks n %" (cached
  blocks are shown, the server is checking them), "Server busy – waiting (n %)" (429 with `retryAfter`), "Building
  blocks…" (all answers are in, workers still meshing).
- No separate low-detail pass: the first blocks appear within the first batch, faster than a coarse heightmap would
  (numbers in "Voxel view – loading measurements").

#### Voxel view – loading measurements (2026-10-30)
Showcase world on the ATM11 test server (77 nodes/devices, 536 sections within 40 blocks, 191 of them with blocks),
headless Chrome on the same Mac (LAN), `tools/showcase/loadbench.mjs` of the website repository (cold = fresh browser profile, reload = same
profile again, tiles = the 2D map's 20 tiles with an empty tile cache), server numbers from `/cccloud map stats`
(thread CPU time). Before = main + the counters, after = this branch; both on an idle machine, the "after" cold run
right after a server start (empty server caches, JIT cold), runs 2–3 with warm server caches.

| | before | after |
|---|---|---|
| requests for the base | 952 (416 × 429) | 9 (0 × 429) |
| first blocks / 50 % / 90 % / 100 % of the triangles | 0.33 s / 2.3 s / 140 s / 208 s | 0.40 s / 0.46 s / 1.8 s / 2.2 s (warm server: 0.47–0.71 s to 100 %) |
| reload (same browser) to 100 % | 264 s (559 × 429) | 0.28–0.53 s (IndexedDB, server answers "same") |
| bytes for the base | 121 KB (JSON) | 11 KB (gzip batches), 7 KB on reload |
| server thread CPU for the base | 127 ms (0.24 ms per section) | 15 ms cold (0.03 ms), 4–5 ms warm (0.008 ms) |
| background CPU for the base | 138 ms | 140 ms cold, 20–30 ms warm |
| meshes sent by the workers (61 regions) | 336 (5.5 per region) | 61–79 |
| triangles / draw calls | 15 288 / 183 | 13 044 / 108 |
| meshing the 191 sections offline (`cloud/dev/voxel-bench.ts`) | 726 ms, 11 ms per region (p50) | 160–210 ms, 1.5–2.7 ms per region |
| map tiles (cold, after the 3D view): first / all | 0.19 s / 0.53 s for 17 of 20, 2 × 429 (the API limit was used up by the voxels, the store pauses 10 s); server thread 39.8 ms CPU (2.3 ms per tile) | 0.23 s / 0.62 s; server thread 20.7 ms CPU (1.0 ms per tile) |
| map tiles (cached): all | – | 0.09–0.14 s |

- Tick impact: the voxel work runs as server tasks between ticks, so `tick query` stayed in its idle range while
  loading (mostly 2–7 ms, single samples up to 28 ms right after the start, before and after alike). Per tick of the loading window the server thread spends 0.05–0.08 ms CPU when its
  caches are warm and 0.26 ms on the first load after a start (15 ms in total, mostly JIT and first-time lookups);
  before, the total was 8× higher but spread over 3.5 minutes by the rate limit (0.03 ms per tick on paper).
- On a busy machine (load average 50–130, earlier the same day) the same "after" build loaded the base in 2.0–3.0 s
  (reload 0.4–0.9 s) against 210–283 s before.

#### X-ray guard for slices and voxels (security review M4)
- Config `map.mapSlices`: `auto` (default: on in singleplayer and for LAN-only dashboards, off with `webAllowInternet`), `on`, `off`. Disabled → `404 disabled` for slice tiles.
- Slice tiles only within 32 blocks (Y) of one of the mainframe's nodes/devices → else `403 out_of_range`.
- Chunks claimed by another team (FTB Chunks; the player is not a member or ally) are never sampled for slices (transparent) and voxel sections there answer `403 claimed`.
- Ores (`#c:ores`) appear as stone in slices and voxels unless the ore touches air **and** is within 16 blocks of one of the requesting mainframe's nodes/devices. Slice tiles are therefore cached per mainframe and player, voxel sections per mainframe.

#### Voxel view – stage 2: block textures (mod side, implemented)
Game clients with the mod read the block textures and upload them, like item icons (same opt-in: client config `shareIconRenders`); the server builds one texture atlas; voxel responses say which tile each face uses. Blocks without textures keep the map colour (stage 1).
- **What a client sends:** for a block id, the sprite of each face of the default state's model, in the order `down, up, north, south, west, east` (first quad per side; models without side quads – flowers, torches – use their first inner quad for every face), and which faces are tinted (grass, leaves: the texture is grey and gets the biome colour). Each sprite is a **16×16 RGBA PNG** (first animation frame; larger resource-pack textures are box-averaged down, smaller ones scaled up), ≤ 4 KB, named by its sprite id (`minecraft:block/stone`).
- **Flow:** a voxel response lists palette ids; ids without stored textures are queued. Rendering clients get batches (payload `cccloud:block_texture_request`, ≤ 64 ids, one outstanding batch and one request per second per client) and answer with `cccloud:block_texture_upload` (≤ 64 blocks, ≤ 192 sprites). The server accepts only ids of that client's batch, valid ids, validated 16×16 PNGs, and sprites that an accepted block of the same upload references; a block with an empty face list is remembered as "cannot render". Entries are versioned with the mod version (`namespace@version`, game version for `minecraft`) and re-requested after updates. Stored in `<world>/cccloud/block_textures.json`; at most 4096 sprites.
- **Voxel response additions:** palette entries with textures get `"tex":[tile×6]` (same face order, `-1` = no texture: use the colour) and `"tint":bits` (bit i = face i is tinted: multiply the texture with the entry's `color`). When at least one entry has textures, the response has `"atlas":{"rev","tile":16,"cols":64,"width","height","url":"/api/voxel-atlas/<rev>.png"}`. Tile `t` is at pixel `((t % cols)·16, ⌊t / cols⌋·16)`; tiles are stable (new sprites are appended), so a cached atlas stays valid for older responses.
- **Atlas:** `GET /api/voxel-atlas/<rev>.png` (public like item icons, rate limited per IP; only game textures): the current atlas, `Cache-Control: public, max-age=31536000, immutable` when `<rev>` is current, else `no-cache` (load the URL from the newest response).
- Dev check: `./gradlew runClient -PtextureCheck=<dir>` reads a few blocks with the real client path, writes `<dir>/voxel_atlas.png` and quits.

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

### Cloud Analyzer – Implementation notes (mod side)
Code: `analyzer/` (geometry + preview, service, item, device info), `client/analyzer/AnalyzerOverlay`, Jade lines in `compat/jade/NetworkJadeProvider`.
- **Item** `cccloud:cloud_analyzer` (stack size 1, recipe: pocket computer + cloud core + redstone + glass pane, shapeless). The overlay also shows while holding a Link Card or a mainframe/uplink/relay/bridge item (main or off hand); client config `analyzerOverlay` (default true).
- **Payload** `cccloud:analyzer_data` (S2C, JSON ≤ 256 KB): `{"dim","nodes":[{id,net,name,type,x,y,z,nr,dr,ok,hops,ps,status}],"devices":[{id,net,name,type,x,y,z,r,signal,hops,status}],"links":[{a,b,signal,kind (radio|ender|wired|fiber),used?}],"ranges":{"uplink":[r],"advanced_uplink":[r],"bridge":[r],"relay":[nodeRange,deviceRange],…}}`; `{}` clears it. Only networks where the player has `view.positions`; only the player's dimension; devices and links within 128 blocks, nodes whose device bubble reaches within 128 blocks; at most 256 elements, nearest first. Checked every 10 ticks while such an item is held, sent when it changed and at least every 2 s; one `{}` when the player stops holding it.
- **Overlay** (client tick, vanilla gizmos, always on top): range bubbles (rings: device range + node range; spheres: latitude rings with LOD by distance; off), cycled with the key "Cloud Analyzer: ranges" (unbound) or sneak + scroll with the analyzer; links coloured by signal (green, yellow below 15 %, red broken; cyan for CC cable and fiber links; faint if unused; dashed ender links); device markers by status; at most 256 shapes.
- **Placement preview** (holding a relay/uplink/bridge, looking at a block): ghost box and range circle at the placement position, a line to the node it would attach to and a billboard label (panel with a coloured border, drawn above everything, scaled with the distance and clamped to a readable size, kept on screen) "Would connect to X · 68 % · hops: 2" / "Out of range – nearest node: X, 143 blocks (range 96)". Same rules as the server's network graph (link if the distance is at most the larger range; fewest hops, then best signal; max 8 hops); a GameTest checks it against `NetworkService.checkLink`. Radio links only: a device that will sit on a CC cable or fiber is not predicted.
- **HUD line** (top centre): "Signal here: 72 % via X" or "No coverage ↗ X, 143 blocks" (arrow relative to the view direction).
- **Right-click** a cloud block with the analyzer: chat summary (status, signal, hops, route as block types, power) for networks with `view`; sneak + right-click highlights the route to the mainframe for 10 s (needs `view.positions`).
- **Jade** on uplinks, relays and bridges: signal and hops, route ("Mainframe → Cloud Relay → Cloud Uplink") and the reason when not online (`no_route`, `no_power`, `legacy_out_of_range`, `unloaded`), only for networks where the looking player has `view`; no coordinates.

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

### Cables – Implementation notes (mod side)
- **CC wired membership** (`cable/CloudWired`): mainframe (both halves), relays, uplinks and bridges expose CC's `WiredElementCapability` on every face and connect to neighbouring elements once after loading (CC cables/modems connect to us from their side). Membership is found with CC's public API only: nodes put their peripheral on the wired network (`cloud_mainframe_<8 hex of id>`, `cloud_relay_<…>` – relays got a small read-only `cloud_relay` peripheral: `getRelayType`, `getStatus`, `isServing`, `getEnergy`, `getEnergyCapacity`), bridges their bridge peripheral, uplinks nothing (no remote control of uplinks over cables). `networkChanged` tells every element which cloud node peripherals share its network and marks the network dirty, so the graph is recomputed on CC network changes only. The bus id of a CC network is `cc:<smallest node id on it>`.
- **Fiber** (`cable/FiberNetworks`): no block entities on cables; a flood fill from every loaded cloud element through fiber blocks (cable, junction, ender fiber link), loaded chunks only, at most 65 536 fiber blocks per pass. It runs when fiber blocks or elements are placed/removed/loaded/unloaded and on the 10 s safety pass. Elements are members, they do not pass fiber through. Ender Fiber Links bound (Link Card, owner/admin) to the same mainframe merge their networks across distance and dimension. Bus ids `fiber:<n>`.
- **Blockstates** (art: docs/MODELS.md): `fiber_cable` and `fiber_junction` have the booleans `north|south|east|west|up|down` (connected on that side; true only towards fiber blocks and cloud devices); `ender_fiber_link` has no properties.
- **Graph** (`NetworkGraph`, unit tested): nodes and devices carry `cables` / `fibers` (bus ids); elements sharing a bus are linked with kind `WIRED` (CC) or `FIBER` (fiber wins when both are shared), signal 1, any distance/dimension, and the radio/ender link of that pair is dropped. Routing minimises **air hops** (radio + ender; cables are free, max 8), then all hops, then the weakest link; a device with a cable to a reachable node always uses the cable. Hop FE counts air hops beyond the first only.
- **Fiber benefits** (config `network.fiberCapacityWeight` 0.5, `power.fiberPowerFactor` 0.5): routed devices attached by fiber count with the weight in the mainframe's device capacity (`capacity.used` on the mainframe node, ready for the upgrade tiers) and pay `fiberPowerFactor` of their per-device FE (rounded up per category; breakdown items carry `fiber: n`).
- **Protocol**: cable links follow the v0.7 protocol – `kind:"wired"` (never `"cable"`, the dashboard draws `wired` solid), no `dist`, never `weak`, plus `medium:"cc"|"fiber"`. Device nodes routed by cable get `via:"cable"|"fiber"`; the mainframe node gets `capacity:{devices,used,wired,fiber}`.
- **Linking** with the Link Card accepts devices that only reach the network by cable (CC or fiber), whatever the distance.
- **In-game Network tab**: cabled tree rows show a plug marker (grey = CC cable, cyan = fiber) instead of signal bars; the mini map draws cables as solid lines.
- **Recipes**: fiber cable ×8 = `SGS/PCP/SGS` (string, `#c:glass_panes`, prismarine crystals, cloud core); junction ×4 = `SFS/FRF/SFS` (stone, fiber, redstone); ender fiber link = `YNY/FXF/YNY` (ender eyes, fiber, ender relay; N = netherite ingot, or `#c:ingots/unobtainium` when AllTheModium is loaded – two conditional recipes).

### Storage card v2: auto-crafting & more (requested 2026-10-23)
Applies to the ME and RS Cloud Bridges (direct mode card and Lua).
1. **Auto-stock (level maintainer)**: each watchlist entry gets an optional `target` and `autoCraft` flag. When the count drops below `min` and the item is craftable, the bridge requests a craft of `min(target − count, batch)` (batch default 64, config max), at most one active job per item, cooldown 30 s, pauses when no CPU is free or a job fails 3× (state shown: idle / crafting / waiting for CPU / missing resources / paused). Operator role to edit. Server-side, works without a computer. Lua: `bridge.setAutoStock(name, min, target, enabled)`.
2. **Craft preview**: before starting a craft the dashboard asks for a plan: required items, what will be crafted, what is missing (AE2 `CraftingPlan` / RS2 preview APIs), time estimate if available; confirm → start. New message `craft_preview` → `craft_plan` result.
3. **Crafting CPUs / autocrafting status**: list CPUs (AE2: name, co-processors, storage, busy/idle, current job) and RS2 autocrafting monitor tasks; per job: progress, items done/total, elapsed, **cancel** (operator).
4. **Browse**: an item grid with icons (sorted by count / name / mod, filter craftable-only, favorites ★ pinned on top), infinite scroll, compact numbers; fluids tab (and AE2 chemicals if present via addons – skip if not available).
5. **Craftables list**: everything with a pattern, searchable, one-tap craft with preview.
6. **Item details**: count history chart (uses long-term stats: 24 h sparkline), craftable yes/no, autostock config, watch, mod tag, recent jobs.
7. **Notifications**: optional alert when a craft finishes or fails (per job "notify me" toggle), via the push targets of v0.5 D when available.
8. Not in this step (security review first): exporting items out of the network into the world.

#### Storage card v2 – protocol (mod side)
The storage behind a card: a linked bridge section, an uplink in direct mode (Storage template), or – in Lua – the bridge bound to the widget `id`. Every request is answered once. Errors:
- `unavailable` (no bridge / not loaded);
- `offline`;
- `unknown_item`;
- `invalid`;
- `rate_limited` (10 storage requests per second per socket);
- the permission errors.

**`{"t":"storage","reqId","uplinkId","id?","op",…}` → `{"t":"storage_result","reqId","ok","error?","data":{…}}`.** Operations and permissions:
- `browse` (`storage.search`):
  - Request: `{"sort":"count"|"name"|"mod","desc":true,"filter":"all"|"craftable"|"favorites","q":"","offset":0,"limit":100}`, with `limit` ≤ 200.
  - Response: `{"rows":[{"name","displayName","count","craftable","favorite"}],"total","offset"}`.
  - Favorites are pinned on top in every sort.
- `craftables` (`storage.search`): `{"q","offset","limit"}` → the same rows, for every item with a pattern (count may be 0).
- `fluids` (`storage.search`): `{"offset","limit"}` → `{"rows":[{"name","displayName","amount"}],"total"}`, amounts in mB.
- `item` (`storage.search`): `{"name"}` → a detail document with these fields:
  - `name`, `displayName`, `mod`, `count`, `craftable`, `favorite`;
  - `watch: {"min","target","autoCraft","state","failures"}|null`;
  - `jobs: [recent jobs of this item, see below]`;
  - `history: [numbers|null]` (24 h of the count as 5-minute averages, oldest first, `null` for gaps; only for watched and favourite items, limited by the retention).
- `cpus` (`storage.search`) → a document with two lists:
  - `"cpus":[{"id","name","coProcessors","storage","busy","job":{"name","displayName","count","progress","elapsedMs"}|null}]` (AE2; empty for RS2);
  - `"tasks":[{"id","name","displayName","count","progress","own":bool}]` (every running job of the network; `own` = started through this bridge).
- `cancel` (`storage.craft`): `{"jobId"}` → `{}`. `jobId` is a bridge job id (from the jobs widget or `item`) or a network task/CPU id from `cpus`.
- `favorite` (`act.buttons`): `{"name","on":bool}` → `{}`. Stored with the bridge, at most 64.
- `autostock` (`storage.autostock`): `{"name","min","target","autoCraft":bool}` → `{}`.
  - `target` ≥ `min`, both 0..2^31−1.
  - The item is added to the watchlist if needed.
  - `min:0` together with `autoCraft:false` turns it off.

**Job rows** (in `item` and in the jobs widget value): `{"jobId","name","displayName","count","status":"queued"|"running"|"waiting"|"done"|"failed"|"cancelled","progress","error?","notify":bool,"source":"dashboard"|"lua"|"autostock"|"rule"}`. `waiting` (with `error:"no_cpu"`) is only used by auto-stock, see below.

**Craft preview: `{"t":"craft_preview","reqId","uplinkId","id?","name","count"}` → `{"t":"craft_plan","reqId","ok","error?","plan":{…}}`** (`storage.craft`).
- The plan has these fields:
  - `name`, `count`;
  - `missing` (bool), `bytes` (AE2 storage needed, else null);
  - `entries:[{"name","displayName","stored","toCraft","missing"}]`, where `stored` = taken from storage;
  - `cpu:{"available":n,"enough":bool}|null`;
  - `timeMs: null` (no mod exposes it yet).
- The preview starts nothing.
- After confirmation, the dashboard sends the usual craft action: `{"op":"craft","name","count","notify":bool}` (`notify` is new and optional; it alerts when the job finishes or fails).

**Auto-stock (level maintainer), server side**
- Every second, each watchlist entry with `autoCraft` and a pattern is checked. When count < `min`, the bridge requests `min(target − count, batch)`:
  - `batch` is config `autoStockBatch`, default 64, max 4096;
  - at most one active job per item;
  - a 30 s cooldown after each request.
- States, shown in `watch.state` and the watchlist widget rows (`state`, `target`, `autoCraft`):
  - `idle`;
  - `crafting`;
  - `waiting_cpu` (no free CPU: retried after the cooldown, no failure counted). The request is a job row with `status:"waiting"`, `error:"no_cpu"` – one per item: a retry plans the same job again (with the current amount), and the row disappears when the item is stocked by other means or its auto-stock is switched off. Nothing is planned while AE2 reports no idle CPU at all; a plan that finds no suitable CPU when it is submitted waits too. Same logic for both bridges (`BridgeCore`); RS2 has no CPUs, so its jobs never wait. Manual crafts (dashboard, Lua, rules) still fail with `no_cpu`;
  - `missing` (missing resources: counted as a failure);
  - `paused` (3 failures in a row; editing the entry resets it).
- Jobs are started with the user `autostock`.
- Lua: `bridge.setAutoStock(name, min, target, enabled)` → true | nil, error.

**Notifications:** a job with `notify` raises `{"t":"alert"}` when done (`info`) or failed (`warn`): "Crafted 64× Iron Ingot" / "Crafting failed: …". Push targets (v0.5 D) get the same.

### Cached sections of unloaded uplinks/bridges (mod side, implemented)
- When the chunk of a linked uplink or bridge unloads, its card stays on the dashboard. The `uplink` snapshot/delta carries `"loaded":false`, `"online":false`, `"lastSeen":<ms since epoch>`, and the last widgets with their last values. The dashboard shows it greyed out: "last seen 12 min ago".
- Live sections always carry `"loaded":true`.
- The mod keeps these snapshots in SavedData (`data/cccloud/sections.dat`, ≤ 64 KB per section, ≤ 256 per mainframe; since 0.10 ≤ 2048 sections and 16 MB per mainframe). They are captured on chunk unload and at server stop, published at server start, and replaced by the live snapshot as soon as the chunk loads. No chunk is ever loaded for this.
- Removed sections: breaking an uplink drops its section at once. A section whose block disappeared without the break path (`/setblock`, `/fill`, explosions or block movers that skip block-entity side effects) is dropped by `OrphanSections` once its position is loaded but holds no uplink/bridge with that id for 5 minutes (checked every 10 s over the linked sections of loaded mainframes, and for removed block entities whose mainframe is not loaded). Unloaded positions never count.
- Actions on an unloaded section get `action_result.error = "unloaded"`.
- Unlinking or breaking a section, or breaking the mainframe, drops its cached snapshot.
- The network map keeps such devices as `unloaded` with their last position.

### Mainframe removal: identity in the item, retention, recovery, misclick guard (2026-10-30)
A misclick or a griefer must not wipe a whole dashboard setup.
- **Identity in the item.** A broken mainframe drops its item with the data component `cccloud:mainframe_identity` = `{id, owner}` (loot `copy_components` from the block entity's implicit components; pick-block with data does the same). The owner name is only for the tooltip ("Mainframe 1a2b3c4d · Owner: Steve"); ownership always comes from the server. Placing it restores the **same mainframe id**, so devices, accounts and roles, PIN, layout, rules, scenes, production statistics, API keys and push targets are back, and uplinks/bridges reconnect by id (they also update the position stored in their link). The block entity data that only the block had is kept server-side with the removal record: owner, dashboard switch and the linked-device list. **Upgrade cards keep dropping** as separate items (no shulker-box inventory); the FE buffer is not kept.
- **Ownership.** The restored mainframe belongs to its original owner, whoever places it; the placer gets no role from placing. Placing an item of a mainframe that currently stands somewhere is refused ("This mainframe already stands: …"); the placer's mainframe limit does not apply to a restore, and removed mainframes do not count against their owner's limit. An item whose data was purged (or from another server) places a new mainframe with a new id for the placer.
- **Retention.** Removing the block (any way: player, explosion, block mover, command) records the mainframe in `data/cccloud/removed_mainframes.dat` and deletes nothing. Config `mainframeRetentionDays` (default 7, 0 = delete at once like before): when the window ends, or with `/cccloud mainframe purge <id>`, everything is deleted (pairing code, PIN, devices, accounts/roles/shares, rules, scenes, production, API keys, push targets, cached sections, its cave slices in the map tile cache, layout) and dashboards are disconnected (4404). Expired entries are checked every minute and at server start. Meanwhile the dashboard keeps its sessions and shows the mainframe offline with `reason:"removed"` and `removedUntil` (ms) in the `state` message and snapshot (status "Removed" with the restore hint and the deletion date); all actions fail as for an unloaded mainframe.
- **Recovery without an item.** `/cccloud mainframe recover` lists the removed mainframes the player owns (all of them for server admins: `cccloud.admin.manage`, op level 2), with the deletion date and a click to recover; `/cccloud mainframe recover <id>` (8-character prefix or full id) gives an item bound to it. Owners get one item per removal and only when the break dropped none (creative, or no fitting tool); when the item may have dropped (survival break with drops, explosion, machine, command) only a server admin can hand out another one, so recovering never yields an extra mainframe. `/cccloud mainframe purge <id>`: owner or server admin.
- **Misclick guard.** Config `mainframeBreakProtection` (default true), checked in NeoForge's `BreakBlockEvent` (player breaks only): in creative the mainframe only breaks while sneaking (otherwise an action-bar hint "Sneak to break the Cloud Mainframe"); in survival only the owner, players with the admin role on it and server admins may break it (others get a hint). Claim mods (FTB Chunks, Open Parties and Claims) cancel the same event for foreign claims; this is an extra layer that also covers unclaimed land.
- GameTests (`cccloud:ingame` batch): `mainframe_break_restore`, `mainframe_break_guard`, `mainframe_retention_purge`, `mainframe_recover_command`.

### Script import (pulled forward from v0.5 E, requested 2026-10-23)
Push Lua programs from the browser straight onto a CC computer through the mainframe → uplink path.
- Dashboard: on every computer card (auto or Lua) an **"Upload script"** action: drop a `.lua` file, paste code, or pick from the **example gallery** (bundled: control-room, cloud-test, cloud-demo variants, storage dashboard; each with a short description and screenshot). Choose a file name, options: overwrite (with confirm), **run now** (in a new multishell tab if available, otherwise only when no program is running → ask), **set as startup** (writes `startup.lua` that runs the file).
- Server: write into the computer's own filesystem via CC's server-side API (the computer's writable mount), only the computer behind that uplink (auto card / Lua section owner). Path validation: relative names `[A-Za-z0-9_.-]{1,64}` (+ optional one subfolder), no `..`, no `rom/`, no disk mounts; max 256 KB per file; respect the computer's quota; atomic write.
- Run: queue the program via `shell.openTab`/`shell.run` through a small helper the mod adds to the ROM (`cloud-run <file>` listens for a `cloud_run` event) – no arbitrary command strings from the web, only the uploaded file name.
- Permissions: **admin** role (code runs on the computer), PIN if one is set, rate limit 10 uploads/min, audit log entry + in-game chat note to the owner ("<device> uploaded control-room.lua to Computer #0").
- Lua running on a CC computer is sandboxed by CC (no Java access, CC's HTTP rules unchanged), so this cannot execute code on the Minecraft server itself.
- Also list/download/delete files of that computer (admin), as the base for the later full Lua editor.

#### Script import – HTTP API (mod side, implemented)
All routes: Bearer token, role **admin** on the mainframe (`403 {"error":"not_allowed"}`; no role → `403 {"error":"no_access"}`). `<uplinkId>` is the card (section id) of a Cloud Uplink. Its computer is the CC computer the uplink is placed on. If the uplink is not on a computer, it is the one computer attached to the uplink over a modem. Errors are `{"error"}` with the status shown.
- **Paths:** up to 8 levels (`a/b/c.lua`, v0.5 E editor; ≤ 255 characters), each part `[A-Za-z0-9_.-]{1,64}`, not `.`/`..`.
  - The first part must not be `rom`, `disk`, `disk2`… (other mounts) or `.cloud` (reserved).
  - No leading `/`, no `\`.
  - Bad path → `400 invalid_path`.
- **Write PIN:** writes, deletes and run need the mainframe PIN if one is set, in the header `X-Cloud-Pin: 1234`. Errors:
  - `403 pin_required`;
  - `403 wrong_pin` + `attemptsLeft`;
  - `403 pin_locked` + `retryAfter`.
- **Write rate limit:** 10 writes/deletes/runs/moves per minute per device → `429 rate_limited`.
- **Card errors:** `404 unknown_computer` (the card is not an uplink on/with a computer, or not loaded), `409 offline` (the mainframe is offline).
- `GET /api/mainframes/<id>/computers/<uplinkId>/files`
  → `{"computer":{"id":0,"label":"Base","on":true,"advanced":true},"used":12345,"capacity":1000000,"files":[{"path":"startup.lua","size":120,"dir":false}]}`
  - Two levels deep, at most 500 entries. `?tree=true` (editor): the whole tree up to 8 levels, depth first, sorted; `"truncated":true` when the 500 entries were reached. `rom`, disk mounts and `.cloud` are never listed.
- `GET …/files/<path>` → the raw file, `text/plain; charset=utf-8`, ≤ 256 KB.
- `PUT …/files/<path>[?overwrite=true][&startup=true]` with the raw file as the body (UTF-8, ≤ 256 KB).
  - Response: `{"ok":true,"path","size","hash"}` (`hash` = the first 16 hex characters of SHA-256 of the content).
  - Missing parent folders are created.
  - `?ifMatch=<hash>` (editor saves): `409 changed` when the file on the computer no longer has that hash (or was deleted) – the editor then offers reload/overwrite. The browser computes the hash of what it loaded with the same rule.
  - `?dir=true` with an empty body creates a folder (and its parents) → `{"ok":true,"path"}`; `409 exists` when a file is in the way.
  - Errors: `409 exists` (without `overwrite=true`), `413 too_large`, `507 quota` (the computer's disk space, CC config `computerSpaceLimit`).
  - The write is atomic: a temporary file, then a rename.
  - `startup=true` also writes `startup.lua` containing `shell.run("<path>")`.
- `DELETE …/files/<path>` → `{"ok":true}`, or `404 not_found`. Folders are deleted with their content.
- `POST …/move` with `{"from","to","overwrite?"}` (editor rename/move of files and folders; permission `scripts.upload`, PIN, rate limit) → `{"ok":true}`; `404 not_found`, `409 exists` (target exists; files only overwrite with `overwrite:true`), `409 into_itself`, `409 not_a_directory`. Parents of `to` are created.
- `POST …/run` with `{"path":"control-room.lua","mode":"auto"|"event"|"reboot"}` → `{"ok":true,"started":"event"|"reboot"}`. Only an uploaded file name ever reaches the computer, never a command string. Modes:
  - `event` queues the event `cloud_run` with the path. A background "cloud" tab (`cloud-run --listen`) runs the file in a new multishell tab. That tab only exists on advanced computers that asked for it: the ROM helper `rom/autorun/cloud.lua` starts it when `.cloud/runner` exists. Without multishell, a running `cloud.ui` dashboard runs it.
  - `reboot` writes `.cloud/run_next` and restarts the computer. The autorun helper runs the file once after boot.
  - `auto`, on advanced computers: the first run writes `.cloud/runner` and uses `reboot`; later runs use `event`. On other computers `auto` gives `409 needs_reboot` (the dashboard asks, then sends `reboot`).
  - The ROM program `cloud-run <file>` runs a file by hand. Computers can switch the background tab off with `set cloud.runner false`.
- **Audit:** every write, delete and run creates an audit log entry and a server log line. The owner, if online, gets a chat note: "<device> (<player>) uploaded control-room.lua to Computer #0".

## Website & Script Store (requested 2026-10-23)

### Project website
- Static site, its own repository `enricofranke/cccloud-website` (`website/`, Astro, no client JS except islands; it reads the docs content, models, screenshots and versions from this repository and the store from `cc-cloud-scripts` at build time), same visual identity as the dashboard and blocks (CC terminal look, pixel headings, gold/cyan, dark + light).
- Pages: landing (hero with an interactive 3D view of our real block models – reuse the art pipeline's converted geometry; feature sections with real dashboard screenshots/short looping clips; how it works; compatibility; security), **Docs** (the same DE/EN content as the in-app `/docs`, single source: move the docs content into a shared folder `docs-content/` consumed by both the dashboard and the website), **Features**, **Download** (CurseForge/Modrinth/GitHub links, version matrix), **Script Store**, Changelog, FAQ. SEO meta, OpenGraph images, sitemap, i18n routes `/de/` and `/en/`.
- Hosting: decided later (GitHub Pages needs a public repo; Cloudflare Pages works with private repos). Build output must be a plain static folder.

### Script Store
- **Registry** = its own git repository [`enricofranke/cc-cloud-scripts`](https://github.com/enricofranke/cc-cloud-scripts) (until 2026-10-30 `store/` in this repo): one folder per script with `manifest.json` (id, name, description DE/EN, author, license, version, tags, required peripherals/mods, uses: redstone/peripherals/http, entry file, screenshots) + Lua files. Submissions via pull request → CI validates (manifest schema, Lua syntax check with a Lua parser, size ≤ 256 KB, no obfuscation/minified blobs, license present) → human review → merge. Every release pinned by content SHA-256.
- **Index**: CI builds `index.json` (+ per-script JSON with file hashes) published with the website (static, CORS enabled for GET).
- **Install flow** (no connection from the Minecraft server to the store needed): on the website, "Install" opens the user's own dashboard at `<dashboard>/store#install=<id>@<version>` (the dashboard URL is remembered in the website's localStorage after the first time, or entered). The dashboard fetches the script from the store index (CSP `connect-src` gets the configured store origin, config `scriptStoreUrl`, default the official site; can be disabled by server admins with `scriptStore=false`), verifies the SHA-256 against the index, **shows the full source and the manifest's "uses" list**, lets the admin pick the computer, then installs via the Script import API (admin role, PIN, audit log). Updates: the dashboard shows "update available" for installed store scripts (stored metadata per file).
- The dashboard also gets a **Store tab** that browses the same index directly.
- Safety messaging: community scripts run on your CC computer and can control what that computer controls (e.g. your reactor) – they cannot touch the Minecraft server itself. Reviewed badge for merged scripts; report button (opens a GitHub issue template).

#### Script Store – dashboard side (web, defined 2026-10-23)
**Config delivery (mod side, implemented):** server config `[store]` `scriptStore` (bool, default true) and `scriptStoreUrl` (must be `https://`, end with `/`, no user info/query/fragment; default `https://cccloud.dev/store/`, the official store). The `snapshot` has `"store":{"url":"https://…/store/","key":"<base64>"|null}` when the store is enabled and the session has `scripts.upload`, else `"store":null` (`key` = `scriptStoreKey` when it is a valid Ed25519 public key). The web server adds the store's origin (`https://host[:port]`) to the CSP `connect-src` only when enabled. No other request goes from the mod to the store.

**Public config:** `GET /api/config` (no token, no secrets) → `{"version":"0.9.0","store":{"enabled":bool,"url":"https://…/store/"|null,"key":"<base64 Ed25519 public key>"|null},"features":{"terminal","voxels","push","rules","map"}}` (the server's switches; the session's permissions still decide what a user may do).

**Installed store scripts (mod side, implemented):** the server remembers them on the computer itself, in `.cloud/store.json` (`{path: {id, version, sha256}}`, ≤ 200 entries; hidden from listings, survives browser changes and works for every admin).
- Install: `PUT …/files/<path>?overwrite=…&store=<id>@<version>` (id `[a-z0-9][a-z0-9-]{0,63}`, version `[0-9A-Za-z][0-9A-Za-z.+-]{0,31}`, else `400 invalid_store_ref`) records the file with the SHA-256 of the uploaded content. Without `store=` a later overwrite keeps the record (the file then shows as changed).
- `GET …/files` adds `"store":{"<path>":{"id","version","sha256","changed":bool,"missing":bool}}` – `changed` = the file on the computer no longer matches the installed content, `missing` = deleted. The dashboard compares `version` with the index for **Update available** and warns about `changed` before overwriting. (The browser-side `localStorage` record above is no longer needed; it may stay as a cache.)
- Delete and move keep the records in step (folders included).

**Signed index (mod side, implemented; security review L9):** the store publishes `index.json.sig` next to `index.json`: Ed25519 over the exact bytes of `index.json`, base64. Config `[store] scriptStoreKey` = the store's public key (base64 of the 32 raw bytes); the default is the official store's key `pNjw/iu9DD70yC8WPSOZd8zXAwbnC4nYLT7wQUFVbds=`. A `PUT …/files/<path>?store=<id>@<version>[&storeFile=<path in the store>]` is only written after the server (off the server thread, through the push client: https, public addresses unless the host is in `pushAllowedHosts`, no redirects, ≤ 2 MB) fetched `index.json` + `.sig` from `scriptStoreUrl`, verified the signature and found the upload's SHA-256 under that script, version and path (`storeFile` defaults to the install path). Errors (`409`): `store_not_configured` (no key), `store_disabled`, `store_unreachable`, `store_bad_signature`, `store_unknown_script`, `store_mismatch`, `invalid_store_ref`. Verified indexes are cached for 5 minutes. The dashboard should verify the signature too (WebCrypto `Ed25519`, where available) before showing a script as installable.

**Index and files (static, CORS GET):** `<url>index.json` (`format: 1`, `scripts:[{id, version, name{en,de}, description{en,de}, author{name,github?,url?}, license, tags[], uses[], requires{cccloud, computer, peripherals[], mods?[]}, entry, files:[{path,size,sha256}], sha256, size, lines, reviewed, updated?}]`) and the files at `<url>files/<id>/<version>/<path>`.

**Dashboard flow:**
- A **Store** entry in the mainframe menu (admins with `scripts.upload`): browse/search the index (cards with name, description, tags, "uses", reviewed badge), open a script → full source of every file (fetched and SHA-256-verified against the index before display) + the "uses" list + the safety note → pick a computer (any card with a computer) → install via the Script import API (`PUT …/files/<path>?overwrite=…`, PIN, then optional run/startup).
- `/store#install=<id>@<version>`: same view for that script, opened from the website; the fragment is cleared with `history.replaceState`; a version mismatch with the index is shown ("the store has 1.2.0 now").
- Installed store scripts are remembered per computer in the browser (`localStorage`, key `cccloud.store.<mainframeId>`: `{uplinkId: {path: {id, version, sha256}}}`) and show **Update available** when the index has a newer version. Files changed on the computer since install (different content) are flagged "changed locally" before overwriting.
- A failed hash check never installs anything ("the file does not match the store index – not installed").
- **Dashboard side of the signed index (web, implemented 2026-10-23):** store installs send `?store=<id>@<version>` (and `&storeFile=<path>` when the install path differs from the store path) and map the `store_*` errors to plain messages. The installed state comes from the `store` records of `GET …/files` (`changed` → "changed locally" warning before overwriting); `localStorage` stays only as a cache for older servers. The browser verifies `index.json.sig` with WebCrypto Ed25519 when it knows the key and shows a badge (signed ✓ / signature invalid → install blocked / not signed / checked by the server when the browser cannot verify). **Needs from the mod:** the store's public key as `"key"` (base64 of the 32 raw bytes, the same value as `scriptStoreKey`) in `snapshot.store` and in `GET /api/config` → `store.key`; without it the dashboard shows "checked by the server" and relies on the server's check. The dashboard reads `GET /api/config` once at start and hides terminal, rules, push, map tiles and voxels when their feature switch is off.

### Fine-grained permissions per mainframe (requested 2026-10-23)
Roles stay as presets, but every grant can be customized with individual permissions.
- Permissions (capabilities): `view`, `view.positions`, `act.buttons` (buttons/toggles/sliders/inputs), `act.redstone` (auto card redstone/power), `storage.search`, `storage.craft`, `storage.autostock`, `scripts.upload`, `scripts.run`, `scripts.delete`, `terminal` (browser terminal, v0.5 E), `layout.edit`, `rules.edit`, `rules.toggle`, `push.manage` (push targets, v0.5 D), `devices.manage`, `access.manage`, `shares.create`, `pin.manage`, `power.toggle` (dashboard on/off), `network.link` (link/unlink devices with the Link Card).
- Presets: viewer = {view, storage.search}; operator = viewer + {act.*, storage.craft, rules.toggle}; admin = everything except ownership transfer; owner = all. A grant = preset + `allow`/`deny` lists (deny wins). Team-rank grants work the same way.
- `AccessResolver` returns an effective permission set; every check uses `has(perm)` instead of role comparisons (keep the role for display). PIN-protected widgets additionally require the PIN as today.
- Web "Access" page: per player/team row a preset dropdown + "Custom" expanding to grouped toggles with short explanations ("Upload scripts – code runs on your computers and can control everything attached"). In-game Players tab: same presets, custom via the web.
- Server-level NeoForge permission nodes stay as an outer gate (e.g. a server can forbid `cccloud.scripts` entirely: new node `cccloud.feature.scripts`, default true).
- Migration: existing roles → presets without overrides. Tests for every permission check site (a test walks all actions/routes and asserts which permission each one needs).

#### Fine-grained permissions – protocol (mod side, implemented)
**Mapping of check sites to permissions.** `AccessGate` holds it, and `PermissionWalkTest` checks every route and message.
- WebSocket:
  - actions: `act.buttons`; `act.redstone` for redstone sides and the auto Computer card's power, turn-on, reboot and shutdown, and device cards' own redstone modes (widget id `redstone` or `*_redstone`, direct mode);
  - crafting through `item_search`: `storage.craft`;
  - watchlist edits: `storage.autostock`;
  - `query`: `storage.search`;
  - `layout_set`: `layout.edit`;
  - `net_unlink`: `network.link`;
  - `rule_set` and `rule_delete`: `rules.edit`;
  - `rule_enable` and `rule_run`: `rules.toggle`;
  - v0.10: `scene_set` and `scene_delete`: `rules.edit` (+ every step's permission); scene buttons: `act.buttons` + every step's permission when it runs; `production`: `view` (overview; storages also `storage.search`), `storage.search` (item), `layout.edit` (pin, unpin).
- HTTP:
  - access: `access.manage`;
  - shares: `shares.create`;
  - push targets: `push.manage`;
  - script list, read and write: `scripts.upload`; run: `scripts.run`; delete: `scripts.delete`;
  - v0.10 API key management (`/api/mainframes/<id>/apikeys`): `api.keys`; the `/api/v1` routes use API keys (scope ∩ the key owner's rights, see "Public API v1").
- In-game screen and commands:
  - dashboard switch: `power.toggle`;
  - pairing code, approvals, devices: `devices.manage`;
  - players, roles, trust: `access.manage`;
  - PIN: `pin.manage`;
  - Link Card and unlink: `network.link`.
- Network-map coordinates and restricted (server/claims/backups) cards: `view.positions`.
- Without `view` there is no access at all (WebSocket close 4403 `no_access`).
- Errors:
  - a missing permission gives `not_allowed`;
  - sessions without `act.buttons` get `read_only` instead;
  - share sessions always get `forbidden`.

**Presets:**
- viewer = {view, storage.search};
- operator = viewer + {view.positions, act.buttons, act.redstone, storage.craft, rules.toggle};
- admin = owner = all (since v0.10 including `api.keys`; the server node `cccloud.api.keys`, default true, removes it like `cccloud.feature.scripts` removes the script permissions).

**Effective set:** the union over all sources (direct grant, team rank, server-admin node) of `preset(role) ∪ allow − deny`. Deny wins within a source. `cccloud.feature.scripts=false` (PermissionAPI node, default true) removes `scripts.*` and `terminal` for that player everywhere, the owner included. Read-only devices are capped at the viewer preset.

**Snapshot and pushes:** the `snapshot` and the `{"t":"role"}` push gain `"perms":["view",…]`. `canControl` = `act.buttons`.

**Access document additions:**
- `you.perms`.
- `catalog: [{"id":"act.buttons","group":"act","presets":["operator","admin","owner"]}, …]`.
- Each `players[]` row gains `preset` (same as `role`), `allow`, `deny` and `perms` (the effective set of that row's source).
- `team.overrides: {"<rank>":{"allow":[…],"deny":[…]}}`.

**Access POST ops:**
- `set_role` takes optional `allow`/`deny` arrays (they replace the custom permissions; absent means keep them).
- New `{"op":"set_perms","player","allow":[…],"deny":[…]}` for an existing direct grant.
- `set_mapping` takes optional `allow`/`deny` for that rank.
- Unknown permission ids → `400 invalid`.
- Escalation rule: nobody can allow a permission they do not have themselves, except the owner and moderators with `cccloud.admin.manage` (→ `403 not_allowed`).

**Migration:** saved v0.6 grants are `{name, role}` and load as presets without overrides. The new `allow`/`deny` fields are optional.

## v0.8 – ATM11 compatibility wave 2 (planned 2026-10-23)
Soft dependencies in `compat/<modid>`, same rules as v0.5 A/B (zero-code cards via direct mode, existing widget types, fail soft, GameTests where headless-feasible, `-PintegrationMods` keys). Ordered by value for ATM11 players:
1. **Storage controllers as storage cards** (direct mode on the controller, same Storage card + search as the bridges where an item-handler aggregate exists): Functional Storage (storage controller / drawers), Sophisticated Storage (controller), Occultism (storage controller / dimensional storage actuator), DimStorage (chest/tank), Integrated Terminals/Tunnels networks if exposed.
2. **Advanced AE** (quantum computer: CPU listing in the jobs/CPU panel), **ExtendedAE** extras where API exists, **AE2 Infinity Booster/Network Analyzer** awareness (none needed – skip if nothing to show).
3. **Quarries & miners**: QuarryPlus, Additional Enchanted Miner – status, progress/area, energy, pause/resume (redstone or API), output fullness alert.
4. **Spawners & mob farms**: Apothic Spawners (stats, redstone control toggle), Gateways to Eternity (active gate, wave), Easy Villagers (breeder/farm status – only if trivial).
5. **Processing**: Iron Furnaces (smelting progress, fuel, upgrades), Cobblegen Galore (rate), Productive Metalworks / Silent Gear Metalworks (foundry state), Agritech Evolved & Productive Farming planters, Mystical Agriculture / Mystical Automation machines (progress, power), Railcraft Reborn boilers/steam (pressure, temperature, water).
6. **Logistics**: Super Factory Manager (manager on/off, program name, last error), Modular Routers (router on/off via redstone mode), LaserIO / Pipez (nothing to show – skip), Logistics Networks (native CC – document only).
7. **Integrated Dynamics**: a variable-display card – an uplink on a Display Panel or a variable store shows the evaluated values; optional: ID "proxy" reading.
8. **World & progression**: **Waystones** – waystones of the owner's team on the network map (name, position, dimension; operator+), **FTB Quests** – team quest progress card (chapters done / total, recent completions), **JourneyMap** (client) – optional waypoints for our network nodes via the JourneyMap API (client config), **FTB Chunks** already done.
9. **Guides**: an in-game handbook for CC: Cloud using **GuideMe** (AE2's guide system, ships with ATM11) or Patchouli/Modonomicon – content from `docs-content/`.
10. **KubeJS** bindings for pack makers: events (`CCCloudEvents.action`, `.deviceLinked`, `.alert`) and a server-side API to publish a section with widgets from KubeJS scripts (no Lua needed), guarded by the same permissions.
Each integration: a manifest entry in `docs/COMPAT.md` (mod, version tested, what the card shows, controls, tested yes/no).

### v0.8 – Implementation notes (mod side)
- Everything is a v0.5 device card (direct mode) or mainframe card (restricted section); no new message types. The manifest with every integration, its version, card, controls and test status is docs/COMPAT.md.
- **Storage cards (item 1):** `compat/card/ItemStorageCard` reads `Capabilities.Item.BLOCK` (scan every 5 s, max 32 768 slots) and answers `query` on its `item_search` widget: `DeviceCard.query` is a new hook, `CloudUplinkBlockEntity.handleQuery` asks the device card before the bridges. Rows are never craftable; a craft op answers `not_craftable`. `compat/storage` only names the block ids per mod. Config `cards.storageCards`.
- **act.redstone on device cards:** a device card's widget with id `redstone` or `*_redstone` (the machine's own redstone mode) needs `act.redstone` (`AccessGate.isRedstoneControl`); every other control `act.buttons`.
- **Mainframe cards of wave 2:** `gateways`, `waystones`, `quests` (config `world.*`), all restricted (`view.positions`). Waystones add the map overlay `"waystones":{"items":[{"name","dim","x","y","z","kind","global"?}],"truncated"}` to their snapshot, like the claims overlay.
- Skipped with reasons (docs/COMPAT.md): ExtendedAE, AE2 Infinity Booster / Network Analyzer, Integrated Terminals/Tunnels storage, Silent Gear Metalworks, Mystical Automation (generic Energy card), JourneyMap waypoints (client never gets the network graph). Items 9 (handbook) and 10 (KubeJS) are below.
- **Handbook (item 9):** data-driven GuideMe guide `cccloud:handbook` (`assets/cccloud/guideme_guides/handbook.json`), pages generated by `tools/gen_handbook.ts` from `cloud/web/src/docs/content` (the dashboard docs; the planned `docs-content/` is that folder): one page per docs page, the start page is `index.md`, German pages in `_de_de/` (GuideMe falls back to English). Conversion: MDX escaping of `<{}` outside code, `{#id}` heading ids dropped, ```` ```recipe ```` blocks → `<RecipesFor id fallbackText>` (every recipe the game loaded for the item, so a modpack's changes show; a run of blocks for the same item – a recipe and its `*_fallback` – becomes one tag; items of other mods such as the shared GuideME guide item → `<Recipe id="cccloud:<file>">`), `/docs/page#anchor` links → `page.md`, navigation title/position/icon in the frontmatter. Guide item: GuideMe's `guideme:guide` with the `guideme:guide_id` component, shapeless recipe book + Cloud Core with a `neoforge:mod_loaded guideme` condition. No Java dependency on GuideMe (the GameTest compiles against it).
- **KubeJS (item 10):** core hooks without KubeJS: `api.CloudEvents` (action accepted in `MinecraftBridge.dispatchAction`, device bound in the uplink/bridge `bind`, alert in `WebHub.publishAlert`; queued and delivered on the server thread at the end of the tick, at most 1024 pending) and `api.ScriptSections` (sections without a block: key `[a-z0-9_]{1,32}`, at most 16 per mainframe and 64 widgets; types button, toggle, slider, gauge, label, chart, progress, indicator, select, number, text, buttons; widgets via `WidgetFactory`, values via `WidgetFactory.valueFor`; stable section id from mainframe id + key, snapshot `"mode":"direct","source":"script","template":"script"`, optional `"restricted":true`; online while the mainframe is; in memory only – scripts publish again on load/`/reload`). Dashboard actions: bridge permission check (`act.buttons`) → `ActionValidator` → the script handler (may reject with a code) → stateful widgets keep the value. `compat/kubejs` registers the event group `CCCloudEvents` (server) and the binding `CCCloud` (server scripts) through `kubejs.plugins.txt`, so nothing loads without KubeJS.
- **Wave 3 – JourneyMap waypoints:** new payloads `cccloud:map_nodes_subscribe` (client → server, `bool`) and `cccloud:map_nodes` (server → client, JSON `{"nodes":[{"id","mf","mfName","name","type","dim","x","y","z"}],"truncated"}`, ≤ 512 nodes). Only networks with `view.positions` (checked on every rebuild, every 5 s; dropped permission = dropped nodes). `map/MapNodes` is the pure builder, `map/MapNodesService` the sender; the client plugin `client/compat/journeymap/CloudJourneyMapPlugin` turns them into non-persistent waypoints. Client config `journeyMapWaypoints`. **Update (visual polish):** one waypoint per node flooded the minimap and the world ("CC: Cloud – Mainframe @ 0 -60 -6: uplink", dozens of beams). Now: `journeyMapWaypointTypes` (client, default `mainframe`, `ender_relay`; opt-in `relay` = both radio relays, `uplink` = both tiers, `bridge`), filtered on the client; all waypoints in one JourneyMap group "CC: Cloud" (persistent group, temporary waypoints, so hiding the group sticks); only mainframes get the in-world marker and beam (`setShowInWorld`/`setShowBeacon` per waypoint, JourneyMap API 2.0.0), the rest is map-only; names are the card title (computer label, Lua title, machine card) or the block name (payload `name` is now empty for devices without a title, not the type id). Rules in `map/MapWaypoints` (unit tested).

### Wave 4 – Peripheral card, more ATM11 mods, dimensions (2026-10-30)
Manifest: docs/COMPAT.md "Wave 4".
- **Peripheral card (`compat/cc/PeripheralCard`):** a device card provider at `DeviceCards.FALLBACK` (−100). `DeviceCards.detect(level, pos, side, fallback)`: uplinks and probes ask with fallbacks, wired machine cards without. The peripheral is the block's `PeripheralCapability` on the uplink's face, else CC's generic peripheral (`dan200.computercraft.impl.Peripherals.getGenericPeripheral`); it is looked up again every 10 s and the card is rebuilt when it changed. `PeripheralMethods` lists the methods with their Java signatures: `@LuaFunction` methods of the peripheral class, plus CC's generic methods (`ServerContext.peripheralMethods()` → `genericMethods`, matched to the generic peripheral's `(target, name)` pairs; reflection into CC internals, erased bridge methods skipped; if CC changes them, only the block's own methods are offered). Getters: getter name (`get…`, `is…`, `list…`, `size`, `list`, `tanks`) and no Lua parameter (the generic target and `ILuaContext` are filled in); setters: `set…` with one boolean. `IComputerAccess`/`IArguments` parameters → never offered. Dynamic peripherals → counted as `unknown`. Calls: reflection on the server thread with an `ILuaContext` that runs main-thread tasks at once and answers CC's `task_complete` pull (so `executeMainThreadTask` works); a `MethodResult` that waits for anything else is an error. Pure rules (names, captions, formatting, settings) in `compat/card/PeripheralValues` (unit tested).
- **Card settings (new hook, new message):** `DeviceCard.settingsInfo()` (snapshot key `cardSettings`: `{kind, type, types, getters:[{name,caption,value:"bool"|"number"|"text"}], setters:[{name,caption}], unknown, maxValues, maxCharts, settings:{show, charts, toggle, pin}}`), `settings()`, `applySettings(json, saved)`. WebSocket `{"t":"card_settings","reqId","uplinkId","settings":{...}}` (≤ 2 KB) → `{"t":"card_settings_result","reqId","ok","error"?}`; `layout.edit` (`AccessGate.Kind.CARD_SETTINGS`), rate-limited like actions, share sessions `forbidden`; errors `invalid_settings`, `unknown_method`, `too_many`, `unsupported`, `uplink_offline`. `DashboardSection.handleCardSettings`; the uplink saves `card_settings` + `card_settings_for` (the block id) and re-applies them leniently when a card for the same block appears.
- **Mainframe cards of wave 4:** `dummies` (Training Dummy DPS meter; counts `LivingDamageEvent.Post` on `DummyEntity` itself, streaks of hits less than 5 s apart, at most 1024 tracked dummies) and `warps` (FTB Essentials, config `world.warpsCard`), both restricted.
- **Dimensions:** the network snapshot gains `"dims":{"<dim>":{"minY","maxY","ceiling"?}}` (from the server's levels, only dimensions on the map). The dashboard's dimension names and icons (`web/src/lib/dims.ts`), slice/cut ranges, and the slice default for dimensions with a roof use it; without `dims` (older mods) it falls back to vanilla ranges.

## v0.10 – Scenes, schedules, production statistics, public API (requested 2026-10-30)
Four features on top of the rules engine, the long-term statistics and the permission model. Same rules as before: deny by default, every check on the server thread with the live rights, strict schemas (unknown keys are an error), bounded sizes and rate limits, share sessions get nothing.

### Scenes (mod side)
A scene is one named button that runs several steps in order ("Night mode": lamp off, door closed, reactor slider to 3, notify). Code: `scene/` (`Scene`, `SceneCodec` = the strict schema, `SceneRunner` = pure step execution with injected checks, `SceneService` = game side, `SceneData` = SavedData `data/cccloud/scenes.dat`, `ScenesSection` = the published section).

**Scene document** (`scene_set` sends it, ≤ 4 KB; `owner`, `needsPin`, `perms`, `runs`, `lastRun` are accepted and ignored so the client may send back what it got):
```json
{"id": "0a1b2c3d", "name": "Night mode", "icon": "star", "color": "cyan", "confirm": true, "pin": false,
 "steps": [{"type": "widget", "uplinkId": "<uuid>", "widgetId": "lamp", "value": false},
           {"type": "delay", "seconds": 2},
           {"type": "redstone", "uplinkId": "<uuid>", "side": "back", "value": 0},
           {"type": "alert", "text": "Good night", "level": "info"}]}
```
- `id`: 8 hex chars, omitted for a new scene. `name` 1–32 characters (plain text). `icon` optional, from the icon set. `color` optional, `cyan|green|yellow|red|gray` (button colours). `confirm` (default false): the button asks before running. `pin` (default false): always ask for the mainframe PIN.
- `steps` 1–16: the rule action types (`widget`, `redstone`, `craft`, `alert`, `dashboard`, same fields and limits as in rules) plus `delay` {`seconds` 0.05–300}. Delays are counted in server ticks (rounded up), at most 600 s per scene in total. A scene step can not run another scene.
- **Owner:** the player who saved the scene last. Saving needs `rules.edit` **and** every permission the steps need (below); otherwise `not_allowed` with `detail` = `step <n>: <perm>`. The owner is stored with the scene (`owner` = player name in the document).

**Running.** The mainframe publishes a section **Scenes** (stable id from mainframe id + `scenes`, `"template":"scenes"`, `"source":"mainframe"`, `"mode":"direct"`, not restricted) while it is loaded and has scenes: one `button` per scene, id `s_<sceneId>`, label = name, `opts.color`, `opts.icon`, `opts.confirm`, and `opts.pin` when the scene asks for the PIN **or** any step targets a PIN-protected widget (`needsPin`). Pressing it is an ordinary action (`{"t":"action","uplinkId":"<scenes section>","id":"s_<sceneId>","value":true,"pin?"}`), so the dashboard, the Cloud Tablet, the Cloud Display (in-world clicks: only scenes without confirm/PIN, like any button) and the layout editor (move the button into any card) all work without new code. `action_result` is `ok` once the run started; errors: `busy` (this scene is already running), `too_many` (4 runs at once per mainframe), `rate_limited` (30 runs per minute per mainframe), `pin_required`/`wrong_pin`/`pin_locked`, `offline`, `unknown_widget`.
- **No escalation:** pressing needs `act.buttons` (the button). Every step is checked **when it runs**, with the live rights of the player behind the device that pressed it (read-only devices capped as always): `widget` = the widget's own action permission (`AccessGate.actionPerm`: `act.buttons`, `act.redstone`, …), `redstone` = `act.redstone`, `craft` = `storage.craft`, `dashboard` = `power.toggle`; `alert` and `delay` need nothing more. A step without its permission fails with `not_allowed`; a step on a PIN widget without a verified PIN fails with `pin_required`. Failed steps do not stop the scene (like rule actions); the run's result is the first error.
- **Scheduled runs** (rule action `scene`, below) run with the **owner's** live rights (no device cap, never with a PIN): a scene that needs the PIN cannot be used by a rule (`pin_widget` on save, `pin_required` at run time); an owner who lost a permission makes that step fail with `not_allowed`; a banned owner or one without any role makes the whole run fail with `not_allowed`.
- Steps are executed on the server thread in a tick listener: everything up to the next delay runs in the same tick, a delay parks the run (no blocking, no threads). If the mainframe goes offline or unloads during a run, the remaining steps fail with `offline` and the run ends.
- **Log:** every run goes to the scene log (last 50, stored) and the server log (`[cccloud] <player> (<device>) ran scene 'Night mode' on <mainframe>: ok`); saves and deletes go to the mainframe's audit log.

**Implementation notes:** the section is `online` while the mainframe is online and its dashboard enabled; a press goes `MinecraftBridge.dispatchAction` → `ScenesSection.press` (button validation, PIN flag) → `SceneService.start` with a `Caller` (the device; `source` dashboard, tablet or display), while `ScenesSection.handleAction` without a caller answers `unsupported` (rules use the scene action, and a rule's widget action on the Scenes section is refused when saving). Steps are executed by `RuleService.runAction`, the same code rule actions use (same validation as a human action, user = the device name). Runs and their per-step results live in memory; the scene log (`scenes.dat`) keeps the last 50 runs.

**Scenes document** (snapshot field `scenes`, delta `{"t":"scenes", …}` after every change and every step; never sent to share sessions):
`{"scenes":[<scene> + "owner":"<name>","needsPin":bool,"perms":["act.redstone",…] (what the steps need),"runs":n,"lastRun":{"by","source":"dashboard|tablet|display|rule|api","started":ms,"finished":ms|null,"steps":[{"ok":true}|{"ok":false,"error":"not_allowed"}|null (not run yet)]}|null],"log":[{"time","scene","name","by","source","result"}],"limit":n,"sectionId":"<uuid>"|null}`. Run state lives in memory; the log is stored.

**Messages** (rate limit as actions; answered with `{"t":"scene_result","reqId","ok","error?","detail?","id?"}`):
- `{"t":"scene_set","reqId","scene":{…}}` – `rules.edit` (+ every step permission); errors `invalid_scene` (+ `detail`), `too_many`, `not_allowed` (+ `detail`).
- `{"t":"scene_delete","reqId","id"}` – `rules.edit`; `unknown_scene`.
- Permission errors as everywhere (`forbidden`/`read_only`/`not_allowed`); share sessions get `forbidden`.

**Limits** (server config `[scenes]`): `scenesEnabled` (true; off = no section, `disabled` for edits), `maxScenesPerMainframe` (32, 1–64).

**Rule action** `{"type":"scene","sceneId":"0a1b2c3d"}` (≤ 10 per rule like every action): starts the scene with its owner's rights; `unknown_scene`, `pin_required`, `busy`, … as the action's result.

### Schedules (mod side)
Time-based triggers are rule triggers ("schedules" = rules whose `when` is `interval`, `schedule` or `time`); no parallel system.
- `{"type":"interval","minutes":N}` (real time, as before; at least `minIntervalMinutes`).
- **New** `{"type":"schedule","days":["mon","tue","wed","thu","fri","sat","sun"],"at":"07:30"}` – real time: on the listed days (1–7, unique) at `at` (`HH:MM`, 24 h) in the server's time zone (config `timezone`, empty = the server's system zone, an invalid zone falls back to it with a log line).
- `{"type":"time","time":0-23999}` (in-game time of day, as before). The dashboard offers presets: dawn 23000, noon 6000, dusk 12000, midnight 18000, or any in-game hour.
- **Missed runs:** the last run of every `interval`/`schedule` trigger is stored (`rules.dat`). After a server restart, at most **one** missed occurrence runs (once the mainframe is loaded and online), and only if it is younger than `missedWindowHours` (default 24); `runMissed=false` never catches up. There is no catch-up storm: one run per rule, whatever the downtime; afterwards the next occurrence is computed from now (interval: lastRun + N minutes when that is still ahead). In-game time does not pass while the server is down, so `time` triggers never catch up.
- **Unloaded mainframes:** time triggers are evaluated for every mainframe with rules, loaded or not (in-game time from the overworld clock). When an occurrence is due while the mainframe is unloaded or offline it is **skipped**: the rule log gets one entry `skipped_unloaded`/`skipped_offline` (`"skipped":true`) and the server log one line, once per outage (not every occurrence); nothing runs and nothing is caught up later.
- **Limits** (server config `[schedules]`): `maxSchedulesPerMainframe` (16, 1–128; `rule_set` → `too_many_schedules`), `minIntervalMinutes` (1, 1–1440; shorter intervals → `invalid_rule`), `runMissed` (true), `missedWindowHours` (24, 1–168), `timezone` ("").
- **Rules document additions:** every rule has `"nextRun": ms|null` (interval/schedule) and `"lastFire": ms|null`; log entries may carry `"skipped":true`; the document has `"schedules":{"count","limit","minInterval","timezone":"Europe/Berlin"}`.
- **Implementation notes:** `RuleEngine` (pure, unit tested) gets the schedule settings per evaluation (`RuleEngine.Settings`: zone, `runMissed`, window, minimum interval – a stored interval below a raised minimum runs at the minimum). The engine created at server start carries the stored last occurrences (`rules.dat` `fired`); only its **first** evaluation looks for missed occurrences, so editing a schedule later never triggers a catch-up. The catch-up waits (within the window) until the mainframe is available. An occurrence that comes due also counts as "fired" when the cooldown or a condition held the run back, and when it was skipped, so neither is caught up after a restart. Interval catch-up uses the most recent missed occurrence. `ScheduleMath` handles daylight saving: a time that does not exist moves forward by the gap, a time that exists twice counts once. Rules of unloaded mainframes are only evaluated when they have schedules (edge triggers need live values).

### Production statistics (mod side)
Code: `production/` (`RateRing`/`ProductionTracker` = pure ring buffers and rates, `EnergyTotals`, `ProductionService` = sampling and documents, `ProductionData` = pins, `ProductionSection` = the pinned charts).
- **Storage sampling:** every `sampleSeconds` (default 60) each AE2/RS storage behind a mainframe's cards (a linked bridge section or an uplink in direct mode on a bridge) is sampled from the bridge's item index snapshot – the same immutable snapshot item searches use (rebuilt at most every 10 ticks); storages are spread over the ticks (at most one per tick). The tracker keeps a reference to the previous snapshot and computes per-item deltas. Bounded memory per storage: the previous snapshot (already capped at 50 000 types by the bridge), at most `trackedItemsPerStorage` (256) items with a ring of `windowSamples` (60) deltas each (the ones with the largest movement win; idle ones are dropped), and rings for the storage totals (gained, consumed, crafted). A sample makes one pass over the changes (totals, deltas looked up per tracked item) and picks new items with a min-heap of at most `trackedItemsPerStorage` entries, so a stock change of 50 000 types allocates entries for 256 items, not for every changed one (follow-up 2026-10-30; before, every changed item got an entry with two rings before trimming). Unit tests `ProductionTrackerTest` (50 000 changed types: at most 256 entries created, the top movers kept, totals over all items; ties keep the tracked item; crafted-only items compete for a slot).
- **Rates:** `gained` = sum of positive deltas, `consumed` = sum of negative deltas (as a positive number), `net` = gained − consumed; reported **per minute**, averaged over the samples in the window (the dashboard shows per minute and per hour). `crafted` = items of finished (`done`) crafting jobs started through the bridge (dashboard, Lua, auto-stock, rules) – the only completions AE2/RS expose to us; jobs started from terminals are only visible while they run (`tasks`).
- **Energy:** every energy card (the generic FE card and its subclasses) reports stored/capacity and the mod's in/out rates (FE/t); cards without rates report the measured net flow (positive = in, negative = out, `measured:true`). Summed per mainframe; rings of in/out per sample.
- Nothing is persisted: a restart starts the windows fresh. Long-term item charts come from the long-term statistics (watched and favourite items, `history` below).
- Config `[production]`: `productionEnabled` (true), `sampleSeconds` (60, 20–600), `windowSamples` (60, 10–240), `trackedItemsPerStorage` (256, 16–1024).
- **Guards against fake deltas:** while a storage is offline its tracker pauses (the next sample only records); an empty index is never a baseline (a network that is still booting looks empty); with a cut-off index (over 50 000 types) items missing from it are not counted as consumed; a sample time that goes backwards is ignored. Only sections that are published on the dashboard are sampled, one storage per bridge (an uplink on a linked bridge does not count twice). Energy is sampled from the cards' last refresh (no extra reads).

**Protocol:** `{"t":"production","reqId","op",…}` → `{"t":"production_result","reqId","ok","error?","data":{…}}` (5 requests per second per socket; errors `disabled`, `unknown_storage`, `unknown_item`, `too_many`, `unknown_pin`, `rate_limited`, the permission errors).
- `op:"overview"` (`view`): `{"interval":60,"window":60,"since":ms|null,"storages":[…],"energy":{…}|null,"pins":[…],"pinLimit":16}`.
  - `storages` – only sections the session may see (restricted ones need `view.positions`) and only with `storage.search`: `{"uplinkId","title","bridge":"me|rs","online","samples":n,"gained","consumed","net","crafted" (per minute),"series":{"gained":[…],"consumed":[…],"crafted":[…]} (per sample, oldest first),"producers":[{"name","displayName","rate"}] (≤ 10, per minute),"consumers":[…] (≤ 10)}`.
  - `energy` – energy cards the session may see: `{"cards":[{"uplinkId","title","stored","capacity","in","out","measured"}],"stored","capacity","in","out","series":{"in":[…],"out":[…]}}` (FE and FE/t), null without cards.
  - `pins` – `[{"id","kind":"item|storage|energy","uplinkId?","name?","label"}]`.
- `op:"item","uplinkId","name"` (`storage.search`) – "what is eating my iron": `{"name","displayName","count","gained","consumed","net","crafted" (per minute),"series":{"net":[…],"gained":[…],"consumed":[…]},"jobs":[bridge job rows of that item],"tasks":[{"name","displayName","count","progress"}] (crafting running on the network now),"history":[24 h of counts as 5-minute averages|null]|null (watched/favourite items only)}`. An item without movement in the window answers zeros.
- `op:"pin","kind":"item|storage|energy","uplinkId?","name?"` (`layout.edit`; item and storage pins also `storage.search`) → `{"id"}`; `op:"unpin","id"` (`layout.edit`). At most 16 pins per mainframe (`too_many`), stored in `data/cccloud/production.dat`, written to the audit log.
- **Production section:** while a mainframe has pins it publishes a section **Production** (stable id from mainframe id + `production`, `"template":"production"`, `"source":"mainframe"`, not restricted) with one `chart` widget per pin (`p_<pinId>`, label "Iron Ingot /min" / "<storage> net /min" / "Energy in/out FE/t", the window as history, updated every sample). It can be arranged in the layout editor like any card ("Iron rate" card) and shows on tablets and displays. Like watchlist rows, a pinned rate is visible to everyone who sees the mainframe's cards; pinning it needs `storage.search`.

### Public API v1 (mod side)
For external tools (Discord bots, Home Assistant, scripts). **Off by default.** Code: `publicapi/` (`ApiKeys` = store + hashing, `ApiKeyData` = SavedData `data/cccloud/apikeys.dat`, `ApiScope`, `ApiKeysApi` = key management, `ApiV1` = the routes on the server thread, `ApiCors`, `ApiSettings`, `OpenApi` = the bundled `cccloud_api/openapi.json`) and in `web/` `ApiHandler` (Netty side) and `ApiEventStream` (the event stream).
- Server config `[api]`: `apiEnabled` (false), `apiKeysPerPlayer` (5, 1–50), `apiRequestsPerMinute` (60 per key, 1–6000), `apiIpRequestsPerMinute` (120, 1–6000), `apiMaxBody` (2048 bytes, 256–4096), `apiCorsOrigins` ([] – exact origins like `https://bot.example.org`), `apiEvents` (true), `apiStreamsPerKey` (2, 1–8; at most 32 streams in total).
- When `apiEnabled=false` the `/api/v1/…` routes do not exist (404 `not_found`, as for any unknown path) and the key management answers `disabled`.
- **Permission** `api.keys` (group `manage`; admin and owner presets) to create keys; the PermissionAPI node `cccloud.api.keys` (boolean, default true) is the server-wide gate: without it a player cannot create keys and their keys stop working.

**Keys.** `cck_` + 43 characters (32 random bytes, base64url). Only the SHA-256 is stored; the key is shown exactly once. Each key belongs to **one player and one mainframe** and has a name (1–40), **scopes**, an optional expiry (1 h, 24 h, 7 d, 30 d, 90 d or never) and an optional IP allow-list (≤ 8 entries, IPs or CIDRs, matched against the client IP after the trusted-proxy rules). At most `apiKeysPerPlayer` keys per player. Listed with created, expiry, last used time and IP (other players' keys, which moderators see, without their IP and allow-list); revocable by the player, and by players with `access.manage` on that mainframe (moderation). A key remembers the browser that created it: **revoking that browser revokes the key** (in game, `/cccloud devices revoke`, the Account page; audit line "revoked with the browser …"), so a lost phone cannot keep control through a key made with it. A browser that expires after 30 days without use does not take its keys along. Keys of a broken mainframe are deleted.
- **Scopes** (a request needs the scope **and** the key owner's live permission – scope ∩ rights, never more):
  - `read:sections` → `view` (restricted sections also `view.positions`): sections, widget values, events.
  - `read:storage` → `storage.search`: item search and item counts of storage cards.
  - `read:stats` → `view` (+ `storage.search` for storages): production statistics.
  - `action:widgets` → the widget's action permission (`act.buttons`, `act.redstone`, …): set a value / press a button.
  - `run:scenes` → `act.buttons` + every step's permission at run time (as a dashboard press).
- **Every request** re-checks the key owner's rights like a device token: banned, no `cccloud.use`, no `cccloud.api.keys`, no role on the mainframe → `403 no_access`; a revoked or unknown key, a device token, no or several Bearer values → `401 unauthorized`; expired → `401 expired`; IP not allowed → `403 ip_not_allowed`; the key lacks the route's scope → `403 missing_scope`; the owner lacks the permission → `403 not_allowed`.
- **Never through the API:** admin and access endpoints, devices, pairing/approval, key management, terminal, files and script upload, layout, rules, push, map/voxels, and PIN-protected widgets and scenes (`403 pin_required` – there is no PIN in the API on purpose: a key that leaks must not be able to reach the second factor).

**Transport.** `Authorization: Bearer cck_…` only; a key in the URL (`?key=`, `?api_key=`, `?token=`, `?access_token=`) is refused with `400 key_in_query` (and the key is not checked). Device tokens are not accepted on `/api/v1`, API keys nowhere else. JSON bodies ≤ `apiMaxBody`, strict schemas. The same Host allow-list, LAN-only rule, IP bans, security headers and connection limits as the dashboard. Rate limits per key and per IP → `429 rate_limited` with `Retry-After` (seconds). Keys are looked up by hash and compared in constant time. Every write (widget action, scene run) is written to the mainframe's audit log ("API key 'bot' (Steve) set Reactor/rods = 3") and the server log. CORS: no CORS headers unless the request's `Origin` is in `apiCorsOrigins` (then `Access-Control-Allow-Origin: <origin>`, `Vary: Origin`; `OPTIONS` preflight allows `GET, POST` and `Authorization, Content-Type`, max-age 600).

**Routes** (all JSON; `<id>` = the key's mainframe, any other mainframe → `404 not_found`):
- `GET /api/v1/openapi.json` – the OpenAPI 3 description (public while the API is enabled).
- `GET /api/v1/me` → `{"key":{"id","name","scopes","expires"},"player","mainframe":{"id","name","online","enabled"}}`.
- `GET /api/v1/mainframes/<id>/sections` (`read:sections`) → `{"sections":[{"id","title","online","loaded","template?","widgets":[{"id","type","label","value","unit?","min?","max?"}]}]}`.
- `GET /api/v1/mainframes/<id>/sections/<sectionId>` (`read:sections`) → one section.
- `POST /api/v1/mainframes/<id>/sections/<sectionId>/widgets/<widgetId>` (`action:widgets`) with `{"value": number|string|boolean}` → `{"ok":true}`; validated like a dashboard action (`invalid_value`, `not_interactive`, `pin_required`, `offline`, …); structured operations (crafting, watchlists, redstone sides) answer `400 not_supported`; scene buttons `400 use_scene_route`. Restricted sections need `view.positions`.
- `GET /api/v1/mainframes/<id>/storage/<sectionId>/items?q=iron&limit=20` (`read:storage`, ≤ 50 rows, 5 per second per key) → `{"rows":[{"name","displayName","count","craftable"}],"truncated"}`.
- `GET /api/v1/mainframes/<id>/production` (`read:stats`) → the production overview (above, without pins).
- `GET /api/v1/mainframes/<id>/scenes` (`run:scenes`) → `{"scenes":[{"id","name","needsPin","running"}]}`; `POST …/scenes/<sceneId>/run` (`run:scenes`) → `{"ok":true}` or the press errors.
- `GET /api/v1/mainframes/<id>/events` (`read:sections`, config `apiEvents`) – a read-only **Server-Sent Events** stream: `event: snapshot` (the sections), then `event: value` `{"sectionId","id","value"}`, `event: section` (a whole section), `event: removed` `{"sectionId"}`, `event: state` `{"online","enabled"}`; a comment line every 20 s. Restricted sections only with `view.positions`. Rights are re-checked every 5 s (lost → the stream ends with `event: error` `{"error":"no_access"}`). At most `apiStreamsPerKey` streams per key and 32 in total (`429 too_many_streams`); slow readers are disconnected.

**Implementation notes:** `Router.Access.API_KEY` routes are handled by `web/ApiHandler` on the Netty thread (key in the URL, Bearer lookup, expiry, IP list, rate limits, body size) and then by `publicapi/ApiV1` on the server thread (live rights, scopes, the action itself). `GET /api/v1/openapi.json` and the `OPTIONS` preflight are public while the API is on. The event stream (`web/ApiEventStream`) is a hub session without a device that never takes a browser slot; it translates hub messages into events and drops everything else (rules, scenes, network, layout). Usage (last time and IP) is saved at most once a minute per key.

**Key management** (dashboard; device token, `api.keys`, 4 KB bodies):
- `GET /api/mainframes/<id>/apikeys` → `{"enabled":bool,"scopes":["read:sections",…],"limit":5,"keys":[{"id","name","player","scopes","created","expires"|null,"expired":bool,"lastUsed"|null,"lastIp"|null,"ips":[…],"own":bool}]}` (own keys; with `access.manage` every key of the mainframe). Expired keys are listed for a week, then deleted.
- Creating a key needs the base permission of each scope (`read:sections`/`read:stats` view, `read:storage` storage.search, `action:widgets`/`run:scenes` act.buttons; else `403 not_allowed`), so no key ever reaches past its creator.
- `POST /api/mainframes/<id>/apikeys` `{"op":"create","name","scopes":[…],"expiresIn":3600|86400|604800|2592000|7776000|null,"ips":[…]}` → `{"key":"cck_…","id", …}` (the only time the key is returned); `{"op":"revoke","id"}` → `{"ok":true}`. Errors `400 invalid`, `403 not_allowed`, `409 too_many`, `409 disabled`. Creating and revoking go to the audit log.

## Cloud Redstone devices (requested 2026-10-30)
"An uplink is complex if you only want to switch a lamp." Switching a lamp from the phone needed an uplink in receiver mode, a channel and a dashboard override. The Cloud Redstone devices make it **place → link with the Link Card → done**: no computer, no Lua, no channels.

### Blocks
- **Cloud Switch** `cccloud:cloud_switch` – a plate on any of the six faces (like an uplink: `facing` = direction towards the block it sits on). Powers like a lever: **strong** into the block it sits on, **weak** on every side. Mode **on/off** (0/15) or **analog** (0–15). Right-click toggles (analog: off ↔ the last level); the dashboard shows a toggle, in analog mode a slider 0–15.
- **Cloud Button** `cccloud:cloud_button` – the same plate rules; a pulse of 15 for `pulse` ticks (2–40, default 10) from a click or the dashboard; ignored while pressed (like a vanilla button). Dashboard: a button.
- **Cloud Lamp** `cccloud:cloud_lamp` – a full block whose light level is the blockstate `level` (0–15). Its own switch + brightness 1–15 from the dashboard (a toggle and a slider whose value is the emitted light; slider 0 = off). Option **follow**: a redstone signal lights it too (the brighter of both wins). Right-click toggles.
- **Cloud Sensor** `cccloud:cloud_sensor` – a plate that reads the strongest neighbour signal (`getBestNeighborSignal`, 0–15) on every neighbour update and once on its first tick after loading (the input is not saved; a lever that stays on sends no update after a load – the same fix as the wireless-redstone transmitter). Dashboard: an indicator (on = level > 0) and a gauge 0–15.
- Blockstates: plates `facing` (6) × `powered` (switch/button: output on; sensor: input > 0) × `status` (`online|connecting|offline`, the network LED) = 36; lamp `level` 0–15. Art and models: docs/MODELS.md "Cloud Redstone devices".
- Every device has a **name** (≤ 32, plain text, default e.g. "Lamp 3f2a") and an optional **group** (≤ 32). Sneak + right-click (any click on a sensor) opens its screen: what it is, state and controls, name and group (with the groups already used on that mainframe), mode / pulse / follow, PIN and confirm, connection (mainframe, distance, range, route) and Unlink. Settings need `network.link` on the device's mainframe (anyone may change an unlinked device); operating it (on/off, level, press) needs nothing more than standing at it, like a lever. Payload `cccloud:rs_device_action {containerId, action, arg}` (actions `name|group|mode|level|power|press|pulse|follow|pin|confirm|unlink`, validated by `RsScreenActions`, unit tested), view JSON through the shared `cccloud:gui_view`.

### Cards (decision: groups → one mainframe card each, ungrouped → one automatic card per mainframe)
- The devices have no section of their own. The mainframe publishes **one card per group** (title = the group as its first device spells it; groups match case-insensitively) and **one automatic card "Redstone"** for every device without a group – one card per device would fill the dashboard, which is what groups are meant to avoid. At most 32 devices per card; more continue on "Halle (2)" (`part` 1, 2, …).
- A card is an ordinary section (`RsGroupSection`): stable id = name UUID of `cccloud:redstone:<mainframe>:<part>:<group key>`, snapshot `"mode":"direct","template":"redstone","source":"mainframe","group"`, `online` = mainframe online and enabled. So the layout editor, custom cards, share links, the Cloud Tablet, the Cloud Display, rules, scenes, history and the public API work without new message types.
- Widgets per device (validated by `WidgetFactory`, ordered by name): id `d_<first 12 hex of the device id>`; switch → `toggle` (analog: `slider` 0–15 step 1), button → `button` (color cyan; the dashboard uses the widget's colour from the layout editor instead when one is set, `withLayoutColor`, for every button widget; all eight layout colours have a button style in both themes), lamp → `toggle` + `…_b` `slider` 0–15 (the light; `opts.history="24h"`), sensor → `indicator` + `…_l` `gauge` 0–15 (`opts.history="24h"`). Every widget carries `opts.icon` (`lever|button|lamp|sensor`, new names in the shared icon set) and `opts.pin` / `opts.confirm` from the device. A card with two or more switches/lamps gets `all_on` / `all_off` buttons (green/gray; `pin`/`confirm` when any of its switchable devices asks).
- Unloaded devices stay on their card with their last state (copies in `data/cccloud/redstone_devices.dat` per mainframe, `RsDeviceData`); actions on them answer `unloaded`. While the mainframe is unloaded the cards stay on the dashboard greyed out (`loaded:false`, kept in the section cache across restarts). Breaking or unlinking a device removes it from its card; a device the mainframe no longer lists (orphan sweep, unlink from the map) is dropped; purging a removed mainframe deletes its copies. A device that arrives with its link in its block entity data (`/setblock`, a structure, a schematic paste) puts its copy on the cards on its first status pass after loading (once its mainframe is loaded), not only after its first state change.
- **Rules and scenes follow a device**: a device keeps its widget id when its group changes, so a rule or scene step that names `(old card, d_…)` is resolved to the device's current card (`RsDevices.sectionOf`, used for rule triggers, rule/scene widget actions and the scene step permission).

### Actions, permissions, limits
- A card action is validated like every widget action (`ActionValidator`: type, range, PIN) and then applied to the device on the server thread. Errors: `uplink_offline` (mainframe offline), `device_offline` (the device has no route: out of range or over capacity), `unloaded`, `rate_limited`, `not_interactive` (sensor widgets), `pin_required`.
- **Permission:** every control on a redstone card needs **`act.redstone`** (`AccessGate.actionPerm(type, mode, id, template)` returns it for `template:"redstone"`, `mode:"direct"`); viewers get `read_only`, share links `forbidden`, the public API needs `action:widgets` ∩ `act.redstone` and never reaches PIN devices (`pin_required`).
- **Rate limit:** each device takes at most `redstoneDeviceChangesPerSecond` (default 4, bursts of twice as many) changes from the dashboard, rules, scenes and the API; in-world clicks are not limited.
- **Rename / regroup from the dashboard:** `{"t":"rs_device","reqId","deviceId","name?","group?"}` (plain text ≤ 32 each, at least one, unknown keys refused) → `{"t":"rs_result","reqId","ok","error?"}`; needs **`network.link`** (`AccessGate.Kind.RS_DEVICE`); errors `unknown_node`, `unloaded` (the block holds the name), the permission errors; share sessions `forbidden`. Written to the audit log. The dashboard offers it in the network map's detail panel. `net_unlink` also unlinks a loaded redstone device.
- Linking: Link Card on the device, same rules as uplinks (`network.link` on the mainframe, `Access.mayRelink`, range/capacity check against the whole network); events `CloudEvents.DeviceLinked` with kind `redstone:<switch|button|lamp|sensor>`.

### Network, capacity, power (config)
- Network device type `REDSTONE` in `NetworkGraph`: **radio only** (no CC cable/fiber membership), range **`network.redstoneDeviceRange` = 32** blocks (uplink 48).
- Capacity: a routed redstone device counts **`network.redstoneDeviceCapacityWeight` = 0.25** of a device (uplink 1, fiber 0.5) in the mainframe's device capacity and in the over-capacity cut-off (`Capacity.overCapacity(…, redstoneWeight, …)`).
- Power: **`power.mainframePerRedstoneDeviceFE` = 1** FE/t per routed device (uplink 4); breakdown key **`redstone`** (with `count`).
- Network map: device nodes have `type` `cloud_switch|cloud_button|cloud_lamp|cloud_sensor`, `name`, `"redstone":true` and `group`; unloaded linked devices keep their type and name. The Cloud Analyzer (placement preview range `redstone`, names, route summary), Jade and the JourneyMap waypoints show them.

### Recipes (cheap, CC style; no new intermediate)
```
cloud_switch   cloud_button   cloud_sensor   cloud_lamp
  L              B              C              P
 SPS            SPS            SPS            NLN
L=minecraft:lever B=minecraft:stone_button C=minecraft:comparator S=minecraft:stone P=minecraft:prismarine_crystals
L(lamp)=minecraft:redstone_lamp N=#c:nuggets/gold
```
The Cloud Core (an ender pearl per two) would make a lamp more expensive than an uplink; prismarine crystals (the radio material of the core and the Link Card) are enough.

### Implementation notes (mod side)
Code: `rsdevice/` – `RsKind`, `RsDevice` (the dashboard copy, pure), `RsCards` (pure card builder, unit tested), `RsPlateBlock` / `RsLampBlock`, `RsDeviceBlockEntity` (one block entity type `cccloud:redstone_device` for all four), `RsDevices` (loaded devices, copies, cards; cards rebuilt at the end of a tick when a device changed and once a second, sent as value deltas when only values changed), `RsDeviceData` (SavedData), `RsGroupSection`, `RsDeviceMenu` / `RsScreens` / `RsScreenActions` and the client `client/gui/RsDeviceScreen`. Config section `[redstoneDevices]` plus the network/power keys above.

**Tests:** unit tests `RsCardsTest` (grouping, automatic card, widgets per kind, ids, chunks of 32, PIN/confirm), `RsScreenActionsTest`, `RedstoneDeviceGraphTest` (capacity weight, FE rate), `RedstoneCardPermissionTest`, the permission walk and share tests (`rs_device`); GameTests `rsdevice_switch_faces` (all six faces incl. bottom/top: strong/weak power, analog levels, clicks), `rsdevice_button_pulse`, `rsdevice_lamp_levels` (dashboard toggle/slider, follow redstone, history range), `rsdevice_sensor_load` (lever on before placing, simulated chunk reload), `rsdevice_group_cards` (case-insensitive groups, automatic card, All on, regroup, break), `rsdevice_rule_scene` (sensor triggers a rule that follows the lamp to its new card; scene dims the lamp), `rsdevice_permissions` (act.redstone, viewer/operator/deny, PIN, rate limit, link takeover), `rsdevice_capacity_power`, `rsdevice_setblock_card` (a lamp and a grouped switch placed by `/setblock` with their link are on their cards without any state change); web tests `redstone-devices.test.ts`.

## Advanced Cloud Sensor (requested 2026-10-30)
"Du hast einen Raum, in den niemand rein soll? Platzier einen Sensor – und du bekommst eine Push-Nachricht, sobald jemand
reinkommt." The **Advanced Cloud Sensor** `cccloud:advanced_cloud_sensor` detects who or what enters a zone – which
player, or which kind of mob – and reports it to the dashboard, to rules and to push notifications.

### Block, linking, network
- A Cloud Redstone plate (`RsKind.ADVANCED_SENSOR`, block `RsPlateBlock`, block entity `cccloud:redstone_device`): any of
  the six faces, `facing` × `powered` (= something matching is inside) × `status` (36 variants), hitbox
  `box(3, 3, 0, 13, 13, 3)` (10×10 plate with a gold-rimmed eye, docs/MODELS.md). Any click opens its screen (like the
  basic sensor). The player who places it is its **owner** (stored with the block; a sensor placed by `/setblock` or a
  structure uses its mainframe's owner).
- Linked with the Link Card like every redstone device (`network.link`, relink rule, range/capacity check). Network device
  type `ADVANCED_SENSOR`: radio only, range `redstoneDeviceRange` (32), capacity **`advancedSensorCapacityWeight` = 0.5**,
  power **`mainframePerAdvancedSensorFE` = 4 FE/t while armed** (breakdown key `sensors`, count = armed sensors); a
  disarmed sensor costs `mainframePerRedstoneDeviceFE` and is counted under `redstone`. Arming/disarming recomputes its
  network (`NetworkService.markDirty(mainframe)`).
- It is a redstone device in every other respect: name and group, an indicator `d_<12 hex>` ("occupied", red, icon `eye`)
  on its group's Redstone card, network map node type `advanced_cloud_sensor` (`redstone:true`, `uplinkId` = its card),
  Cloud Analyzer, Jade (plus "Armed · N inside · zone 3×3×3 in front", only for players with a role, never names),
  `net_unlink`, `rs_device` rename/regroup.

### Zone
- Size along the world axes X × Y × Z, default 3×3×3, each 1 … the server maximum (`advancedSensorMaxZoneX/Y/Z`, default
  9/5/9, hard caps 32/16/32; larger saved sizes are clamped). Placement **in front** (starts at the sensor's block and
  reaches `size` blocks out of the face it looks out of, i.e. away from the block it sits on; centred on the other two
  axes, the extra block of an even size on the positive side) or **around** (centred on the sensor). `SensorZone`.
- **Claims:** the zone must lie in the owner's claim or unclaimed land – every chunk it touches is checked with
  `XrayGuard.claimedByOther` (FTB Chunks; members and allies count as the own team, like the Cloud Probe). Not in spawn
  protection (`Level.mayInteract` at the zone point closest to the spawn; with the owner offline: the dedicated server's
  rule, the owner counted as no operator unless listed) and inside the world border (all eight corners). Checked when the
  zone is set: in game with the player who sets it, from the dashboard, rules and scenes with the owner; refused with
  `zone_claimed` / `zone_protected` (screen notice, action result). Claims are checked again every 5 s while it scans: a
  zone that another team claims later is not watched (`problem: zone_claimed` on the card, grey outline) until that
  changes. Claim mods other than FTB Chunks are not asked (as for the probe).
- **Outline:** the block entity sends its zone (min/max, watching or not) in its update tag; the client draws it with the
  vanilla gizmos from the client tick (`SensorZoneOverlay`, like the Cloud Analyzer) while the player holds an Advanced
  Cloud Sensor or a Link Card (every sensor within 48 blocks, at most 32 boxes) or has a sensor's screen open (only that
  one). Cyan = watching, grey = not (disarmed without output, or claimed).

### Detection
- **Scan** every 10 ticks at a phase from the block position (sensors spread over the ticks), at most
  **`advancedSensorScansPerTick`** (default 32) scans per server tick on the whole server (the rest wait a tick), one AABB
  entity query of the zone (`Level.getEntities`, never loads chunks: only loaded chunks are seen), at most 256 entities
  looked at and 64 tracked. Nothing is scanned while the sensor is disarmed **and** its output is off. TickCost part
  `advanced sensors` (scale benchmark: one in ten redstone devices of the benchmark bases is an Advanced Cloud Sensor).
- **Kinds** (`SensorKind`): `player` (name; spectators never; players hidden by a vanish mod as far as cheaply visible:
  entity tag `vanished`, NeoForge persistent flag `vanished`, or invisible without the invisibility effect), `hostile`
  (any `Enemy` mob), `passive` (every other `Mob`), `item` (dropped items, by the item's name – never an anvil name),
  `other` (vehicles, armor stands). Projectiles, XP orbs, markers, displays are never reported. Each with its entity type
  id and English name ("Zombie", "Villager").
- **Filter** (`SensorFilter`): mode `all` (everyone, default) / `players` / `mobs` (hostile + passive) / `hostile`;
  **ignore my team** (default on: the owner and players in one of the owner's teams – the owner's scoreboard team or any
  enabled `TeamProvider` team (FTB Teams; any rank), cached 5 s per player); **allow list** ("only these players", empty =
  all) and **deny list** ("never these players"), ≤ 16 Minecraft names each, case-insensitive. Order: mode, deny, allow
  (an allowed name is reported even from the owner's team), team.
- **Presence** (`SensorTracker`): an entity counts as gone after 2 scans in a row without it (no flicker at the edge).
  **Cooldown per entity** (`advancedSensorEntityCooldown`, default 60 s, 0 = off): an entity that comes back within the
  cooldown after its last reported entry is inside again but silent (no "entered", and no "left" when it goes again).
  **Baseline:** the first scan after loading, arming, or a new zone or filter only records who is there (no events) – a
  restart or arming never reports who is already inside.
- **Events** (`SensorLog`): "entered" / "left" with kind, type id, name and time; the last **20** are kept (saved with the
  block, older ones dropped), with a sequence number; **today** = entries since midnight in the server's schedule time
  zone (`schedules.timezone`). Disarmed: no events.
- **Redstone:** while something that passes the filter is inside and the output is on (default), 15 like a lever: strong
  into the block it sits on, weak on every side. Works disarmed and without a mainframe.

### Dashboard card
- One section per sensor (`SensorSection`), **section id = the device id** (so the map node, `net_unlink`, the orphan sweep
  and the section cache treat it like an uplink's card), title = the device's name, `"mode":"direct","template":"sensor",
  "source":"sensor"`, online = the device is online. Unloaded: greyed out with its last values (section cache, also at
  server stop); unlinked or broken: removed.
- Widgets (`SensorCards`, validated through `WidgetFactory`, `order`/`span` set): `activity` (new managed type
  **`sensor_log`**, read-only, refused from Lua), `occupied` indicator (`history:"24h"`), `armed` toggle (with the device's
  PIN/confirm), `detect` select (Everyone / Players only / Mobs only / Hostile mobs only), `notify` select (Off / Info /
  Warning / Critical), `team` and `output` toggles, `allow` / `deny` text (comma-separated names), `zone_x/y/z` numbers
  (1 … max; left out when the max is 1) and `place` select (In front / Around).
- `sensor_log` value: `{"armed","today","zone":"3×3×3 in front","problem"?,"summary":"Steve and 2 Zombies inside · 5 today",
  "inside":[{"k":"player","n":"Steve","c":1},{"k":"hostile","n":"Zombie","id":"minecraft:zombie","c":2}],"more"?,
  "events":[{"t":ms,"in":true,"k","n"?,"id"}]}` (inside grouped, ≤ 16 rows; events newest first). The dashboard renders it
  (`components/Sensor.tsx`, `lib/sensor.ts`: chips, events with relative time, "Armed"/"Disarmed", today); the tablet, the
  in-game card preview and Cloud Displays show `summary` (the tablet also the 5 newest events).
- **Permissions** (`AccessGate.actionPerm`, template `sensor`): `armed` needs **`act.redstone`**, every other control
  **`network.link`**; rate limit `redstoneDeviceChangesPerSecond` per sensor. Rules and scenes use the same checks.

### Private values (player names)
- Widgets may carry a **`publicValue`** next to `value` in a published snapshot (and `publishValue(…, value, pub)` for
  deltas): `WebHub` stores both per widget version; sessions that may see names (`WebHub.seesNames` = `view.positions`,
  i.e. operator preset and up, the same right as positions and restricted cards) get `value`, all others the public value
  – snapshots, section messages, value deltas, share links (`ShareFilter` works on the public form), Cloud Displays
  (`sectionText` is always public), the public API (`sectionSnapshots(id, names)` by the key's rights; the event stream
  like a dashboard), `InGameClients.section`. Alerts likewise: `publishAlert(…, text, level, publicText)`; the push listener
  gets `text`. `publicValue` itself is never sent. A value published without a public form is not private any more.
- The sensor's card: in `activity` players become "a player" (inside grouped as "N players", events without `n`), the
  allow/deny lists become "". **`advancedSensorPlayerNames = false`** (server): no names anywhere – not reported, not
  stored in the log, not in push; the full and the public form are the same. Rules for one player by name then never
  fire; the allow/deny lists still work (they are matched at scan time and not reported).
- In-game screen: names only for players with `view.positions` on the sensor's mainframe (unlinked: its owner); the
  lists only for players who may change them (`network.link`).

### Alerts and push
- Every reported entry (armed, alert level not Off) becomes an alert on the sensor's card (`uplinkId` = its card, level
  `info|warn|crit`, default `warn`), which the hub sends to the dashboards (toast, alert log; `crit` also a system
  notification) and `PushService` to the push targets (`alerts` event, from their `minLevel`): "Steve entered Reactor
  room" (public: "A player entered Reactor room"). Only while the sensor is online.
- **Rate limit per sensor** (`SensorAlerts`): the first entries of a scan go out at once; everything that enters during
  the next **`advancedSensorAlertCooldown`** (default 10 s) is collected (≤ 64, more counted) and sent as **one summary**
  when it is over ("3 Zombies and a Skeleton entered …", "Steve, Alex and 2 more …" – `SensorText`, at most four parts),
  which starts the next cooldown. With the per-entity cooldown a mob at the door gives one alert per minute at most.
  Disarming drops what was collected. The mainframe-wide push limit (30/min) still applies.

### Rules
- New trigger `{"type":"sensor","uplinkId":"<sensor card>","kind":"any|player|mob|hostile|passive|item|other","player"?}`
  (`kind` default `any`; `player` = a Minecraft name, only with `any`/`player`, stored as `player`). Fires when the
  sensor's log has new "entered" events that match (after the trigger last looked; the first look only records the
  position; an unloaded sensor keeps it). Cooldown and conditions as for every rule. Never while the mainframe is offline
  or the sensor disarmed (no events).
- **`{who}`** in an alert action's text becomes who it was (`SensorText.who` of the matching entries: "Steve", "Steve and
  Alex", "2 Zombies and a Cow"): with names for operators and push, "a player" for viewers and share links (the alert's
  public form). Rules without a sensor trigger and "Run now" fill in "someone". A rule run with `notify` pushes
  "Rule 'X': ok (Steve)".
- Dashboard: "an Advanced Sensor detects …" in the rule editor (sensor cards only, kind, optional player), plain-language
  summary "Reactor room detects the player Steve".

### Screen
- `RsDeviceScreen` for `advanced_sensor`: Armed (needs `act.redstone`; unlinked: anyone), inside now, today, the 5 newest
  events, zone size −/+ per axis and placement, Detect, Ignore me and my team, Redstone output, Notify, the two lists
  (edit boxes) and the zone hint; PIN/confirm (for the card's arm switch), name, group, connection as for every device.
  Actions `s_armed|s_team|s_output|s_detect|s_notify|s_place|s_allow|s_deny|s_zone` (argument ≤ 300 characters, validated
  by `RsScreenActions` and `SensorActions.screen`), settings need `network.link`.

### Config
`[advancedSensor]` `advancedSensorPlayerNames` (true), `advancedSensorMaxZoneX/Y/Z` (9/5/9), `advancedSensorEntityCooldown`
(60 s), `advancedSensorAlertCooldown` (10 s), `advancedSensorScansPerTick` (32); `[network]` `advancedSensorCapacityWeight`
(0.5); `[power]` `mainframePerAdvancedSensorFE` (4).

### Recipe (AllTheModium-free fallback like the other ATM recipes)
```
advanced_cloud_sensor (AllTheModium)   advanced_cloud_sensor_fallback (without)
 A E A                                  G E G
 O S O                                  O S O
 A C A                                  D C D
A=#c:ingots/allthemodium E=minecraft:ender_eye O=minecraft:observer S=cccloud:cloud_sensor C=cccloud:cloud_core
G=#c:ingots/gold D=#c:gems/diamond
```

### Implementation notes (mod side)
Code: `sensor/` – `SensorKind`, `SensorEntry`, `SensorFilter`, `SensorTracker`, `SensorLog`, `SensorAlerts`, `SensorText`,
`SensorSettings`, `SensorZone`, `SensorActions`, `SensorCards` (pure, unit tested), `AdvancedSensor` (game logic, owned by
`RsDeviceBlockEntity`, saved under `sensor`), `SensorSection`, `SensorZones` (client registry); client
`SensorZoneOverlay`; `WebHub` private values; `Rule.SensorTrigger`, `RuleEngine.SensorFeed/Context`.

**Tests:** unit `SensorFilterTest`, `SensorLogTest`, `SensorAlertsTest` (rate limit, summary text, plurals),
`SensorTrackerTest` (presence, cooldown, bounds, zone geometry, settings), `SensorCardsTest` (widgets, public form,
names off, permissions, actions), `SensorTriggerTest` (schema, kinds, player, `{who}`), `WebHubPrivateValuesTest`,
`AdvancedSensorGraphTest` (capacity, FE armed/disarmed); GameTests `sensor_mobs_enter` (zombie and villager: entered with
kind and type, the card, left), `sensor_team_filters` (team mate ignored, modes, deny, allow over team, vanished),
`sensor_redstone` (lamp on/off, output off, disarmed keeps the output and logs nothing), `sensor_rule_who` (`{who}` with
the player's name; rules for another player and for mobs do not run), `sensor_claims` (zone in another team's claim
refused in game, from the dashboard and on the card; claimed later: not watching), `sensor_names_hidden` (operator vs
viewer session: card and alert, display text, API form), `sensor_rate_limit` (first alert at once, one summary after the
cooldown, no new event or alert within the per-entity cooldown); web `advanced-sensor.test.ts`.

## Live recipes in the dashboard docs (requested 2026-10-30)
Modpacks (All the Mods via KubeJS, CraftTweaker, datapacks) change recipes. JEI/EMI and the in-game handbook (GuideME `<RecipeFor>`) show the recipes the server loaded, but the dashboard's `/docs` showed the defaults baked into `recipes.gen.json` – on a modpack server possibly wrong grids. Now the dashboard asks the server.

### `GET /api/recipes` (mod side)
- **Public**, decided like the docs themselves (`/docs` is a static file) and like `/api/icon` and `/api/config`: recipes are game data that JEI shows every player, no secrets. The route `RECIPES` is explicitly allow-listed in `Router`, read-only (GET only), and still behind the LAN-only rule, IP bans, the Host check and the security headers; no CORS; **60 requests per minute per IP** (`429 rate_limited`). It lists only recipes of our items and our recipe ids, nothing else of the recipe manager.
- Answer: `application/json`, `Cache-Control: no-cache` with an `ETag` (`If-None-Match` → `304`), `404 not_available` if the catalog cannot be built, `503 unavailable` if it is not there within 10 s.
```json
{"v":1,
 "recipes":[
  {"id":"cccloud:cloud_core","file":"cloud_core","status":"default","kind":"shaped","type":"minecraft:crafting_shaped","result":"cccloud:cloud_core","count":2,
   "grid":["ABA","BCB","ABA"],"key":{"A":"#c:nuggets/gold","B":"minecraft:prismarine_crystals","C":"#c:ender_pearls"}},
  {"id":"kubejs:cheap_core","status":"added","kind":"shapeless","type":"minecraft:crafting_shapeless","result":"cccloud:cloud_core","shapeless":true,
   "items":["minecraft:ender_pearl","minecraft:oak_planks|minecraft:spruce_planks"]},
  {"id":"kubejs:smelted_core","status":"added","kind":"other","type":"minecraft:smelting","result":"cccloud:quantum_core","station":"minecraft:furnace","items":["#c:ender_pearls"]}],
 "removed":["cccloud:cloud_sensor"],
 "inactive":["cccloud:cloud_probe"],
 "icons":{"#c:nuggets/gold":"minecraft:gold_nugget"},
 "names":{"mekanism:nugget_steel":"Steel Nugget"},
 "truncated":false}
```
- **Which recipes:** every recipe of the recipe manager whose id is `cccloud:*` or whose result is a `cccloud:` item (checked through the recipe manager's cached recipe-book displays, so scanning a big pack's recipes is cheap). Shaped/shapeless crafting comes with `grid` + `key` / `items` in the shape of the docs' ```` ```recipe ```` blocks (`file` = the id's path for our ids); other types with a display (smelting, stonecutting, smithing, modded types that implement `Recipe#display`) as `kind:"other"` with their inputs (`items`) and `station`; a recipe under our id without any display only with its `type`. Grids larger than 3×3 (extended crafting tables) list their distinct ingredients with `large:true`.
- **Ingredients:** an item id, a tag `#ns:path` or alternatives `a|b` (at most 8). `icons`: a representative item per tag from the server's tag contents (a `minecraft:` member when there is one, so AlmostUnified-style packs show their material). `names`: English names (server language) of the items named, at most 512, never untranslated keys.
- **Status**, compared with the mod's own recipe files – `data/cccloud/recipe/*.json` of the pack `mod/cccloud` in the server's resource manager, whatever datapacks put on top: `default` (same kind, result, count and ingredients; shapeless in any order; grids trimmed of empty outer rows/columns like vanilla), `changed` (same id, other content), `added` (no file of ours with that id). `removed` lists our files whose conditions hold but whose recipe is not loaded (a datapack file with the condition `neoforge:never`, KubeJS `remove`); `inactive` those whose `neoforge:conditions` do not hold here (the AllTheModium variants without AllTheModium, `*_fallback` and `ender_fiber_link` with it, the bridges without AE2/RS, the handbook without GuideME) – by design, not a pack change. NeoForge's built-in condition types (`always`, `never`, `not`, `and`, `or`, `mod_loaded`) are evaluated directly, other types through NeoForge's condition codec; an unreadable condition counts as holding.
- **Limits and cache:** at most 256 recipes (`truncated:true`), 16 inputs of a non-crafting recipe, 9 of a shapeless one. Built on the first request on Minecraft's background executor – the server thread only hands over the recipe manager, the resource manager and the registries, which are read-only once loaded (a reload in between replaces them; the next request builds again) – and cached until the recipe manager changes (identity check; also dropped by `OnDatapackSyncEvent` after `/reload` – which is also how KubeJS recipe changes arrive – and on server stop). A failed build is not cached (`404 not_available`, the next request tries again).
- Code: `recipes/RecipeCatalog` (cache, defaults, conditions, build), `recipes/RecipeShape` (the normalized shape from a recipe display or from a recipe JSON), `HttpHandler.recipes`, `GameBridge.recipes()`.

### Dashboard
- `/docs` fetches `/api/recipes` once per page load (`docs/live.ts`: `parseCatalog` checks shape, ids and bounds and drops what does not fit; `applyLive` merges). Each ```` ```recipe ```` block with a `file` is matched by id `cccloud:<file>`:
  - `default` → the server's grid (the same);
  - `changed` → the server's grid with the badge **Changed by this server/modpack**, the default under "Default recipe";
  - `removed` → the default greyed out with **Not craftable on this server**;
  - `inactive` → one muted line ("Cloud Probe: Needs AllTheModium – not active on this server");
  - after the last recipe of an item: the server's `added` recipes for it (**Added by this server/modpack**), and – if none of the page's recipes for that item works here – the server's working ones (the fallback probe on the start page of a server without AllTheModium). On "All recipes", added recipes for items without a docs recipe get a section "Added by this server" before "For pack makers".
- The recipes page says above the grids "Live recipes of this server. 1 changed · 2 added · 1 removed" (or "All as shipped with the mod."). Without a catalog – the dev mock, offline, an older server: anything but a valid v1 answer – the generated defaults show as before, with "The mod's default recipes – this server's live recipes could not be loaded …".
- Icons through `/api/icon` (a tag: the server's representative item, else the docs' own); names: the docs' DE/EN names, then the server's English names, then the id.

### Website, handbook, pack makers
- The website renders the same docs content without a server, so it shows the defaults, with the note "Default recipes – your modpack may change them; the in-game handbook, JEI and your dashboard show the live ones." (DE/EN) under the title of the recipes page.
- The in-game handbook shows the live recipes by itself (GuideME reads the client's recipe map).
- The recipes page ends with **For pack makers**: stable recipe ids (a generated table: id, result, when it loads), `c:` tags (unification mods such as AlmostUnified), the conditional AllTheModium recipes with their `*_fallback` variants, removing a recipe with a `neoforge:never` datapack file, a KubeJS example (`replaceInput` keeps the id → "changed"; `remove` + an own id → "not craftable" + "added"), and that the FTB Quests chapters use item tasks, never recipes.

### Tests
- Unit: `RecipeCatalogTest` (every recipe file of the mod normalizes and fits 3×3; conditions; a hand-made "server" with an overridden, a removed, an added and an inactive recipe, tags, alternatives and a shapeless recipe in another order; size bound and ETag; only `mod/cccloud` counts as default), `RecipeRouteTest` (public and read-only, ETag/304, 404 without a game, 60/min, LAN-only), the route and permission walks.
- GameTest `recipes_live_catalog`: GameTest runs (`-Dcccloud.gametest=true`) enable the datapack `testpacks/live_recipes` through `AddPackFindersEvent` (excluded from the release jar) that overrides the Cloud Lamp, removes the Cloud Sensor and adds a Cloud Core recipe in its own namespace. The test checks those three, that every other loaded recipe of ours comes out `default` (the JSON comparison agrees with the decoded recipes for all of them), that the conditional variants follow the loaded mods, the tag icons and names, the cache, and the same document over HTTP.
- Web: `live-recipes.test.ts` (parsing and hostile input, badges, merging on the recipes page and on other pages, the fallback, names and icons, the pack makers section).

## Scaling: big bases and big servers (2026-10-30)
Goal: bases with hundreds of machines per mainframe, many mainframes, many players and browsers – without lag on the
server thread, with a dashboard that opens in under 2 s, and with no view, packet or payload that can exceed a protocol
limit. Targets: a 500-device mainframe with 10 browsers costs < 1 ms per tick on average and never more than 5 ms in a
tick; memory is bounded per device and per series.

### Benchmark (`-PscaleTest`)
- `./gradlew runGameTestServer -PscaleTest --max-workers=3` runs only the GameTest `cccloud:scale_test` (its own batch,
  `test_environment` `cccloud:scaletest`); without the flag the test passes at once (normal CI is not slowed down).
- Builds above the test structure (chunks force-loaded, everything removed afterwards): one mainframe with
  `-PscaleTestBig=500` devices – 35 % uplinks with a ready-made machine card (8 widgets incl. a 300-point chart, 30 % of
  the machines never change), 35 % Lua cards with 25 widgets (gauges, labels, toggles, buttons, two 60-point charts,
  progress, indicators, select, number, text; `opts.history` 1h/24h/7d on some), 30 % Cloud Redstone devices in groups of
  10 – plus 4 relays; `-PscaleTestMainframes=10` more mainframes with `-PscaleTestDevices=100` devices each; 100 rules (84
  threshold rules on Lua gauges, 16 one-minute schedules) on the big one; Memory upgrade and Quantum Core everywhere.
- A thread stands in for the CC computers and calls `set()` like Lua does (two gauges per Lua card every tick, the rest
  every 1–10 s: about 26 000 calls per second); two redstone groups get All on/All off every 5 s.
- `-PscaleTestBrowsers=50` real WebSocket clients (`-PscaleTestBigBrowsers=10` on the big mainframe). The server is held at a
  real 20 TPS (sleeping after each tick, outside the measurement) and the hub flushes on its own 100 ms timer, as on a real
  server. 10 s idle (`-PscaleTestBaseline`), 30 s load (`-PscaleTestSeconds`).
- Measured: cccloud's server-thread time per tick by part (`TickCost`: uplinks, mainframes, redstone devices, network,
  rules, history, production, scenes, mainframe cards, redstone cards, screens, web housekeeping) as avg/p50/p95/p99/max,
  the whole tick in wall and CPU time, and `cccloudCpuEstMs` (cccloud's wall time scaled by the tick's CPU/wall ratio: on
  a busy machine the server thread is preempted, which inflates wall times); snapshot size and time per browser; bytes,
  messages and values per second per browser; the view of every in-game screen (mainframe, uplink with a Lua or machine
  card, relay, redstone device) against `OPEN_VIEW`/`MAX_VIEW`; the tablet snapshot against `InGamePayloads.MAX_DATA`;
  network recompute and link check; history memory and save encoding; heap per device; machine load.
- Writes `run/scaletest-report.json`. Fails only on functional problems (a browser disconnected, a view or a tablet
  message over its limit, no deltas); `-PscaleTestStrict=true` also checks the timing targets.
- `-PscaleTestHold=180` keeps the bases running and writes `run/scaletest-session.json`; then
  `node tools/e2e/scale-render.mjs [--chrome <Chrome binary>]` opens the big mainframe's dashboard in headless Chromium and
  measures time to the first card and to all cards, long tasks while loading and while live updates arrive, JS heap and
  DOM size. `-PscaleTestJfr=<file>` records a Java Flight Recording of the run.

### What was slow, and what changed
Measured 2026-10-30 on a 10-core Apple M-series machine shared with other work (system load 10–14 during the runs,
recorded in each report), before = main at 39ab961 with the same benchmark code, after = branch `scale`. Wall times
include preemption by other processes; `CPU est.` is cccloud's share of the server thread's CPU time (see above).

**Full benchmark** (500 + 10 × 100 devices, 1 186 sections, 100 rules, 50 browsers, ~5 500 value changes per second):

| | before | after |
|---|---|---|
| cccloud ms/tick under load, avg / p95 / max (wall) | 6.38 / 18.0 / 69.7 | 1.61 / 4.07 / 31.2 |
| cccloud ms/tick under load, CPU est. avg / p95 / max | 4.76 / 9.44 / 43.0 | 1.27 / 2.91 / 8.9 |
| cccloud ms/tick idle (browsers connected), avg (wall) | 4.74 | 0.78 |
| whole tick under load, avg / p95 | 8.42 / 22.3 ms | 2.76 / 5.44 ms |
| uplinks (block entity ticks), avg / max | 5.13 / 36.9 ms | 1.22 / 31.1 ms |
| history, redstone cards, network: max per tick | 32.9 / 12.0 / 32.3 ms | 7.3 / 2.2 / 6.1 ms |
| network recompute of all 11 networks / Link Card check (big network) | 56.9 / 14.6 ms | 6.1 / 3.1 ms (and per changed network only) |
| messages / s to a browser of the big mainframe | 5 790 | 12 |
| bytes / s to a browser of the big mainframe | 490 KiB | 129 KiB raw, ~57 KiB with compression |
| snapshot of the big mainframe: size / time to 10 browsers at once | 1.35 MB / 932 ms | 1.09 MB (112 KB compressed) / 186 ms |
| opening a Cloud Tablet on the big mainframe (server thread) | 127 ms, then 1.35 MB > 1 MB payload limit: never arrives | 0.4 ms; 367 messages, largest 310 KB |
| mainframe screen view (500 devices) | 539 KB > 256 KB: empty screen | 86 KB, network only when it changed |
| network map JSON of the big mainframe | 538 KB (2 506 links) | 278 KB (510 links) |
| dashboard (headless Chromium): first card | never – the page stayed busy (renderer > 100 % CPU for 18 min) | 0.24–0.28 s warm, 1.5 s cold |
| dashboard while live updates arrive | – | 0 % busy, 54–59 fps, 7 300 DOM nodes, 57–65 MB JS heap |
| resolving a 500-card page (layout engine, Node) | 58 s | < 0.1 s |

**Target configuration** (one mainframe with 500 devices, 10 browsers): cccloud 0.50–0.99 ms/tick on average (CPU est.
0.48–0.71 ms; before 1.86 ms / 1.53 ms), p95 1.3–2.5 ms, p99 2.8–7.9 ms; the largest single ticks were the 500-device
network recompute (6–15 ms on this machine), a burst of rules (7–12 ms when 84 thresholds are crossed in the same
second) and wall-time stalls of up to 65 ms with under 9 ms of CPU (preemption or GC on the shared machine). Before, 2 of
the 10 browsers were disconnected as "too slow"; after, none. Heap: about 36 KB per device (whole server, all mods'
share; unchanged).

The bottlenecks and the fixes:
- **Hub (`web/WebHub`).** Every changed value was its own WebSocket message (`{"t":"value",…}`, 85 bytes, 4 000 per second
  per browser); outbox operations scanned the whole outbox per publish; a new browser's snapshot (1.35 MB) was
  serialised while holding the mainframe's hub lock, so the server thread waited whenever it published to that mainframe
  (126 ms per browser); the network map was serialised on the server thread. Now:
  - one message per flush per mainframe with all changed values: `{"t":"values","s":{"<uplinkId>":{"<widgetId>":value}}}`
    (latest value per widget; charts: a number appends a point, an array replaces the history, as with `value`; restricted
    sections and share filtering apply per section). `value` is still understood by every client (dashboard, tablet,
    display, public API – the API's SSE stream still sends one `value` event per widget);
  - published sections are immutable versions (`Section` head + widget states `W`): a value update creates a new
    widget state and section version without copying JSON trees; a snapshot takes references under the lock and is
    serialised outside it, every section's text is built once per version and shared by all browsers and displays;
  - section messages (`{"t":"uplink"}`) are built at flush time from the current version, so a browser that joins between
    a publish and the next flush never gets an older section after its snapshot; deltas of flushes that happen while a
    snapshot is being serialised wait for it (no delta before the snapshot);
  - in-game sessions (Cloud Tablet) get the snapshot head and then one `uplink` message per section, serialised on a
    snapshot thread, not on the server thread (a big base's snapshot no longer exceeds the 1 MB payload limit);
  - `historySamples` reads only the sections that have widgets with `opts.history` (indexed on publish).
- **Web server.** permessage-deflate (`webCompression`, default on): the snapshot and the batched values shrink 5–10×; a
  browser may lag 4 MB / 10 s behind (the 1.3 MB snapshot no longer got a browser disconnected as "too slow" by the
  deltas right after it).
- **Server thread.** Uplinks hand changed values to the hub every other tick (staggered), sample only their charts (chart
  options read once instead of on every tick), mark their chunk unsaved every 5 s without the comparator updates of
  `setChanged`; redstone cards of many mainframes refresh spread over the ticks and are not rebuilt from unchanged
  devices; the rules document (all rules + log) goes out at most once a second however many rules run; mainframe power
  usage is recomputed when counts or browsers change; bridges are staggered. **Network:** a device loading, unloading or
  changing recomputes only its own network (`NetworkService.markDirty(mainframeId)`), the 10 s safety pass is spread over
  the ticks per mainframe; when more than three rules run in one evaluation the server log gets one summary line; the map
  JSON is only rebuilt when that network changed (topology, statuses, capacity, names)
  or once a minute, and maps of more than 200 devices leave out the links a device does not use (`unusedLinks` counts
  them; a 500-device map 538 KB → 276 KB).
- **In-game screens.** The mainframe screen gets a compact network (short node ids, x/z only, tree links only, at most
  600 devices with problems first, names cut to 32 characters, `more` = the rest; a view still over the limit goes without
  the network) and only when its content changed (`netV`; the screen keeps the
  last one). Displays read the hub's cached section text.
- **Dashboard.** The layout is resolved only when the structure changes (`MainframeConn.structure`), value deltas swap in
  new widget objects for the cards they touch (`lib/hydrate.ts`) and only those cards render again; on pages with more
  than 24 cards a card renders its widgets when it comes within 800 px of the screen; one `values` message is one signal
  write. The rules and scenes pages render on structure changes only; the storage browser stops scrolling in after 1000
  rows (search for the rest). The grid engine's `compact` moved items up one row at a time over a copy of all items
  (minutes for a 500-card page): now O(n²) with exactly the same results (randomised test against the original), and
  `freeSpot` tries only rows and columns where something ends (same results); `LayoutMigration.java` has the same change.
- **Layout limits.** A 500-card page needs more than 256 card entries, rows beyond 999 and, with every widget box saved,
  about 1 MB. Now: ≤ 2048 cards, ≤ 8192 widgets, ≤ 256 KB, y ≤ 99 999 (`LayoutValidator`, `protocol.ts`, `layout.ts`); the
  `layout_set` frame limit follows (256 KB + 1 KB). The editor leaves out the widget entries of cards whose widgets all sit
  where the automatic packing puts them, checked by resolving the sparse document again.

### Limits and memory
| What | Limit | Notes |
|---|---|---|
| Layout document | 256 KB, 2048 cards, 8192 widgets, y ≤ 99 999 | 5 saves per 10 s per mainframe, `layout.edit` |
| Section cache (unloaded sections) | 2048 sections and 16 MB per mainframe, 64 KB per section | was 256 sections |
| Long-term statistics | 2 MB per mainframe, `historyMaxTotalMB` (128) for all | a series' size is fixed at creation: 1 h 60 floats, 24 h 1 440, 7 d 3 456, 30 d 4 896 (4 bytes each) |
| Browser backlog | 1 MB write buffer + 4 MB or 10 s | then 1013 `too slow`, the browser reconnects |
| Compression | ~300 KB deflate state per open browser | inflated frames ≤ 257 KB |
| Tablet queue | 4096 messages | then a fresh snapshot |
| Mainframe screen | 600 devices (≤ ~110 KB), view ≤ 256 KB | the dashboard's Network page shows all |
| Network map | links of devices not on their route left out above 200 devices | |

Memory of the hub per section: the published section (head + one immutable state per widget, the widget definition
shared between versions) and its serialised text, about 2–4 KB per section for typical cards (a 300-point chart adds
~1.5 KB); the benchmark reports the whole heap growth per device (all of Minecraft's and CC: Cloud's share).

### Remaining limits
- Single ticks above 5 ms remain: recomputing a 500-device network (6–15 ms on the benchmark machine; it happens when a
  device of that network loads, unloads or links, and once per 10 s spread over the ticks) and a burst of many rules in
  the same second (7–12 ms). Both are per mainframe now, not for all networks at once.
- The uplink block entity tick is still the largest part of cccloud's tick cost on a base where every value changes all
  the time (about half of it is handing ~5 000 value changes per second to the hub); it scales with the number of
  changed values, not with browsers.
- A single dashboard page with hundreds of cards is a long scroll (the browser's CSS grid; Firefox caps a grid at
  10 000 rows): split big bases into pages.
- The network map (Network page, 3D view) still receives the whole network of a mainframe in the snapshot (~276 KB
  for 500 devices before compression).
- The machine card refresh runs once per second per card regardless of viewers (rules and history need the values).

## Live config: settings without a restart (2026-10-30)

Every value of `cccloud-server.toml` and `cccloud-integrations-server.toml` applies while the server runs. NeoForge
watches both files and fires `ModConfigEvent.Reloading` (on its file-watcher thread); `/cccloud config reload` re-reads
them on demand. `config/ConfigReload` compares the values with what was applied last (a snapshot taken when the server
started) on the server thread and re-applies only what was cached (`config/ConfigPlan`); the rest is read where it is
used anyway. Values that could not be applied keep their old state and stay "changed", so the next reload retries them.

### Markers in the file
- `config/MarkedBuilder` (a `ModConfigSpec.Builder`) ends every value's comment with a line `(applies live)` or
  `(restart required)`, after NeoForge's own `Default:`/`Range:` lines; `restart()` before a value marks it. The first
  value (`allowCloud`) carries a short header explaining live reload. `ConfigMarkersTest` checks both files.
- Today every value is `(applies live)`. Nothing in these files is registry or worldgen data. `runMissed` and
  `missedWindowHours` only matter when the server starts, by their nature, and are read then.

### Audit (95 values in `cccloud-server.toml`, 12 in `cccloud-integrations-server.toml`)
| Area | Values | Before | Now |
|---|---|---|---|
| `[web]` | `webEnabled`, `webBind`, `webPort`, `publicUrl`, `webAllowInternet`, `webTrustedProxies`, `webTrustCloudflareHeader`, `webCompression`, `webAllowedHosts`, `tlsCertPath`, `tlsKeyPath` (11) | cached at server start | web server reconfigured (below) |
| `[api]` | `apiRequestsPerMinute`, `apiIpRequestsPerMinute` | refreshed every 5 s, but each key's/IP's bucket kept the limit it was made with | buckets take the new limit on their next request, keeping what was used (`TokenBucket.reconfigure`) |
| `[api]` | `apiEnabled`, `apiEvents`, `apiMaxBody`, `apiCorsOrigins`, `apiStreamsPerKey`, `apiKeysPerPlayer` | applied within 5 s | at once; switching the API or its stream off ends open streams at once |
| `[upgrades]` | `baseDevices`, `baseBrowsers`, `baseRules`, `baseHistoryHours` | baked into each mainframe's published upgrade effects when it loaded | effects recomputed for every mainframe, loaded or not (`UpgradeEffects.withBase`) |
| `[redstoneDevices]` | `redstoneDeviceChangesPerSecond` | bucket per device made once | bucket follows the config on its next use |
| `[power]` | `mainframeMaxInputFE`; `relayBufferFE` & co. (input rate = buffer/50) | fixed when the block entity was made | follow the config every tick |
| `[production]` | `trackedItemsPerStorage` | only for new trackers | trackers start again with the new cap |
| `[store]` | `scriptStoreKey` | verified index cached 5 min per URL, also after a key change | cache keyed by URL and key; cleared on reload |
| `[map]` | `mapTileMaxAgeDays` | next daily sweep | sweep at once |
| `[push]` | `pushAllowedHosts` | retries used the list from the first attempt | read for every attempt |
| integrations `[cards]` | `deviceCards`, `storageCards`, `genericCardExcludes`, `mffsRequirePin` | detection-time (a shown card kept its kind and its PIN flag) | every uplink detects its device and wired machine cards again |
| everything else (63 + 8) | ranges, power rates, buffers, feature switches, limits, schedules, scenes, rules, map, voxels, terminal, teams, … | read where used (network recomputed on reload) | unchanged; network recomputed, team changes re-checked at once |

### Web server
- `web/WebSettings` is the whole `[web]` section as one value (plus a hash of the TLS files, so renewed certificates at
  the same paths are picked up by `/cccloud config reload`); `web/WebPolicy` is the rules a connection is accepted and
  served under (TLS, trusted proxies, LAN rule, Cloudflare header, Host list, compression). Every connection keeps the
  policy it was accepted with (`WebInstance.POLICY`) until it closes.
- `CloudWebServer.reconfigure`, all or nothing: build the policy like at a start (TLS loaded; failure → nothing changes);
  a new address is bound **before** the old socket closes (failure → nothing changes, the old one keeps serving; only the
  same port on an overlapping address, e.g. `0.0.0.0` → `127.0.0.1`, has to close first and binds the old address again
  if the new one fails); swap the policy for new connections; close the old socket; close the connections of older
  policies (WebSockets with 1012 "service restart", the dashboard reconnects at once), unless only compression or the
  certificate files' contents changed. The same address line and warnings as at start are logged.
- `webEnabled=false` closes the listener and every connection but keeps the hub's state; `true` starts it again. The
  rate limiter maps, pairing/share failure counters and API stream counts live in the instance and survive a reconfigure.
- `XrayGuard` (`mapSlices = auto`) treats the dashboard as internet-facing while either the file or the running server
  allows the internet, so a reload that could not be applied never turns slices on.

### Protocol
- Server → dashboard `{"t":"config"}`: a feature switch of `GET /api/config` changed; the dashboard fetches it again.

### Commands (`cccloud.command.reload`, op level 4 by default; console)
- `/cccloud config reload`: re-reads both files (invalid values corrected to their defaults in memory, as NeoForge does
  on load), applies the difference, fires `ModConfigEvent.Reloading` so NeoForge syncs the values to clients, prints and
  logs the report: changed keys (old → new), applied live, needs a restart, not applied + why.
- `/cccloud config show [key]`: read-only; a key, its name alone (`webPort`) or part of it; value, default, live/restart.
  Secrets (names with token, secret, password, private key, key path: `tlsKeyPath`) only as "(set, hidden)"/"(not set)";
  user info in URLs is masked. The same masking applies to the reload report and the log.

### Tests
Unit: `ConfigMarkersTest`, `ConfigTextTest` (secrets never printed), `ConfigPlanTest`, `ConfigReportTest`,
`TokenBucketReloadTest`, `ApiReloadTest`, `LiveConfigWebTest` (real sockets: new port bound before the old closes, a
failed bind and unreadable TLS files keep everything, new rules close old connections and apply like a fresh start,
compression alone keeps them), `StoreVerifierTest` (rotated key), `UpgradeEffectsTest` (rebase). GameTest
`config_live_reload` (environment `cccloud:config`, its own batch): edits three cached values, fires the real
`ModConfigEvent.Reloading`, checks the running web server, a loaded mainframe and the report, then `/cccloud config
reload`'s re-read restores the file's values.

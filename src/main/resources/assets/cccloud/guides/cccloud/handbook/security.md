---
# Generated from the dashboard docs by tools/gen_handbook.ts – edit cloud/web/src/docs/content instead.
navigation:
  title: "Security & hosting"
  position: 14
  icon: cccloud:quantum_core
---

# Security and hosting

The dashboard can control things in your world, so it is protected like a real web service.

## Default: LAN only

Out of the box only devices in the server's local network (and the host itself) can open the dashboard. Everyone else gets "LAN only".

```toml
[web]
internetAccess = "off"   # default: only LAN addresses may connect (in game: /cccloud internet)
```

The config file is `<world>/serverconfig/cccloud-server.toml`; changes apply without a restart (see [below](security.md)).

## Approvals and devices

- A pairing code is valid for 10 minutes and works once.
- After the code, the player who opened it must click **[Allow]** in-game (within 2 minutes).
- Every device belongs to that player. If the player loses access (untrusted, de-opped, banned), the device stops working immediately.
- Devices unused for 30 days expire.
- List and revoke devices in the mainframe screen or with commands.

## Owner, trusted players, ops

The player who placed the mainframe owns it. Owner, trusted players and ops (level 2+) can see the pairing code, approve devices, link uplinks and change settings. Others see a read-only status.

## Commands

Look at the mainframe and run:

```
/cccloud status
/cccloud devices
/cccloud revoke <device name>
/cccloud revoke all
/cccloud trust <player>
/cccloud untrust <player>
/cccloud pin set <4-8 digits>
/cccloud pin clear
```

## PIN

Critical widgets (`pin=true`) need a 4–8 digit PIN. 5 wrong tries lock that device for 5 minutes and notify the owner.

## Internet access in 2 minutes

**Recommended: CC: Cloud Connect.** One command in game (server owner, op level 4, or the server console):

```
/cccloud internet connect mybase
```

Your dashboard is then at **https://mybase.cccloud.dev** – HTTPS, from anywhere, with no port forwarding, no domain and no proxy. The server opens one connection out to the CC: Cloud relay and keeps it open; nothing changes in your router. Pick any free name (3–32 characters: a–z, 0–9 and -); `/cccloud internet connect` without a name suggests one. It applies at once, no restart. In `serverconfig/cccloud-server.toml`: `internetAccess = "connect"` and `connectName = "mybase"` in `[connect]`.

| Mode | Easy | Secure | Needs |
|---|---|---|---|
| `connect` – **recommended** | one command | yes – HTTPS, approval in game | outgoing HTTPS (port 443) |
| `off` (default) | nothing to do | yes – only your local network | – |
| `http` – easy mode | one command, one port | **no**, see below | TCP port 8787 forwarded |
| HTTPS (Caddy, Cloudflare Tunnel, own certificate) | some setup | yes | a domain, see [Advanced](#advanced) |

`/cccloud internet` shows the current mode, the address and whether it is online; `/cccloud internet off` closes internet access again.

### CC: Cloud Connect

- **It just works:** every new browser still needs **[Allow]** in game, then the controls behave like on the LAN; PIN-protected controls still ask for their PIN. Browsers through Connect count as internet addresses: wrong pairing codes and logins block an address for a while.
- **Stricter, if you want:** `internetRequirePin = true` in the server config makes every control action from the internet ask for the mainframe PIN (once, then 10 minutes per browser); mainframes without a PIN are then view-only from outside (`/cccloud pin set <digits>`).
- **Where you see it:** the mainframe screen lists the address first and shows whether Connect is online; pairing links in chat use it, so they work on a phone with mobile data too.
- **The name is yours:** the mod makes a key in `<server folder>/cccloud/connect/` and proves it to the relay; nobody else can take the name. Moving the server? Copy that folder along. A name unused for 90 days is freed; `/cccloud internet connect release` frees it at once and switches Connect off.
- **Limits:** 20 open dashboard tabs at once and 2 GB of traffic per day per server. The public API works through Connect (not with `internetRequirePin`: a key can't carry the PIN); live event streams (`/events`) don't go through it.
- **Who can see what:** HTTPS ends at Cloudflare, where the relay runs. The relay passes your dashboard traffic on and could technically read it; it is run by the CC: Cloud author, stores nothing and keeps no request logs. The dashboard pages always come from the relay (the official files of your mod version), never from a Minecraft server, so nobody can put a fake login page on a cccloud.dev address. Report abuse on the CC: Cloud Discord or GitHub; names used for phishing are switched off. If you don't want to trust the relay, use `/cccloud internet off` and one of the [advanced](#advanced) setups.

### Plain HTTP – easy mode

```
/cccloud internet http
/cccloud internet http mybase.duckdns.org
```

The chat answers with the **link** to open, the **port** to forward in your router and **how to test it** (a phone with Wi-Fi off). The same in the config: `internetAccess = "http"` (optionally `internetDomain = "mybase.duckdns.org"` for a fixed name). It is not encrypted – prefer Connect.

### Plain HTTP: what can happen

> **Warning:** Plain HTTP is not encrypted: anyone on the network path (the same Wi-Fi, a hotspot, your provider) can read your login and PIN and then use the dashboard like you.

It just works: controls behave like on the LAN, PIN-protected controls still ask for their PIN. What stays in place: new browsers need **[Allow]** in game, a browser unused for 24 hours is logged out when it comes from the internet (`publicTokenIdleHours`), wrong pairing codes and logins block an address for a while, share links stay read-only and API keys can't control anything over plain HTTP.

Stricter: `internetRequirePin = true` in the server config makes every control action from the internet ask for the mainframe PIN (once, then 10 minutes per browser); mainframes without a PIN are then view-only from outside. Safer: HTTPS – [CC: Cloud Connect](#connect) or one of the [advanced](#advanced) setups.

## Advanced: your own HTTPS setup

These setups use the advanced `[web]` keys and `internetAccess = "off"`.

### Caddy reverse proxy (recommended)

```toml
[web]
webBind = "127.0.0.1"
webAllowInternet = true
webTrustedProxies = ["127.0.0.1"]
publicUrl = "https://dash.example.com"
```

```
dash.example.com {
    reverse_proxy 127.0.0.1:8787
}
```

Caddy gets a certificate automatically and forwards WebSockets.

### Cloudflare Tunnel (no open port)

Same `[web]` settings as for Caddy, then:

```bash
cloudflared tunnel login
cloudflared tunnel create cccloud
cloudflared tunnel route dns cccloud dash.example.com
cloudflared tunnel run cccloud
```

```yaml
tunnel: cccloud
credentials-file: /root/.cloudflared/<tunnel-id>.json
ingress:
  - hostname: dash.example.com
    service: http://127.0.0.1:8787
  - service: http_status:404
```

### TLS in the mod

```toml
[web]
webAllowInternet = true
tlsCertPath = "/etc/letsencrypt/live/mc.example.com/fullchain.pem"
tlsKeyPath = "/srv/mc/privkey-pkcs8.pem"
publicUrl = "https://mc.example.com:8787"
```

Convert the Let's Encrypt key first: `openssl pkcs8 -topk8 -nocrypt -in privkey.pem -out privkey-pkcs8.pem`. If the files cannot be loaded the dashboard does not start – it never falls back to plain HTTP silently.

### Port opened by hand

Older setups: `webAllowInternet = true` with `publicUrl = "http://mc.example.com:8787"` opens the port without the protections of `http` mode. Prefer `/cccloud internet http`.

## Item icons on a dedicated server

The dashboard shows real item icons. The best ones come from the game itself: every player with CC: Cloud renders icons in the background (a few minutes for a big pack, without costing FPS), and the server keeps them in `<world>/cccloud/icons`.

So that a fresh server has icons before anyone joins, it also draws the simple ones itself right after start, on one low-priority background thread:

- **Drawn by the server:** flat items (ingots, tools, most modded items) and plain blocks (stone, ores, logs, storage blocks, machines with a cube model).
- **Left for the players:** chests, shields, heads and other special models, slabs, stairs, fences and other shapes, 3D machine models. They show a letter tile until a player with the mod joins.

Players' renders replace the server's later; they look exactly like the inventory. In All the Mods 11 the server draws about 27,000 of 38,500 items on its own (29,000 with vanilla textures), with about 10 seconds of CPU time on one core, never on the server's main thread, so ticks are not affected.

**Vanilla items** need Minecraft's textures, and a dedicated server has none (the server jar ships no textures). Both ways to get them are your choice:

```toml
[icons]
renderIconsOnServer = true      # default: draw flat items and plain blocks at server start
downloadVanillaAssets = false   # true: get Minecraft's client jar from Mojang once, for vanilla icons
vanillaAssetsJar = ""           # or: the path to a client jar you own
```

- `downloadVanillaAssets = true` downloads the official Minecraft client jar of exactly your server's version from Mojang's servers, once (about 40 MB, checked against Mojang's SHA-1, kept in `<server>/cccloud/cache`). It is only read locally for textures; nothing from it is passed on – browsers only get the 32×32 icons. By turning it on you agree to Mojang's terms (the [Minecraft EULA](https://www.minecraft.net/eula)).
- `vanillaAssetsJar` points to a client jar you already have, e.g. `.minecraft/versions/26.1.2/26.1.2.jar`.

Without either, vanilla items get their icons when the first player with the mod joins; the server log says so once at start. The settings are in the `[icons]` section of `<world>/serverconfig/cccloud-server.toml` and apply while the server runs: after a change it looks for missing icons again (so vanilla items are drawn right after you allow the download). In singleplayer and on LAN the host's game renders every icon, so the server renderer stays off there.

`/cccloud icons status` shows how many icons come from players, how many from the server and how many are missing, and how far the server renderer is. `/cccloud icons rebuild` draws everything again, `/cccloud icons clear` deletes all icons.

## Big servers

CC: Cloud is built for big bases: hundreds of machines on one mainframe, many mainframes and many browsers. Most of it needs no settings; this is what a server owner can do.

- **Pages:** the dashboard only draws the cards near the screen, but a page with 500 cards is still a long scroll. Split big bases into pages (layout editor, or `opts.page` in Lua); up to about 100 cards per page is comfortable. Layouts of up to 2048 cards can be saved.
- **Compression:** keep `webCompression = true` (the default). Browsers get the snapshot and the updates compressed (5–10× smaller); it costs a little CPU on the web server's own threads, never on the server thread. Reverse proxies (Caddy, nginx, Cloudflare) pass it through.
- **Browsers:** each browser of a mainframe gets all its updates. A 500-device mainframe where everything changes all the time sends about 130 KB/s per browser (about 60 KB/s compressed). The Bandwidth upgrade (or `baseBrowsers`) limits browsers per mainframe; share links count separately.
- **Long-term statistics:** each mainframe keeps at most 2 MB of history; `historyMaxTotalMB` (default 128) caps all mainframes together. Every series has a fixed size from the start (1 h ≈ 240 B, 24 h ≈ 5.6 KB, 7 days ≈ 14 KB, 30 days ≈ 20 KB).
- **Map tiles on disk:** the 2D map caches its tiles in `<world>/cccloud/maptiles`. Tiles nobody drew or looked at for 30 days are deleted (`mapTileMaxAgeDays`, 0 keeps them), and purging a mainframe deletes its cave slices.
- **In-game screens:** the mainframe screen lists the first 600 devices (problems first) and gets the network only when it changed; the dashboard's Network page shows every device. Big network maps leave out the links a device does not use.
- **Slow browsers:** a browser that falls more than 4 MB or 10 seconds behind is disconnected and reconnects with a fresh snapshot; nothing piles up on the server.
- **Advanced Cloud Sensors:** each looks at its zone twice a second with one bounded entity query, spread over the ticks; `advancedSensorScansPerTick` (default 32) caps the scans per tick on the whole server, and a disarmed sensor without redstone output does not scan at all.
- **Measuring:** `/neoforge tps` or spark show the server's tick time. CC: Cloud's own cost for a 500-device mainframe with 10 browsers is about 0.5–1 ms per tick (developers: `./gradlew runGameTestServer -PscaleTest` measures it).

## Changing settings without a restart

Every setting in `cccloud-server.toml` and `cccloud-integrations-server.toml` applies while the server runs. Edit the file and save it: NeoForge notices the change (within a few seconds) and CC: Cloud applies it. Or run, as an op (permission `cccloud.command.reload`):

```
/cccloud config reload     # read both files again, apply, list what changed
/cccloud config show       # all values (secrets only as "set")
/cccloud config show webPort
```

`config reload` prints which values changed, what was applied live and what would need a restart (the last line of each setting's comment in the file says which; at the moment every setting applies live). It also picks up renewed TLS certificate files at the same paths and retries a dashboard that could not start. The same report goes to the server log.

What happens to the dashboard:

- **`[web]` (port, address, TLS, proxies, internet access, allowed hosts):** the web server restarts on its own, without the game. A new port or address is opened **before** the old one closes; if it cannot be opened (port in use, no permission), nothing changes, the old one keeps running and the log and `config reload` say why. Unreadable TLS files keep the old settings too – a reload never falls back to plain HTTP. Open dashboards reconnect by themselves; after a port, address or TLS change open the new address (the mainframe screen and `/cccloud status` show it).
- **Internet access:** switching `webAllowInternet` on behaves exactly like a server start with it (same warnings in the log, e.g. for plain HTTP); switching it off closes every connection at once, and only LAN browsers get back in.
- **Feature switches** (API, push, rules, scenes, terminal, voxels, production statistics, Script Store): apply to the next action; open dashboards update their menus.
- **Limits** (API rate limits, Cloud Switch changes per second, upgrade base limits, history memory, buffers): apply at once. A lowered limit counts what was already used; a raised one gives nothing back. Browsers already open over a lowered browser limit stay connected (like after removing a Bandwidth upgrade).
- **Ranges and power:** every network is recomputed within a second.
- **Item icons** (`[icons]`): the server opens the vanilla textures again (downloading them once if you just allowed it) and draws the icons that are still missing; switching the server renderer off stops it, the icons it drew stay.

A file with a syntax error is replaced by NeoForge with the defaults (it keeps a backup next to it) – the defaults are LAN only, so nothing opens up by accident.

## What the server logs

Every pairing, approval, revoke and dashboard action appears as `[cccloud] …` in the server log.


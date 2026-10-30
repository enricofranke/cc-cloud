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
webAllowInternet = false   # default: only LAN addresses may connect
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

## Internet access

> **Warning:** Plain HTTP over the internet sends your access token and PIN unencrypted. Use HTTPS (Caddy, Cloudflare Tunnel or TLS in the mod).

Set `webAllowInternet = true` and pick one of the setups below. The dashboard shows a warning when it runs over plain HTTP from a public address.

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

### Port opened directly

Only for quick tests: set `webAllowInternet = true`, `publicUrl = "http://mc.example.com:8787"` and open TCP 8787 in your host's panel.

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

A file with a syntax error is replaced by NeoForge with the defaults (it keeps a backup next to it) – the defaults are LAN only, so nothing opens up by accident.

## What the server logs

Every pairing, approval, revoke and dashboard action appears as `[cccloud] …` in the server log.


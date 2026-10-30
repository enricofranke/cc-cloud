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

The config file is `<world>/serverconfig/cccloud-server.toml`; changes to `[web]` need a server restart.

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

## What the server logs

Every pairing, approval, revoke and dashboard action appears as `[cccloud] …` in the server log.


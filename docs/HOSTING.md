# Hosting the CC: Cloud dashboard

The dashboard is served by the mod itself, on the Minecraft server (dedicated server, or the integrated
server in singleplayer/LAN). There is no external service. All settings live in the **server config**
`<world>/serverconfig/cccloud-server.toml` (created on first start; changes need a server restart for the
`[web]` section).

| Key | Default | Meaning |
|---|---|---|
| `allowCloud` | `true` | Master switch for the blocks. `false` keeps every mainframe offline. |
| `web.webEnabled` | `true` | Run the embedded HTTP/WebSocket server. |
| `web.webBind` | `0.0.0.0` | Bind address. Use `127.0.0.1` behind a reverse proxy/tunnel on the same host. |
| `web.webPort` | `8787` | TCP port. |
| `web.webAllowInternet` | `false` | `false`: only loopback and private addresses (10/8, 172.16/12, 192.168/16, 100.64/10, fc00::/7, fe80::/10) may connect, everyone else gets `403 {"error":"lan_only"}`. |
| `web.webTrustedProxies` | `[]` | IPs/CIDRs of reverse proxies. `X-Forwarded-For` is only honoured when the direct peer is listed (the right-most address that is not a listed proxy counts, so a client cannot spoof it). |
| `web.webAllowedHosts` | `[]` | Extra host names the dashboard may be opened with. IP addresses, `localhost`, `*.local`/`*.lan` names and the host of `publicUrl` always work; any other `Host` header gets `421` (protection against DNS rebinding). |
| `web.webTrustCloudflareHeader` | `false` | Use `CF-Connecting-IP` from a listed proxy. Only for Cloudflare Tunnel (cloudflared sets the header); Caddy, nginx and others pass a client-supplied `CF-Connecting-IP` through unchanged, which would let anyone fake their IP (rate limits, bans, LAN-only check). |
| `web.publicUrl` | `""` | URL shown in chat. Empty = `http://<server-ip>:<port>`. |
| `web.tlsCertPath`, `web.tlsKeyPath` | `""` | Optional HTTPS directly in the mod (PEM certificate chain + PKCS#8 PEM key). |
| `uplinks.uplinkRange` | `128` | Range of normal uplinks (advanced: unlimited, cross-dimension). |
| `uplinks.maxWidgetsPerUplink` | `32` | Widget limit per uplink (max. 32). |

Pairing works the same everywhere: right-click the mainframe (owner, trusted player or op) to see the URL
and a pairing code (10 minutes, single use), enter it in the browser, then click **[Allow]** in the chat.

## 1. LAN / singleplayer (default)

Nothing to configure. Open `http://<pc-ip>:8787` on a phone in the same Wi-Fi. If it does not load, allow
Java through the PC firewall for private networks.

## 2. Hosted server, port opened directly

Only for quick tests – without TLS, tokens and PINs travel unencrypted.

```toml
[web]
webAllowInternet = true
publicUrl = "http://mc.example.com:8787"
```

Open TCP port 8787 in the host's firewall/panel (many Minecraft hosts let you add an extra port).

## 3. Caddy reverse proxy with HTTPS (recommended)

```toml
[web]
webBind = "127.0.0.1"
webAllowInternet = true
webTrustedProxies = ["127.0.0.1"]
publicUrl = "https://dash.example.com"
```

`/etc/caddy/Caddyfile`:

```
dash.example.com {
    reverse_proxy 127.0.0.1:8787
}
```

Caddy gets a Let's Encrypt certificate automatically and proxies WebSockets out of the box. The mod checks
that the WebSocket `Origin` matches the `Host` header, so keep the default `Host` forwarding (Caddy does).

## 4. Cloudflare Tunnel (no open port)

```toml
[web]
webBind = "127.0.0.1"
webAllowInternet = true
webTrustedProxies = ["127.0.0.1"]
publicUrl = "https://dash.example.com"
```

```
cloudflared tunnel login
cloudflared tunnel create cccloud
cloudflared tunnel route dns cccloud dash.example.com
```

`~/.cloudflared/config.yml`:

```yaml
tunnel: cccloud
credentials-file: /root/.cloudflared/<tunnel-id>.json
ingress:
  - hostname: dash.example.com
    service: http://127.0.0.1:8787
  - service: http_status:404
```

Run `cloudflared tunnel run cccloud`. The real client IP arrives in `CF-Connecting-IP`; set `webTrustedProxies = ["127.0.0.1"]`
and `webTrustCloudflareHeader = true` so the mod uses it for rate limits and bans. Do **not** enable the Cloudflare
header with Caddy or other proxies: they forward a client-supplied `CF-Connecting-IP` unchanged.

## 5. TLS directly in the mod

```toml
[web]
webAllowInternet = true
tlsCertPath = "/etc/letsencrypt/live/mc.example.com/fullchain.pem"
tlsKeyPath = "/srv/mc/privkey-pkcs8.pem"
publicUrl = "https://mc.example.com:8787"
```

Convert a Let's Encrypt key to PKCS#8 first:
`openssl pkcs8 -topk8 -nocrypt -in privkey.pem -out privkey-pkcs8.pem`. If the files cannot be loaded the
dashboard does not start (it never silently falls back to plain HTTP).

## Operations

- `/cccloud status|devices|revoke <name|all>|trust <player>|untrust <player>|pin set <digits>|pin clear`
  (look at the mainframe; owner, trusted players and ops).
- Every pairing, approval, revoke and dashboard action is logged as `[cccloud] …` in the server log.
- Banned players' devices stop working; IP-banned addresses cannot connect. Devices unused for 30 days expire.
- If the port is in use, the server logs an error and keeps running without the dashboard.

## FAQ

**I paired a browser but no approval request shows up.**
The request appears in chat as `[Allow] [Deny]`, with the time left; you have about 2 minutes. If the mainframe screen is open, the request is also shown at the top of the screen. Type `/cccloud approve` to approve the newest request addressed to you; if several are waiting, it lists them so you can pick one. Type `/cccloud` on its own to see all commands.

**In singleplayer, approvals and dashboard buttons hang while I am in the pause menu.**
A singleplayer world pauses as soon as you open the ESC menu, and the dashboard server pauses with it: approvals, actions and live values wait until you return to the game. Use **Open to LAN** (ESC → Open to LAN) and the world no longer pauses in the menu. Dedicated servers never pause.

**How do I get the pairing code into the browser quickly?**
Click the code in chat to copy it. Or click **[Pair in browser]**, which opens `<dashboard>/pair#code=XXXX-XXXX` with the form already filled in. The code sits in the URL fragment, so it never reaches the server or its logs.

**Does CC: Cloud change my computers?**
Only computers you upload scripts to from the dashboard. The first "Run now" on an advanced computer restarts it once. From then on, a small background tab named "cloud" waits for further "Run now" requests. To disable this for good on a computer, run `set cloud.runner false` on it.

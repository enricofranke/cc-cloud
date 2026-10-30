# Hosting the CC: Cloud dashboard

The dashboard is served by the mod itself, on the Minecraft server (dedicated server, or the integrated
server in singleplayer/LAN). There is no external service – unless you choose CC: Cloud Connect, the optional relay
that gives the dashboard an HTTPS address without port forwarding. All settings live in the **server config**
`<world>/serverconfig/cccloud-server.toml` (created on first start). Changes apply without a restart – see
[Changing settings without a restart](#changing-settings-without-a-restart).

## Internet access in one setting

```
/cccloud internet connect mybase             # CC: Cloud Connect: https://mybase.cccloud.dev (recommended)
/cccloud internet connect                    # status, or a suggested free name
/cccloud internet connect release            # give the name back, Connect off
/cccloud internet http                       # plain HTTP, easy mode (insecure)
/cccloud internet http mybase.duckdns.org    # with your own (DynDNS) name
/cccloud internet off                        # LAN only again (default)
/cccloud internet                            # status
```

Switching needs op level 4 (or the console); the status needs level 2. The command writes `internetAccess` (and
`internetDomain`) to the server config and prints the link, the port to forward and how to test it from a phone on
mobile data. It applies at once (live config); the file works the same way.

| Mode | Easy | Secure | Needs |
|---|---|---|---|
| `connect` (**recommended**) | one command, nothing in the router | yes: HTTPS; trusts the CC: Cloud relay | outgoing HTTPS (TCP 443) |
| `off` (default) | – | LAN only | – |
| `http` | one command, one forwarded port | **no**: logins and PIN can be read on the way | – |
| your own HTTPS (sections 3–5 below) | some setup | yes | a domain, a proxy/tunnel or certificate files |

### CC: Cloud Connect (`connect`)

The server opens one outbound WebSocket to `wss://connect.cccloud.dev` and keeps it open (reconnects by itself). Browsers
open `https://<name>.cccloud.dev`: the relay serves the official dashboard of your mod version and passes the
dashboard's API calls and WebSocket through the tunnel into the mod, where they go through the same checks as on the
LAN. Nothing has to be opened in the router or the host's firewall; the dashboard port stays LAN only.

- **Name:** 3–32 characters `a–z`, `0–9`, `-` (no `--`), first come, first served. `/cccloud internet connect` without a
  name suggests one from the world name. Reserved and look-alike names (`www`, `admin`, `minecraft-login`, …) are
  refused.
- **Key:** the mod makes `<server folder>/cccloud/connect/server-key.pem` (mode 600) and `server-key.pub` once; the name
  is bound to it. **Moving the server: copy that folder.** Lost the key? Pick a new name; the old one expires after 90
  days without a connection.
- **Just works, like `http`:** new browsers need [Allow] in game as always; control from the internet needs no PIN by
  default (PIN-protected controls keep their PIN). `internetRequirePin = true` makes every control action from the
  internet need the mainframe PIN (one unlock per browser for 10 minutes; PIN-less mainframes are view-only from
  outside). Browsers through Connect count as internet addresses: stricter rate limits and the lockout after wrong
  codes apply; logins keep the usual 30 days (HTTPS).
- **Status:** `/cccloud internet`, the mainframe screen (the Connect address comes first, with its state), and pairing
  links in chat use the Connect address while it is online.
- **Limits:** 20 open dashboard tabs, 2 GB per day; public API event streams are not forwarded.
- **Dashboard versions:** the relay serves dashboards of released versions. A development build gets an "Update
  CC: Cloud" page on its Connect address; its LAN address works.
- **Trust:** HTTPS ends at Cloudflare, where the relay runs; it is operated by the mod author, stores nothing and keeps
  no request logs, but it could technically read the traffic. Details and what it can and cannot do: docs/SECURITY.md
  "CC: Cloud Connect". To avoid it, use `off` plus your own HTTPS (sections 3–5).
- **Firewall on the server:** only outgoing TCP 443 to `connect.cccloud.dev` (Cloudflare) is needed.

`http` mode just works (no PIN needed) and keeps what does not get in the way: new browsers need [Allow] in game,
tokens unused for `publicTokenIdleHours` (24) are logged out when they come from the internet, stricter rate limits,
share links stay read-only, API keys cannot control over plain HTTP, and there are warnings in the log, for ops in game
and in the dashboard. `internetRequirePin = true` adds the PIN for every control action from the internet (PIN-less
mainframes are view-only from outside). Details: [`docs/SECURITY.md`](SECURITY.md) "Internet access modes".

The other `[web]` keys below stay advanced overrides: `publicUrl` always wins, TLS files keep working in every mode,
and with `internetAccess = "off"` the keys decide everything as before (an older `webAllowInternet = true` setup keeps
working).

## All keys

| Key | Default | Meaning |
|---|---|---|
| `allowCloud` | `true` | Master switch for the blocks. `false` keeps every mainframe offline. |
| `web.internetAccess` | `"off"` | `off` (LAN only, the keys below decide), `connect` (CC: Cloud Connect, see above) or `http` (plain HTTP over the internet, easy mode with the protections above). |
| `web.internetDomain` | `""` | Host name or IP players use from the internet (shown in game, allowed as Host). Empty: the public IP. |
| `web.publicTokenIdleHours` | `24` | `http` mode: browsers unused this long are logged out when they come from the internet. |
| `web.internetRequirePin` | `false` | `http` mode, stricter: control from the internet only after the mainframe PIN (10 minutes per browser); PIN-less mainframes view-only from outside. |
| `web.webEnabled` | `true` | Run the embedded HTTP/WebSocket server. |
| `web.webBind` | `0.0.0.0` | Bind address. Use `127.0.0.1` behind a reverse proxy/tunnel on the same host. |
| `web.webPort` | `8787` | TCP port. |
| `web.webAllowInternet` | `false` | `false`: only loopback and private addresses (10/8, 172.16/12, 192.168/16, 100.64/10, fc00::/7, fe80::/10) may connect, everyone else gets `403 {"error":"lan_only"}`. |
| `web.webTrustedProxies` | `[]` | IPs/CIDRs of reverse proxies. `X-Forwarded-For` is only honoured when the direct peer is listed (the right-most address that is not a listed proxy counts, so a client cannot spoof it). |
| `web.webAllowedHosts` | `[]` | Extra host names the dashboard may be opened with. IP addresses, `localhost`, `*.local`/`*.lan` names and the host of `publicUrl` always work; any other `Host` header gets `421` (protection against DNS rebinding). |
| `web.webTrustCloudflareHeader` | `false` | Use `CF-Connecting-IP` from a listed proxy. Only for Cloudflare Tunnel (cloudflared sets the header); Caddy, nginx and others pass a client-supplied `CF-Connecting-IP` through unchanged, which would let anyone fake their IP (rate limits, bans, LAN-only check). |
| `web.publicUrl` | `""` | URL shown in chat. Empty = `http://<server-ip>:<port>`. |
| `web.tlsCertPath`, `web.tlsKeyPath` | `""` | Optional HTTPS directly in the mod (PEM certificate chain + PKCS#8 PEM key). |
| `connect.connectName` | `""` | The Connect name: `https://<name>.cccloud.dev`. |
| `connect.connectRelay` | `wss://connect.cccloud.dev` | The relay the tunnel connects to (only for a self-hosted relay). |
| `uplinks.uplinkRange` | `128` | Range of normal uplinks (advanced: unlimited, cross-dimension). |
| `uplinks.maxWidgetsPerUplink` | `32` | Widget limit per uplink (max. 32). |

Pairing works the same everywhere: right-click the mainframe (owner, trusted player or op) to see the URL
and a pairing code (10 minutes, single use), enter it in the browser, then click **[Allow]** in the chat.

## 1. LAN / singleplayer (default)

Nothing to configure. Open `http://<pc-ip>:8787` on a phone in the same Wi-Fi. If it does not load, allow
Java through the PC firewall for private networks.

## 2. Hosted server, port opened directly

Use `/cccloud internet http` (above): plain HTTP with the PIN protections. Open TCP port 8787 in the host's
firewall/panel (many Minecraft hosts let you add an extra port). The old way, `webAllowInternet = true` with
`publicUrl = "http://mc.example.com:8787"`, opens the port without those protections.

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

## Changing settings without a restart

Edit `cccloud-server.toml` (or `cccloud-integrations-server.toml`) and save it: NeoForge notices the change (within a few
seconds; the command below applies it at once) and CC: Cloud applies it. Or run, as an op with `cccloud.command.reload` (op level 4) or from the console:

```
/cccloud config reload        # re-read both files, apply, print what changed / was applied / needs a restart
/cccloud config show          # every value with its default (secrets only as "set")
/cccloud config show webPort  # one value
```

The last comment line of every setting in the file says `(applies live)` or `(restart required)`; today every setting
applies live.

- **`[web]`**: only the dashboard's web server restarts, never the game. A new port or address is bound **before** the old
  one is closed. If that fails (port in use, privileged port, unknown address) or the TLS files cannot be loaded, nothing
  of the new `[web]` section is applied and the old server keeps running; the log and the command say why, and the next
  reload tries again. Open dashboards reconnect on their own; after a port, address or TLS change open the new URL (the
  mainframe screen shows it).
- **Renewed certificates**: `/cccloud config reload` also loads renewed TLS files at the same paths (Let's Encrypt
  renewals): new connections get the new certificate, open ones keep running.
- **`webAllowInternet`**: switching it on logs the same lines and warnings as a start with it; switching it off closes
  every connection at once.
- **Limits and switches** (`[api]`, `[push]`, `[upgrades]`, `redstoneDeviceChangesPerSecond`, ...) apply at once; a lowered
  rate limit counts what was already used.
- A file NeoForge cannot parse is replaced with the defaults (a backup is kept next to it): LAN only, API off.

## Operations

- `/cccloud status|devices|revoke <name|all>|trust <player>|untrust <player>|pin set <digits>|pin clear`
  (look at the mainframe; owner, trusted players and ops).
- Every pairing, approval, revoke and dashboard action is logged as `[cccloud] …` in the server log.
- Banned players' devices stop working; IP-banned addresses cannot connect. Devices unused for 30 days expire.
- If the port is in use at start, the server logs an error and keeps running without the dashboard; free the port and run
  `/cccloud config reload` to start it without a restart.

## FAQ

**Connect says "name taken" / "reserved" / "revoked".**
Taken: another server key owns the name (moved the server? copy `<server folder>/cccloud/connect/` from the old one);
otherwise pick another name. Reserved: the name is on the relay's list. Revoked: the CC: Cloud team switched the name off
after an abuse report; contact us on Discord or GitHub.

**Connect stays "retrying".**
The server cannot reach `connect.cccloud.dev` on TCP 443 (a firewall that blocks outgoing connections, no internet,
a proxy). `/cccloud internet` shows the reason; it keeps trying by itself.

**I paired a browser but no approval request shows up.**
The request appears in chat as `[Allow] [Deny]`, with the time left; you have about 2 minutes. If the mainframe screen is open, the request is also shown at the top of the screen. Type `/cccloud approve` to approve the newest request addressed to you; if several are waiting, it lists them so you can pick one. Type `/cccloud` on its own to see all commands.

**In singleplayer, approvals and dashboard buttons hang while I am in the pause menu.**
A singleplayer world pauses as soon as you open the ESC menu, and the dashboard server pauses with it: approvals, actions and live values wait until you return to the game. Use **Open to LAN** (ESC → Open to LAN) and the world no longer pauses in the menu. Dedicated servers never pause.

**How do I get the pairing code into the browser quickly?**
Click the code in chat to copy it. Or click **[Pair in browser]**, which opens `<dashboard>/pair#code=XXXX-XXXX` with the form already filled in. The code sits in the URL fragment, so it never reaches the server or its logs.

**Does CC: Cloud change my computers?**
Only computers you upload scripts to from the dashboard. The first "Run now" on an advanced computer restarts it once. From then on, a small background tab named "cloud" waits for further "Run now" requests. To disable this for good on a computer, run `set cloud.runner false` on it.

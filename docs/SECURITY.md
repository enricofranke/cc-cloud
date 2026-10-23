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

## Attackers

1. **Other players on the server.** They may try to see or control someone else's mainframe, raise their own role, or abuse team membership.
2. **Internet scanners and random clients.** These matter as soon as the port is reachable, for example with `webAllowInternet=true` or behind a proxy.
3. **Share-link holders.** Anyone the link was forwarded to, including people who find it later in a chat log.
4. **Malicious mod clients.** Modified game clients sending crafted GUI payloads, such as screen actions or Link Card use.
5. **A stolen browser or token.** For example a lost phone.
6. **Cross-site attackers.** Web pages that try to use a victim's browser: CSRF, WebSocket hijacking, Referer leaks.

## Mitigations

### Rights: one decision point
- `AccessResolver` (in `access/`) is the only code that answers "which role does player X have on mainframe Y". The sources are:
  - the owner;
  - direct grants;
  - team ranks through a `TeamProvider`;
  - the PermissionAPI nodes `cccloud.admin.*`.

  The highest role wins.
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
- Escalation rules for the Access API and `/cccloud role`:
  - Admins can only change players whose role is below their own.
  - Admins cannot grant a role above their own.
  - The owner cannot be changed.
  - `owner` and `share` can never be granted.
  - A team can only be assigned by one of its members, or by a server moderator with `cccloud.admin.manage`.
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
  - Every HTTP route declares its access level in `Router` and is checked centrally (`RouteAuthTest` walks every route).
  - WebSocket messages other than `auth`, `auth_share` and `pong` are rejected before authentication.
  - Authentication must happen within 5 seconds.
- Size limits:
  - 2 KB WebSocket frames before authentication;
  - 8 KB per message after authentication, 64 KB only for `layout_set`;
  - 4 KB HTTP bodies.
- Parsing:
  - Strict JSON schemas; unknown fields are rejected in the v0.6 APIs.
  - Bounded numbers and lengths.
  - Ids are checked against patterns before they are used.
- Rate limits:
  - 10 actions per second per socket;
  - 5 item searches per second;
  - 5 layout saves per 10 seconds per mainframe;
  - 120 API requests per minute per IP;
  - icons: 200 per minute per IP.
- Connections are limited per IP and in total. Slow consumers are disconnected and never buffered without limit.
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

### No code execution from the web
- The browser only sends data that is validated against schemas. There is no eval, no scripting endpoint and no command passthrough.
- The v0.5 terminal and editor (admin only) will reach only the CC computer's own Lua sandbox:
  - CC's HTTP rules keep blocking private IPs;
  - file paths are canonicalised inside the computer root;
  - `..`, absolute paths, symlinks, `rom/` and disk mounts are rejected;
  - size and quota limits apply.

### Dependencies
- The mod uses only Minecraft, NeoForge, Netty and Gson.
- Integrations are soft dependencies (`compileOnly`), loaded only when the mod is present. They are reached through neutral interfaces (`TeamProvider`, `TeamSource`, `StorageAdapter`).
- The web bundle keeps a short, audited dependency list (`pnpm audit` in CI).

## Residual risks and operator advice
- Without TLS, tokens travel in plain text on the LAN. For internet access, use HTTPS: the built-in TLS, a reverse proxy, or Tailscale (docs/HOSTING.md).
- Server admins with `cccloud.admin.*` can see every mainframe by design (moderation). This is logged, but not prevented.
- Anyone a share link is forwarded to can view it until it expires or is revoked. Prefer short expiries and low `maxViewers`.
- A player who gets someone else's in-game account can approve new browsers for it. That is outside the scope of this mod.

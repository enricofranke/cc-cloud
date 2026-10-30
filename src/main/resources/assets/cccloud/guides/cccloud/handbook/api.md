---
# Generated from the dashboard docs by tools/gen_handbook.ts – edit cloud/web/src/docs/content instead.
navigation:
  title: "Public API"
  position: 13
---

# Public API

The public API lets **your own tools** read values and press buttons: a Discord bot, Home Assistant, a spreadsheet script. It is a small HTTP JSON API on the dashboard's address, with **API keys** you create in the dashboard.

> **Note:** The API is **off by default**. A server admin switches it on; until then the routes do not exist.

## Turn it on

In the world's `serverconfig/cccloud-server.toml` (for a dedicated server: `world/serverconfig/`), section `[api]`, then restart the server:

```toml
[api]
apiEnabled = true                       # default false
apiKeysPerPlayer = 5
apiRequestsPerMinute = 60               # per key
apiIpRequestsPerMinute = 120
apiCorsOrigins = []                     # e.g. ["https://bot.example.org"] for browser tools
apiEvents = true                        # the live event stream
```

Players need the **API keys** permission on the mainframe (admins and owners have it; see [roles](accounts.md)). Server owners can forbid keys for a player with the permission node `cccloud.api.keys` – their keys then stop working too.

## Create a key

Dashboard → ⚙ → **API keys** → **New key**:

1. A **name** (one key per tool, so you can revoke it on its own).
2. The **scopes** – what the key may do (below).
3. An **expiry**: 1 hour, 24 hours, 7, 30 or 90 days, or never.
4. Optional: the **IP addresses** it may be used from (up to 8, e.g. `192.168.1.0/24`).

The key (`cck_…`) is shown **exactly once** – copy it then. The server only keeps a fingerprint (hash). The list shows every key with its scopes, created, expiry, last use and IP; **Revoke** stops a key at once. Players with “Manage access” see and can revoke every key of the mainframe. A key also ends when the browser you created it with is revoked (lost phone: revoking it ends its keys too).

## Scopes

A request needs the scope **and** the key owner's current permission – a key never does more than its player may.

| Scope | Allows | Needs |
|---|---|---|
| `read:sections` | read cards, widgets and values; the live event stream | View |
| `read:storage` | search storage cards, item counts | Search storage |
| `read:stats` | production statistics | View (storages: Search storage) |
| `action:widgets` | press buttons, set toggles, sliders, selects, numbers and texts | Use controls (redstone widgets: Switch redstone) |
| `run:scenes` | list and run scenes | Use controls and every step's permission |

## Routes

All JSON, `Authorization: Bearer cck_…` on every request. `<id>` is the key's mainframe (shown in the dashboard under ⚙ → This mainframe).

| Route | Scope |
|---|---|
| `GET /api/v1/me` | any – the key, its player and mainframe |
| `GET /api/v1/mainframes/<id>/sections` | read:sections |
| `GET /api/v1/mainframes/<id>/sections/<sectionId>` | read:sections |
| `POST /api/v1/mainframes/<id>/sections/<sectionId>/widgets/<widgetId>` with `{"value": …}` | action:widgets |
| `GET /api/v1/mainframes/<id>/storage/<sectionId>/items?q=iron&limit=20` | read:storage |
| `GET /api/v1/mainframes/<id>/production` | read:stats |
| `GET /api/v1/mainframes/<id>/scenes`, `POST …/scenes/<sceneId>/run` | run:scenes |
| `GET /api/v1/mainframes/<id>/events` (Server-Sent Events) | read:sections |
| `GET /api/v1/openapi.json` | public – the OpenAPI description |

## Limits

- 60 requests per minute per key and 120 per IP address (`apiRequestsPerMinute`, `apiIpRequestsPerMinute`); more answers `429 rate_limited` with a `Retry-After` header. Item searches: 5 per second per key.
- Request bodies up to 2 KB (`apiMaxBody`), up to 5 keys per player (`apiKeysPerPlayer`).
- Event streams: 2 per key (`apiStreamsPerKey`), 32 in total; a comment line every 20 s keeps them open, slow readers are disconnected.

## Security

- **No PIN in the API, on purpose:** PIN-protected controls and scenes answer `403 pin_required` – a leaked key can never reach the second factor. Admin pages, access, devices, pairing, key management, the terminal, scripts, layout, rules, push and the map are never reachable with a key.
- Keys are shown **once** and stored only as a hash. Keep them in a secrets file, never in a URL – a key in the query string (`?key=`) is refused with `400 key_in_query`.
- Pick **few scopes**, an **expiry** and an **IP allow-list** where you can; **revoke** a key you no longer need or that may have leaked.
- The API follows the dashboard's rules: the same host and LAN-only settings, IP bans and connection limits. Use HTTPS when it is reachable from the internet ([internet access](security.md)). Every widget action and scene run is written to the mainframe's audit log (“API key 'bot' (Steve) …”).
- Browser tools on other sites need their exact origin in `apiCorsOrigins`; otherwise browsers cannot call the API (servers and bots are not affected).

## Examples with curl

```bash
KEY="cck_..."                                    # from the dashboard, shown once
BASE="https://dash.example.com/api/v1/mainframes/<mainframe-id>"

# who am I, which mainframe, which scopes
curl -s -H "Authorization: Bearer $KEY" https://dash.example.com/api/v1/me

# all sections (cards) with their widgets and values
curl -s -H "Authorization: Bearer $KEY" "$BASE/sections"

# set a widget: a toggle, slider, select, number or text (a button: "value": true)
curl -s -X POST -H "Authorization: Bearer $KEY" -H "Content-Type: application/json" \
  -d '{"value": false}' "$BASE/sections/<section-id>/widgets/lights"

# list the scenes, run one
curl -s -H "Authorization: Bearer $KEY" "$BASE/scenes"
curl -s -X POST -H "Authorization: Bearer $KEY" "$BASE/scenes/<scene-id>/run"

# live changes as Server-Sent Events (event: snapshot, value, section, removed, state)
curl -N -H "Authorization: Bearer $KEY" "$BASE/events"
```

Errors are JSON like `{"error":"pin_required"}`: `401 unauthorized` / `expired` (key), `403 no_access` (the player lost access), `ip_not_allowed`, `pin_required`, `404 not_found`, `429 rate_limited`.

## Home Assistant

A **REST sensor** reads a widget, a **rest_command** runs a scene (use it in automations or on a dashboard button). The key needs `read:sections` and `run:scenes`.

```yaml
# configuration.yaml – the key goes into secrets.yaml:
#   cccloud_auth: "Bearer cck_..."
sensor:
  - platform: rest
    name: Reactor temperature
    resource: https://dash.example.com/api/v1/mainframes/<mainframe-id>/sections/<section-id>
    headers:
      Authorization: !secret cccloud_auth
    value_template: >
      {{ (value_json.widgets | selectattr('id', 'eq', 'temp') | first).value }}
    unit_of_measurement: K
    scan_interval: 30

rest_command:
  cccloud_night_mode:
    url: https://dash.example.com/api/v1/mainframes/<mainframe-id>/scenes/<scene-id>/run
    method: POST
    headers:
      Authorization: !secret cccloud_auth
```

## A small Discord bot

`!temp` answers with the reactor temperature, `!night` runs the “Night mode” scene. It needs Node 18 or newer, a Discord bot with the **Message Content** intent (Discord developer portal → Bot), and a key with `read:sections` and `run:scenes`. List your section and scene ids once with curl (`/sections`, `/scenes`).

```js
// bot.mjs – npm install discord.js, then:
// DISCORD_TOKEN=... CCCLOUD_KEY=cck_... node bot.mjs
import { Client, GatewayIntentBits } from "discord.js";

const BASE = "https://dash.example.com/api/v1/mainframes/<mainframe-id>";
const REACTOR = "<section-id>"; // the card with the "temp" gauge
const NIGHT = "<scene-id>"; // the "Night mode" scene

async function api(path, init = {}) {
  const res = await fetch(BASE + path, { ...init, headers: { Authorization: `Bearer ${process.env.CCCLOUD_KEY}` } });
  if (!res.ok) throw new Error((await res.json().catch(() => ({}))).error ?? res.status);
  return res.json();
}

const bot = new Client({ intents: [GatewayIntentBits.Guilds, GatewayIntentBits.GuildMessages, GatewayIntentBits.MessageContent] });
bot.on("messageCreate", async (msg) => {
  if (msg.author.bot) return;
  try {
    if (msg.content === "!temp") {
      const temp = (await api(`/sections/${REACTOR}`)).widgets.find((w) => w.id === "temp");
      await msg.reply(`Reactor: ${temp.value} ${temp.unit ?? ""}`);
    } else if (msg.content === "!night") {
      await api(`/scenes/${NIGHT}/run`, { method: "POST" });
      await msg.reply("Night mode started.");
    }
  } catch (e) {
    await msg.reply(`CC: Cloud says: ${e.message}`);
  }
});
bot.login(process.env.DISCORD_TOKEN);
```


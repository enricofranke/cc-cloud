---
# Aus der Dashboard-Doku erzeugt (tools/gen_handbook.ts) – Änderungen in cloud/web/src/docs/content machen.
navigation:
  title: "Öffentliche API"
  position: 13
---

# Öffentliche API

Mit der öffentlichen API können **deine eigenen Programme** Werte lesen und Knöpfe drücken: ein Discord-Bot, Home Assistant, ein Tabellen-Skript. Es ist eine kleine HTTP-JSON-API unter der Adresse des Dashboards, mit **API-Schlüsseln**, die du im Dashboard anlegst.

> **Hinweis:** Die API ist **standardmäßig aus**. Ein Server-Admin schaltet sie ein; bis dahin gibt es die Routen nicht.

## Einschalten

In der `serverconfig/cccloud-server.toml` der Welt (beim dedizierten Server: `world/serverconfig/`), Abschnitt `[api]`, danach den Server neu starten:

```toml
[api]
apiEnabled = true                       # default false
apiKeysPerPlayer = 5
apiRequestsPerMinute = 60               # per key
apiIpRequestsPerMinute = 120
apiCorsOrigins = []                     # e.g. ["https://bot.example.org"] for browser tools
apiEvents = true                        # the live event stream
```

Spieler brauchen auf dem Mainframe die Berechtigung **API keys** (Admins und Besitzer haben sie; siehe [Rollen](accounts.md)). Server-Betreiber können Schlüssel für einen Spieler mit dem Berechtigungsknoten `cccloud.api.keys` verbieten – seine Schlüssel funktionieren dann auch nicht mehr.

## Schlüssel anlegen

Dashboard → ⚙ → **API-Schlüssel** → **Neuer Schlüssel**:

1. Ein **Name** (ein Schlüssel pro Programm – dann kannst du ihn einzeln widerrufen).
2. Die **Bereiche** (Scopes) – was der Schlüssel darf (unten).
3. Ein **Ablauf**: 1 Stunde, 24 Stunden, 7, 30 oder 90 Tage oder nie.
4. Optional: die **IP-Adressen**, von denen er genutzt werden darf (bis 8, z. B. `192.168.1.0/24`).

Der Schlüssel (`cck_…`) wird **genau einmal** angezeigt – kopiere ihn dann. Der Server behält nur einen Fingerabdruck (Hash). Die Liste zeigt jeden Schlüssel mit Bereichen, Erstellung, Ablauf, letzter Nutzung und IP; **Widerrufen** stoppt einen Schlüssel sofort. Spieler mit „Manage access“ sehen und widerrufen alle Schlüssel des Mainframes. Ein Schlüssel endet auch, wenn der Browser widerrufen wird, mit dem du ihn erstellt hast (Handy verloren: es zu widerrufen beendet auch seine Schlüssel).

## Bereiche

Eine Anfrage braucht den Bereich **und** die aktuelle Berechtigung des Schlüssel-Besitzers – ein Schlüssel kann nie mehr als sein Spieler.

| Bereich | Erlaubt | Braucht |
|---|---|---|
| `read:sections` | Karten, Widgets und Werte lesen; der Live-Ereignisstrom | View |
| `read:storage` | Speicherkarten durchsuchen, Itemmengen | Search storage |
| `read:stats` | Produktionsstatistik | View (Speicher: Search storage) |
| `action:widgets` | Knöpfe drücken, Schalter, Regler, Auswahl, Zahlen und Texte setzen | Use controls (Redstone-Widgets: Switch redstone) |
| `run:scenes` | Szenen auflisten und starten | Use controls und die Berechtigung jedes Schritts |

## Routen

Alles JSON, `Authorization: Bearer cck_…` bei jeder Anfrage. `<id>` ist der Mainframe des Schlüssels (steht im Dashboard unter ⚙ → This mainframe).

| Route | Bereich |
|---|---|
| `GET /api/v1/me` | jeder – der Schlüssel, sein Spieler und Mainframe |
| `GET /api/v1/mainframes/<id>/sections` | read:sections |
| `GET /api/v1/mainframes/<id>/sections/<sectionId>` | read:sections |
| `POST /api/v1/mainframes/<id>/sections/<sectionId>/widgets/<widgetId>` mit `{"value": …}` | action:widgets |
| `GET /api/v1/mainframes/<id>/storage/<sectionId>/items?q=iron&limit=20` | read:storage |
| `GET /api/v1/mainframes/<id>/production` | read:stats |
| `GET /api/v1/mainframes/<id>/scenes`, `POST …/scenes/<sceneId>/run` | run:scenes |
| `GET /api/v1/mainframes/<id>/events` (Server-Sent Events) | read:sections |
| `GET /api/v1/openapi.json` | öffentlich – die OpenAPI-Beschreibung |

## Grenzen

- 60 Anfragen pro Minute und Schlüssel und 120 pro IP-Adresse (`apiRequestsPerMinute`, `apiIpRequestsPerMinute`); darüber antwortet der Server mit `429 rate_limited` und einem `Retry-After`-Header. Item-Suchen: 5 pro Sekunde und Schlüssel.
- Anfragen bis 2 KB (`apiMaxBody`), bis zu 5 Schlüssel pro Spieler (`apiKeysPerPlayer`).
- Ereignisströme: 2 pro Schlüssel (`apiStreamsPerKey`), 32 insgesamt; alle 20 s hält eine Kommentarzeile sie offen, langsame Leser werden getrennt.

## Sicherheit

- **Keine PIN in der API, mit Absicht:** PIN-geschützte Bedienelemente und Szenen antworten mit `403 pin_required` – ein verlorener Schlüssel kommt nie an den zweiten Faktor. Admin-Seiten, Zugriff, Geräte, Kopplung, Schlüsselverwaltung, Terminal, Skripte, Layout, Regeln, Push und die Karte sind mit einem Schlüssel nie erreichbar.
- Schlüssel werden **einmal** gezeigt und nur als Hash gespeichert. Bewahre sie in einer Geheimnis-Datei auf, nie in einer URL – ein Schlüssel im Query-String (`?key=`) wird mit `400 key_in_query` abgelehnt.
- Wähle **wenige Bereiche**, einen **Ablauf** und wo möglich eine **IP-Liste**; **widerrufe** Schlüssel, die du nicht mehr brauchst oder die bekannt geworden sein könnten.
- Die API folgt den Regeln des Dashboards: dieselben Host- und Nur-LAN-Einstellungen, IP-Sperren und Verbindungsgrenzen. Nutze HTTPS, wenn sie aus dem Internet erreichbar ist ([Zugriff aus dem Internet](security.md)). Jede Widget-Aktion und jeder Szenenlauf landet im Audit-Protokoll des Mainframes („API key 'bot' (Steve) …“).
- Browser-Programme auf anderen Seiten brauchen ihren genauen Ursprung in `apiCorsOrigins`; sonst können Browser die API nicht aufrufen (Server und Bots betrifft das nicht).

## Beispiele mit curl

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

Fehler sind JSON wie `{"error":"pin_required"}`: `401 unauthorized` / `expired` (Schlüssel), `403 no_access` (der Spieler hat keinen Zugriff mehr), `ip_not_allowed`, `pin_required`, `404 not_found`, `429 rate_limited`.

## Home Assistant

Ein **REST-Sensor** liest ein Widget, ein **rest_command** startet eine Szene (in Automationen oder als Dashboard-Knopf). Der Schlüssel braucht `read:sections` und `run:scenes`.

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

## Ein kleiner Discord-Bot

`!temp` antwortet mit der Reaktortemperatur, `!night` startet die Szene „Nachtmodus“. Er braucht Node 18 oder neuer, einen Discord-Bot mit dem Intent **Message Content** (Discord Developer Portal → Bot) und einen Schlüssel mit `read:sections` und `run:scenes`. Die IDs deiner Karten und Szenen listest du einmal mit curl auf (`/sections`, `/scenes`).

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


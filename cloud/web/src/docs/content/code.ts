// Code examples shared by both languages (Lua is the same in DE and EN).
import GEN from "./recipes.gen.json" with { type: "json" };
import MODS_GEN from "./mods.gen.json" with { type: "json" };

export const FENCE = "```";

export const LUA_FIRST = `local cloud = peripheral.find("cloud_uplink")
cloud.setTitle("My Base")
cloud.gauge("temp", "Temperature", {max=1200, unit="K", warn=900, crit=1100})
cloud.toggle("lights", "Lights", {value=false})
cloud.button("alarm", "Alarm", {color="red", confirm=true})

while true do
  local _, id, value, user = os.pullEvent("cloud_action")
  if id == "lights" then
    redstone.setOutput("back", value)
    cloud.set("lights", value)
  elseif id == "alarm" then
    cloud.alert(user .. " pressed the alarm", "warn")
  end
end`;

export const LUA_EVENT = `while true do
  local event, id, value, user, side = os.pullEvent("cloud_action")
  print(user .. " used " .. id .. " = " .. tostring(value))
end`;

export const LUA_LAYOUT = `local cloud = peripheral.find("cloud_uplink")
cloud.setTitle("Reactor")
cloud.page("reactor", "Reactor", {icon="flame"})
cloud.page("power", "Power", {icon="bolt"})
cloud.group("core", "Core", {page="reactor", cols=2})
cloud.gauge("temp", "Temperature", {max=1200, unit="K", warn=900, crit=1100, group="core"})
cloud.chart("load", "Load", {min=0, max=100, unit="%", points=120, group="core", span=2})
cloud.indicator("running", "Running", {value=true, onColor="green", page="power"})`;

export const LUA_SETMANY = `cloud.setMany({temp = 812, load = 64, running = true})`;

export const LUA_MAINFRAME = `local mf = peripheral.find("cloud_mainframe")
print("Dashboard on: " .. tostring(mf.isEnabled()))
print("Online: " .. tostring(mf.isOnline()))
for _, u in ipairs(mf.getUplinks()) do
  print(u.title or u.id, u.x, u.y, u.z, u.online and "online" or "offline")
end
print(("Power: %d / %d FE, %d FE/t"):format(mf.getEnergy(), mf.getEnergyCapacity(), mf.getEnergyUsage()))`;

export const LUA_BRIDGE = `local bridge = peripheral.find("me_cloud_bridge") or peripheral.find("rs_cloud_bridge")
local s = bridge.getStorage()
print(("Items: %d / %d"):format(s.itemsUsed, s.itemsTotal))
for _, item in ipairs(bridge.findItems("iron", 5)) do
  print(item.displayName, item.count, item.craftable and "(craftable)" or "")
end
local result = bridge.craft("minecraft:iron_block", 4)
if result.ok then print("Job " .. result.jobId) else print("Error: " .. result.error) end`;

export const LUA_BRIDGE_WIDGETS = `local cloud = peripheral.find("cloud_uplink")
local bridge = peripheral.find("me_cloud_bridge")
local name = peripheral.getName(bridge)
cloud.setTitle("Storage")
cloud.itemSearch("search", "Search & craft", {bridge=name, max=1024})
cloud.watchlist("watch", "Watchlist", {bridge=name})
cloud.jobs("jobs", "Crafting jobs", {bridge=name})`;

export const UI_QUICK = `local ui = require("cloud.ui")
local dash = ui.connect()
dash:title("Reactor")
local temp = dash:gauge("temp", "Temperature", {max=1200, unit="K", warn=900, crit=1100})
dash:button("scram", "SCRAM", {color="red", confirm=true, pin=true}, function(user)
  redstone.setOutput("back", true)
  dash:notify(user .. " pressed SCRAM", "crit")
end)
dash:every(1, function() temp:set(math.random(600, 1000)) end)
dash:run()`;

export const UI_FARM = `-- startup.lua: a tree farm with lights, a counter and a harvest button
local ui = require("cloud.ui")
local dash = ui.connect()
dash:title("Tree Farm")

local lights = false
dash:toggle("lights", "Grow lights", {value=false}, function(user, value)
  lights = value
  redstone.setOutput("top", value)
end)

local logs = dash:gauge("logs", "Logs in chest", {max=1728, warn=1500, crit=1700})
local chest = peripheral.find("minecraft:chest")

dash:button("harvest", "Harvest now", {color="green"}, function(user)
  redstone.setOutput("back", true)
  sleep(0.5)
  redstone.setOutput("back", false)
  dash:notify(user .. " started a harvest")
end)

dash:every(5, function()
  local count = 0
  for _, item in pairs(chest.list()) do count = count + item.count end
  logs:set(count)
end)

dash:run()`;

export const UI_REACTOR = `-- A Mekanism-style reactor panel with pages, a chart and a PIN-protected SCRAM
local ui = require("cloud.ui")
local dash = ui.connect()
local reactor = peripheral.find("fissionReactorLogicAdapter")

dash:title("Fission Reactor")
dash:page("main", "Reactor", {icon="flame"})
dash:page("log", "History", {icon="chart"})
dash:group("core", "Core", {page="main", cols=2})

local temp = dash:gauge("temp", "Temperature", {max=1200, unit="K", warn=900, crit=1100, group="core"})
local burn = dash:slider("burn", "Burn rate", {min=0, max=20, step=0.5, value=1, unit="mB/t", group="core"},
  function(user, value) reactor.setBurnRate(value) end)
local running = dash:indicator("running", "Active", {group="core"})
local history = dash:chart("history", "Temperature", {min=0, max=1200, unit="K", points=300, interval=2, page="log"})

dash:button("scram", "SCRAM", {color="red", confirm=true, pin=true, page="main"}, function(user)
  reactor.scram()
  dash:notify(user .. " pressed SCRAM", "crit")
end)

dash:every(2, function()
  local k = reactor.getTemperature()
  dash:setMany({temp = math.floor(k), history = math.floor(k), running = reactor.getStatus()})
  if k > 1100 then reactor.scram(); dash:notify("Auto-SCRAM at " .. math.floor(k) .. " K", "crit") end
end)

dash:run()`;

export const SHELL_EDIT = `edit startup.lua`;
export const SHELL_RUN = `startup`;
export const SHELL_LUA = `lua
lua> peripheral.find("cloud_uplink").getClients()
1
lua> exit()`;

export const TOML_LAN = `[web]
webAllowInternet = false   # default: only LAN addresses may connect`;

export const TOML_CADDY = `[web]
webBind = "127.0.0.1"
webAllowInternet = true
webTrustedProxies = ["127.0.0.1"]
publicUrl = "https://dash.example.com"`;

export const CADDYFILE = `dash.example.com {
    reverse_proxy 127.0.0.1:8787
}`;

export const CLOUDFLARED = `cloudflared tunnel login
cloudflared tunnel create cccloud
cloudflared tunnel route dns cccloud dash.example.com
cloudflared tunnel run cccloud`;

export const CLOUDFLARED_YML = `tunnel: cccloud
credentials-file: /root/.cloudflared/<tunnel-id>.json
ingress:
  - hostname: dash.example.com
    service: http://127.0.0.1:8787
  - service: http_status:404`;

export const TOML_TLS = `[web]
webAllowInternet = true
tlsCertPath = "/etc/letsencrypt/live/mc.example.com/fullchain.pem"
tlsKeyPath = "/srv/mc/privkey-pkcs8.pem"
publicUrl = "https://mc.example.com:8787"`;

export const PKCS8 = `openssl pkcs8 -topk8 -nocrypt -in privkey.pem -out privkey-pkcs8.pem`;

export const COMMANDS = `/cccloud status
/cccloud devices
/cccloud revoke <device name>
/cccloud revoke all
/cccloud trust <player>
/cccloud untrust <player>
/cccloud pin set <4-8 digits>
/cccloud pin clear`;

// ---- v0.10 public API (docs page "api"; the same in both languages)
export const TOML_API = `[api]
apiEnabled = true                       # default false
apiKeysPerPlayer = 5
apiRequestsPerMinute = 60               # per key
apiIpRequestsPerMinute = 120
apiCorsOrigins = []                     # e.g. ["https://bot.example.org"] for browser tools
apiEvents = true                        # the live event stream`;

export const API_CURL = `KEY="cck_..."                                    # from the dashboard, shown once
BASE="https://dash.example.com/api/v1/mainframes/<mainframe-id>"

# who am I, which mainframe, which scopes
curl -s -H "Authorization: Bearer $KEY" https://dash.example.com/api/v1/me

# all sections (cards) with their widgets and values
curl -s -H "Authorization: Bearer $KEY" "$BASE/sections"

# set a widget: a toggle, slider, select, number or text (a button: "value": true)
curl -s -X POST -H "Authorization: Bearer $KEY" -H "Content-Type: application/json" \\
  -d '{"value": false}' "$BASE/sections/<section-id>/widgets/lights"

# list the scenes, run one
curl -s -H "Authorization: Bearer $KEY" "$BASE/scenes"
curl -s -X POST -H "Authorization: Bearer $KEY" "$BASE/scenes/<scene-id>/run"

# live changes as Server-Sent Events (event: snapshot, value, section, removed, state)
curl -N -H "Authorization: Bearer $KEY" "$BASE/events"`;

export const API_HA = `# configuration.yaml – the key goes into secrets.yaml:
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
      Authorization: !secret cccloud_auth`;

export const API_DISCORD = `// bot.mjs – npm install discord.js, then:
// DISCORD_TOKEN=... CCCLOUD_KEY=cck_... node bot.mjs
import { Client, GatewayIntentBits } from "discord.js";

const BASE = "https://dash.example.com/api/v1/mainframes/<mainframe-id>";
const REACTOR = "<section-id>"; // the card with the "temp" gauge
const NIGHT = "<scene-id>"; // the "Night mode" scene

async function api(path, init = {}) {
  const res = await fetch(BASE + path, { ...init, headers: { Authorization: \`Bearer \${process.env.CCCLOUD_KEY}\` } });
  if (!res.ok) throw new Error((await res.json().catch(() => ({}))).error ?? res.status);
  return res.json();
}

const bot = new Client({ intents: [GatewayIntentBits.Guilds, GatewayIntentBits.GuildMessages, GatewayIntentBits.MessageContent] });
bot.on("messageCreate", async (msg) => {
  if (msg.author.bot) return;
  try {
    if (msg.content === "!temp") {
      const temp = (await api(\`/sections/\${REACTOR}\`)).widgets.find((w) => w.id === "temp");
      await msg.reply(\`Reactor: \${temp.value} \${temp.unit ?? ""}\`);
    } else if (msg.content === "!night") {
      await api(\`/scenes/\${NIGHT}/run\`, { method: "POST" });
      await msg.reply("Night mode started.");
    }
  } catch (e) {
    await msg.reply(\`CC: Cloud says: \${e.message}\`);
  }
});
bot.login(process.env.DISCORD_TOKEN);`;

/** Mod names for recipe conditions ("Needs …", the pack makers' table). */
export const MOD_NAMES: Record<string, string> = {
  guideme: "GuideME",
  allthemodium: "AllTheModium",
  allthetweaks: "All The Tweaks",
  ae2: "Applied Energistics 2",
  refinedstorage: "Refined Storage",
};

/** Every crafting recipe of the mod, generated from its recipe JSONs (dev/build-recipes.ts). */
export const ALL_RECIPES = GEN.recipes as GenRecipe[];
const byFile = (f: string) => {
  const r = ALL_RECIPES.find((x) => x.file === f);
  if (!r) throw new Error(`recipe ${f} missing`);
  return r;
};
/** Named recipes used in the text. */
export const RECIPES = {
  core: byFile("cloud_core"),
  mainframe: byFile("cloud_mainframe"),
  uplink: byFile("cloud_uplink"),
  advancedUplink: byFile("advanced_cloud_uplink"),
  probe: byFile("cloud_probe"),
  linkCard: byFile("cloud_link_card"),
  meBridge: byFile("me_cloud_bridge"),
  relay: byFile("cloud_relay"),
  advancedRelay: byFile("advanced_cloud_relay"),
  enderRelay: byFile("ender_relay"),
  rsBridge: byFile("rs_cloud_bridge"),
  cloudSwitch: byFile("cloud_switch"),
  cloudButton: byFile("cloud_button"),
  cloudLamp: byFile("cloud_lamp"),
  cloudSensor: byFile("cloud_sensor"),
};
export interface GenRecipe {
  file: string;
  result: string;
  count?: number;
  shapeless?: boolean;
  grid?: string[];
  key?: Record<string, string>;
  items?: string[];
  requires?: string[];
}

/** Groups for the "All recipes" page (by result id). */
export const RECIPE_GROUPS: { id: string; en: string; de: string; match: (r: GenRecipe) => boolean }[] = [
  { id: "basics", en: "Basics", de: "Grundlagen", match: (r) => /cloud_core|cloud_link_card|cloud_mainframe|cloud_uplink|cloud_probe/.test(r.result) },
  { id: "network", en: "Relays and cables", de: "Relays und Kabel", match: (r) => /relay|fiber/.test(r.result) },
  { id: "bridges", en: "Bridges", de: "Bridges", match: (r) => /bridge/.test(r.result) },
  { id: "devices", en: "Tablet, display, analyzer", de: "Tablet, Display, Analyzer", match: (r) => /tablet|display|analyzer/.test(r.result) },
  { id: "redstone", en: "Redstone devices", de: "Redstone-Geräte", match: (r) => /cloud_(switch|button|lamp|sensor)$/.test(r.result) },
  { id: "upgrades", en: "Mainframe upgrades", de: "Mainframe-Upgrades", match: (r) => /upgrade_|quantum_core/.test(r.result) },
  { id: "misc", en: "Other", de: "Sonstiges", match: () => true },
];
export function groupOf(r: GenRecipe) {
  return RECIPE_GROUPS.find((g) => g.match(r))?.id ?? "misc";
}
/** Markdown for one group: a heading per item, then its recipes (normal and fallback). */
const NAMES = GEN.names as Record<string, { en: string; de: string }>;
export function recipeSection(groupId: string, lang: "en" | "de") {
  const EXTRA: Record<string, { en: string; de: string }> = { "guideme:guide": { en: "CC: Cloud Handbook", de: "CC: Cloud-Handbuch" } };
  const name = (id: string) => NAMES[id]?.[lang] ?? EXTRA[id]?.[lang] ?? id.replace(/^[a-z0-9_]+:/, "").replace(/_/g, " ");
  const rs = ALL_RECIPES.filter((r) => groupOf(r) === groupId);
  const results = [...new Set(rs.map((r) => r.result))];
  return results
    .map((res) => {
      const list = rs.filter((r) => r.result === res);
      return `### ${name(res)}\n${list.map((r) => recipe(r)).join("\n")}`;
    })
    .join("\n\n");
}

export const recipe = (r: object) => `${FENCE}recipe\n${JSON.stringify(r)}\n${FENCE}`;
export const code = (lang: string, src: string) => `${FENCE}${lang}\n${src}\n${FENCE}`;

// ------------------------------------------------------------ supported mods (generated from docs/COMPAT.md)
type ModEntry = { group: string; mod: string; id: string; version: string; what: string; controls: string; tested: string; skipped: boolean };
const GROUP_DE: Record<string, string> = {
  v05: "Energie, Server und Teams",
  "Storage controllers": "Lager-Controller",
  "Applied Energistics add-ons": "Applied-Energistics-Erweiterungen",
  Quarries: "Steinbrüche",
  "Spawners and mob farms": "Spawner und Mobfarmen",
  "Processing machines": "Verarbeitungsmaschinen",
  Logistics: "Logistik",
  "Integrated Dynamics": "Integrated Dynamics",
  "World & progression": "Welt und Fortschritt",
  "In-game handbook": "Handbuch im Spiel",
  "KubeJS bindings": "KubeJS",
  "Wave 3 – more ATM11 machines": "Weitere ATM11-Maschinen",
};
const GROUP_EN: Record<string, string> = { v05: "Energy, server and teams", "Wave 3 – more ATM11 machines": "More ATM11 machines" };
const cell = (s: string) => (s || "–").replace(/\|/g, "/").replace(/\n/g, " ");
/** Markdown tables of every supported mod, grouped like docs/COMPAT.md; skipped mods in a short list at the end. */
export function modsSection(lang: "en" | "de") {
  const mods = (MODS_GEN as { mods: ModEntry[] }).mods;
  const groups = [...new Set(mods.map((m) => m.group))];
  const head = lang === "de" ? "| Mod | Version | Was die Karte zeigt | Steuerung | Getestet |" : "| Mod | Version | What the card shows | Controls | Tested |";
  const parts = groups.map((g) => {
    const rows = mods.filter((m) => m.group === g && !m.skipped);
    if (!rows.length) return "";
    const title = lang === "de" ? (GROUP_DE[g] ?? g) : (GROUP_EN[g] ?? g);
    return `### ${title}\n\n${head}\n|---|---|---|---|---|\n${rows.map((m) => `| ${cell(m.mod)}${m.id ? ` (\`${m.id}\`)` : ""} | ${cell(m.version)} | ${cell(m.what)} | ${cell(m.controls)} | ${cell(m.tested)} |`).join("\n")}`;
  });
  const skipped = mods.filter((m) => m.skipped);
  const skip = skipped.length
    ? `### ${lang === "de" ? "Ohne eigene Karte" : "No card of their own"}\n\n${skipped.map((m) => `- **${m.mod}**${m.id ? ` (\`${m.id}\`)` : ""}: ${m.what.replace(/^(skipped|no own card[^:]*|no card):\s*/i, "")}`).join("\n")}`
    : "";
  return [...parts, skip].filter(Boolean).join("\n\n");
}

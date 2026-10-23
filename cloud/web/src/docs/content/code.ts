// Code examples shared by both languages (Lua is the same in DE and EN).

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

/** Crafting recipes (match src/main/resources/data/cccloud/recipe). */
export const RECIPES = {
  core: { result: "cccloud:cloud_core", count: 2, grid: ["nPn", "PEP", "nPn"], key: { n: "#c:nuggets/gold", P: "minecraft:prismarine_crystals", E: "#c:ender_pearls" } },
  mainframe: { result: "cccloud:cloud_mainframe", grid: ["GKG", "GAG", "GDG"], key: { G: "#c:ingots/gold", K: "cccloud:cloud_core", A: "computercraft:computer_advanced", D: "computercraft:disk_drive" } },
  uplink: { result: "cccloud:cloud_uplink", count: 2, grid: ["SKS", "SMS"], key: { S: "minecraft:stone", K: "cccloud:cloud_core", M: "computercraft:wired_modem" } },
  advancedUplink: { result: "cccloud:advanced_cloud_uplink", grid: ["GGG", "GUG", "GEG"], key: { G: "#c:ingots/gold", U: "cccloud:cloud_uplink", E: "#c:ender_pearls" } },
  linkCard: { result: "cccloud:cloud_link_card", shapeless: true, items: ["minecraft:paper", "#c:nuggets/gold", "#c:dusts/redstone", "minecraft:prismarine_crystals"] },
  meBridge: { result: "cccloud:me_cloud_bridge", grid: ["GKG", "MIM", "GGG"], key: { G: "#c:ingots/gold", K: "cccloud:cloud_core", M: "computercraft:wired_modem", I: "ae2:interface" } },
  relay: { result: "cccloud:cloud_relay", grid: ["SMS", "RKR", "SSS"], key: { S: "minecraft:stone", M: "computercraft:wired_modem", K: "cccloud:cloud_core", R: "#c:dusts/redstone" } },
  advancedRelay: { result: "cccloud:advanced_cloud_relay", grid: ["GEG", "GXG", "GGG"], key: { G: "#c:ingots/gold", E: "#c:ender_pearls", X: "cccloud:cloud_relay" } },
  enderRelay: { result: "cccloud:ender_relay", grid: [" Y ", "YXY", " B "], key: { Y: "minecraft:ender_eye", B: "#c:storage_blocks/gold", X: "cccloud:advanced_cloud_relay" } },
  rsBridge: { result: "cccloud:rs_cloud_bridge", grid: ["GKG", "MIM", "GGG"], key: { G: "#c:ingots/gold", K: "cccloud:cloud_core", M: "computercraft:wired_modem", I: "refinedstorage:interface" } },
};

export const recipe = (r: object) => `${FENCE}recipe\n${JSON.stringify(r)}\n${FENCE}`;
export const code = (lang: string, src: string) => `${FENCE}${lang}\n${src}\n${FENCE}`;

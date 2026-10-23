---
# Generated from the dashboard docs by tools/gen_handbook.ts – edit cloud/web/src/docs/content instead.
navigation:
  title: "Lua reference"
  position: 3
  icon: computercraft:computer_advanced
---

# Lua reference

Everything a program can do with CC: Cloud. In-game, `help cloud` prints a short version.

## Getting the peripheral

```lua
local cloud = peripheral.find("cloud_uplink")
```

The uplink must touch the computer or sit on its wired modem network. Several uplinks? Use `peripheral.wrap("cloud_uplink_0")`.

## A first program

```lua
local cloud = peripheral.find("cloud_uplink")
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
end
```

## Widgets

Every widget takes an **id** (1–32 characters: a–z, 0–9, _), a label and an optional options table. Calling it again with the same id updates it.

| Method | Options |
|---|---|
| `cloud.setTitle(text)` | card title (max 48 characters) |
| `cloud.button(id, label, opts)` | color, confirm, pin |
| `cloud.toggle(id, label, opts)` | value, confirm, pin |
| `cloud.slider(id, label, opts)` | min, max, step, value, unit, pin |
| `cloud.gauge(id, label, opts)` | min, max, unit, warn, crit, value |
| `cloud.label(id, text)` | – |
| `cloud.chart(id, label, opts)` | min, max, unit, points (60–300), interval, style = "line" \| "sparkline" |
| `cloud.progress(id, label, opts)` | min, max, unit, value |
| `cloud.indicator(id, label, opts)` | value, onColor, offColor |
| `cloud.select(id, label, opts)` | options = \{"low","mid","high"\}, value |
| `cloud.number(id, label, opts)` | min, max, step, value, unit |
| `cloud.text(id, label, opts)` | maxLength (≤ 256), value, placeholder |
| `cloud.buttons(id, label, opts)` | options = \{"A","B","C"\} |
| `cloud.itemSearch(id, label, opts)` | bridge = peripheral name, max |
| `cloud.watchlist(id, label, opts)` | bridge |
| `cloud.jobs(id, label, opts)` | bridge |

Colours: cyan, green, yellow, red, gray. A gauge is "low is bad" when crit \< warn (e.g. coolant).

## Values

| Method | Does |
|---|---|
| `cloud.set(id, value)` | update a gauge, label, toggle, chart (appends a point), … |
| `cloud.setMany({id=value, …})` | several values in one go |
| `cloud.remove(id)` | remove a widget |
| `cloud.clear()` | remove everything (the automatic Computer card returns) |

```lua
cloud.setMany({temp = 812, load = 64, running = true})
```

## Events

When someone uses a widget, every computer attached to the uplink gets a `cloud_action` event:

```lua
while true do
  local event, id, value, user, side = os.pullEvent("cloud_action")
  print(user .. " used " .. id .. " = " .. tostring(value))
end
```

| Widget | value |
|---|---|
| button | `true` |
| toggle | `true` / `false` |
| slider, number | number |
| select, buttons | the chosen option (string) |
| text | string (plain text) |
| itemSearch craft | table \{op="craft", name, count, jobId\} |

`user` is the device name, `side` the uplink's peripheral name. At most 64 actions wait in the queue per computer.

## Layout: pages and groups

```lua
local cloud = peripheral.find("cloud_uplink")
cloud.setTitle("Reactor")
cloud.page("reactor", "Reactor", {icon="flame"})
cloud.page("power", "Power", {icon="bolt"})
cloud.group("core", "Core", {page="reactor", cols=2})
cloud.gauge("temp", "Temperature", {max=1200, unit="K", warn=900, crit=1100, group="core"})
cloud.chart("load", "Load", {min=0, max=100, unit="%", points=120, group="core", span=2})
cloud.indicator("running", "Running", {value=true, onColor="green", page="power"})
```

- `cloud.page(id, title, {icon=})` – a tab. With pages an uplink may hold 64 widgets instead of 32.
- `cloud.group(id, title, {page=, cols=1..4})` – a card; widgets join it with `group=`.
- Every widget also accepts `page`, `group`, `span` (1–4), `order` (0–10000), `color` and `icon`.
- Icons: home, bolt, flame, drop, box, fan, lock, warning, gear, chart, star, leaf, heart, cpu, shield, pick.

## Alerts and status

| Method | Does |
|---|---|
| `cloud.alert(text, level)` | toast + log; level "info", "warn" or "crit"; max 1 per second |
| `cloud.notify(text, level)` | same as alert |
| `cloud.isOnline()` | true if the mainframe is online and the dashboard on |
| `cloud.getClients()` | number of connected dashboards |
| `cloud.getLastClientSeen()` | seconds since a dashboard was connected, or nil |

## cloud_mainframe

A computer next to the mainframe can read it:

| Method | Returns |
|---|---|
| `isEnabled()` | dashboard switched on |
| `isOnline()` | loaded, powered, not redstone-killed and enabled |
| `setEnabled(bool)` | switch the dashboard on/off |
| `getUplinks()` | list of \{id, title, online, loaded, x, y, z, dimension\} |
| `getEnergy()`, `getEnergyCapacity()`, `getEnergyUsage()` | FE stored, buffer size, FE/t |
| `getNetwork()` | \{nodes, links, routes\} – see [network](network.md) |

```lua
local mf = peripheral.find("cloud_mainframe")
print("Dashboard on: " .. tostring(mf.isEnabled()))
print("Online: " .. tostring(mf.isOnline()))
for _, u in ipairs(mf.getUplinks()) do
  print(u.title or u.id, u.x, u.y, u.z, u.online and "online" or "offline")
end
print(("Power: %d / %d FE, %d FE/t"):format(mf.getEnergy(), mf.getEnergyCapacity(), mf.getEnergyUsage()))
```

The pairing code is never readable from Lua.

## Bridges

Peripheral types `me_cloud_bridge` and `rs_cloud_bridge`, same methods:

| Method | Returns |
|---|---|
| `isConnected()` | connected to a powered network |
| `getEnergy()`, `getMaxEnergy()`, `getEnergyUsage()` | network energy |
| `getStorage()` | \{itemsUsed, itemsTotal, typesUsed, fluidsUsed, fluidsTotal\} |
| `listItems([filter], [limit=256])` | \{name, displayName, count, craftable\}… |
| `findItems(query, [limit=50])` | same, matching id or name |
| `getItem(name)` | one item or nil |
| `isCraftable(name)` | boolean |
| `craft(name, count)` | \{ok, jobId\} or \{ok=false, error\} |
| `getCraftingJobs()` | running jobs |
| `cancelCraft(jobId)` | where supported |
| `listFluids([limit])` | fluids |

```lua
local bridge = peripheral.find("me_cloud_bridge") or peripheral.find("rs_cloud_bridge")
local s = bridge.getStorage()
print(("Items: %d / %d"):format(s.itemsUsed, s.itemsTotal))
for _, item in ipairs(bridge.findItems("iron", 5)) do
  print(item.displayName, item.count, item.craftable and "(craftable)" or "")
end
local result = bridge.craft("minecraft:iron_block", 4)
if result.ok then print("Job " .. result.jobId) else print("Error: " .. result.error) end
```

## Limits

- ids: 1–32 characters a–z, 0–9, _
- 32 widgets per uplink, 64 with pages
- titles and labels 48 characters, text values 256, alert text 200
- alerts: 1 per second per uplink

## Upload scripts from the browser

Admins can put Lua programs on a computer without typing them in-game: click **\</>** in the header of a computer's card.

- **Upload:** drop a `.lua` file or paste code, choose the file name (`name.lua` or `folder/name.lua`), then **Upload & run**. Existing files are only replaced after you confirm.
- **Run it now** starts the program in a new tab on advanced computers. Basic computers have no tabs: the dashboard asks to restart the computer and run it once.
- **Start it automatically** writes a `startup.lua` that runs your file at every boot.
- **Examples:** ready-made programs (control room, test program, the widget demo) – pick one, adjust it, upload.
- **Files:** see what is on the computer, open, download or delete files, and how much disk space is left.

Only admins of the mainframe see this, and changes need the mainframe PIN if one is set. The code runs on that CC computer inside CC's sandbox – never on the Minecraft server itself. Every upload is logged and the owner gets a chat note. Limits: 256 KB per file, 10 changes per minute.

## Script Store

Ready-made programs from the community: **Script Store** in the mainframe's menu (admins). Each script shows what it does, what it **uses** (redstone, peripherals, internet …) and its full source – checked against the store's SHA-256 before you see it. Pick a computer and install; run it now or start it automatically. Installed store scripts show **Update available** when there is a newer version, and you are warned before overwriting a file you changed on the computer.

On the project website, **Install** opens your dashboard at `/store#install=<script>` – the same view, nothing is installed without your click. Server admins can switch the store off (`scriptStore=false`). Community scripts run on your CC computer and can control what it controls – they cannot touch the Minecraft server.


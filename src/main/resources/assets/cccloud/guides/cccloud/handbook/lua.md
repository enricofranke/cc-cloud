---
# Generated from the dashboard docs by tools/gen_handbook.ts – edit cloud/web/src/docs/content instead.
navigation:
  title: "Lua reference"
  position: 5
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
| `cloud.itemSearch(id, label, opts)` | bridge = peripheral name (a side, or a bridge on the computer's cable network such as `me_cloud_bridge_0`), max |
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
- `cloud.group(id, title, {page=, cols=1..4, x=, y=, w=3..12, h=1..60, color=, icon=, order=})` – a card; widgets join it with `group=`.
- Every widget also accepts `page`, `group`, `span` (1–4), `order` (0–10000), `color`, `icon` and a box `x`, `y`, `w`, `h`.
- Icons: home, bolt, flame, drop, box, fan, lock, warning, gear, chart, star, leaf, heart, cpu, shield, pick, lever, button, lamp, sensor.

### Bigger layouts

Pages and cards sit on a 12-column grid, and so do the widgets in a card. With `x`, `y`, `w` and `h` a script says where they go (rows are about 32 px high). All four place the card; `w` alone only sets its width. Card colours: gold, cyan, green, yellow, red, purple, orange, gray.

```lua
dash:page("plant", "Plant", {icon="flame"})
dash:group("map", "Base map", {page="plant", x=0, y=0, w=8, h=14, color="purple", icon="star"})
dash:group("log", "Readings", {page="plant", x=8, y=0, w=4, h=14})
dash:gauge("temp", "Core", {group="log", w=6, h=6})
dash:gauge("fuel", "Fuel", {group="log", x=6, y=0, w=6, h=6})
```

This is where a script's cards start. What someone arranges in the [layout editor](dashboard.md) wins, and stays when the script restarts.

## Canvas: draw your own card

A canvas is a surface in a card that your program draws on: mimic diagrams, maps, charts of your own, small games. No fixed widget does this. Try `cloud-mimic`, `cloud-map`, `cloud-snake` and `cloud-overview`.

```lua
local ui = require("cloud.ui")
local dash = ui.connect()
local x = 80
local map = dash:canvas("map", "Base map", {width=160, height=90, input="click", bg="panel"}, function(user, ev)
  x = ev.x -- ev.type is "click", ev.x and ev.y are pixels of the canvas
end)
dash:every(0.5, function()
  map:clear()
  map:line(0, 45, 160, 45, "line")
  map:circle(x, 45, 6, "cyan")
  map:text(4, 4, "Marker at " .. x, "text")
  map:item(140, 4, "minecraft:iron_ingot", 16)
  map:draw() -- sends everything since clear() as one frame
end)
dash:run()
```

`dash:canvas(id, label, opts, handler)` takes `width` (16–480, default 160), `height` (16–320, default 90), `bg`, `input`, `pin`, `confirm` and the layout options. Coordinates are pixels of the canvas, top left is 0, 0. The dashboard scales it to the card.

| `cloud.ui` | Draws |
|---|---|
| `clear([color])` | starts a new frame |
| `rect(x, y, w, h, color)`, `box(x, y, w, h, color, [line])` | filled rectangle, outline |
| `line(x1, y1, x2, y2, color, [line])` | line, up to 16 wide |
| `circle(x, y, r, color)`, `ring(x, y, r, color, [line])` | filled circle, outline |
| `pixel(x, y, color)` | one pixel |
| `text(x, y, text, color, {size=8, align="l"})` | plain text, size 6–64, align "l", "c" or "r" |
| `gradient(x, y, w, h, from, to, ["h"\|"v"])` | gradient |
| `icon(x, y, name, [size], [color])` | a bundled icon |
| `item(x, y, "minecraft:iron_ingot", [size])` | an item's icon, up to 64 per frame |
| `bitmap(x, y, rows, [scale])` | rows of paint colours 0–f, a space is transparent |
| `draw()` | sends the frame, returns its size in bytes |

**Colours:** `bg`, `panel`, `text`, `dim`, `line`, `accent`, `gold`, `cyan`, `green`, `yellow`, `red`, `purple`, `orange` and `gray` follow the dashboard's light or dark theme. CC's colour names and `colors.red` and friends work too, and so does `"#rrggbb"` or `"#rrggbbaa"`.

**Input:** with `input="click"` the handler gets `(user, {type="click", x=, y=})`. With `input="drag"` it gets `down`, several `drag` and one `up`. Input needs the same right as a button (`act.buttons`). `pin` and `confirm` work like on a button and need `input="click"`. Share links show the canvas without input. Share links that hide numbers do not show canvases at all, since a canvas can draw any number.

**Limits:** 4 canvases per uplink, 1024 commands and 24 KB per frame, 128 characters per text. Call `draw()` as often as you like: 10 frames per second per canvas, 64 KB/s per uplink and 256 KB/s per mainframe go out. A frame over the limit waits and the newest one wins, so the last thing you drew always arrives. A frame that breaks a rule is an error in your program.

Frames are not saved: after a restart the card waits for your program to draw again. Cloud Tablets and Cloud Displays show a note instead of the canvas.

Without `cloud.ui`: `cloud.canvas(id, label, opts)` and `cloud.draw(id, commands)`, with commands like `{"r", 0, 0, 10, 10, "red"}`. The operations are listed in `help cloud`.

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
| `getCards()` | the cards a program may read: list of \{id, title, online, kind, widgets (a count)\}, at most 256 |
| `getCard(id)` | one card: \{id, title, online, kind, widgets=\{\{id, type, label, value, unit, min, max, warn, crit\}, …\}\} |

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

### Read other cards

A program on the mainframe's peripheral can combine the values of machine cards into its own dashboard. Try `cloud-overview`.

```lua
local mf = peripheral.find("cloud_mainframe")
for _, card in ipairs(mf.getCards()) do
  print(card.id, card.title, card.online, card.widgets)
end
-- by title and widget id:
local values = require("cloud.ui").readCards(mf)
print(values["Reactor"].temp)
```

A computer has no owner, so a program reads what a viewer sees: **never** restricted cards (Server, Claims, Backups and the like), player names only in their public form, and a blocked card without values. Reading needs a computer next to the mainframe or on its wired network. At most 10 reads per second; above that `getCards` and `getCard` return `nil, "rate_limited"`.

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

Ready-made programs from the community: **Script Store** in the mainframe's menu (admins). Each script shows what it does, what it **uses** (redstone, peripherals, internet …) and its full source – checked against the SHA-256 in the store's index before you see it. The index is signed by the store: your browser checks the signature where it can (**signed ✓**), and the Minecraft server checks it again before it writes any file. That proves the files come from the store – not that the code is safe, so **read the code before you install it**. Pick a computer and install; run it now or start it automatically. Installed store scripts show **Update available** when there is a newer version, and you are warned before overwriting a file you changed on the computer.

On the project website, **Install** opens your dashboard at `/store#install=<script>` – the same view, nothing is installed without your click. Server admins can switch the store off (`scriptStore=false`). Community scripts run on your CC computer and can control what it controls – they cannot touch the Minecraft server.


---
# Generated from the dashboard docs by tools/gen_handbook.ts – edit cloud/web/src/docs/content instead.
navigation:
  title: "cloud.ui library"
  position: 5
  icon: cccloud:cloud_uplink
---

# cloud.ui helper library

`cloud.ui` ships in every computer's ROM. It wraps the uplink in a builder and runs the event loop for you: one function per widget, handlers instead of `os.pullEvent`, timers with `every`.

## Quick start

```lua
local ui = require("cloud.ui")
local dash = ui.connect()
dash:title("Reactor")
local temp = dash:gauge("temp", "Temperature", {max=1200, unit="K", warn=900, crit=1100})
dash:button("scram", "SCRAM", {color="red", confirm=true, pin=true}, function(user)
  redstone.setOutput("back", true)
  dash:notify(user .. " pressed SCRAM", "crit")
end)
dash:every(1, function() temp:set(math.random(600, 1000)) end)
dash:run()
```

## API

| Function | Does |
|---|---|
| `ui.connect([uplink])` | finds the uplink (or takes a name / wrapped peripheral), returns `dash` |
| `dash:title(text)` | card title |
| `dash:page(id, title, opts)`, `dash:group(id, title, opts)` | layout |
| `dash:gauge(id, label, opts)` … | every widget type: button, toggle, slider, gauge, chart, progress, indicator, select, number, text, buttons, label, itemSearch, watchlist, jobs |
| `(…, opts, handler)` | optional last argument: `function(user, value)` called on use |
| `widget:set(value)` | update; `widget:remove()`, `widget:on(handler)` |
| `dash:set(id, value)`, `dash:setMany(t)` | update by id |
| `dash:on(id, handler)` | handler for a widget |
| `dash:every(seconds, fn)` | timer (first call right away) |
| `dash:onEvent(event, fn)` | any other CC event (redstone, timer, …) |
| `dash:notify(text, level)` | alert |
| `dash:clients()`, `dash:isOnline()` | status |
| `dash:clear()`, `dash:run()`, `dash:stop()` | lifecycle |

Errors inside handlers are printed and do not stop the loop. Ctrl+T ends it.

## Example: tree farm

```lua
-- startup.lua: a tree farm with lights, a counter and a harvest button
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

dash:run()
```

## Example: reactor panel

Adjust the peripheral name and methods to your reactor mod.

```lua
-- A Mekanism-style reactor panel with pages, a chart and a PIN-protected SCRAM
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

dash:run()
```

## Built-in programs

- `cloud-demo` – every widget on two pages
- `cloud-storage [bridge] [uplink]` – a custom storage dashboard for an ME / RS bridge


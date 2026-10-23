---
# Aus der Dashboard-Doku erzeugt (tools/gen_handbook.ts) – Änderungen in cloud/web/src/docs/content machen.
navigation:
  title: "cloud.ui-Bibliothek"
  position: 4
  icon: cccloud:cloud_uplink
---

# cloud.ui-Bibliothek

`cloud.ui` liegt im ROM jedes Computers. Sie verpackt den Uplink in einen Baukasten und übernimmt die Ereignisschleife: eine Funktion pro Widget, Handler statt `os.pullEvent`, Timer mit `every`.

## Schnellstart

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

| Funktion | Macht |
|---|---|
| `ui.connect([uplink])` | findet den Uplink (oder nimmt einen Namen / ein Peripheral), liefert `dash` |
| `dash:title(text)` | Kartentitel |
| `dash:page(id, title, opts)`, `dash:group(id, title, opts)` | Layout |
| `dash:gauge(id, label, opts)` … | jeder Widget-Typ: button, toggle, slider, gauge, chart, progress, indicator, select, number, text, buttons, label, itemSearch, watchlist, jobs |
| `(…, opts, handler)` | optionales letztes Argument: `function(user, value)`, wird bei Bedienung aufgerufen |
| `widget:set(value)` | aktualisieren; `widget:remove()`, `widget:on(handler)` |
| `dash:set(id, value)`, `dash:setMany(t)` | über die ID aktualisieren |
| `dash:on(id, handler)` | Handler für ein Widget |
| `dash:every(sekunden, fn)` | Timer (erster Aufruf sofort) |
| `dash:onEvent(event, fn)` | jedes andere CC-Ereignis (redstone, timer, …) |
| `dash:notify(text, level)` | Meldung |
| `dash:clients()`, `dash:isOnline()` | Status |
| `dash:clear()`, `dash:run()`, `dash:stop()` | Ablauf |

Fehler in Handlern werden ausgegeben und stoppen die Schleife nicht. Strg+T beendet sie.

## Beispiel: Baumfarm

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

## Beispiel: Reaktor-Panel

Peripheral-Name und Methoden an deinen Reaktor-Mod anpassen.

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

## Mitgelieferte Programme

- `cloud-demo` – alle Widgets auf zwei Seiten
- `cloud-storage [bridge] [uplink]` – ein eigenes Lager-Dashboard für eine ME / RS Bridge


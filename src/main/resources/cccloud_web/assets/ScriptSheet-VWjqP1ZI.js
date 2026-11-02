const __vite__mapDeps=(i,m=__vite__mapDeps,d=(m.f||(m.f=["assets/Editor-B-99iQfB.js","assets/jsxRuntime.module-BtYR33k1.js","assets/ui-DVo7ENYd.js","assets/scriptstore-B6ZAuB9S.js","assets/index-BBJ2LSfe.js","assets/icons-B3dhAFvK.js","assets/layout-Bl5nn4n2.js","assets/index-DYRHvP1r.css","assets/scripts-0xu91dBh.js"])))=>i.map(i=>d[i]);
import{_ as e,c as t,d as n,f as r,l as i,t as a}from"./jsxRuntime.module-BtYR33k1.js";import{f as o}from"./ui-DVo7ENYd.js";import{L as s,R as c,j as ee,p as l,z as te}from"./index-BBJ2LSfe.js";import{a as u,c as d,i as f,l as p,n as m,o as h,s as ne,u as g}from"./scripts-0xu91dBh.js";var _=Object.assign({"../../../../examples/cloud-test.lua":`-- CC: Cloud test program: an uplink next to this computer, a redstone lamp on the back.
local cloud = peripheral.find("cloud_uplink")
if not cloud then error("No Cloud Uplink found. Attach one to this computer.", 0) end

cloud.clear()
cloud.setTitle("Testlabor")
cloud.toggle("lamp", "Lampe (hinten)", { value = false })
cloud.button("count", "Zaehler +1", { color = "cyan" })
cloud.label("counter", "Klicks: 0")
cloud.gauge("time", "Ingame-Zeit", { min = 0, max = 24, unit = "h", warn = 20, crit = 22 })
cloud.slider("level", "Redstone-Staerke", { min = 0, max = 15, step = 1, value = 0 })
cloud.button("alarm", "Test-Alarm", { color = "red", confirm = true })

local clicks = 0
local timer = os.startTimer(1)
print("Cloud test running. Online: " .. tostring(cloud.isOnline()))

while true do
  local event, a, b, c = os.pullEvent()
  if event == "cloud_action" then
    local id, value, user = a, b, c
    print(("%s -> %s = %s"):format(tostring(user), id, tostring(value)))
    if id == "lamp" then
      redstone.setOutput("back", value)
      cloud.set("lamp", value)
    elseif id == "count" then
      clicks = clicks + 1
      cloud.set("counter", "Klicks: " .. clicks)
    elseif id == "level" then
      redstone.setAnalogOutput("back", value)
      cloud.set("level", value)
    elseif id == "alarm" then
      cloud.alert("Test-Alarm von " .. tostring(user), "crit")
    end
  elseif event == "timer" and a == timer then
    cloud.set("time", math.floor(os.time() * 10) / 10)
    timer = os.startTimer(1)
  end
end
`,"../../../../examples/control-room.lua":`-- CC: Cloud example: a small control room for one computer.
-- Needs a Cloud Uplink on this computer. Lamp/door on the back side, optional speaker.
-- Run: control-room   (stop with Ctrl+T)

local ui = require("cloud.ui")
local dash = ui.connect()
dash:clear()
dash:title("Kontrollraum")

-- Lamp on the back side: switch from the dashboard
dash:toggle("lamp", "Lampe hinten", { value = redstone.getOutput("back") }, function(user, on)
  redstone.setOutput("back", on)
  dash:set("lamp", on)
  print(user .. " -> Lampe " .. (on and "an" or "aus"))
end)

-- Open a door on the left side for 5 seconds.
-- No sleep() inside handlers: it would block all other dashboard actions. Use a timer instead.
local doorTimer
dash:button("door", "Tuer oeffnen", { color = "cyan" }, function(user)
  redstone.setOutput("left", true)
  doorTimer = os.startTimer(5)
  dash:notify(user .. " hat die Tuer geoeffnet", "info")
end)
dash:onEvent("timer", function(id)
  if id == doorTimer then
    redstone.setOutput("left", false)
    doorTimer = nil
  end
end)

-- Redstone strength 0-15 on the right side (e.g. machine speed)
dash:slider("power", "Redstone rechts", { min = 0, max = 15, step = 1, value = 0 }, function(_, level)
  redstone.setAnalogOutput("right", level)
  dash:set("power", level)
end)

-- Start a program on this computer in a new tab (advanced computer)
local programs = { "cloud-demo", "worm", "adventure" }
local chosen = programs[1]
dash:select("program", "Programm", { options = programs, value = chosen }, function(_, value)
  chosen = value
  dash:set("program", value)
end)
dash:button("start", "Starten", { color = "green", confirm = true }, function(user)
  if multishell then
    shell.openTab(chosen)
    dash:notify(user .. " startet " .. chosen, "info")
  else
    dash:notify("Programme starten braucht einen Advanced Computer", "warn")
  end
end)

-- Live data: in-game time as gauge + chart
local clock = dash:gauge("time", "Ingame-Uhrzeit", { min = 0, max = 24, unit = "h", warn = 19, crit = 22 })
local day = dash:chart("day", "Tageszeit-Verlauf", { min = 0, max = 24, points = 60 })
dash:every(1, function()
  local t = math.floor(os.time() * 10) / 10
  clock:set(t)
  day:set(t)
end)

-- Emergency button with PIN (set the PIN in-game: /cccloud pin set 1234)
dash:button("alarm", "ALARM", { color = "red", confirm = true, pin = true }, function(user)
  redstone.setOutput("top", true)
  dash:notify("ALARM ausgeloest von " .. user, "crit")
end)
dash:button("reset", "Alarm aus", { color = "gray" }, function()
  redstone.setOutput("top", false)
end)

print("Kontrollraum laeuft. Strg+T zum Beenden.")
dash:run()
`,"../../../../src/main/resources/data/computercraft/lua/rom/programs/cloud-demo.lua":`-- cloud-demo: shows every CC: Cloud widget on the dashboard.
-- Usage: cloud-demo [uplink]

local ui = require("cloud.ui")
local dash = ui.connect(...)

dash:clear()
dash:title("Cloud Demo")
dash:page("overview", "Overview", { icon = "home" })
dash:page("controls", "Controls", { icon = "gear" })
dash:group("plant", "Power plant", { page = "overview", cols = 2 })
dash:group("inputs", "Inputs", { page = "controls", cols = 2 })

-- Read-only widgets
local temp = dash:gauge("temp", "Core temperature", { min = 0, max = 1200, unit = "K", warn = 900, crit = 1100, group = "plant", icon = "flame" })
local power = dash:chart("power", "Power output", { min = 0, max = 100, unit = "MW", points = 60, interval = 1, group = "plant", span = 2 })
local fuel = dash:progress("fuel", "Fuel", { min = 0, max = 100, unit = "%", group = "plant" })
local running = dash:indicator("running", "Running", { value = true, onColor = "green", offColor = "red", group = "plant" })
local status = dash:label("status", "Starting...", { page = "overview" })

-- Inputs
local enabled = true
local mode = "mid"
dash:toggle("enabled", "Reactor enabled", { value = true, group = "inputs" }, function(user, value)
    enabled = value
    dash:notify(user .. (value and " started" or " stopped") .. " the reactor", value and "info" or "warn")
end)
dash:slider("rods", "Control rods", { min = 0, max = 100, step = 5, value = 50, unit = "%", group = "inputs" })
dash:select("mode", "Mode", { options = { "low", "mid", "high" }, value = "mid", group = "inputs" }, function(user, value) mode = value end)
dash:number("target", "Target output", { min = 0, max = 100, step = 1, value = 60, unit = "MW", group = "inputs" })
dash:text("note", "Shift note", { maxLength = 64, placeholder = "Anything to report?", page = "controls" }, function(user, value)
    print(user .. " wrote: " .. value)
end)
dash:buttons("preset", "Presets", { options = { "Eco", "Normal", "Boost" }, page = "controls" }, function(user, value)
    dash:notify(user .. " chose preset " .. value)
end)
dash:button("scram", "SCRAM", { color = "red", confirm = true, page = "controls", icon = "warning" }, function(user)
    enabled = false
    dash:set("enabled", false)
    dash:notify(user .. " pressed SCRAM", "crit")
end)

local factor = { low = 0.5, mid = 1, high = 1.5 }
local t, level = 0, 100
dash:every(1, function()
    t = t + 1
    local out = enabled and (40 + 20 * math.sin(t / 6)) * factor[mode] or 0
    level = enabled and math.max(0, level - 0.2) or level
    dash:setMany({
        temp = math.floor(300 + out * 8),
        power = math.floor(out * 10) / 10,
        fuel = math.floor(level),
        running = enabled,
    })
    status:set(("%s, %d dashboard(s) connected"):format(enabled and "Running" or "Stopped", dash:clients()))
end)

print("Cloud demo is running on " .. peripheral.getName(dash:peripheral()) .. ". Press Ctrl+T to stop.")
dash:run()
`,"../../../../src/main/resources/data/computercraft/lua/rom/programs/cloud-map.lua":`-- A map of your base on a canvas: every node of the network at its real position, with its links. Click a node for its name.
-- Needs a Cloud Mainframe next to the computer or on its wired network.
-- Usage: cloud-map [uplink]

local ui = require("cloud.ui")
local dash = ui.connect(...)
local mainframe = ui.mainframe()
if not mainframe then error("cloud-map reads the network from the mainframe: put this computer next to a Cloud Mainframe or on its wired network.", 0) end

dash:clear()
dash:title("Base map")
dash:group("map", "Base map", { w = 12, h = 20, color = "cyan", icon = "home" })
local W, H, MARGIN = 320, 180, 16
local selected
local nodes = {}

local canvas
canvas = dash:canvas("map", "Network", { width = W, height = H, input = "click", bg = "bg", group = "map", h = 18 }, function(_, ev)
    local best, distance = nil, 64
    for _, node in ipairs(nodes) do
        local d = (node.sx - ev.x) ^ 2 + (node.sy - ev.y) ^ 2
        if d < distance then best, distance = node, d end
    end
    selected = best
end)

local KIND = { mainframe = "gold", relay = "cyan", uplink = "green", bridge = "purple" }

local function draw()
    local network = mainframe.getNetwork()
    local dimension
    nodes = {}
    for _, node in ipairs(network.nodes or {}) do
        dimension = dimension or node.dim
        if node.pos and node.dim == dimension then nodes[#nodes + 1] = node end
    end
    canvas:clear()
    if #nodes == 0 then
        canvas:text(W / 2, H / 2 - 4, "No network nodes yet", "dim", { align = "c" }):draw()
        return
    end

    local minX, maxX, minZ, maxZ = math.huge, -math.huge, math.huge, -math.huge
    for _, node in ipairs(nodes) do
        minX, maxX = math.min(minX, node.pos.x), math.max(maxX, node.pos.x)
        minZ, maxZ = math.min(minZ, node.pos.z), math.max(maxZ, node.pos.z)
    end
    local scale = math.min((W - 2 * MARGIN) / math.max(16, maxX - minX), (H - 2 * MARGIN) / math.max(16, maxZ - minZ))
    local byId = {}
    for _, node in ipairs(nodes) do
        node.sx = math.floor(MARGIN + (node.pos.x - minX) * scale)
        node.sy = math.floor(MARGIN + (node.pos.z - minZ) * scale)
        byId[node.id] = node
    end

    for x = 0, W, 16 do canvas:line(x, 0, x, H, "#ffffff08") end
    for y = 0, H, 16 do canvas:line(0, y, W, y, "#ffffff08") end
    for _, link in ipairs(network.links or {}) do
        local a, b = byId[link.a], byId[link.b]
        if a and b then canvas:line(a.sx, a.sy, b.sx, b.sy, "line", 1) end
    end
    for _, node in ipairs(nodes) do
        local size = node.type == "mainframe" and 4 or 2.5
        canvas:circle(node.sx, node.sy, size, KIND[node.type] or "gray")
        if node.status and node.status ~= "online" then canvas:ring(node.sx, node.sy, size + 2, "red", 1) end
    end
    if selected and byId[selected.id] then
        local node = byId[selected.id]
        canvas:ring(node.sx, node.sy, 7, "text", 1)
        local label = (node.name and node.name ~= "" and node.name or node.type) .. "  " .. node.pos.x .. " " .. node.pos.y .. " " .. node.pos.z
        canvas:rect(0, H - 12, W, 12, "panel"):text(4, H - 10, label, "text", { size = 7 })
    end
    canvas:draw()
end

dash:every(1, draw)
print("Base map is on the dashboard. Ctrl+T ends it.")
dash:run()
`,"../../../../src/main/resources/data/computercraft/lua/rom/programs/cloud-mimic.lua":`-- A reactor mimic diagram drawn on a canvas: core, coolant loop and a turbine. Click a pump or the rods on the diagram.
-- Without a reactor it simulates one; put your own readings into read() and your own controls into the click handler.
-- Usage: cloud-mimic [uplink]

local ui = require("cloud.ui")
local dash = ui.connect(...)

-- What the diagram shows. Replace the body of read() with calls to your reactor.
local state = { temp = 400, coolant = 80, power = 0, pump = true, rods = true }
local function read()
    local heat = state.rods and -6 or 14
    local cooling = state.pump and 8 or 0
    state.temp = math.max(300, math.min(1400, state.temp + heat - cooling + math.random(-3, 3)))
    state.coolant = math.max(0, math.min(100, state.coolant + (state.pump and 1 or -1)))
    state.power = state.rods and math.max(0, state.power - 40) or math.min(1000, state.power + 25)
end

dash:clear()
dash:title("Reactor")
dash:group("plant", "Reactor mimic", { w = 8, h = 15, color = "red", icon = "flame" })
dash:group("side", "Readings", { w = 4, h = 15, icon = "chart" })
local trend = dash:chart("temp", "Core temperature", { unit = "K", min = 300, max = 1400, group = "side", h = 6 })
local power = dash:gauge("power", "Output", { max = 1000, unit = "kFE/t", group = "side", h = 6 })

local W, H = 240, 135
local hot = { x = 12, y = 30, w = 70, h = 80 }
local pump = { x = 112, y = 92, w = 32, h = 24 }
local turbine = { x = 170, y = 22, w = 58, h = 40 }

local function inside(box, x, y)
    return x >= box.x and y >= box.y and x < box.x + box.w and y < box.y + box.h
end

local canvas
canvas = dash:canvas("mimic", "Plant", { width = W, height = H, input = "click", bg = "panel", group = "plant", h = 13 }, function(user, ev)
    if inside(hot, ev.x, ev.y) then
        state.rods = not state.rods
        dash:notify(user .. (state.rods and " inserted the rods" or " pulled the rods"), "warn")
    elseif inside(pump, ev.x, ev.y) then
        state.pump = not state.pump
    end
end)

-- The pipes and the labels never change: only the live parts differ from frame to frame.
local function pipe(x1, y1, x2, y2, flowing, tick)
    local color = flowing and "cyan" or "gray"
    canvas:line(x1, y1, x2, y2, color, 3)
    if not flowing then return end
    local length = math.max(math.abs(x2 - x1), math.abs(y2 - y1))
    for offset = tick % 12, length, 12 do
        local t = offset / length
        canvas:rect(x1 + (x2 - x1) * t - 1, y1 + (y2 - y1) * t - 1, 2, 2, "text")
    end
end

local tick = 0
local function draw()
    tick = tick + 2
    local running = state.power > 0
    canvas:clear()
    canvas:gradient(0, 0, W, H, "panel", "bg", "v")
    canvas:text(hot.x, 12, "CORE", "dim", { size = 8 })
    canvas:text(turbine.x, 10, "TURBINE", "dim", { size = 8 })

    pipe(82, 44, 170, 44, running, tick)
    pipe(200, 62, 200, 104, state.pump, tick)
    pipe(200, 104, 144, 104, state.pump, tick)
    pipe(112, 104, 82, 104, state.pump, tick)

    -- core: the fill rises with the temperature, red above 1000 K
    local fill = (state.temp - 300) / 1100
    local level = hot.h * fill
    local color = state.temp > 1000 and "red" or state.temp > 800 and "orange" or "green"
    canvas:rect(hot.x, hot.y, hot.w, hot.h, "bg")
    canvas:gradient(hot.x, hot.y + hot.h - level, hot.w, level, color, "#00000000", "v")
    canvas:box(hot.x, hot.y, hot.w, hot.h, color, 2)
    canvas:text(hot.x + hot.w / 2, 62, math.floor(state.temp) .. " K", "text", { size = 12, align = "c" })
    canvas:text(hot.x + hot.w / 2, 80, state.rods and "RODS IN" or "RODS OUT", state.rods and "green" or "yellow", { align = "c" })

    -- pump: a turning rotor while it runs
    canvas:rect(pump.x, pump.y, pump.w, pump.h, "bg")
    canvas:box(pump.x, pump.y, pump.w, pump.h, state.pump and "green" or "red", 1)
    canvas:ring(pump.x + 16, pump.y + 12, 8, state.pump and "green" or "gray", 1)
    local angle = state.pump and tick / 2 or 0
    canvas:line(pump.x + 16 - math.cos(angle) * 8, pump.y + 12 - math.sin(angle) * 8, pump.x + 16 + math.cos(angle) * 8, pump.y + 12 + math.sin(angle) * 8, "text", 1)
    canvas:text(pump.x + 16, pump.y + 26, "PUMP", "dim", { size = 6, align = "c" })

    canvas:rect(turbine.x, turbine.y, turbine.w, turbine.h, "bg")
    canvas:box(turbine.x, turbine.y, turbine.w, turbine.h, running and "cyan" or "gray", 1)
    canvas:text(turbine.x + turbine.w / 2, turbine.y + 14, math.floor(state.power) .. " kFE/t", "text", { size = 8, align = "c" })
    canvas:icon(216, 118, "fan", 12, state.coolant < 25 and "red" or "dim")
    canvas:text(212, 121, "coolant " .. math.floor(state.coolant) .. "%", "dim", { size = 6, align = "r" })
    canvas:draw()
end

dash:every(0.25, function()
    read()
    draw()
end)
dash:every(1, function()
    trend:set(state.temp)
    power:set(state.power)
end)
print("Reactor mimic is on the dashboard. Ctrl+T ends it.")
dash:run()
`,"../../../../src/main/resources/data/computercraft/lua/rom/programs/cloud-overview.lua":`-- One canvas with the numbers of all your machine cards: reads the other cards on the mainframe, the same ones a viewer sees.
-- Needs a Cloud Mainframe next to the computer or on its wired network.
-- Usage: cloud-overview [uplink]

local ui = require("cloud.ui")
local dash = ui.connect(...)
local mainframe = ui.mainframe()
if not mainframe then error("cloud-overview reads cards from the mainframe: put this computer next to a Cloud Mainframe or on its wired network.", 0) end

local ROW, W, H = 14, 320, 180

dash:clear()
dash:title("Overview")
dash:group("all", "All machines", { w = 12, color = "gold", icon = "chart" })
local canvas = dash:canvas("all", "", { width = W, height = H, bg = "panel", group = "all", h = 18 })

local function draw()
    local cards, err = mainframe.getCards()
    if not cards then return printError(err) end
    canvas:clear()
    local y = 4
    for _, row in ipairs(cards) do
        if y > H - ROW then break end
        if row.title ~= "Overview" and row.widgets > 0 then
            local card = mainframe.getCard(row.id)
            for _, widget in ipairs(card and card.widgets or {}) do
                if y <= H - ROW and type(widget.value) == "number" and widget.max and widget.max > (widget.min or 0) then
                    local min = widget.min or 0
                    local share = math.max(0, math.min(1, (widget.value - min) / (widget.max - min)))
                    local color = widget.crit and widget.value >= widget.crit and "red" or widget.warn and widget.value >= widget.warn and "yellow" or "green"
                    canvas:text(4, y + 2, row.title .. " " .. (widget.label or widget.id), row.online and "text" or "dim", { size = 7 })
                    canvas:rect(190, y + 2, 100, 8, "bg"):rect(190, y + 2, 100 * share, 8, color)
                    canvas:text(316, y + 2, tostring(math.floor(widget.value)) .. (widget.unit and " " .. widget.unit or ""), "dim", { size = 7, align = "r" })
                    y = y + ROW
                end
            end
        end
    end
    if y == 4 then canvas:text(W / 2, H / 2 - 4, "No machine cards with numbers yet", "dim", { align = "c" }) end
    canvas:draw()
end

dash:every(2, draw)
print("Overview is on the dashboard. Ctrl+T ends it.")
dash:run()
`,"../../../../src/main/resources/data/computercraft/lua/rom/programs/cloud-snake.lua":`-- Snake on the dashboard: click on the side of the snake you want it to turn to. Everyone with the dashboard plays the same game.
-- Usage: cloud-snake [uplink]

local ui = require("cloud.ui")
local dash = ui.connect(...)

local CELL, COLS, ROWS = 6, 30, 20
local W, H = COLS * CELL, ROWS * CELL
local snake, dir, food, score, best, over, player

dash:clear()
dash:title("Snake")
dash:group("game", "Snake", { w = 6, color = "green", icon = "star" })
local board = dash:label("score", "Click the board to start", { group = "game" })

local function place_food()
    repeat
        food = { x = math.random(0, COLS - 1), y = math.random(0, ROWS - 1) }
        local taken = false
        for _, part in ipairs(snake) do taken = taken or (part.x == food.x and part.y == food.y) end
    until not taken
end

local function start(user)
    snake, dir, score, over, player = { { x = 15, y = 10 }, { x = 14, y = 10 }, { x = 13, y = 10 } }, { x = 1, y = 0 }, 0, false, user
    place_food()
end

local canvas = dash:canvas("board", "", { width = W, height = H, input = "click", bg = "bg", group = "game", h = 10 }, function(user, ev)
    if not snake or over then return start(user) end
    player = user
    local head = snake[1]
    local dx, dy = ev.x / CELL - head.x, ev.y / CELL - head.y
    -- turn only: a snake cannot reverse into itself
    if dir.x ~= 0 then dir = { x = 0, y = dy < 0 and -1 or 1 } else dir = { x = dx < 0 and -1 or 1, y = 0 } end
end)

local function step()
    if not snake or over then return end
    local head = { x = snake[1].x + dir.x, y = snake[1].y + dir.y }
    if head.x < 0 or head.y < 0 or head.x >= COLS or head.y >= ROWS then over = true end
    for _, part in ipairs(snake) do over = over or (part.x == head.x and part.y == head.y) end
    if over then
        if score > (best or 0) then best = score end
        board:set(player .. " scored " .. score .. " (best " .. (best or 0) .. "). Click to play again")
        return
    end
    table.insert(snake, 1, head)
    if head.x == food.x and head.y == food.y then
        score = score + 1
        board:set(player .. ": " .. score)
        place_food()
    else
        table.remove(snake)
    end
end

local function draw()
    canvas:clear()
    canvas:box(0, 0, W, H, "line", 1)
    if snake then
        canvas:rect(food.x * CELL + 1, food.y * CELL + 1, CELL - 2, CELL - 2, "red")
        for i, part in ipairs(snake) do canvas:rect(part.x * CELL, part.y * CELL, CELL - 1, CELL - 1, i == 1 and "yellow" or "green") end
    end
    if not snake or over then canvas:text(W / 2, H / 2 - 6, over and "GAME OVER" or "CLICK TO START", "text", { size = 12, align = "c" }) end
    canvas:draw()
end

dash:every(0.25, function()
    step()
    draw()
end)
print("Snake is on the dashboard. Ctrl+T ends it.")
dash:run()
`}),v={"cloud-demo.lua":`Every widget type in one program – the quickest way to see what a dashboard can do. Also built into the computer as the program cloud-demo.`},y=Object.entries(_).map(([e,t])=>{let n=e.split(`/`).pop(),r=f(t);return{name:n,title:g(n),text:v[n]??r.text,...r.run?{run:r.run}:{},code:t,lines:t.split(`
`).length}}).sort((e,t)=>e.name.localeCompare(t.name)),b=null;function re(e){let[t,i]=n(()=>b);return r(()=>{t||l(()=>import(`./Editor-B-99iQfB.js`).then(e=>i(()=>b=e.default)),__vite__mapDeps([0,1,2,3,4,5,6,7,8]))},[]),t?a(t,{...e}):null}function x({conn:l,uplinkId:f,title:g,onClose:_}){let v=i(()=>ne(l.baseUrl.replace(/\/$/,``),l.token,l.info.value?.id??``,f),[l,f]),[b,x]=n(`upload`),[S,C]=n(null),[w,T]=n(null),[E,D]=n(``),[O,k]=n(``),[A,ie]=n(!0),[j,ae]=n(!1),[M,N]=n(!1),[P,F]=n(null),[I,L]=n(!1),[R,z]=n(null),[B,V]=n(null),[H,U]=n(null),[W,G]=n(null),K=t(void 0),q=t(null),J=()=>v.list().then(e=>{C(e),T(null)}).catch(e=>T(d(e)));r(()=>{J()},[v]);let Y=e=>new Promise(t=>V({...e,resolve:t}));async function X(e){for(;;)try{let t=await e(K.current);return U(null),t}catch(e){if(!(e instanceof m)||![`pin_required`,`wrong_pin`,`pin_locked`].includes(e.code))throw U(null),e;e.code!==`pin_required`&&(K.current=void 0);let t=e.code===`pin_locked`?{kind:`locked`,text:d(e)}:e.code===`wrong_pin`?{kind:`error`,text:`Wrong PIN${e.attemptsLeft===void 0?``:` – ${e.attemptsLeft} attempts left`}.`}:{kind:`idle`},n=await new Promise(e=>U({state:t,resolve:e}));if(n===null)throw new m(0,`cancelled`);K.current=n}}let Z=async e=>{if(F(null),e.size>262144)return F(`The file is too large (max 256 KB).`);let t=await e.text();D(t),k(p(e.name)),z(null)},oe=e=>{D(e.code),k(e.name),x(`upload`),z(null),F(null)},se=async e=>{e?.preventDefault();let t=O.trim(),n=h(t)??(E.trim()?new Blob([E]).size>262144?`The file is too large (max 256 KB).`:null:`Paste some code or drop a .lua file first.`);if(n)return F(n);F(null),N(!0);try{let e=!1;if(S?.files.some(e=>e.path===t)&&(e=await Y({title:`Replace ${t}?`,text:`${t} already exists on ${S.computer.label||`Computer #${S.computer.id}`}. The old version is overwritten.`,label:`Replace`,color:`yellow`}),!e)||j&&S?.files.some(e=>e.path===`startup.lua`)&&t!==`startup.lua`&&!await Y({title:`Replace startup.lua?`,text:`The computer's startup.lua will only run ${t}.`,label:`Replace startup`,color:`yellow`}))return;try{await X(n=>v.write(t,E,{overwrite:e,startup:j,pin:n}))}catch(e){if(e instanceof m&&e.code===`exists`&&await Y({title:`Replace ${t}?`,text:`${t} already exists.`,label:`Replace`,color:`yellow`}))await X(e=>v.write(t,E,{overwrite:!0,startup:j,pin:e}));else throw e}let n=`${t} saved on ${S?.computer.label||`the computer`}.`;if(A)try{let e=await X(e=>v.run(t,`auto`,e));n+=e===`event`?` It runs in a new tab.`:``}catch(e){if(!(e instanceof m)||e.code!==`needs_reboot`)throw e;await Y({title:`Restart the computer?`,text:`This computer cannot start programs in the background (it is not an advanced computer). Restart it to run the script once? A program running now is stopped.`,label:`Restart & run`,color:`yellow`})&&(await X(e=>v.run(t,`reboot`,e)),n+=` The computer restarts and runs it.`)}z(n),o({level:`info`,title:`Script uploaded`,text:n}),J()}catch(e){e instanceof m&&e.code===`cancelled`||F(d(e))}finally{N(!1)}},ce=async e=>{try{let t=await v.read(e),n=URL.createObjectURL(new Blob([t],{type:`text/plain`})),r=document.createElement(`a`);r.href=n,r.download=e.split(`/`).pop(),r.click(),setTimeout(()=>URL.revokeObjectURL(n),1e3)}catch(e){o({level:`warn`,title:`Download failed`,text:d(e)})}},le=async e=>{if(await Y({title:`Delete ${e}?`,text:`The file is removed from the computer. This cannot be undone.`,label:`Delete`,color:`red`}))try{await X(t=>v.remove(e,t)),o({level:`info`,title:`Deleted`,text:`${e} was deleted.`}),J()}catch(e){e instanceof m&&e.code===`cancelled`||o({level:`warn`,title:`Delete failed`,text:d(e)})}},Q=S?.computer,$=O?h(O.trim()):null;return a(te,{title:`Scripts – ${g}`,onClose:_,children:[a(`div`,{class:`scripts`,children:[a(`div`,{class:`scripts-comp`,children:Q?a(e,{children:[a(`span`,{class:`led ${Q.on?`led-on`:`led-off`}`,"aria-hidden":`true`}),a(`b`,{children:Q.label||`Computer #${Q.id}`}),a(`span`,{class:`muted small`,children:[`#`,Q.id,` · `,Q.advanced?`advanced`:`basic`,` · `,u(S.used),` of `,u(S.capacity),` used`]})]}):a(`span`,{class:`muted small`,children:w??`Connecting to the computer…`})}),a(`button`,{type:`button`,class:`btn btn-small open-editor`,onClick:()=>G({}),children:`Open the editor`}),a(`div`,{class:`nd-tabs`,role:`tablist`,"aria-label":`Scripts`,children:[[`upload`,`Upload`],[`examples`,`Examples (${y.length})`],[`files`,`Files${S?` (${S.files.filter(e=>!e.dir).length})`:``}`]].map(([e,t])=>a(`button`,{type:`button`,role:`tab`,"aria-selected":b===e,class:`nd-tab ${b===e?`on`:``}`,onClick:()=>x(e),children:t},e))}),b===`upload`&&a(`form`,{class:`form scripts-upload`,onSubmit:se,novalidate:!0,children:[a(`div`,{class:`dropzone ${I?`drag`:``}`,onDragOver:e=>{e.preventDefault(),L(!0)},onDragLeave:()=>L(!1),onDrop:e=>{e.preventDefault(),L(!1);let t=e.dataTransfer?.files?.[0];t&&Z(t)},children:[a(`span`,{children:`Drop a .lua file here or`}),a(`button`,{type:`button`,class:`btn`,onClick:()=>q.current?.click(),children:`Choose file`}),a(`input`,{ref:q,type:`file`,accept:`.lua,text/plain,text/x-lua`,hidden:!0,onChange:e=>{let t=e.target.files?.[0];t&&Z(t),e.target.value=``}})]}),a(`label`,{class:`field`,children:[a(`span`,{class:`field-label`,children:`Code`}),a(`textarea`,{class:`code-area`,value:E,onInput:e=>{D(e.target.value),z(null)},placeholder:`-- paste Lua here
local ui = require("cloud.ui")
local dash = ui.connect()
…`,spellcheck:!1,autoCapitalize:`off`,autoCorrect:`off`,rows:12}),a(`span`,{class:`field-hint`,children:[E?`${E.split(`
`).length} lines · ${u(new Blob([E]).size)}`:`Max 256 KB.`,` The full editor comes later.`]})]}),a(`label`,{class:`field`,children:[a(`span`,{class:`field-label`,children:`File name on the computer`}),a(`input`,{class:`input`,value:O,onInput:e=>k(e.target.value),onBlur:()=>O&&!/\.lua$/i.test(O)&&!O.includes(`/`)&&k(p(O)),placeholder:`control-room.lua`,maxLength:129,"aria-invalid":!!$,spellcheck:!1,autoCapitalize:`off`}),$?a(`span`,{class:`field-hint err`,children:$}):S?.files.some(e=>e.path===O.trim())&&a(`span`,{class:`field-hint warn`,children:`Exists – you will be asked before it is replaced.`})]}),a(`label`,{class:`check-row`,children:[a(`input`,{type:`checkbox`,checked:A,onChange:e=>ie(e.target.checked)}),`Run it now`,Q&&!Q.advanced?` (restarts this basic computer)`:` (in a new tab)`]}),a(`label`,{class:`check-row`,children:[a(`input`,{type:`checkbox`,checked:j,onChange:e=>ae(e.target.checked)}),`Start it automatically (startup.lua)`]}),P&&a(`p`,{class:`form-error`,role:`alert`,children:P}),R&&a(`p`,{class:`form-ok`,role:`status`,children:[`✓ `,R]}),a(`button`,{type:`submit`,class:`pxbtn pxbtn-gold pxbtn-wide`,disabled:M||!E.trim()||!O.trim()||!!$,children:a(`span`,{children:M?`Uploading…`:A?`Upload & run`:`Upload`})}),a(`p`,{class:`muted small`,children:[`The code runs on this CC computer only, inside CC's sandbox – not on the Minecraft server. Admins only; the mainframe PIN is asked if one is set.`,` `,a(ee,{to:`/docs/lua#upload`,children:`More`})]})]}),b===`examples`&&a(`ul`,{class:`ex-grid`,children:y.map(e=>a(`li`,{class:`ex-card`,children:[a(`div`,{class:`ex-head`,children:[a(`b`,{children:e.title}),a(`span`,{class:`mono small muted`,children:e.name})]}),a(`p`,{class:`ex-text`,children:e.text}),a(`pre`,{class:`ex-code`,"aria-hidden":`true`,children:e.code.split(`
`).filter(e=>e.trim()&&!e.trim().startsWith(`--`)).slice(0,4).join(`
`)}),a(`div`,{class:`ex-foot`,children:[a(`span`,{class:`muted small`,children:[e.lines,` lines`]}),a(`button`,{type:`button`,class:`btn`,onClick:()=>oe(e),children:`Use this`})]})]},e.name))}),b===`files`&&a(`div`,{class:`files`,children:[S&&a(`div`,{class:`files-usage`,role:`img`,"aria-label":`${u(S.used)} of ${u(S.capacity)} used`,children:a(`span`,{ref:e=>{e&&(e.style.width=`${Math.min(100,Math.round(S.used/Math.max(1,S.capacity)*100))}%`)}})}),w&&a(`p`,{class:`form-error`,children:w}),S&&S.files.length===0&&a(`p`,{class:`muted`,children:`No files yet. Upload a script or pick an example.`}),a(`ul`,{class:`file-list`,children:S?.files.map(e=>e.dir?a(`li`,{class:`file-dir`,children:[e.path,`/`]},e.path):a(`li`,{class:`file-row ${e.path.includes(`/`)?`nested`:``}`,children:[a(`span`,{class:`file-name mono`,children:e.path.includes(`/`)?e.path.split(`/`)[1]:e.path}),a(`span`,{class:`muted small`,children:u(e.size)}),a(`span`,{class:`file-actions`,children:[a(`button`,{type:`button`,class:`btn btn-s`,onClick:()=>G({path:e.path}),children:`Edit`}),a(`button`,{type:`button`,class:`btn btn-s`,onClick:()=>ce(e.path),"aria-label":`Download ${e.path}`,children:`Download`}),a(`button`,{type:`button`,class:`btn btn-s btn-danger-ghost`,onClick:()=>le(e.path),"aria-label":`Delete ${e.path}`,children:`Delete`})]})]},e.path))})]})]}),B&&a(s,{title:B.title,text:B.text,confirmLabel:B.label,color:B.color,onCancel:()=>{B.resolve(!1),V(null)},onConfirm:()=>{B.resolve(!0),V(null)}}),H&&a(c,{title:`Mainframe PIN`,text:`Changing files on a computer needs the mainframe PIN.`,color:`gold`,state:H.state,onCancel:()=>{H.resolve(null),U(null)},onSubmit:e=>{U({...H,state:{kind:`checking`}}),H.resolve(e)}}),W&&a(re,{conn:l,uplinkId:f,title:g,initialPath:W.path,onClose:_,onBack:()=>(G(null),J())})]})}export{x as default};
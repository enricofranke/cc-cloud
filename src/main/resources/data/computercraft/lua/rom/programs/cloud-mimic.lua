-- A reactor mimic diagram drawn on a canvas: core, coolant loop and a turbine. Click a pump or the rods on the diagram.
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

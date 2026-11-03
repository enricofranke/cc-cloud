local ui = require("cloud.ui")
local dash = ui.connect()
assert(dash:peripheral() == uplink)

dash:title("Test")
-- the card design goes to the uplink as it is (the uplink checks it), nil removes it
assert(dash:style({ bg = "custom", bgColor = "#0b1a2a", parts = { gauge_fill = "color: #5ef0c4;" } }) == dash)
assert(calls[#calls][1] == "setStyle" and calls[#calls][2].parts.gauge_fill == "color: #5ef0c4;", "style passed on")
dash:style(nil)
assert(calls[#calls][1] == "setStyle" and calls[#calls][2] == nil, "style removed")
assert(not pcall(dash.style, dash, "color: red"), "a style is a table")
dash:page("main", "Main", { icon = "home" })
local temp = dash:gauge("temp", "Temp", { max = 1200, unit = "K", page = "main" })
local pressed, toggled, mode = {}, nil, nil
dash:button("scram", "SCRAM", { color = "red", confirm = true, pin = true }, function(user) pressed[#pressed + 1] = user end)
local t = dash:toggle("pump", "Pump", function(user, value) toggled = value end)
dash:select("mode", "Mode", { options = { "low", "high" } }):on(function(user, value) mode = value end)
dash:itemSearch("search", "Search", { bridge = bridge })
dash:on("explode", function() error("boom") end)
local ticks = 0
dash:every(1, function() ticks = ticks + 1; temp:set(ticks * 100) end)

-- validation errors from the uplink reach the caller
assert(not pcall(dash.gauge, dash, "Bad Id", "x"))
assert(not pcall(dash.button, dash, "b2", "B", { colour = "red" }))

os.queueEvent("cloud_action", "scram", true, "alice", "left")
os.queueEvent("cloud_action", "scram", true, "mallory", "right") -- another uplink: ignored
os.queueEvent("cloud_action", "pump", true, "bob", "left")
os.queueEvent("cloud_action", "mode", "high", "bob", "left")
os.queueEvent("cloud_action", "explode", true, "bob", "left")
local seen = 0
dash:onEvent("redstone", function() seen = seen + 1 end)
os.queueEvent("redstone")
run_until_stop(dash.run, dash)
fire_all_timers()
fire_all_timers()
run_until_stop(dash.run, dash)

assert(#pressed == 1 and pressed[1] == "alice", "button handler")
assert(toggled == true and t.value == true, "toggle handler and value")
assert(mode == "high", "select handler")
assert(seen == 1, "custom event listener")
assert(#errors == 1 and errors[1]:find("boom"), "handler errors are printed, not fatal")
assert(ticks >= 2, "timers run: " .. ticks)

local found
for _, c in ipairs(calls) do if c[1] == "itemSearch" then found = c[4].bridge end end
assert(found == "back", "a wrapped bridge is passed by name, got " .. tostring(found))

dash:setMany({ temp = 5 })
assert(temp.value == 5)
assert(dash:clients() == 2)
dash:notify("hi", "warn")

-- canvas: the helper's commands all pass the server's validation
local canvas = dash:canvas("map", "Map", { width = 100, height = 50, input = "drag", bg = "panel" }, function(user, ev)
    pressed.canvas = user .. ":" .. ev.type .. ":" .. ev.x
end)
canvas:clear("panel"):rect(0, 0, 10, 10, "red"):box(1, 1, 8, 8):line(0, 0, 5, 5, colors and colors.red or "red", 2)
canvas:circle(5, 5, 3, "cyan"):ring(5, 5, 4, "#336699"):pixel(1, 1, "gold"):text(2, 2, "hi", "text", { size = 12, align = "c" }):text(2, 2, 42)
canvas:gradient(0, 0, 10, 10, "red", "blue", "v"):icon(1, 1, "flame"):item(1, 1, "minecraft:iron_ingot"):bitmap(0, 0, { "0f", "f" }, 2)
assert(canvas:draw() > 0, "draw returns the frame size")
assert(#canvas.frame == 13)
canvas:clear()
assert(#canvas.frame == 0)
assert(not pcall(canvas.rect, canvas, 0, 0, 1, 1, "url(x)") or not pcall(canvas.draw, canvas), "bad colors fail")
assert(not pcall(dash.canvas, dash, "bad", "x", { input = "hover" }))
os.queueEvent("cloud_action", "map", { type = "down", x = 3, y = 4 }, "alice", "left")
run_until_stop(dash.run, dash)
assert(pressed.canvas == "alice:down:3", "canvas handler gets the pointer")
assert(canvas.value == nil, "a pointer event is no widget value")

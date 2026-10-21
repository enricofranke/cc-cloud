local ui = require("cloud.ui")
local dash = ui.connect()
assert(dash:peripheral() == uplink)

dash:title("Test")
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

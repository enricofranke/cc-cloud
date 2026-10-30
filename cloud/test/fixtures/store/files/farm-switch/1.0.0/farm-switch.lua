-- farm-switch: turn a farm on and off from the dashboard, with an optional day-only schedule
-- and a counter for the output chest.
-- Usage: farm-switch [side] [chest]
--   side   redstone side that powers the farm (default: back). A signal means ON.
--   chest  peripheral name of the output chest (optional, found automatically)

local ui = require("cloud.ui")
local args = { ... }

local SIDE = args[1] or "back"
local VALID = { top = true, bottom = true, left = true, right = true, front = true, back = true }
if not VALID[SIDE] then
    printError("Unknown side '" .. SIDE .. "'. Use top, bottom, left, right, front or back.")
    return
end

local chest = args[2] and peripheral.wrap(args[2]) or peripheral.find("inventory")

local dash = ui.connect()
dash:clear()
dash:title("Farm")
dash:page("farm", "Farm", { icon = "leaf" })
dash:group("main", "Farm", { page = "farm", cols = 2 })

local state = {
    on = redstone.getOutput(SIDE),
    dayOnly = false,
    since = os.epoch("utc"),
    runtime = 0, -- seconds switched on, this session
}

local function apply(on, who)
    if on == state.on then return end
    state.on = on
    state.since = os.epoch("utc")
    redstone.setOutput(SIDE, on)
    dash:set("power", on)
    dash:set("running", on)
    if who then dash:notify(("%s switched the farm %s"):format(who, on and "on" or "off"), "info") end
end

local function isDay()
    local t = os.time()
    return t >= 6 and t < 18.5
end

dash:toggle("power", "Farm power", { value = state.on, group = "main" }, function(user, on)
    apply(on, user)
end)
local running = dash:indicator("running", "Running", { value = state.on, onColor = "green", group = "main" })

dash:toggle("dayonly", "Only during the day", { value = false, group = "main" }, function(user, on)
    state.dayOnly = on
    dash:set("dayonly", on)
    print(user .. (on and " enabled" or " disabled") .. " the day-only schedule")
end)

local uptime = dash:label("uptime", "Runtime: 0 min", { group = "main" })
local items = chest and dash:chart("items", "Items in output chest", { points = 120, interval = 30, group = "main", span = 2 })
local fill = chest and dash:progress("fill", "Chest fill", { min = 0, max = 100, unit = "%", group = "main", span = 2 })

local function countChest()
    local total, slots = 0, 0
    for _, stack in pairs(chest.list()) do
        total = total + stack.count
        slots = slots + 1
    end
    return total, math.floor(slots / chest.size() * 100)
end

dash:every(5, function()
    if state.dayOnly then
        local want = isDay()
        if want ~= state.on then apply(want, nil) end
    end
    if state.on then state.runtime = state.runtime + 5 end
    running:set(state.on)
    uptime:set(("Runtime: %d min"):format(math.floor(state.runtime / 60)))
end)

if chest then
    dash:every(30, function()
        local total, percent = countChest()
        items:set(total)
        fill:set(percent)
        if percent >= 95 then dash:notify("Farm output chest is full", "warn") end
    end)
end

print(("Farm switch on side %s running. Ctrl+T to stop."):format(SIDE))
dash:run()

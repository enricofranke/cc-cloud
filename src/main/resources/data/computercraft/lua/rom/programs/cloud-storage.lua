-- cloud-storage: a custom storage dashboard for an ME or RS Cloud Bridge.
-- Usage: cloud-storage [bridge] [uplink]

local ui = require("cloud.ui")
local args = { ... }

local bridge
if args[1] then
    bridge = peripheral.wrap(args[1])
else
    bridge = peripheral.find("me_cloud_bridge") or peripheral.find("rs_cloud_bridge")
end
if not bridge then
    printError("No ME/RS Cloud Bridge found. Attach one (or a wired modem next to it) to this computer.")
    return
end
local name = peripheral.getName(bridge)
local dash = ui.connect(args[2])

dash:clear()
dash:title(peripheral.getType(bridge) == "rs_cloud_bridge" and "Refined Storage" or "ME Storage")
dash:page("storage", "Storage", { icon = "box" })
dash:page("crafting", "Crafting", { icon = "gear" })
dash:group("status", "Status", { page = "storage", cols = 2 })

local storage = dash:gauge("storage", "Storage used", { min = 0, max = 100, unit = "%", warn = 85, crit = 95, group = "status" })
local energy = dash:gauge("energy", "Energy", { min = 0, max = 100, unit = "%", group = "status", icon = "bolt" })
local online = dash:indicator("online", "Connected", { group = "status" })
local items = dash:chart("items", "Items stored", { points = 120, interval = 5, group = "status", span = 2 })
local top = dash:label("top", "-", { page = "storage" })
dash:watchlist("watch", "Watchlist", { bridge = name, page = "storage" })
dash:itemSearch("search", "Search & craft", { bridge = name, page = "crafting" })
dash:jobs("jobs", "Crafting jobs", { bridge = name, page = "crafting" })

local function pct(a, b)
    if not a or not b or b <= 0 then return 0 end
    return math.floor(a / b * 1000) / 10
end

dash:every(2, function()
    local connected = bridge.isConnected()
    online:set(connected)
    if not connected then return end
    local s = bridge.getStorage()
    storage:set(pct(s.itemsUsed, s.itemsTotal))
    energy:set(pct(bridge.getEnergy(), bridge.getMaxEnergy()))
    items:set(s.itemsUsed or 0)
    local list = bridge.listItems("", 3)
    local parts = {}
    for _, item in ipairs(list) do parts[#parts + 1] = ("%s x%d"):format(item.displayName, item.count) end
    top:set(#parts > 0 and ("Top: " .. table.concat(parts, ", ")) or "Empty")
end)

print("Storage dashboard for " .. name .. " is running. Press Ctrl+T to stop.")
dash:run()

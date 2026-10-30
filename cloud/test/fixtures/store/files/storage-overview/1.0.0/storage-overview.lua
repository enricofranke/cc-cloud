-- storage-overview: your ME or RS system at a glance, with low-stock alerts for the items you care about.
-- Needs a Cloud Uplink on this computer and an ME or RS Cloud Bridge next to it (or on a wired modem network).
-- Usage: storage-overview [bridge]
--
-- Edit the STOCK table below: item id = minimum amount. Every time an item drops below its minimum
-- the dashboard gets one warning (and one "back to normal" note when it recovers).

local ui = require("cloud.ui")
local args = { ... }

local STOCK = {
    ["minecraft:iron_ingot"] = 1000,
    ["minecraft:redstone"] = 512,
    ["minecraft:glass"] = 256,
    ["ae2:certus_quartz_crystal"] = 128,
}
local TOP = 8 -- how many of the most common items to list

local bridge = args[1] and peripheral.wrap(args[1]) or peripheral.find("me_cloud_bridge") or peripheral.find("rs_cloud_bridge")
if not bridge then
    printError("No ME/RS Cloud Bridge found. Attach one (or a wired modem next to it) to this computer.")
    return
end
local name = peripheral.getName(bridge)
local kind = peripheral.getType(bridge) == "rs_cloud_bridge" and "Refined Storage" or "ME Network"

local dash = ui.connect()
dash:clear()
dash:title(kind)
dash:page("overview", "Overview", { icon = "box" })
dash:page("stock", "Stock", { icon = "warning" })
dash:page("craft", "Crafting", { icon = "gear" })
dash:group("health", "Health", { page = "overview", cols = 3 })

local online = dash:indicator("online", "Connected", { group = "health" })
local used = dash:gauge("used", "Storage used", { min = 0, max = 100, unit = "%", warn = 80, crit = 95, group = "health" })
local energy = dash:gauge("energy", "Energy", { min = 0, max = 100, unit = "%", group = "health", icon = "bolt" })
local trend = dash:chart("trend", "Items stored", { points = 180, interval = 10, page = "overview", span = 3 })
local types = dash:label("types", "-", { page = "overview" })
local top = dash:label("top", "-", { page = "overview", span = 2 })

local low = dash:label("low", "All stocked", { page = "stock", span = 2 })
local stockBars = {}
local order = {}
for item in pairs(STOCK) do order[#order + 1] = item end
table.sort(order)
for i, item in ipairs(order) do
    local short = item:gsub("^[^:]+:", ""):gsub("_", " ")
    stockBars[item] = dash:progress("stock_" .. i, short, { min = 0, max = STOCK[item] * 2, page = "stock" })
end

dash:itemSearch("search", "Search & craft", { bridge = name, page = "craft" })
dash:jobs("jobs", "Crafting jobs", { bridge = name, page = "craft" })

local function pct(a, b)
    if not a or not b or b <= 0 then return 0 end
    return math.floor(a / b * 1000 + 0.5) / 10
end

local function short(n)
    if n >= 1e6 then return ("%.1fM"):format(n / 1e6) end
    if n >= 1e4 then return ("%.0fk"):format(n / 1e3) end
    return tostring(n)
end

local function fit(text)
    if #text > 250 then return text:sub(1, 247) .. "..." end
    return text
end

local below = {}

dash:every(5, function()
    local connected = bridge.isConnected()
    online:set(connected)
    if not connected then return end

    local s = bridge.getStorage()
    used:set(pct(s.itemsUsed, s.itemsTotal))
    energy:set(pct(bridge.getEnergy(), bridge.getMaxEnergy()))
    trend:set(s.itemsUsed or 0)
    types:set(("%s item types, %s items"):format(short(s.typesUsed or 0), short(s.itemsUsed or 0)))

    local list = bridge.listItems("", 256)
    table.sort(list, function(a, b) return a.count > b.count end)
    local parts = {}
    for i = 1, math.min(TOP, #list) do
        parts[#parts + 1] = ("%s %s"):format(short(list[i].count), list[i].displayName)
    end
    top:set(#parts > 0 and fit(table.concat(parts, " | ")) or "Empty")

    local missing = {}
    for _, item in ipairs(order) do
        local entry = bridge.getItem(item)
        local count = entry and entry.count or 0
        stockBars[item]:set(count)
        local isLow = count < STOCK[item]
        if isLow then missing[#missing + 1] = (entry and entry.displayName or item) .. " " .. count .. "/" .. STOCK[item] end
        if isLow and not below[item] then
            dash:notify(("Low stock: %s (%d of %d)"):format(entry and entry.displayName or item, count, STOCK[item]), "warn")
        elseif not isLow and below[item] then
            dash:notify(("Restocked: %s"):format(entry and entry.displayName or item), "info")
        end
        below[item] = isLow
    end
    low:set(#missing > 0 and fit("Low: " .. table.concat(missing, ", ")) or "All stocked")
end)

print("Storage overview for " .. name .. " running. Ctrl+T to stop.")
dash:run()

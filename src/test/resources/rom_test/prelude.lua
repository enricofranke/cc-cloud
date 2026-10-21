-- Minimal CraftOS stand-ins for the ROM tests. The uplink validates through Java (__create/__set).
function require(name)
    local fn = __modules[name]
    if not fn then error("module '" .. name .. "' not found", 2) end
    local cached = package_loaded and package_loaded[name]
    if cached then return cached end
    package_loaded = package_loaded or {}
    package_loaded[name] = fn(name)
    return package_loaded[name]
end

printed = {}
function print(...) local t = table.pack(...) for i = 1, t.n do t[i] = tostring(t[i]) end printed[#printed + 1] = table.concat(t, " ") end
errors = {}
function printError(msg) errors[#errors + 1] = tostring(msg) end

-- os: a scripted event queue; when it runs dry, pullEvent raises STOP_TEST to end dash:run().
local queue, next_timer = {}, 0
timers = {}
os = os or {}
function os.queueEvent(...) queue[#queue + 1] = table.pack(...) end
function os.startTimer(s) next_timer = next_timer + 1; timers[#timers + 1] = next_timer; return next_timer end
function os.pullEvent(filter)
    while true do
        local ev = table.remove(queue, 1)
        if not ev then error("STOP_TEST", 0) end
        if ev[1] == "terminate" then error("Terminated", 0) end
        if not filter or ev[1] == filter then return table.unpack(ev, 1, ev.n) end
    end
end
function fire_all_timers() local t = timers; timers = {} for _, id in ipairs(t) do os.queueEvent("timer", id) end end

-- Fake peripherals.
calls = {}
local function uplink_method(name)
    return function(...)
        calls[#calls + 1] = { name, ... }
        local a = table.pack(...)
        if name == "label" then return __create("label", a[1], a[2], a[3]) end
        if name == "set" then return __set(a[1], a[2]) end
        if name == "setMany" then for id, v in pairs(a[1]) do __set(id, v) end return end
        return __create(name, a[1], a[2], a[3])
    end
end
uplink = {}
for _, m in ipairs({ "button", "toggle", "slider", "gauge", "chart", "progress", "indicator", "select", "number", "text",
                     "buttons", "itemSearch", "watchlist", "jobs", "page", "group", "label", "set", "setMany" }) do
    uplink[m] = uplink_method(m)
end
function uplink.setTitle(t) assert(type(t) == "string" and #t <= 48, "bad title") calls[#calls + 1] = { "setTitle", t } end
function uplink.clear() calls[#calls + 1] = { "clear" } end
function uplink.remove(id) calls[#calls + 1] = { "remove", id } return true end
function uplink.notify(text, level) calls[#calls + 1] = { "notify", text, level } return true end
uplink.alert = uplink.notify
function uplink.getClients() return 2 end
function uplink.isOnline() return true end

bridge = {
    isConnected = function() return true end,
    getStorage = function() return { itemsUsed = 1234, itemsTotal = 8192, typesUsed = 3, fluidsUsed = 0 } end,
    getEnergy = function() return 500 end,
    getMaxEnergy = function() return 1000 end,
    listItems = function(filter, limit) return { { name = "minecraft:iron_ingot", displayName = "Iron Ingot", count = 1000, craftable = true } } end,
}

local devices = { left = { "cloud_uplink", uplink }, back = { "me_cloud_bridge", bridge } }
peripheral = {}
function peripheral.find(kind) for _, d in pairs(devices) do if d[1] == kind then return d[2] end end end
function peripheral.wrap(name) return devices[name] and devices[name][2] end
function peripheral.getName(p) for name, d in pairs(devices) do if d[2] == p then return name end end error("not a peripheral") end
function peripheral.getType(p)
    if type(p) == "string" then return devices[p] and devices[p][1] end
    for _, d in pairs(devices) do if d[2] == p then return d[1] end end
end

function run_until_stop(fn, ...)
    local ok, err = pcall(fn, ...)
    if ok or err ~= "STOP_TEST" then error("expected the event loop to run until STOP_TEST, got: " .. tostring(err), 0) end
end
function assert_no_errors() if #errors > 0 then error("handler errors: " .. table.concat(errors, "; "), 0) end end

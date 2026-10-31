-- The canvas programs against the fake uplink: each frame goes through the server's validation (CanvasFrame).
math.randomseed(7)
local cards = {
    { id = "a", title = "Reactor", online = true, kind = "lua", widgets = 2 },
    { id = "b", title = "Empty", online = false, kind = "lua", widgets = 0 },
}
local mainframe = {
    getNetwork = function()
        return {
            nodes = {
                { id = "m", type = "mainframe", name = "Base", dim = "minecraft:overworld", status = "online", pos = { x = 10, y = 64, z = 20 } },
                { id = "r", type = "relay", name = "", dim = "minecraft:overworld", status = "offline", pos = { x = 90, y = 70, z = -30 } },
                { id = "u", type = "uplink", name = "Far", dim = "minecraft:the_nether", pos = { x = 1, y = 70, z = 1 } },
                { id = "x", type = "uplink", dim = "minecraft:overworld" },
            },
            links = { { a = "m", b = "r" }, { a = "m", b = "u" } },
        }
    end,
    getCards = function() return cards end,
    getCard = function(id)
        if id ~= "a" then return nil end
        return { id = "a", title = "Reactor", online = true, widgets = {
            { id = "temp", type = "gauge", label = "Temp", value = 812, min = 0, max = 1200, unit = "K", warn = 800, crit = 1100 },
            { id = "note", type = "label", value = "fine" },
            { id = "fuel", type = "gauge", value = 3, max = 100 },
        } }
    end,
}
devices.right = { "cloud_mainframe", mainframe }

local function play(name, ...)
    os.queueEvent("__start")
    for _, event in ipairs({ ... }) do os.queueEvent(table.unpack(event)) end
    run_until_stop(__programs[name])
    for _ = 1, 3 do fire_all_timers(); run_until_stop(__programs[name]) end
    assert_no_errors()
end

play("cloud-mimic",
    { "cloud_action", "mimic", { type = "click", x = 20, y = 50 }, "alice", "left" },
    { "cloud_action", "mimic", { type = "click", x = 120, y = 100 }, "alice", "left" })
play("cloud-map", { "cloud_action", "map", { type = "click", x = 16, y = 16 }, "alice", "left" })
play("cloud-snake", { "cloud_action", "board", { type = "click", x = 5, y = 5 }, "alice", "left" }, { "cloud_action", "board", { type = "click", x = 5, y = 5 }, "bob", "left" })
play("cloud-overview")

local read = require("cloud.ui").readCards(mainframe)
assert(read.Reactor.temp == 812 and read.Reactor.note == "fine", "readCards by title and widget id")

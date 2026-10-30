-- More CraftOS stand-ins for the Script Store programs (runs after prelude.lua): redstone, CC's os.time/date/epoch,
-- shell, and the peripherals the store scripts look for (a Mekanism fission reactor adapter, a chest).
local outputs, analog = {}, {}
redstone = {
    getSides = function() return { "top", "bottom", "left", "right", "front", "back" } end,
    getInput = function(side) return false end,
    getAnalogInput = function(side) return 0 end,
    getOutput = function(side) return outputs[side] == true end,
    setOutput = function(side, on) assert(type(on) == "boolean", "setOutput needs a boolean") outputs[side] = on end,
    getAnalogOutput = function(side) return analog[side] or 0 end,
    setAnalogOutput = function(side, level)
        assert(type(level) == "number" and level >= 0 and level <= 15, "analog level 0-15")
        analog[side] = level
    end,
}
rs = redstone

-- CC semantics: os.time() is the in-game time (0-24), os.epoch() milliseconds.
function os.time() return 12.5 end
function os.date(format) return format == "*t" and { year = 2026, month = 1, day = 1, hour = 12, min = 30, sec = 0 } or "12:30" end
function os.epoch() return 1767225600000 end
function os.clock() return 1 end
function os.cancelTimer(id) end
function os.getComputerID() return 1 end

shell = { openTab = function() return 2 end, run = function() return true end }

local reactor = {
    getStatus = function() return false end,
    getTemperature = function() return 400 end,
    getDamagePercent = function() return 0 end,
    getBurnRate = function() return 1 end,
    getActualBurnRate = function() return 0 end,
    getMaxBurnRate = function() return 20 end,
    setBurnRate = function(rate) end,
    getCoolantFilledPercentage = function() return 0.9 end,
    getHeatedCoolantFilledPercentage = function() return 0.1 end,
    getFuelFilledPercentage = function() return 0.5 end,
    getWasteFilledPercentage = function() return 0.1 end,
    activate = function() end,
    scram = function() end,
}
local chest = {
    size = function() return 27 end,
    list = function() return { [1] = { name = "minecraft:wheat", count = 40 }, [2] = { name = "minecraft:wheat_seeds", count = 12 } } end,
}
bridge.getItem = function(name) return { name = name, displayName = name, count = 100, craftable = true } end

local devices = {
    left = { "cloud_uplink", uplink },
    back = { "me_cloud_bridge", bridge },
    right = { "fissionReactorLogicAdapter", reactor },
    top = { "inventory", chest },
}
function peripheral.find(kind) for _, d in pairs(devices) do if d[1] == kind then return d[2] end end end
function peripheral.wrap(name) return devices[name] and devices[name][2] end
function peripheral.getName(p) for name, d in pairs(devices) do if d[2] == p then return name end end error("not a peripheral") end
function peripheral.getType(p)
    if type(p) == "string" then return devices[p] and devices[p][1] end
    for _, d in pairs(devices) do if d[2] == p then return d[1] end end
end
function peripheral.getNames() local names = {} for name in pairs(devices) do names[#names + 1] = name end return names end
function peripheral.isPresent(name) return devices[name] ~= nil end

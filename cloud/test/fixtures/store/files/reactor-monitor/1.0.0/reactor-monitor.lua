-- reactor-monitor: a Mekanism fission reactor on your dashboard, with an automatic safety SCRAM.
-- Needs a Cloud Uplink on this computer and a Fission Reactor Logic Adapter next to it
-- (or on a wired modem network). Usage: reactor-monitor [adapter]
--
-- The auto-SCRAM runs on this computer, so it keeps protecting the reactor even when nobody
-- has the dashboard open. Set a PIN for the SCRAM/Start buttons in-game: /cccloud pin set 1234

local ui = require("cloud.ui")
local args = { ... }

-- Safety limits: SCRAM when any of these is reached.
local LIMITS = {
    temperature = 1100, -- Kelvin (Mekanism damages the reactor above 1200 K)
    damage = 1,         -- percent
    coolantMin = 15,    -- percent of the coolant tank
    wasteMax = 85,      -- percent of the waste tank
    heatedMax = 90,     -- percent of the heated coolant tank
}

local reactor = args[1] and peripheral.wrap(args[1]) or peripheral.find("fissionReactorLogicAdapter")
if not reactor then
    printError("No Fission Reactor Logic Adapter found. Place one on the reactor and connect it to this computer.")
    return
end

local dash = ui.connect()
dash:clear()
dash:title("Fission Reactor")
dash:page("core", "Reactor", { icon = "flame" })
dash:page("tanks", "Tanks", { icon = "drop" })
dash:group("status", "Status", { page = "core", cols = 2 })
dash:group("control", "Control", { page = "core", cols = 2 })

local running = dash:indicator("running", "Running", { value = false, onColor = "green", group = "status" })
local temp = dash:gauge("temp", "Temperature", { min = 0, max = 1200, unit = "K", warn = 900, crit = LIMITS.temperature, group = "status" })
local damage = dash:gauge("damage", "Damage", { min = 0, max = 100, unit = "%", warn = 0.5, crit = LIMITS.damage, group = "status" })
local burn = dash:gauge("burn", "Burn rate", { min = 0, max = reactor.getMaxBurnRate(), unit = "mB/t", group = "status" })
local history = dash:chart("history", "Temperature (last 5 min)", { min = 0, max = 1200, unit = "K", points = 150, interval = 2, group = "status", span = 2 })
local reason = dash:label("reason", "Auto-SCRAM armed", { group = "control", span = 2 })

dash:slider("rate", "Target burn rate", { min = 0, max = reactor.getMaxBurnRate(), step = 0.1, value = reactor.getBurnRate(), unit = "mB/t", group = "control", span = 2 }, function(user, value)
    reactor.setBurnRate(value)
    dash:set("rate", value)
    print(user .. " set burn rate to " .. value)
end)

dash:button("start", "Start", { color = "green", confirm = true, pin = true, group = "control" }, function(user)
    if reactor.getStatus() then return end
    local ok, err = pcall(reactor.activate)
    if ok then
        reason:set("Started by " .. user)
        dash:notify(user .. " started the reactor", "info")
    else
        dash:notify("Start failed: " .. tostring(err), "warn")
    end
end)

dash:button("scram", "SCRAM", { color = "red", confirm = true, pin = true, group = "control" }, function(user)
    if reactor.getStatus() then reactor.scram() end
    reason:set("SCRAM by " .. user)
    dash:notify(user .. " pressed SCRAM", "crit")
end)

local coolant = dash:progress("coolant", "Coolant", { min = 0, max = 100, unit = "%", page = "tanks" })
local heated = dash:progress("heated", "Heated coolant", { min = 0, max = 100, unit = "%", page = "tanks" })
local fuel = dash:progress("fuel", "Fuel", { min = 0, max = 100, unit = "%", page = "tanks" })
local waste = dash:progress("waste", "Waste", { min = 0, max = 100, unit = "%", page = "tanks" })

local function pct(fraction)
    return math.floor((fraction or 0) * 1000 + 0.5) / 10
end

local function check(values)
    if values.temp >= LIMITS.temperature then return ("temperature %d K"):format(values.temp) end
    if values.damage >= LIMITS.damage then return ("damage %.1f %%"):format(values.damage) end
    if values.coolant < LIMITS.coolantMin then return ("coolant %.1f %%"):format(values.coolant) end
    if values.waste >= LIMITS.wasteMax then return ("waste %.1f %%"):format(values.waste) end
    if values.heated >= LIMITS.heatedMax then return ("heated coolant %.1f %%"):format(values.heated) end
end

dash:every(1, function()
    local values = {
        temp = math.floor(reactor.getTemperature()),
        damage = reactor.getDamagePercent(),
        coolant = pct(reactor.getCoolantFilledPercentage()),
        heated = pct(reactor.getHeatedCoolantFilledPercentage()),
        fuel = pct(reactor.getFuelFilledPercentage()),
        waste = pct(reactor.getWasteFilledPercentage()),
    }
    local active = reactor.getStatus()

    running:set(active)
    temp:set(values.temp)
    damage:set(values.damage)
    burn:set(reactor.getActualBurnRate())
    history:set(values.temp)
    coolant:set(values.coolant)
    heated:set(values.heated)
    fuel:set(values.fuel)
    waste:set(values.waste)

    local problem = active and check(values)
    if problem then
        reactor.scram()
        reason:set("Auto-SCRAM: " .. problem)
        dash:notify("Reactor auto-SCRAM: " .. problem, "crit")
        print("Auto-SCRAM: " .. problem)
    end
end)

print("Reactor monitor running. Ctrl+T to stop (the reactor keeps its state).")
dash:run()

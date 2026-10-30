-- door-controller: open your base door from the dashboard, close it automatically,
-- lock it down with a PIN and see who used it last.
-- Usage: door-controller [door-side] [button-side]
--   door-side    redstone side wired to the door / piston (default: left)
--   button-side  optional side with a button or pressure plate inside the base;
--                a signal there opens the door too (ignored during lockdown)

local ui = require("cloud.ui")
local args = { ... }

local DOOR = args[1] or "left"
local BUTTON = args[2]
local VALID = { top = true, bottom = true, left = true, right = true, front = true, back = true }
for _, side in ipairs({ DOOR, BUTTON }) do
    if side and not VALID[side] then
        printError("Unknown side '" .. side .. "'. Use top, bottom, left, right, front or back.")
        return
    end
end

local dash = ui.connect()
dash:clear()
dash:title("Base door")
-- Groups take page and cols only; the card shows the icon of its first widget.
dash:group("door", "Door", { cols = 2 })

local closeAfter = 5
local lockdown = false
local timer
local log = {}

local open = dash:indicator("open", "Door open", { value = false, onColor = "yellow", group = "door", icon = "lock" })
local locked = dash:indicator("locked", "Lockdown", { value = false, onColor = "red", group = "door" })
local last = dash:label("last", "Nobody yet", { group = "door", span = 2 })

local function remember(text)
    table.insert(log, 1, os.date("%H:%M") .. " " .. text)
    while #log > 4 do table.remove(log) end
    last:set(table.concat(log, " | "):sub(1, 250))
end

local function setOpen(on)
    redstone.setOutput(DOOR, on)
    open:set(on)
    if timer then os.cancelTimer(timer) timer = nil end
    if on and closeAfter > 0 then timer = os.startTimer(closeAfter) end
end

dash:button("toggle", "Open / close", { color = "cyan", group = "door" }, function(user)
    if lockdown then
        dash:notify(user .. " tried to open the door during lockdown", "warn")
        return
    end
    local now = not redstone.getOutput(DOOR)
    setOpen(now)
    remember(user .. (now and " opened" or " closed"))
end)

dash:slider("delay", "Close after", { min = 0, max = 30, step = 1, value = closeAfter, unit = "s", group = "door" }, function(user, value)
    closeAfter = value
    dash:set("delay", value)
end)

dash:toggle("lockdown", "Lockdown", { value = false, color = "red", pin = true, group = "door", span = 2 }, function(user, on)
    lockdown = on
    dash:set("lockdown", on)
    locked:set(on)
    if on then setOpen(false) end
    remember(user .. (on and " locked down" or " lifted lockdown"))
    dash:notify(("%s %s the lockdown"):format(user, on and "started" or "ended"), on and "warn" or "info")
end)

dash:onEvent("timer", function(id)
    if id == timer then
        timer = nil
        redstone.setOutput(DOOR, false)
        open:set(false)
    end
end)

if BUTTON then
    local was = redstone.getInput(BUTTON)
    dash:onEvent("redstone", function()
        local now = redstone.getInput(BUTTON)
        if now and not was and not lockdown then
            setOpen(true)
            remember("button inside")
        end
        was = now
    end)
end

setOpen(false)
print(("Door controller on side %s running. Ctrl+T to stop."):format(DOOR))
dash:run()

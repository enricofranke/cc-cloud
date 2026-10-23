-- Soak test program (tools/e2e/soak.mjs): updates the uplink's widgets every game tick and counts dashboard actions.
-- Writes soak_status.json every 20 ticks so the harness can check that the computer keeps up (about 1200 ticks/min).
local cloud = peripheral.find("cloud_uplink")
if not cloud then error("no cloud_uplink attached") end

local function try(f, ...)
  local ok, err = pcall(f, ...)
  return ok, err
end

try(cloud.clear)
try(cloud.setTitle, "Soak Lua")
try(cloud.gauge, "load", "Load", { min = 0, max = 100, unit = "%", history = "1h" })
try(cloud.chart, "wave", "Wave", { min = 0, max = 100, history = "1h" })
try(cloud.label, "tick", "tick 0")
try(cloud.toggle, "flag", "Flag", { value = false })
try(cloud.slider, "level", "Level", { min = 0, max = 100, step = 1, value = 50 })

local tick, actions, errors, lastError = 0, 0, 0, nil
local started = os.epoch("utc")
local timer = os.startTimer(0.05)
while true do
  local ev = { os.pullEvent() }
  if ev[1] == "timer" and ev[2] == timer then
    tick = tick + 1
    local ok, err = try(cloud.setMany, {
      load = (tick * 7) % 101,
      wave = math.floor(50 + 50 * math.sin(tick / 20)),
      tick = "tick " .. tick,
    })
    if not ok then errors = errors + 1; lastError = tostring(err) end
    if tick % 20 == 0 then
      local f = fs.open("soak_status.json", "w")
      f.write(textutils.serializeJSON({ tick = tick, actions = actions, errors = errors, lastError = lastError,
        started = started, time = os.epoch("utc") }))
      f.close()
    end
    timer = os.startTimer(0.05)
  elseif ev[1] == "cloud_action" then
    actions = actions + 1
  end
end

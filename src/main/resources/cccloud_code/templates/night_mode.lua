-- Night mode: lights on at dusk, off in the morning (in-game time), and a quiet hour for the farms.
local LIGHTS = "Base lights"   -- card with a "power" toggle
local FARM = "Farm"            -- card with an "active" toggle

cloud.at({gameTime = 13000}, function()   -- dusk
  cloud.set(LIGHTS, "power", true)
  cloud.set(FARM, "active", false)
  cloud.log("night: lights on, farm paused")
end)

cloud.at({gameTime = 0}, function()       -- 6:00 in the morning
  cloud.set(LIGHTS, "power", false)
  cloud.set(FARM, "active", true)
  cloud.log("morning: lights off, farm running")
end)

-- Right now: is it night already?
local t = cloud.gameTime()
local night = t >= 13000 and t < 23000
cloud.set(LIGHTS, "power", night)
cloud.log("started at game time", t, night and "(night)" or "(day)")

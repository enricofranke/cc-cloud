-- Reactor guard: switches the reactor off when it gets too hot and back on once it cooled down.
-- Change the names below to your card and widgets (hover a card title on the dashboard to see them).
local CARD = "Reactor"     -- card title or id
local HEAT = "heat"        -- a gauge or number widget
local SWITCH = "active"    -- the toggle that runs the reactor
local HOT, COOL = 80, 40   -- off above HOT, on again below COOL

cloud.on("above", {card = CARD, widget = HEAT, value = HOT}, function(e)
  local ok, err = cloud.set(CARD, SWITCH, false)
  if ok then
    cloud.notify(("Reactor off: heat %d"):format(e.value), "crit")
  else
    cloud.warn("could not switch the reactor off: " .. err)
  end
end)

cloud.on("below", {card = CARD, widget = HEAT, value = COOL}, function(e)
  if cloud.store.get("auto_restart") ~= false then
    cloud.set(CARD, SWITCH, true)
    cloud.log("cooled down to", e.value, "- reactor on again")
  end
end)

cloud.log("guarding", CARD, "- off above", HOT, "on below", COOL)

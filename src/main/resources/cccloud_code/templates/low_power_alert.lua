-- Low power alert: a push notification when the mainframe's buffer runs low, and one when it is full again.
local LOW, OK = 20, 60   -- percent

cloud.on("below", {card = "mainframe", widget = "percent", value = LOW}, function(e)
  cloud.notify(("Mainframe power low: %.0f %%"):format(e.value), "warn")
end)

cloud.on("above", {card = "mainframe", widget = "percent", value = OK}, function(e)
  cloud.toast(("Power back to %.0f %%"):format(e.value))
end)

local mf = cloud.getMainframe()
cloud.log(("watching %s: %.0f %% now"):format(mf.name, mf.percent))

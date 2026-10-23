-- A handler that sleeps must not block other actions (each handler runs in its own coroutine).
local ui = require("cloud.ui")
local dash = ui.connect()
local log = {}
dash:button("slow", "Slow", function(user) log[#log + 1] = "slow start"; sleep(5); log[#log + 1] = "slow end" end)
dash:button("fast", "Fast", function(user) log[#log + 1] = "fast"; fire_all_timers() end)

os.queueEvent("cloud_action", "slow", true, "alice", "left")
os.queueEvent("cloud_action", "fast", true, "bob", "left")
run_until_stop(dash.run, dash)
assert(table.concat(log, ",") == "slow start,fast,slow end", "fast ran while slow slept: " .. table.concat(log, ","))
assert_no_errors()

-- The pool is bounded: with ui.MAX_HANDLERS sleeping handlers, further actions wait in the queue.
ui.MAX_HANDLERS = 2
local started = 0
dash:button("nap", "Nap", function() started = started + 1; sleep(10) end)
for _ = 1, 3 do os.queueEvent("cloud_action", "nap", true, "c", "left") end
run_until_stop(dash.run, dash)
assert(started == 2, "only MAX_HANDLERS handlers run at once, got " .. started)

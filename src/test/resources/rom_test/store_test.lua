-- One Script Store program (__store_program, from StoreScriptsTest) against the validating fake uplink: its declarations,
-- a few timer rounds and one dashboard action per interactive widget. Any error (also inside a handler) fails the test.
local program = __store_program

run_until_stop(program)
assert_no_errors()

-- A valid value for every interactive widget the program declared, sent like the dashboard does.
local actions = {}
for _, c in ipairs(calls) do
    local kind, id, opts = c[1], c[2], c[4] or {}
    local value
    if kind == "button" or kind == "toggle" then
        value = true
    elseif kind == "slider" or kind == "number" then
        value = opts.min or 0
    elseif kind == "select" or kind == "buttons" then
        value = opts.options and opts.options[1]
    elseif kind == "text" then
        value = "test"
    end
    if value ~= nil and not actions[id] then
        actions[id] = true
        os.queueEvent("cloud_action", id, value, "tester", "left")
    end
end
assert(next(calls) ~= nil, "the program declared nothing")

for _ = 1, 3 do
    fire_all_timers()
    run_until_stop(program)
    assert_no_errors()
end

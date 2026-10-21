run_until_stop(__programs["cloud-storage"])
fire_all_timers()
run_until_stop(__programs["cloud-storage"])
assert_no_errors()
local top
for _, c in ipairs(calls) do if c[1] == "set" and c[2] == "top" then top = c[3] end end
assert(top and top:find("Iron Ingot x1000"), "top items label: " .. tostring(top))

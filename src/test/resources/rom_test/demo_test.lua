os.queueEvent("cloud_action", "mode", "high", "alice", "left")
os.queueEvent("cloud_action", "enabled", false, "alice", "left")
os.queueEvent("cloud_action", "preset", "Boost", "alice", "left")
os.queueEvent("cloud_action", "note", "all good", "alice", "left")
os.queueEvent("cloud_action", "scram", true, "alice", "left")
run_until_stop(__programs["cloud-demo"])
for _ = 1, 3 do fire_all_timers(); run_until_stop(__programs["cloud-demo"]) end
assert_no_errors()

-- E2E probe for the CC: Cloud bridges inside the real modpack. Placed as startup.lua by tools/e2e/scenario.mjs.
-- KIND is replaced by the scenario ("rs_cloud_bridge" or "me_cloud_bridge"). Writes e2e_result.json every 5 s.
local KIND = "__KIND__"
local bridge = peripheral.find(KIND)
local cloud = peripheral.find("cloud_uplink")

local function try(f, ...)
  local r = table.pack(pcall(f, ...))
  if r[1] then return table.unpack(r, 2, r.n) end
  return { error = tostring(r[2]) }
end

local setup = {}
if cloud then
  setup.title = { pcall(cloud.setTitle, "E2E Lua " .. KIND) }
  setup.gauge = { pcall(cloud.gauge, "iron", "Iron ingots", { min = 0, max = 256 }) }
  setup.label = { pcall(cloud.label, "types", "Types: ?") }
  if bridge then
    setup.search = { pcall(cloud.itemSearch, "search", "Search", { bridge = peripheral.getName(bridge) }) }
    setup.watch = { pcall(cloud.watchlist, "watch", "Watchlist", { bridge = peripheral.getName(bridge) }) }
  end
end

local craftResult, round = nil, 0
while true do
  round = round + 1
  local out = { round = round, time = os.epoch("utc"), bridge = bridge and peripheral.getName(bridge) or nil, uplink = cloud ~= nil, setup = setup }
  if bridge then
    out.connected = try(bridge.isConnected)
    out.energy = try(bridge.getEnergy)
    out.maxEnergy = try(bridge.getMaxEnergy)
    out.energyUsage = try(bridge.getEnergyUsage)
    out.storage = try(bridge.getStorage)
    out.items = try(bridge.listItems)
    out.findIron = try(bridge.findItems, "iron")
    out.findModFilter = try(bridge.findItems, "@minecraft")
    out.getIron = try(bridge.getItem, "minecraft:iron_ingot")
    out.isCraftableIronBlock = try(bridge.isCraftable, "minecraft:iron_block")
    if round == 1 then craftResult = { try(bridge.craft, "minecraft:iron_block", 1) } end
    out.craftIronBlock = craftResult
    out.craftUnknown = (round == 1) and { try(bridge.craft, "minecraft:not_an_item", 1) } or nil
    out.jobs = try(bridge.getCraftingJobs)
    out.fluids = try(bridge.listFluids)
    if cloud and type(out.getIron) == "table" then
      pcall(cloud.set, "iron", out.getIron.count or 0)
      if type(out.storage) == "table" then pcall(cloud.set, "types", "Types: " .. tostring(out.storage.typesUsed)) end
    end
  end
  if cloud then out.online = try(cloud.isOnline); out.clients = try(cloud.getClients) end
  local ok, json = pcall(textutils.serializeJSON, out)
  if not ok then json = textutils.serialize({ serializeError = json, fallback = textutils.serialize(out) }) end
  local f = fs.open("e2e_result.json", "w")
  f.write(json)
  f.close()
  sleep(5)
end

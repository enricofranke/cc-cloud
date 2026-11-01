---
# Generated from the dashboard docs by tools/gen_handbook.ts – edit cloud/web/src/docs/content instead.
navigation:
  title: "Bridges (AE2 / RS)"
  position: 12
  icon: cccloud:cloud_core
item_ids:
  - cccloud:me_cloud_bridge
  - cccloud:rs_cloud_bridge
---

# Bridges: AE2 and Refined Storage

The **ME Cloud Bridge** (Applied Energistics 2) and **RS Cloud Bridge** (Refined Storage) connect a storage network to the dashboard. They only exist when the mod is installed.

## Set up

1. Craft the bridge ([recipe](index.md)) and place it **on the network** like any other device (ME: uses one channel; RS: a network node).
2. Put a **Cloud Uplink directly on the bridge** and link the uplink with the Link Card.
3. The dashboard shows a ready-made **Storage** card – no computer, no code.

The bridge uses the network's own power, no FE for the mainframe.

## The storage card

- gauges: storage used, energy
- labels: total items, item types, energy usage
- **search**: type a name or id, see counts, a "craftable" badge and the mod of each item
- **watchlist** and **crafting jobs** (below)

## Crafting from the dashboard

Tap a craftable item → pick **1 / 16 / 64 / custom** → **Craft**. The job appears under crafting jobs with a progress bar. Errors are explained: no pattern, all CPUs busy, amount too large (limit `maxCraftAmount`, default 100000). Only devices with control rights can craft.

**Not enough items:** the dialog lists what is missing with have / need and, when the mod can tell, how many are possible now – **Craft 37** makes just those. **Order anyway** keeps the whole amount: it waits as *Waiting for items* and starts by itself once the items are there. Orders survive a restart, up to 8 per bridge, and you can cancel them. A Lua `craft()` or a rule waits the same way; `getCraftingJobs()` shows the `missing` list.

**Stuck:** a job that has not moved for `craftStuckSeconds` (server config, default 60, 0 = off) gets a red **Stuck** badge with the reason, one alert, and can start a rule (“a craft gets stuck”). AE2 does not say which machine hangs, so set it above your slowest machine. **Retry** cancels the job and asks for the same craft again; it is only offered for crafts started in the dashboard, because a Lua program or rule may request it again itself.

## Factory Manager program

The **\</>** button on a Factory Manager card shows the program on its disk, with every error and warning and a copy button. It tells your base's layout, so it needs “See coordinates”, and never appears on share links or displays.

With **Write machine programs** (`device.program`, admin and owner) you can edit it, paste SFML or load a .sfml, .sfm or .txt file of up to 32 KB. **Check and review** compiles it and shows the old and new line count and what changed. A program with errors is only written if you confirm it, because the manager stops running. The PIN is asked if the mainframe has one, every write lands in the audit log, and **Undo last change** restores the old program once, until the server restarts.

## Watchlist

Add items with a minimum amount (from a search result or by id). Items below their minimum are listed first in red and trigger a **warn** alert once per crossing. Edit the minimum or remove items any time. The watchlist is stored in the bridge.

## Browse, auto-stock and crafting CPUs

- **Browse** (next to the search): every item as a grid with icons – sorted by amount, name or mod, with a **Craftable** tab, **Fluids** and ★ **favourites** pinned on top. Scroll for more.
- **Item details:** amount, a 24-hour chart, recent jobs, ★, **Craft…** and **Keep in stock…**.
- **Craft preview:** before a craft starts you see what is taken from storage, what gets crafted and what is missing, plus free CPUs. Tick **Notify me** to get a notice when it is done or fails.
- **Keep in stock (auto-stock):** set a minimum and a target – when the amount drops below the minimum and there is a pattern, the server crafts up to the target in batches of 64 (one job per item, every 30 s at most). The badge shows the state: crafting, waiting for a CPU, missing resources or paused after 3 failures. While every crafting CPU is busy, the item has one "Waiting for CPU" row in the crafting jobs – it is tried again every 30 s and never counts as a failure. It runs on the server – no computer needed. From Lua: `bridge.setAutoStock(name, min, target, enabled)`.
- **CPUs & jobs:** every crafting CPU with its current job, all running jobs, and **Cancel** (operators).
- **Production:** what the network gains and uses per minute, and what eats an item – see [production statistics](automation.md).

## From Lua

```lua
local bridge = peripheral.find("me_cloud_bridge") or peripheral.find("rs_cloud_bridge")
local s = bridge.getStorage()
print(("Items: %d / %d"):format(s.itemsUsed, s.itemsTotal))
for _, item in ipairs(bridge.findItems("iron", 5)) do
  print(item.displayName, item.count, item.craftable and "(craftable)" or "")
end
local result = bridge.craft("minecraft:iron_block", 4)
if result.ok then print("Job " .. result.jobId) else print("Error: " .. result.error) end
```

Your own storage card with the same widgets:

```lua
local cloud = peripheral.find("cloud_uplink")
local bridge = peripheral.find("me_cloud_bridge")
local name = peripheral.getName(bridge)
cloud.setTitle("Storage")
cloud.itemSearch("search", "Search & craft", {bridge=name, max=1024})
cloud.watchlist("watch", "Watchlist", {bridge=name})
cloud.jobs("jobs", "Crafting jobs", {bridge=name})
```

Or just run `cloud-storage`. All methods: [Lua reference → Bridges](lua.md).


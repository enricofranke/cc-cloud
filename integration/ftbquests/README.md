# FTB Quests chapters for ATM11: CC: Tweaked and CC: Cloud

Two quest chapters in the format of All the Mods 11 (0.12.0-beta, Minecraft 26.1.2, FTB Quests 26.1.2.9).
Both sit in ATM11's **Tech** chapter group (`2B51AC12041E3F89`), after EnderIO/XyCraft (`order_index` 12 and 13).

| Chapter | File | Quests |
|---|---|---|
| CC: Tweaked | `chapters/cc_tweaked.json5` | 24: computer, shell/help, edit, advanced computer, disk drive and floppies, startup, pocket computer, speaker, printer, redstone relay, monitors, advanced monitors, wired modems and cable, wireless modem/rednet, ender modem, GPS, turtles, mining turtle, crafty turtle, inventory and energy/fluid peripherals, Energy Meter, Energized Power, Logistics Networks |
| CC: Cloud (current) | `chapters/cc_cloud.json5` | 13: only blocks and items in CC: Cloud 0.2.0 |
| CC: Cloud (full) | `variants/full/chapters/cc_cloud.json5` | 26: the 13 above plus a "coming soon" section (relays, upgrade cards, Quantum Core, tablets, display, wireless redstone, rules) |

The CC: Cloud chapter starts after the CC: Tweaked quest **Advanced Computer** (cross-chapter dependency plus a quest link).
Quests that depend on other mods (Energy Meter, Energized Power, Logistics Networks, GPS, ME/RS Cloud Bridge, kill switch) are `optional: true`, so the chapters can be completed without them.
The ME and RS Cloud Bridge quests are both optional; the follow-up quest (watchlist and crafting) needs only one of them (`dependency_requirement: "one_completed"`). FTB Quests has no "mod is loaded" condition; ATM11 ships AE2 and Refined Storage, so both items exist in the pack.

## Conventions taken from ATM11
- Text is not in the chapter files: titles, subtitles, descriptions and checkmark-task titles live in `lang/<locale>/chapters/<file>.json5`, chapter titles in the shared `lang/<locale>/chapter.json5`.
- 16-digit uppercase hex ids (first digit 0-7), unique against every id in the pack's quest book.
- `default_quest_shape: ""` (pack default), shapes `hexagon` for milestones and `gear` for the info quest, size 1.0-1.5, coordinates on a 0.5 grid (`grid_scale: 0.5`).
- Colour codes like the pack (`&e` items, `&b` concepts, `&a` commands, `&2` Lua code, `&7` hints, `&r` reset).
- Modest rewards: XP, a few vanilla items, and on milestones a roll from the pack's generic loot tables "Reward: Common/Uncommon/Rare" (`type: "random"`, `exclude_from_claim_all: true`).
- Turtle upgrades are matched with an FTB Filter System smart filter (`and(or(item(...))or(component(fuzzy:{...})))`), so a pickaxe or crafting table on either side counts.

## Install
```sh
integration/ftbquests/install.sh "<instance-path>"          # current CC: Cloud chapter
integration/ftbquests/install.sh "<instance-path>" --full   # with the "coming soon" section
integration/ftbquests/install.sh "<instance-path>" --dry-run
```
`<instance-path>` is the folder with `config/ftbquests/quests` (CurseForge instance or server).

The script:
1. copies `config/ftbquests/quests` to `config/ftbquests/quests.backup-<timestamp>`,
2. copies `chapters/cc_tweaked.json5` and `chapters/cc_cloud.json5` (stops without changes if a *different* chapter already uses one of these file names; re-running it updates our own chapters),
3. merges the lang keys for `en_us` and `de_de` into `lang/<locale>/chapters/cc_*.json5` and `lang/<locale>/chapter.json5`, never overwriting a key that is already there (ATM11 ships no `de_de` folder; it is created, other chapters fall back to `en_us`),
4. touches `chapter_groups.json5` only if the Tech group is missing.

Then restart the game/server or run `/ftbquests reload`. To undo, delete `config/ftbquests/quests` and rename the backup folder back.

Since existing lang keys are never overwritten, text changes in a newer version of these files do not reach an instance that already has them. To update the text, delete `lang/*/chapters/cc_tweaked.json5` and `cc_cloud.json5` in the instance (and our 4 `chapter.*` keys in `lang/*/chapter.json5`) before installing again.

## Switching to the full variant later
Run `install.sh <instance> --full`. Going back to "current" works the same way; the extra lang keys of the coming-soon quests stay in the file and are simply unused.
Before the next CC: Cloud release, remove items from the "coming soon" list once they ship: move their `Q(...)` entries from `CLOUD_SOON` to `CLOUD` in `tools/generate.py` and regenerate.

## Planned item ids (must be used exactly like this by the mod)
Only the full variant uses them. Upgrade cards follow `cccloud:upgrade_<type>_<tier>` with type `capacity|bandwidth|range|efficiency|memory` and tier `allthemodium|vibranium|unobtainium`.

| Id | Feature | SPEC |
|---|---|---|
| `cccloud:cloud_relay` | Cloud Relay | v0.4 (already registered on main, not in the 0.2.0 jar) |
| `cccloud:advanced_cloud_relay` | Advanced Cloud Relay | v0.4 (same) |
| `cccloud:ender_relay` | Ender Relay | v0.4 (same) |
| `cccloud:upgrade_capacity_allthemodium` / `_vibranium` / `_unobtainium` | Capacity cards | v0.7 |
| `cccloud:upgrade_bandwidth_allthemodium` / `_vibranium` / `_unobtainium` | Bandwidth cards | v0.7 |
| `cccloud:upgrade_range_allthemodium` / `_vibranium` / `_unobtainium` | Range cards | v0.7 |
| `cccloud:upgrade_efficiency_allthemodium` / `_vibranium` / `_unobtainium` | Efficiency cards | v0.7 |
| `cccloud:upgrade_memory_allthemodium` / `_vibranium` / `_unobtainium` | Memory cards | v0.7 |
| `cccloud:quantum_core` | Quantum Core | v0.7 |
| `cccloud:cloud_tablet`, `cccloud:advanced_cloud_tablet` | Cloud Tablets | v0.7 |
| `cccloud:cloud_display` | Cloud Display | v0.7 |

The machine-readable list is `tools/planned_ids.json`.

## Editing
`tools/generate.py` is the single source of truth (layout, tasks, rewards, English and German text). The generated files are committed so the install works without Python.
```sh
python3 integration/ftbquests/tools/generate.py --pack "<instance>/config/ftbquests/quests"   # --pack only needed when new ids must be allocated
python3 integration/ftbquests/tools/validate.py "<instance>"
```
Ids are stored in `tools/ids.json`; new quests get a random id that is checked against the whole pack.
`validate.py` (needs `npx`) parses every file with the reference JSON5 parser, checks every item id against the jars in `<instance>/mods` plus the vanilla client jar (planned ids are only allowed in the full variant), checks id uniqueness against the pack, that dependencies and quest links resolve without cycles, that every quest and checkmark task has `en_us` and `de_de` text, and that the reward tables and the chapter group exist.

Editing in-game (FTB Quests edit mode) also works; FTB Quests then rewrites the files in the instance. Copy them back here if the changes should be kept.

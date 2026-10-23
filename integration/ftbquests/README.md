# FTB Quests chapters for ATM11: CC: Tweaked and CC: Cloud

Two quest chapters in the format of All the Mods 11 (0.12.0-beta, Minecraft 26.1.2, FTB Quests 26.1.2.9), for CC: Tweaked 1.120.0 and CC: Cloud 0.9.0.
Both sit in ATM11's **Tech** chapter group (`2B51AC12041E3F89`), after EnderIO/XyCraft (`order_index` 12 and 13).

| Chapter | File | Quests |
|---|---|---|
| CC: Tweaked | `chapters/cc_tweaked.json5` | 24: computer, shell/help, edit, advanced computer, disk drive and floppies, startup, pocket computer, speaker, printer, redstone relay, monitors, advanced monitors, wired modems and cable, wireless modem/rednet, ender modem, GPS, turtles, mining turtle, crafty turtle, inventory and energy/fluid peripherals, Energy Meter, Energized Power, Logistics Networks |
| CC: Cloud | `chapters/cc_cloud.json5` | 35, see below |

CC: Cloud quests, by area (it starts after the CC: Tweaked quest **Advanced Computer**, cross-chapter dependency plus a quest link):
- **Start:** Cloud Core, Handbook (GuideMe guide item, optional), Cloud Mainframe, power (FE), Cloud Uplink, Cloud Link Card, pair a browser, kill switch and trust (optional), the automatic Computer card, first `cloud.ui` program (`cloud-demo`), Advanced Cloud Uplink.
- **Storage:** ME / RS Cloud Bridge (both optional, one is needed for the next quest), watchlist and remote crafting, auto-stock (Storage card v2).
- **Dashboard:** browser terminal, Script Store and script upload, rules and notifications.
- **Network:** Cloud Analyzer, Cloud Relay, Advanced Cloud Relay, Ender Relay, CC cable links, Fiber Cable, Fiber Junction, Ender Fiber Link.
- **Mainframe upgrades:** Allthemodium / Vibranium / Unobtainium cards (any card of the tier counts), Quantum Core, Chunk Anchor upgrade (optional; the quest says that it is off by default in the server config).
- **In-game devices:** wireless redstone, Cloud Tablet, Advanced Cloud Tablet, Cloud Display.

Quests that depend on other mods or server settings are `optional: true`, so the chapters can be completed without them. FTB Quests has no "mod is loaded" condition; ATM11 ships AE2, Refined Storage and GuideMe, so all items exist in the pack.

Until CC: Cloud 0.9.0 there were two CC: Cloud variants ("current" and "full" with a "coming soon" section). Every planned item exists now, so there is only one chapter; quest ids are unchanged, only the "coming soon" info quest was removed.

## Conventions taken from ATM11
- Text is not in the chapter files: titles, subtitles, descriptions and checkmark-task titles live in `lang/<locale>/chapters/<file>.json5`, chapter titles in the shared `lang/<locale>/chapter.json5`.
- 16-digit uppercase hex ids (first digit 0-7), unique against every id in the pack's quest book.
- `default_quest_shape: ""` (pack default), shapes `hexagon` for milestones, size 1.0-1.5, coordinates on a 0.5 grid (`grid_scale: 0.5`); long-distance dependency lines are hidden (`hide_dependency_lines`) like elsewhere in the pack.
- Colour codes like the pack (`&e` items, `&b` concepts, `&a` commands, `&2` Lua code, `&7` hints, `&r` reset).
- Modest rewards: XP, a few vanilla items, and on milestones a roll from the pack's generic loot tables "Reward: Common/Uncommon/Rare" (`type: "random"`, `exclude_from_claim_all: true`).
- Turtle upgrades and "any upgrade card of a tier" are matched with an FTB Filter System smart filter; the handbook task matches `guideme:guide` with the component `guideme:guide_id = cccloud:handbook` (`match_components: "fuzzy"`).

## Install
```sh
integration/ftbquests/install.sh "<instance-path>"                  # new install
integration/ftbquests/install.sh "<instance-path>" --replace-text   # update an earlier install, incl. its text
integration/ftbquests/install.sh "<instance-path>" --dry-run
```
`<instance-path>` is the folder with `config/ftbquests/quests` (CurseForge instance or server). `--full` is still accepted and does nothing.

The script:
1. copies `config/ftbquests/quests` to `config/ftbquests/quests.backup-<timestamp>`,
2. copies `chapters/cc_tweaked.json5` and `chapters/cc_cloud.json5` (stops without changes if a *different* chapter already uses one of these file names; re-running it updates our own chapters),
3. merges the lang keys for `en_us` and `de_de` into `lang/<locale>/chapters/cc_*.json5` and `lang/<locale>/chapter.json5`, never overwriting a key that is already there (ATM11 ships no `de_de` folder; it is created, other chapters fall back to `en_us`). With `--replace-text` our two per-chapter lang files are replaced instead, which is needed to update the text of an earlier install (for example the subtitles of the former "coming soon" quests); `chapter.json5` is always only merged,
4. touches `chapter_groups.json5` only if the Tech group is missing.

Then restart the game/server or run `/ftbquests reload`. To undo, delete `config/ftbquests/quests` and rename the backup folder back.

## Editing
`tools/generate.py` is the single source of truth (layout, tasks, rewards, English and German text). The generated files are committed so the install works without Python.
```sh
python3 integration/ftbquests/tools/generate.py --pack "<instance>/config/ftbquests/quests"   # --pack only needed when new ids must be allocated
python3 integration/ftbquests/tools/validate.py "<instance>"
```
Ids are stored in `tools/ids.json`; new quests get a random id that is checked against the whole pack.
`validate.py` (needs `npx`) parses every file with the reference JSON5 parser, checks every item id against the jars in `<instance>/mods` plus the vanilla client jar, checks id uniqueness against the pack, that dependencies and quest links resolve without cycles, that every quest and checkmark task has `en_us` and `de_de` text, and that the reward tables and the chapter group exist. It works on an instance where the chapters are already installed (our own files are skipped).

Editing in-game (FTB Quests edit mode) also works; FTB Quests then rewrites the files in the instance. Copy them back here if the changes should be kept.

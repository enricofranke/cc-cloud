# FTB Quests chapters for ATM11: CC: Tweaked and CC: Cloud

Two quest chapters in the format of All the Mods 11 (0.12.0-beta, Minecraft 26.1.2, FTB Quests 26.1.2.9), for CC: Tweaked 1.120.0 and CC: Cloud 0.9.0.
Both sit in ATM11's **Tech** chapter group (`2B51AC12041E3F89`), after EnderIO/XyCraft (`order_index` 12 and 13).

| Chapter | File | Quests |
|---|---|---|
| CC: Tweaked | `chapters/cc_tweaked.json5` | 24: computer, shell/help, edit, advanced computer, disk drive and floppies, startup, pocket computer, speaker, printer, redstone relay, monitors, advanced monitors, wired modems and cable, wireless modem/rednet, ender modem, GPS, turtles, mining turtle, crafty turtle, inventory and energy/fluid peripherals, Energy Meter, Energized Power, Logistics Networks |
| CC: Cloud | `chapters/cc_cloud.json5` | 39, see below |

CC: Cloud quests, by area (it starts after the CC: Tweaked quest **Advanced Computer**, cross-chapter dependency plus a quest link):
- **Start:** Cloud Core, Handbook (GuideMe guide item, optional), Cloud Mainframe, power (FE), Cloud Uplink, Cloud Link Card, pair a browser, kill switch and trust (optional), the automatic Computer card, first `cloud.ui` program (`cloud-demo`), Advanced Cloud Uplink, Cloud Probe (optional: one machine at a distance).
- **Storage:** ME / RS Cloud Bridge (both optional, one is needed for the next quest), watchlist and remote crafting, auto-stock (Storage card v2).
- **Dashboard:** browser terminal, Script Store and script upload, rules and notifications.
- **Network:** Cloud Analyzer, Cloud Relay, Advanced Cloud Relay, Ender Relay, CC cable links, Fiber Cable, Fiber Junction, Ender Fiber Link.
- **Mainframe upgrades:** Allthemodium / Vibranium / Unobtainium cards (any card of the tier counts), Quantum Core, Chunk Anchor upgrade (optional; the quest says that it is off by default in the server config).
- **In-game devices:** wireless redstone, Cloud Tablet, Advanced Cloud Tablet, Cloud Display.
- **Redstone devices:** Cloud Lamp, Cloud Switch / Button (groups and cards), Cloud Sensor in a rule.

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
There are three ways; the files of `quests/` are the single source for all of them.

**1. Automatic (standard jar `cccloud-<version>.jar`).** The build copies `quests/` into the jar as `/cccloud_quests/`. On server start (dedicated, singleplayer and LAN alike), only when FTB Quests is loaded, `compat/ftbquests/QuestAutoInstall` runs `install/QuestInstaller` on `<config>/ftbquests/quests` before FTB Quests reads the book. Checked against FTB Quests 26.1.2.9 (ATM11's jar): FTB Quests reads its quest folder on `ServerStartedEvent` (`ServerQuestFile.load`), and NeoForge loads `cccloud-server.toml` right before `ServerAboutToStartEvent`, where we install. Clients get the chapters with FTB Quests' normal sync on join (`SyncQuestsMessage` plus the translations). The same rules as `install.sh`, plus a memory:
- a backup of the book first (`ftbquests/quests.backup-<time>`), only when something changes;
- a chapter file that is not ours (another chapter id) is never replaced;
- lang keys are only added, never overwritten (FTB Quests merges every file of a locale, so a key anywhere in `lang/<locale>/` counts);
- ATM11's Tech group if it exists, otherwise our own group "CC: Cloud" (`754527ACD5F594C9`, order 0 and 1); a fresh book also gets FTB Quests' default `data.json5`;
- every id our chapters define is checked against every id in the book's chapters, reward tables, groups and data; on any collision nothing is installed (WARN line naming the ids and files; retried on the next start);
- the CC: Tweaked chapter only when the book has no CC: Tweaked chapter of its own (a chapter whose icon is a `computercraft:` item, or whose tasks/icons use at least three different `computercraft:` items); CC: Cloud's dependencies and quest link into it are then left out, and so are the loot rewards when the book has none of ATM11's reward tables;
- **once:** `config/cccloud/quests-installed.json` records per chapter `installed` / `removed` / `skipped` / `kept` (already there by hand, not this version) with a content hash, and the hash of every text it wrote. A deleted chapter is never added again; a skipped or kept one never touched;
- **updates:** when the shipped chapters change (`questVersion`, a hash of the files), a chapter is replaced only if its content still has the recorded hash, keeping its group and position; texts likewise per key; new keys are added. Edited chapters and texts stay, with a log line. The hashes are of the content, not the bytes: key order, layout, number spelling, the chapter's group/position and FTB Quests' `ftbquests:missing_item` wrapping of items from mods that are not installed do not count, because FTB Quests re-writes the whole book in its own layout whenever it saves (checked: a book re-saved by FTB Quests 26.1.2.9, also in ATM11, is the same content). The turtle-upgrade components are therefore written as FTB Quests writes them (`{ id: … }`);
- one log line says what was installed or skipped and why, and that `installQuests = false` in `cccloud-server.toml` turns it off.

**2. Modpacks: modpack jar + release zip.** `cccloud-<version>-modpack.jar` (Gradle task `modpackJar`) has the same classes, mod id and version, but no `/cccloud_quests/` and `/cccloud-edition.properties` with `edition=modpack`: `installQuests` defaults to false and the installer does nothing. Packs ship the chapters in their own quest book (as they are or adapted: layout, rewards, chapter group). With the standard jar, `installQuests = false` in `defaultconfigs/cccloud-server.toml` does the same. Every GitHub release carries `cccloud-quests-atm11-<version>.zip` next to both jars: one folder with `quests/`, `install.sh`, a bilingual `README.txt` (which download, install with the script, the manual steps for hosts with only a file manager) and `LICENSE`. `./gradlew questPack` builds it into `build/distributions/` (part of `./gradlew build`; CI attaches all three files on `v*` tags). The text of `README.txt` is `release/README.txt` (`@VERSION@` becomes the mod version). Outside ATM11 the milestone rewards point to ATM11's reward tables (`TABLES` in `tools/generate.py`), so adapt them there.

**3. The script, by hand:**
```sh
./install.sh "<instance-path>"                                      # from the unpacked release zip
integration/ftbquests/install.sh "<instance-path>"                  # new install from the repository
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

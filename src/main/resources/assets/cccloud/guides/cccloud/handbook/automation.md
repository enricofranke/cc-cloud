---
# Generated from the dashboard docs by tools/gen_handbook.ts – edit cloud/web/src/docs/content instead.
navigation:
  title: "Rules, scenes & notifications"
  position: 7
---

# Rules, scenes, notifications and history

Automation that runs on the **server** – no browser and no computer program needed – plus scenes, schedules, alerts on your phone, long-term charts and production statistics.

## Rules

Open **Rules** in the dashboard settings (⚙). A rule reads like a sentence: **When** something happens, **if** conditions hold, **then** do something.

| Part | Choices |
|---|---|
| When | a widget value crosses a number, an item count in a storage card, mainframe power in %, a card goes offline, every N minutes, on days at a time, an in-game time of day ([schedules](automation.md)) |
| If (optional, all must hold) | widget value, item count, power, a card is online |
| Then (1–10, in order) | set a redstone side of a computer, use a control (button, toggle, slider, select, number, text), craft an item, send an alert, turn the dashboard on/off, run a [scene](automation.md) |

- Value triggers fire **once** when the check becomes true, not again while it stays true. **Cooldown** (at least 5 s) is the minimum time between two runs.
- **Run now** runs the actions once, ignoring trigger, conditions and cooldown – handy for testing.
- Safety: a rule may not change the widget its own trigger watches (that would loop), controls protected by the PIN cannot be used, and at most 20 rule runs per second per mainframe. Every run is logged (**Recent runs**).
- The number of rules depends on the mainframe's **Memory** upgrade (8 without, then 24 / 64 / 128).
- Permissions: “Edit rules” to create, change and delete; “Enable rules” to switch them on/off and use **Run now** (operators have it).
- Lua: `cloud_mainframe.getRules()` and `setRuleEnabled(id, enabled)`.

**Example:** When *Core temperature (Fission Reactor)* is above 900 – then set redstone back of *Reactor computer* to 15 and alert “Reactor hot” (warning).

## Scenes

A scene is **one button that runs several steps in order** – “Night mode”: lights off, wait 2 s, door closed, reactor slider to 3, a notice. Open **Scenes** in the dashboard settings (⚙).

- **Steps** (up to 16): the same actions as rules – set a redstone side, use a control, craft, send an alert, dashboard on/off – plus **Wait** (0.05–300 s, at most 600 s per scene). ↑ and ↓ change the order.
- Every scene is a button on the **Scenes** card: press it on the dashboard, on a Cloud Tablet, on a Cloud Display (only scenes without confirmation or PIN) or move it to any card with the layout editor. **Test run** in the Scenes screen runs the saved scene and ticks off every step as it runs; the last run keeps a result per step.
- Options: colour, icon, **ask before running** and **always ask for the PIN**. A step on a PIN-protected control makes the scene ask for the PIN anyway.
- **Rights:** creating and changing a scene needs “Edit rules” **and** every permission its steps need – nobody can hand out more than they may do themselves. Pressing it needs “Use controls”; each step is checked again **when it runs**, with the rights of the player who pressed it. A step without its permission fails, the others still run.
- **Schedule…** opens a new rule that runs the scene on days at a time (below).
- Limits: 32 scenes per mainframe (`maxScenesPerMainframe`), the same scene never twice at once, at most 4 runs at once and 30 per minute per mainframe. `scenesEnabled = false` in `[scenes]` switches scenes off.

## Schedules

Schedules are rules with a **time trigger** – pick it under **When** in the rule editor:

| Trigger | Runs |
|---|---|
| every … minutes | real time, every N minutes (at least `minIntervalMinutes`, default 1) |
| on days at a time | real time: on the picked weekdays at HH:MM (24 h) in the **server's time zone** – the editor shows it (config `timezone`, empty = the server's system zone) |
| an in-game time of day | when the world clock passes the time: **dawn**, **noon**, **dusk**, **midnight** or any in-game hour |

- With the action **Run a scene**, one schedule runs many steps. A scheduled scene runs with the rights of its **owner** (who saved it last), never with a PIN – so scenes that need the PIN cannot be scheduled.
- The rules list shows **next run in …** for real-time schedules.
- **Missed runs:** after a server restart at most **one** missed run is made up, and only if it is younger than `missedWindowHours` (24 h); `runMissed = false` never catches up. In-game time stands still while the server is down, so in-game triggers never catch up.
- **Unloaded mainframes:** a run that is due while the mainframe is unloaded or offline is **skipped** – the log shows “skipped – mainframe unloaded” once per outage, and nothing is made up later. Keep the mainframe's chunk loaded (Chunk Anchor upgrade, FTB Chunks) for reliable schedules.
- At most 16 time-based rules per mainframe (`maxSchedulesPerMainframe` in `[schedules]`).

## Push notifications

Open **Notifications** in the dashboard settings. Add up to 5 targets per mainframe:

- **Discord:** in the channel settings → Integrations → Webhooks → New Webhook → copy the URL.
- **ntfy:** a topic URL like `https://ntfy.sh/my-base-alerts` (pick a long, hard to guess name, or use a token); subscribe to the same topic in the ntfy app.
- **Webhook:** any https endpoint; it gets a JSON POST (event, mainframe, title, text, level, time).

Choose the minimum level (everything, warnings and errors, or errors only) and the events: **alerts** (scripts, cards, crafting, rules), **rules** (runs of rules with “push every run”), **approvals** (a browser waits for approval) and **power** (no power / restored). **Send test** checks a target; the result appears next to it.

The links and tokens are stored on the server only – the dashboard shows them masked. The server only sends to public https addresses (no local network, no redirects), at most 30 messages per minute per mainframe. Needs the “Push targets” permission (admins).

## Long-term history

Widgets whose script asks for history (`history="24h"` in the widget options, or `"1h"`, `"7d"`, `"30d"`) get a small **chart button**. It opens the history with a range switcher (1 h, 24 h, 7 days, 30 days), min / average / max and the value under your finger or mouse; gaps mean the mainframe or the chunk was not loaded.

```lua
cloud.gauge("temp", "Core temperature", { min = 0, max = 1200, unit = "K", history = "7d" })
```

How much the server keeps depends on the mainframe's **Memory** upgrade: 1 h without, 24 h / 7 days / 30 days with the tiers. **Export CSV** downloads the values (time, value) for spreadsheets – it needs the “Use controls” permission.

## Production statistics

The **Production** page (a tab next to Network, or ⚙ → Production) shows what every AE2 / Refined Storage network behind your cards **gains, consumes and crafts** – per minute or per hour, over the last hour.

- The server samples each storage every minute (`sampleSeconds`) and keeps a window of 60 samples (`windowSamples`). The first numbers arrive after one minute; a server restart starts the window fresh.
- Per storage: gained, consumed, net and crafted, a chart, and the **top producers and consumers** with their icons.
- Click an item – “what is eating my iron?” – for its rates and chart, its crafting jobs, crafting running on the network now, the last 24 hours (watched and favourite items) and when the stock runs out at this rate.
- **What it cannot tell:** AE2 and Refined Storage only report how much the storage gained and lost, not which machine, pipe or player took the items. Items that come and go between two samples cancel out. **Crafted** counts finished jobs started through the bridge (dashboard, Lua, auto-stock, rules); jobs started at a terminal are only visible while they run.
- **Energy:** every energy card with stored energy and in/out in FE/t, the totals and a chart. Blocks whose mod reports no rates show the measured change (“measured”).
- **Pin as card** puts an item's rate, a storage's net rate or the energy totals as a chart on the **Production** card – arrange it with the layout editor; tablets and displays show it too. Up to 16 pins; needs “Edit the layout” (and “Search storage” for items and storages).
- Storage numbers need the “Search storage” permission. `productionEnabled = false` in `[production]` switches the statistics off.

## Terminal

The **>_** button on a computer's card opens its **terminal** in the browser: the live screen (colours on advanced computers) and your keyboard, like standing in front of it. Click the screen and type; **Terminate** is Ctrl+T, and **Reboot**, **Shut down** and **Turn on** control the computer. Paste sends the first line of the clipboard. On phones, type into the field below the screen and press **Enter**.

It needs the “Terminal” permission (admins) and the mainframe PIN if one is set; opening a terminal is logged. The server admin can switch it off (`terminalEnabled`) or forbid scripts and the terminal for a player (`cccloud.feature.scripts`).

## Lua editor

In a computer's **\</>** sheet, **Open the editor** shows all its files and folders (up to 8 levels) next to a code editor with Lua highlighting, line numbers, search (Ctrl/⌘ + F) and undo. **Save** (Ctrl/⌘ + S) writes the file; **Save & run** also starts it in a new tab on the computer. **+ File**, **+ Folder**, rename/move (✎) and delete (×) work on the tree.

If the file was changed on the computer since you opened it, saving asks before it overwrites. Files are limited to 256 KB, `rom` and disk drives are never touched, and the editor needs the same permissions as uploading scripts (plus the PIN if one is set).


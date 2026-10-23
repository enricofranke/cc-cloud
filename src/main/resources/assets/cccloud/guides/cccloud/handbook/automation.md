---
# Generated from the dashboard docs by tools/gen_handbook.ts – edit cloud/web/src/docs/content instead.
navigation:
  title: "Rules, notifications & history"
  position: 7
---

# Rules, notifications and history

Automation that runs on the **server** – no browser and no computer program needed – plus alerts on your phone and long-term charts.

## Rules

Open **Rules** in the dashboard settings (⚙). A rule reads like a sentence: **When** something happens, **if** conditions hold, **then** do something.

| Part | Choices |
|---|---|
| When | a widget value crosses a number, an item count in a storage card, mainframe power in %, a card goes offline, every N minutes, a time of day (in-game) |
| If (optional, all must hold) | widget value, item count, power, a card is online |
| Then (1–10, in order) | set a redstone side of a computer, use a control (button, toggle, slider, select, number, text), craft an item, send an alert, turn the dashboard on/off |

- Value triggers fire **once** when the check becomes true, not again while it stays true. **Cooldown** (at least 5 s) is the minimum time between two runs.
- **Run now** runs the actions once, ignoring trigger, conditions and cooldown – handy for testing.
- Safety: a rule may not change the widget its own trigger watches (that would loop), controls protected by the PIN cannot be used, and at most 20 rule runs per second per mainframe. Every run is logged (**Recent runs**).
- The number of rules depends on the mainframe's **Memory** upgrade (8 without, then 24 / 64 / 128).
- Permissions: “Edit rules” to create, change and delete; “Enable rules” to switch them on/off and use **Run now** (operators have it).
- Lua: `cloud_mainframe.getRules()` and `setRuleEnabled(id, enabled)`.

**Example:** When *Core temperature (Fission Reactor)* is above 900 – then set redstone back of *Reactor computer* to 15 and alert “Reactor hot” (warning).

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

## Terminal

The **>_** button on a computer's card opens its **terminal** in the browser: the live screen (colours on advanced computers) and your keyboard, like standing in front of it. Click the screen and type; **Terminate** is Ctrl+T, and **Reboot**, **Shut down** and **Turn on** control the computer. Paste sends the first line of the clipboard. On phones, type into the field below the screen and press **Enter**.

It needs the “Terminal” permission (admins) and the mainframe PIN if one is set; opening a terminal is logged. The server admin can switch it off (`terminalEnabled`) or forbid scripts and the terminal for a player (`cccloud.feature.scripts`).

## Lua editor

In a computer's **\</>** sheet, **Open the editor** shows all its files and folders (up to 8 levels) next to a code editor with Lua highlighting, line numbers, search (Ctrl/⌘ + F) and undo. **Save** (Ctrl/⌘ + S) writes the file; **Save & run** also starts it in a new tab on the computer. **+ File**, **+ Folder**, rename/move (✎) and delete (×) work on the tree.

If the file was changed on the computer since you opened it, saving asks before it overwrites. Files are limited to 256 KB, `rom` and disk drives are never touched, and the editor needs the same permissions as uploading scripts (plus the PIN if one is set).


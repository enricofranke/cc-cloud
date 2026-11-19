---
# Generated from the dashboard docs by tools/gen_handbook.ts – edit cloud/web/src/docs/content instead.
navigation:
  title: "Cloud Scripts"
  position: 11
  icon: cccloud:cloud_mainframe
---

# Cloud Scripts

Cloud Scripts are Lua scripts that run **inside the mainframe** – no computer, no disk, no chunk of their own. They read the cards of the dashboard, react when a value changes, run on timers and act with **your** rights: press buttons, set sliders and toggles, start scenes, craft, change a card's [design](dashboard.md), send alerts. They keep running when nobody has the dashboard open.

Use them for the logic that is too much for a [rule](automation.md) – state, loops, several cards at once – and that should not depend on a computer staying loaded.

## Getting started

1. Open **⚙ → Cloud Scripts** on the dashboard. It needs the **Cloud Scripts** permission (`code.edit`): admins and the owner have it, others get it under [Access & roles](accounts.md).
2. **New script** → pick a template – *Reactor guard*, *Low power alert* or *Night mode* – or a blank script, give it a name and **Create**.
3. Change the card and widget names at the top to yours (a card's title or id, and the widget's id that the computer's program gives it; `cloud.getCard` lists them). The editor completes `cloud.` and the Lua libraries as you type, shows the documentation and the arguments when you point at a function, and underlines mistakes.
4. **Save** (Ctrl/⌘ + S). Every save is a new version; the server keeps the last 20.
5. **Start**. The **Log** next to the editor (on phones: the **Log** tab) shows what `print`, `cloud.log` and `cloud.warn` write, live; errors appear there with their line, and the editor marks that line.
6. Tick **Autostart** to have it start again when the mainframe is back and after a server restart.
7. Stop it with **Stop**, the mainframe's **redstone kill switch**, or in game with `/cccloud scripts stop [all|<name>]`.

```lua
-- every 10 seconds: the reactor's heat to the log
cloud.every(10, function()
  print("heat", cloud.getValue("Reactor", "heat"))
end)

-- above 80: switch it off and tell everyone
cloud.on("above", {card = "Reactor", widget = "heat", value = 80}, function(e)
  local ok, err = cloud.set("Reactor", "active", false)
  if ok then
    cloud.notify("Reactor off at " .. e.value, "crit")
  else
    cloud.warn("could not switch it off: " .. err)
  end
end)
```

> **Tip:** `cloud.on` and the timers keep a script alive after its last line. A script that only runs through once is **finished** – it shows as stopped with that reason.

## The editor

- **Completions** for `cloud.`, `cloud.store.`, `cloud.script.`, the Lua libraries (`string.`, `table.`, `math.` …), the event names in `cloud.on("…")` and the Lua keywords. Ctrl + Space opens them anywhere.
- **Hover** a function for its signature, what it does, its parameters, the permission it needs, whether it waits or pauses, and an example. Inside a call the parameter you are typing is highlighted.
- **Problems:** the server compiles your text a moment after you stop typing and marks syntax errors; names the sandbox removes (`io`, `require` …) get a warning. The last **runtime error** of the saved version is marked on its line too.
- **Versions:** pick one to see what changed against the current version or the one before it. **Revert** saves its code as a new version, so nothing is lost.
- **Saving at the same time:** if someone saved a newer version while you were editing, the editor shows the difference and lets you keep yours or take theirs.
- Restart a running script after saving so it runs the new code.

## How scripts run

- **Rights:** a script acts with the live rights of its **author** – whoever saved it last – but never with more than the person who started it. If the author loses access to the mainframe, the script stops. Widgets and scenes protected by the PIN are refused; widgets and scenes that ask for confirmation need `{confirm=true}`.
- **Audit log:** every action lands in the mainframe's audit log as “script *name* (by *player*) …”. Beyond 6 audited actions a minute per script, one entry per minute sums them up, so the log stays readable.
- **Reads** come from a snapshot the mainframe takes five times a second; **actions** are queued to the server thread (at most 20 a second per script) and functions that wait for the server, like `cloud.press`, pause the script for a tick.
- **When they stop:** only when the mainframe is unloaded, loses power or its redstone kill switch is on – switching the dashboard off does **not** stop them. Autostart scripts start again when the mainframe is back and after a server restart.
- **Errors:** an error that is not caught stops the script; the list shows the message and its line, the log keeps it.

## Limits

Scripts run on **worker threads, never on the server thread**, so a busy script cannot make the server lag. These limits keep it that way (server admins can change them in the config, `cloudScripts.*`):

| Limit | Default |
|---|---|
| CPU time | 50 ms per second per script; a script that used it up is paused and continues in the next second |
| One run between two pauses | at most 200 ms, else the script stops with “ran too long without pausing” – an endless loop cannot be caught with `pcall` |
| Scripts | 16 per mainframe, 8 of them running |
| Source | 32 KB per script |
| Strings | 1 MB from `string.rep`, `string.format`, `table.concat` and `gsub` |
| Memory | 16 MB per script: the size of what it keeps, measured again whenever it has made a quarter of that or twice its last size anew (and every 10 s) – an estimate, not a hard bound: between two measurements it can briefly hold about three times as much, and a text built with `..` gets all its bytes at once the first time it is used as a whole |
| Timers | at least 0.25 s apart (`cloud.sleep` at least one tick), 32 timers and 64 handlers per script |
| Storage | 16 KB per script (`cloud.store`) |
| Log | 20 lines a second (more are counted and dropped), the last 200 lines are kept |
| Reads | from a snapshot, 5 times a second |
| Actions | through the server thread, at most 20 a second per script |
| Versions | the last 20 per script |

## Sandbox

Cloud Scripts are Lua with the usual libraries – but without anything that reaches outside: no `io`, no `os.execute`, no `debug`, no `package`/`require`, no loading files, no bytecode (`load` only takes text), no `string.dump` and no access to Java.

**Global functions:** `_G`, `_VERSION`, `assert`, `error`, `getmetatable`, `ipairs`, `load`, `next`, `pairs`, `pcall`, `print`, `rawequal`, `rawget`, `rawlen`, `rawset`, `select`, `setmetatable`, `tonumber`, `tostring`, `type`, `unpack`, `xpcall`

**Libraries:**

- `string`: `byte`, `char`, `find`, `format`, `gmatch`, `gsub`, `len`, `lower`, `match`, `rep`, `reverse`, `sub`, `upper`
- `table`: `concat`, `insert`, `pack`, `remove`, `sort`, `unpack`, `move`
- `math`: `abs`, `acos`, `asin`, `atan`, `atan2`, `ceil`, `cos`, `cosh`, `deg`, `exp`, `floor`, `fmod`, `frexp`, `huge`, `ldexp`, `log`, `log10`, `max`, `min`, `modf`, `pi`, `pow`, `rad`, `random`, `randomseed`, `sin`, `sinh`, `sqrt`, `tan`, `tanh`
- `utf8`: `char`, `charpattern`, `codepoint`, `codes`, `len`, `offset`
- `coroutine`: `create`, `isyieldable`, `resume`, `running`, `status`, `wrap`, `yield`
- `bit32`: `arshift`, `band`, `bnot`, `bor`, `btest`, `bxor`, `extract`, `lrotate`, `lshift`, `replace`, `rrotate`, `rshift`
- `os`: `clock`, `date`, `difftime`, `time`

**Not available:** `io`, `debug`, `package`, `require`, `dofile`, `loadfile`, `loadstring`, `setfenv`, `getfenv`, `collectgarbage`, `string.dump`, `string.pack`, `string.packsize`, `string.unpack`, `table.create`, `os.execute`, `os.exit`, `os.getenv`, `os.remove`, `os.rename`, `os.tmpname`

## API reference

The Cloud Scripts API. Every script gets one global table, `cloud`. Reads come from a snapshot the mainframe takes five times a second; actions run on the server with the rights of the script's author (never more) and land in the audit log.

### Cards, values and devices

**`cloud.getCards()`** → `Card[]`

Lists the dashboard's cards, at most 256.

- Returns: `Card[]` – Every card the author may see (widgets = how many)

```lua
for _, card in ipairs(cloud.getCards()) do
  print(card.title, card.online)
end
```

**`cloud.getCard(card)`** → `Card?`

One card with all its widgets and values.

- Returns: `Card?` – The card with its widgets, or nil

```lua
local reactor = cloud.getCard("Reactor")
if reactor then print(#reactor.widgets) end
```

**`cloud.getValue(card, widget)`** → `any`

Reads one widget's current value.

- Returns: `any` – The widget's value, or nil if unknown

```lua
local heat = cloud.getValue("Reactor", "heat")
```

**`cloud.getWidget(card, widget)`** → `Widget?`

One widget with its type, label, value and limits.

- Returns: `Widget?` – The widget, or nil

**`cloud.isOnline(card?)`** → `boolean`

Without an argument: the mainframe is online. With a card: that device is online.

- `card` (CardRef, optional)

**`cloud.getDevices()`** → `Device[]`

The devices linked to the mainframe (uplinks, bridges, relays, redstone devices).

**`cloud.getMainframe()`** → `Mainframe`

The mainframe: name, online, dashboard switch and power.

**`cloud.isEnabled()`** → `boolean`

The dashboard switch (as on the cloud_mainframe peripheral).

**`cloud.getEnergy()`** → `integer`

Stored FE in the mainframe's buffer.

**`cloud.getEnergyCapacity()`** → `integer`

Buffer size in FE.

**`cloud.getEnergyUsage()`** → `integer`

Consumption in FE per tick.

**`cloud.getItemCount(card, item)`** → `integer?` · needs `storage.search` · waits for the server (one tick)

Counts an item in the storage behind a storage card (AE2, Refined Storage). Waits for the server (one tick).

- Returns: `integer?` – How many are stored, or nil if the storage is not reachable

```lua
local iron = cloud.getItemCount("ME Storage", "minecraft:iron_ingot")
```

**`cloud.getRedstone(channel)`** → `integer?`

The level of a wireless redstone channel of the network.

- Returns: `integer?` – Level 0–15, or nil for an unknown channel

**`cloud.getRules()`** → `Rule[]`

The mainframe's rules.

**`cloud.getScenes()`** → `Scene[]`

The mainframe's scenes.

### Actions

**`cloud.press(card, widget, options?)`** → `boolean, string?` · needs `act.buttons` · waits for the server (one tick)

Presses a button, like a click on the dashboard.

- `options` (table, optional) – \{confirm=true\} for widgets that ask for confirmation
- Returns: `boolean`; `string?` – The error, e.g. not_allowed, pin_required, confirm_required, offline

```lua
local ok, err = cloud.press("Doors", "open")
if not ok then cloud.warn("could not open: " .. err) end
```

**`cloud.set(card, widget, value, options?)`** → `boolean, string?` · needs `act.buttons` · waits for the server (one tick)

Sets a toggle, slider, select, number or text widget. Same checks as a person on the dashboard; PIN widgets are refused.

- `value` (any) – boolean, number or string, as the widget takes it
- `options` (table, optional) – \{confirm=true\} for widgets that ask for confirmation

```lua
cloud.set("Base lights", "power", false)
```

**`cloud.setRedstone(card, side, level)`** → `boolean, string?` · needs `act.redstone` · waits for the server (one tick)

Sets a redstone output of a computer card (automatic mode).

- `side` (string) – top, bottom, left, right, front or back
- `level` (integer) – 0–15

**`cloud.craft(card, item, amount)`** → `boolean, string?` · needs `storage.craft` · waits for the server (one tick)

Requests a craft on a storage card, bounded by the server's craft limit.

**`cloud.setEnabled(enabled)`** → `boolean, string?` · needs `power.toggle` · waits for the server (one tick)

Switches the dashboard on or off (as on the cloud_mainframe peripheral). Scripts keep running.

**`cloud.runScene(scene, options?)`** → `boolean, string?` · needs `act.buttons` · waits for the server (one tick)

Starts a scene. Every step is checked with the script's rights; scenes that need the PIN are refused.

- `scene` (string) – Scene id or name
- `options` (table, optional) – \{confirm=true\} for scenes that ask for confirmation

**`cloud.setRuleEnabled(rule, enabled)`** → `boolean, string?` · needs `rules.toggle` · waits for the server (one tick)

Switches a rule on or off (as on the cloud_mainframe peripheral).

- `rule` (string) – Rule id or name

**`cloud.runRule(rule)`** → `boolean, string?` · needs `rules.toggle` · waits for the server (one tick)

Runs a rule's actions now, like "Run now" on the dashboard.

- `rule` (string) – Rule id or name

**`cloud.setStyle(card, design?)`** → `boolean, string?` · needs `layout.edit` · waits for the server (one tick)

Sets a card's design in the dashboard layout, like the layout editor: background, custom CSS and card parts, checked like the editor's. It replaces the card's whole design, including the one its program sets with dash:style.

- `design` (table, optional) – \{bg=, bgColor=, css=, parts=\{title=, gauge_fill=, button=, …\}\}; nil removes the card's own design

```lua
cloud.setStyle("Reactor", {parts = {title = "color: #ff5a4f", gauge_fill = "color: #ff5a4f"}})
```

### Events

**`cloud.on(event, filter?, handler)`** → `integer`

Calls the handler whenever the event happens. above/below fire once when the value crosses the threshold.

- `event` (string) – change, above, below, online, offline or redstone
- `filter` (table, optional) – \{card=, widget=\} (above/below also value=), \{channel=\} for redstone
- `handler` (function) – Called with the Event
- Returns: `integer` – Handler id for cloud.off

```lua
cloud.on("above", {card="Reactor", widget="heat", value=80}, function(e)
  cloud.set("Reactor", "active", false)
end)
```

**`cloud.wait(event, filter?, timeout?)`** → `Event?` · pauses the script

Pauses until the event happens (like os.pullEvent, with a filter).

- `filter` (table, optional)
- `timeout` (number, optional) – Seconds; nil waits forever
- Returns: `Event?` – The event, or nil after the timeout

```lua
local e = cloud.wait("online", {card="Reactor"}, 60)
```

**`cloud.off(id)`** → `boolean`

Removes an event handler or cancels a timer.

- Returns: `boolean` – true if it existed

### Timers

**`cloud.cancel(id)`** → `boolean`

Cancels a timer (the same as cloud.off).

**`cloud.sleep(seconds)`** · pauses the script

Pauses this script (or this handler) without using CPU. At least one tick (0.05 s).

```lua
cloud.sleep(5)
```

**`cloud.every(seconds, handler)`** → `integer`

Calls the handler again and again. A run that is still busy is not started twice.

- `seconds` (number) – At least 0.25
- Returns: `integer` – Timer id

```lua
cloud.every(10, function()
  print(cloud.getEnergy())
end)
```

**`cloud.after(seconds, handler)`** → `integer`

Calls the handler once after the delay.

**`cloud.at(time, handler)`** → `integer`

Calls the handler at a time of day: real time, or in-game time (0 = 6:00, 13000 = dusk).

- `time` (any) – "HH:MM" (every day, server time zone), \{time="HH:MM", days=\{"mon","fri"\}\} or \{gameTime=13000\}

```lua
cloud.at({gameTime=13000}, function()
  cloud.set("Lights", "power", true)
end)
```

### Notifications

**`cloud.notify(text, level?)`** → `boolean`

A toast on every dashboard and a push notification (as on the cloud_uplink peripheral).

- `level` (string, optional) – info (default), warn or crit
- Returns: `boolean` – false if it was dropped by the rate limit

**`cloud.alert(text, level?)`** → `boolean`

The same as cloud.notify.

- `level` (string, optional)

**`cloud.toast(text, level?)`** → `boolean`

Only a toast on the open dashboards, no push.

- `level` (string, optional)

**`cloud.push(text, level?, title?)`** → `boolean`

Only a push notification to the mainframe's push targets.

- `level` (string, optional)
- `title` (string, optional) – Default: the script's name

### Storage

**`cloud.store.get(key)`** → `any`

Reads a stored value (it survives restarts and new versions).

```lua
local runs = (cloud.store.get("runs") or 0) + 1
cloud.store.set("runs", runs)
```

**`cloud.store.set(key, value)`** → `boolean`

Stores a value for this script (nil deletes it). Small: see the limits.

- `value` (any) – nil, boolean, number, string or a table of those

**`cloud.store.delete(key)`** → `boolean`

Deletes a stored value.

**`cloud.store.keys()`** → `string[]`

Every stored key.

### Log and info

**`cloud.log(...)`**

Writes a line to the script's log (print does the same).

**`cloud.warn(...)`**

Writes a warning to the script's log.

**`cloud.time()`** → `integer`

The real time.

- Returns: `integer` – Milliseconds since 1970

**`cloud.clock()`** → `number`

Seconds since the script started.

- Returns: `number` – Seconds since the script started

**`cloud.gameTime()`** → `integer`

The in-game time of day.

- Returns: `integer` – 0–23999 (0 = 6:00, 6000 = noon, 13000 = dusk)

**`cloud.script`** → `ScriptInfo`

This script: id, name, author, version.

### Events

For `cloud.on(event, filter, handler)` and `cloud.wait(event, filter, timeout)`. The handler gets an Event table.

| Event | Filter | What happens |
|---|---|---|
| `change` | `card`, `widget` | A widget's value changed. Without a filter: every widget of every card. |
| `above` | `card`, `widget`, `value` | A number rose above value (once per crossing; booleans count as 0/1). |
| `below` | `card`, `widget`, `value` | A number fell below value (once per crossing). |
| `online` | `card` | A device came online. |
| `offline` | `card` | A device went offline. |
| `redstone` | `channel` | A wireless redstone channel changed its level. |

### Types

Tables the functions return or take. A `?` after a type means it can be `nil`.

**CardRef** – a string: `string`. A card: its id, its title (case-insensitive, the first match) or "mainframe" for the mainframe itself (widgets energy, capacity, percent, usage, enabled).

**Card**

| Field | Type | Meaning |
|---|---|---|
| `id` | `string` | Card id |
| `title` | `string` | Title on the dashboard |
| `online` | `boolean` | The device is online |
| `kind` | `string` | "lua" for program cards, else the template (storage, redstone, …) |
| `widgets` | `Widget[]` | getCard: the widgets; getCards: how many |

**Widget**

| Field | Type | Meaning |
|---|---|---|
| `id` | `string` | Widget id |
| `type` | `string` | button, toggle, slider, gauge, label, … |
| `label` | `string?` | Label |
| `value` | `any` | Current value |
| `unit` | `string?` | Unit |
| `min` | `number?` | Minimum |
| `max` | `number?` | Maximum |
| `pin` | `boolean?` | Needs the PIN: scripts cannot use it |
| `confirm` | `boolean?` | Asks for confirmation: pass \{confirm=true\} |

**Device**

| Field | Type | Meaning |
|---|---|---|
| `id` | `string` | Device id (the card id for uplinks and bridges) |
| `type` | `string` | uplink, bridge, relay, switch, lamp, … |
| `name` | `string` | Name |
| `status` | `string` | online, offline, no_power, out_of_range, … |
| `online` | `boolean` | status == "online" |

**Mainframe**

| Field | Type | Meaning |
|---|---|---|
| `id` | `string` | Mainframe id |
| `name` | `string` | Name |
| `online` | `boolean` | Loaded, powered, not switched off by redstone |
| `enabled` | `boolean` | The dashboard switch |
| `energy` | `integer` | Stored FE |
| `capacity` | `integer` | Buffer size in FE |
| `percent` | `number` | energy / capacity × 100 |
| `usage` | `integer` | FE per tick |

**Rule**

| Field | Type | Meaning |
|---|---|---|
| `id` | `string` | Rule id |
| `name` | `string` | Name |
| `enabled` | `boolean` | Switched on |
| `trigger` | `string` | Trigger type |
| `runs` | `integer` | Runs since the server started |
| `lastResult` | `string?` | ok or an error |

**Scene**

| Field | Type | Meaning |
|---|---|---|
| `id` | `string` | Scene id |
| `name` | `string` | Name |
| `needsPin` | `boolean` | Needs the PIN: scripts cannot run it |
| `confirm` | `boolean` | Asks for confirmation: pass \{confirm=true\} |
| `running` | `boolean` | Running right now |

**Event**

| Field | Type | Meaning |
|---|---|---|
| `type` | `string` | change, above, below, online, offline or redstone |
| `card` | `string?` | Card id |
| `title` | `string?` | Card title |
| `widget` | `string?` | Widget id |
| `value` | `any` | New value (redstone: the level) |
| `old` | `any` | Value before |
| `channel` | `string?` | Wireless redstone channel |
| `time` | `integer` | When it happened (ms since 1970) |

**ScriptInfo**

| Field | Type | Meaning |
|---|---|---|
| `id` | `string` | Script id |
| `name` | `string` | Script name |
| `author` | `string` | Who saved it last; the script acts with their rights |
| `version` | `integer` | Saved version |
| `language` | `string` | "lua" |


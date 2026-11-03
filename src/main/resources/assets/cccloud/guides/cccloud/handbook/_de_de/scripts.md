---
# Aus der Dashboard-Doku erzeugt (tools/gen_handbook.ts) – Änderungen in cloud/web/src/docs/content machen.
navigation:
  title: "Cloud Scripts"
  position: 10
  icon: cccloud:cloud_mainframe
---

# Cloud Scripts

Cloud Scripts sind Lua-Skripte, die **im Mainframe** laufen – ohne Computer, ohne Diskette, ohne eigenen Chunk. Sie lesen die Karten des Dashboards, reagieren, wenn sich ein Wert ändert, laufen nach Timern und handeln mit **deinen** Rechten: Knöpfe drücken, Schieberegler und Schalter stellen, Szenen starten, craften, das [Design](dashboard.md) einer Karte ändern, Meldungen schicken. Sie laufen weiter, auch wenn niemand das Dashboard offen hat.

Nimm sie für Logik, die für eine [Regel](automation.md) zu viel ist – Zustand, Schleifen, mehrere Karten auf einmal – und die nicht davon abhängen soll, dass ein Computer geladen bleibt.

## Erste Schritte

1. Öffne **⚙ → Cloud Scripts** im Dashboard. Dafür brauchst du das Recht **Cloud Scripts** (`code.edit`): Admins und der Besitzer haben es, andere bekommen es unter [Zugriff & Rollen](accounts.md).
2. **Neues Skript** → wähle eine Vorlage – *Reaktorwächter*, *Warnung bei wenig Strom* oder *Nachtmodus* – oder ein leeres Skript, gib ihm einen Namen und **Anlegen**.
3. Ändere oben die Karten- und Widget-Namen auf deine (Titel oder ID einer Karte und die ID, die das Programm des Computers dem Widget gibt; `cloud.getCard` listet sie). Der Editor ergänzt beim Tippen `cloud.` und die Lua-Bibliotheken, zeigt die Doku und die Argumente, wenn du auf eine Funktion zeigst, und unterstreicht Fehler.
4. **Speichern** (Strg/⌘ + S). Jedes Speichern ist eine neue Version; der Server behält die letzten 20.
5. **Starten**. Das **Log** neben dem Editor (am Handy: der Tab **Log**) zeigt live, was `print`, `cloud.log` und `cloud.warn` schreiben; Fehler erscheinen dort mit ihrer Zeile, und der Editor markiert diese Zeile.
6. Setz den Haken bei **Autostart**, damit es wieder startet, wenn der Mainframe zurück ist, und nach einem Server-Neustart.
7. Stoppen kannst du es mit **Stoppen**, dem **Redstone-Notschalter** des Mainframes oder im Spiel mit `/cccloud scripts stop [all|<name>]`.

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

> **Tipp:** `cloud.on` und die Timer halten ein Skript nach seiner letzten Zeile am Leben. Ein Skript, das nur einmal durchläuft, ist **fertig** – es steht dann mit diesem Grund als gestoppt da.

## Der Editor

- **Vervollständigung** für `cloud.`, `cloud.store.`, `cloud.script.`, die Lua-Bibliotheken (`string.`, `table.`, `math.` …), die Ereignisnamen in `cloud.on("…")` und die Lua-Schlüsselwörter. Strg + Leertaste öffnet sie überall.
- **Zeig** auf eine Funktion, um ihre Signatur zu sehen, was sie tut, ihre Parameter, das Recht, das sie braucht, ob sie wartet oder pausiert, und ein Beispiel. In einem Aufruf ist der Parameter hervorgehoben, den du gerade tippst.
- **Probleme:** Der Server kompiliert deinen Text kurz nachdem du aufhörst zu tippen und markiert Syntaxfehler; Namen, die die Sandbox entfernt (`io`, `require` …), bekommen eine Warnung. Auch der letzte **Laufzeitfehler** der gespeicherten Version ist an seiner Zeile markiert.
- **Versionen:** Wähle eine, um zu sehen, was sich gegenüber der aktuellen oder der vorigen geändert hat. **Auf v… zurück** speichert ihren Code als neue Version, es geht also nichts verloren.
- **Gleichzeitig gespeichert:** Hat jemand eine neuere Version gespeichert, während du bearbeitet hast, zeigt der Editor den Unterschied, und du behältst deine oder übernimmst ihre.
- Starte ein laufendes Skript nach dem Speichern neu, damit es den neuen Code ausführt.

## So laufen Skripte

- **Rechte:** Ein Skript handelt mit den aktuellen Rechten seines **Autors** – wer es zuletzt gespeichert hat –, aber nie mit mehr als die Person, die es gestartet hat. Verliert der Autor den Zugriff auf den Mainframe, stoppt das Skript. Widgets und Szenen mit PIN-Schutz werden abgelehnt; Widgets und Szenen, die nach Bestätigung fragen, brauchen `{confirm=true}`.
- **Audit-Log:** Jede Aktion landet im Audit-Log des Mainframes als „script *Name* (by *Spieler*) …“. Ab 6 protokollierten Aktionen pro Minute und Skript fasst ein Eintrag pro Minute sie zusammen, damit das Log lesbar bleibt.
- **Lesen** kommt aus einem Abbild, das der Mainframe fünfmal pro Sekunde macht; **Aktionen** gehen in die Warteschlange des Server-Threads (höchstens 20 pro Sekunde und Skript), und Funktionen, die auf den Server warten, wie `cloud.press`, pausieren das Skript für einen Tick.
- **Wann sie stoppen:** nur, wenn der Mainframe entladen wird, keinen Strom mehr hat oder sein Redstone-Notschalter an ist – das Dashboard auszuschalten stoppt sie **nicht**. Autostart-Skripte starten wieder, wenn der Mainframe zurück ist, und nach einem Server-Neustart.
- **Fehler:** Ein Fehler, der nicht abgefangen wird, stoppt das Skript; die Liste zeigt die Meldung und ihre Zeile, das Log behält sie.

## Grenzen

Skripte laufen auf **Worker-Threads, nie auf dem Server-Thread**, damit ein beschäftigtes Skript den Server nicht ruckeln lässt. Diese Grenzen sorgen dafür (Server-Admins können sie in der Konfiguration ändern, `cloudScripts.*`):

| Grenze | Standard |
|---|---|
| CPU-Zeit | 50 ms pro Sekunde und Skript; ein Skript, das sie verbraucht hat, wird pausiert und macht in der nächsten Sekunde weiter |
| Ein Lauf zwischen zwei Pausen | höchstens 200 ms, sonst stoppt das Skript mit „ran too long without pausing“ – eine Endlosschleife lässt sich nicht mit `pcall` abfangen |
| Skripte | 16 pro Mainframe, davon 8 laufend |
| Quelltext | 32 KB pro Skript |
| Texte | 1 MB aus `string.rep`, `string.format`, `table.concat` und `gsub` |
| Speicher | 16 MB je Skript: die Größe dessen, was es behält, neu gemessen, sobald es ein Viertel davon oder das Doppelte seiner letzten Größe neu angelegt hat (und alle 10 s) – eine Schätzung, keine harte Grenze: zwischen zwei Messungen kann es kurz etwa das Dreifache halten, und ein mit `..` gebauter Text bekommt alle Bytes auf einmal, wenn er zum ersten Mal als Ganzes gebraucht wird |
| Timer | mindestens 0,25 s Abstand (`cloud.sleep` mindestens einen Tick), 32 Timer und 64 Handler pro Skript |
| Speicher für Werte | 16 KB pro Skript (`cloud.store`) |
| Log | 20 Zeilen pro Sekunde (mehr werden gezählt und verworfen), die letzten 200 Zeilen bleiben |
| Lesen | aus einem Abbild, 5-mal pro Sekunde |
| Aktionen | über den Server-Thread, höchstens 20 pro Sekunde und Skript |
| Versionen | die letzten 20 pro Skript |

## Sandbox

Cloud Scripts sind Lua mit den üblichen Bibliotheken – aber ohne alles, was nach draußen reicht: kein `io`, kein `os.execute`, kein `debug`, kein `package`/`require`, kein Laden von Dateien, kein Bytecode (`load` nimmt nur Text), kein `string.dump` und kein Zugriff auf Java.

**Globale Funktionen:** `_G`, `_VERSION`, `assert`, `error`, `getmetatable`, `ipairs`, `load`, `next`, `pairs`, `pcall`, `print`, `rawequal`, `rawget`, `rawlen`, `rawset`, `select`, `setmetatable`, `tonumber`, `tostring`, `type`, `unpack`, `xpcall`

**Bibliotheken:**

- `string`: `byte`, `char`, `find`, `format`, `gmatch`, `gsub`, `len`, `lower`, `match`, `rep`, `reverse`, `sub`, `upper`
- `table`: `concat`, `insert`, `pack`, `remove`, `sort`, `unpack`, `move`
- `math`: `abs`, `acos`, `asin`, `atan`, `atan2`, `ceil`, `cos`, `cosh`, `deg`, `exp`, `floor`, `fmod`, `frexp`, `huge`, `ldexp`, `log`, `log10`, `max`, `min`, `modf`, `pi`, `pow`, `rad`, `random`, `randomseed`, `sin`, `sinh`, `sqrt`, `tan`, `tanh`
- `utf8`: `char`, `charpattern`, `codepoint`, `codes`, `len`, `offset`
- `coroutine`: `create`, `isyieldable`, `resume`, `running`, `status`, `wrap`, `yield`
- `bit32`: `arshift`, `band`, `bnot`, `bor`, `btest`, `bxor`, `extract`, `lrotate`, `lshift`, `replace`, `rrotate`, `rshift`
- `os`: `clock`, `date`, `difftime`, `time`

**Nicht verfügbar:** `io`, `debug`, `package`, `require`, `dofile`, `loadfile`, `loadstring`, `setfenv`, `getfenv`, `collectgarbage`, `string.dump`, `string.pack`, `string.packsize`, `string.unpack`, `table.create`, `os.execute`, `os.exit`, `os.getenv`, `os.remove`, `os.rename`, `os.tmpname`

## API-Referenz

Die API der Cloud Scripts. Jedes Skript bekommt eine globale Tabelle, `cloud`. Lesen kommt aus einem Abbild, das der Mainframe fünfmal pro Sekunde macht; Aktionen laufen auf dem Server mit den Rechten des Skript-Autors (nie mehr) und landen im Audit-Log.

### Karten, Werte und Geräte

**`cloud.getCards()`** → `Card[]`

Listet die Karten des Dashboards, höchstens 256.

- Gibt zurück: `Card[]` – Jede Karte, die der Autor sehen darf (widgets = Anzahl)

```lua
for _, card in ipairs(cloud.getCards()) do
  print(card.title, card.online)
end
```

**`cloud.getCard(card)`** → `Card?`

Eine Karte mit all ihren Widgets und Werten.

- Gibt zurück: `Card?` – Die Karte mit ihren Widgets, oder nil

```lua
local reactor = cloud.getCard("Reactor")
if reactor then print(#reactor.widgets) end
```

**`cloud.getValue(card, widget)`** → `any`

Liest den aktuellen Wert eines Widgets.

- Gibt zurück: `any` – Der Wert des Widgets, oder nil, wenn unbekannt

```lua
local heat = cloud.getValue("Reactor", "heat")
```

**`cloud.getWidget(card, widget)`** → `Widget?`

Ein Widget mit Typ, Beschriftung, Wert und Grenzen.

- Gibt zurück: `Widget?` – Das Widget, oder nil

**`cloud.isOnline(card?)`** → `boolean`

Ohne Argument: der Mainframe ist online. Mit einer Karte: dieses Gerät ist online.

- `card` (CardRef, optional)

**`cloud.getDevices()`** → `Device[]`

Die mit dem Mainframe verbundenen Geräte (Uplinks, Bridges, Relays, Redstone-Geräte).

**`cloud.getMainframe()`** → `Mainframe`

Der Mainframe: Name, online, Dashboard-Schalter und Strom.

**`cloud.isEnabled()`** → `boolean`

Der Dashboard-Schalter (wie beim Peripheral cloud_mainframe).

**`cloud.getEnergy()`** → `integer`

Gespeicherte FE im Puffer des Mainframes.

**`cloud.getEnergyCapacity()`** → `integer`

Puffergröße in FE.

**`cloud.getEnergyUsage()`** → `integer`

Verbrauch in FE pro Tick.

**`cloud.getItemCount(card, item)`** → `integer?` · braucht `storage.search` · wartet auf den Server (einen Tick)

Zählt ein Item im Speicher hinter einer Speicherkarte (AE2, Refined Storage). Wartet auf den Server (einen Tick).

- Gibt zurück: `integer?` – Wie viele gelagert sind, oder nil, wenn der Speicher nicht erreichbar ist

```lua
local iron = cloud.getItemCount("ME Storage", "minecraft:iron_ingot")
```

**`cloud.getRedstone(channel)`** → `integer?`

Der Pegel eines Funk-Redstone-Kanals des Netzwerks.

- Gibt zurück: `integer?` – Pegel 0–15, oder nil für einen unbekannten Kanal

**`cloud.getRules()`** → `Rule[]`

Die Regeln des Mainframes.

**`cloud.getScenes()`** → `Scene[]`

Die Szenen des Mainframes.

### Aktionen

**`cloud.press(card, widget, options?)`** → `boolean, string?` · braucht `act.buttons` · wartet auf den Server (einen Tick)

Drückt einen Knopf, wie ein Klick auf dem Dashboard.

- `options` (table, optional) – \{confirm=true\} für Widgets, die nach Bestätigung fragen
- Gibt zurück: `boolean`; `string?` – Der Fehler, z. B. not_allowed, pin_required, confirm_required, offline

```lua
local ok, err = cloud.press("Doors", "open")
if not ok then cloud.warn("could not open: " .. err) end
```

**`cloud.set(card, widget, value, options?)`** → `boolean, string?` · braucht `act.buttons` · wartet auf den Server (einen Tick)

Stellt einen Schalter, Regler, eine Auswahl, Zahl oder einen Text. Dieselben Prüfungen wie für eine Person am Dashboard; PIN-Widgets werden abgelehnt.

- `value` (any) – boolean, Zahl oder Text, wie das Widget es nimmt
- `options` (table, optional) – \{confirm=true\} für Widgets, die nach Bestätigung fragen

```lua
cloud.set("Base lights", "power", false)
```

**`cloud.setRedstone(card, side, level)`** → `boolean, string?` · braucht `act.redstone` · wartet auf den Server (einen Tick)

Setzt einen Redstone-Ausgang einer Computer-Karte (automatischer Modus).

- `side` (string) – top, bottom, left, right, front oder back
- `level` (integer) – 0–15

**`cloud.craft(card, item, amount)`** → `boolean, string?` · braucht `storage.craft` · wartet auf den Server (einen Tick)

Gibt einen Crafting-Auftrag an eine Speicherkarte, begrenzt durch das Crafting-Limit des Servers.

**`cloud.setEnabled(enabled)`** → `boolean, string?` · braucht `power.toggle` · wartet auf den Server (einen Tick)

Schaltet das Dashboard ein oder aus (wie beim Peripheral cloud_mainframe). Skripte laufen weiter.

**`cloud.runScene(scene, options?)`** → `boolean, string?` · braucht `act.buttons` · wartet auf den Server (einen Tick)

Startet eine Szene. Jeder Schritt wird mit den Rechten des Skripts geprüft; Szenen mit PIN werden abgelehnt.

- `scene` (string) – Szenen-ID oder Name
- `options` (table, optional) – \{confirm=true\} für Szenen, die nach Bestätigung fragen

**`cloud.setRuleEnabled(rule, enabled)`** → `boolean, string?` · braucht `rules.toggle` · wartet auf den Server (einen Tick)

Schaltet eine Regel ein oder aus (wie beim Peripheral cloud_mainframe).

- `rule` (string) – Regel-ID oder Name

**`cloud.runRule(rule)`** → `boolean, string?` · braucht `rules.toggle` · wartet auf den Server (einen Tick)

Führt die Aktionen einer Regel jetzt aus, wie „Jetzt ausführen“ auf dem Dashboard.

- `rule` (string) – Regel-ID oder Name

**`cloud.setStyle(card, design?)`** → `boolean, string?` · braucht `layout.edit` · wartet auf den Server (einen Tick)

Setzt das Design einer Karte im Dashboard-Layout, wie der Layout-Editor: Hintergrund, eigenes CSS und Kartenteile, geprüft wie im Editor. Es ersetzt das ganze Design der Karte, auch das, das ihr Programm mit dash:style setzt.

- `design` (table, optional) – \{bg=, bgColor=, css=, parts=\{title=, gauge_fill=, button=, …\}\}; nil entfernt das eigene Design der Karte

```lua
cloud.setStyle("Reactor", {parts = {title = "color: #ff5a4f", gauge_fill = "color: #ff5a4f"}})
```

### Ereignisse

**`cloud.on(event, filter?, handler)`** → `integer`

Ruft den Handler bei jedem Ereignis auf. above/below lösen einmal aus, wenn der Wert die Schwelle überschreitet.

- `event` (string) – change, above, below, online, offline oder redstone
- `filter` (table, optional) – \{card=, widget=\} (above/below auch value=), \{channel=\} für redstone
- `handler` (function) – Wird mit dem Event aufgerufen
- Gibt zurück: `integer` – Handler-ID für cloud.off

```lua
cloud.on("above", {card="Reactor", widget="heat", value=80}, function(e)
  cloud.set("Reactor", "active", false)
end)
```

**`cloud.wait(event, filter?, timeout?)`** → `Event?` · pausiert das Skript

Pausiert, bis das Ereignis passiert (wie os.pullEvent, mit Filter).

- `filter` (table, optional)
- `timeout` (number, optional) – Sekunden; nil wartet ewig
- Gibt zurück: `Event?` – Das Event, oder nil nach dem Timeout

```lua
local e = cloud.wait("online", {card="Reactor"}, 60)
```

**`cloud.off(id)`** → `boolean`

Entfernt einen Event-Handler oder bricht einen Timer ab.

- Gibt zurück: `boolean` – true, wenn es ihn gab

### Timer

**`cloud.cancel(id)`** → `boolean`

Bricht einen Timer ab (dasselbe wie cloud.off).

**`cloud.sleep(seconds)`** · pausiert das Skript

Pausiert dieses Skript (oder diesen Handler), ohne CPU zu verbrauchen. Mindestens einen Tick (0,05 s).

```lua
cloud.sleep(5)
```

**`cloud.every(seconds, handler)`** → `integer`

Ruft den Handler immer wieder auf. Ein Lauf, der noch beschäftigt ist, wird nicht doppelt gestartet.

- `seconds` (number) – Mindestens 0,25
- Gibt zurück: `integer` – Timer-ID

```lua
cloud.every(10, function()
  print(cloud.getEnergy())
end)
```

**`cloud.after(seconds, handler)`** → `integer`

Ruft den Handler einmal nach der Wartezeit auf.

**`cloud.at(time, handler)`** → `integer`

Ruft den Handler zu einer Uhrzeit auf: echte Zeit oder Spielzeit (0 = 6:00, 13000 = Abenddämmerung).

- `time` (any) – "HH:MM" (jeden Tag, Zeitzone des Servers), \{time="HH:MM", days=\{"mon","fri"\}\} oder \{gameTime=13000\}

```lua
cloud.at({gameTime=13000}, function()
  cloud.set("Lights", "power", true)
end)
```

### Benachrichtigungen

**`cloud.notify(text, level?)`** → `boolean`

Ein Toast auf jedem Dashboard und eine Push-Benachrichtigung (wie beim Peripheral cloud_uplink).

- `level` (string, optional) – info (Standard), warn oder crit
- Gibt zurück: `boolean` – false, wenn die Ratenbegrenzung es verworfen hat

**`cloud.alert(text, level?)`** → `boolean`

Dasselbe wie cloud.notify.

- `level` (string, optional)

**`cloud.toast(text, level?)`** → `boolean`

Nur ein Toast auf den offenen Dashboards, kein Push.

- `level` (string, optional)

**`cloud.push(text, level?, title?)`** → `boolean`

Nur eine Push-Benachrichtigung an die Push-Ziele des Mainframes.

- `level` (string, optional)
- `title` (string, optional) – Standard: der Name des Skripts

### Speicher

**`cloud.store.get(key)`** → `any`

Liest einen gespeicherten Wert (er übersteht Neustarts und neue Versionen).

```lua
local runs = (cloud.store.get("runs") or 0) + 1
cloud.store.set("runs", runs)
```

**`cloud.store.set(key, value)`** → `boolean`

Speichert einen Wert für dieses Skript (nil löscht ihn). Klein: siehe die Grenzen.

- `value` (any) – nil, boolean, Zahl, Text oder eine Tabelle davon

**`cloud.store.delete(key)`** → `boolean`

Löscht einen gespeicherten Wert.

**`cloud.store.keys()`** → `string[]`

Alle gespeicherten Schlüssel.

### Log und Infos

**`cloud.log(...)`**

Schreibt eine Zeile ins Log des Skripts (print macht dasselbe).

**`cloud.warn(...)`**

Schreibt eine Warnung ins Log des Skripts.

**`cloud.time()`** → `integer`

Die echte Uhrzeit.

- Gibt zurück: `integer` – Millisekunden seit 1970

**`cloud.clock()`** → `number`

Sekunden seit dem Start des Skripts.

- Gibt zurück: `number` – Sekunden seit dem Start des Skripts

**`cloud.gameTime()`** → `integer`

Die Uhrzeit im Spiel.

- Gibt zurück: `integer` – 0–23999 (0 = 6:00, 6000 = Mittag, 13000 = Abenddämmerung)

**`cloud.script`** → `ScriptInfo`

Dieses Skript: ID, Name, Autor, Version.

### Ereignisse

Für `cloud.on(event, filter, handler)` und `cloud.wait(event, filter, timeout)`. Der Handler bekommt eine Event-Tabelle.

| Ereignis | Filter | Was passiert |
|---|---|---|
| `change` | `card`, `widget` | Der Wert eines Widgets hat sich geändert. Ohne Filter: jedes Widget jeder Karte. |
| `above` | `card`, `widget`, `value` | Eine Zahl ist über value gestiegen (einmal je Überschreitung; booleans zählen als 0/1). |
| `below` | `card`, `widget`, `value` | Eine Zahl ist unter value gefallen (einmal je Unterschreitung). |
| `online` | `card` | Ein Gerät ist online gekommen. |
| `offline` | `card` | Ein Gerät ist offline gegangen. |
| `redstone` | `channel` | Ein Funk-Redstone-Kanal hat seinen Pegel geändert. |

### Typen

Tabellen, die die Funktionen zurückgeben oder nehmen. Ein `?` nach einem Typ heißt: kann `nil` sein.

**CardRef** – ein Text: `string`. Eine Karte: ihre ID, ihr Titel (Groß-/Kleinschreibung egal, der erste Treffer) oder "mainframe" für den Mainframe selbst (Widgets energy, capacity, percent, usage, enabled).

**Card**

| Feld | Typ | Bedeutung |
|---|---|---|
| `id` | `string` | Karten-ID |
| `title` | `string` | Titel auf dem Dashboard |
| `online` | `boolean` | Das Gerät ist online |
| `kind` | `string` | "lua" für Programmkarten, sonst die Vorlage (storage, redstone, …) |
| `widgets` | `Widget[]` | getCard: die Widgets; getCards: wie viele |

**Widget**

| Feld | Typ | Bedeutung |
|---|---|---|
| `id` | `string` | Widget-ID |
| `type` | `string` | button, toggle, slider, gauge, label, … |
| `label` | `string?` | Beschriftung |
| `value` | `any` | Aktueller Wert |
| `unit` | `string?` | Einheit |
| `min` | `number?` | Minimum |
| `max` | `number?` | Maximum |
| `pin` | `boolean?` | Braucht die PIN: Skripte können es nicht bedienen |
| `confirm` | `boolean?` | Fragt nach Bestätigung: \{confirm=true\} mitgeben |

**Device**

| Feld | Typ | Bedeutung |
|---|---|---|
| `id` | `string` | Geräte-ID (bei Uplinks und Bridges die Karten-ID) |
| `type` | `string` | uplink, bridge, relay, switch, lamp, … |
| `name` | `string` | Name |
| `status` | `string` | online, offline, no_power, out_of_range, … |
| `online` | `boolean` | status == "online" |

**Mainframe**

| Feld | Typ | Bedeutung |
|---|---|---|
| `id` | `string` | Mainframe-ID |
| `name` | `string` | Name |
| `online` | `boolean` | Geladen, mit Strom, nicht per Redstone abgeschaltet |
| `enabled` | `boolean` | Der Dashboard-Schalter |
| `energy` | `integer` | Gespeicherte FE |
| `capacity` | `integer` | Puffergröße in FE |
| `percent` | `number` | energy / capacity × 100 |
| `usage` | `integer` | FE pro Tick |

**Rule**

| Feld | Typ | Bedeutung |
|---|---|---|
| `id` | `string` | Regel-ID |
| `name` | `string` | Name |
| `enabled` | `boolean` | Eingeschaltet |
| `trigger` | `string` | Auslöser |
| `runs` | `integer` | Läufe seit dem Serverstart |
| `lastResult` | `string?` | ok oder ein Fehler |

**Scene**

| Feld | Typ | Bedeutung |
|---|---|---|
| `id` | `string` | Szenen-ID |
| `name` | `string` | Name |
| `needsPin` | `boolean` | Braucht die PIN: Skripte können sie nicht starten |
| `confirm` | `boolean` | Fragt nach Bestätigung: \{confirm=true\} mitgeben |
| `running` | `boolean` | Läuft gerade |

**Event**

| Feld | Typ | Bedeutung |
|---|---|---|
| `type` | `string` | change, above, below, online, offline oder redstone |
| `card` | `string?` | Karten-ID |
| `title` | `string?` | Kartentitel |
| `widget` | `string?` | Widget-ID |
| `value` | `any` | Neuer Wert (redstone: der Pegel) |
| `old` | `any` | Wert davor |
| `channel` | `string?` | Funk-Redstone-Kanal |
| `time` | `integer` | Wann es passiert ist (ms seit 1970) |

**ScriptInfo**

| Feld | Typ | Bedeutung |
|---|---|---|
| `id` | `string` | Skript-ID |
| `name` | `string` | Skriptname |
| `author` | `string` | Wer es zuletzt gespeichert hat; das Skript handelt mit dessen Rechten |
| `version` | `integer` | Gespeicherte Version |
| `language` | `string` | "lua" |


---
# Aus der Dashboard-Doku erzeugt (tools/gen_handbook.ts) – Änderungen in cloud/web/src/docs/content machen.
navigation:
  title: "Lua-Referenz"
  position: 4
  icon: computercraft:computer_advanced
---

# Lua-Referenz

Alles, was ein Programm mit CC: Cloud tun kann. Im Spiel zeigt `help cloud` eine Kurzfassung.

## Das Peripheral holen

```lua
local cloud = peripheral.find("cloud_uplink")
```

Der Uplink muss den Computer berühren oder im Netzwerk seines Wired Modems hängen. Mehrere Uplinks? Nimm `peripheral.wrap("cloud_uplink_0")`.

## Ein erstes Programm

```lua
local cloud = peripheral.find("cloud_uplink")
cloud.setTitle("My Base")
cloud.gauge("temp", "Temperature", {max=1200, unit="K", warn=900, crit=1100})
cloud.toggle("lights", "Lights", {value=false})
cloud.button("alarm", "Alarm", {color="red", confirm=true})

while true do
  local _, id, value, user = os.pullEvent("cloud_action")
  if id == "lights" then
    redstone.setOutput("back", value)
    cloud.set("lights", value)
  elseif id == "alarm" then
    cloud.alert(user .. " pressed the alarm", "warn")
  end
end
```

## Widgets

Jedes Widget bekommt eine **ID** (1–32 Zeichen: a–z, 0–9, _), eine Beschriftung und optional eine Optionstabelle. Ein erneuter Aufruf mit derselben ID aktualisiert es.

| Methode | Optionen |
|---|---|
| `cloud.setTitle(text)` | Kartentitel (max. 48 Zeichen) |
| `cloud.button(id, label, opts)` | color, confirm, pin |
| `cloud.toggle(id, label, opts)` | value, confirm, pin |
| `cloud.slider(id, label, opts)` | min, max, step, value, unit, pin |
| `cloud.gauge(id, label, opts)` | min, max, unit, warn, crit, value |
| `cloud.label(id, text)` | – |
| `cloud.chart(id, label, opts)` | min, max, unit, points (60–300), interval, style = "line" \| "sparkline" |
| `cloud.progress(id, label, opts)` | min, max, unit, value |
| `cloud.indicator(id, label, opts)` | value, onColor, offColor |
| `cloud.select(id, label, opts)` | options = \{"low","mid","high"\}, value |
| `cloud.number(id, label, opts)` | min, max, step, value, unit |
| `cloud.text(id, label, opts)` | maxLength (≤ 256), value, placeholder |
| `cloud.buttons(id, label, opts)` | options = \{"A","B","C"\} |
| `cloud.itemSearch(id, label, opts)` | bridge = Peripheral-Name (eine Seite oder eine Bridge im Kabelnetz des Computers, z. B. `me_cloud_bridge_0`), max |
| `cloud.watchlist(id, label, opts)` | bridge |
| `cloud.jobs(id, label, opts)` | bridge |

Farben: cyan, green, yellow, red, gray. Eine Gauge gilt als „wenig ist schlecht“, wenn crit \< warn (z. B. Kühlmittel).

## Werte

| Methode | Macht |
|---|---|
| `cloud.set(id, value)` | Gauge, Label, Toggle, Chart (hängt einen Punkt an) … aktualisieren |
| `cloud.setMany({id=value, …})` | mehrere Werte auf einmal |
| `cloud.remove(id)` | ein Widget entfernen |
| `cloud.clear()` | alles entfernen (die automatische Computer-Karte kommt zurück) |

```lua
cloud.setMany({temp = 812, load = 64, running = true})
```

## Ereignisse

Bedient jemand ein Widget, bekommt jeder Computer am Uplink ein `cloud_action`-Ereignis:

```lua
while true do
  local event, id, value, user, side = os.pullEvent("cloud_action")
  print(user .. " used " .. id .. " = " .. tostring(value))
end
```

| Widget | value |
|---|---|
| button | `true` |
| toggle | `true` / `false` |
| slider, number | Zahl |
| select, buttons | die gewählte Option (String) |
| text | String (reiner Text) |
| itemSearch-Craft | Tabelle \{op="craft", name, count, jobId\} |

`user` ist der Gerätename, `side` der Peripheral-Name des Uplinks. Pro Computer warten höchstens 64 Aktionen in der Warteschlange.

## Layout: Seiten und Gruppen

```lua
local cloud = peripheral.find("cloud_uplink")
cloud.setTitle("Reactor")
cloud.page("reactor", "Reactor", {icon="flame"})
cloud.page("power", "Power", {icon="bolt"})
cloud.group("core", "Core", {page="reactor", cols=2})
cloud.gauge("temp", "Temperature", {max=1200, unit="K", warn=900, crit=1100, group="core"})
cloud.chart("load", "Load", {min=0, max=100, unit="%", points=120, group="core", span=2})
cloud.indicator("running", "Running", {value=true, onColor="green", page="power"})
```

- `cloud.page(id, title, {icon=})` – ein Tab. Mit Seiten darf ein Uplink 64 statt 32 Widgets haben.
- `cloud.group(id, title, {page=, cols=1..4})` – eine Karte; Widgets kommen mit `group=` hinein.
- Jedes Widget akzeptiert außerdem `page`, `group`, `span` (1–4), `order` (0–10000), `color` und `icon`.
- Icons: home, bolt, flame, drop, box, fan, lock, warning, gear, chart, star, leaf, heart, cpu, shield, pick, lever, button, lamp, sensor.

## Meldungen und Status

| Methode | Macht |
|---|---|
| `cloud.alert(text, level)` | Einblendung + Protokoll; level "info", "warn" oder "crit"; max. 1 pro Sekunde |
| `cloud.notify(text, level)` | wie alert |
| `cloud.isOnline()` | true, wenn der Mainframe online und das Dashboard an ist |
| `cloud.getClients()` | Anzahl verbundener Dashboards |
| `cloud.getLastClientSeen()` | Sekunden seit zuletzt ein Dashboard verbunden war, oder nil |

## cloud_mainframe

Ein Computer neben dem Mainframe kann ihn auslesen:

| Methode | Liefert |
|---|---|
| `isEnabled()` | Dashboard eingeschaltet |
| `isOnline()` | geladen, mit Strom, kein Redstone-Not-Aus und eingeschaltet |
| `setEnabled(bool)` | Dashboard ein-/ausschalten |
| `getUplinks()` | Liste von \{id, title, online, loaded, x, y, z, dimension\} |
| `getEnergy()`, `getEnergyCapacity()`, `getEnergyUsage()` | gespeicherte FE, Puffergröße, FE/t |
| `getNetwork()` | \{nodes, links, routes\} – siehe [Netzwerk](network.md) |

```lua
local mf = peripheral.find("cloud_mainframe")
print("Dashboard on: " .. tostring(mf.isEnabled()))
print("Online: " .. tostring(mf.isOnline()))
for _, u in ipairs(mf.getUplinks()) do
  print(u.title or u.id, u.x, u.y, u.z, u.online and "online" or "offline")
end
print(("Power: %d / %d FE, %d FE/t"):format(mf.getEnergy(), mf.getEnergyCapacity(), mf.getEnergyUsage()))
```

Der Kopplungscode ist aus Lua nie lesbar.

## Bridges

Peripheral-Typen `me_cloud_bridge` und `rs_cloud_bridge`, gleiche Methoden:

| Methode | Liefert |
|---|---|
| `isConnected()` | mit einem Netzwerk mit Strom verbunden |
| `getEnergy()`, `getMaxEnergy()`, `getEnergyUsage()` | Netzwerk-Energie |
| `getStorage()` | \{itemsUsed, itemsTotal, typesUsed, fluidsUsed, fluidsTotal\} |
| `listItems([filter], [limit=256])` | \{name, displayName, count, craftable\}… |
| `findItems(query, [limit=50])` | dasselbe, passend zu ID oder Name |
| `getItem(name)` | ein Item oder nil |
| `isCraftable(name)` | boolean |
| `craft(name, count)` | \{ok, jobId\} oder \{ok=false, error\} |
| `getCraftingJobs()` | laufende Aufträge |
| `cancelCraft(jobId)` | wo unterstützt |
| `listFluids([limit])` | Flüssigkeiten |

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

## Grenzen

- IDs: 1–32 Zeichen a–z, 0–9, _
- 32 Widgets pro Uplink, 64 mit Seiten
- Titel und Beschriftungen 48 Zeichen, Textwerte 256, Meldungstext 200
- Meldungen: 1 pro Sekunde und Uplink

## Skripte aus dem Browser hochladen

Admins können Lua-Programme auf einen Computer bringen, ohne sie im Spiel abzutippen: **\</>** oben in der Karte des Computers anklicken.

- **Upload:** eine `.lua`-Datei hineinziehen oder Code einfügen, Dateinamen wählen (`name.lua` oder `ordner/name.lua`), dann **Upload & run**. Vorhandene Dateien werden nur nach Bestätigung ersetzt.
- **Run it now** startet das Programm auf Advanced-Computern in einem neuen Tab. Normale Computer haben keine Tabs: Das Dashboard fragt, ob der Computer neu starten und es einmal ausführen soll.
- **Start it automatically** schreibt eine `startup.lua`, die deine Datei bei jedem Start ausführt.
- **Examples:** fertige Programme (Kontrollraum, Testprogramm, die Widget-Demo) – auswählen, anpassen, hochladen.
- **Files:** sehen, was auf dem Computer liegt, Dateien öffnen, herunterladen oder löschen, und wie viel Speicher frei ist.

Das sehen nur Admins des Mainframes, und Änderungen brauchen die Mainframe-PIN, falls eine gesetzt ist. Der Code läuft auf diesem CC-Computer in CCs Sandbox – nie auf dem Minecraft-Server selbst. Jeder Upload wird protokolliert, und der Besitzer bekommt eine Chat-Nachricht. Grenzen: 256 KB pro Datei, 10 Änderungen pro Minute.

## Script Store

Fertige Programme aus der Community: **Script Store** im Menü des Mainframes (Admins). Jedes Skript zeigt, was es tut, was es **nutzt** (Redstone, Peripherie, Internet …) und den vollständigen Quelltext – vor der Anzeige gegen den SHA-256 im Index des Stores geprüft. Der Index ist vom Store signiert: Dein Browser prüft die Signatur, wo er kann (**signed ✓**), und der Minecraft-Server prüft sie noch einmal, bevor er eine Datei schreibt. Das beweist, dass die Dateien aus dem Store stammen – nicht, dass der Code sicher ist, also **lies den Code, bevor du installierst**. Computer wählen und installieren; sofort starten oder automatisch beim Start. Installierte Store-Skripte zeigen **Update available**, wenn es eine neuere Version gibt, und du wirst gewarnt, bevor eine auf dem Computer geänderte Datei überschrieben wird.

Auf der Projekt-Website öffnet **Install** dein Dashboard unter `/store#install=<skript>` – dieselbe Ansicht, ohne deinen Klick wird nichts installiert. Server-Admins können den Store abschalten (`scriptStore=false`). Community-Skripte laufen auf deinem CC-Computer und können steuern, was er steuert – den Minecraft-Server selbst können sie nicht anfassen.


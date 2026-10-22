import{S as e,_ as t,a as n,b as r,c as i,d as a,f as o,g as s,h as c,i as l,l as ee,m as te,n as u,o as ne,p as re,r as d,s as f,t as p,u as ie,v as ae,x as m,y as oe}from"./index-CAbUqyll.js";var h=Object.defineProperty,se=((e,t)=>{let n={};for(var r in e)h(n,r,{get:e[r],enumerable:!0});return t||h(n,Symbol.toStringTag,{value:`Module`}),n})({CADDYFILE:()=>A,CLOUDFLARED:()=>j,CLOUDFLARED_YML:()=>M,COMMANDS:()=>F,FENCE:()=>"```",LUA_BRIDGE:()=>x,LUA_BRIDGE_WIDGETS:()=>S,LUA_EVENT:()=>_,LUA_FIRST:()=>g,LUA_LAYOUT:()=>v,LUA_MAINFRAME:()=>b,LUA_SETMANY:()=>y,PKCS8:()=>P,RECIPES:()=>le,SHELL_EDIT:()=>T,SHELL_LUA:()=>D,SHELL_RUN:()=>E,TOML_CADDY:()=>k,TOML_LAN:()=>O,TOML_TLS:()=>N,UI_FARM:()=>C,UI_QUICK:()=>ce,UI_REACTOR:()=>w,code:()=>de,recipe:()=>ue}),g=`local cloud = peripheral.find("cloud_uplink")
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
end`,_=`while true do
  local event, id, value, user, side = os.pullEvent("cloud_action")
  print(user .. " used " .. id .. " = " .. tostring(value))
end`,v=`local cloud = peripheral.find("cloud_uplink")
cloud.setTitle("Reactor")
cloud.page("reactor", "Reactor", {icon="flame"})
cloud.page("power", "Power", {icon="bolt"})
cloud.group("core", "Core", {page="reactor", cols=2})
cloud.gauge("temp", "Temperature", {max=1200, unit="K", warn=900, crit=1100, group="core"})
cloud.chart("load", "Load", {min=0, max=100, unit="%", points=120, group="core", span=2})
cloud.indicator("running", "Running", {value=true, onColor="green", page="power"})`,y=`cloud.setMany({temp = 812, load = 64, running = true})`,b=`local mf = peripheral.find("cloud_mainframe")
print("Dashboard on: " .. tostring(mf.isEnabled()))
print("Online: " .. tostring(mf.isOnline()))
for _, u in ipairs(mf.getUplinks()) do
  print(u.title or u.id, u.x, u.y, u.z, u.online and "online" or "offline")
end
print(("Power: %d / %d FE, %d FE/t"):format(mf.getEnergy(), mf.getEnergyCapacity(), mf.getEnergyUsage()))`,x=`local bridge = peripheral.find("me_cloud_bridge") or peripheral.find("rs_cloud_bridge")
local s = bridge.getStorage()
print(("Items: %d / %d"):format(s.itemsUsed, s.itemsTotal))
for _, item in ipairs(bridge.findItems("iron", 5)) do
  print(item.displayName, item.count, item.craftable and "(craftable)" or "")
end
local result = bridge.craft("minecraft:iron_block", 4)
if result.ok then print("Job " .. result.jobId) else print("Error: " .. result.error) end`,S=`local cloud = peripheral.find("cloud_uplink")
local bridge = peripheral.find("me_cloud_bridge")
local name = peripheral.getName(bridge)
cloud.setTitle("Storage")
cloud.itemSearch("search", "Search & craft", {bridge=name, max=1024})
cloud.watchlist("watch", "Watchlist", {bridge=name})
cloud.jobs("jobs", "Crafting jobs", {bridge=name})`,ce=`local ui = require("cloud.ui")
local dash = ui.connect()
dash:title("Reactor")
local temp = dash:gauge("temp", "Temperature", {max=1200, unit="K", warn=900, crit=1100})
dash:button("scram", "SCRAM", {color="red", confirm=true, pin=true}, function(user)
  redstone.setOutput("back", true)
  dash:notify(user .. " pressed SCRAM", "crit")
end)
dash:every(1, function() temp:set(math.random(600, 1000)) end)
dash:run()`,C=`-- startup.lua: a tree farm with lights, a counter and a harvest button
local ui = require("cloud.ui")
local dash = ui.connect()
dash:title("Tree Farm")

local lights = false
dash:toggle("lights", "Grow lights", {value=false}, function(user, value)
  lights = value
  redstone.setOutput("top", value)
end)

local logs = dash:gauge("logs", "Logs in chest", {max=1728, warn=1500, crit=1700})
local chest = peripheral.find("minecraft:chest")

dash:button("harvest", "Harvest now", {color="green"}, function(user)
  redstone.setOutput("back", true)
  sleep(0.5)
  redstone.setOutput("back", false)
  dash:notify(user .. " started a harvest")
end)

dash:every(5, function()
  local count = 0
  for _, item in pairs(chest.list()) do count = count + item.count end
  logs:set(count)
end)

dash:run()`,w=`-- A Mekanism-style reactor panel with pages, a chart and a PIN-protected SCRAM
local ui = require("cloud.ui")
local dash = ui.connect()
local reactor = peripheral.find("fissionReactorLogicAdapter")

dash:title("Fission Reactor")
dash:page("main", "Reactor", {icon="flame"})
dash:page("log", "History", {icon="chart"})
dash:group("core", "Core", {page="main", cols=2})

local temp = dash:gauge("temp", "Temperature", {max=1200, unit="K", warn=900, crit=1100, group="core"})
local burn = dash:slider("burn", "Burn rate", {min=0, max=20, step=0.5, value=1, unit="mB/t", group="core"},
  function(user, value) reactor.setBurnRate(value) end)
local running = dash:indicator("running", "Active", {group="core"})
local history = dash:chart("history", "Temperature", {min=0, max=1200, unit="K", points=300, interval=2, page="log"})

dash:button("scram", "SCRAM", {color="red", confirm=true, pin=true, page="main"}, function(user)
  reactor.scram()
  dash:notify(user .. " pressed SCRAM", "crit")
end)

dash:every(2, function()
  local k = reactor.getTemperature()
  dash:setMany({temp = math.floor(k), history = math.floor(k), running = reactor.getStatus()})
  if k > 1100 then reactor.scram(); dash:notify("Auto-SCRAM at " .. math.floor(k) .. " K", "crit") end
end)

dash:run()`,T=`edit startup.lua`,E=`startup`,D=`lua
lua> peripheral.find("cloud_uplink").getClients()
1
lua> exit()`,O=`[web]
webAllowInternet = false   # default: only LAN addresses may connect`,k=`[web]
webBind = "127.0.0.1"
webAllowInternet = true
webTrustedProxies = ["127.0.0.1"]
publicUrl = "https://dash.example.com"`,A=`dash.example.com {
    reverse_proxy 127.0.0.1:8787
}`,j=`cloudflared tunnel login
cloudflared tunnel create cccloud
cloudflared tunnel route dns cccloud dash.example.com
cloudflared tunnel run cccloud`,M=`tunnel: cccloud
credentials-file: /root/.cloudflared/<tunnel-id>.json
ingress:
  - hostname: dash.example.com
    service: http://127.0.0.1:8787
  - service: http_status:404`,N=`[web]
webAllowInternet = true
tlsCertPath = "/etc/letsencrypt/live/mc.example.com/fullchain.pem"
tlsKeyPath = "/srv/mc/privkey-pkcs8.pem"
publicUrl = "https://mc.example.com:8787"`,P=`openssl pkcs8 -topk8 -nocrypt -in privkey.pem -out privkey-pkcs8.pem`,F=`/cccloud status
/cccloud devices
/cccloud revoke <device name>
/cccloud revoke all
/cccloud trust <player>
/cccloud untrust <player>
/cccloud pin set <4-8 digits>
/cccloud pin clear`,le={core:{result:`cccloud:cloud_core`,count:2,grid:[`nPn`,`PEP`,`nPn`],key:{n:`#c:nuggets/gold`,P:`minecraft:prismarine_crystals`,E:`#c:ender_pearls`}},mainframe:{result:`cccloud:cloud_mainframe`,grid:[`GKG`,`GAG`,`GDG`],key:{G:`#c:ingots/gold`,K:`cccloud:cloud_core`,A:`computercraft:computer_advanced`,D:`computercraft:disk_drive`}},uplink:{result:`cccloud:cloud_uplink`,count:2,grid:[`SKS`,`SMS`],key:{S:`minecraft:stone`,K:`cccloud:cloud_core`,M:`computercraft:wired_modem`}},advancedUplink:{result:`cccloud:advanced_cloud_uplink`,grid:[`GGG`,`GUG`,`GEG`],key:{G:`#c:ingots/gold`,U:`cccloud:cloud_uplink`,E:`#c:ender_pearls`}},linkCard:{result:`cccloud:cloud_link_card`,shapeless:!0,items:[`minecraft:paper`,`#c:nuggets/gold`,`#c:dusts/redstone`,`minecraft:prismarine_crystals`]},meBridge:{result:`cccloud:me_cloud_bridge`,grid:[`GKG`,`MIM`,`GGG`],key:{G:`#c:ingots/gold`,K:`cccloud:cloud_core`,M:`computercraft:wired_modem`,I:`ae2:interface`}},rsBridge:{result:`cccloud:rs_cloud_bridge`,grid:[`GKG`,`MIM`,`GGG`],key:{G:`#c:ingots/gold`,K:`cccloud:cloud_core`,M:`computercraft:wired_modem`,I:`refinedstorage:interface`}}},ue=e=>`\`\`\`recipe\n${JSON.stringify(e)}\n\`\`\``,de=(e,t)=>`\`\`\`${e}\n${t}\n\`\`\``,{code:I,recipe:L,RECIPES:R}=se,fe={start:`# Erste Schritte

CC: Cloud bringt deine ComputerCraft-Computer und dein Lager auf ein Dashboard im Browser deines Handys oder PCs. Der Minecraft-Server stellt das Dashboard selbst bereit – kein Konto, kein externer Dienst.

> **Tipp:** Der schnellste Weg: Cloud Mainframe craften, mit Strom versorgen, einen Cloud Uplink an einen Computer setzen, beide mit einer Cloud Link Card verbinden und das Handy koppeln. Der Computer erscheint automatisch auf dem Dashboard – ganz ohne Code.

## Die Blöcke {#blocks}

| Block / Item | Funktion |
|---|---|
| **Cloud Mainframe** | Das Herz einer Basis. Zwei Blöcke hoch. Stellt die Verbindung zum Dashboard her, zeigt den Kopplungscode und verwaltet Geräte. Einer pro Basis oder Team. Braucht FE-Strom. |
| **Cloud Uplink** | Haftet wie ein Modem an einer Blockseite. An einem Computer wird er zur Karte dieses Computers im Dashboard. Reichweite 128 Blöcke, gleiche Dimension. |
| **Advanced Cloud Uplink** | Genauso, aber mit unbegrenzter Reichweite und dimensionsübergreifend. |
| **Cloud Link Card** | Verbindet Uplinks und Bridges mit einem Mainframe. |
| **ME / RS Cloud Bridge** | Hängt sich in ein Applied-Energistics-2- oder Refined-Storage-Netzwerk. Mit einem Uplink darauf bekommst du eine fertige Lager-Karte (Suche, Craften, Merkliste). Gibt es nur, wenn AE2 / RS installiert ist. |
| **Cloud Core** | Crafting-Zutat. |

## Crafting-Rezepte {#recipes}

Zutaten mit „(beliebig)“ akzeptieren jede Variante aus dem Tag (z. B. jeden Goldbarren). JEI/EMI zeigen im Spiel dieselben Rezepte.

### Cloud Core
${L(R.core)}

### Cloud Mainframe
${L(R.mainframe)}

### Cloud Uplink
${L(R.uplink)}

### Advanced Cloud Uplink
${L(R.advancedUplink)}

### Cloud Link Card
${L(R.linkCard)}

### ME- und RS Cloud Bridge
${L(R.meBridge)}
${L(R.rsBridge)}

## Mainframe aufstellen {#mainframe}

1. Stelle den **Cloud Mainframe** auf (er ist zwei Blöcke hoch). Wer ihn platziert, ist sein **Besitzer**.
2. Rechtsklick darauf: Der Bildschirm zeigt Status, Dashboard-Adresse (URL) und einen Kopplungscode.
3. Schalte dort das Dashboard **ein** (oder Schleichen + Rechtsklick mit leerer Hand).

Ein Redstone-Signal an einer der beiden Hälften ist ein **Not-Aus**: Der Mainframe geht offline, bis das Signal weg ist.

## Strom (FE) {#power}

Der Mainframe läuft mit **FE** (Forge Energy, auch RF genannt) – der Einheit von Powah, Ender IO, Thermal, AE2 und den meisten Technik-Mods. Schließe ein beliebiges FE-Kabel oder einen Generator an eine der beiden Hälften an.

| | Standard |
|---|---|
| Grundverbrauch | 40 FE/t |
| Pro verbundenem Uplink | +5 FE/t |
| Pro verbundenem Dashboard | +2 FE/t |
| Puffer | 200.000 FE |
| Max. Eingang | 2.000 FE/t |

Ohne Strom geht der Mainframe offline (nach 5 Sekunden mit leerem Puffer) und das Dashboard zeigt **„Mainframe has no power“**. Sobald der Puffer wieder voll genug ist, verbindet er sich von selbst. Uplinks und Bridges brauchen kein FE. Server-Admins können die Strompflicht abschalten (\`requirePower = false\`).

## Uplinks und Bridges verbinden {#linking}

1. Setze einen **Cloud Uplink** an eine Seite eines Computers (oder auf eine ME / RS Cloud Bridge).
2. Rechtsklick mit der **Cloud Link Card** auf den **Mainframe** – auf der Karte steht jetzt „Linked to Mainframe @ x y z“.
3. Rechtsklick mit der Karte auf den **Uplink** (oder die Bridge). Fertig – die Statusleuchte wird grün.

Schleichen + Rechtsklick in die Luft löscht die Karte. Nur Besitzer und vertraute Spieler dürfen verbinden.

## Handy koppeln {#pairing}

1. Rechtsklick auf den Mainframe und den **Kopplungscode** merken (8 Zeichen, 10 Minuten gültig, einmal verwendbar).
2. Öffne die Dashboard-URL aus dem Mainframe-Bildschirm im Browser (z. B. \`http://192.168.1.20:8787\`). Im selben WLAN klappt das sofort.
3. Code und einen Gerätenamen eingeben, **Pair device** tippen.
4. Klicke in Minecraft im Chat (oder im Mainframe-Bildschirm) auf **[Allow]**. Der Browser macht automatisch weiter.

> **Tipp:** Auf dem Handy mit „Zum Home-Bildschirm“ wie eine App installieren. Ein Browser kann mehrere Mainframes speichern.

Wie es weitergeht: [das Dashboard](/docs/dashboard). Eigene Widgets? Starte mit den [CC-Grundlagen](/docs/cc-basics) und der [Lua-Referenz](/docs/lua).
`,"cc-basics":`# CC-Grundlagen für Einsteiger

Neu bei ComputerCraft? Hier steht das Wenige, das du für ein Dashboard-Programm brauchst.

## Shell oder Lua? {#shell-vs-lua}

Ein Computer startet in der **Shell** – einer Kommandozeile wie im Terminal. Die Shell startet **Programme** über ihren Namen (\`edit\`, \`ls\`, \`cloud-demo\`). Lua-Code versteht sie **nicht**.

> **Achtung:** Tippst du Lua direkt in die Shell, z. B. \`cloud.setTitle("x")\`, kommt **„No such program“**. Die Shell sucht dann ein Programm namens \`cloud.setTitle("x")\`. Schreib Lua-Code in eine Datei (siehe unten) oder nutze die \`lua\`-Eingabe.

## Ein Programm mit edit schreiben {#edit}

${I(``,T)}

1. \`edit startup.lua\` eingeben und Enter drücken. Ein Editor öffnet sich.
2. Code eintippen oder einfügen. (Aus dem Dashboard: mit **Copy** kopieren, dann in Minecraft Strg+V.)
3. **Strg** drücken, mit den Pfeiltasten **Save** wählen und Enter, dann **Strg** → **Exit**.

Eine Datei namens \`startup.lua\` startet automatisch, wenn der Computer hochfährt – ideal für Dashboards.

## Ein Programm starten {#run}

Gib seinen Namen ohne \`.lua\` ein:

${I(``,E)}

Ein laufendes Programm stoppst du mit **Strg+T** (eine Sekunde gedrückt halten). Neustart mit **Strg+R**.

## Dateien auf den Computer ziehen {#drag-files}

Schreib den Code am PC in einem beliebigen Editor, speichere ihn als \`startup.lua\` und **zieh die Datei auf den geöffneten Computerbildschirm** in Minecraft. CC: Tweaked lädt sie in den aktuellen Ordner hoch.

## Schnell testen mit der lua-Eingabe {#lua-prompt}

\`lua\` öffnet eine interaktive Lua-Eingabe – praktisch, um einzelne Zeilen auszuprobieren:

${I(``,D)}

## Nützliche Befehle {#commands}

| Befehl | Macht |
|---|---|
| \`ls\` | Dateien auflisten |
| \`edit <datei>\` | Datei bearbeiten oder anlegen |
| \`rm <datei>\` | Datei löschen |
| \`help cloud\` | Kurzreferenz zu CC: Cloud |
| \`cloud-demo\` | zeigt alle Dashboard-Widgets |
| \`cloud-storage\` | eigenes Lager-Dashboard für eine ME / RS Bridge |
| \`peripheral\` | in Lua: \`peripheral.getNames()\` listet angeschlossene Blöcke |

Weiter: [Lua-Referenz](/docs/lua) oder die [cloud.ui-Bibliothek](/docs/ui-library).
`,dashboard:`# Das Dashboard

Öffne die URL, die der Mainframe anzeigt. Ein Browser kann mehrere Mainframes speichern; tippe einen an, um sein Dashboard zu öffnen.

## Karten und Widgets {#widgets}

Jeder Uplink wird zu einer **Karte**. Ein Computer ohne eigenes Programm zeigt die automatische **Computer-Karte**: Strom, Turn on / Reboot / Shut down und seine sechs Redstone-Seiten (Seite antippen schaltet den Ausgang, die LED zeigt den Eingang; analoge Stärken unter „Advanced“).

| Widget | Zeigt / macht |
|---|---|
| gauge | Wert auf einem Bogen mit Warn-/Kritisch-Farben |
| chart | Linie oder Sparkline der letzten Werte |
| progress | Balken mit Wert / Maximum |
| indicator | LED an/aus |
| label | Text oder Schlüssel/Wert |
| button | sendet eine Aktion (optional mit Bestätigung und PIN) |
| toggle | Schalter (reagiert sofort, springt zurück, wenn der Computer nicht bestätigt) |
| slider | Zahl, wird beim Loslassen gesendet |
| select, buttons | eine Option wählen |
| number, text | Eingabefelder (Enter oder Set/Send) |
| item search, watchlist, jobs | Lager-Widgets der [Bridges](/docs/bridges) |

## Seiten und Layout-Editor {#edit-mode}

Programme können Widgets auf **Seiten** (Tabs: unten auf dem Handy, oben am PC) und in **Gruppen** legen. Spieler mit Steuerrechten sehen einen **Stift**:

- Karten und Widgets umsortieren (am PC ziehen, auf dem Handy ▲▼)
- Kartengröße ändern (rechten Rand ziehen, 1–4 Spalten) und Widget-Breite
- umbenennen, umfärben, Icon wählen, ausblenden
- Seiten anlegen, umbenennen, löschen, Karten zwischen Seiten verschieben

**Save** speichert das Layout auf dem Server – alle Geräte sehen es. Startet ein Skript neu, bleibt das Layout erhalten, weil die Widget-IDs gleich bleiben.

## Meldungen {#alerts}

Programme senden Meldungen mit \`cloud.alert(text, level)\`. Sie erscheinen als Einblendung und bleiben im Protokoll unter der **Glocke**. Info-Meldungen verschwinden von selbst, warn und crit bleiben, bis du sie schließt. Mit **Enable notifications** im Protokoll bekommst du für kritische Meldungen System-Benachrichtigungen, auch wenn der Tab im Hintergrund ist.

## PIN-geschützte Bedienelemente {#pin}

Widgets mit \`pin=true\` fragen vor dem Auslösen die Mainframe-PIN über ein Ziffernfeld ab. Der Besitzer legt sie mit \`/cccloud pin set <4-8 Ziffern>\` fest. Nach 5 falschen PINs ist das Gerät 5 Minuten gesperrt und der Besitzer wird im Spiel benachrichtigt.

## Zustände {#states}

| Anzeige | Bedeutung |
|---|---|
| Dashboard disabled | das Dashboard ist im Mainframe-Bildschirm ausgeschaltet |
| Offline | der Chunk des Mainframes ist nicht geladen, der Block wurde abgebaut oder der Redstone-Not-Aus ist aktiv |
| Mainframe has no power | der FE-Puffer ist leer – siehe [Strom](/docs/start#power) |
| Uplink offline | dieser Uplink ist nicht geladen oder außer Reichweite |
| Read-only device | dieses Gerät darf zusehen, aber nicht steuern |
| Server not reachable | siehe [Fehlerbehebung](/docs/faq#not-reachable) |
`,lua:`# Lua-Referenz

Alles, was ein Programm mit CC: Cloud tun kann. Im Spiel zeigt \`help cloud\` eine Kurzfassung.

## Das Peripheral holen {#peripheral}

${I(`lua`,`local cloud = peripheral.find("cloud_uplink")`)}

Der Uplink muss den Computer berühren oder im Netzwerk seines Wired Modems hängen. Mehrere Uplinks? Nimm \`peripheral.wrap("cloud_uplink_0")\`.

## Ein erstes Programm {#first-program}

${I(`lua`,g)}

## Widgets {#widgets}

Jedes Widget bekommt eine **ID** (1–32 Zeichen: a–z, 0–9, _), eine Beschriftung und optional eine Optionstabelle. Ein erneuter Aufruf mit derselben ID aktualisiert es.

| Methode | Optionen |
|---|---|
| \`cloud.setTitle(text)\` | Kartentitel (max. 48 Zeichen) |
| \`cloud.button(id, label, opts)\` | color, confirm, pin |
| \`cloud.toggle(id, label, opts)\` | value, confirm, pin |
| \`cloud.slider(id, label, opts)\` | min, max, step, value, unit, pin |
| \`cloud.gauge(id, label, opts)\` | min, max, unit, warn, crit, value |
| \`cloud.label(id, text)\` | – |
| \`cloud.chart(id, label, opts)\` | min, max, unit, points (60–300), interval, style = "line" \\| "sparkline" |
| \`cloud.progress(id, label, opts)\` | min, max, unit, value |
| \`cloud.indicator(id, label, opts)\` | value, onColor, offColor |
| \`cloud.select(id, label, opts)\` | options = {"low","mid","high"}, value |
| \`cloud.number(id, label, opts)\` | min, max, step, value, unit |
| \`cloud.text(id, label, opts)\` | maxLength (≤ 256), value, placeholder |
| \`cloud.buttons(id, label, opts)\` | options = {"A","B","C"} |
| \`cloud.itemSearch(id, label, opts)\` | bridge = Peripheral-Name, max |
| \`cloud.watchlist(id, label, opts)\` | bridge |
| \`cloud.jobs(id, label, opts)\` | bridge |

Farben: cyan, green, yellow, red, gray. Eine Gauge gilt als „wenig ist schlecht“, wenn crit < warn (z. B. Kühlmittel).

## Werte {#values}

| Methode | Macht |
|---|---|
| \`cloud.set(id, value)\` | Gauge, Label, Toggle, Chart (hängt einen Punkt an) … aktualisieren |
| \`cloud.setMany({id=value, …})\` | mehrere Werte auf einmal |
| \`cloud.remove(id)\` | ein Widget entfernen |
| \`cloud.clear()\` | alles entfernen (die automatische Computer-Karte kommt zurück) |

${I(`lua`,y)}

## Ereignisse {#events}

Bedient jemand ein Widget, bekommt jeder Computer am Uplink ein \`cloud_action\`-Ereignis:

${I(`lua`,_)}

| Widget | value |
|---|---|
| button | \`true\` |
| toggle | \`true\` / \`false\` |
| slider, number | Zahl |
| select, buttons | die gewählte Option (String) |
| text | String (reiner Text) |
| itemSearch-Craft | Tabelle {op="craft", name, count, jobId} |

\`user\` ist der Gerätename, \`side\` der Peripheral-Name des Uplinks. Pro Computer warten höchstens 64 Aktionen in der Warteschlange.

## Layout: Seiten und Gruppen {#layout}

${I(`lua`,v)}

- \`cloud.page(id, title, {icon=})\` – ein Tab. Mit Seiten darf ein Uplink 64 statt 32 Widgets haben.
- \`cloud.group(id, title, {page=, cols=1..4})\` – eine Karte; Widgets kommen mit \`group=\` hinein.
- Jedes Widget akzeptiert außerdem \`page\`, \`group\`, \`span\` (1–4), \`order\` (0–10000), \`color\` und \`icon\`.
- Icons: home, bolt, flame, drop, box, fan, lock, warning, gear, chart, star, leaf, heart, cpu, shield, pick.

## Meldungen und Status {#alerts}

| Methode | Macht |
|---|---|
| \`cloud.alert(text, level)\` | Einblendung + Protokoll; level "info", "warn" oder "crit"; max. 1 pro Sekunde |
| \`cloud.notify(text, level)\` | wie alert |
| \`cloud.isOnline()\` | true, wenn der Mainframe online und das Dashboard an ist |
| \`cloud.getClients()\` | Anzahl verbundener Dashboards |
| \`cloud.getLastClientSeen()\` | Sekunden seit zuletzt ein Dashboard verbunden war, oder nil |

## cloud_mainframe {#mainframe}

Ein Computer neben dem Mainframe kann ihn auslesen:

| Methode | Liefert |
|---|---|
| \`isEnabled()\` | Dashboard eingeschaltet |
| \`isOnline()\` | geladen, mit Strom, kein Redstone-Not-Aus und eingeschaltet |
| \`setEnabled(bool)\` | Dashboard ein-/ausschalten |
| \`getUplinks()\` | Liste von {id, title, online, loaded, x, y, z, dimension} |
| \`getEnergy()\`, \`getEnergyCapacity()\`, \`getEnergyUsage()\` | gespeicherte FE, Puffergröße, FE/t |

${I(`lua`,b)}

Der Kopplungscode ist aus Lua nie lesbar.

## Bridges {#bridges}

Peripheral-Typen \`me_cloud_bridge\` und \`rs_cloud_bridge\`, gleiche Methoden:

| Methode | Liefert |
|---|---|
| \`isConnected()\` | mit einem Netzwerk mit Strom verbunden |
| \`getEnergy()\`, \`getMaxEnergy()\`, \`getEnergyUsage()\` | Netzwerk-Energie |
| \`getStorage()\` | {itemsUsed, itemsTotal, typesUsed, fluidsUsed, fluidsTotal} |
| \`listItems([filter], [limit=256])\` | {name, displayName, count, craftable}… |
| \`findItems(query, [limit=50])\` | dasselbe, passend zu ID oder Name |
| \`getItem(name)\` | ein Item oder nil |
| \`isCraftable(name)\` | boolean |
| \`craft(name, count)\` | {ok, jobId} oder {ok=false, error} |
| \`getCraftingJobs()\` | laufende Aufträge |
| \`cancelCraft(jobId)\` | wo unterstützt |
| \`listFluids([limit])\` | Flüssigkeiten |

${I(`lua`,x)}

## Grenzen {#limits}

- IDs: 1–32 Zeichen a–z, 0–9, _
- 32 Widgets pro Uplink, 64 mit Seiten
- Titel und Beschriftungen 48 Zeichen, Textwerte 256, Meldungstext 200
- Meldungen: 1 pro Sekunde und Uplink
`,"ui-library":`# cloud.ui-Bibliothek

\`cloud.ui\` liegt im ROM jedes Computers. Sie verpackt den Uplink in einen Baukasten und übernimmt die Ereignisschleife: eine Funktion pro Widget, Handler statt \`os.pullEvent\`, Timer mit \`every\`.

## Schnellstart {#quick-start}

${I(`lua`,ce)}

## API {#api}

| Funktion | Macht |
|---|---|
| \`ui.connect([uplink])\` | findet den Uplink (oder nimmt einen Namen / ein Peripheral), liefert \`dash\` |
| \`dash:title(text)\` | Kartentitel |
| \`dash:page(id, title, opts)\`, \`dash:group(id, title, opts)\` | Layout |
| \`dash:gauge(id, label, opts)\` … | jeder Widget-Typ: button, toggle, slider, gauge, chart, progress, indicator, select, number, text, buttons, label, itemSearch, watchlist, jobs |
| \`(…, opts, handler)\` | optionales letztes Argument: \`function(user, value)\`, wird bei Bedienung aufgerufen |
| \`widget:set(value)\` | aktualisieren; \`widget:remove()\`, \`widget:on(handler)\` |
| \`dash:set(id, value)\`, \`dash:setMany(t)\` | über die ID aktualisieren |
| \`dash:on(id, handler)\` | Handler für ein Widget |
| \`dash:every(sekunden, fn)\` | Timer (erster Aufruf sofort) |
| \`dash:onEvent(event, fn)\` | jedes andere CC-Ereignis (redstone, timer, …) |
| \`dash:notify(text, level)\` | Meldung |
| \`dash:clients()\`, \`dash:isOnline()\` | Status |
| \`dash:clear()\`, \`dash:run()\`, \`dash:stop()\` | Ablauf |

Fehler in Handlern werden ausgegeben und stoppen die Schleife nicht. Strg+T beendet sie.

## Beispiel: Baumfarm {#example-farm}

${I(`lua`,C)}

## Beispiel: Reaktor-Panel {#example-reactor}

Peripheral-Name und Methoden an deinen Reaktor-Mod anpassen.

${I(`lua`,w)}

## Mitgelieferte Programme {#programs}

- \`cloud-demo\` – alle Widgets auf zwei Seiten
- \`cloud-storage [bridge] [uplink]\` – ein eigenes Lager-Dashboard für eine ME / RS Bridge
`,bridges:`# Bridges: AE2 und Refined Storage

Die **ME Cloud Bridge** (Applied Energistics 2) und die **RS Cloud Bridge** (Refined Storage) verbinden ein Lagernetzwerk mit dem Dashboard. Es gibt sie nur, wenn der jeweilige Mod installiert ist.

## Einrichten {#setup}

1. Bridge craften ([Rezept](/docs/start#recipes)) und wie jedes andere Gerät **ins Netzwerk** setzen (ME: belegt einen Kanal; RS: ein Netzwerkknoten).
2. Einen **Cloud Uplink direkt auf die Bridge** setzen und den Uplink mit der Link Card verbinden.
3. Das Dashboard zeigt eine fertige **Storage**-Karte – ohne Computer, ohne Code.

Die Bridge nutzt den Strom des Netzwerks, kein FE vom Mainframe.

## Die Lager-Karte {#storage-card}

- Anzeigen: belegter Speicher, Energie
- Werte: Items gesamt, Item-Typen, Energieverbrauch
- **Suche**: Name oder ID eintippen, Anzahl, „craftable“-Abzeichen und Mod jedes Items sehen
- **Merkliste** und **Crafting-Aufträge** (siehe unten)

## Craften vom Handy {#crafting}

Ein craftbares Item antippen → **1 / 16 / 64 / eigene Menge** wählen → **Craft**. Der Auftrag erscheint unter den Crafting-Aufträgen mit Fortschrittsbalken. Fehler werden erklärt: kein Muster, alle CPUs belegt, fehlende Zutaten, Menge zu groß (Grenze \`maxCraftAmount\`, Standard 4096). Craften dürfen nur Geräte mit Steuerrechten.

## Merkliste {#watchlist}

Füge Items mit einer Mindestmenge hinzu (aus einem Suchergebnis oder per ID). Items unter ihrem Minimum stehen rot ganz oben und lösen pro Unterschreitung einmal eine **warn**-Meldung aus. Minimum ändern oder Items entfernen geht jederzeit. Die Merkliste wird in der Bridge gespeichert.

## Aus Lua {#lua}

${I(`lua`,x)}

Eine eigene Lager-Karte mit denselben Widgets:

${I(`lua`,S)}

Oder einfach \`cloud-storage\` starten. Alle Methoden: [Lua-Referenz → Bridges](/docs/lua#bridges).
`,security:`# Sicherheit und Hosting

Über das Dashboard lässt sich deine Welt steuern – deshalb ist es wie ein echter Webdienst geschützt.

## Standard: nur LAN {#lan-only}

Ab Werk können nur Geräte im lokalen Netzwerk des Servers (und der Host selbst) das Dashboard öffnen. Alle anderen bekommen „LAN only“.

${I(`toml`,O)}

Die Konfigurationsdatei ist \`<welt>/serverconfig/cccloud-server.toml\`; Änderungen an \`[web]\` brauchen einen Server-Neustart.

## Freigaben und Geräte {#devices}

- Ein Kopplungscode ist 10 Minuten gültig und funktioniert einmal.
- Nach dem Code muss der Spieler, der ihn geöffnet hat, im Spiel auf **[Allow]** klicken (innerhalb von 2 Minuten).
- Jedes Gerät gehört zu diesem Spieler. Verliert er den Zugriff (nicht mehr vertraut, kein OP mehr, gebannt), funktioniert das Gerät sofort nicht mehr.
- Geräte, die 30 Tage nicht benutzt wurden, verfallen.
- Geräte auflisten und entfernen: im Mainframe-Bildschirm oder per Befehl.

## Besitzer, vertraute Spieler, OPs {#trust}

Wer den Mainframe platziert hat, besitzt ihn. Besitzer, vertraute Spieler und OPs (Level 2+) sehen den Kopplungscode, geben Geräte frei, verbinden Uplinks und ändern Einstellungen. Alle anderen sehen nur den Status.

## Befehle {#commands}

Schau den Mainframe an und gib ein:

${I(``,F)}

## PIN {#pin}

Kritische Widgets (\`pin=true\`) brauchen eine 4- bis 8-stellige PIN. 5 Fehlversuche sperren das Gerät für 5 Minuten und benachrichtigen den Besitzer.

## Zugriff aus dem Internet {#internet}

> **Achtung:** Unverschlüsseltes HTTP über das Internet überträgt Zugangstoken und PIN im Klartext. Nutze HTTPS (Caddy, Cloudflare Tunnel oder TLS im Mod).

Setze \`webAllowInternet = true\` und wähle eine der folgenden Varianten. Läuft das Dashboard per HTTP über eine öffentliche Adresse, zeigt es eine Warnung.

### Caddy als Reverse Proxy (empfohlen) {#caddy}

${I(`toml`,k)}

${I(``,A)}

Caddy holt sich automatisch ein Zertifikat und leitet WebSockets weiter.

### Cloudflare Tunnel (kein offener Port) {#cloudflare}

Gleiche \`[web]\`-Einstellungen wie bei Caddy, dann:

${I(`bash`,j)}

${I(`yaml`,M)}

### TLS direkt im Mod {#tls}

${I(`toml`,N)}

Den Let's-Encrypt-Schlüssel vorher umwandeln: \`${P}\`. Lassen sich die Dateien nicht laden, startet das Dashboard nicht – es fällt nie heimlich auf HTTP zurück.

### Port direkt freigeben {#open-port}

Nur für schnelle Tests: \`webAllowInternet = true\`, \`publicUrl = "http://mc.example.com:8787"\` setzen und TCP 8787 im Panel deines Hosters freigeben.

## Was der Server protokolliert {#logs}

Jede Kopplung, Freigabe, Sperre und Dashboard-Aktion steht als \`[cccloud] …\` im Server-Log.
`,faq:`# Fehlerbehebung und FAQ

## „No such program“ {#no-such-program}

Du hast Lua-Code in die **Shell** getippt. Die Shell startet nur Programme. Schreib den Code mit \`edit startup.lua\` in eine Datei und starte \`startup\`, oder tippe vorher \`lua\`. Siehe [CC-Grundlagen](/docs/cc-basics#shell-vs-lua).

## „No widgets yet“ / leere Karte {#no-widgets}

- Der Uplink muss **an einem Computer** (oder einer Bridge) sitzen und mit der Link Card **verbunden** sein.
- Ein Computer ohne eigenes Programm zeigt die automatische Computer-Karte. Hat dein Programm alle Widgets entfernt (\`cloud.clear()\`), kommt die Karte nach Programmende zurück.
- Dein Programm muss wirklich laufen: Leg es in \`startup.lua\`, damit es nach Neustart oder Chunk-Laden startet.

## Die Bridge erscheint nicht {#bridge}

- Der Uplink muss **direkt auf der Bridge** sitzen, nicht an einem Kabel daneben.
- Die Bridge muss Teil eines Netzwerks mit Strom sein (ME: braucht einen Kanal – Controller und Kanalanzahl der Kabel prüfen; RS: der Controller braucht Energie).
- Verbinde mit der Link Card den **Uplink**, nicht die Bridge.

## Das Dashboard ist nicht erreichbar {#not-reachable}

- Nimm die URL aus dem Mainframe-Bildschirm. \`localhost\` funktioniert nur auf dem PC, auf dem Minecraft läuft.
- Handy und PC müssen im selben Netzwerk sein (WLAN, nicht mobile Daten) – oder siehe [Zugriff aus dem Internet](/docs/security#internet).
- Erlaube Java in der PC-Firewall für private Netzwerke.
- Port 8787 muss frei sein; sonst steht es im Server-Log und ein Admin kann \`webPort\` ändern.
- Auf einem gemieteten Server muss der Port freigegeben sein – oder Reverse Proxy / Tunnel nutzen.

## „Mainframe has no power“ {#no-power}

Der FE-Puffer ist leer. Schließe einen Generator oder ein FE-Kabel an den Mainframe an; er verbindet sich von selbst, sobald der Puffer wieder voll genug ist. Siehe [Strom](/docs/start#power).

## „Access revoked“ {#revoked}

Das Gerät wurde entfernt (im Mainframe-Bildschirm oder mit \`/cccloud revoke\`), ist nach 30 Tagen verfallen, oder der Spieler, der es freigegeben hat, hat keinen Zugriff mehr. Mit einem neuen Code erneut koppeln.

## „LAN only“ {#lan-only}

Der Server erlaubt nur lokale Adressen. Ein Admin kann \`webAllowInternet\` einschalten – am besten hinter HTTPS. Siehe [Sicherheit](/docs/security#internet).

## Bedienelemente sind ausgegraut {#locked}

Das Dashboard ist ausgeschaltet, der Mainframe offline oder ohne Strom, der Uplink offline, oder dein Gerät ist schreibgeschützt. Das Banner oben sagt, was zutrifft.

## Falsche PIN / gesperrt {#pin-locked}

Nach 5 falschen PINs ist das Gerät 5 Minuten gesperrt. Der Besitzer kann die PIN mit \`/cccloud pin set\` ändern.
`},{code:z,recipe:B,RECIPES:V}=se,pe={start:`# Getting started

CC: Cloud puts your ComputerCraft computers and storage on a dashboard in your phone or desktop browser. The Minecraft server hosts the dashboard itself – there is no account and no external service.

> **Tip:** The quickest path: craft a Cloud Mainframe, power it, put a Cloud Uplink on a computer, link the two with a Cloud Link Card and pair your phone. The computer appears on the dashboard automatically – no code needed.

## The blocks {#blocks}

| Block / item | What it does |
|---|---|
| **Cloud Mainframe** | The heart of a base. Two blocks tall. Hosts the connection to the dashboard, shows the pairing code and manages devices. One per base or team. Needs FE power. |
| **Cloud Uplink** | Sticks to a block face like a modem. On a computer it becomes that computer's card on the dashboard. Range 128 blocks, same dimension. |
| **Advanced Cloud Uplink** | Same, but unlimited range and works across dimensions. |
| **Cloud Link Card** | Links uplinks and bridges to a mainframe. |
| **ME / RS Cloud Bridge** | Joins an Applied Energistics 2 or Refined Storage network. With an uplink on it you get a ready-made storage card (search, craft, watchlist). Only exists when AE2 / RS is installed. |
| **Cloud Core** | Crafting ingredient. |

## Crafting recipes {#recipes}

Ingredients shown as "(any)" accept every variant from the tag (e.g. any gold ingot). JEI/EMI show the same recipes in-game.

### Cloud Core
${B(V.core)}

### Cloud Mainframe
${B(V.mainframe)}

### Cloud Uplink
${B(V.uplink)}

### Advanced Cloud Uplink
${B(V.advancedUplink)}

### Cloud Link Card
${B(V.linkCard)}

### ME and RS Cloud Bridge
${B(V.meBridge)}
${B(V.rsBridge)}

## Place the mainframe {#mainframe}

1. Place the **Cloud Mainframe** (it takes two blocks of height). Whoever places it is its **owner**.
2. Right-click it: the screen shows the status, the dashboard address (URL) and a pairing code.
3. Turn the dashboard **on** in that screen (or sneak + right-click with an empty hand).

A redstone signal on either half is a **kill switch**: the mainframe goes offline until the signal is gone.

## Power (FE) {#power}

The mainframe runs on **FE** (Forge Energy, also called RF) – the unit used by Powah, Ender IO, Mekanism's FE output, Thermal, AE2 and most tech mods. Connect any FE cable or generator to either half.

| | Default |
|---|---|
| Base consumption | 40 FE/t |
| Per linked uplink | +5 FE/t |
| Per connected dashboard | +2 FE/t |
| Buffer | 200,000 FE |
| Max input | 2,000 FE/t |

Without power the mainframe goes offline (after 5 seconds on an empty buffer) and the dashboard says **"Mainframe has no power"**. It reconnects by itself once the buffer refills. Uplinks and bridges need no FE. Server admins can turn the requirement off (\`requirePower = false\`).

## Link uplinks and bridges {#linking}

1. Place a **Cloud Uplink** on a side of a computer (or on an ME / RS Cloud Bridge).
2. Right-click the **mainframe** with a **Cloud Link Card** – the card now says "Linked to Mainframe @ x y z".
3. Right-click the **uplink** (or bridge) with the card. Done – its status light turns green.

Sneak + right-click into the air clears the card. Only the owner and trusted players can link.

## Pair your phone {#pairing}

1. Right-click the mainframe and note the **pairing code** (8 characters, valid 10 minutes, single use).
2. Open the dashboard URL from the mainframe screen in your browser (e.g. \`http://192.168.1.20:8787\`). On the same Wi-Fi this just works.
3. Enter the code and a device name, tap **Pair device**.
4. In Minecraft, click **[Allow]** in the chat (or in the mainframe screen). The browser continues by itself.

> **Tip:** On a phone, use "Add to Home Screen" to install the dashboard like an app. A browser can hold several mainframes.

What you see next: [the dashboard](/docs/dashboard). Want your own widgets? Start with [CC basics](/docs/cc-basics) and the [Lua reference](/docs/lua).
`,"cc-basics":`# CC basics for beginners

New to ComputerCraft? This page explains the few things you need to run a dashboard program.

## Shell vs. Lua {#shell-vs-lua}

A computer starts in the **shell** – a command line like a terminal. The shell runs **programs** by name (\`edit\`, \`ls\`, \`cloud-demo\`). It does **not** understand Lua code.

> **Warning:** If you type Lua directly into the shell, e.g. \`cloud.setTitle("x")\`, you get **"No such program"**. The shell looks for a program called \`cloud.setTitle("x")\`. Put Lua code into a file (below) or use the \`lua\` prompt.

## Write a program with edit {#edit}

${z(``,T)}

1. Type \`edit startup.lua\` and press Enter. An editor opens.
2. Type or paste your code. (Pasting from the dashboard: copy with the **Copy** button, then Ctrl+V in Minecraft.)
3. Press **Ctrl**, choose **Save** with the arrow keys and Enter, then **Ctrl** → **Exit**.

A file named \`startup.lua\` runs automatically whenever the computer boots – perfect for dashboards.

## Run a program {#run}

Type its name without \`.lua\`:

${z(``,E)}

Stop a running program with **Ctrl+T** (hold it for a second). Reboot with **Ctrl+R**.

## Drag files onto the computer {#drag-files}

Write the code on your PC in any editor, save it as \`startup.lua\` and **drag the file onto the open computer screen** in Minecraft. CC: Tweaked uploads it into the current folder.

## Quick tests with the lua prompt {#lua-prompt}

\`lua\` opens an interactive Lua prompt – handy to try one line at a time:

${z(``,D)}

## Useful commands {#commands}

| Command | Does |
|---|---|
| \`ls\` | list files |
| \`edit <file>\` | edit or create a file |
| \`rm <file>\` | delete a file |
| \`help cloud\` | CC: Cloud quick reference |
| \`cloud-demo\` | shows every dashboard widget |
| \`cloud-storage\` | custom storage dashboard for an ME / RS bridge |
| \`peripheral\` | in Lua: \`peripheral.getNames()\` lists attached blocks |

Next: [the Lua reference](/docs/lua) or the [cloud.ui helper](/docs/ui-library).
`,dashboard:`# The dashboard

Open the URL shown by the mainframe. One browser can hold several mainframes; tap one to open its dashboard.

## Cards and widgets {#widgets}

Every uplink becomes a **card**. A computer without its own program shows the automatic **Computer card**: power, Turn on / Reboot / Shut down and its six redstone sides (tap a side to switch its output, the LED shows the input; analog levels under "Advanced").

| Widget | Shows / does |
|---|---|
| gauge | value on an arc with warn/crit colours |
| chart | line or sparkline of recent values |
| progress | bar with value / max |
| indicator | LED on/off |
| label | text or key/value |
| button | sends an action (optional confirm and PIN) |
| toggle | on/off switch (updates instantly, rolls back if the computer does not confirm) |
| slider | number, sent when you let go |
| select, buttons | pick one option |
| number, text | input fields (Enter or Set/Send) |
| item search, watchlist, jobs | storage widgets of the [bridges](/docs/bridges) |

## Pages and the layout editor {#edit-mode}

Programs can put widgets on **pages** (tabs: bottom on the phone, top on desktop) and in **groups**. Players with control rights see a **pencil**:

- reorder cards and widgets (drag on desktop, ▲▼ on the phone)
- resize cards (drag the right edge, 1–4 columns) and widgets
- rename, recolour, give an icon, hide
- create, rename and delete pages, move cards between them

**Save** stores the layout on the server – all devices see it. A script restart keeps the layout because widget ids stay the same.

## Alerts {#alerts}

Programs send alerts with \`cloud.alert(text, level)\`. They pop up as toasts and are kept in the **bell** log. Info toasts fade; warn and crit stay until dismissed. Tap **Enable notifications** in the alert log to get system notifications for crit alerts while the tab is in the background.

## PIN-protected controls {#pin}

Widgets with \`pin=true\` ask for the mainframe PIN on a keypad before they act. The owner sets it with \`/cccloud pin set <4-8 digits>\`. After 5 wrong PINs the device is locked for 5 minutes and the owner is notified in-game.

## States you may see {#states}

| Message | Meaning |
|---|---|
| Dashboard disabled | the dashboard is switched off in the mainframe screen |
| Offline | the mainframe chunk is unloaded, the block was broken or the redstone kill switch is on |
| Mainframe has no power | the FE buffer is empty – see [power](/docs/start#power) |
| Uplink offline | that uplink is unloaded or out of range |
| Read-only device | this device may watch but not control |
| Server not reachable | see [troubleshooting](/docs/faq#not-reachable) |
`,lua:`# Lua reference

Everything a program can do with CC: Cloud. In-game, \`help cloud\` prints a short version.

## Getting the peripheral {#peripheral}

${z(`lua`,`local cloud = peripheral.find("cloud_uplink")`)}

The uplink must touch the computer or sit on its wired modem network. Several uplinks? Use \`peripheral.wrap("cloud_uplink_0")\`.

## A first program {#first-program}

${z(`lua`,g)}

## Widgets {#widgets}

Every widget takes an **id** (1–32 characters: a–z, 0–9, _), a label and an optional options table. Calling it again with the same id updates it.

| Method | Options |
|---|---|
| \`cloud.setTitle(text)\` | card title (max 48 characters) |
| \`cloud.button(id, label, opts)\` | color, confirm, pin |
| \`cloud.toggle(id, label, opts)\` | value, confirm, pin |
| \`cloud.slider(id, label, opts)\` | min, max, step, value, unit, pin |
| \`cloud.gauge(id, label, opts)\` | min, max, unit, warn, crit, value |
| \`cloud.label(id, text)\` | – |
| \`cloud.chart(id, label, opts)\` | min, max, unit, points (60–300), interval, style = "line" \\| "sparkline" |
| \`cloud.progress(id, label, opts)\` | min, max, unit, value |
| \`cloud.indicator(id, label, opts)\` | value, onColor, offColor |
| \`cloud.select(id, label, opts)\` | options = {"low","mid","high"}, value |
| \`cloud.number(id, label, opts)\` | min, max, step, value, unit |
| \`cloud.text(id, label, opts)\` | maxLength (≤ 256), value, placeholder |
| \`cloud.buttons(id, label, opts)\` | options = {"A","B","C"} |
| \`cloud.itemSearch(id, label, opts)\` | bridge = peripheral name, max |
| \`cloud.watchlist(id, label, opts)\` | bridge |
| \`cloud.jobs(id, label, opts)\` | bridge |

Colours: cyan, green, yellow, red, gray. A gauge is "low is bad" when crit < warn (e.g. coolant).

## Values {#values}

| Method | Does |
|---|---|
| \`cloud.set(id, value)\` | update a gauge, label, toggle, chart (appends a point), … |
| \`cloud.setMany({id=value, …})\` | several values in one go |
| \`cloud.remove(id)\` | remove a widget |
| \`cloud.clear()\` | remove everything (the automatic Computer card returns) |

${z(`lua`,y)}

## Events {#events}

When someone uses a widget, every computer attached to the uplink gets a \`cloud_action\` event:

${z(`lua`,_)}

| Widget | value |
|---|---|
| button | \`true\` |
| toggle | \`true\` / \`false\` |
| slider, number | number |
| select, buttons | the chosen option (string) |
| text | string (plain text) |
| itemSearch craft | table {op="craft", name, count, jobId} |

\`user\` is the device name, \`side\` the uplink's peripheral name. At most 64 actions wait in the queue per computer.

## Layout: pages and groups {#layout}

${z(`lua`,v)}

- \`cloud.page(id, title, {icon=})\` – a tab. With pages an uplink may hold 64 widgets instead of 32.
- \`cloud.group(id, title, {page=, cols=1..4})\` – a card; widgets join it with \`group=\`.
- Every widget also accepts \`page\`, \`group\`, \`span\` (1–4), \`order\` (0–10000), \`color\` and \`icon\`.
- Icons: home, bolt, flame, drop, box, fan, lock, warning, gear, chart, star, leaf, heart, cpu, shield, pick.

## Alerts and status {#alerts}

| Method | Does |
|---|---|
| \`cloud.alert(text, level)\` | toast + log; level "info", "warn" or "crit"; max 1 per second |
| \`cloud.notify(text, level)\` | same as alert |
| \`cloud.isOnline()\` | true if the mainframe is online and the dashboard on |
| \`cloud.getClients()\` | number of connected dashboards |
| \`cloud.getLastClientSeen()\` | seconds since a dashboard was connected, or nil |

## cloud_mainframe {#mainframe}

A computer next to the mainframe can read it:

| Method | Returns |
|---|---|
| \`isEnabled()\` | dashboard switched on |
| \`isOnline()\` | loaded, powered, not redstone-killed and enabled |
| \`setEnabled(bool)\` | switch the dashboard on/off |
| \`getUplinks()\` | list of {id, title, online, loaded, x, y, z, dimension} |
| \`getEnergy()\`, \`getEnergyCapacity()\`, \`getEnergyUsage()\` | FE stored, buffer size, FE/t |

${z(`lua`,b)}

The pairing code is never readable from Lua.

## Bridges {#bridges}

Peripheral types \`me_cloud_bridge\` and \`rs_cloud_bridge\`, same methods:

| Method | Returns |
|---|---|
| \`isConnected()\` | connected to a powered network |
| \`getEnergy()\`, \`getMaxEnergy()\`, \`getEnergyUsage()\` | network energy |
| \`getStorage()\` | {itemsUsed, itemsTotal, typesUsed, fluidsUsed, fluidsTotal} |
| \`listItems([filter], [limit=256])\` | {name, displayName, count, craftable}… |
| \`findItems(query, [limit=50])\` | same, matching id or name |
| \`getItem(name)\` | one item or nil |
| \`isCraftable(name)\` | boolean |
| \`craft(name, count)\` | {ok, jobId} or {ok=false, error} |
| \`getCraftingJobs()\` | running jobs |
| \`cancelCraft(jobId)\` | where supported |
| \`listFluids([limit])\` | fluids |

${z(`lua`,x)}

## Limits {#limits}

- ids: 1–32 characters a–z, 0–9, _
- 32 widgets per uplink, 64 with pages
- titles and labels 48 characters, text values 256, alert text 200
- alerts: 1 per second per uplink
`,"ui-library":`# cloud.ui helper library

\`cloud.ui\` ships in every computer's ROM. It wraps the uplink in a builder and runs the event loop for you: one function per widget, handlers instead of \`os.pullEvent\`, timers with \`every\`.

## Quick start {#quick-start}

${z(`lua`,ce)}

## API {#api}

| Function | Does |
|---|---|
| \`ui.connect([uplink])\` | finds the uplink (or takes a name / wrapped peripheral), returns \`dash\` |
| \`dash:title(text)\` | card title |
| \`dash:page(id, title, opts)\`, \`dash:group(id, title, opts)\` | layout |
| \`dash:gauge(id, label, opts)\` … | every widget type: button, toggle, slider, gauge, chart, progress, indicator, select, number, text, buttons, label, itemSearch, watchlist, jobs |
| \`(…, opts, handler)\` | optional last argument: \`function(user, value)\` called on use |
| \`widget:set(value)\` | update; \`widget:remove()\`, \`widget:on(handler)\` |
| \`dash:set(id, value)\`, \`dash:setMany(t)\` | update by id |
| \`dash:on(id, handler)\` | handler for a widget |
| \`dash:every(seconds, fn)\` | timer (first call right away) |
| \`dash:onEvent(event, fn)\` | any other CC event (redstone, timer, …) |
| \`dash:notify(text, level)\` | alert |
| \`dash:clients()\`, \`dash:isOnline()\` | status |
| \`dash:clear()\`, \`dash:run()\`, \`dash:stop()\` | lifecycle |

Errors inside handlers are printed and do not stop the loop. Ctrl+T ends it.

## Example: tree farm {#example-farm}

${z(`lua`,C)}

## Example: reactor panel {#example-reactor}

Adjust the peripheral name and methods to your reactor mod.

${z(`lua`,w)}

## Built-in programs {#programs}

- \`cloud-demo\` – every widget on two pages
- \`cloud-storage [bridge] [uplink]\` – a custom storage dashboard for an ME / RS bridge
`,bridges:`# Bridges: AE2 and Refined Storage

The **ME Cloud Bridge** (Applied Energistics 2) and **RS Cloud Bridge** (Refined Storage) connect a storage network to the dashboard. They only exist when the mod is installed.

## Set up {#setup}

1. Craft the bridge ([recipe](/docs/start#recipes)) and place it **on the network** like any other device (ME: uses one channel; RS: a network node).
2. Put a **Cloud Uplink directly on the bridge** and link the uplink with the Link Card.
3. The dashboard shows a ready-made **Storage** card – no computer, no code.

The bridge uses the network's own power, no FE for the mainframe.

## The storage card {#storage-card}

- gauges: storage used, energy
- labels: total items, item types, energy usage
- **search**: type a name or id, see counts, a "craftable" badge and the mod of each item
- **watchlist** and **crafting jobs** (below)

## Crafting from the phone {#crafting}

Tap a craftable item → pick **1 / 16 / 64 / custom** → **Craft**. The job appears under crafting jobs with a progress bar. Errors are explained: no pattern, all CPUs busy, missing ingredients, amount too large (limit \`maxCraftAmount\`, default 4096). Only devices with control rights can craft.

## Watchlist {#watchlist}

Add items with a minimum amount (from a search result or by id). Items below their minimum are listed first in red and trigger a **warn** alert once per crossing. Edit the minimum or remove items any time. The watchlist is stored in the bridge.

## From Lua {#lua}

${z(`lua`,x)}

Your own storage card with the same widgets:

${z(`lua`,S)}

Or just run \`cloud-storage\`. All methods: [Lua reference → Bridges](/docs/lua#bridges).
`,security:`# Security and hosting

The dashboard can control things in your world, so it is protected like a real web service.

## Default: LAN only {#lan-only}

Out of the box only devices in the server's local network (and the host itself) can open the dashboard. Everyone else gets "LAN only".

${z(`toml`,O)}

The config file is \`<world>/serverconfig/cccloud-server.toml\`; changes to \`[web]\` need a server restart.

## Approvals and devices {#devices}

- A pairing code is valid for 10 minutes and works once.
- After the code, the player who opened it must click **[Allow]** in-game (within 2 minutes).
- Every device belongs to that player. If the player loses access (untrusted, de-opped, banned), the device stops working immediately.
- Devices unused for 30 days expire.
- List and revoke devices in the mainframe screen or with commands.

## Owner, trusted players, ops {#trust}

The player who placed the mainframe owns it. Owner, trusted players and ops (level 2+) can see the pairing code, approve devices, link uplinks and change settings. Others see a read-only status.

## Commands {#commands}

Look at the mainframe and run:

${z(``,F)}

## PIN {#pin}

Critical widgets (\`pin=true\`) need a 4–8 digit PIN. 5 wrong tries lock that device for 5 minutes and notify the owner.

## Internet access {#internet}

> **Warning:** Plain HTTP over the internet sends your access token and PIN unencrypted. Use HTTPS (Caddy, Cloudflare Tunnel or TLS in the mod).

Set \`webAllowInternet = true\` and pick one of the setups below. The dashboard shows a warning when it runs over plain HTTP from a public address.

### Caddy reverse proxy (recommended) {#caddy}

${z(`toml`,k)}

${z(``,A)}

Caddy gets a certificate automatically and forwards WebSockets.

### Cloudflare Tunnel (no open port) {#cloudflare}

Same \`[web]\` settings as for Caddy, then:

${z(`bash`,j)}

${z(`yaml`,M)}

### TLS in the mod {#tls}

${z(`toml`,N)}

Convert the Let's Encrypt key first: \`${P}\`. If the files cannot be loaded the dashboard does not start – it never falls back to plain HTTP silently.

### Port opened directly {#open-port}

Only for quick tests: set \`webAllowInternet = true\`, \`publicUrl = "http://mc.example.com:8787"\` and open TCP 8787 in your host's panel.

## What the server logs {#logs}

Every pairing, approval, revoke and dashboard action appears as \`[cccloud] …\` in the server log.
`,faq:`# Troubleshooting and FAQ

## "No such program" {#no-such-program}

You typed Lua code into the **shell**. The shell only runs programs. Put the code into a file with \`edit startup.lua\` and run \`startup\`, or type \`lua\` first. See [CC basics](/docs/cc-basics#shell-vs-lua).

## "No widgets yet" / empty card {#no-widgets}

- The uplink must be **on a computer** (or a bridge) and **linked** with the Link Card.
- A computer without its own program shows the automatic Computer card. If your program cleared all widgets (\`cloud.clear()\`), the card comes back after the program ends.
- Your program must actually run: put it in \`startup.lua\` so it starts after a reboot or chunk load.

## The bridge does not show up {#bridge}

- The uplink must sit **directly on the bridge**, not on a cable next to it.
- The bridge must be part of a powered network (ME: it needs a channel – check your controller and cable channel count; RS: the controller must have energy).
- Link the **uplink**, not the bridge, with the Link Card.

## The dashboard is not reachable {#not-reachable}

- Use the URL from the mainframe screen. \`localhost\` only works on the PC that runs Minecraft.
- Phone and PC must be in the same network (Wi-Fi, not mobile data) – or see [internet access](/docs/security#internet).
- Allow Java through the PC firewall for private networks.
- Port 8787 must be free; if not, the server log says so and an admin can change \`webPort\`.
- On a hosted server the port must be opened, or use a reverse proxy / tunnel.

## "Mainframe has no power" {#no-power}

The FE buffer is empty. Connect a generator or FE cable to the mainframe; it reconnects by itself once the buffer refills. See [power](/docs/start#power).

## "Access revoked" {#revoked}

The device was removed (in the mainframe screen or with \`/cccloud revoke\`), it expired after 30 days, or the player who approved it lost access. Pair again with a new code.

## "LAN only" {#lan-only}

The server only allows local addresses. An admin can enable \`webAllowInternet\` – preferably behind HTTPS. See [security](/docs/security#internet).

## Controls are greyed out {#locked}

The dashboard is disabled, the mainframe is offline or without power, the uplink is offline, or your device is read-only. The banner at the top says which.

## Wrong PIN / locked {#pin-locked}

After 5 wrong PINs the device is locked for 5 minutes. The owner can change the PIN with \`/cccloud pin set\`.
`},H=[{id:`start`,icon:`home`,title:{en:`Getting started`,de:`Erste Schritte`}},{id:`cc-basics`,icon:`cpu`,title:{en:`CC basics`,de:`CC-Grundlagen`}},{id:`dashboard`,icon:`chart`,title:{en:`The dashboard`,de:`Das Dashboard`}},{id:`lua`,icon:`gear`,title:{en:`Lua reference`,de:`Lua-Referenz`}},{id:`ui-library`,icon:`star`,title:{en:`cloud.ui library`,de:`cloud.ui-Bibliothek`}},{id:`bridges`,icon:`box`,title:{en:`Bridges (AE2 / RS)`,de:`Bridges (AE2 / RS)`}},{id:`security`,icon:`shield`,title:{en:`Security & hosting`,de:`Sicherheit & Hosting`}},{id:`faq`,icon:`warning`,title:{en:`Troubleshooting`,de:`Fehlerbehebung`}}],me={de:fe,en:pe},U={en:{docs:`Docs`,search:`Search the docs…`,noResults:`Nothing found for`,results:`Results`,menu:`Menu`,back:`Dashboard`,onThisPage:`On this page`,prev:`Previous`,next:`Next`,copyLink:`Copy link to this section`,linkCopied:`Link copied`,language:`Language`,any:`any`,shapeless:`Shapeless – any arrangement`,result:`Result`},de:{docs:`Doku`,search:`Doku durchsuchen…`,noResults:`Nichts gefunden für`,results:`Treffer`,menu:`Menü`,back:`Dashboard`,onThisPage:`Auf dieser Seite`,prev:`Zurück`,next:`Weiter`,copyLink:`Link zu diesem Abschnitt kopieren`,linkCopied:`Link kopiert`,language:`Sprache`,any:`beliebig`,shapeless:`Formlos – beliebige Anordnung`,result:`Ergebnis`}},he={"#c:nuggets/gold":{en:`Gold Nugget`,de:`Goldklumpen`},"#c:ingots/gold":{en:`Gold Ingot`,de:`Goldbarren`},"#c:ender_pearls":{en:`Ender Pearl`,de:`Enderperle`},"#c:dusts/redstone":{en:`Redstone Dust`,de:`Redstonestaub`},"minecraft:prismarine_crystals":{en:`Prismarine Crystals`,de:`Prismarinkristalle`},"minecraft:stone":{en:`Stone`,de:`Stein`},"minecraft:paper":{en:`Paper`,de:`Papier`},"computercraft:computer_advanced":{en:`Advanced Computer`,de:`Advanced Computer`},"computercraft:disk_drive":{en:`Disk Drive`,de:`Disk Drive`},"computercraft:wired_modem":{en:`Wired Modem`,de:`Wired Modem`},"ae2:interface":{en:`ME Interface`,de:`ME Interface`},"refinedstorage:interface":{en:`RS Interface`,de:`RS Interface`},"cccloud:cloud_core":{en:`Cloud Core`,de:`Cloud Core`},"cccloud:cloud_mainframe":{en:`Cloud Mainframe`,de:`Cloud Mainframe`},"cccloud:cloud_uplink":{en:`Cloud Uplink`,de:`Cloud Uplink`},"cccloud:advanced_cloud_uplink":{en:`Advanced Cloud Uplink`,de:`Advanced Cloud Uplink`},"cccloud:cloud_link_card":{en:`Cloud Link Card`,de:`Cloud Link Card`},"cccloud:me_cloud_bridge":{en:`ME Cloud Bridge`,de:`ME Cloud Bridge`},"cccloud:rs_cloud_bridge":{en:`RS Cloud Bridge`,de:`RS Cloud Bridge`}};function W(e,t){let n=he[e]?.[t];return n?e.startsWith(`#`)?`${n} (${U[t].any})`:n:e.replace(/^#?[a-z0-9_.-]+:/,``).replace(/[_/]/g,` `)}function ge(e=typeof navigator>`u`?[]:navigator.languages??[navigator.language]){for(let t of e){let e=t.toLowerCase().slice(0,2);if(e===`de`)return`de`;if(e===`en`)return`en`}return`en`}function _e(e){return e.toLowerCase().normalize(`NFKD`).replace(/[̀-ͯ]/g,``).replace(/ß/g,`ss`).replace(/[^a-z0-9]+/g,`-`).replace(/^-+|-+$/g,``).slice(0,64)||`section`}function G(e){let t=[],n=``,r=()=>{n&&t.push({t:`text`,v:n}),n=``},i=0;for(;i<e.length;){let a=e[i];if(a===`\\`&&i+1<e.length)n+=e[i+1],i+=2;else{if(a==="`"){let n=e.indexOf("`",i+1);if(n>i){r(),t.push({t:`code`,v:e.slice(i+1,n)}),i=n+1;continue}}if(a===`*`&&e[i+1]===`*`){let n=e.indexOf(`**`,i+2);if(n>i+2){r(),t.push({t:`strong`,c:G(e.slice(i+2,n))}),i=n+2;continue}}if(a===`*`&&e[i+1]!==` `){let n=e.indexOf(`*`,i+1);if(n>i+1&&e[n-1]!==` `){r(),t.push({t:`em`,c:G(e.slice(i+1,n))}),i=n+1;continue}}if(a===`[`){let n=e.indexOf(`](`,i),a=n>0?e.indexOf(`)`,n+2):-1;if(n>i&&a>n){let o=e.slice(n+2,a).trim();if(ve(o)){r(),t.push({t:`link`,href:o,c:G(e.slice(i+1,n))}),i=a+1;continue}}}n+=a,i++}}return r(),t}function ve(e){return/^(\/docs(\/[a-z0-9-]*)?(#[a-z0-9-]+)?|#[a-z0-9-]+|https?:\/\/[^\s"'<>]+|\/|\/#\/[a-z0-9/-]*)$/i.test(e)}function K(e){return e.map(e=>e.t===`text`||e.t===`code`?e.v:K(e.c)).join(``)}function ye(e){let t=e.replace(/\r\n?/g,`
`).split(`
`),n=[],r=new Set,i=0,a=e=>{let t=e;for(let n=2;r.has(t);n++)t=`${e}-${n}`;return r.add(t),t};for(;i<t.length;){let e=t[i];if(!e.trim()){i++;continue}let r=/^```\s*([a-z0-9_-]*)\s*$/i.exec(e);if(r){let e=[];for(i++;i<t.length&&!/^```\s*$/.test(t[i]);)e.push(t[i++]);i++;let a=e.join(`
`);if(r[1]===`recipe`)try{n.push({t:`recipe`,recipe:JSON.parse(a)})}catch{n.push({t:`code`,lang:`json`,code:a})}else n.push({t:`code`,lang:r[1]||``,code:a});continue}let o=/^(#{1,3})\s+(.*?)(?:\s+\{#([a-z0-9-]+)\})?\s*$/.exec(e);if(o){let e=G(o[2]),t=K(e);n.push({t:`h`,level:o[1].length,id:a(o[3]??_e(t)),c:e,text:t}),i++;continue}if(/^\|.*\|\s*$/.test(e)){let e=[];for(;i<t.length&&/^\|.*\|\s*$/.test(t[i]);){let n=t[i].trim().slice(1,-1).split(/(?<!\\)\|/).map(e=>e.trim().replace(/\\\|/g,`|`));n.every(e=>/^:?-{2,}:?$/.test(e))||e.push(n),i++}let[r,...a]=e;n.push({t:`table`,head:(r??[]).map(G),rows:a.map(e=>e.map(G))});continue}if(/^>\s?/.test(e)){let e=[],r=[];for(;i<t.length&&/^>\s?/.test(t[i]);){let n=t[i].replace(/^>\s?/,``);n.trim()?r.push(n.trim()):(r.length&&e.push(r.join(` `)),r=[]),i++}r.length&&e.push(r.join(` `));let a=e[0]??``,o=/^\*\*(warning|achtung|warnung)/i.test(a)?`warn`:/^\*\*(tip|tipp)/i.test(a)?`tip`:`note`;n.push({t:`callout`,kind:o,c:e.map(G)});continue}let s=/^\s*[-*]\s+/,c=/^\s*\d+[.)]\s+/;if(s.test(e)||c.test(e)){let r=c.test(e),a=r?c:s,o=[];for(;i<t.length&&(a.test(t[i])||/^\s{2,}\S/.test(t[i])&&o.length);)a.test(t[i])?o.push(t[i].replace(a,``)):o[o.length-1]+=` `+t[i].trim(),i++;n.push({t:r?`ol`:`ul`,items:o.map(G)});continue}let l=[];for(;i<t.length&&t[i].trim()&&!/^(#{1,3}\s|```|>|\|)/.test(t[i])&&!s.test(t[i])&&!c.test(t[i]);)l.push(t[i++].trim());n.push({t:`p`,c:G(l.join(` `))})}return n}function be(e){switch(e.t){case`h`:return e.text;case`p`:return K(e.c);case`ul`:case`ol`:return e.items.map(K).join(` `);case`callout`:return e.c.map(K).join(` `);case`table`:return[...e.head,...e.rows.flat()].map(K).join(` `);case`code`:return e.code;case`recipe`:return[e.recipe.result,...Object.values(e.recipe.key??{}),...e.recipe.items??[]].join(` `)}}var xe=new Map;function q(e,t){let n=`${e}/${t}`,r=xe.get(n);return r||(r=ye(me[e][t]??``),xe.set(n,r)),r}var Se=new Map;function Ce(e){let t=Se.get(e);if(t)return t;t=[];for(let n of H){let r={page:n.id,pageTitle:n.title[e],anchor:``,heading:n.title[e],text:``};for(let i of q(e,n.id))i.t===`h`&&i.level>1?((r.text||r.anchor)&&t.push(r),r={page:n.id,pageTitle:n.title[e],anchor:i.id,heading:i.text,text:``}):i.t!==`h`&&(r.text+=` `+be(i));t.push(r)}return Se.set(e,t),t}var J=e=>e.toLowerCase().normalize(`NFKD`).replace(/[̀-ͯ]/g,``);function we(e,t,n=20){let r=J(t).split(/\s+/).filter(e=>e.length>=2);if(!r.length)return[];let i=[];for(let t of Ce(e)){let e=J(t.heading),n=J(t.text),a=0,o=!0;for(let t of r){let r=e.includes(t),i=n.includes(t);if(!r&&!i){o=!1;break}a+=(r?10:0)+(i?1+Math.min(4,n.split(t).length-2):0)}if(!o)continue;let s=n.indexOf(r[0]),c=Math.max(0,s-50),l=t.text.replace(/\s+/g,` `).trim(),ee=(c>0?`…`:``)+l.slice(c,c+140).trim()+(l.length>c+140?`…`:``);i.push({...t,score:a,snippet:ee})}return i.sort((e,t)=>t.score-e.score).slice(0,n)}var Y=t((()=>{let e=c(`lang`,``);return e===`de`||e===`en`?e:ge()})()),Te=e=>{Y.value=e,s(`lang`,e),document.documentElement.lang=e};function X(){let e=/^\/docs\/?([a-z0-9-]*)/.exec(location.pathname);return{page:H.some(t=>t.id===e?.[1])?e[1]:H[0].id,anchor:decodeURIComponent(location.hash.replace(/^#/,``))}}var Z=t(X());addEventListener(`popstate`,()=>Z.value=X());function Q(e){history.pushState(null,``,e),Z.value=X(),Z.value.anchor||window.scrollTo(0,0)}function Ee(e,t){m(()=>{t&&requestAnimationFrame(()=>document.getElementById(t)?.scrollIntoView({block:`start`}))},[e,t])}function $({c:t}){return a(e,{children:t.map((e,t)=>{switch(e.t){case`text`:return e.v;case`code`:return a(`code`,{children:e.v},t);case`strong`:return a(`strong`,{children:a($,{c:e.c})},t);case`em`:return a(`em`,{children:a($,{c:e.c})},t);case`link`:return a(De,{href:e.href,c:e.c},t)}})})}function De({href:e,c:t}){return/^https?:/.test(e)?a(`a`,{href:e,target:`_blank`,rel:`noopener noreferrer`,children:a($,{c:t})}):a(`a`,{href:e,onClick:t=>{t.metaKey||t.ctrlKey||t.shiftKey||(t.preventDefault(),e.startsWith(`#`)?Q(`${location.pathname}${e}`):e.startsWith(`/docs`)?Q(e):location.assign(e))},children:a($,{c:t})})}function Oe({b:e}){let t=`h${e.level}`;return a(t,{id:e.id,class:`dh dh${e.level}`,children:[a($,{c:e.c}),e.level>1&&a(`a`,{href:`#${e.id}`,class:`anchor`,"aria-label":U[Y.value].copyLink,title:U[Y.value].copyLink,onClick:async t=>{t.preventDefault();let n=`${location.origin}${location.pathname}#${e.id}`;history.replaceState(null,``,`#${e.id}`),await u(n)&&re({level:`info`,title:U[Y.value].docs,text:U[Y.value].linkCopied},2500)},children:`#`})]})}function ke({r:e}){let t=Y.value,n=[];if(e.shapeless)for(let t=0;t<9;t++)n.push(e.items?.[t]??null);else{let t=e.grid??[];for(let r=0;r<3;r++)for(let i=0;i<3;i++){let a=t[r]?.[i];n.push(a&&a!==` `&&a!==`.`?e.key?.[a]??null:null)}}let r=[...new Set(n.filter(Boolean))],i=e=>String.fromCharCode(65+r.indexOf(e));return a(`figure`,{class:`recipe`,children:[a(`div`,{class:`recipe-main`,children:[a(`div`,{class:`craft-grid`,role:`img`,"aria-label":`${W(e.result,t)}: ${r.map(e=>W(e,t)).join(`, `)}`,children:n.map((e,n)=>a(`span`,{class:`slot ${e?`full`:``}`,title:e?W(e,t):void 0,children:e&&a(`span`,{class:`slot-tag t${r.indexOf(e)%6}`,children:i(e)})},n))}),a(`span`,{class:`craft-arrow`,"aria-hidden":`true`,children:`→`}),a(`div`,{class:`craft-result`,children:[a(`span`,{class:`slot full result`,title:W(e.result,t),children:[a(d,{name:`box`,size:22}),e.count&&e.count>1&&a(`span`,{class:`slot-count`,children:e.count})]}),a(`span`,{class:`craft-result-name`,children:W(e.result,t)})]})]}),a(`figcaption`,{children:[a(`ul`,{class:`recipe-legend`,children:r.map(e=>a(`li`,{children:[a(`span`,{class:`slot-tag t${r.indexOf(e)%6}`,children:i(e)}),` `,W(e,t)]},e))}),e.shapeless&&a(`p`,{class:`muted small`,children:U[t].shapeless})]})]})}function Ae({b:e}){switch(e.t){case`h`:return a(Oe,{b:e});case`p`:return a(`p`,{children:a($,{c:e.c})});case`ul`:case`ol`:{let t=e.t;return a(t,{children:e.items.map((e,t)=>a(`li`,{children:a($,{c:e})},t))})}case`callout`:return a(`aside`,{class:`callout callout-${e.kind}`,children:e.c.map((e,t)=>a(`p`,{children:a($,{c:e})},t))});case`table`:return a(`div`,{class:`table-wrap`,children:a(`table`,{children:[a(`thead`,{children:a(`tr`,{children:e.head.map((e,t)=>a(`th`,{children:a($,{c:e})},t))})}),a(`tbody`,{children:e.rows.map((e,t)=>a(`tr`,{children:e.map((e,t)=>a(`td`,{children:a($,{c:e})},t))},t))})]})});case`code`:return a(p,{code:e.code,label:e.lang||`shell`});case`recipe`:return a(ke,{r:e.recipe})}}function je({onPick:e,autoFocus:t}){let[n,i]=r(``),o=Y.value,s=oe(()=>n.trim().length>=2?we(o,n):[],[n,o]);return a(`div`,{class:`docs-search`,children:[a(`input`,{type:`search`,class:`input docs-search-input`,placeholder:U[o].search,value:n,autoFocus:t,"aria-label":U[o].search,onInput:e=>i(e.target.value)}),n.trim().length>=2&&a(`div`,{class:`docs-hits`,role:`listbox`,"aria-label":U[o].results,children:s.length===0?a(`p`,{class:`muted small`,children:[U[o].noResults,` „`,n,`“`]}):s.map(t=>a(`a`,{class:`docs-hit`,href:`/docs/${t.page}${t.anchor?`#${t.anchor}`:``}`,onClick:n=>{n.preventDefault(),Q(`/docs/${t.page}${t.anchor?`#${t.anchor}`:``}`),i(``),e?.()},children:[a(`span`,{class:`docs-hit-title`,children:[t.heading,` `,a(`span`,{class:`muted`,children:[`· `,t.pageTitle]})]}),a(`span`,{class:`docs-hit-snippet`,children:t.snippet})]},`${t.page}#${t.anchor}`))})]})}function Me({page:e,onPick:t}){let n=Y.value,r=q(n,e).filter(e=>e.t===`h`&&e.level===2);return a(`nav`,{class:`docs-nav`,"aria-label":U[n].docs,children:H.map(i=>a(`div`,{children:[a(`a`,{href:`/docs/${i.id}`,class:`docs-nav-page ${i.id===e?`on`:``}`,"aria-current":i.id===e?`page`:void 0,onClick:e=>{e.preventDefault(),Q(`/docs/${i.id}`),t?.()},children:[a(d,{name:i.icon,size:14}),i.title[n]]}),i.id===e&&a(`div`,{class:`docs-toc`,children:r.map(e=>a(`a`,{href:`#${e.id}`,onClick:n=>{n.preventDefault(),Q(`/docs/${i.id}#${e.id}`),t?.()},children:e.text},e.id))})]},i.id))})}function Ne(){let e=Y.value;return a(`div`,{class:`seg-ctl lang-toggle`,role:`radiogroup`,"aria-label":U[e].language,children:[`de`,`en`].map(t=>a(`button`,{type:`button`,role:`radio`,"aria-checked":e===t,class:e===t?`on`:``,onClick:()=>Te(t),children:t.toUpperCase()},t))})}function Pe({page:e,onClose:t,children:n}){return a(`div`,{class:`backdrop backdrop-sheet`,onClick:e=>e.target===e.currentTarget&&t(),children:a(`section`,{class:`sheet docs-sheet`,role:`dialog`,"aria-modal":`true`,"aria-label":U[Y.value].menu,children:[a(`header`,{class:`sheet-head`,children:[a(`h2`,{class:`px-title`,children:U[Y.value].docs}),a(`button`,{type:`button`,class:`icon-btn`,"aria-label":`Close`,onClick:t,children:a(f,{})})]}),a(`div`,{class:`sheet-body`,children:[a(je,{onPick:t}),a(Me,{page:e,onPick:t}),n]})]})})}function Fe(){let{page:e,anchor:t}=Z.value,s=Y.value,[c,u]=r(!1),re=q(s,e),d=H.findIndex(t=>t.id===e),f=H[d-1],p=H[d+1],oe=ae(null);Ee(e,t),m(()=>{document.documentElement.lang=s,document.title=`${H[d].title[s]} – CC: Cloud ${U[s].docs}`},[e,s]),m(()=>{me[s][e]||Q(`/docs`)},[e,s]);let h=te.value;return a(`div`,{class:`docs`,children:[a(`header`,{class:`topbar docs-top`,children:[a(`a`,{href:`/`,class:`brand`,onClick:e=>{e.preventDefault(),history.pushState(null,``,`/`),dispatchEvent(new PopStateEvent(`popstate`))},"aria-label":`CC: Cloud dashboard`,children:[a(ie,{size:26}),a(`span`,{class:`brand-text`,children:[a(`span`,{class:`brand-cc`,children:`CC:`}),a(`span`,{class:`brand-cloud`,children:`Cloud`}),a(`span`,{class:`docs-badge`,children:U[s].docs})]})]}),a(`div`,{class:`docs-top-actions`,children:[a(Ne,{}),a(`button`,{type:`button`,class:`icon-btn`,onClick:o,"aria-label":`Theme: ${h}`,children:a(h===`dark`?i:h===`light`?ee:n,{})}),a(`button`,{type:`button`,class:`btn btn-small docs-menu-btn`,onClick:()=>u(!0),children:[`☰ `,U[s].menu]})]})]}),a(`div`,{class:`docs-layout`,children:[a(`aside`,{class:`docs-side`,children:[a(`a`,{class:`link-back`,href:`/`,onClick:e=>{e.preventDefault(),history.pushState(null,``,`/`),dispatchEvent(new PopStateEvent(`popstate`))},children:[a(ne,{size:16}),` `,U[s].back]}),a(je,{}),a(Me,{page:e})]}),a(`main`,{class:`docs-main`,ref:oe,children:[a(`article`,{class:`docs-article`,lang:s,children:re.map((t,n)=>a(Ae,{b:t},`${e}-${s}-${n}`))}),a(`nav`,{class:`docs-pager`,children:[f?a(`a`,{href:`/docs/${f.id}`,onClick:e=>{e.preventDefault(),Q(`/docs/${f.id}`)},children:[a(`span`,{class:`muted small`,children:[`← `,U[s].prev]}),a(`span`,{children:f.title[s]})]}):a(`span`,{}),p&&a(`a`,{class:`next`,href:`/docs/${p.id}`,onClick:e=>{e.preventDefault(),Q(`/docs/${p.id}`)},children:[a(`span`,{class:`muted small`,children:[U[s].next,` →`]}),a(`span`,{children:p.title[s]})]})]})]})]}),c&&a(Pe,{page:e,onClose:()=>u(!1)}),a(l,{})]})}export{Fe as default};
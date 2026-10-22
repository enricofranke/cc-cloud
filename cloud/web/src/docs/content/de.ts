import * as C from "./code.ts";

const { code, recipe, RECIPES: R } = C;

export const de: Record<string, string> = {
  // ---------------------------------------------------------------- start
  start: `# Erste Schritte

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
${recipe(R.core)}

### Cloud Mainframe
${recipe(R.mainframe)}

### Cloud Uplink
${recipe(R.uplink)}

### Advanced Cloud Uplink
${recipe(R.advancedUplink)}

### Cloud Link Card
${recipe(R.linkCard)}

### ME- und RS Cloud Bridge
${recipe(R.meBridge)}
${recipe(R.rsBridge)}

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
`,

  // ------------------------------------------------------------ cc basics
  "cc-basics": `# CC-Grundlagen für Einsteiger

Neu bei ComputerCraft? Hier steht das Wenige, das du für ein Dashboard-Programm brauchst.

## Shell oder Lua? {#shell-vs-lua}

Ein Computer startet in der **Shell** – einer Kommandozeile wie im Terminal. Die Shell startet **Programme** über ihren Namen (\`edit\`, \`ls\`, \`cloud-demo\`). Lua-Code versteht sie **nicht**.

> **Achtung:** Tippst du Lua direkt in die Shell, z. B. \`cloud.setTitle("x")\`, kommt **„No such program“**. Die Shell sucht dann ein Programm namens \`cloud.setTitle("x")\`. Schreib Lua-Code in eine Datei (siehe unten) oder nutze die \`lua\`-Eingabe.

## Ein Programm mit edit schreiben {#edit}

${code("", C.SHELL_EDIT)}

1. \`edit startup.lua\` eingeben und Enter drücken. Ein Editor öffnet sich.
2. Code eintippen oder einfügen. (Aus dem Dashboard: mit **Copy** kopieren, dann in Minecraft Strg+V.)
3. **Strg** drücken, mit den Pfeiltasten **Save** wählen und Enter, dann **Strg** → **Exit**.

Eine Datei namens \`startup.lua\` startet automatisch, wenn der Computer hochfährt – ideal für Dashboards.

## Ein Programm starten {#run}

Gib seinen Namen ohne \`.lua\` ein:

${code("", C.SHELL_RUN)}

Ein laufendes Programm stoppst du mit **Strg+T** (eine Sekunde gedrückt halten). Neustart mit **Strg+R**.

## Dateien auf den Computer ziehen {#drag-files}

Schreib den Code am PC in einem beliebigen Editor, speichere ihn als \`startup.lua\` und **zieh die Datei auf den geöffneten Computerbildschirm** in Minecraft. CC: Tweaked lädt sie in den aktuellen Ordner hoch.

## Schnell testen mit der lua-Eingabe {#lua-prompt}

\`lua\` öffnet eine interaktive Lua-Eingabe – praktisch, um einzelne Zeilen auszuprobieren:

${code("", C.SHELL_LUA)}

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
`,

  // ------------------------------------------------------------- dashboard
  dashboard: `# Das Dashboard

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
`,

  // ------------------------------------------------------------------ lua
  lua: `# Lua-Referenz

Alles, was ein Programm mit CC: Cloud tun kann. Im Spiel zeigt \`help cloud\` eine Kurzfassung.

## Das Peripheral holen {#peripheral}

${code("lua", 'local cloud = peripheral.find("cloud_uplink")')}

Der Uplink muss den Computer berühren oder im Netzwerk seines Wired Modems hängen. Mehrere Uplinks? Nimm \`peripheral.wrap("cloud_uplink_0")\`.

## Ein erstes Programm {#first-program}

${code("lua", C.LUA_FIRST)}

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

${code("lua", C.LUA_SETMANY)}

## Ereignisse {#events}

Bedient jemand ein Widget, bekommt jeder Computer am Uplink ein \`cloud_action\`-Ereignis:

${code("lua", C.LUA_EVENT)}

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

${code("lua", C.LUA_LAYOUT)}

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

${code("lua", C.LUA_MAINFRAME)}

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

${code("lua", C.LUA_BRIDGE)}

## Grenzen {#limits}

- IDs: 1–32 Zeichen a–z, 0–9, _
- 32 Widgets pro Uplink, 64 mit Seiten
- Titel und Beschriftungen 48 Zeichen, Textwerte 256, Meldungstext 200
- Meldungen: 1 pro Sekunde und Uplink
`,

  // ---------------------------------------------------------- ui library
  "ui-library": `# cloud.ui-Bibliothek

\`cloud.ui\` liegt im ROM jedes Computers. Sie verpackt den Uplink in einen Baukasten und übernimmt die Ereignisschleife: eine Funktion pro Widget, Handler statt \`os.pullEvent\`, Timer mit \`every\`.

## Schnellstart {#quick-start}

${code("lua", C.UI_QUICK)}

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

${code("lua", C.UI_FARM)}

## Beispiel: Reaktor-Panel {#example-reactor}

Peripheral-Name und Methoden an deinen Reaktor-Mod anpassen.

${code("lua", C.UI_REACTOR)}

## Mitgelieferte Programme {#programs}

- \`cloud-demo\` – alle Widgets auf zwei Seiten
- \`cloud-storage [bridge] [uplink]\` – ein eigenes Lager-Dashboard für eine ME / RS Bridge
`,

  // --------------------------------------------------------------- bridges
  bridges: `# Bridges: AE2 und Refined Storage

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

${code("lua", C.LUA_BRIDGE)}

Eine eigene Lager-Karte mit denselben Widgets:

${code("lua", C.LUA_BRIDGE_WIDGETS)}

Oder einfach \`cloud-storage\` starten. Alle Methoden: [Lua-Referenz → Bridges](/docs/lua#bridges).
`,

  // -------------------------------------------------------------- security
  security: `# Sicherheit und Hosting

Über das Dashboard lässt sich deine Welt steuern – deshalb ist es wie ein echter Webdienst geschützt.

## Standard: nur LAN {#lan-only}

Ab Werk können nur Geräte im lokalen Netzwerk des Servers (und der Host selbst) das Dashboard öffnen. Alle anderen bekommen „LAN only“.

${code("toml", C.TOML_LAN)}

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

${code("", C.COMMANDS)}

## PIN {#pin}

Kritische Widgets (\`pin=true\`) brauchen eine 4- bis 8-stellige PIN. 5 Fehlversuche sperren das Gerät für 5 Minuten und benachrichtigen den Besitzer.

## Zugriff aus dem Internet {#internet}

> **Achtung:** Unverschlüsseltes HTTP über das Internet überträgt Zugangstoken und PIN im Klartext. Nutze HTTPS (Caddy, Cloudflare Tunnel oder TLS im Mod).

Setze \`webAllowInternet = true\` und wähle eine der folgenden Varianten. Läuft das Dashboard per HTTP über eine öffentliche Adresse, zeigt es eine Warnung.

### Caddy als Reverse Proxy (empfohlen) {#caddy}

${code("toml", C.TOML_CADDY)}

${code("", C.CADDYFILE)}

Caddy holt sich automatisch ein Zertifikat und leitet WebSockets weiter.

### Cloudflare Tunnel (kein offener Port) {#cloudflare}

Gleiche \`[web]\`-Einstellungen wie bei Caddy, dann:

${code("bash", C.CLOUDFLARED)}

${code("yaml", C.CLOUDFLARED_YML)}

### TLS direkt im Mod {#tls}

${code("toml", C.TOML_TLS)}

Den Let's-Encrypt-Schlüssel vorher umwandeln: \`${C.PKCS8}\`. Lassen sich die Dateien nicht laden, startet das Dashboard nicht – es fällt nie heimlich auf HTTP zurück.

### Port direkt freigeben {#open-port}

Nur für schnelle Tests: \`webAllowInternet = true\`, \`publicUrl = "http://mc.example.com:8787"\` setzen und TCP 8787 im Panel deines Hosters freigeben.

## Was der Server protokolliert {#logs}

Jede Kopplung, Freigabe, Sperre und Dashboard-Aktion steht als \`[cccloud] …\` im Server-Log.
`,

  // ------------------------------------------------------------------- faq
  faq: `# Fehlerbehebung und FAQ

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
`,
};

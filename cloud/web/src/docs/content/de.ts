import * as C from "./code.ts";

const { code, recipe, RECIPES: R } = C;

export const de: Record<string, string> = {
  // ---------------------------------------------------------------- start
  start: `# Erste Schritte

CC: Cloud bringt deine ComputerCraft-Computer und dein Lager auf ein Dashboard in jedem Browser – PC, Tablet oder Handy. Der Minecraft-Server stellt das Dashboard selbst bereit – kein externer Dienst, keine Registrierung.

> **Tipp:** Der schnellste Weg: Cloud Mainframe craften, mit Strom versorgen, einen Cloud Uplink an einen Computer setzen, beide mit einer Cloud Link Card verbinden und den Browser koppeln. Der Computer erscheint automatisch auf dem Dashboard – ganz ohne Code.
>
> Du willst nur eine Lampe schalten? Eine **Cloud-Lampe** setzen (oder einen Cloud-Schalter neben deine Redstone-Lampen) und verbinden – sie erscheint als Schalter im Dashboard. Siehe [Redstone-Geräte](/docs/redstone).

## Die Blöcke {#blocks}

| Block / Item | Funktion |
|---|---|
| **Cloud Mainframe** | Das Herz einer Basis. Zwei Blöcke hoch. Stellt die Verbindung zum Dashboard her, zeigt den Kopplungscode und verwaltet Geräte. Einer pro Basis oder Team. Braucht FE-Strom. |
| **Cloud Uplink** | Haftet wie ein Modem an einer Blockseite. An einem Computer wird er zur Karte dieses Computers im Dashboard. Reichweite 128 Blöcke, gleiche Dimension. |
| **Advanced Cloud Uplink** | Genauso, aber mit unbegrenzter Reichweite und dimensionsübergreifend. |
| **Cloud Link Card** | Verbindet Uplinks, Bridges und Redstone-Geräte mit einem Mainframe. |
| **Cloud-Schalter / -Taster / -Lampe / -Sensor** | Kleine [Redstone-Geräte](/docs/redstone) ohne Computer: verbinden und Lampen, Türen und Maschinen im Dashboard schalten. Geräte mit derselben Gruppe teilen sich eine Karte. |
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

### Mainframe abbauen und umziehen {#moving}

Der Mainframe trägt seine Identität im Item. Abbauen (Spitzhacke) und das Item wieder platzieren – egal wo, auch in einer anderen Basis – ergibt **denselben Mainframe**: Geräte, Konten, PIN, Layout, Regeln, Szenen und API-Schlüssel sind wieder da, verknüpfte Uplinks und Bridges verbinden sich von selbst. Er gehört weiter seinem Besitzer, egal wer ihn platziert. Upgrade-Karten fallen als eigene Items heraus; setze sie wieder ein.

- **Nichts wird sofort gelöscht.** Nach dem Abbauen behält der Server alles **7 Tage** lang (\`mainframeRetentionDays\`); das Dashboard zeigt den Mainframe solange als **Removed**. Erst danach oder mit \`/cccloud mainframe purge <id>\` werden seine Daten gelöscht.
- **Item verloren?** Im Kreativmodus fällt beim Abbauen nichts heraus: \`/cccloud mainframe recover\` listet deine abgebauten Mainframes und gibt dir ihr Item (einmal pro Abbau). Ist das Item gedroppt, platziere dieses – oder frag einen Server-Admin, der kann immer wiederherstellen.
- **Schutz vor Fehlklicks** (\`mainframeBreakProtection\`): Im Kreativmodus lässt sich der Mainframe nur **schleichend** abbauen; im Überlebensmodus nur von seinem Besitzer, seinen Admins und Server-Admins. Claim-Mods schützen ihn zusätzlich.

## Strom (FE) {#power}

Der Mainframe läuft mit **FE** (Forge Energy, auch RF genannt) – der Einheit von Powah, Ender IO, Thermal, AE2 und den meisten Technik-Mods. Schließe ein beliebiges FE-Kabel oder einen Generator an eine der beiden Hälften an.

| | Standard |
|---|---|
| Grundverbrauch | 40 FE/t |
| Pro Uplink / Advanced Uplink / Bridge | +4 / +8 / +10 FE/t |
| Pro verbundenem Dashboard | +2 FE/t |
| Pro zusätzlichem Sprung eines Geräts | +1 FE/t |
| Puffer | 200.000 FE |
| Max. Eingang | 2.000 FE/t |

Ohne Strom geht der Mainframe offline (nach 5 Sekunden mit leerem Puffer) und das Dashboard zeigt **„Mainframe has no power“**. Sobald der Puffer wieder voll genug ist, verbindet er sich von selbst. Uplinks und Bridges brauchen kein eigenes FE, [Relays](/docs/network#power) schon. Server-Admins können die Strompflicht abschalten (\`requirePower = false\`).

## Uplinks und Bridges verbinden {#linking}

1. Setze einen **Cloud Uplink** an eine Seite eines Computers (oder auf eine ME / RS Cloud Bridge).
2. Rechtsklick mit der **Cloud Link Card** auf den **Mainframe** – auf der Karte steht jetzt „Linked to Mainframe @ x y z“.
3. Rechtsklick mit der Karte auf den **Uplink** (oder die Bridge). Fertig – die Statusleuchte wird grün.

Schleichen + Rechtsklick in die Luft löscht die Karte. Nur Besitzer und vertraute Spieler dürfen verbinden.

## Browser koppeln {#pairing}

1. Rechtsklick auf den Mainframe und den **Kopplungscode** merken (8 Zeichen, 10 Minuten gültig, einmal verwendbar).
2. Öffne die Dashboard-URL aus dem Mainframe-Bildschirm im Browser (z. B. \`http://192.168.1.20:8787\`). Im selben WLAN klappt das sofort.
3. Code und einen Namen für diesen Browser eingeben, **Pair device** anklicken.
4. Klicke in Minecraft im Chat (oder im Mainframe-Bildschirm) auf **[Allow]**. Der Browser macht automatisch weiter.

> **Tipp:** Verknüpfe deinen Spieler einmal mit \`/cccloud link\`, dann sieht der Browser jeden Mainframe, auf dem du eine Rolle hast – siehe [Konten und Rollen](/docs/accounts). Als App installieren: „App installieren“ in Chrome/Edge am PC, „Zum Home-Bildschirm“ auf Tablet und Handy.

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

Jeder Uplink wird zu einer **Karte**; [Redstone-Geräte](/docs/redstone) teilen sich eine Karte pro Gruppe. Ein Computer ohne eigenes Programm zeigt die automatische **Computer-Karte**: Strom, Turn on / Reboot / Shut down und seine sechs Redstone-Seiten (Seite antippen schaltet den Ausgang, die LED zeigt den Eingang; analoge Stärken unter „Advanced“).

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

Programme können Widgets auf **Seiten** (Tabs: oben in breiten Fenstern, unten auf schmalen Bildschirmen) und in **Gruppen** legen. Admins sehen einen **Stift**: Damit wird das Dashboard selbst zum Editor – du ordnest die echten, laufenden Karten an.

- **Verschieben:** eine Karte an ihrer Kopfzeile ziehen, ein Widget an beliebiger Stelle. Die anderen machen Platz und gleiten zur Seite; ein gestrichelter Rahmen zeigt, wo es landet. Ein Widget auf eine andere Karte ziehen, auf freie Fläche (es bekommt seine **eigene neue Karte**) oder auf einen Seiten-Tab (kurz darüber halten öffnet die Seite).
- **Größe ändern:** rechten Rand, unteren Rand oder die Ecke ziehen – das Schild zeigt die Größe (Karten nutzen 12 Spalten der Seite, Widgets 12 Spalten ihrer Karte).
- **Mehrere Widgets auswählen** mit Shift-Klick und eines davon ziehen, um alle zu verschieben.
- **Einstellungen** (⚙ oder Klick): Beschriftung, Akzentfarbe, Icon, Größe und Darstellung mit Live-Vorschau – ein Gauge als Skala, Balken oder große Zahl, ein Label als Zeile oder große Zahl, ein Chart als Diagramm oder Sparkline, ein Indikator als Chip oder Lampe. Karten bekommen Titel, Icon, Akzent und den Stil kompakt oder komfortabel.
- **Ausblenden** mit ×. **+ Add** listet alles Ausgeblendete (zurückziehen oder Show drücken) und zeigt jeden Widget-Typ live mit der Lua-Zeile, die ihn erzeugt.
- **Touch:** gedrückt halten zum Aufnehmen (kurze Vibration), dann ziehen – normales Wischen scrollt weiter. **Tastatur:** mit Tab zu Karte oder Widget, Leertaste nimmt auf, Pfeiltasten verschieben, Shift + Pfeiltasten ändern die Größe, Bild auf/ab verschieben ein Widget zur vorigen/nächsten Karte, Leertaste legt ab, Esc bricht ab. **?** zeigt alle Kürzel.
- **Rückgängig/Wiederholen** (Strg/⌘ + Z, Strg/⌘ + Shift + Z), **Reset** auf die Vorgabe der Skripte und **Save** (Strg/⌘ + S). Wer mit ungespeicherten Änderungen geht, wird vorher gefragt.

**Save** speichert das Layout auf dem Server – alle Browser sehen es. Startet ein Skript neu, bleibt das Layout erhalten, weil die Widget-IDs gleich bleiben. Auf schmalen Bildschirmen stehen die Karten in Lesereihenfolge untereinander (auf Handys zweispaltig).

## Meldungen {#alerts}

Programme senden Meldungen mit \`cloud.alert(text, level)\`. Sie erscheinen als Einblendung und bleiben im Protokoll unter der **Glocke**. Info-Meldungen verschwinden von selbst, warn und crit bleiben, bis du sie schließt. Mit **Enable notifications** im Protokoll bekommst du für kritische Meldungen System-Benachrichtigungen, auch wenn der Tab im Hintergrund ist.

## PIN-geschützte Bedienelemente {#pin}

Widgets mit \`pin=true\` fragen vor dem Auslösen die Mainframe-PIN über ein Ziffernfeld ab. Der Besitzer legt sie mit \`/cccloud pin set <4-8 Ziffern>\` fest. Nach 5 falschen PINs ist das Gerät 5 Minuten gesperrt und der Besitzer wird im Spiel benachrichtigt.

## Zustände {#states}

| Anzeige | Bedeutung |
|---|---|
| Dashboard disabled | das Dashboard ist im Mainframe-Bildschirm ausgeschaltet |
| Offline | der Chunk des Mainframes ist nicht geladen oder der Redstone-Not-Aus ist aktiv |
| Removed | der Mainframe-Block wurde abgebaut – sein Item wieder platzieren bringt alles zurück ([wie](/docs/start#moving)) |
| Mainframe has no power | der FE-Puffer ist leer – siehe [Strom](/docs/start#power) |
| Uplink offline | dieser Uplink ist nicht geladen oder außer Reichweite |
| Read-only device | dieses Gerät darf zusehen, aber nicht steuern |
| Server not reachable | siehe [Fehlerbehebung](/docs/faq#not-reachable) |
`,

  // ------------------------------------------------------------- redstone
  redstone: `# Redstone-Geräte

Eine Lampe vom Handy aus schalten – ohne Computer, Lua oder Kanäle: **hinstellen, mit der Link Card verbinden, fertig.** Vier kleine Blöcke, jeder günstiger als ein Uplink.

| Block | Funktion | Im Dashboard |
|---|---|---|
| **Cloud-Schalter** | Haftet wie ein Hebel an jeder Blockseite – an der Wand, auf dem Boden oder unter der Decke. Gibt Strom wie ein Hebel: stark in den Block, an dem er hängt, schwach rundherum. An/aus (0 oder 15) oder analog (0–15). | ein Schalter, im Analog-Modus ein Schieberegler |
| **Cloud-Taster** | Wie ein Knopf: ein kurzer Impuls, 2–40 Ticks (Standard 10 = eine halbe Sekunde). | ein Knopf |
| **Cloud-Lampe** | Ein Lampenblock, der von selbst leuchtet – ganz ohne Redstone. Helligkeit 1–15; kann zusätzlich auf ein Redstone-Signal reagieren. | ein Schalter und ein Helligkeitsregler |
| **Cloud-Sensor** | Liest das Redstone-Signal, das er bekommt (der stärkste Nachbar, 0–15). | eine Leuchte (an/aus) und die Stufe |

## Einrichten {#setup}

1. Gerät craften und setzen (Rezepte [unten](/docs/redstone#recipes)).
2. Mainframe mit einer **Cloud Link Card** anklicken, dann das Gerät – wie bei einem Uplink und mit denselben Rechten (Besitzer, Admins, „Geräte verbinden“).
3. Es erscheint von selbst im Dashboard: auf der Karte **Redstone** oder auf der Karte seiner Gruppe.

In der Welt schaltet ein Klick einen Schalter oder eine Lampe und drückt einen Taster. **Schleichen + Rechtsklick** (beim Sensor jeder Klick) öffnet das Fenster des Geräts: was es ist, sein Zustand, Name und Gruppe, Modus oder Impulslänge, PIN und Nachfrage und wie es verbunden ist.

## Gruppen und Karten {#groups}

- Jedes Gerät hat einen **Namen** und optional eine **Gruppe**. Geräte mit derselben Gruppe am selben Mainframe teilen sich **eine Karte** – zum Beispiel „Halle“ mit sechs Lampenschaltern. Groß- und Kleinschreibung spielt keine Rolle.
- Geräte ohne Gruppe teilen sich die automatische Karte **Redstone** (eine pro Mainframe).
- Eine Karte mit zwei oder mehr Schaltern oder Lampen bekommt **Alle an** und **Alle aus**.
- Höchstens 32 Geräte pro Karte; weitere landen auf „Halle (2)“.
- Name und Gruppe setzt du im Fenster des Geräts oder im Dashboard: **Netzwerk** → Gerät antippen → **Dashboard** (braucht „Geräte verbinden“).
- Die Karten sind normale Karten: im [Layout-Editor](/docs/dashboard#edit-mode) anordnen, einzelne Regler auf eigene Karten ziehen, auf Cloud Tablet oder Cloud Display zeigen und schreibgeschützt teilen.

## Regeln, Szenen und die API {#automation}

- **Regeln:** einen Sensor unter **Wenn** → „ein Widget-Wert“ wählen (z. B. *über 0* = an) und eine Push-Nachricht senden, eine Szene starten oder eine Lampe schalten. Beispiel: Wenn *Türsensor* über 0 – dann *Verandalicht* an und Meldung „Jemand ist an der Tür“.
- **Szenen:** ein Schritt „Regler bedienen“ auf einem Schalter, einer Lampe (auch ihrer Helligkeit) oder einem Taster.
- Regeln und Szenen funktionieren weiter, wenn ein Gerät in eine andere Gruppe wechselt.
- **[Öffentliche API](/docs/api):** die Karten sind normale Bereiche – lesen mit \`read:sections\`, schalten mit \`action:widgets\` (\`POST …/sections/<Karte>/widgets/<id>\` mit \`{"value": true}\`). Geräte mit PIN sind über die API nicht erreichbar.
- **Verlauf:** das Licht der Lampe und die Stufe des Sensors werden aufgezeichnet (24 h, begrenzt durch das Memory-Upgrade des Mainframes) – auf das Diagramm-Symbol tippen.

## Rechte und Schutz {#rights}

- Bedienen braucht **„Redstone schalten“** (Operatoren haben es). Betrachter und Freigabe-Links schauen nur zu.
- Pro Gerät: **Mainframe-PIN abfragen** und **Vor dem Schalten nachfragen** (in seinem Fenster, braucht „Geräte verbinden“). „Alle an / Alle aus“ fragt, wenn ein Gerät der Karte fragt.
- Verbinden, Umbenennen und die Einstellungen brauchen **„Geräte verbinden“** am Mainframe; von Hand anklicken kann es trotzdem jeder, der den Block benutzen darf – wie einen Hebel.
- Jedes Gerät nimmt höchstens 4 Änderungen pro Sekunde aus Dashboard, Regeln, Szenen und API an (\`redstoneDeviceChangesPerSecond\`, kurz bis zu 8); Klicks in der Welt sind nicht begrenzt.

## Reichweite, Kapazität und Strom {#network}

| | Redstone-Gerät | Cloud Uplink |
|---|---|---|
| Reichweite zum nächsten Knoten | 32 Blöcke (\`redstoneDeviceRange\`) | 48 |
| Geräte-Kapazität | 0,25 (\`redstoneDeviceCapacityWeight\`) | 1 |
| Strom am Mainframe | 1 FE/t (\`mainframePerRedstoneDeviceFE\`) | 4 FE/t |

- Nur Funk (keine Kabel); [Relais](/docs/network) verlängern die Reichweite wie bei Uplinks. Außer Reichweite oder über der Kapazität antworten seine Regler „keine Verbindung zum Mainframe“.
- Ein Gerät in einem nicht geladenen Chunk bleibt mit seinem letzten Zustand auf seiner Karte; seine Regler antworten „nicht geladen“.
- Die Geräte erscheinen auf der Netzwerkkarte, im Cloud Analyzer und in Jade.

## Rezepte {#recipes}

### Cloud-Schalter
${recipe(R.cloudSwitch)}

### Cloud-Taster
${recipe(R.cloudButton)}

### Cloud-Lampe
${recipe(R.cloudLamp)}

### Cloud-Sensor
${recipe(R.cloudSensor)}
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
- Icons: home, bolt, flame, drop, box, fan, lock, warning, gear, chart, star, leaf, heart, cpu, shield, pick, lever, button, lamp, sensor.

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
| \`getNetwork()\` | {nodes, links, routes} – siehe [Netzwerk](/docs/network#map) |

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

## Skripte aus dem Browser hochladen {#upload}

Admins können Lua-Programme auf einen Computer bringen, ohne sie im Spiel abzutippen: **</>** oben in der Karte des Computers anklicken.

- **Upload:** eine \`.lua\`-Datei hineinziehen oder Code einfügen, Dateinamen wählen (\`name.lua\` oder \`ordner/name.lua\`), dann **Upload & run**. Vorhandene Dateien werden nur nach Bestätigung ersetzt.
- **Run it now** startet das Programm auf Advanced-Computern in einem neuen Tab. Normale Computer haben keine Tabs: Das Dashboard fragt, ob der Computer neu starten und es einmal ausführen soll.
- **Start it automatically** schreibt eine \`startup.lua\`, die deine Datei bei jedem Start ausführt.
- **Examples:** fertige Programme (Kontrollraum, Testprogramm, die Widget-Demo) – auswählen, anpassen, hochladen.
- **Files:** sehen, was auf dem Computer liegt, Dateien öffnen, herunterladen oder löschen, und wie viel Speicher frei ist.

Das sehen nur Admins des Mainframes, und Änderungen brauchen die Mainframe-PIN, falls eine gesetzt ist. Der Code läuft auf diesem CC-Computer in CCs Sandbox – nie auf dem Minecraft-Server selbst. Jeder Upload wird protokolliert, und der Besitzer bekommt eine Chat-Nachricht. Grenzen: 256 KB pro Datei, 10 Änderungen pro Minute.

## Script Store {#store}

Fertige Programme aus der Community: **Script Store** im Menü des Mainframes (Admins). Jedes Skript zeigt, was es tut, was es **nutzt** (Redstone, Peripherie, Internet …) und den vollständigen Quelltext – vor der Anzeige gegen den SHA-256 im Index des Stores geprüft. Der Index ist vom Store signiert: Dein Browser prüft die Signatur, wo er kann (**signed ✓**), und der Minecraft-Server prüft sie noch einmal, bevor er eine Datei schreibt. Das beweist, dass die Dateien aus dem Store stammen – nicht, dass der Code sicher ist, also **lies den Code, bevor du installierst**. Computer wählen und installieren; sofort starten oder automatisch beim Start. Installierte Store-Skripte zeigen **Update available**, wenn es eine neuere Version gibt, und du wirst gewarnt, bevor eine auf dem Computer geänderte Datei überschrieben wird.

Auf der Projekt-Website öffnet **Install** dein Dashboard unter \`/store#install=<skript>\` – dieselbe Ansicht, ohne deinen Klick wird nichts installiert. Server-Admins können den Store abschalten (\`scriptStore=false\`). Community-Skripte laufen auf deinem CC-Computer und können steuern, was er steuert – den Minecraft-Server selbst können sie nicht anfassen.
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

  network: `# Netzwerk, Relays und Reichweite

Jeder Uplink und jede Bridge verbindet sich mit dem **nächsten erreichbaren Knoten** – dem Mainframe oder einem Relay – und das Netzwerk leitet ihn über den besten Weg zum Mainframe (wenigste Sprünge, dann bestes Signal; höchstens 8 Sprünge). Wie ein WLAN-Mesh: Mit Relays erreichst du weit entfernte Basen.

## Reichweiten {#ranges}

| Block | Reichweite zu Knoten | Reichweite für Geräte |
|---|---|---|
| Cloud Mainframe | 96 | 96 |
| Cloud Relay | 96 | 48 |
| Advanced Cloud Relay | 256 | 128 |
| Ender Relay | beliebig weit und dimensionsübergreifend zu anderen Ender Relays | 48 |
| Cloud Uplink | – | 48 |
| Advanced Cloud Uplink | – | 128 |
| ME / RS Cloud Bridge | – | 48 |
| Cloud-Schalter / -Taster / -Lampe / -Sensor | – | 32 |

**Knoten-Reichweite** = wie weit ein Relay oder der Mainframe **andere Relays und den Mainframe** erreicht. **Geräte-Reichweite** = wie weit er **Uplinks und Bridges** erreicht. Auf der Karte ist die Knoten-Reichweite der **durchgezogene Kreis**, die Geräte-Reichweite der **gestrichelte Kreis**; ein Klick auf ein Relay beschriftet beide, und das **?** auf der Karte erklärt, wie man Relays plant.

Entfernungen werden in Blöcken in gerader Linie gemessen (auch nach oben und unten), innerhalb einer Dimension. Eine Verbindung steht, wenn ein Ende das andere erreicht – die größere der beiden Reichweiten zählt. Kabel (unten) haben keine Reichweitengrenze.

## Signal {#signal}

Signal = 1 − Entfernung / Reichweite, angezeigt als 0–100 % und Balken. Unter 15 % ist eine Verbindung **schwach**: Sie funktioniert, wird aber gelb angezeigt – rück das Gerät näher heran oder setz ein Relay.

## Relays {#relays}

- **Cloud Relay** – ein Mast für mittlere Entfernungen.
- **Advanced Cloud Relay** – größere Reichweite für Knoten und Geräte.
- **Ender Relay** – verbindet sich mit anderen Ender Relays über jede Entfernung und **dimensionsübergreifend** (z. B. in den Nether). Ender Relays mit der Link Card zu einer Gruppe verbinden.

Relay aufstellen und wie jedes Gerät verbinden: Link Card auf den Mainframe, dann auf das Relay.

### Cloud Relay
${recipe(R.relay)}

### Advanced Cloud Relay
${recipe(R.advancedRelay)}

### Ender Relay
${recipe(R.enderRelay)}

> **Tipp:** Klappt das Verbinden nicht, nennt die Meldung den nächsten Knoten und seine Entfernung. Setz ein Relay dazwischen.

## Kabel (kabelgebundene Verbindungen) {#cables}

Geräte können sich auch **per Kabel** verbinden, wie bei AE2: Uplink oder Bridge mit CC: Tweakeds **Networking Cable** und **Wired Modems** an den Mainframe oder ein Relay anschließen (Modems per Rechtsklick einschalten). Alles im selben CC-Kabelnetz ist verkabeltes Mitglied.

- Kabel haben **keine Reichweitengrenze**, immer **100 %** Signal und kosten **kein zusätzliches FE pro Sprung**.
- Ein Gerät kann Kabel und Funk gleichzeitig haben; das Kabel wird bevorzugt, Funk ist die Reserve.
- Im Dashboard sind Kabel **durchgezogene Linien** (grau = CC-Kabel, leuchtend cyan = Glasfaser), Funkverbindungen **gestrichelt** (nach Signal gefärbt), Ender-Verbindungen **gepunktet**.

**Fiber Cable** (Glasfaser, Teil von CC: Cloud) funktioniert genauso, verbindet aber nur unsere Geräte – ein sauberes eigenes Netz. Geräte an Glasfaser zählen nur **halb** für die Gerätekapazität des Mainframes und zahlen **halbes** FE. Mit der **Fiber Junction** verzweigst du und gehst durch Wände, ein **Ender Fiber Link**-Paar verbindet zwei Glasfasernetze über jede Entfernung und Dimension.

## Wenn ein Relay ausfällt {#reroute}

Ist ein Relay abgebaut, nicht geladen oder ohne Strom, wird alles dahinter **umgeleitet**, sofern es einen anderen Weg gibt. Sonst zeigen diese Geräte **No route**. Relays laden nie Chunks zwangsweise; Teile in nicht geladenen Chunks zeigen **Unloaded**.

Geräte, die vor v0.4 verbunden wurden und nach den neuen Regeln außer Reichweite sind, funktionieren weiter und zeigen **Out of range (legacy)**, bis du in der Nähe ein Relay aufstellst oder sie neu verbindest.

## Strom {#power}

Das Netzwerk kostet FE. Der Mainframe braucht 40 FE/t + 4 pro Uplink + 8 pro Advanced Uplink + 10 pro Bridge + 2 pro verbundenem Dashboard + 1 pro zusätzlichem Sprung jedes Geräts. Relays brauchen eigenes FE: Cloud Relay 20 FE/t, Advanced 60 FE/t, Ender Relay 120 FE/t. Ein Klick auf die Stromanzeige oben im Dashboard zeigt die Aufschlüsselung.

Die Aufschlüsselung zeigt auch Geräte an Glasfaser (sie zahlen die Hälfte), den **Chunk Anchor**, falls eingebaut, und ein **Efficiency**-Upgrade als grüne Zeile mit dem eingesparten FE. Darunter zeigt **Device capacity**, wie viele Geräte der Mainframe trägt und wie viele seine Upgrade-Karten erlauben; dieselbe Zahl steht als Chip im Network-Tab.

## Die Netzwerkkarte {#map}

Der Tab **Network** zeigt alles, was verbunden ist, in drei Ansichten:

- **Topology** – der Mainframe als Wurzel, Relays als Äste, Geräte als Blätter. Kabel sind durchgezogen, Funkverbindungen gestrichelt und nach Signal gefärbt (grün stark, cyan ok, gelb schwach), Ender-Verbindungen lila gepunktet, Legacy orange; der Punkt zeigt den Status. Geräte ohne Weg stehen unter **Not connected**.
- **Map** – eine Draufsicht (x/z) pro Dimension auf einer echten Karte der Welt: **Surface** (wie eine Minecraft-Karte) oder **Slice** – ein Schnitt in einer Höhe, die du mit dem Y-Regler wählst, damit eine Untergrundbasis auf y −60 ihre Räume und Höhlen zeigt (dunkler = tiefer). Gezeigt werden nur Orte in der Nähe deines Netzwerks und nur Chunks, die geladen waren. Mit Koordinatengitter, Reichweitenkreisen (umschaltbar; durchgezogen = Knoten-Reichweite, gestrichelt = Geräte-Reichweite), Verbindungen und Statuspunkten. Ziehen zum Verschieben, Scrollen oder Zwei-Finger-Zoom, ⤢ zeigt alles.
- **3D** – siehe unten.

Mit FTB Chunks und Waystones zeigt die Karte auch die **geclaimten Chunks** deines Teams (Teamfarbe, zwangsgeladene gestrichelt) und deine **Wegsteine** (lila Rauten, globale gold) – ein- und ausschaltbar mit **Claims** und **Waystones**. Wegsteine sind auch in der 3D-Ansicht markiert. Wie alle Koordinaten werden sie nur an Spieler geschickt, die Positionen sehen dürfen.

Ein Klick auf einen Knoten zeigt Details mit Untermenüs: **Info** (Typ, Position, Dimension, Status, Signal oder Kabel, Reichweite), **Route** (jeder Sprung bis zum Mainframe und was ein Relay weiterleitet), **Power** (Verbrauch und Puffer) und **Card** (die Live-Karte des Geräts, Bedienung je nach Rolle). **Open card** springt zur Karte im Dashboard; **Unlink** entfernt ihn aus dem Netzwerk (Admins). Positionen sehen nur Browser, die den Mainframe steuern dürfen.

## 3D-Ansicht {#3d}

Die Ansicht **3D** zeigt deine Basis mit den echten Blockmodellen an ihren echten Koordinaten: Verbindungen als Linien (Kabel durchgezogen, Funk gestrichelt, Ender gepunktet), Reichweiten als durchscheinende Kugeln (Schalter **Ranges**) und – wenn der Server es schickt – das grobe Gelände um deine Blöcke (Schalter **Terrain**; nur geladene Chunks, schwarze Löcher sind nicht geladene Bereiche).

- **Maus:** ziehen zum Drehen, Rechtsklick- oder Shift-Ziehen zum Verschieben, Mausrad zum Zoomen. **Touch:** ein Finger dreht, zwei Finger zoomen und verschieben. **Tastatur:** Ansicht fokussieren, Pfeiltasten verschieben, + und − zoomen, Esc hebt die Auswahl auf. ⤢ zeigt wieder alles.
- Ein Klick auf einen Block zeigt seine Details. Ein Klick auf ein **Relay** (oder den Mainframe) hebt alles hervor, was darüber läuft, und blendet den Rest ab.
- **Fly to…** findet einen Block über den Namen und fliegt die Kamera hin; die Dimensions-Tabs wechseln zwischen Overworld, Nether und weiteren.
- **Blocks:** die Welt um dein Netzwerk als Voxel – echtes Gelände, Höhlen und deine Basis, eingefärbt wie eine Minecraft-Karte, mit weichen Schatten in Ecken, leuchtenden Lampen und durchsichtigem Wasser. Gesendet werden nur Blöcke, die Luft berühren (kein Röntgenblick auf Erze), nur im Umkreis von etwa 40 Blöcken um deine Geräte und nur aus geladenen Chunks. **Cut** entfernt alles oberhalb einer Höhe (Regler), damit du in eine Untergrundbasis schauen kannst; **Night** dunkelt die Welt ab und lässt die Lampen leuchten.
- Aus der Ferne werden Blöcke größer gezeichnet, damit die Basis lesbar bleibt; der farbige Punkt über jedem Block ist sein Status.
- 3D braucht WebGL. Ohne WebGL (abgeschaltet, sehr alte Grafik) zeigt das Dashboard stattdessen die 2D-Karte. Die 3D-Ansicht wird erst geladen, wenn du sie öffnest – der Rest des Dashboards bleibt schnell.

Aus Lua liefert \`cloud_mainframe.getNetwork()\` dieselben Knoten, Verbindungen und Wege.
`,
  upgrades: `# Upgrades, Tablet und Display

Neu in v0.7: Mainframe-Upgrades für große Basen, das **Cloud Tablet** fürs Dashboard im Spiel und das **Cloud Display** für Karten an der Wand.

## Mainframe-Upgrades {#upgrades}

Der Mainframe hat **4 Upgrade-Slots** in seinem Fenster (Trichter können nichts einfügen). Ohne Upgrades schafft er:

| Grenze | Ohne Upgrades |
|---|---|
| Verbundene Geräte (Uplinks, Bridges, Relays) | 16 |
| Browser gleichzeitig | 4 |
| Regeln | 8 |
| Verlauf | 1 h |
| Knoten-Reichweite des Mainframes | 96 |

Upgrade-Karten gibt es in drei Stufen. Pro Typ zählt nur eine Karte – die höchste Stufe gewinnt.

| Karte | Allthemodium | Vibranium | Unobtainium |
|---|---|---|---|
| Capacity (Geräte) | 48 | 128 | 512 |
| Bandwidth (Browser, Update-Rate) | 8 Browser | 16 | 64 |
| Range (Knoten-Reichweite ×) | ×1,5 | ×2 | ×3 |
| Efficiency (Mainframe-FE ×) | ×0,8 | ×0,65 | ×0,5 |
| Memory (Regeln / Verlauf) | 24 / 24 h | 64 / 7 Tage | 128 / 30 Tage |

- **Quantum Core** (ATM-Star-Stufe, einer pro Mainframe): unbegrenzt Geräte und Browser, der Mainframe arbeitet als Ender-Knoten (dimensionsübergreifend) und braucht nur ein Viertel des FE.
- Die Rezepte nutzen die Metalle des Packs (\`#c:ingots/allthemodium\` usw.). Ohne AllTheModium gibt es Ersatzrezepte mit Diamant, Netherit und Netherstern. Nachschauen in JEI/EMI.
- **Über der Grenze:** Neue Verbindungen werden mit einer klaren Meldung abgelehnt. Nimmst du eine Karte heraus, zeigen die überzähligen Geräte **Over capacity** und pausieren (die mit den meisten Sprüngen bzw. zuletzt verbundenen zuerst), bis wieder Platz ist.
- Das Dashboard zeigt die genutzte Kapazität (z. B. „12.5 / 48 devices“) im Network-Tab und im Strom-Popup; Geräte an Glasfaser zählen halb.

## Cloud Tablet {#tablet}

Ein Handgerät, das dein Dashboard **im Spiel** öffnet – ein eigenes Fenster mit denselben Karten und Widgets, ganz ohne Browser.

- Funktioniert in Netzwerk-Reichweite jedes Knotens (Mainframe oder Relay). Das **Advanced Cloud Tablet** funktioniert zusätzlich überall, wo ein Ender Relay hinreicht.
- Nutzt **deine Rolle** auf diesem Mainframe: Viewer schauen zu, Operatoren steuern, Admins bearbeiten.
- Optionale Taste zum Öffnen (standardmäßig nicht belegt; unter Steuerung einstellen), solange das Tablet im Inventar oder in einem Curios-Slot ist.
- Hergestellt aus einem CC-Pocket-Computer und einem Cloud Core (Advanced: dazu Gold und eine Enderperle).

## Cloud Display {#display}

Ein Display-Block, der eine Dashboard-Karte oder -Seite **in der Welt** zeigt, wie ein CC-Monitor.

- Mehrere nebeneinander ergeben einen größeren Bildschirm (Rechtecke bis **8 × 6**).
- Per Rechtsklick in seinem Fenster Karte oder Seite wählen. Standardmäßig nur zum Ansehen; Operatoren können **klickbare Buttons** erlauben – dann drückt ein Rechtsklick auf einen Button im Display ihn.
- Aktualisiert höchstens 4× pro Sekunde und wird ab 48 Blöcken Entfernung nicht mehr gezeichnet – schont Server und FPS.

## Drahtloses Redstone {#redstone}

Uplinks bekommen in ihrem Fenster einen **Redstone-Modus**: **Sender** oder **Empfänger** auf einem Kanalnamen. Empfänger geben das stärkste Signal aller Sender auf diesem Kanal aus (0–15). Das funktioniert über Relays, Kabel und – mit Ender Relays – dimensionsübergreifend, mit dem nächsten Tick als Verzögerung. Bis zu 64 Kanäle pro Netzwerk; sehr schnelle Takte (schneller als 2 Ticks) werden geglättet.

Im Dashboard listet der Chip **Redstone** im Network-Tab jeden Kanal mit Pegel, Sendern und Empfängern. Spieler mit dem Recht „Switch redstone“ (ab operator) können einen Kanal **übersteuern** – ein Pegel von 0 bis 15 für 30 s, 5 min, 1 h oder bis zum Zurücksetzen –, etwa um vom Handy aus eine Tür zu öffnen. Die Übersteuerung gewinnt gegen die Sender, bis sie abläuft oder du sie aufhebst; jede wird protokolliert.
`,
  automation: `# Regeln, Szenen, Benachrichtigungen und Verlauf

Automatisierung, die auf dem **Server** läuft – ohne Browser und ohne Computerprogramm –, dazu Szenen, Zeitpläne, Meldungen aufs Handy, Langzeit-Diagramme und Produktionsstatistiken.

## Regeln {#rules}

Öffne **Rules** in den Dashboard-Einstellungen (⚙). Eine Regel liest sich wie ein Satz: **Wenn** etwas passiert, **falls** Bedingungen gelten, **dann** tue etwas.

| Teil | Möglichkeiten |
|---|---|
| Wenn | ein Widget-Wert überschreitet eine Zahl, eine Itemanzahl in einer Lager-Karte, Mainframe-Strom in %, eine Karte geht offline, alle N Minuten, an Tagen zu einer Uhrzeit, eine Uhrzeit im Spiel ([Zeitpläne](/docs/automation#schedules)) |
| Falls (optional, alle müssen gelten) | Widget-Wert, Itemanzahl, Strom, eine Karte ist online |
| Dann (1–10, der Reihe nach) | eine Redstone-Seite eines Computers setzen, ein Bedienelement nutzen (Button, Schalter, Regler, Auswahl, Zahl, Text), ein Item craften, eine Meldung senden, das Dashboard ein-/ausschalten, eine [Szene](/docs/automation#scenes) starten |

- Wert-Auslöser feuern **einmal**, wenn die Prüfung wahr wird, nicht erneut, solange sie wahr bleibt. Die **Abklingzeit** (mindestens 5 s) ist der Mindestabstand zwischen zwei Läufen.
- **Run now** führt die Aktionen einmal aus und ignoriert Auslöser, Bedingungen und Abklingzeit – praktisch zum Testen.
- Sicherheit: Eine Regel darf nicht das Widget ändern, das ihr eigener Auslöser beobachtet (das wäre eine Schleife), PIN-geschützte Bedienelemente sind nicht erlaubt, und höchstens 20 Regelläufe pro Sekunde und Mainframe. Jeder Lauf wird protokolliert (**Recent runs**).
- Wie viele Regeln es geben darf, hängt vom **Memory**-Upgrade des Mainframes ab (8 ohne, dann 24 / 64 / 128).
- Rechte: „Edit rules“ zum Anlegen, Ändern und Löschen; „Enable rules“ zum Ein-/Ausschalten und für **Run now** (haben Operatoren).
- Lua: \`cloud_mainframe.getRules()\` und \`setRuleEnabled(id, enabled)\`.

**Beispiel:** Wenn *Core temperature (Fission Reactor)* über 900 – dann Redstone hinten am *Reaktor-Computer* auf 15 und Meldung „Reaktor heiß“ (Warnung).

**Ohne Computer:** ein [Cloud-Sensor](/docs/redstone#automation) neben einer Druckplatte – wenn *Türsensor* über 0, dann *Verandalicht* an und eine Push-Nachricht „Jemand ist an der Tür“.

## Szenen {#scenes}

Eine Szene ist **ein Knopf, der mehrere Schritte nacheinander ausführt** – „Nachtmodus“: Licht aus, 2 s warten, Tür zu, Reaktor-Regler auf 3, eine Meldung. Öffne **Szenen** in den Dashboard-Einstellungen (⚙).

- **Schritte** (bis 16): dieselben Aktionen wie bei Regeln – Redstone-Seite setzen, Bedienelement nutzen, craften, Meldung senden, Dashboard an/aus – plus **Pause** (0,05–300 s, höchstens 600 s pro Szene). Mit ↑ und ↓ änderst du die Reihenfolge.
- Jede Szene ist ein Knopf auf der Karte **Scenes**: drück ihn im Dashboard, auf einem Cloud Tablet, auf einem Cloud Display (nur Szenen ohne Nachfrage und PIN) oder verschieb ihn mit dem Layout-Editor auf eine beliebige Karte. **Testlauf** im Szenen-Fenster startet die gespeicherte Szene und hakt jeden Schritt beim Ablauf ab; der letzte Lauf behält ein Ergebnis pro Schritt.
- Optionen: Farbe, Symbol, **vor dem Start nachfragen** und **immer nach der PIN fragen**. Ein Schritt auf einem PIN-geschützten Bedienelement lässt die Szene ohnehin nach der PIN fragen.
- **Rechte:** Anlegen und Ändern braucht „Edit rules“ **und** jede Berechtigung, die die Schritte brauchen – niemand kann mehr weitergeben, als er selbst darf. Drücken braucht „Use controls“; jeder Schritt wird **beim Ablauf** erneut geprüft, mit den Rechten dessen, der gedrückt hat. Ein Schritt ohne Berechtigung schlägt fehl, die anderen laufen trotzdem.
- **Planen…** öffnet eine neue Regel, die die Szene an Tagen zu einer Uhrzeit startet (unten).
- Grenzen: 32 Szenen pro Mainframe (\`maxScenesPerMainframe\`), dieselbe Szene nie zweimal gleichzeitig, höchstens 4 Läufe gleichzeitig und 30 pro Minute und Mainframe. \`scenesEnabled = false\` in \`[scenes]\` schaltet Szenen ab.

## Zeitpläne {#schedules}

Zeitpläne sind Regeln mit einem **Zeit-Auslöser** – wähle ihn im Regel-Editor unter **When**:

| Auslöser | Läuft |
|---|---|
| alle … Minuten | Echtzeit, alle N Minuten (mindestens \`minIntervalMinutes\`, Standard 1) |
| an Tagen zu einer Uhrzeit | Echtzeit: an den gewählten Wochentagen um HH:MM (24 h) in der **Zeitzone des Servers** – der Editor zeigt sie (Einstellung \`timezone\`, leer = Systemzone des Servers) |
| eine Uhrzeit im Spiel | wenn die Weltuhr die Uhrzeit überschreitet: **Sonnenaufgang**, **Mittag**, **Sonnenuntergang**, **Mitternacht** oder jede Spielstunde |

- Mit der Aktion **Run a scene** startet ein Zeitplan viele Schritte. Eine geplante Szene läuft mit den Rechten ihres **Besitzers** (wer sie zuletzt gespeichert hat), nie mit PIN – Szenen, die die PIN brauchen, lassen sich deshalb nicht planen.
- Die Regelliste zeigt bei Echtzeit-Plänen **next run in …**.
- **Verpasste Läufe:** Nach einem Server-Neustart wird höchstens **ein** verpasster Lauf nachgeholt, und nur wenn er jünger als \`missedWindowHours\` (24 h) ist; \`runMissed = false\` holt nie nach. Die Spielzeit steht still, solange der Server aus ist – Spielzeit-Auslöser holen deshalb nie nach.
- **Nicht geladene Mainframes:** Ist ein Lauf fällig, während der Mainframe nicht geladen oder offline ist, wird er **übersprungen** – das Protokoll zeigt einmal pro Ausfall „skipped – mainframe unloaded“, und nichts wird später nachgeholt. Halte den Chunk des Mainframes geladen (Chunk-Anchor-Upgrade, FTB Chunks) für verlässliche Zeitpläne.
- Höchstens 16 Zeit-Regeln pro Mainframe (\`maxSchedulesPerMainframe\` in \`[schedules]\`).

## Push-Benachrichtigungen {#push}

Öffne **Notifications** in den Dashboard-Einstellungen. Bis zu 5 Ziele pro Mainframe:

- **Discord:** in den Kanaleinstellungen → Integrationen → Webhooks → Neuer Webhook → URL kopieren.
- **ntfy:** eine Topic-URL wie \`https://ntfy.sh/meine-basis-alarme\` (langen, schwer zu ratenden Namen wählen oder ein Token nutzen); dasselbe Topic in der ntfy-App abonnieren.
- **Webhook:** jeder https-Endpunkt; er bekommt einen JSON-POST (event, mainframe, title, text, level, time).

Wähle die Mindeststufe (alles, Warnungen und Fehler oder nur Fehler) und die Ereignisse: **alerts** (Skripte, Karten, Crafting, Regeln), **rules** (Läufe von Regeln mit „push every run“), **approvals** (ein Browser wartet auf Freigabe) und **power** (kein Strom / wieder da). **Send test** prüft ein Ziel; das Ergebnis steht daneben.

Links und Tokens liegen nur auf dem Server – das Dashboard zeigt sie maskiert. Der Server sendet nur an öffentliche https-Adressen (kein lokales Netz, keine Weiterleitungen), höchstens 30 Nachrichten pro Minute und Mainframe. Braucht das Recht „Push targets“ (Admins).

## Langzeit-Verlauf {#history}

Widgets, deren Skript einen Verlauf anfordert (\`history="24h"\` in den Widget-Optionen, oder \`"1h"\`, \`"7d"\`, \`"30d"\`), bekommen einen kleinen **Diagramm-Button**. Er öffnet den Verlauf mit Zeitraum-Wahl (1 h, 24 h, 7 Tage, 30 Tage), Min / Durchschnitt / Max und dem Wert unter Finger oder Maus; Lücken bedeuten, dass Mainframe oder Chunk nicht geladen waren.

\`\`\`lua
cloud.gauge("temp", "Core temperature", { min = 0, max = 1200, unit = "K", history = "7d" })
\`\`\`

Wie viel der Server aufbewahrt, hängt vom **Memory**-Upgrade ab: 1 h ohne, 24 h / 7 Tage / 30 Tage mit den Stufen. **Export CSV** lädt die Werte (Zeit, Wert) für Tabellen herunter – braucht das Recht „Use controls“.

## Produktionsstatistik {#production}

Die Seite **Produktion** (ein Tab neben Network, oder ⚙ → Produktion) zeigt, was jedes AE2- bzw. Refined-Storage-Netz hinter deinen Karten **dazubekommt, verbraucht und craftet** – pro Minute oder pro Stunde, über die letzte Stunde.

- Der Server misst jeden Speicher jede Minute (\`sampleSeconds\`) und behält ein Fenster von 60 Messwerten (\`windowSamples\`). Die ersten Zahlen kommen nach einer Minute; ein Server-Neustart beginnt das Fenster von vorn.
- Pro Speicher: dazu, verbraucht, netto und gecraftet, ein Diagramm und die **größten Zugänge und Abgänge** mit ihren Symbolen.
- Ein Klick auf ein Item – „was frisst mein Eisen?“ – zeigt seine Raten und sein Diagramm, seine Crafting-Aufträge, was gerade im Netz gecraftet wird, die letzten 24 Stunden (beobachtete und favorisierte Items) und wann der Vorrat bei diesem Tempo leer ist.
- **Was sie nicht sagen kann:** AE2 und Refined Storage melden nur, wie viel der Speicher dazubekommt und verliert – nicht, welche Maschine, Leitung oder welcher Spieler die Items genommen hat. Was zwischen zwei Messungen kommt und geht, hebt sich auf. **Gecraftet** zählt fertige Aufträge, die über die Bridge gestartet wurden (Dashboard, Lua, Auto-Lager, Regeln); am Terminal gestartete Aufträge sind nur sichtbar, solange sie laufen.
- **Energie:** jede Energiekarte mit gespeicherter Energie und rein/raus in FE/t, die Summen und ein Diagramm. Blöcke, deren Mod keine Raten meldet, zeigen die gemessene Änderung („gemessen“).
- **Als Karte anheften** legt die Rate eines Items, die Netto-Rate eines Speichers oder die Energiesummen als Diagramm auf die Karte **Production** – mit dem Layout-Editor anordnen; Tablets und Displays zeigen sie auch. Bis zu 16 Anheftungen; braucht „Edit the layout“ (und „Search storage“ für Items und Speicher).
- Speicherzahlen brauchen das Recht „Search storage“. \`productionEnabled = false\` in \`[production]\` schaltet die Statistik ab.

## Terminal {#terminal}

Der Button **>_** auf der Karte eines Computers öffnet sein **Terminal** im Browser: der Bildschirm live (Farben bei Advanced-Computern) und deine Tastatur, als stündest du davor. Auf den Bildschirm klicken und tippen; **Terminate** ist Strg+T, **Reboot**, **Shut down** und **Turn on** steuern den Computer. Einfügen schickt die erste Zeile der Zwischenablage. Auf dem Handy ins Feld unter dem Bildschirm tippen und **Enter** drücken.

Braucht das Recht „Terminal“ (Admins) und die Mainframe-PIN, falls eine gesetzt ist; das Öffnen wird protokolliert. Der Server-Admin kann es abschalten (\`terminalEnabled\`) oder Skripte und Terminal für einen Spieler verbieten (\`cccloud.feature.scripts\`).

## Lua-Editor {#editor}

Im **</>**-Fenster eines Computers zeigt **Open the editor** alle Dateien und Ordner (bis 8 Ebenen) neben einem Code-Editor mit Lua-Hervorhebung, Zeilennummern, Suche (Strg/⌘ + F) und Rückgängig. **Save** (Strg/⌘ + S) speichert; **Save & run** startet die Datei zusätzlich in einem neuen Tab auf dem Computer. **+ File**, **+ Folder**, Umbenennen/Verschieben (✎) und Löschen (×) arbeiten im Dateibaum.

Wurde die Datei seit dem Öffnen auf dem Computer geändert, fragt das Speichern vor dem Überschreiben. Dateien sind auf 256 KB begrenzt, \`rom\` und Disketten werden nie angefasst, und der Editor braucht dieselben Rechte wie das Hochladen von Skripten (plus die PIN, falls gesetzt).
`,
  mods: `# Unterstützte Mods

CC: Cloud zeigt Maschinen vieler Mods als fertige Karten – ganz ohne Lua. Setz einen **Cloud Uplink** auf die Maschine (oder daneben), verbinde ihn mit der Link Card, und ihre Karte erscheint im Dashboard. Alles ist optional: Eine Karte gibt es nur, wenn die Mod installiert ist. Geprüft wurde alles mit **All the Mods 11** (MC 26.1.2).

Daneben funktionieren zwei allgemeine Karten mit fast jeder Mod: die **Energy**-Karte für jeden Block, der FE speichert, und die **Tank**-Karte für einfache Flüssigkeitstanks. Pack-Ersteller können einzelne Blöcke mit dem Block-Tag \`#cccloud:no_generic_card\` ausschließen. AE2 und Refined Storage haben eigene [Bridges](/docs/bridges).

Bedienelemente folgen den [Rechten](/docs/accounts#roles): Buttons und Schalter brauchen „Use controls“, der Redstone-Modus einer Maschine „Switch redstone“, und Karten mit Koordinaten (Wegsteine, Tore, Quests) „See coordinates“.

## Nach Bereich {#list}

Die Beschreibungen in den Tabellen stammen aus dem Kompatibilitäts-Verzeichnis und sind auf Englisch.

${C.modsSection("de")}

Diese Seite wird aus dem Kompatibilitäts-Verzeichnis (docs/COMPAT.md im Quellcode) erzeugt und passt damit immer zur Mod.
`,
  recipes: `# Alle Rezepte

Alle Crafting-Rezepte von CC: Cloud, erzeugt aus den Rezeptdateien der Mod selbst – die Liste ist also immer vollständig. Manche Rezepte hängen von anderen Mods ab: Ein Hinweis sagt **Braucht …** (nur mit dieser Mod) oder **Nur ohne …** (das Ersatzrezept für Packs ohne sie). JEI oder EMI zeigen dasselbe im Spiel.

## Grundlagen {#basics}

${C.recipeSection("basics", "de")}

## Relays und Kabel {#network}

${C.recipeSection("network", "de")}

## Bridges {#bridges}

${C.recipeSection("bridges", "de")}

## Tablet, Display, Analyzer {#devices}

${C.recipeSection("devices", "de")}

## Redstone-Geräte {#redstone}

${C.recipeSection("redstone", "de")}

## Mainframe-Upgrades {#upgrades}

${C.recipeSection("upgrades", "de")}

## Sonstiges {#misc}

${C.recipeSection("misc", "de")}
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

## Craften im Dashboard {#crafting}

Ein craftbares Item antippen → **1 / 16 / 64 / eigene Menge** wählen → **Craft**. Der Auftrag erscheint unter den Crafting-Aufträgen mit Fortschrittsbalken. Fehler werden erklärt: kein Muster, alle CPUs belegt, fehlende Zutaten, Menge zu groß (Grenze \`maxCraftAmount\`, Standard 4096). Craften dürfen nur Geräte mit Steuerrechten.

## Merkliste {#watchlist}

Füge Items mit einer Mindestmenge hinzu (aus einem Suchergebnis oder per ID). Items unter ihrem Minimum stehen rot ganz oben und lösen pro Unterschreitung einmal eine **warn**-Meldung aus. Minimum ändern oder Items entfernen geht jederzeit. Die Merkliste wird in der Bridge gespeichert.

## Durchsuchen, Auto-Lager und Crafting-CPUs {#storage-v2}

- **Browse** (neben der Suche): alle Items als Raster mit Icons – sortiert nach Menge, Name oder Mod, mit einem Tab **Craftable**, **Fluids** und ★ **Favoriten** oben angepinnt. Weiter scrollen lädt mehr.
- **Item-Details:** Menge, ein 24-Stunden-Verlauf, letzte Aufträge, ★, **Craft…** und **Keep in stock…**.
- **Crafting-Vorschau:** Bevor ein Auftrag startet, siehst du, was aus dem Lager genommen, was gecraftet wird und was fehlt, dazu freie CPUs. Mit **Notify me** bekommst du eine Meldung, wenn er fertig ist oder fehlschlägt.
- **Auf Lager halten (Auto-Lager):** Minimum und Ziel festlegen – fällt die Menge unter das Minimum und es gibt ein Muster, craftet der Server in Paketen zu 64 bis zum Ziel (ein Auftrag pro Item, höchstens alle 30 s). Das Abzeichen zeigt den Zustand: crafting, waiting for a CPU, missing resources oder nach 3 Fehlschlägen pausiert. Solange alle Crafting-CPUs belegt sind, hat das Item eine Zeile „Waiting for CPU“ in den Crafting-Aufträgen – es wird alle 30 s neu versucht und zählt nie als Fehlschlag. Läuft auf dem Server – kein Computer nötig. Aus Lua: \`bridge.setAutoStock(name, min, target, enabled)\`.
- **CPUs & Aufträge:** jede Crafting-CPU mit ihrem aktuellen Auftrag, alle laufenden Aufträge und **Cancel** (Operatoren).
- **Produktion:** was das Netz pro Minute dazubekommt und verbraucht und was ein Item frisst – siehe [Produktionsstatistik](/docs/automation#production).

## Aus Lua {#lua}

${code("lua", C.LUA_BRIDGE)}

Eine eigene Lager-Karte mit denselben Widgets:

${code("lua", C.LUA_BRIDGE_WIDGETS)}

Oder einfach \`cloud-storage\` starten. Alle Methoden: [Lua-Referenz → Bridges](/docs/lua#bridges).
`,

  accounts: `# Konten, Rollen und Teilen

Ein **Gerät** ist jeder Browser – auf PC, Tablet oder Handy. Jede Seite des Dashboards funktioniert überall gleich, mit Maus, Tastatur oder Touch.

## Spieler verknüpfen {#link}

1. Tippe in Minecraft \`/cccloud link\` (oder nutze „Link this browser“ im Mainframe-Bildschirm). Du bekommst einen Einmal-Code (10 Minuten).
2. Dashboard öffnen, Code und einen Namen für diesen Browser eingeben, **Pair device** anklicken.
3. Im Chat auf **[Allow]** klicken.

Der Browser ist jetzt mit **deinem Spieler** verknüpft, nicht mit einem einzelnen Mainframe: Er zeigt jeden Mainframe, auf dem du eine Rolle hast, und du wechselst über den Mainframe-Namen oben in der Kopfzeile. Browser, die vorher mit einem Mainframe-Code gekoppelt wurden, funktionieren für diesen einen Mainframe weiter.

## Rollen {#roles}

| Rolle | Darf |
|---|---|
| viewer | Werte und Meldungen sehen – keine Bedienung, keine Koordinaten |
| operator | viewer + Koordinaten, Buttons, Schalter, Schieberegler, Redstone, Craften, Regeln ein-/ausschalten |
| admin | alles: Skripte, Terminal, Layout bearbeiten, Regeln, Geräte, Zugriff, Freigabe-Links, PIN |
| owner | der Spieler, der den Mainframe aufgestellt hat: admin + übertragen und löschen |

Rollen sind **Vorlagen**. Jedem Spieler und jedem Team-Rang kannst du einzelne Rechte dazugeben oder wegnehmen (**Custom** auf der Seite Access) – etwa ein operator, der auch das Layout bearbeiten darf, oder ein admin ohne Skript-Upload. Wegnehmen gewinnt gegen Dazugeben. Ohne „View the dashboard“ hat ein Spieler gar keinen Zugriff. Du kannst nur Rechte vergeben, die du selbst hast (der Besitzer kann alle vergeben). Ein Server kann Skripte und Terminal mit dem Permission-Node \`cccloud.feature.scripts\` für alle abschalten.

Rollen kommen aus mehreren Quellen – **die höchste gilt**: der Besitzer, einem Spieler gegebene Rollen (\`/cccloud role <spieler> <rolle>\`, Mainframe-Bildschirm oder Seite **Access**), Team-Ränge und Server-Rechte. Was du nicht darfst, ist ausgeblendet oder deaktiviert, mit Begründung.

## Teams {#teams}

Ein Mainframe kann einem Team aus **FTB Teams**, **Open Parties and Claims** oder einem Vanilla-Scoreboard-Team zugeordnet werden. Jeder Team-Rang bekommt eine Rolle auf diesem Mainframe (Standard: owner/officer/leader → admin, member → operator, ally → viewer). Tritt jemand ein oder aus, ändert sich sein Zugriff sofort.

## Die Seite Access {#access}

Admins öffnen sie über das Mainframe-Menü in der Kopfzeile (**Access & roles**):

- die Liste der Spieler mit Rolle und Herkunft der Rolle
- direkt vergebene Rollen ändern oder entfernen, Spieler per Name hinzufügen
- ein Team zuordnen und die Rolle pro Team-Rang wählen
- **Custom** neben einer Rolle: einzelne Rechte dazugeben oder wegnehmen, gruppiert (Ansehen, Bedienung, Lager, Skripte & Terminal, Layout & Automatisierung, Verwaltung), jeweils mit kurzer Erklärung; der Button zeigt, wie viele von der Vorlage abweichen (z. B. „−2“)

Wer die Seite öffnen darf, ist ebenfalls ein Recht („Manage access“); Freigabe-Links brauchen „Share links“.

## Geräte {#devices}

**Devices** im Kopfzeilen-Menü listet jeden Browser, der mit deinem Spieler verknüpft ist – Name, Typ (Computer, Tablet, Handy), letzte Adresse und letzte Nutzung. Ein verlorenes oder altes Gerät dort entfernen, oder im Spiel mit \`/cccloud devices\` und \`/cccloud revoke\`.

## Freigabe-Links {#shares}

Admins können eine **schreibgeschützte** Ansicht teilen – für ein Stream-Overlay, einen Freund oder ein Wand-Display:

1. Mainframe-Menü → **Share links** → **New link**.
2. Ganze Seiten oder einzelne Karten wählen, die Laufzeit (1 Stunde, 24 Stunden, 7 Tage oder unbegrenzt), wie viele gleichzeitig zusehen dürfen, und optional **genaue Zahlen ausblenden** (nur Anzeigen).
3. Link kopieren – er wird nur einmal angezeigt.

Der geheime Teil hinter \`#\` landet nie in den Server-Logs und verschwindet aus der Adresszeile, sobald die Seite offen ist. Der Browser hält ihn nur im Speicher – nach einem Neuladen oder aus einem Lesezeichen den vollständigen Link erneut öffnen. Wer den Link hat, sieht **„Shared by …“** und kann nichts drücken: Es gibt gar keine Bedienelemente, und der Server lehnt jede Aktion über einen Freigabe-Link ab. Nie enthalten: Koordinaten, Item-Suche, Netzwerkkarte und Verlaufs-Export. Ein Widerruf trennt alle Zuschauer sofort.

## Server-Rechte {#permissions}

Server-Admins steuern CC: Cloud mit **LuckPerms** oder **FTB Ranks** (oder über das OP-Level, wenn kein Rechte-Mod installiert ist):

| Node | Standard | Erlaubt |
|---|---|---|
| \`cccloud.use\` | alle | Blöcke aufstellen und nutzen, Browser verknüpfen |
| \`cccloud.admin.view\` | OP | jeden Mainframe ansehen |
| \`cccloud.admin.operate\` | OP | jeden Mainframe bedienen |
| \`cccloud.admin.manage\` | OP | Geräte und Rollen jedes Mainframes verwalten |
| \`cccloud.limit.mainframes\` | 3 | Mainframes pro Spieler |

Schaut ein Admin auf einen fremden Mainframe, sieht der Besitzer das im Protokoll.

## Tastatur {#keyboard}

Tab springt durch alles, Enter oder Leertaste löst aus, Escape schließt Dialoge und Menüs, die Pfeiltasten wechseln zwischen Seiten-Tabs und Menüeinträgen, und „Skip to content“ springt an der Kopfzeile vorbei.
`,
  // -------------------------------------------------------------- security
  api: `# Öffentliche API

Mit der öffentlichen API können **deine eigenen Programme** Werte lesen und Knöpfe drücken: ein Discord-Bot, Home Assistant, ein Tabellen-Skript. Es ist eine kleine HTTP-JSON-API unter der Adresse des Dashboards, mit **API-Schlüsseln**, die du im Dashboard anlegst.

> **Hinweis:** Die API ist **standardmäßig aus**. Ein Server-Admin schaltet sie ein; bis dahin gibt es die Routen nicht.

## Einschalten {#enable}

In der \`serverconfig/cccloud-server.toml\` der Welt (beim dedizierten Server: \`world/serverconfig/\`), Abschnitt \`[api]\`, danach den Server neu starten:

${code("toml", C.TOML_API)}

Spieler brauchen auf dem Mainframe die Berechtigung **API keys** (Admins und Besitzer haben sie; siehe [Rollen](/docs/accounts#roles)). Server-Betreiber können Schlüssel für einen Spieler mit dem Berechtigungsknoten \`cccloud.api.keys\` verbieten – seine Schlüssel funktionieren dann auch nicht mehr.

## Schlüssel anlegen {#keys}

Dashboard → ⚙ → **API-Schlüssel** → **Neuer Schlüssel**:

1. Ein **Name** (ein Schlüssel pro Programm – dann kannst du ihn einzeln widerrufen).
2. Die **Bereiche** (Scopes) – was der Schlüssel darf (unten).
3. Ein **Ablauf**: 1 Stunde, 24 Stunden, 7, 30 oder 90 Tage oder nie.
4. Optional: die **IP-Adressen**, von denen er genutzt werden darf (bis 8, z. B. \`192.168.1.0/24\`).

Der Schlüssel (\`cck_…\`) wird **genau einmal** angezeigt – kopiere ihn dann. Der Server behält nur einen Fingerabdruck (Hash). Die Liste zeigt jeden Schlüssel mit Bereichen, Erstellung, Ablauf, letzter Nutzung und IP; **Widerrufen** stoppt einen Schlüssel sofort. Spieler mit „Manage access“ sehen und widerrufen alle Schlüssel des Mainframes.

## Bereiche {#scopes}

Eine Anfrage braucht den Bereich **und** die aktuelle Berechtigung des Schlüssel-Besitzers – ein Schlüssel kann nie mehr als sein Spieler.

| Bereich | Erlaubt | Braucht |
|---|---|---|
| \`read:sections\` | Karten, Widgets und Werte lesen; der Live-Ereignisstrom | View |
| \`read:storage\` | Speicherkarten durchsuchen, Itemmengen | Search storage |
| \`read:stats\` | Produktionsstatistik | View (Speicher: Search storage) |
| \`action:widgets\` | Knöpfe drücken, Schalter, Regler, Auswahl, Zahlen und Texte setzen | Use controls (Redstone-Widgets: Switch redstone) |
| \`run:scenes\` | Szenen auflisten und starten | Use controls und die Berechtigung jedes Schritts |

## Routen {#routes}

Alles JSON, \`Authorization: Bearer cck_…\` bei jeder Anfrage. \`<id>\` ist der Mainframe des Schlüssels (steht im Dashboard unter ⚙ → This mainframe).

| Route | Bereich |
|---|---|
| \`GET /api/v1/me\` | jeder – der Schlüssel, sein Spieler und Mainframe |
| \`GET /api/v1/mainframes/<id>/sections\` | read:sections |
| \`GET /api/v1/mainframes/<id>/sections/<sectionId>\` | read:sections |
| \`POST /api/v1/mainframes/<id>/sections/<sectionId>/widgets/<widgetId>\` mit \`{"value": …}\` | action:widgets |
| \`GET /api/v1/mainframes/<id>/storage/<sectionId>/items?q=iron&limit=20\` | read:storage |
| \`GET /api/v1/mainframes/<id>/production\` | read:stats |
| \`GET /api/v1/mainframes/<id>/scenes\`, \`POST …/scenes/<sceneId>/run\` | run:scenes |
| \`GET /api/v1/mainframes/<id>/events\` (Server-Sent Events) | read:sections |
| \`GET /api/v1/openapi.json\` | öffentlich – die OpenAPI-Beschreibung |

## Grenzen {#limits}

- 60 Anfragen pro Minute und Schlüssel und 120 pro IP-Adresse (\`apiRequestsPerMinute\`, \`apiIpRequestsPerMinute\`); darüber antwortet der Server mit \`429 rate_limited\` und einem \`Retry-After\`-Header. Item-Suchen: 5 pro Sekunde und Schlüssel.
- Anfragen bis 2 KB (\`apiMaxBody\`), bis zu 5 Schlüssel pro Spieler (\`apiKeysPerPlayer\`).
- Ereignisströme: 2 pro Schlüssel (\`apiStreamsPerKey\`), 32 insgesamt; alle 20 s hält eine Kommentarzeile sie offen, langsame Leser werden getrennt.

## Sicherheit {#security}

- **Keine PIN in der API, mit Absicht:** PIN-geschützte Bedienelemente und Szenen antworten mit \`403 pin_required\` – ein verlorener Schlüssel kommt nie an den zweiten Faktor. Admin-Seiten, Zugriff, Geräte, Kopplung, Schlüsselverwaltung, Terminal, Skripte, Layout, Regeln, Push und die Karte sind mit einem Schlüssel nie erreichbar.
- Schlüssel werden **einmal** gezeigt und nur als Hash gespeichert. Bewahre sie in einer Geheimnis-Datei auf, nie in einer URL – ein Schlüssel im Query-String (\`?key=\`) wird mit \`400 key_in_query\` abgelehnt.
- Wähle **wenige Bereiche**, einen **Ablauf** und wo möglich eine **IP-Liste**; **widerrufe** Schlüssel, die du nicht mehr brauchst oder die bekannt geworden sein könnten.
- Die API folgt den Regeln des Dashboards: dieselben Host- und Nur-LAN-Einstellungen, IP-Sperren und Verbindungsgrenzen. Nutze HTTPS, wenn sie aus dem Internet erreichbar ist ([Zugriff aus dem Internet](/docs/security#internet)). Jede Widget-Aktion und jeder Szenenlauf landet im Audit-Protokoll des Mainframes („API key 'bot' (Steve) …“).
- Browser-Programme auf anderen Seiten brauchen ihren genauen Ursprung in \`apiCorsOrigins\`; sonst können Browser die API nicht aufrufen (Server und Bots betrifft das nicht).

## Beispiele mit curl {#curl}

${code("bash", C.API_CURL)}

Fehler sind JSON wie \`{"error":"pin_required"}\`: \`401 unauthorized\` / \`expired\` (Schlüssel), \`403 no_access\` (der Spieler hat keinen Zugriff mehr), \`ip_not_allowed\`, \`pin_required\`, \`404 not_found\`, \`429 rate_limited\`.

## Home Assistant {#home-assistant}

Ein **REST-Sensor** liest ein Widget, ein **rest_command** startet eine Szene (in Automationen oder als Dashboard-Knopf). Der Schlüssel braucht \`read:sections\` und \`run:scenes\`.

${code("yaml", C.API_HA)}

## Ein kleiner Discord-Bot {#discord}

\`!temp\` antwortet mit der Reaktortemperatur, \`!night\` startet die Szene „Nachtmodus“. Er braucht Node 18 oder neuer, einen Discord-Bot mit dem Intent **Message Content** (Discord Developer Portal → Bot) und einen Schlüssel mit \`read:sections\` und \`run:scenes\`. Die IDs deiner Karten und Szenen listest du einmal mit curl auf (\`/sections\`, \`/scenes\`).

${code("js", C.API_DISCORD)}
`,

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
- Das Gerät mit dem Browser und der Minecraft-Server müssen im selben Netzwerk sein (z. B. im selben WLAN, nicht mobile Daten) – oder siehe [Zugriff aus dem Internet](/docs/security#internet).
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

## Meine Geräte gehen offline, wenn ich weggehe {#unloaded}

Minecraft lässt nur die Chunks in der Nähe von Spielern laufen. Gehst du weg, wird der Chunk eines Computers, Uplinks oder einer Bridge entladen – er steht still, und CC: Cloud erreicht ihn nicht. Das Dashboard behält seine Karte **ausgegraut mit den letzten Werten** und zeigt „last seen 12 min ago – chunk not loaded“; die Bedienelemente sind gesperrt, bis der Chunk wieder geladen ist. CC: Cloud lädt nie selbst Chunks.

Damit eine Basis weiterläuft, während du weg bist, halte ihre Chunks geladen: mit FTB Chunks **force-loaden** (claimen, dann in der Karte force-load), mit einem Chunk-Loader aus einer anderen Mod, oder über die Spawn-Chunks des Servers. Dann wird die Karte von selbst wieder lebendig.

## Falsche PIN / gesperrt {#pin-locked}

Nach 5 falschen PINs ist das Gerät 5 Minuten gesperrt. Der Besitzer kann die PIN mit \`/cccloud pin set\` ändern.
`,
};

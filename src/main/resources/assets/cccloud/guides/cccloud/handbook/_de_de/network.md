---
# Aus der Dashboard-Doku erzeugt (tools/gen_handbook.ts) – Änderungen in cloud/web/src/docs/content machen.
navigation:
  title: "Netzwerk, Relays & Reichweite"
  position: 6
  icon: cccloud:cloud_relay
---

# Netzwerk, Relays und Reichweite

Jeder Uplink und jede Bridge verbindet sich mit dem **nächsten erreichbaren Knoten** – dem Mainframe oder einem Relay – und das Netzwerk leitet ihn über den besten Weg zum Mainframe (wenigste Sprünge, dann bestes Signal; höchstens 8 Sprünge). Wie ein WLAN-Mesh: Mit Relays erreichst du weit entfernte Basen.

Ein Rechtsklick auf Uplink, Bridge oder Relay zeigt, womit der Block verbunden ist, welche Karte er ins Dashboard stellt (als Live-Vorschau), sein Signal und – wenn etwas nicht stimmt – eine Zeile, wie man es behebt. **Im Dashboard zeigen** schickt einen Chat-Link, der genau diese Karte öffnet; **Umbenennen** ändert den Titel der Karte für alle.

## Reichweiten

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

## Signal

Signal = 1 − Entfernung / Reichweite, angezeigt als 0–100 % und Balken. Unter 15 % ist eine Verbindung **schwach**: Sie funktioniert, wird aber gelb angezeigt – rück das Gerät näher heran oder setz ein Relay.

## Relays

- **Cloud Relay** – ein Mast für mittlere Entfernungen.
- **Advanced Cloud Relay** – größere Reichweite für Knoten und Geräte.
- **Ender Relay** – verbindet sich mit anderen Ender Relays über jede Entfernung und **dimensionsübergreifend** (z. B. in den Nether). Ender Relays mit der Link Card zu einer Gruppe verbinden.

Relay aufstellen und wie jedes Gerät verbinden: Link Card auf den Mainframe, dann auf das Relay.

### Cloud Relay
<RecipeFor id="cccloud:cloud_relay" fallbackText="Dieses Rezept gibt es in diesem Modpack nicht (eine benötigte Mod fehlt oder das Pack hat es geändert)." />

### Advanced Cloud Relay
<RecipeFor id="cccloud:advanced_cloud_relay" fallbackText="Dieses Rezept gibt es in diesem Modpack nicht (eine benötigte Mod fehlt oder das Pack hat es geändert)." />

### Ender Relay
<RecipeFor id="cccloud:ender_relay" fallbackText="Dieses Rezept gibt es in diesem Modpack nicht (eine benötigte Mod fehlt oder das Pack hat es geändert)." />

> **Tipp:** Klappt das Verbinden nicht, nennt die Meldung den nächsten Knoten und seine Entfernung. Setz ein Relay dazwischen.

## Kabel (kabelgebundene Verbindungen)

Geräte können sich auch **per Kabel** verbinden, wie bei AE2: Uplink oder Bridge mit CC: Tweakeds **Networking Cable** und **Wired Modems** an den Mainframe oder ein Relay anschließen (Modems per Rechtsklick einschalten). Alles im selben CC-Kabelnetz ist verkabeltes Mitglied.

- Kabel haben **keine Reichweitengrenze**, immer **100 %** Signal und kosten **kein zusätzliches FE pro Sprung**.
- Ein Gerät kann Kabel und Funk gleichzeitig haben; das Kabel wird bevorzugt, Funk ist die Reserve.
- Im Dashboard sind Kabel **durchgezogene Linien** (grau = CC-Kabel, leuchtend cyan = Glasfaser), Funkverbindungen **gestrichelt** (nach Signal gefärbt), Ender-Verbindungen **gepunktet**.

**Fiber Cable** (Glasfaser, Teil von CC: Cloud) funktioniert genauso, verbindet aber nur unsere Geräte – ein sauberes eigenes Netz. Geräte an Glasfaser zählen nur **halb** für die Gerätekapazität des Mainframes und zahlen **halbes** FE. Mit der **Fiber Junction** verzweigst du und gehst durch Wände, ein **Ender Fiber Link**-Paar verbindet zwei Glasfasernetze über jede Entfernung und Dimension.

## Wenn ein Relay ausfällt

Ist ein Relay abgebaut, nicht geladen oder ohne Strom, wird alles dahinter **umgeleitet**, sofern es einen anderen Weg gibt. Sonst zeigen diese Geräte **No route**. Relays laden nie Chunks zwangsweise; Teile in nicht geladenen Chunks zeigen **Unloaded**.

Geräte, die vor v0.4 verbunden wurden und nach den neuen Regeln außer Reichweite sind, funktionieren weiter und zeigen **Out of range (legacy)**, bis du in der Nähe ein Relay aufstellst oder sie neu verbindest.

## Strom

Das Netzwerk kostet FE. Der Mainframe braucht 40 FE/t + 4 pro Uplink + 8 pro Advanced Uplink + 10 pro Bridge + 2 pro verbundenem Dashboard + 1 pro zusätzlichem Sprung jedes Geräts. Relays brauchen eigenes FE: Cloud Relay 20 FE/t, Advanced 60 FE/t, Ender Relay 120 FE/t. Ein Klick auf die Stromanzeige oben im Dashboard zeigt die Aufschlüsselung.

Die Aufschlüsselung zeigt auch Geräte an Glasfaser (sie zahlen die Hälfte), den **Chunk Anchor**, falls eingebaut, und ein **Efficiency**-Upgrade als grüne Zeile mit dem eingesparten FE. Darunter zeigt **Device capacity**, wie viele Geräte der Mainframe trägt und wie viele seine Upgrade-Karten erlauben; dieselbe Zahl steht als Chip im Network-Tab.

## Die Netzwerkkarte

Der Tab **Network** zeigt alles, was verbunden ist, in drei Ansichten:

- **Topology** – der Mainframe als Wurzel, Relays als Äste, Geräte als Blätter. Kabel sind durchgezogen, Funkverbindungen gestrichelt und nach Signal gefärbt (grün stark, cyan ok, gelb schwach), Ender-Verbindungen lila gepunktet, Legacy orange; der Punkt zeigt den Status. Geräte ohne Weg stehen unter **Not connected**.
- **Map** – eine Draufsicht (x/z) pro Dimension auf einer echten Karte der Welt: **Surface** (wie eine Minecraft-Karte) oder **Slice** – ein Schnitt in einer Höhe, die du mit dem Y-Regler wählst, damit eine Untergrundbasis auf y −60 ihre Räume und Höhlen zeigt (dunkler = tiefer). Gezeigt werden nur Orte in der Nähe deines Netzwerks und nur Chunks, die geladen waren. Mit Koordinatengitter, Reichweitenkreisen (umschaltbar; durchgezogen = Knoten-Reichweite, gestrichelt = Geräte-Reichweite), Verbindungen und Statuspunkten. Ziehen zum Verschieben, Scrollen oder Zwei-Finger-Zoom, ⤢ zeigt alles.
- **3D** – siehe unten.

Mit FTB Chunks und Waystones zeigt die Karte auch die **geclaimten Chunks** deines Teams (Teamfarbe, zwangsgeladene gestrichelt) und deine **Wegsteine** (lila Rauten, globale gold) – ein- und ausschaltbar mit **Claims** und **Waystones**. Wegsteine sind auch in der 3D-Ansicht markiert. Wie alle Koordinaten werden sie nur an Spieler geschickt, die Positionen sehen dürfen.

Ein Klick auf einen Knoten zeigt Details mit Untermenüs: **Info** (Typ, Position, Dimension, Status, Signal oder Kabel, Reichweite), **Route** (jeder Sprung bis zum Mainframe und was ein Relay weiterleitet), **Power** (Verbrauch und Puffer) und **Card** (die Live-Karte des Geräts, Bedienung je nach Rolle). **Open card** springt zur Karte im Dashboard; **Unlink** entfernt ihn aus dem Netzwerk (Admins). Positionen sehen nur Browser, die den Mainframe steuern dürfen.

## 3D-Ansicht

Die Ansicht **3D** zeigt deine Basis mit den echten Blockmodellen an ihren echten Koordinaten: Verbindungen als Linien (Kabel durchgezogen, Funk gestrichelt, Ender gepunktet), Reichweiten als durchscheinende Kugeln (Schalter **Ranges**) und – wenn der Server es schickt – das grobe Gelände um deine Blöcke (Schalter **Terrain**; nur geladene Chunks, schwarze Löcher sind nicht geladene Bereiche).

- **Maus:** ziehen zum Drehen, Rechtsklick- oder Shift-Ziehen zum Verschieben, Mausrad zum Zoomen. **Touch:** ein Finger dreht, zwei Finger zoomen und verschieben. **Tastatur:** Ansicht fokussieren, Pfeiltasten verschieben, + und − zoomen, Esc hebt die Auswahl auf. ⤢ zeigt wieder alles.
- Ein Klick auf einen Block zeigt seine Details. Ein Klick auf ein **Relay** (oder den Mainframe) hebt alles hervor, was darüber läuft, und blendet den Rest ab.
- **Fly to…** findet einen Block über den Namen und fliegt die Kamera hin; die Dimensions-Tabs wechseln zwischen Overworld, Nether und weiteren.
- **Blocks:** die Welt um dein Netzwerk als Voxel – echtes Gelände, Höhlen und deine Basis, eingefärbt wie eine Minecraft-Karte, mit weichen Schatten in Ecken, leuchtenden Lampen und durchsichtigem Wasser. Gesendet werden nur Blöcke, die Luft berühren (kein Röntgenblick auf Erze), nur im Umkreis von etwa 40 Blöcken um deine Geräte und nur aus geladenen Chunks. **Cut** entfernt alles oberhalb einer Höhe (Regler), damit du in eine Untergrundbasis schauen kannst; **Night** dunkelt die Welt ab und lässt die Lampen leuchten.
- Aus der Ferne werden Blöcke größer gezeichnet, damit die Basis lesbar bleibt; der farbige Punkt über jedem Block ist sein Status.
- 3D braucht WebGL. Ohne WebGL (abgeschaltet, sehr alte Grafik) zeigt das Dashboard stattdessen die 2D-Karte. Die 3D-Ansicht wird erst geladen, wenn du sie öffnest – der Rest des Dashboards bleibt schnell.

Aus Lua liefert `cloud_mainframe.getNetwork()` dieselben Knoten, Verbindungen und Wege.


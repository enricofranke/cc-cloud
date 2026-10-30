---
# Aus der Dashboard-Doku erzeugt (tools/gen_handbook.ts) – Änderungen in cloud/web/src/docs/content machen.
navigation:
  title: "Erste Schritte"
  position: 0
  icon: cccloud:cloud_mainframe
---

# Erste Schritte

CC: Cloud bringt deine ComputerCraft-Computer und dein Lager auf ein Dashboard in jedem Browser – PC, Tablet oder Handy. Der Minecraft-Server stellt das Dashboard selbst bereit – kein externer Dienst, keine Registrierung.

> **Tipp:** Der schnellste Weg: Cloud Mainframe craften, mit Strom versorgen, einen Cloud Uplink an einen Computer setzen, beide mit einer Cloud Link Card verbinden und den Browser koppeln. Der Computer erscheint automatisch auf dem Dashboard – ganz ohne Code.
>
> Du willst nur eine Lampe schalten? Eine **Cloud-Lampe** setzen (oder einen Cloud-Schalter neben deine Redstone-Lampen) und verbinden – sie erscheint als Schalter im Dashboard. Siehe [Redstone-Geräte](redstone.md).

## Die Blöcke

| Block / Item | Funktion |
|---|---|
| **Cloud Mainframe** | Das Herz einer Basis. Zwei Blöcke hoch. Stellt die Verbindung zum Dashboard her, zeigt den Kopplungscode und verwaltet Geräte. Einer pro Basis oder Team. Braucht FE-Strom. |
| **Cloud Uplink** | Haftet wie ein Modem an einer Blockseite; an niedrigen oder kleinen Seiten (Stufen, Solarpanels) wird er zum kompakten Modul; oben oder unten braucht er eine volle Fläche. An einem Computer wird er zur Karte dieses Computers im Dashboard. Reichweite 128 Blöcke, gleiche Dimension. |
| **Advanced Cloud Uplink** | Genauso, aber mit unbegrenzter Reichweite und dimensionsübergreifend. |
| **Cloud-Sonde** | Liest **eine** Maschine bis zu 8 Blöcke entfernt, als säße ein Uplink daran – für eingebaute Maschinen oder wenn jede Seite mit Rohren und Kabeln belegt ist. Teuer; zählt wie zwei Geräte. Siehe *Maschinen ohne freie Seite* weiter unten. |
| **Cloud Link Card** | Verbindet Uplinks, Bridges und Redstone-Geräte mit einem Mainframe. |
| **Cloud-Schalter / -Taster / -Lampe / -Sensor** | Kleine [Redstone-Geräte](redstone.md) ohne Computer: verbinden und Lampen, Türen und Maschinen im Dashboard schalten. Geräte mit derselben Gruppe teilen sich eine Karte. |
| **ME / RS Cloud Bridge** | Hängt sich in ein Applied-Energistics-2- oder Refined-Storage-Netzwerk. Mit einem Uplink darauf bekommst du eine fertige Lager-Karte (Suche, Craften, Merkliste). Gibt es nur, wenn AE2 / RS installiert ist. |
| **Cloud Core** | Crafting-Zutat. |

## Crafting-Rezepte

Zutaten mit „(beliebig)“ akzeptieren jede Variante aus dem Tag (z. B. jeden Goldbarren). JEI/EMI zeigen im Spiel dieselben Rezepte.

### Cloud Core
<RecipeFor id="cccloud:cloud_core" fallbackText="Dieses Rezept gibt es in diesem Modpack nicht (eine benötigte Mod fehlt oder das Pack hat es geändert)." />

### Cloud Mainframe
<RecipeFor id="cccloud:cloud_mainframe" fallbackText="Dieses Rezept gibt es in diesem Modpack nicht (eine benötigte Mod fehlt oder das Pack hat es geändert)." />

### Cloud Uplink
<RecipeFor id="cccloud:cloud_uplink" fallbackText="Dieses Rezept gibt es in diesem Modpack nicht (eine benötigte Mod fehlt oder das Pack hat es geändert)." />

### Advanced Cloud Uplink
<RecipeFor id="cccloud:advanced_cloud_uplink" fallbackText="Dieses Rezept gibt es in diesem Modpack nicht (eine benötigte Mod fehlt oder das Pack hat es geändert)." />

### Cloud-Sonde
<RecipeFor id="cccloud:cloud_probe" fallbackText="Dieses Rezept gibt es in diesem Modpack nicht (eine benötigte Mod fehlt oder das Pack hat es geändert)." />
Ohne Allthemodium ersetzen 4 Diamanten und 2 Netheritschrott die Vibranium-Barren (alle Rezepte: JEI/EMI oder die Rezeptseite).

### Cloud Link Card
<RecipeFor id="cccloud:cloud_link_card" fallbackText="Dieses Rezept gibt es in diesem Modpack nicht (eine benötigte Mod fehlt oder das Pack hat es geändert)." />

### ME- und RS Cloud Bridge
<RecipeFor id="cccloud:me_cloud_bridge" fallbackText="Dieses Rezept gibt es in diesem Modpack nicht (eine benötigte Mod fehlt oder das Pack hat es geändert)." />
<RecipeFor id="cccloud:rs_cloud_bridge" fallbackText="Dieses Rezept gibt es in diesem Modpack nicht (eine benötigte Mod fehlt oder das Pack hat es geändert)." />

## Mainframe aufstellen

1. Stelle den **Cloud Mainframe** auf (er ist zwei Blöcke hoch). Wer ihn platziert, ist sein **Besitzer**.
2. Rechtsklick darauf: Der Bildschirm zeigt Status, Dashboard-Adresse (URL) und einen Kopplungscode.
3. Schalte dort das Dashboard **ein** (oder Schleichen + Rechtsklick mit leerer Hand).

Ein Redstone-Signal an einer der beiden Hälften ist ein **Not-Aus**: Der Mainframe geht offline, bis das Signal weg ist.

### Mainframe abbauen und umziehen

Der Mainframe trägt seine Identität im Item. Abbauen (Spitzhacke) und das Item wieder platzieren – egal wo, auch in einer anderen Basis – ergibt **denselben Mainframe**: Geräte, Konten, PIN, Layout, Regeln, Szenen und API-Schlüssel sind wieder da, verknüpfte Uplinks und Bridges verbinden sich von selbst. Er gehört weiter seinem Besitzer, egal wer ihn platziert. Upgrade-Karten fallen als eigene Items heraus; setze sie wieder ein.

- **Nichts wird sofort gelöscht.** Nach dem Abbauen behält der Server alles **7 Tage** lang (`mainframeRetentionDays`); das Dashboard zeigt den Mainframe solange als **Removed**. Erst danach oder mit `/cccloud mainframe purge <id>` werden seine Daten gelöscht.
- **Item verloren?** Im Kreativmodus fällt beim Abbauen nichts heraus: `/cccloud mainframe recover` listet deine abgebauten Mainframes und gibt dir ihr Item (einmal pro Abbau). Ist das Item gedroppt, platziere dieses – oder frag einen Server-Admin, der kann immer wiederherstellen.
- **Schutz vor Fehlklicks** (`mainframeBreakProtection`): Im Kreativmodus lässt sich der Mainframe nur **schleichend** abbauen; im Überlebensmodus nur von seinem Besitzer, seinen Admins und Server-Admins. Claim-Mods schützen ihn zusätzlich.

## Strom (FE)

Der Mainframe läuft mit **FE** (Forge Energy, auch RF genannt) – der Einheit von Powah, Ender IO, Thermal, AE2 und den meisten Technik-Mods. Schließe ein beliebiges FE-Kabel oder einen Generator an eine der beiden Hälften an.

| | Standard |
|---|---|
| Grundverbrauch | 40 FE/t |
| Pro Uplink / Advanced Uplink / Bridge | +4 / +8 / +10 FE/t |
| Pro verbundenem Dashboard | +2 FE/t |
| Pro zusätzlichem Sprung eines Geräts | +1 FE/t |
| Puffer | 200.000 FE |
| Max. Eingang | 2.000 FE/t |

Ohne Strom geht der Mainframe offline (nach 5 Sekunden mit leerem Puffer) und das Dashboard zeigt **„Mainframe has no power“**. Sobald der Puffer wieder voll genug ist, verbindet er sich von selbst. Uplinks und Bridges brauchen kein eigenes FE, [Relays](network.md) schon. Server-Admins können die Strompflicht abschalten (`requirePower = false`).

## Uplinks und Bridges verbinden

1. Setze einen **Cloud Uplink** an eine Seite eines Computers (oder auf eine ME / RS Cloud Bridge).
2. Rechtsklick mit der **Cloud Link Card** auf den **Mainframe** – auf der Karte steht jetzt „Linked to Mainframe @ x y z“.
3. Rechtsklick mit der Karte auf den **Uplink** (oder die Bridge). Fertig – die Statusleuchte wird grün.

Schleichen + Rechtsklick in die Luft löscht die Karte. Nur Besitzer und vertraute Spieler dürfen verbinden.

### Maschinen ohne freie Seite: Cloud-Sonde

Ein Uplink liest den Block, an dem er sitzt. Ist eine Maschine in eine Wand gebaut oder jede Seite mit Rohren und Kabeln belegt, nimm eine **Cloud-Sonde** im Umkreis von 8 Blöcken (gleiche Dimension):

1. Setz die Sonde an eine volle Fläche in der Nähe und verbinde sie wie einen Uplink mit dem Mainframe.
2. **Schleichen + Rechtsklick auf die Maschine** mit der Link Card – die Karte merkt sie sich (und die angeklickte Seite).
3. **Rechtsklick auf die Sonde** mit der Karte. Sie zeigt jetzt die Karte der Maschine, genau wie ein Uplink an dieser Seite.

Das Auswählen ist eine normale Interaktion, Claims und Spawn-Schutz gelten also: Wo du die Maschine nicht benutzen darfst, kannst du sie nicht auswählen. Nur du kannst deine Auswahl an eine Sonde geben. Rechtsklick auf die Sonde öffnet ihren Bildschirm: Er zeigt, was sie liest, und hat **Ziel löschen**; der Cloud-Analysator zeichnet eine dünne Linie zur Maschine. Wird die Maschine entfernt, vergisst die Sonde sie.

Zeigt ein Uplink keine Karte und sagt der Bildschirm *„Diese Seite gibt nichts frei“*, bietet die Maschine Energie, Items oder Flüssigkeiten an einer anderen Seite an – setz den Uplink dorthin (der Bildschirm nennt eine) oder nimm eine Sonde.

## Browser koppeln

1. Rechtsklick auf den Mainframe und den **Kopplungscode** merken (8 Zeichen, 10 Minuten gültig, einmal verwendbar).
2. Öffne die Dashboard-URL aus dem Mainframe-Bildschirm im Browser (z. B. `http://192.168.1.20:8787`). Im selben WLAN klappt das sofort.
3. Code und einen Namen für diesen Browser eingeben, **Pair device** anklicken.
4. Klicke in Minecraft im Chat (oder im Mainframe-Bildschirm) auf **[Allow]**. Der Browser macht automatisch weiter.

> **Tipp:** Verknüpfe deinen Spieler einmal mit `/cccloud link`, dann sieht der Browser jeden Mainframe, auf dem du eine Rolle hast – siehe [Konten und Rollen](accounts.md). Als App installieren: „App installieren“ in Chrome/Edge am PC, „Zum Home-Bildschirm“ auf Tablet und Handy.

Wie es weitergeht: [das Dashboard](dashboard.md). Eigene Widgets? Starte mit den [CC-Grundlagen](cc-basics.md) und der [Lua-Referenz](lua.md).


---
# Aus der Dashboard-Doku erzeugt (tools/gen_handbook.ts) – Änderungen in cloud/web/src/docs/content machen.
navigation:
  title: "Redstone-Geräte"
  position: 3
  icon: cccloud:cloud_lamp
---

# Redstone-Geräte

Eine Lampe vom Handy aus schalten – ohne Computer, Lua oder Kanäle: **hinstellen, mit der Link Card verbinden, fertig.** Vier kleine Blöcke, jeder günstiger als ein Uplink.

| Block | Funktion | Im Dashboard |
|---|---|---|
| **Cloud-Schalter** | Haftet wie ein Hebel an jeder Blockseite – an der Wand, auf dem Boden oder unter der Decke. Gibt Strom wie ein Hebel: stark in den Block, an dem er hängt, schwach rundherum. An/aus (0 oder 15) oder analog (0–15). | ein Schalter, im Analog-Modus ein Schieberegler |
| **Cloud-Taster** | Wie ein Knopf: ein kurzer Impuls, 2–40 Ticks (Standard 10 = eine halbe Sekunde). | ein Knopf |
| **Cloud-Lampe** | Ein Lampenblock, der von selbst leuchtet – ganz ohne Redstone. Helligkeit 1–15; kann zusätzlich auf ein Redstone-Signal reagieren. | ein Schalter und ein Helligkeitsregler |
| **Cloud-Sensor** | Liest das Redstone-Signal, das er bekommt (der stärkste Nachbar, 0–15). | eine Leuchte (an/aus) und die Stufe |

## Einrichten

1. Gerät craften und setzen (Rezepte [unten](redstone.md)).
2. Mainframe mit einer **Cloud Link Card** anklicken, dann das Gerät – wie bei einem Uplink und mit denselben Rechten (Besitzer, Admins, „Geräte verbinden“).
3. Es erscheint von selbst im Dashboard: auf der Karte **Redstone** oder auf der Karte seiner Gruppe.

In der Welt schaltet ein Klick einen Schalter oder eine Lampe und drückt einen Taster. **Schleichen + Rechtsklick** (beim Sensor jeder Klick) öffnet das Fenster des Geräts: was es ist, sein Zustand, Name und Gruppe, Modus oder Impulslänge, PIN und Nachfrage und wie es verbunden ist.

## Gruppen und Karten

- Jedes Gerät hat einen **Namen** und optional eine **Gruppe**. Geräte mit derselben Gruppe am selben Mainframe teilen sich **eine Karte** – zum Beispiel „Halle“ mit sechs Lampenschaltern. Groß- und Kleinschreibung spielt keine Rolle.
- Geräte ohne Gruppe teilen sich die automatische Karte **Redstone** (eine pro Mainframe).
- Eine Karte mit zwei oder mehr Schaltern oder Lampen bekommt **Alle an** und **Alle aus**.
- Höchstens 32 Geräte pro Karte; weitere landen auf „Halle (2)“.
- Name und Gruppe setzt du im Fenster des Geräts oder im Dashboard: **Netzwerk** → Gerät antippen → **Dashboard** (braucht „Geräte verbinden“).
- Die Karten sind normale Karten: im [Layout-Editor](dashboard.md) anordnen, einzelne Regler auf eigene Karten ziehen, auf Cloud Tablet oder Cloud Display zeigen und schreibgeschützt teilen.

## Regeln, Szenen und die API

- **Regeln:** einen Sensor unter **Wenn** → „ein Widget-Wert“ wählen (z. B. *über 0* = an) und eine Push-Nachricht senden, eine Szene starten oder eine Lampe schalten. Beispiel: Wenn *Türsensor* über 0 – dann *Verandalicht* an und Meldung „Jemand ist an der Tür“.
- **Szenen:** ein Schritt „Regler bedienen“ auf einem Schalter, einer Lampe (auch ihrer Helligkeit) oder einem Taster.
- Regeln und Szenen funktionieren weiter, wenn ein Gerät in eine andere Gruppe wechselt.
- **[Öffentliche API](api.md):** die Karten sind normale Bereiche – lesen mit `read:sections`, schalten mit `action:widgets` (`POST …/sections/<Karte>/widgets/<id>` mit `{"value": true}`). Geräte mit PIN sind über die API nicht erreichbar.
- **Verlauf:** das Licht der Lampe und die Stufe des Sensors werden aufgezeichnet (24 h, begrenzt durch das Memory-Upgrade des Mainframes) – auf das Diagramm-Symbol tippen.

## Rechte und Schutz

- Bedienen braucht **„Redstone schalten“** (Operatoren haben es). Betrachter und Freigabe-Links schauen nur zu.
- Pro Gerät: **Mainframe-PIN abfragen** und **Vor dem Schalten nachfragen** (in seinem Fenster, braucht „Geräte verbinden“). „Alle an / Alle aus“ fragt, wenn ein Gerät der Karte fragt.
- Verbinden, Umbenennen und die Einstellungen brauchen **„Geräte verbinden“** am Mainframe; von Hand anklicken kann es trotzdem jeder, der den Block benutzen darf – wie einen Hebel.
- Jedes Gerät nimmt höchstens 4 Änderungen pro Sekunde aus Dashboard, Regeln, Szenen und API an (`redstoneDeviceChangesPerSecond`, kurz bis zu 8); Klicks in der Welt sind nicht begrenzt.

## Reichweite, Kapazität und Strom

| | Redstone-Gerät | Cloud Uplink |
|---|---|---|
| Reichweite zum nächsten Knoten | 32 Blöcke (`redstoneDeviceRange`) | 48 |
| Geräte-Kapazität | 0,25 (`redstoneDeviceCapacityWeight`) | 1 |
| Strom am Mainframe | 1 FE/t (`mainframePerRedstoneDeviceFE`) | 4 FE/t |

- Nur Funk (keine Kabel); [Relais](network.md) verlängern die Reichweite wie bei Uplinks. Außer Reichweite oder über der Kapazität antworten seine Regler „keine Verbindung zum Mainframe“.
- Ein Gerät in einem nicht geladenen Chunk bleibt mit seinem letzten Zustand auf seiner Karte; seine Regler antworten „nicht geladen“.
- Die Geräte erscheinen auf der Netzwerkkarte, im Cloud Analyzer und in Jade.

## Rezepte

### Cloud-Schalter
<RecipesFor id="cccloud:cloud_switch" fallbackText="Dieses Rezept gibt es in diesem Modpack nicht (eine benötigte Mod fehlt oder das Pack hat es geändert)." />

### Cloud-Taster
<RecipesFor id="cccloud:cloud_button" fallbackText="Dieses Rezept gibt es in diesem Modpack nicht (eine benötigte Mod fehlt oder das Pack hat es geändert)." />

### Cloud-Lampe
<RecipesFor id="cccloud:cloud_lamp" fallbackText="Dieses Rezept gibt es in diesem Modpack nicht (eine benötigte Mod fehlt oder das Pack hat es geändert)." />

### Cloud-Sensor
<RecipesFor id="cccloud:cloud_sensor" fallbackText="Dieses Rezept gibt es in diesem Modpack nicht (eine benötigte Mod fehlt oder das Pack hat es geändert)." />


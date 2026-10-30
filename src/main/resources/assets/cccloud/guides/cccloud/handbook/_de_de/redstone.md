---
# Aus der Dashboard-Doku erzeugt (tools/gen_handbook.ts) – Änderungen in cloud/web/src/docs/content machen.
navigation:
  title: "Redstone-Geräte"
  position: 3
  icon: cccloud:cloud_lamp
---

# Redstone-Geräte

Eine Lampe vom Handy aus schalten – ohne Computer, Lua oder Kanäle: **hinstellen, mit der Link Card verbinden, fertig.** Vier kleine Blöcke, jeder günstiger als ein Uplink – und der [Erweiterte Cloud-Sensor](redstone.md), der dir sagt, wer oder was in eine Zone kommt.

| Block | Funktion | Im Dashboard |
|---|---|---|
| **Cloud-Schalter** | Haftet wie ein Hebel an jeder Blockseite – an der Wand, auf dem Boden oder unter der Decke. Gibt Strom wie ein Hebel: stark in den Block, an dem er hängt, schwach rundherum. An/aus (0 oder 15) oder analog (0–15). | ein Schalter, im Analog-Modus ein Schieberegler |
| **Cloud-Taster** | Wie ein Knopf: ein kurzer Impuls, 2–40 Ticks (Standard 10 = eine halbe Sekunde). | ein Knopf |
| **Cloud-Lampe** | Ein Lampenblock, der von selbst leuchtet – ganz ohne Redstone. Helligkeit 1–15; kann zusätzlich auf ein Redstone-Signal reagieren. | ein Schalter und ein Helligkeitsregler |
| **Cloud-Sensor** | Liest das Redstone-Signal, das er bekommt (der stärkste Nachbar, 0–15). | eine Leuchte (an/aus) und die Stufe |
| **Erweiterter Cloud-Sensor** | Überwacht eine Zone vor sich: wer hereinkommt (der Name eines Spielers) oder was (die Art des Mobs). Schickt eine Push-Nachricht und gibt Redstone aus, solange etwas drin ist. | eine eigene Karte: wer gerade drin ist, die letzten Ereignisse, heute, scharf/unscharf und die Filter |

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

### Erweiterter Cloud-Sensor
<RecipesFor id="cccloud:advanced_cloud_sensor" fallbackText="Dieses Rezept gibt es in diesem Modpack nicht (eine benötigte Mod fehlt oder das Pack hat es geändert)." />

## Erweiterter Cloud-Sensor

Du hast einen Raum, in den niemand rein soll? Platzier einen **Erweiterten Cloud-Sensor** – und du bekommst eine Push-Nachricht, sobald jemand reinkommt, und **wer** es war: den Namen eines Spielers oder die Art des Mobs.

1. Craften (Rezept [oben](redstone.md)), an Wand, Boden oder Decke setzen und mit der Link Card verbinden wie jedes Redstone-Gerät.
2. Anklicken öffnet sein Fenster: die Zone, was er erkennt, Meldungen. Solange du einen Erweiterten Cloud-Sensor oder eine Link Card hältst oder das Fenster offen ist, wird die Zone in der Welt umrandet (cyan = überwacht, grau = überwacht nicht).
3. Im Dashboard bekommt er **eine eigene Karte** – und auf der Redstone-Karte seiner Gruppe eine „besetzt“-Leuchte.

**Die Zone** ist ein Quader vor dem Sensor (ab dem Sensor, weg vom Block, an dem er hängt) oder um ihn herum: Breite × Höhe × Tiefe entlang X, Y und Z der Welt, standardmäßig 3 × 3 × 3, höchstens 9 × 5 × 9 (Server-Config `advancedSensorMaxZoneX/Y/Z`). Sie muss in deinem eigenen Claim oder auf freiem Land liegen – nicht im Claim eines anderen Teams (FTB Chunks), nicht im Spawnschutz und nicht jenseits der Weltgrenze. Claimt ein anderes Team später einen Teil davon, überwacht der Sensor nicht mehr, bis sich das ändert (seine Karte sagt es).

**Was er sieht:** Spieler (mit Namen), feindliche Mobs, friedliche Mobs (Tiere, Dorfbewohner, Golems), fallen gelassene Items und anderes, das sich bewegt (Loren, Boote, Rüstungsständer) – jeweils mit Art, z. B. „Zombie“ oder „Villager“. Er schaut zweimal pro Sekunde, nur in geladenen Chunks. Zuschauer und Spieler, die ein Vanish-Mod versteckt, werden nie gemeldet.

| Einstellung | Wirkung |
|---|---|
| Erkennen | alle, nur Spieler, nur Mobs oder nur feindliche Mobs |
| Mich und mein Team ignorieren | standardmäßig an: du (wer ihn gesetzt hat) und die Mitglieder deiner Teams (Scoreboard-Team, FTB Teams) werden nicht gemeldet |
| Nur diese Spieler | wenn gesetzt, werden nur diese Spieler gemeldet – auch aus deinem Team |
| Nie diese Spieler | werden nie gemeldet |
| Scharf | unscharf: nichts wird protokolliert, gemeldet oder an Regeln gegeben – der Redstone-Ausgang funktioniert weiter. Scharfschalten meldet nicht, wer schon drin ist. |
| Redstone-Ausgang | solange etwas Passendes drin ist, gibt er Strom wie ein Hebel: stark in den Block, an dem er hängt, schwach rundherum – auch ohne Mainframe |
| Melden | Aus, Info, Warnung (Standard) oder Kritisch: die Stufe seiner Meldungen |

**Die Karte** zeigt, wer gerade drin ist, die letzten 20 Ereignisse („Steve came in · 2m ago“) und wie viele heute hereingekommen sind, dazu die Regler oben und die Zone. *Occupied* („besetzt“) speichert einen 24-h-Verlauf.

**Meldungen:** jeder Zutritt wird eine Meldung im Dashboard und geht an deine [Push-Ziele](automation.md) (Discord, ntfy, Webhook) ab ihrer Stufe: „Steve entered Reactor room“. Kein Spam: die erste Meldung geht sofort raus, was in den nächsten 10 Sekunden hereinkommt, folgt als **eine** Zusammenfassung („3 Zombies entered Reactor room“), und ein Wesen, das binnen 60 Sekunden hinaus- und wieder hereinläuft, wird nicht erneut gemeldet (`advancedSensorAlertCooldown`, `advancedSensorEntityCooldown`).

**Regeln:** unter **Wenn** „ein Erweiterter Sensor erkennt …“ wählen – jemand oder etwas, ein Spieler (optional ein bestimmter Spieler), ein Mob, ein feindlicher oder friedlicher Mob, ein Item oder etwas anderes. Schreib `{who}` in eine Meldung: „\{who\} betritt den Reaktorraum“ wird zu „Steve betritt den Reaktorraum“ oder „2 Zombies betritt …“. Siehe [Regeln](automation.md).

### Wer Namen sieht

Spielernamen sind auf einem gemeinsamen Server persönliche Daten, deshalb:

- Nur Spieler mit der Rolle **Operator** oder höher am Mainframe (das Recht „Koordinaten sehen“) sehen Namen – auf der Karte, in Meldungen und in der API. Betrachter, Freigabe-Links und Cloud Displays sehen immer **„a player“**; Mobs behalten ihre Art.
- Push-Ziele bekommen Namen (Admins richten sie ein).
- Der Server kann Namen ganz abschalten: `advancedSensorPlayerNames = false` – dann sehen alle „a player“, es werden keine Namen gespeichert, und Regeln für einen bestimmten Spieler lösen nie aus. Die Listen *Nur* und *Nie* funktionieren weiter.

### Rechte, Reichweite und Strom

- Scharf- und Unscharfschalten braucht **„Redstone schalten“** (Operatoren) – auf der Karte und in seinem Fenster; PIN und Nachfrage aus seinem Fenster schützen den Schalter. Alle anderen Einstellungen brauchen **„Geräte verbinden“** (Admins).
- Reichweite 32 Blöcke wie die anderen Redstone-Geräte; er zählt **0,5** Geräte gegen die Kapazität des Mainframes (`advancedSensorCapacityWeight`) und kostet **4 FE/t, solange er scharf ist** (`mainframePerAdvancedSensorFE`), unscharf 1 FE/t.


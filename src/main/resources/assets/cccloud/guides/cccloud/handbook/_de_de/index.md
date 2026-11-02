---
# Aus der Dashboard-Doku erzeugt (tools/gen_handbook.ts) – Änderungen in cloud/web/src/docs/content machen.
navigation:
  title: "Erste Schritte"
  position: 0
  icon: cccloud:cloud_mainframe
item_ids:
  - cccloud:cloud_mainframe
  - cccloud:cloud_uplink
  - cccloud:advanced_cloud_uplink
  - cccloud:cloud_link_card
  - cccloud:cloud_core
  - cccloud:cloud_probe
  - cccloud:cloud_analyzer
---

# Erste Schritte

CC: Cloud bringt deine Maschinen, dein Lager und deinen Redstone – und mit CC: Tweaked deine Computer – auf ein Dashboard in jedem Browser: PC, Tablet oder Handy. Das Dashboard läuft im Mod auf deinem eigenen Minecraft-Server, ganz ohne Konto. Standardmäßig bleibt es in deinem eigenen Netzwerk; von überall erreichst du es mit dem kostenlosen, optionalen [CC: Cloud Connect](#anywhere).

> **Tipp:** Der schnellste Weg: Cloud Mainframe craften, mit Strom versorgen, einen Cloud Uplink an eine Maschine (oder mit CC: Tweaked an einen Computer) setzen, beide mit einer Cloud Link Card verbinden und den Browser koppeln. Das Gerät erscheint automatisch auf dem Dashboard – ganz ohne Code.
>
> Du willst nur eine Lampe schalten? Eine **Cloud-Lampe** setzen (oder einen Cloud-Schalter neben deine Redstone-Lampen) und verbinden – sie erscheint als Schalter im Dashboard. Siehe [Redstone-Geräte](redstone.md).

## Installation

Lege **CC: Cloud** in den `mods`-Ordner von Client **und** Server (Minecraft 26.1.2, NeoForge). Im Einzelspieler reicht der Client. **CC: Tweaked** (1.120.0 oder neuer) ist optional: Es bringt programmierbare Computer dazu ([was sich ändert](#cc-tweaked)). Jedes Release hat drei Downloads:

| Datei | Nimm sie, wenn |
|---|---|
| `cccloud-<version>.jar` | du auf deinem eigenen Server oder im Einzelspieler spielst. Sie bringt die Quests mit (unten). |
| `cccloud-<version>-modpack.jar` | du ein Modpack baust: dieselbe Mod, aber sie fasst das Questbuch nie an. |
| `cccloud-quests-atm11-<version>.zip` | du ein Modpack baust oder die Quests von Hand installierst: die Quest-Kapitel. |

Client und Server brauchen **denselben CC: Cloud-Build**. Weicht deiner ab, kommst du trotzdem rein, und der Chat sagt es dir beim Beitreten – mit einem Link zur Jar des Servers. Siehe [Deine Version ist nicht die des Servers](faq.md).

### Mit oder ohne CC: Tweaked

CC: Cloud läuft auch allein. **CC: Tweaked** bringt programmierbare Computer dazu – installier es auf Client und Server (1.120.0 oder neuer), wenn du sie willst. Alles andere funktioniert so oder so gleich:

| | Ohne CC: Tweaked | Mit CC: Tweaked |
|---|---|---|
| Mainframe, Uplinks, Sonden, Relays, Fiber Cable, Upgrades | ✓ | ✓ |
| Maschinen-, Energie-, Tank- und Lager-Karten, AE2-/RS-Bridges, Redstone-Geräte, Sensoren | ✓ | ✓ |
| Dashboard, Regeln, Szenen, Zeitpläne, Vorlagen, Push, Verlauf, Berichte, API, Tablet, Display | ✓ | ✓ |
| Computer-Karte, Lua-Programme und ihre Widgets (das Peripheral `cloud_uplink`) | – | ✓ |
| Terminal im Browser, Lua-Editor, Skripte hochladen, Installieren aus dem Script Store | – | ✓ |
| Peripherie-Karte, Karten für Maschinen im Kabelnetz eines Computers | – | ✓ |
| CC-Netzwerkkabel und Wired Modems als Verbindung | – | ✓ |

Ohne CC: Tweaked nehmen die Rezepte mit CC-Teilen stattdessen Vanilla-Items (markiert mit **Nur ohne CC: Tweaked**, siehe [die Rezepte](#recipes)), das Questbuch bekommt nur das CC: Cloud-Kapitel, und im Script Store kannst du stöbern, aber nichts installieren. Installierst du CC: Tweaked später, kommt der Rest dazu – Mainframes, Verknüpfungen und Dashboards bleiben, wie sie sind.

### Quests (FTB Quests)

Mit **FTB Quests** bekommst du zwei Quest-Kapitel: **CC: Tweaked** (24 Quests: Computer, Turtles, Monitore, Modems, erste Programme) und **CC: Cloud** (39 Quests: Mainframe, Uplinks, Koppeln, Bridges, Relays, Upgrades, Tablet, Display), auf Englisch und Deutsch.

Mit `cccloud-<version>.jar` kommen sie von selbst: Beim ersten Start, bevor FTB Quests seine Quests liest, trägt CC: Cloud sie ins Questbuch ein (`config/ftbquests/quests`) – in die Gruppe **Tech** von ATM11 nach Ender IO und XyCraft, sonst in eine neue Gruppe **CC: Cloud**. Die Spieler bekommen sie vom Server; im Einzelspieler passiert dasselbe in deinem Spielordner.

- Zuerst kommt ein Backup (`config/ftbquests/quests.backup-<Zeit>`). Andere Kapitel und Texte werden nie ersetzt.
- Hat das Questbuch schon ein CC: Tweaked-Kapitel, kommt nur das CC: Cloud-Kapitel dazu. Ist CC: Tweaked nicht installiert, wartet sein Kapitel und kommt beim ersten Start mit CC: Tweaked.
- Nur einmal: Ein gelöschtes Kapitel kommt nicht wieder. Neue Versionen aktualisieren ein Kapitel nur, wenn es niemand geändert hat; deine Änderungen bleiben.
- Im Server-Log steht eine Zeile dazu (`FTB Quests: …`). Abschalten mit `installQuests = false` in `config/cccloud-server.toml` auf dem Server (im Einzelspieler: in deinem Spielordner).

> **Modpack-Ersteller:** Nimm `cccloud-<version>-modpack.jar` – dieselbe Mod mit derselben ID und Version, sie kann also die normale Jar ersetzen, fasst aber das Questbuch nie an (`installQuests` ist aus und die Quest-Dateien sind nicht drin). Liefere die Kapitel aus `cccloud-quests-atm11-<version>.zip` in deinem Questbuch mit, unverändert oder angepasst (Layout, Belohnungen, Kapitelgruppe; die Meilenstein-Belohnungen nutzen die Belohnungstabellen von ATM11). Du nimmst doch die normale Jar? Dann `installQuests = false` in `defaultconfigs/cccloud-server.toml` eintragen. `install.sh` und `README.txt` im Zip beschreiben auch die Installation von Hand.

## Die Blöcke

| Block / Item | Funktion |
|---|---|
| **Cloud Mainframe** | Das Herz einer Basis. Zwei Blöcke hoch. Stellt die Verbindung zum Dashboard her, zeigt den Kopplungscode und verwaltet Geräte. Einer pro Basis oder Team. Braucht FE-Strom. |
| **Cloud Uplink** | Haftet wie ein Modem an einer Blockseite; an niedrigen oder kleinen Seiten (Stufen, Solarpanels) wird er zum kompakten Modul; oben oder unten braucht er eine volle Fläche. An einem Computer (CC: Tweaked) wird er zur Karte dieses Computers im Dashboard, an einer Maschine zur Karte der Maschine. Reichweite 48 Blöcke bis zum nächsten Mainframe oder Relay, gleiche Dimension. |
| **Advanced Cloud Uplink** | Genauso, aber mit 128 Blöcken Reichweite. Über Dimensionen hinweg verbinden [Ender Relays](network.md). |
| **Cloud-Sonde** | Liest **eine** Maschine bis zu 8 Blöcke entfernt, als säße ein Uplink daran – für eingebaute Maschinen oder wenn jede Seite mit Rohren und Kabeln belegt ist. Teuer; zählt wie zwei Geräte. Siehe *Maschinen ohne freie Seite* weiter unten. |
| **Cloud Link Card** | Verbindet Uplinks, Bridges und Redstone-Geräte mit einem Mainframe. |
| **Cloud-Schalter / -Taster / -Lampe / -Sensor** | Kleine [Redstone-Geräte](redstone.md) ohne Computer: verbinden und Lampen, Türen und Maschinen im Dashboard schalten. Geräte mit derselben Gruppe teilen sich eine Karte. |
| **ME / RS Cloud Bridge** | Hängt sich in ein Applied-Energistics-2- oder Refined-Storage-Netzwerk. Mit einem Uplink darauf bekommst du eine fertige Lager-Karte (Suche, Craften, Merkliste). Gibt es nur, wenn AE2 / RS installiert ist. |
| **Cloud Core** | Crafting-Zutat. |

## Crafting-Rezepte

Zutaten mit „(beliebig)“ akzeptieren jede Variante aus dem Tag (z. B. jeden Goldbarren). JEI/EMI zeigen im Spiel dieselben Rezepte.

Ohne CC: Tweaked nehmen die Rezepte mit CC-Teilen an derselben Stelle Vanilla-Items: einen **Werker** statt des Advanced Computers, ein **Gearbeitetes Bücherregal** statt des Disk Drives, einen **Beobachter** statt des Wired Modems, **Getöntes Glas** statt des Advanced Monitors, einen **Kompass** / eine **Uhr** statt des (Advanced) Pocket Computers und einen **Enderkristall** statt des Ender Modems. Bei jedem Rezept steht, wann es gilt (**Braucht CC: Tweaked** / **Nur ohne CC: Tweaked**).

### Cloud Core
<RecipesFor id="cccloud:cloud_core" fallbackText="Dieses Rezept gibt es in diesem Modpack nicht (eine benötigte Mod fehlt oder das Pack hat es geändert)." />

### Cloud Mainframe
<RecipesFor id="cccloud:cloud_mainframe" fallbackText="Dieses Rezept gibt es in diesem Modpack nicht (eine benötigte Mod fehlt oder das Pack hat es geändert)." />

### Cloud Uplink
<RecipesFor id="cccloud:cloud_uplink" fallbackText="Dieses Rezept gibt es in diesem Modpack nicht (eine benötigte Mod fehlt oder das Pack hat es geändert)." />

### Advanced Cloud Uplink
<RecipesFor id="cccloud:advanced_cloud_uplink" fallbackText="Dieses Rezept gibt es in diesem Modpack nicht (eine benötigte Mod fehlt oder das Pack hat es geändert)." />

### Cloud-Sonde
<RecipesFor id="cccloud:cloud_probe" fallbackText="Dieses Rezept gibt es in diesem Modpack nicht (eine benötigte Mod fehlt oder das Pack hat es geändert)." />
Ohne Allthemodium ersetzen 4 Diamanten und 2 Netheritschrott die Vibranium-Barren (alle Rezepte: JEI/EMI oder die Rezeptseite).

### Cloud Link Card
<RecipesFor id="cccloud:cloud_link_card" fallbackText="Dieses Rezept gibt es in diesem Modpack nicht (eine benötigte Mod fehlt oder das Pack hat es geändert)." />

### ME- und RS Cloud Bridge
<RecipesFor id="cccloud:me_cloud_bridge" fallbackText="Dieses Rezept gibt es in diesem Modpack nicht (eine benötigte Mod fehlt oder das Pack hat es geändert)." />
<RecipesFor id="cccloud:rs_cloud_bridge" fallbackText="Dieses Rezept gibt es in diesem Modpack nicht (eine benötigte Mod fehlt oder das Pack hat es geändert)." />

## Mainframe aufstellen

1. Stelle den **Cloud Mainframe** auf (er ist zwei Blöcke hoch). Wer ihn platziert, ist sein **Besitzer**.
2. Rechtsklick darauf: Der Bildschirm zeigt Status, Dashboard-Adresse (URL) und einen Kopplungscode.
3. Schalte dort das Dashboard **ein** (oder Schleichen + Rechtsklick mit leerer Hand).

Ein Redstone-Signal an einer der beiden Hälften ist ein **Not-Aus**: Der Mainframe geht offline, bis das Signal weg ist.

### Mainframe umbenennen

Ein neuer Mainframe heißt nach seiner Position. Gib ihm einen Namen in seinem Fenster (**Name** im Reiter Übersicht) oder im Dashboard unter **Settings** beim Namen. Du brauchst dafür das Recht „Edit the layout“. Bis zu 32 Zeichen; ein leerer Name geht zurück zur Position. Dashboards, Tablets, Displays und die Netzwerkkarte folgen sofort.

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

Das Auswählen ist eine normale Interaktion, Claims und Spawn-Schutz gelten also: Wo du die Maschine nicht benutzen darfst, kannst du sie nicht auswählen. Nur du kannst deine Auswahl an eine Sonde geben, innerhalb von 10 Minuten. Die Sonde prüft weiter: Claimt ein anderes Team den Chunk der Maschine oder senkt der Server die Reichweite, liest sie nichts, bis sich das ändert; wird die Sonde versetzt (mit einem Block-Mover oder einer Contraption), vergisst sie ihre Maschine. Rechtsklick auf die Sonde öffnet ihren Bildschirm: Er zeigt, was sie liest, und hat **Ziel löschen**; der Cloud-Analysator, die Netzwerkkarte im Dashboard (2D und 3D) und die Minikarte im Mainframe-Bildschirm zeichnen eine dünne lila Linie zur Maschine. Wird die Maschine entfernt, vergisst die Sonde sie.

Zeigt ein Uplink keine Karte und sagt der Bildschirm *„Diese Seite gibt nichts frei“*, bietet die Maschine Energie, Items oder Flüssigkeiten an einer anderen Seite an – setz den Uplink dorthin (der Bildschirm nennt eine) oder nimm eine Sonde.

## Browser koppeln

1. Rechtsklick auf den Mainframe und den **Kopplungscode** merken (8 Zeichen, 10 Minuten gültig, einmal verwendbar).
2. Öffne die Dashboard-URL aus dem Mainframe-Bildschirm im Browser (z. B. `http://192.168.1.20:8787`). Im selben WLAN klappt das sofort.
3. Code und einen Namen für diesen Browser eingeben, **Pair device** anklicken.
4. Klicke in Minecraft im Chat (oder im Mainframe-Bildschirm) auf **[Allow]**. Der Browser macht automatisch weiter.

> **Tipp:** Verknüpfe deinen Spieler einmal mit `/cccloud link`, dann sieht der Browser jeden Mainframe, auf dem du eine Rolle hast – siehe [Konten und Rollen](accounts.md). Als App installieren: „App installieren“ in Chrome/Edge am PC, „Zum Home-Bildschirm“ auf Tablet und Handy.

## Von überall öffnen

Ab Werk antwortet das Dashboard nur in deinem eigenen Netzwerk. Am einfachsten erreichst du es über mobile Daten oder bei Freunden mit **CC: Cloud Connect**: einem kostenlosen, optionalen Relay ohne Portfreigabe und ohne Domain. Führe das einmal im Spiel aus (Serverbesitzer, OP-Level 4 oder die Serverkonsole):

```
/cccloud internet connect mybase
```

Dein Dashboard liegt dann unter **https://mybase.cccloud.dev**. Jeder neue Browser muss weiterhin gekoppelt und im Spiel erlaubt werden. Limits, Datenschutz und die anderen Wege: [Zugriff aus dem Internet](security.md).

Wie es weitergeht: [das Dashboard](dashboard.md). Eigene Widgets? Mit CC: Tweaked startest du mit den [CC-Grundlagen](cc-basics.md) und der [Lua-Referenz](lua.md).


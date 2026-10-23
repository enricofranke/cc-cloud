---
# Aus der Dashboard-Doku erzeugt (tools/gen_handbook.ts) – Änderungen in cloud/web/src/docs/content machen.
navigation:
  title: "Das Dashboard"
  position: 2
  icon: cccloud:cloud_uplink
---

# Das Dashboard

Öffne die URL, die der Mainframe anzeigt. Ein Browser kann mehrere Mainframes speichern; tippe einen an, um sein Dashboard zu öffnen.

## Karten und Widgets

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
| item search, watchlist, jobs | Lager-Widgets der [Bridges](bridges.md) |

## Seiten und Layout-Editor

Programme können Widgets auf **Seiten** (Tabs: oben in breiten Fenstern, unten auf schmalen Bildschirmen) und in **Gruppen** legen. Admins sehen einen **Stift**: Damit wird das Dashboard selbst zum Editor – du ordnest die echten, laufenden Karten an.

- **Verschieben:** eine Karte an ihrer Kopfzeile ziehen, ein Widget an beliebiger Stelle. Die anderen machen Platz und gleiten zur Seite; ein gestrichelter Rahmen zeigt, wo es landet. Ein Widget auf eine andere Karte ziehen, auf freie Fläche (es bekommt seine **eigene neue Karte**) oder auf einen Seiten-Tab (kurz darüber halten öffnet die Seite).
- **Größe ändern:** rechten Rand, unteren Rand oder die Ecke ziehen – das Schild zeigt die Größe (Karten nutzen 12 Spalten der Seite, Widgets 12 Spalten ihrer Karte).
- **Mehrere Widgets auswählen** mit Shift-Klick und eines davon ziehen, um alle zu verschieben.
- **Einstellungen** (⚙ oder Klick): Beschriftung, Akzentfarbe, Icon, Größe und Darstellung mit Live-Vorschau – ein Gauge als Skala, Balken oder große Zahl, ein Label als Zeile oder große Zahl, ein Chart als Diagramm oder Sparkline, ein Indikator als Chip oder Lampe. Karten bekommen Titel, Icon, Akzent und den Stil kompakt oder komfortabel.
- **Ausblenden** mit ×. **+ Add** listet alles Ausgeblendete (zurückziehen oder Show drücken) und zeigt jeden Widget-Typ live mit der Lua-Zeile, die ihn erzeugt.
- **Touch:** gedrückt halten zum Aufnehmen (kurze Vibration), dann ziehen – normales Wischen scrollt weiter. **Tastatur:** mit Tab zu Karte oder Widget, Leertaste nimmt auf, Pfeiltasten verschieben, Shift + Pfeiltasten ändern die Größe, Bild auf/ab verschieben ein Widget zur vorigen/nächsten Karte, Leertaste legt ab, Esc bricht ab. **?** zeigt alle Kürzel.
- **Rückgängig/Wiederholen** (Strg/⌘ + Z, Strg/⌘ + Shift + Z), **Reset** auf die Vorgabe der Skripte und **Save** (Strg/⌘ + S). Wer mit ungespeicherten Änderungen geht, wird vorher gefragt.

**Save** speichert das Layout auf dem Server – alle Browser sehen es. Startet ein Skript neu, bleibt das Layout erhalten, weil die Widget-IDs gleich bleiben. Auf schmalen Bildschirmen stehen die Karten in Lesereihenfolge untereinander (auf Handys zweispaltig).

## Meldungen

Programme senden Meldungen mit `cloud.alert(text, level)`. Sie erscheinen als Einblendung und bleiben im Protokoll unter der **Glocke**. Info-Meldungen verschwinden von selbst, warn und crit bleiben, bis du sie schließt. Mit **Enable notifications** im Protokoll bekommst du für kritische Meldungen System-Benachrichtigungen, auch wenn der Tab im Hintergrund ist.

## PIN-geschützte Bedienelemente

Widgets mit `pin=true` fragen vor dem Auslösen die Mainframe-PIN über ein Ziffernfeld ab. Der Besitzer legt sie mit `/cccloud pin set <4-8 Ziffern>` fest. Nach 5 falschen PINs ist das Gerät 5 Minuten gesperrt und der Besitzer wird im Spiel benachrichtigt.

## Zustände

| Anzeige | Bedeutung |
|---|---|
| Dashboard disabled | das Dashboard ist im Mainframe-Bildschirm ausgeschaltet |
| Offline | der Chunk des Mainframes ist nicht geladen, der Block wurde abgebaut oder der Redstone-Not-Aus ist aktiv |
| Mainframe has no power | der FE-Puffer ist leer – siehe [Strom](index.md) |
| Uplink offline | dieser Uplink ist nicht geladen oder außer Reichweite |
| Read-only device | dieses Gerät darf zusehen, aber nicht steuern |
| Server not reachable | siehe [Fehlerbehebung](faq.md) |


---
# Aus der Dashboard-Doku erzeugt (tools/gen_handbook.ts) – Änderungen in cloud/web/src/docs/content machen.
navigation:
  title: "Regeln, Szenen & Benachrichtigungen"
  position: 7
---

# Regeln, Szenen, Benachrichtigungen und Verlauf

Automatisierung, die auf dem **Server** läuft – ohne Browser und ohne Computerprogramm –, dazu Szenen, Zeitpläne, Meldungen aufs Handy, Langzeit-Diagramme und Produktionsstatistiken.

## Regeln

Öffne **Rules** in den Dashboard-Einstellungen (⚙). Eine Regel liest sich wie ein Satz: **Wenn** etwas passiert, **falls** Bedingungen gelten, **dann** tue etwas.

| Teil | Möglichkeiten |
|---|---|
| Wenn | ein Widget-Wert überschreitet eine Zahl, eine Itemanzahl in einer Lager-Karte, Mainframe-Strom in %, eine Karte geht offline, alle N Minuten, an Tagen zu einer Uhrzeit, eine Uhrzeit im Spiel ([Zeitpläne](automation.md)) |
| Falls (optional, alle müssen gelten) | Widget-Wert, Itemanzahl, Strom, eine Karte ist online |
| Dann (1–10, der Reihe nach) | eine Redstone-Seite eines Computers setzen, ein Bedienelement nutzen (Button, Schalter, Regler, Auswahl, Zahl, Text), ein Item craften, eine Meldung senden, das Dashboard ein-/ausschalten, eine [Szene](automation.md) starten |

- Wert-Auslöser feuern **einmal**, wenn die Prüfung wahr wird, nicht erneut, solange sie wahr bleibt. Die **Abklingzeit** (mindestens 5 s) ist der Mindestabstand zwischen zwei Läufen.
- **Run now** führt die Aktionen einmal aus und ignoriert Auslöser, Bedingungen und Abklingzeit – praktisch zum Testen.
- Sicherheit: Eine Regel darf nicht das Widget ändern, das ihr eigener Auslöser beobachtet (das wäre eine Schleife), PIN-geschützte Bedienelemente sind nicht erlaubt, und höchstens 20 Regelläufe pro Sekunde und Mainframe. Jeder Lauf wird protokolliert (**Recent runs**).
- Wie viele Regeln es geben darf, hängt vom **Memory**-Upgrade des Mainframes ab (8 ohne, dann 24 / 64 / 128).
- Rechte: „Edit rules“ zum Anlegen, Ändern und Löschen; „Enable rules“ zum Ein-/Ausschalten und für **Run now** (haben Operatoren).
- Lua: `cloud_mainframe.getRules()` und `setRuleEnabled(id, enabled)`.

**Beispiel:** Wenn *Core temperature (Fission Reactor)* über 900 – dann Redstone hinten am *Reaktor-Computer* auf 15 und Meldung „Reaktor heiß“ (Warnung).

## Szenen

Eine Szene ist **ein Knopf, der mehrere Schritte nacheinander ausführt** – „Nachtmodus“: Licht aus, 2 s warten, Tür zu, Reaktor-Regler auf 3, eine Meldung. Öffne **Szenen** in den Dashboard-Einstellungen (⚙).

- **Schritte** (bis 16): dieselben Aktionen wie bei Regeln – Redstone-Seite setzen, Bedienelement nutzen, craften, Meldung senden, Dashboard an/aus – plus **Pause** (0,05–300 s, höchstens 600 s pro Szene). Mit ↑ und ↓ änderst du die Reihenfolge.
- Jede Szene ist ein Knopf auf der Karte **Scenes**: drück ihn im Dashboard, auf einem Cloud Tablet, auf einem Cloud Display (nur Szenen ohne Nachfrage und PIN) oder verschieb ihn mit dem Layout-Editor auf eine beliebige Karte. **Testlauf** im Szenen-Fenster startet die gespeicherte Szene und hakt jeden Schritt beim Ablauf ab; der letzte Lauf behält ein Ergebnis pro Schritt.
- Optionen: Farbe, Symbol, **vor dem Start nachfragen** und **immer nach der PIN fragen**. Ein Schritt auf einem PIN-geschützten Bedienelement lässt die Szene ohnehin nach der PIN fragen.
- **Rechte:** Anlegen und Ändern braucht „Edit rules“ **und** jede Berechtigung, die die Schritte brauchen – niemand kann mehr weitergeben, als er selbst darf. Drücken braucht „Use controls“; jeder Schritt wird **beim Ablauf** erneut geprüft, mit den Rechten dessen, der gedrückt hat. Ein Schritt ohne Berechtigung schlägt fehl, die anderen laufen trotzdem.
- **Planen…** öffnet eine neue Regel, die die Szene an Tagen zu einer Uhrzeit startet (unten).
- Grenzen: 32 Szenen pro Mainframe (`maxScenesPerMainframe`), dieselbe Szene nie zweimal gleichzeitig, höchstens 4 Läufe gleichzeitig und 30 pro Minute und Mainframe. `scenesEnabled = false` in `[scenes]` schaltet Szenen ab.

## Zeitpläne

Zeitpläne sind Regeln mit einem **Zeit-Auslöser** – wähle ihn im Regel-Editor unter **When**:

| Auslöser | Läuft |
|---|---|
| alle … Minuten | Echtzeit, alle N Minuten (mindestens `minIntervalMinutes`, Standard 1) |
| an Tagen zu einer Uhrzeit | Echtzeit: an den gewählten Wochentagen um HH:MM (24 h) in der **Zeitzone des Servers** – der Editor zeigt sie (Einstellung `timezone`, leer = Systemzone des Servers) |
| eine Uhrzeit im Spiel | wenn die Weltuhr die Uhrzeit überschreitet: **Sonnenaufgang**, **Mittag**, **Sonnenuntergang**, **Mitternacht** oder jede Spielstunde |

- Mit der Aktion **Run a scene** startet ein Zeitplan viele Schritte. Eine geplante Szene läuft mit den Rechten ihres **Besitzers** (wer sie zuletzt gespeichert hat), nie mit PIN – Szenen, die die PIN brauchen, lassen sich deshalb nicht planen.
- Die Regelliste zeigt bei Echtzeit-Plänen **next run in …**.
- **Verpasste Läufe:** Nach einem Server-Neustart wird höchstens **ein** verpasster Lauf nachgeholt, und nur wenn er jünger als `missedWindowHours` (24 h) ist; `runMissed = false` holt nie nach. Die Spielzeit steht still, solange der Server aus ist – Spielzeit-Auslöser holen deshalb nie nach.
- **Nicht geladene Mainframes:** Ist ein Lauf fällig, während der Mainframe nicht geladen oder offline ist, wird er **übersprungen** – das Protokoll zeigt einmal pro Ausfall „skipped – mainframe unloaded“, und nichts wird später nachgeholt. Halte den Chunk des Mainframes geladen (Chunk-Anchor-Upgrade, FTB Chunks) für verlässliche Zeitpläne.
- Höchstens 16 Zeit-Regeln pro Mainframe (`maxSchedulesPerMainframe` in `[schedules]`).

## Push-Benachrichtigungen

Öffne **Notifications** in den Dashboard-Einstellungen. Bis zu 5 Ziele pro Mainframe:

- **Discord:** in den Kanaleinstellungen → Integrationen → Webhooks → Neuer Webhook → URL kopieren.
- **ntfy:** eine Topic-URL wie `https://ntfy.sh/meine-basis-alarme` (langen, schwer zu ratenden Namen wählen oder ein Token nutzen); dasselbe Topic in der ntfy-App abonnieren.
- **Webhook:** jeder https-Endpunkt; er bekommt einen JSON-POST (event, mainframe, title, text, level, time).

Wähle die Mindeststufe (alles, Warnungen und Fehler oder nur Fehler) und die Ereignisse: **alerts** (Skripte, Karten, Crafting, Regeln), **rules** (Läufe von Regeln mit „push every run“), **approvals** (ein Browser wartet auf Freigabe) und **power** (kein Strom / wieder da). **Send test** prüft ein Ziel; das Ergebnis steht daneben.

Links und Tokens liegen nur auf dem Server – das Dashboard zeigt sie maskiert. Der Server sendet nur an öffentliche https-Adressen (kein lokales Netz, keine Weiterleitungen), höchstens 30 Nachrichten pro Minute und Mainframe. Braucht das Recht „Push targets“ (Admins).

## Langzeit-Verlauf

Widgets, deren Skript einen Verlauf anfordert (`history="24h"` in den Widget-Optionen, oder `"1h"`, `"7d"`, `"30d"`), bekommen einen kleinen **Diagramm-Button**. Er öffnet den Verlauf mit Zeitraum-Wahl (1 h, 24 h, 7 Tage, 30 Tage), Min / Durchschnitt / Max und dem Wert unter Finger oder Maus; Lücken bedeuten, dass Mainframe oder Chunk nicht geladen waren.

```lua
cloud.gauge("temp", "Core temperature", { min = 0, max = 1200, unit = "K", history = "7d" })
```

Wie viel der Server aufbewahrt, hängt vom **Memory**-Upgrade ab: 1 h ohne, 24 h / 7 Tage / 30 Tage mit den Stufen. **Export CSV** lädt die Werte (Zeit, Wert) für Tabellen herunter – braucht das Recht „Use controls“.

## Produktionsstatistik

Die Seite **Produktion** (ein Tab neben Network, oder ⚙ → Produktion) zeigt, was jedes AE2- bzw. Refined-Storage-Netz hinter deinen Karten **dazubekommt, verbraucht und craftet** – pro Minute oder pro Stunde, über die letzte Stunde.

- Der Server misst jeden Speicher jede Minute (`sampleSeconds`) und behält ein Fenster von 60 Messwerten (`windowSamples`). Die ersten Zahlen kommen nach einer Minute; ein Server-Neustart beginnt das Fenster von vorn.
- Pro Speicher: dazu, verbraucht, netto und gecraftet, ein Diagramm und die **größten Zugänge und Abgänge** mit ihren Symbolen.
- Ein Klick auf ein Item – „was frisst mein Eisen?“ – zeigt seine Raten und sein Diagramm, seine Crafting-Aufträge, was gerade im Netz gecraftet wird, die letzten 24 Stunden (beobachtete und favorisierte Items) und wann der Vorrat bei diesem Tempo leer ist.
- **Was sie nicht sagen kann:** AE2 und Refined Storage melden nur, wie viel der Speicher dazubekommt und verliert – nicht, welche Maschine, Leitung oder welcher Spieler die Items genommen hat. Was zwischen zwei Messungen kommt und geht, hebt sich auf. **Gecraftet** zählt fertige Aufträge, die über die Bridge gestartet wurden (Dashboard, Lua, Auto-Lager, Regeln); am Terminal gestartete Aufträge sind nur sichtbar, solange sie laufen.
- **Energie:** jede Energiekarte mit gespeicherter Energie und rein/raus in FE/t, die Summen und ein Diagramm. Blöcke, deren Mod keine Raten meldet, zeigen die gemessene Änderung („gemessen“).
- **Als Karte anheften** legt die Rate eines Items, die Netto-Rate eines Speichers oder die Energiesummen als Diagramm auf die Karte **Production** – mit dem Layout-Editor anordnen; Tablets und Displays zeigen sie auch. Bis zu 16 Anheftungen; braucht „Edit the layout“ (und „Search storage“ für Items und Speicher).
- Speicherzahlen brauchen das Recht „Search storage“. `productionEnabled = false` in `[production]` schaltet die Statistik ab.

## Terminal

Der Button **>_** auf der Karte eines Computers öffnet sein **Terminal** im Browser: der Bildschirm live (Farben bei Advanced-Computern) und deine Tastatur, als stündest du davor. Auf den Bildschirm klicken und tippen; **Terminate** ist Strg+T, **Reboot**, **Shut down** und **Turn on** steuern den Computer. Einfügen schickt die erste Zeile der Zwischenablage. Auf dem Handy ins Feld unter dem Bildschirm tippen und **Enter** drücken.

Braucht das Recht „Terminal“ (Admins) und die Mainframe-PIN, falls eine gesetzt ist; das Öffnen wird protokolliert. Der Server-Admin kann es abschalten (`terminalEnabled`) oder Skripte und Terminal für einen Spieler verbieten (`cccloud.feature.scripts`).

## Lua-Editor

Im **\</>**-Fenster eines Computers zeigt **Open the editor** alle Dateien und Ordner (bis 8 Ebenen) neben einem Code-Editor mit Lua-Hervorhebung, Zeilennummern, Suche (Strg/⌘ + F) und Rückgängig. **Save** (Strg/⌘ + S) speichert; **Save & run** startet die Datei zusätzlich in einem neuen Tab auf dem Computer. **+ File**, **+ Folder**, Umbenennen/Verschieben (✎) und Löschen (×) arbeiten im Dateibaum.

Wurde die Datei seit dem Öffnen auf dem Computer geändert, fragt das Speichern vor dem Überschreiben. Dateien sind auf 256 KB begrenzt, `rom` und Disketten werden nie angefasst, und der Editor braucht dieselben Rechte wie das Hochladen von Skripten (plus die PIN, falls gesetzt).


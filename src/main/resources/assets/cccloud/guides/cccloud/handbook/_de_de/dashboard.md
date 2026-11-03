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

Jeder Uplink wird zu einer **Karte**; [Redstone-Geräte](redstone.md) teilen sich eine Karte pro Gruppe. Ein Computer (CC: Tweaked) ohne eigenes Programm zeigt die automatische **Computer-Karte**: Strom, Turn on / Reboot / Shut down und seine sechs Redstone-Seiten (Seite antippen schaltet den Ausgang, die LED zeigt den Eingang; analoge Stärken unter „Advanced“).

**Maschinen im Kabelnetz des Computers** (CC: Tweaked). Hat der Computer ein Wired Modem mit CC-Netzwerkkabel zu anderen Maschinen (deren Modems eingeschaltet), zeigt der Uplink am Computer auch für jede dieser Maschinen eine Karte – dieselbe Karte, die ein Uplink direkt an der Maschine zeigen würde (Energie, Tanks, Lager-Controller, Mod-Maschinen), mit denselben Bedienelementen und Rechten. Bis zu 8 pro Uplink (Server-Einstellung `wiredMachineCards`); eine Maschine mit eigenem Uplink oder eigener Sonde behält diese, und gleichnamige Maschinen unterscheidet der Name, den der Computer für sie nutzt („Energy Cell (energy_cell_2)“). Normale Truhen haben keine Karte.

**Peripherie-Karte – für alles andere** (CC: Tweaked). Ein Uplink oder eine Sonde an einem Block ohne eigene Karte, den CC: Tweaked aber als Peripheriegerät anbietet (die Methoden, die ein Computer an ihm aufrufen könnte, auch CCs Energie-, Item- und Flüssigkeits-Methoden), zeigt die **Peripherie-Karte**: Name des Blocks und seine Peripherie-Typen. Was sie zeigt, wählst du im [Layout-Editor](#edit-mode): Karte anklicken, unter **Werte** bis zu 6 Methoden ohne Argumente ankreuzen (`get…`, `is…`, `list…` sowie `size`, `list`, `tanks` von Lagerblöcken), bis zu 2 Zahlen auch als Diagramm, dann **Werte speichern**. Eine Ja/Nein-Methode wird zur Lampe, alles andere zu einem kurzen Text. Hat der Block eine Methode `set…(true/false)`, kann sie der **Schalter** der Karte werden (Operatoren schalten ihn, mit Bestätigung und – wenn du es nicht abschaltest – der PIN). Aufgerufen werden nur die Methoden, die du gewählt hast, alle paar Sekunden und seltener, wenn sie langsam sind; Methoden mit Argumenten und die von Peripheriegeräten, die ihre Argumente nicht angeben, werden nicht angeboten – ein Lua-Programm kann sie weiter nutzen. Werte auswählen braucht das Recht, das Layout zu bearbeiten. Ein Peripheriegerät im Kabelnetz eines Computers bekommt keine Peripherie-Karte (nur Maschinen mit einer echten Karte).

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

Programme können Widgets auf **Seiten** (Tabs: oben in breiten Fenstern, unten auf schmalen Bildschirmen) und in **Gruppen** legen. Wer das Recht **Edit the layout** hat, sieht einen **Stift**: Damit wird das Dashboard selbst zum Editor – du ordnest die echten, laufenden Karten an.

- **Verschieben:** eine Karte an ihrer Kopfzeile ziehen, ein Widget an beliebiger Stelle. Die anderen machen Platz und gleiten zur Seite; ein gestrichelter Rahmen zeigt, wo es landet. Ein Widget auf eine andere Karte ziehen, auf freie Fläche (es bekommt seine **eigene neue Karte**) oder auf einen Seiten-Tab (kurz darüber halten öffnet die Seite).
- **Größe ändern:** rechten Rand, unteren Rand oder die Ecke ziehen – das Schild zeigt die Größe (Karten nutzen 12 Spalten der Seite, Widgets 12 Spalten ihrer Karte).
- **Mehrere Widgets auswählen** mit Shift-Klick und eines davon ziehen, um alle zu verschieben.
- **Einstellungen** (✎ oder Klick): Beschriftung, Akzentfarbe (ein Button nimmt sie als seine Farbe – auch der eines Cloud Buttons, standardmäßig cyan), Icon, Größe und Darstellung mit Live-Vorschau – ein Gauge als Skala, Balken oder große Zahl, ein Label als Zeile oder große Zahl, ein Chart als Diagramm oder Sparkline, ein Indikator als Chip oder Lampe. Karten bekommen Titel, Icon, Akzent, den Stil kompakt oder komfortabel und ein [Design](#card-design) (Hintergrund, eigenes CSS, Kartenteile); eine [Peripherie-Karte](#widgets) auch ihre Werte und ihren Schalter.
- **Toolbox:** öffnet sich an der Seite (auf Handys als Sheet; Taste **A**). **Cards** enthält eine leere Karte und fertige Vorlagen, **Widgets** jeden Widget-Typ: eines auf eine Karte oder freie Fläche ziehen und dann seine Daten wählen. **Hidden** listet alles Ausgeblendete.
- **Ausblenden** mit × oder per Ziehen auf die Toolbox. Zurück holst du es mit einem Klick unter **Hidden** oder per Herausziehen.
- **Touch:** gedrückt halten zum Aufnehmen (kurze Vibration), dann ziehen – normales Wischen scrollt weiter. **Tastatur:** mit Tab zu Karte oder Widget, Leertaste nimmt auf, Pfeiltasten verschieben, Shift + Pfeiltasten ändern die Größe, Bild auf/ab verschieben ein Widget zur vorigen/nächsten Karte, Leertaste legt ab, Esc bricht ab. **?** zeigt alle Kürzel.
- **Rückgängig/Wiederholen** (Strg/⌘ + Z, Strg/⌘ + Shift + Z), **Reset** auf die Vorgabe der Skripte und **Save** (Strg/⌘ + S). Wer mit ungespeicherten Änderungen geht, wird vorher gefragt.

**Vorlagen** in **+ Add** legt eine fertige Seite auf dein Dashboard – siehe [Dashboard-Vorlagen](presets.md).

**Save** speichert das Layout auf dem Server – alle Browser sehen es. Startet ein Skript neu, bleibt das Layout erhalten, weil die Widget-IDs gleich bleiben. Auf schmalen Bildschirmen stehen die Karten in Lesereihenfolge untereinander (auf Handys zweispaltig).

## Kartendesign

In den Einstellungen einer Karte wählst du einen **Hintergrund** und, wenn du magst, ein paar Zeilen **eigenes CSS** für die Karte und ihre [Teile](#card-parts). Alles wird mit dem Layout gespeichert, reist in Teilen-Codes und Vorlagen mit und funktioniert im hellen und im dunklen Theme. Ein Programm kann ein [eigenes Design](#card-script-design) mitbringen.

- **Hintergrund:** Standard, eine **Tönung** in einer der acht Akzentfarben, vier **Verläufe** (Accent, Aurora, Ember, Lagoon), vier **Muster** (Grid, Stripes, Dots, Circuit) oder **Custom** mit deiner eigenen Farbe. Tönungen, Verläufe und Muster folgen dem Theme und dem Akzent der Karte. Bei einer eigenen Farbe wählt die Karte selbst helle oder dunkle Schrift und legt, wo nötig, einen leichten Schleier über die Farbe, damit die Schrift lesbar bleibt.
- **Icons:** das Icon eines Widgets (in seinen Einstellungen) steht vor seiner Beschriftung – bei Zeilen, großen Zahlen, Gauges, Charts, Schaltern, Slidern und Eingabefeldern –, genau wie das Icon einer Karte in der Kopfzeile. Widgets ohne Beschriftung, etwa die Artikelsuche, haben keine Icon-Einstellung.

### Eigenes CSS

**Custom CSS** (zuerst eingeklappt) nimmt Deklarationen, eine pro Zeile, `eigenschaft: wert;`:

```css
background: linear-gradient(135deg, #1e3a5f, var(--panel));
border: 2px solid var(--accent);
border-radius: 12px;
letter-spacing: 0.04em;
```

Nur Deklarationen – keine Selektoren, keine `{ }`-Blöcke, keine `@`-Regeln. Unter dem Feld listet der Editor jede verworfene Deklaration mit Grund. Das kannst du nutzen:

| Was | Eigenschaften |
|---|---|
| Hintergrund | background, background-color, -image, -size, -position, -repeat, -blend-mode |
| Rahmen | border (auch -top, -right, -bottom, -left, -width bis 8 px, -color, -style), border-radius bis 32 px, box-shadow (nur inset) |
| Fläche | opacity, filter (blur, brightness, contrast, grayscale, hue-rotate, saturate, sepia; sanfte Werte) |
| Schrift | color, font-family (silkscreen, monospace, sans-serif, serif, system-ui), font-weight, font-style, letter-spacing, text-transform, ein kleiner text-shadow |

Werte dürfen Farben sein (`#hex`, `rgb()`, `hsl()`, `oklch()`, `color-mix()` …), Verläufe und die Theme-Tokens des Dashboards mit `var()`, z. B. `var(--accent)`, `var(--panel)`, `var(--text)`, `var(--muted)`, `var(--line)` – so folgt die Karte dem Theme. Einheiten: px, em, rem, %, deg.

**Warum so streng?** Ein Layout kann von jedem mit Bearbeitungsrecht stammen, aus einem Teilen-Code oder aus dem Store. Das Dashboard behandelt jede Zeile als nicht vertrauenswürdig und prüft sie bei jedem Zeichnen der Karte neu:

- **Nichts lädt oder läuft:** kein `url()`, `image-set()`, `attr()`, `expression` oder `javascript:`, keine Kommentare, Backslash-Escapes, Anführungszeichen oder `!important`, nur einfaches ASCII. Eine Karte kann nichts nachladen und nicht erfahren, wer sie ansieht.
- **Nichts verlässt die Karte:** kein `position`, `top`/`left`/`inset`, `z-index`, `transform`, `margin`, `width`/`height`, `content`, `pointer-events`, `cursor` und keine äußeren Schatten (das kleine Leuchten eines Teils bleibt in der Karte), und Zahlen haben Grenzen. Der Hintergrund liegt auf einer eigenen Ebene, auf die Karte zugeschnitten und nie einem Klick im Weg – eine Karte kann also keine anderen Karten, die PIN-Abfrage oder den Login verdecken.
- **Schrift bleibt lesbar:** eine Schriftfarbe zu nah am Hintergrund wird verworfen (und angezeigt).
- Der Server prüft dieselben Regeln, bevor er ein Layout speichert.

### Kartenteile

Unter **Parts**, unter Custom CSS, gestaltest du die Teile in einer Karte einzeln: Teil wählen, dann Deklarationen dafür schreiben wie oben. Ein Punkt markiert die Teile mit CSS. Jedes Teil nimmt nur die Eigenschaften seiner Zeile:

| Teil | Was es ist | Eigenschaften |
|---|---|---|
| title_bar | die Leiste mit dem Kartentitel | background, background-color, -image, border-bottom, box-shadow (inset) |
| title | der Titeltext | Schrift, font-size (10–22 px) |
| screen | die dunklen Kacheln von Gauges, Zahlen und Lampen | background, background-color, -image, border, border-color, border-radius, box-shadow |
| row | die Kästen von Label-Zeilen und Indikatoren | wie screen |
| label | die Beschriftung jedes Widgets | Schrift |
| value | die Werte: Zahlen, Texte, Zustände | Schrift |
| gauge_track | der unbeleuchtete Teil von Gauges und Balken | color (die Skala), background, background-color, -image, border, border-color, border-radius, box-shadow (inset) |
| gauge_fill | der beleuchtete Teil von Gauges und Balken | color (die Skala), background, background-color, -image, border-radius, box-shadow, filter |
| chart_line | die Linie von Charts | color (tönt auch die Fläche darunter), filter |
| button | jeder Button | background-color, background-image, color, border, border-color, border-radius, box-shadow, font-family, font-weight, letter-spacing, text-transform, text-shadow |
| toggle | der Schalter von Toggles (sein Hintergrund, solange er aus ist) | background, background-color, -image, border, border-color, border-radius, box-shadow (inset) |
| toggle_on | ein Schalter, solange er an ist | background, background-color, -image, border-color, box-shadow |

*Schrift* heißt color, font-family, font-weight, font-style, letter-spacing, text-transform und text-shadow. Ein leuchtendes Gauge, als **gauge_fill**:

```css
color: #5ef0ff;
box-shadow: 0 0 6px rgba(94, 240, 255, 0.7);
filter: drop-shadow(0 0 3px rgba(94, 240, 255, 0.9));
```

- **Leuchten:** screen, row, gauge_fill, button und toggle_on dürfen einen äußeren box-shadow haben, gauge_fill und chart_line ein `drop-shadow()` in filter. Nur px, Versatz bis 2 px, Unschärfe bis 6 px, Ausdehnung bis 2 px und zusammen höchstens 8 px – so bleibt es in der Karte.
- **Warnungen bleiben:** die Farben von gauge_fill, chart_line und value gelten im normalen Bereich. Im Warn- und im kritischen Bereich zeigt das Dashboard sein Gelb und Rot.
- Bis 512 Zeichen und 12 Deklarationen pro Teil, mit denselben Prüfungen wie Custom CSS. Eine Schriftfarbe zu nah am Hintergrund wird auch hier verworfen. Dialoge, die ein Widget öffnet (Artikelsuche, PIN-Feld, Bestätigungen), werden nie gestaltet.

### Das Design eines Programms

Ein Programm kann seiner Karte ein Design geben: `dash:style{...}` in [cloud.ui](ui-library.md) (eine Gruppe nimmt `style` in ihren Optionen), `setStyle` am [Uplink](lua.md) oder `cloud.setStyle(card, design)` in einem [Cloud Script](scripts.md). Ein Design ist eine Tabelle mit `bg`, `bgColor`, `css` und `parts` und wird geprüft wie im Editor:

```lua
-- the Reactor Console look: dark glass, a glowing gauge, monospace read-outs
dash:style({
  bg = "custom", bgColor = "#07121c",
  css = "border: 1px solid rgba(94, 240, 255, 0.3); border-radius: 14px;",
  parts = {
    title = "color: #7ff3ff; font-family: monospace; letter-spacing: 3px; text-transform: uppercase;",
    value = "color: #e9fdff; font-family: monospace; font-weight: 700;",
    gauge_fill = "color: #5ef0ff; filter: drop-shadow(0 0 3px rgba(94, 240, 255, 0.9));",
    button = "border: 1px solid rgba(255, 130, 120, 0.65); box-shadow: 0 0 6px rgba(255, 80, 70, 0.6); letter-spacing: 4px;",
  },
})
```

- Die Karte zeigt das Design des Programms, bis jemand das Design im Editor ändert. Die erste Änderung übernimmt das Design des Programms ins Layout, du änderst also nur, was anders sein soll. Ab dann gilt das Design aus dem Layout als Ganzes, und **Use the script's design** in den Einstellungen der Karte kehrt zum Design des Programms zurück.
- `cloud.setStyle` aus einem Cloud Script speichert das Design im Layout, wie der Editor (es braucht das Recht, das Layout zu bearbeiten); `cloud.setStyle(card)` entfernt es wieder.

## Meldungen

Programme senden Meldungen mit `cloud.alert(text, level)`. Sie erscheinen als Einblendung und bleiben im Protokoll unter der **Glocke**. Info-Meldungen verschwinden von selbst, warn und crit bleiben, bis du sie schließt. Mit **Enable notifications** im Protokoll bekommst du für kritische Meldungen System-Benachrichtigungen, auch wenn der Tab im Hintergrund ist. Das Protokoll kommt vom Server: Ein Browser, der sich neu verbindet, hat die letzten 50 noch (bis zum Neustart des Servers). Auf einem [Cloud Tablet](upgrades.md) stehen sie im Tab Alerts.

## PIN-geschützte Bedienelemente

Widgets mit `pin=true` fragen vor dem Auslösen die Mainframe-PIN über ein Ziffernfeld ab. Der Besitzer legt sie mit `/cccloud pin set <4-8 Ziffern>` fest. Nach 5 falschen PINs ist das Gerät 5 Minuten gesperrt und der Besitzer wird im Spiel benachrichtigt.

## Zustände

| Anzeige | Bedeutung |
|---|---|
| Dashboard disabled | das Dashboard ist im Mainframe-Bildschirm ausgeschaltet |
| Offline | der Chunk des Mainframes ist nicht geladen oder der Redstone-Not-Aus ist aktiv |
| Removed | der Mainframe-Block wurde abgebaut – sein Item wieder platzieren bringt alles zurück ([wie](index.md)) |
| Mainframe has no power | der FE-Puffer ist leer – siehe [Strom](index.md) |
| Uplink offline | dieser Uplink ist nicht geladen oder außer Reichweite |
| Gesperrt – diese Maschine liegt im Claim von … | ein anderes Team hat den Chunk der Maschine geclaimt – siehe [Claims](faq.md) |
| Read-only device | dieses Gerät darf zusehen, aber nicht steuern |
| Server not reachable | siehe [Fehlerbehebung](faq.md) |


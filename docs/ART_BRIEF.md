Du baust 3D-Blockmodelle + Texturen für einen Minecraft-Mod (NeoForge 26.1), ein Addon für CC: Tweaked.
Mod-ID: `cccloud`. Ziel: Die Blöcke sollen richtig hochwertig und "krass" aussehen – detailreich, mit Tiefe, Leuchteffekten und beweglichen Teilen – aber trotzdem eindeutig in den CC: Tweaked Stil passen.

## Referenzen
`~/ref/cct-reference/` enthält die Original-Assets von CC: Tweaked (aktueller Stand für MC 26.x):
- `textures/block/` – alle Block-Texturen (Computer, Monitore, Modems, Kabel, Disk Drive, Drucker, Lautsprecher, Turtles …)
- `textures/item/`, `textures/gui/` – Items und GUIs
- `models/block/`, `models/item/` – die Original-Modell-JSONs (z. B. `modem.json`, `cable_*.json`, `turtle_*.json`)

**Schritt 0, bevor du irgendwas zeichnest:** Sieh dir die Referenzen an und erstelle `preview/reference_sheet.png` (alle CC-Blocktexturen 8x hochskaliert nebeneinander). Schreib danach in `STYLE_NOTES.md` in 10–15 Stichpunkten, was du am CC-Stil beobachtest (Palette, Kontrast, Rahmenbreite, wie Schatten/Highlights gesetzt sind, wie Normal- und Advanced-Tier sich unterscheiden, wie flach/tief die Modelle sind). Halte dich beim Zeichnen an diese Notizen.

**Lizenz:** Die CC-Assets stehen unter der ComputerCraft Public License. Nur als Stilvorlage verwenden – keine Pixel, Texturen oder Modelle 1:1 kopieren oder in unseren Mod übernehmen. Alles neu zeichnen.

Palette (aus den Originalen ausgelesen):
- Normal-Tier: hellgraues Steingehäuse
- Advanced-Tier: Gold #ddd771 / #ddd973, Highlight #ecec9c, dunkel #2d2d2d / #303030, Screen #111111
- Cloud-Akzent: Cyan #5ec8e5, hell #a8e6f5, dunkel #2a7f99
- Status: grün #5fd35f, gelb #e8c547, rot #d34f4f, Ender-Grün #2fa37a

## Stil-Leitplanken: krass, aber es muss nach CC: Tweaked aussehen
Unsere Blöcke sollen die "Premium-Version" von CC sein – mehr Tiefe und Details, aber sie müssen neben einem Advanced Computer, einem Monitor und einem Ender Modem stehen können, ohne aufzufallen wie aus einem anderen Modpack.
- **Gleiche Materialsprache:** Steingrau (Normal) und Gold (Advanced) wie bei CC, dunkle Innenflächen, einfache 1-px-Rahmen. Cyan ist nur Akzent (LEDs, Linsen, Hologramm) – maximal ca. 10–15 % der sichtbaren Fläche.
- **Kein Sci-Fi-Overkill:** keine Neon-Flächen, keine Chrom-Effekte, keine Glitzer- oder Rauschtexturen, keine bunten Farbverläufe. Wenig Farben pro Textur (wie CC: ca. 6–10).
- **Gleicher Kontrast und gleiche Helligkeit wie die CC-Texturen** – nicht dunkler/greller. Prüfe das, indem du deine Texturen im reference_sheet direkt neben die passenden CC-Texturen legst (`preview/side_by_side.png`: Mainframe neben Advanced Computer, Uplink neben Ender Modem, Normal-Uplink neben Wired Modem).
- **Tiefe über Geometrie, nicht über gemalte Schatten:** Details entstehen durch echte Elemente (Einschübe, Lamellen, Rahmen), die Texturen bleiben so sauber wie bei CC.
- **Proportionen:** Der Uplink darf nicht größer wirken als ein CC-Modem plus Antenne; nichts ragt mehr als 4 px über den Block hinaus (Ausnahme: Hologramm über dem Mainframe).
- Wenn du zwischen "auffälliger" und "passt besser zu CC" abwägen musst: nimm "passt besser zu CC".

## Technische Regeln (Minecraft-Modellformat – unbedingt einhalten)
- Vanilla Java Block Model JSON (`elements`, `faces`, `uv`). Koordinaten in Pixeln 0–16 pro Block, erlaubt -16 bis 32.
- Element-Rotation nur um EINE Achse und nur -45, -22.5, 0, 22.5, 45 Grad.
- Max. ca. 120 Elemente pro Modell.
- Pixeldichte bleibt 16 px pro Block (1 Texel = 1/16 Block), sonst passt es nicht zu CC. Nutze pro Block einen Textur-Atlas (64x64 oder 128x128 PNG), UVs entsprechend.
- Leuchtende Teile (LEDs, Linsen, Kristalle) als eigene Elemente mit `"light_emission": 15`, damit sie im Dunkeln glühen.
- Glas als eigene Textur mit Alpha (wird als translucent gerendert).
- Teile, die sich bewegen (Lüfter, Satellitenschüssel, Hologramm, Kern), sind SEPARATE Modelle – die animiere ich per BlockEntityRenderer im Code. Gib für jedes bewegte Teil den Drehpunkt (Pivot in Pixeln) in einer `models/README.md` an.
- Texturen pixelgenau per Python-Skript (`tools/gen_textures.py`) zeichnen: Palette + ASCII-Pixelmaps, kein Bildmodell, kein Downscaling, kein Antialiasing. Licht von oben links.
- Modelle ebenfalls per Skript erzeugen (`tools/gen_models.py`), damit man sie leicht nachjustieren kann.

## 1. Cloud Mainframe – 2 Blöcke hoch (wie eine Tür: unterer + oberer Block)
Ein Server-Rack aus der Zukunft im CC-Look.
- Rahmen: vier goldene Eckpfosten (2x2 px), dazwischen dunkles Chassis, 1 px zurückgesetzt.
- Front: Rauchglas-Tür (translucent) 1 px vor dem Rack, mit goldenem Griff. Dahinter 6 Blade-Server-Einschübe, die unterschiedlich weit herausstehen (0,5–1 px Versatz), jeder mit Laufwerksschächten und einer leuchtenden LED-Leiste (Status-Farben, emissiv).
- Seiten: tiefe Lüftungsgitter (Lamellen als echte Elemente mit Vertiefung), auf einer Seite ein runder Lüfter 8x8 im Gehäuse → Lüfterblatt als separates Modell `mainframe_fan.json` (dreht sich).
- Rückseite: Port-Panel mit Cyan-Ports (emissiv), 3–4 dicke Kabel im CC-Kabel-Stil, die nach unten laufen.
- Oben: Hologramm-Projektor – runder Emitter-Ring 10x10 mit leuchtender Cyan-Linse, dazu 4 kleine Antennen unterschiedlicher Höhe an den Ecken.
- Hologramm `mainframe_hologram.json`: Voxel-Wolke (ca. 10x6x6 px aus kleinen Würfeln) halbtransparent Cyan, schwebt über dem Projektor. Wird im Code gedreht, wippt auf und ab und flackert, wenn der Mainframe offline geht.
- Dateien: `mainframe_lower.json`, `mainframe_upper.json`, `mainframe_fan.json`, `mainframe_hologram.json`, Atlas `mainframe.png`, `mainframe_glass.png`, `mainframe_hologram.png`.
- Zustände: Ich brauche die LED/Linsen-Texturen in drei Varianten (online grün/cyan, verbinde gelb, offline rot/aus): `mainframe_leds_online.png`, `..._connecting.png`, `..._offline.png` (oder ein Atlas, in dem nur der LED-Bereich wechselt).
- Item-Modell `mainframe_item.json`: verkleinerte Gesamtansicht, gute `display`-Werte für GUI, Hand, Boden.

## 2. Cloud Uplink – kompaktes Antennenmodul, das an einem Block klebt
Modell für die Ausrichtung "klebt an Nordseite" (Blockstate dreht es in alle 6 Richtungen).
- Montageplatte 10x10x2 an der Blockseite, mit 4 Schrauben.
- Gehäuse 6x6x4, gestuft/abgeschrägt (gestaffelte Elemente), seitliche Kühlrippen.
- Statuskristall 2x2x2 oben auf dem Gehäuse, emissiv (grün/gelb/rot über drei Texturvarianten).
- Mast 1x1x5 mit Satellitenschüssel 6x6, parabolisch aus 3 gestuften Ringen → Schüssel als separates Modell `uplink_dish.json` (dreht sich langsam).
- Normal-Tier: Steingrau mit Cyan-Akzenten. Dateien: `uplink.json`, `uplink_dish.json`, Atlas `uplink.png`.
- Advanced-Tier: Goldtrims, statt Schüssel ein schwebender Ender-Kern (Oktaeder aus 45°-gedrehten Würfeln, Ender-Grün + Cyan, emissiv), der rotiert. Dateien: `uplink_advanced.json`, `uplink_advanced_core.json`, Atlas `uplink_advanced.png`.
- Zusätzlich ein leuchtender Cyan-Ring um die Montageplatte (eigenes Element `direct_ring`, emissiv) – wird eingeblendet, wenn der Uplink direkt an einer Maschine statt an einem Computer klebt.

## 3. Items (flach, 16x16, transparent)
- `item/cloud_link_card.png` – Keycard, hellgrau, goldener Chip, Cyan-Wolke.
- `item/cloud_core.png` – leuchtender Cyan-Kristall in goldener Fassung.

## 4. Partikel
- `particle/data_packet.png` – 4x4 px leuchtender Cyan-Würfel (fliegt im Spiel vom Uplink zum Mainframe, wenn ein Befehl kommt).

## 5. Vorschau (wichtig, damit ich es prüfen kann)
- `preview/viewer.html`: eine einzelne HTML-Datei mit three.js (vom CDN, feste Version), die die Modell-JSONs + Texturen lädt (nearest-neighbor Filter!) und anzeigt: drehbare Kamera, Schalter für Zustand online/verbinde/offline, Tag/Nacht (Nacht = nur emissive Teile hell), Animationen der bewegten Teile.
- `preview/contact_sheet.png`: alle Texturen 8x hochskaliert mit Dateinamen.

Ausgabe-Pfade: `src/main/resources/assets/cccloud/models/block/`, `.../models/item/`, `.../textures/block/`, `.../textures/item/`, `.../textures/particle/`.
Am Ende: liste alle Dateien, die Element-Anzahl pro Modell und die Pivot-Punkte der bewegten Teile auf, und zeig mir `preview/side_by_side.png` und `preview/contact_sheet.png`.

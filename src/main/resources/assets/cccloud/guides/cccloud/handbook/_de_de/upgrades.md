---
# Aus der Dashboard-Doku erzeugt (tools/gen_handbook.ts) – Änderungen in cloud/web/src/docs/content machen.
navigation:
  title: "Upgrades, Tablet & Display"
  position: 7
  icon: cccloud:cloud_tablet
---

# Upgrades, Tablet und Display

Neu in v0.7: Mainframe-Upgrades für große Basen, das **Cloud Tablet** fürs Dashboard im Spiel und das **Cloud Display** für Karten an der Wand.

## Mainframe-Upgrades

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
- Die Rezepte nutzen die Metalle des Packs (`#c:ingots/allthemodium` usw.). Ohne AllTheModium gibt es Ersatzrezepte mit Diamant, Netherit und Netherstern. Nachschauen in JEI/EMI.
- **Über der Grenze:** Neue Verbindungen werden mit einer klaren Meldung abgelehnt. Nimmst du eine Karte heraus, zeigen die überzähligen Geräte **Over capacity** und pausieren (die mit den meisten Sprüngen bzw. zuletzt verbundenen zuerst), bis wieder Platz ist.
- Das Dashboard zeigt die genutzte Kapazität (z. B. „12.5 / 48 devices“) im Network-Tab und im Strom-Popup; Geräte an Glasfaser zählen halb.

## Cloud Tablet

Ein Handgerät, das dein Dashboard **im Spiel** öffnet – ein eigenes Fenster mit denselben Karten und Widgets, ganz ohne Browser.

- Funktioniert in Netzwerk-Reichweite jedes Knotens (Mainframe oder Relay). Das **Advanced Cloud Tablet** funktioniert zusätzlich überall, wo ein Ender Relay hinreicht.
- Nutzt **deine Rolle** auf diesem Mainframe: Viewer schauen zu, Operatoren steuern, Admins bearbeiten.
- Optionale Taste zum Öffnen (standardmäßig nicht belegt; unter Steuerung einstellen), solange das Tablet im Inventar oder in einem Curios-Slot ist.
- Hergestellt aus einem CC-Pocket-Computer und einem Cloud Core (Advanced: dazu Gold und eine Enderperle).

## Cloud Display

Ein Display-Block, der eine Dashboard-Karte oder -Seite **in der Welt** zeigt, wie ein CC-Monitor.

- Mehrere nebeneinander ergeben einen größeren Bildschirm (Rechtecke bis **8 × 6**).
- Per Rechtsklick in seinem Fenster Karte oder Seite wählen. Standardmäßig nur zum Ansehen; Operatoren können **klickbare Buttons** erlauben – dann drückt ein Rechtsklick auf einen Button im Display ihn.
- Aktualisiert höchstens 4× pro Sekunde und wird ab 48 Blöcken Entfernung nicht mehr gezeichnet – schont Server und FPS.

## Drahtloses Redstone

Uplinks bekommen in ihrem Fenster einen **Redstone-Modus**: **Sender** oder **Empfänger** auf einem Kanalnamen. Empfänger geben das stärkste Signal aller Sender auf diesem Kanal aus (0–15). Das funktioniert über Relays, Kabel und – mit Ender Relays – dimensionsübergreifend, mit dem nächsten Tick als Verzögerung. Bis zu 64 Kanäle pro Netzwerk; sehr schnelle Takte (schneller als 2 Ticks) werden geglättet.

Im Dashboard listet der Chip **Redstone** im Network-Tab jeden Kanal mit Pegel, Sendern und Empfängern. Spieler mit dem Recht „Switch redstone“ (ab operator) können einen Kanal **übersteuern** – ein Pegel von 0 bis 15 für 30 s, 5 min, 1 h oder bis zum Zurücksetzen –, etwa um vom Handy aus eine Tür zu öffnen. Die Übersteuerung gewinnt gegen die Sender, bis sie abläuft oder du sie aufhebst; jede wird protokolliert.


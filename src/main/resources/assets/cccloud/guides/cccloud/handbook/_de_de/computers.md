---
# Aus der Dashboard-Doku erzeugt (tools/gen_handbook.ts) – Änderungen in cloud/web/src/docs/content machen.
navigation:
  title: "Cloud-Computer"
  position: 5
  icon: cccloud:advanced_cloud_computer
item_ids:
  - cccloud:cloud_computer
  - cccloud:advanced_cloud_computer
---

# Cloud-Computer

Ein Computer, den du in die Welt stellst – ohne weitere Mod. Er führt Programme auf der eigenen Script-Engine von CC: Cloud aus, zeigt sie auf seinem Bildschirm, liest und setzt Redstone und erscheint im Dashboard wie jeder Computer.

| Block | Bildschirm | Maus |
|---|---|---|
| **Cloud-Computer** | 51×19 Zeichen, Graustufen | – |
| **Erweiterter Cloud-Computer** | 51×19 Zeichen, 16 Farben | Klicken, Ziehen, Scrollen |

## Benutzen

1. Herstellen und aufstellen (Rezepte [unten](computers.md)). Er schaut dich an; die Bildschirmseite ist **vorne**.
2. **Rechtsklick** öffnet den Bildschirm. Ist er aus, klick auf den Bildschirm, um ihn einzuschalten.
3. Tippen wie auf einer Tastatur; **Strg+V** fügt eine Zeile ein (bis 512 Zeichen). **Esc** schließt den Bildschirm, der Computer läuft weiter.
4. Eine Sekunde **halten**: **Strg+T** stoppt das laufende Programm, **Strg+R** startet den Computer neu, **Strg+S** fährt ihn herunter.

Abbauen behält den Computer: das Item merkt sich Nummer und Namen, und wieder aufgestellt sind seine Dateien wieder da. Zurück bekommt ihn nur sein Besitzer – stellt ein anderer Spieler das Item auf, bekommt er einen neuen, leeren Computer.

## Redstone

Programme benennen die Seiten so, wie der Computer sie sieht: **front** (der Bildschirm), **back**, **left**, **right**, **top** und **bottom**. Ausgänge versorgen wie ein Redstone-Block (stark, 0–15); Eingänge lesen das stärkste Signal auf der Seite. Herunterfahren schaltet alle Ausgänge ab.

## Im Dashboard

Setz einen [Cloud Uplink](index.md) auf den Computer und verbinde ihn: er bekommt die automatische **Computer**-Karte (Strom, Name, Redstone je Seite), das Browser-Terminal und den Script-Editor – genau wie ein CC: Tweaked-Computer.

## Dateien und Grenzen

Jeder Computer hat ein eigenes Laufwerk mit **1 MB**; `rom` enthält die eingebauten Programme und ist schreibgeschützt. Eine **Programmdatei darf höchstens 256 KB** groß sein – eine größere bricht mit *program too large, max 256 KB* ab (größere Daten in eigene Dateien legen und mit `fs.open` lesen). Offene Dateien halten zusammen höchstens die Größe des Laufwerks. Ein Programm bekommt einen Anteil der Serverzeit; eines, das zu lange läuft, ohne auf ein Ereignis zu warten (`os.pullEvent`, `sleep`), bricht mit *too long without yielding* und der Zeile ab, in der es passiert ist, und der Computer geht aus.

## Rechte

- Wer einen Computer aufstellt, besitzt ihn. Besitzer, die Mitglieder seines Teams (FTB Teams) und Server-Admins dürfen den Bildschirm öffnen, tippen und ihn ein- und ausschalten; alle anderen bekommen eine Meldung.
- Ein Computer, den eine Maschine oder ein Befehl aufgestellt hat, hat keinen Besitzer und ist für alle offen.
- Ein Computer im Claim eines anderen Teams fährt herunter und lässt sich nicht benutzen, bis der Claim es erlaubt – siehe [Claims](faq.md).

## Rezepte

<RecipesFor id="cccloud:cloud_computer" fallbackText="Dieses Rezept gibt es in diesem Modpack nicht (eine benötigte Mod fehlt oder das Pack hat es geändert)." />

<RecipesFor id="cccloud:advanced_cloud_computer" fallbackText="Dieses Rezept gibt es in diesem Modpack nicht (eine benötigte Mod fehlt oder das Pack hat es geändert)." />


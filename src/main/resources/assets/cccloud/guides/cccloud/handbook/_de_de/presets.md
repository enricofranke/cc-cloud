---
# Aus der Dashboard-Doku erzeugt (tools/gen_handbook.ts) – Änderungen in cloud/web/src/docs/content machen.
navigation:
  title: "Dashboard-Vorlagen"
  position: 3
  icon: cccloud:cloud_display
---

# Dashboard-Vorlagen

Eine **Vorlage** ist eine fertige Dashboard-Seite. Sie sagt, welche Arten von Geräten sie braucht und wo deren Widgets hinkommen. Dein Dashboard passt sie an deine Geräte an und legt sie als **neue Seite** an, was du schon gebaut hast, bleibt also, wie es ist.

Eine Vorlage ist reine Layout-Beschreibung: kein Lua, nichts, was läuft, nichts, was installiert wird.

## Dein erstes Dashboard

Solange niemand das Dashboard eines Mainframes eingerichtet hat, sehen alle, die das Layout bearbeiten dürfen, über den Karten **Mit einer Vorlage starten**: die offizielle Vorlage, die am besten zu dieser Basis passt, einen Klick entfernt. Die neue Seite kommt nach vorne, deine automatischen Karten bleiben auf ihren Seiten. **Rückgängig** in der folgenden Meldung stellt alles wieder her.

**Nicht jetzt** blendet das Angebot für diesen Mainframe in diesem Browser aus. Andere Mainframes fragen weiter, und der Tab **Vorlagen** bleibt erreichbar. Eine leere Seite bietet ebenfalls **Vorlage hinzufügen** an.

## Vorlage hinzufügen

1. Tippe auf den **Stift**, dann auf **+ Add** und auf den Tab **Vorlagen**.
2. Wähle, woher die Vorlagen kommen: **Offiziell**, **Community** oder **Code**.
3. Such dir eine aus. Jede Vorlage sagt, wie sie zu dieser Basis passt: **Passt**, **Teilweise** oder **Geräte fehlen**. Die beste ist als empfohlen markiert.
4. Prüfe die Live-Vorschau und was die Vorlage benutzt, dann tippe auf **Als neue Seite hinzufügen**.

Die Vorlage landet auf neuen Seiten, sonst ändert sich nichts. Wie bei jeder Änderung im Editor nimmt **Rückgängig** sie zurück und **Save** behält sie. Ein Dashboard hat höchstens 12 Seiten. Braucht eine Vorlage mehr freie Seiten, als da sind, sagt sie das, und du löschst zuerst eine Seite.

| Offizielle Vorlage | Gemacht für |
|---|---|
| Basis-Übersicht | die wichtigsten Werte aller Geräte; passt zu jeder Basis |
| Stromnetz | alles, was Strom speichert, erzeugt und misst |
| ME/RS-Leitstand | Kapazität, Item-Suche, Merkliste und Crafting-Aufträge |
| Reaktorsteuerung | Kernwerte und Grenzen neben den Schaltern, Tanks auf einer zweiten Seite |
| Farmen und Produktion | Farmen, Maschinen und volle Ausgänge, Produktion auf einer zweiten Seite |
| Türen und Redstone | Türen, Tore und Redstone-Ausgänge mit deinen Szenen |
| Alarme und Status | jede Warnleuchte und jeder Wert mit Grenze |
| Handy-Schnellzugriff | eine schmale Spalte fürs Handy |

Offizielle Vorlagen gehören zur Mod und brauchen weder Store noch Internet.

## Welches Gerät wohin kommt

Eine Vorlage hat **Plätze**: „ein Akku“, „bis zu acht Farmen“. Das Dashboard füllt jeden Platz mit den Geräten, die es sieht, funktionierende zuerst, und jedes Gerät füllt einen Platz. Passen mehr Geräte, als ein Platz fasst, fragt die Vorlage nach; sonst gibt es nichts zu wählen.

Ein Platz kann optional sein. Bleibt er leer, fällt dieser Teil der Vorlage weg. Bleibt ein nötiger Platz leer, sagt die Vorlage, was fehlt, und lässt sich noch nicht hinzufügen.

## Eine Seite teilen

Öffne die Seite, tippe auf den **Stift**, geh auf **+ Add**, **Vorlagen** und nimm **Diese Seite als Vorlage teilen**. Du bekommst einen **Code**, der mit `ccp1.` beginnt. Er enthält das Layout und welche Art Gerät jeder Teil braucht: keine Geräte-IDs, keine Werte, keine Koordinaten. Karten mit Gerätenamen tragen auf der anderen Basis den Namen des Geräts, das sie füllt.

Um einen Code zu benutzen, füge ihn unter **Code** ein und tippe auf **Code prüfen**. Ein Code braucht weder Server noch Internet. Er trägt eine Prüfsumme: Ein abgeschnittener Code wird mit einer klaren Meldung abgelehnt, statt halb zu funktionieren, und Grenzen verhindern, dass ein Code zu groß wird.

Einen Code kann jede Person schreiben, deshalb ist ein eingefügter Code als **Community, unsigniert** markiert. Er kann nichts ausführen, aber sieh dir an, was er auf dein Dashboard legt, bevor du ihn hinzufügst.

## Community-Vorlagen

Der Tab **Community** zeigt die Vorlagen des CC: Cloud-Stores. Dein **Server** lädt sie, prüft Signatur und SHA-256 und gibt sie erst dann ans Dashboard; der Browser lädt selbst nichts. Dafür brauchst du das Recht, das Layout zu bearbeiten. Ist auf dem Server der [Script Store](lua.md) ausgeschaltet, gibt es keine Community-Vorlagen. Offizielle Vorlagen und lange Codes funktionieren weiter.

Jede Store-Vorlage hat außerdem einen **Kurzcode**, ihre ID, zum Beispiel `door-panel`. Füge ihn unter **Code** ein. Die Website zeigt jede Store-Vorlage mit Kurz- und Langcode.

Eine Store-Vorlage kann das Skript nennen, für das sie gemacht ist. Das Dashboard bietet dann einen Knopf in den Script Store, wo die üblichen Schritte gelten: Du siehst den ganzen Quelltext und entscheidest selbst. Die Vorlage installiert nichts.

Um eine Vorlage zu veröffentlichen, starte mit einer geteilten Seite und folge der Anleitung zum Einreichen auf der Website.

## Was eine Vorlage nicht kann

- **Etwas ausführen.** Sie enthält Positionen, Titel, Icons und Farben. Das Dashboard prüft jedes Feld gegen ein festes Format und lehnt alles andere ab.
- **Ein Bedienelement ändern.** Buttons, Schalter und Slider behalten, was sie tun, ihren Namen, ihre Bestätigung und ihre PIN. Wer sie benutzen darf, bleibt gleich, und eine Vorlage kann sie nicht umbenennen.
- **Mehr zeigen, als du sehen darfst.** Eingeschränkte Bereiche (Claims, Waystones, die Zahlen des Servers) nutzt nur ein Platz, der ausdrücklich danach fragt, und sie erscheinen nur für Personen, die sie sehen dürfen. In einem [Freigabe-Link](accounts.md) sind sie nie enthalten.
- **Von selbst handeln.** Es ändert sich nichts, bis jemand mit dem Recht, das Layout zu bearbeiten, den Knopf drückt.


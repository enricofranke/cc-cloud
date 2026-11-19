---
# Aus der Dashboard-Doku erzeugt (tools/gen_handbook.ts) – Änderungen in cloud/web/src/docs/content machen.
navigation:
  title: "Bridges (AE2 / RS)"
  position: 14
  icon: cccloud:cloud_core
item_ids:
  - cccloud:me_cloud_bridge
  - cccloud:rs_cloud_bridge
---

# Bridges: AE2 und Refined Storage

Die **ME Cloud Bridge** (Applied Energistics 2) und die **RS Cloud Bridge** (Refined Storage) verbinden ein Lagernetzwerk mit dem Dashboard. Es gibt sie nur, wenn der jeweilige Mod installiert ist.

## Einrichten

1. Bridge craften ([Rezept](index.md)) und wie jedes andere Gerät **ins Netzwerk** setzen (ME: belegt einen Kanal; RS: ein Netzwerkknoten).
2. Einen **Cloud Uplink direkt auf die Bridge** setzen und den Uplink mit der Link Card verbinden.
3. Das Dashboard zeigt eine fertige **Storage**-Karte – ohne Computer, ohne Code.

Die Bridge nutzt den Strom des Netzwerks, kein FE vom Mainframe.

## Die Lager-Karte

- Anzeigen: belegter Speicher, Energie
- Werte: Items gesamt, Item-Typen, Energieverbrauch
- **Suche**: Name oder ID eintippen, Anzahl, „craftable“-Abzeichen und Mod jedes Items sehen
- **Merkliste** und **Crafting-Aufträge** (siehe unten)

## Craften im Dashboard

Ein craftbares Item antippen → **1 / 16 / 64 / eigene Menge** wählen → **Craft**. Der Auftrag erscheint unter den Crafting-Aufträgen mit Fortschrittsbalken. Fehler werden erklärt: kein Muster, alle CPUs belegt, Menge zu groß (Grenze `maxCraftAmount`, Standard 100000). Craften dürfen nur Geräte mit Steuerrechten.

**Nicht genug Items:** Der Dialog zeigt, was fehlt (vorhanden / nötig) und, wenn der Mod es sagen kann, wie viele jetzt möglich sind – **Craft 37** macht genau diese. **Order anyway** behält die ganze Menge: Der Auftrag wartet als *Waiting for items* und startet von selbst, sobald die Items da sind. Bestellungen überleben einen Neustart, bis zu 8 pro Bridge, und lassen sich abbrechen. Ein Lua-`craft()` oder eine Regel wartet genauso; `getCraftingJobs()` zeigt die Liste `missing`.

**Stuck:** Ein Auftrag, der sich `craftStuckSeconds` lang nicht bewegt (Server-Config, Standard 60, 0 = aus), bekommt ein rotes **Stuck**-Abzeichen mit Grund, einen Alarm und kann eine Regel starten („a craft gets stuck“). AE2 sagt nicht, welche Maschine hängt, also den Wert über die langsamste Maschine legen. **Retry** bricht den Auftrag ab und fordert denselben Craft neu an; das gibt es nur für Crafts aus dem Dashboard, weil ein Lua-Programm oder eine Regel selbst neu anfragen kann.

## Factory-Manager-Programm

Der Knopf **\</>** auf einer Factory-Manager-Karte zeigt das Programm auf der Disk mit allen Fehlern und Warnungen und einem Kopier-Knopf. Es verrät den Aufbau der Basis, braucht daher „See coordinates“ und erscheint nie auf Share-Links oder Displays.

Mit **Write machine programs** (`device.program`, Admin und Besitzer) lässt es sich bearbeiten, SFML einfügen oder eine .sfml-, .sfm- oder .txt-Datei bis 32 KB laden. **Check and review** kompiliert und zeigt alte und neue Zeilenzahl und die Änderungen. Ein Programm mit Fehlern wird nur nach Bestätigung geschrieben, denn dann läuft der Manager nicht mehr. Die PIN wird abgefragt, wenn der Mainframe eine hat, jeder Schreibvorgang steht im Audit-Log, und **Undo last change** stellt das alte Programm einmal wieder her, bis zum Server-Neustart.

## Merkliste

Füge Items mit einer Mindestmenge hinzu (aus einem Suchergebnis oder per ID). Items unter ihrem Minimum stehen rot ganz oben und lösen pro Unterschreitung einmal eine **warn**-Meldung aus. Minimum ändern oder Items entfernen geht jederzeit. Die Merkliste wird in der Bridge gespeichert.

## Durchsuchen, Auto-Lager und Crafting-CPUs

- **Browse** (neben der Suche): alle Items als Raster mit Icons – sortiert nach Menge, Name oder Mod, mit einem Tab **Craftable**, **Fluids** und ★ **Favoriten** oben angepinnt. Weiter scrollen lädt mehr.
- **Item-Details:** Menge, ein 24-Stunden-Verlauf, letzte Aufträge, ★, **Craft…** und **Keep in stock…**.
- **Crafting-Vorschau:** Bevor ein Auftrag startet, siehst du, was aus dem Lager genommen, was gecraftet wird und was fehlt, dazu freie CPUs. Mit **Notify me** bekommst du eine Meldung, wenn er fertig ist oder fehlschlägt.
- **Auf Lager halten (Auto-Lager):** Minimum und Ziel festlegen – fällt die Menge unter das Minimum und es gibt ein Muster, craftet der Server in Paketen zu 64 bis zum Ziel (ein Auftrag pro Item, höchstens alle 30 s). Das Abzeichen zeigt den Zustand: crafting, waiting for a CPU, missing resources oder nach 3 Fehlschlägen pausiert. Solange alle Crafting-CPUs belegt sind, hat das Item eine Zeile „Waiting for CPU“ in den Crafting-Aufträgen – es wird alle 30 s neu versucht und zählt nie als Fehlschlag. Läuft auf dem Server – kein Computer nötig. Aus Lua: `bridge.setAutoStock(name, min, target, enabled)`.
- **CPUs & Aufträge:** jede Crafting-CPU mit ihrem aktuellen Auftrag, alle laufenden Aufträge und **Cancel** (Operatoren).
- **Produktion:** was das Netz pro Minute dazubekommt und verbraucht und was ein Item frisst – siehe [Produktionsstatistik](automation.md).

## Aus Lua

```lua
local bridge = peripheral.find("me_cloud_bridge") or peripheral.find("rs_cloud_bridge")
local s = bridge.getStorage()
print(("Items: %d / %d"):format(s.itemsUsed, s.itemsTotal))
for _, item in ipairs(bridge.findItems("iron", 5)) do
  print(item.displayName, item.count, item.craftable and "(craftable)" or "")
end
local result = bridge.craft("minecraft:iron_block", 4)
if result.ok then print("Job " .. result.jobId) else print("Error: " .. result.error) end
```

Eine eigene Lager-Karte mit denselben Widgets:

```lua
local cloud = peripheral.find("cloud_uplink")
local bridge = peripheral.find("me_cloud_bridge")
local name = peripheral.getName(bridge)
cloud.setTitle("Storage")
cloud.itemSearch("search", "Search & craft", {bridge=name, max=1024})
cloud.watchlist("watch", "Watchlist", {bridge=name})
cloud.jobs("jobs", "Crafting jobs", {bridge=name})
```

Oder einfach `cloud-storage` starten. Alle Methoden: [Lua-Referenz → Bridges](lua.md).


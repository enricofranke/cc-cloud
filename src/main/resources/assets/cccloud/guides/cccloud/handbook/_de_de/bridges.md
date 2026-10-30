---
# Aus der Dashboard-Doku erzeugt (tools/gen_handbook.ts) – Änderungen in cloud/web/src/docs/content machen.
navigation:
  title: "Bridges (AE2 / RS)"
  position: 11
  icon: cccloud:cloud_core
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

Ein craftbares Item antippen → **1 / 16 / 64 / eigene Menge** wählen → **Craft**. Der Auftrag erscheint unter den Crafting-Aufträgen mit Fortschrittsbalken. Fehler werden erklärt: kein Muster, alle CPUs belegt, fehlende Zutaten, Menge zu groß (Grenze `maxCraftAmount`, Standard 4096). Craften dürfen nur Geräte mit Steuerrechten.

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


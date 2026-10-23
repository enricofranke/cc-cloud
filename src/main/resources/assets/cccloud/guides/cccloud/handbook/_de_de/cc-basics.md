---
# Aus der Dashboard-Doku erzeugt (tools/gen_handbook.ts) – Änderungen in cloud/web/src/docs/content machen.
navigation:
  title: "CC-Grundlagen"
  position: 1
  icon: computercraft:computer_advanced
---

# CC-Grundlagen für Einsteiger

Neu bei ComputerCraft? Hier steht das Wenige, das du für ein Dashboard-Programm brauchst.

## Shell oder Lua?

Ein Computer startet in der **Shell** – einer Kommandozeile wie im Terminal. Die Shell startet **Programme** über ihren Namen (`edit`, `ls`, `cloud-demo`). Lua-Code versteht sie **nicht**.

> **Achtung:** Tippst du Lua direkt in die Shell, z. B. `cloud.setTitle("x")`, kommt **„No such program“**. Die Shell sucht dann ein Programm namens `cloud.setTitle("x")`. Schreib Lua-Code in eine Datei (siehe unten) oder nutze die `lua`-Eingabe.

## Ein Programm mit edit schreiben

```
edit startup.lua
```

1. `edit startup.lua` eingeben und Enter drücken. Ein Editor öffnet sich.
2. Code eintippen oder einfügen. (Aus dem Dashboard: mit **Copy** kopieren, dann in Minecraft Strg+V.)
3. **Strg** drücken, mit den Pfeiltasten **Save** wählen und Enter, dann **Strg** → **Exit**.

Eine Datei namens `startup.lua` startet automatisch, wenn der Computer hochfährt – ideal für Dashboards.

## Ein Programm starten

Gib seinen Namen ohne `.lua` ein:

```
startup
```

Ein laufendes Programm stoppst du mit **Strg+T** (eine Sekunde gedrückt halten). Neustart mit **Strg+R**.

## Dateien auf den Computer ziehen

Schreib den Code am PC in einem beliebigen Editor, speichere ihn als `startup.lua` und **zieh die Datei auf den geöffneten Computerbildschirm** in Minecraft. CC: Tweaked lädt sie in den aktuellen Ordner hoch.

## Schnell testen mit der lua-Eingabe

`lua` öffnet eine interaktive Lua-Eingabe – praktisch, um einzelne Zeilen auszuprobieren:

```
lua
lua> peripheral.find("cloud_uplink").getClients()
1
lua> exit()
```

## Nützliche Befehle

| Befehl | Macht |
|---|---|
| `ls` | Dateien auflisten |
| `edit <datei>` | Datei bearbeiten oder anlegen |
| `rm <datei>` | Datei löschen |
| `help cloud` | Kurzreferenz zu CC: Cloud |
| `cloud-demo` | zeigt alle Dashboard-Widgets |
| `cloud-storage` | eigenes Lager-Dashboard für eine ME / RS Bridge |
| `peripheral` | in Lua: `peripheral.getNames()` listet angeschlossene Blöcke |

Weiter: [Lua-Referenz](lua.md) oder die [cloud.ui-Bibliothek](ui-library.md).


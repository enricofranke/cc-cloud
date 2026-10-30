#!/usr/bin/env python3
"""Generates the FTB Quests chapters for CC: Tweaked and CC: Cloud (ATM11 format).

Source of truth for every quest (layout, tasks, rewards, en_us + de_de text).
Output (relative to integration/ftbquests/):
  quests/chapters/cc_tweaked.json5, quests/chapters/cc_cloud.json5
  quests/lang/<locale>/chapters/<chapter>.json5
  quests/lang/<locale>/chapter.additions.json5                              (merged into chapter.json5)

Ids are stable: they live in tools/ids.json. New symbolic names get a fresh random id that is
checked against every id of the ATM11 quest book (pass --pack <instance>/config/ftbquests/quests).

Usage: python3 tools/generate.py [--pack <quests dir>]
"""
import json
import os
import random
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
IDS_FILE = os.path.join(HERE, "ids.json")

# ATM11 chapter group "Tech" (chapter_groups.json5). Existing Tech chapters use order_index 0..11.
GROUP_TECH = "2B51AC12041E3F89"

# ATM11 generic reward tables (reward_tables/common.json5 etc.), decimal form as used by "random" rewards.
TABLES = {
    "common": 487623848494439020,     # 06C4634E81851A6C "Reward: Common"
    "uncommon": 4196188979167302596,  # 3A3BDBA4E9AD13C4 "Reward: Uncommon"
    "rare": 5564196992594175882,      # 4D37FFAF33DF678A "Reward: Rare"
}

# ---------------------------------------------------------------------------------------------
# ids
# ---------------------------------------------------------------------------------------------
HEX16 = re.compile(r"\b[0-9A-F]{16}\b")


def pack_ids(pack_dir):
    found = set()
    if not pack_dir:
        return found
    for base, _, files in os.walk(pack_dir):
        for f in files:
            if f.endswith(".json5") or f.endswith(".snbt"):
                with open(os.path.join(base, f), encoding="utf-8") as fh:
                    found.update(HEX16.findall(fh.read()))
    return found


class Ids:
    def __init__(self, pack_dir):
        self.map = {}
        if os.path.exists(IDS_FILE):
            with open(IDS_FILE, encoding="utf-8") as fh:
                self.map = json.load(fh)
        self.taken = pack_ids(pack_dir) | set(self.map.values())
        self.pack_known = bool(pack_dir)
        self.used = set()
        self.rng = random.SystemRandom()

    def get(self, name):
        self.used.add(name)
        if name not in self.map:
            if not self.pack_known:
                sys.exit(f"new id needed for '{name}': run with --pack <instance>/config/ftbquests/quests")
            while True:
                # same range as the pack's ids: positive 64-bit, first hex digit 0-7
                v = "%016X" % self.rng.randrange(0x0100000000000000, 0x7FFFFFFFFFFFFFFF)
                if v not in self.taken:
                    break
            self.taken.add(v)
            self.map[name] = v
        return self.map[name]

    def save(self):
        unused = sorted(set(self.map) - self.used)
        for name in unused:  # keep the file tidy, ids of removed quests are dropped
            del self.map[name]
        with open(IDS_FILE, "w", encoding="utf-8") as fh:
            json.dump(dict(sorted(self.map.items())), fh, indent=2)
            fh.write("\n")


# ---------------------------------------------------------------------------------------------
# FTB-style JSON5 writer (unquoted keys, trailing commas, 2-space indent, floats with .0)
# ---------------------------------------------------------------------------------------------
IDENT = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")


class F(float):
    """Marks a number that must be written as a float (x, y, size)."""


def _key(k, quote_all):
    return json.dumps(k, ensure_ascii=False) if quote_all or not IDENT.match(k) else k


def emit(v, ind=0, quote_all=False):
    pad = "  " * ind
    if isinstance(v, dict):
        if not v:
            return "{ }"
        out = ["{"]
        for k, val in v.items():
            out.append(f"{pad}  {_key(k, quote_all)}: {emit(val, ind + 1, quote_all)},")
        out.append(pad + "}")
        return "\n".join(out)
    if isinstance(v, list):
        if not v:
            return "[\n" + pad + "]"
        out = ["["]
        for val in v:
            out.append(f"{pad}  {emit(val, ind + 1, quote_all)},")
        out.append(pad + "]")
        return "\n".join(out)
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, F):
        return repr(float(v))
    if isinstance(v, (int, float)):
        return str(v)
    return json.dumps(v, ensure_ascii=False)


# ---------------------------------------------------------------------------------------------
# quest helpers
# ---------------------------------------------------------------------------------------------
def item(id_, count=1, components=None):
    d = {"id": id_, "count": 1}
    if components:
        d["components"] = components
    return ("item", d, count)


def anyof(icon_id, ids, icon_components=None):
    flt = "or(" + "".join(f"item({i})" for i in ids) + ")"
    return ("filter", flt, icon_id, icon_components)


def filt(flt, icon_id, icon_components=None):
    return ("filter", flt, icon_id, icon_components)


def check(en, de):
    return ("check", en, de)


def Q(key, x, y, title, sub, desc, tasks, rewards=(), deps=(), icon=None, shape=None, size=1.0,
      dep_req=None, optional=False, hide_lines=False):
    return dict(key=key, x=x, y=y, title=title, sub=sub, desc=desc, tasks=tasks, rewards=list(rewards),
                deps=list(deps), icon=icon, shape=shape, size=size, dep_req=dep_req, optional=optional,
                hide_lines=hide_lines)


def xp(n):
    return ("xp", n)


def give(id_, n=1):
    return ("item", id_, n)


def table(name):
    return ("random", TABLES[name])


def T(en, de):
    return {"en_us": en, "de_de": de}


MINING_TURTLE = {"computercraft:right_turtle_upgrade": "minecraft:diamond_pickaxe"}
CRAFTY_TURTLE = {"computercraft:right_turtle_upgrade": "minecraft:crafting_table"}


def turtle_with(upgrade):
    return ("and(or(item(computercraft:turtle_normal)item(computercraft:turtle_advanced))"
            f"or(component(fuzzy:{{\"computercraft:left_turtle_upgrade\":\"{upgrade}\"}})"
            f"component(fuzzy:{{\"computercraft:right_turtle_upgrade\":\"{upgrade}\"}})))")


# ---------------------------------------------------------------------------------------------
# Chapter 1: CC: Tweaked
# ---------------------------------------------------------------------------------------------
CCT_CHAPTER = dict(
    key="cct", filename="cc_tweaked", order_index=12, icon="computercraft:computer_advanced",
    title=T("CC: Tweaked", "CC: Tweaked"),
    subtitle=T("Computers, turtles and Lua", "Computer, Turtles und Lua"),
)

CCT = [
    Q("cct.computer", 0, 0,
      T("Your First Computer", "Dein erster Computer"),
      T("&7Hello, world", "&7Hallo, Welt"),
      T([
          "&eCC: Tweaked&r adds programmable &eComputers&r that run &bLua&r. Right-click one to open its terminal.",
          "",
          "The terminal is a &6shell&r: type a program name and press Enter. Try &aprograms&r, &als&r and &aid&r.",
          "",
          "&7Give it a name with &alabel set Base&7 - a labelled computer keeps its files when you break it.",
      ], [
          "&eCC: Tweaked&r bringt programmierbare &eComputer&r, die &bLua&r ausführen. Rechtsklick öffnet das Terminal.",
          "",
          "Das Terminal ist eine &6Shell&r: Programmnamen eintippen und Enter drücken. Probier &aprograms&r, &als&r und &aid&r.",
          "",
          "&7Gib ihm mit &alabel set Basis&7 einen Namen - ein benannter Computer behält beim Abbauen seine Dateien.",
      ]),
      [item("computercraft:computer_normal")], [xp(50), give("minecraft:redstone", 8)],
      shape="hexagon", size=1.5),

    Q("cct.help", 1.5, -1,
      T("Ask for Help", "Frag die Hilfe"),
      T("&7The manual is built in", "&7Das Handbuch ist eingebaut"),
      T([
          "Every program and API has a help page. Type &ahelp&r for an overview, &ahelp programs&r or e.g. &ahelp redstone&r.",
          "",
          "Press &eTab&r to auto-complete program and file names, and &eUp&r to repeat the last command.",
      ], [
          "Jedes Programm und jede API hat eine Hilfeseite. Tippe &ahelp&r für einen Überblick, &ahelp programs&r oder z. B. &ahelp redstone&r.",
          "",
          "&eTab&r vervollständigt Programm- und Dateinamen, &ePfeil hoch&r wiederholt den letzten Befehl.",
      ]),
      [check("Read a help page", "Eine Hilfeseite lesen")], [xp(25)],
      deps=["cct.computer"]),

    Q("cct.edit", 1.5, 1,
      T("Write a Program", "Schreib ein Programm"),
      T("&7edit, save, run", "&7edit, speichern, starten"),
      T([
          "Type &aedit hello&r to open the editor and write:",
          "&2print(\"Hello from \" .. os.getComputerLabel())",
          "",
          "Press &eCtrl&r, choose &eSave&r, then &eExit&r. Run it by typing &ahello&r.",
          "",
          "&7Use &als&7, &amv&7, &acp&7 and &arm&7 to manage your files.",
      ], [
          "Tippe &aedit hello&r, um den Editor zu öffnen, und schreibe:",
          "&2print(\"Hallo von \" .. os.getComputerLabel())",
          "",
          "&eStrg&r drücken, &eSave&r wählen, dann &eExit&r. Starte es mit &ahello&r.",
          "",
          "&7Mit &als&7, &amv&7, &acp&7 und &arm&7 verwaltest du deine Dateien.",
      ]),
      [check("Run your own program", "Ein eigenes Programm starten")], [xp(25)],
      deps=["cct.computer"]),

    Q("cct.advanced", 3, 0,
      T("Advanced Computer", "Erweiterter Computer"),
      T("&7Colours and a mouse", "&7Farben und Maus"),
      T([
          "The &eAdvanced Computer&r has a colour screen and mouse support, and you can open tabs with &abg&r and &afg&r.",
          "",
          "Try &apaint image&r or &aworm&r. In code, &2term.setTextColour(colours.lime)&r changes the text colour.",
          "",
          "&7Most add-ons, including &eCC: Cloud&7, build on the Advanced Computer.",
      ], [
          "Der &eErweiterte Computer&r hat einen Farbbildschirm und Mausunterstützung; mit &abg&r und &afg&r öffnest du Tabs.",
          "",
          "Probier &apaint bild&r oder &aworm&r. Im Code ändert &2term.setTextColour(colours.lime)&r die Textfarbe.",
          "",
          "&7Die meisten Add-ons, auch &eCC: Cloud&7, bauen auf dem Erweiterten Computer auf.",
      ]),
      [item("computercraft:computer_advanced")], [xp(100), give("minecraft:gold_ingot", 4), table("common")],
      deps=["cct.computer"], shape="hexagon", size=1.25),

    Q("cct.disk", 1.5, 2.5,
      T("Disk Drive and Floppy Disks", "Diskettenlaufwerk und Disketten"),
      T("&7Carry code between computers", "&7Code zwischen Computern tragen"),
      T([
          "Place a &eDisk Drive&r next to a computer and insert a &eFloppy Disk&r. It appears as the folder &adisk/&r.",
          "",
          "&acp hello disk/hello&r copies your program onto the disk. Dye a floppy in a crafting grid to colour-code it.",
      ], [
          "Stell ein &eDiskettenlaufwerk&r neben einen Computer und leg eine &eDiskette&r ein. Sie erscheint als Ordner &adisk/&r.",
          "",
          "&acp hello disk/hello&r kopiert dein Programm auf die Diskette. Mit Farbstoff im Crafting-Feld färbst du Disketten ein.",
      ]),
      [item("computercraft:disk_drive"), item("computercraft:disk")], [xp(50), give("minecraft:paper", 8)],
      deps=["cct.edit"]),

    Q("cct.startup", 1.5, 4,
      T("Startup Scripts", "Startskripte"),
      T("&7Run something on boot", "&7Beim Start etwas ausführen"),
      T([
          "A file called &astartup.lua&r runs every time the computer boots, also after a server restart or chunk reload.",
          "",
          "&aedit startup.lua&r and call your program, e.g. &2shell.run(\"hello\")&r.",
          "",
          "&7A disk with &astartup.lua&7 in a drive takes priority: handy to install programs on many computers.",
      ], [
          "Eine Datei namens &astartup.lua&r läuft bei jedem Start des Computers, auch nach einem Serverneustart oder Chunk-Reload.",
          "",
          "&aedit startup.lua&r und dein Programm aufrufen, z. B. &2shell.run(\"hello\")&r.",
          "",
          "&7Eine Diskette mit &astartup.lua&7 im Laufwerk hat Vorrang: praktisch, um viele Computer einzurichten.",
      ]),
      [check("Create a startup.lua", "Eine startup.lua anlegen")], [xp(25)],
      deps=["cct.disk"]),

    Q("cct.pocket", -1.5, 2.5,
      T("Pocket Computer", "Taschencomputer"),
      T("&7A computer in your hand", "&7Ein Computer in der Hand"),
      T([
          "The &ePocket Computer&r is a computer you carry around. Right-click to open it.",
          "",
          "Craft it together with a &eWireless Modem&r to send and receive &arednet&r messages on the move.",
      ], [
          "Der &eTaschencomputer&r ist ein Computer zum Mitnehmen. Rechtsklick öffnet ihn.",
          "",
          "Zusammen mit einem &eKabellosen Modem&r gecraftet empfängt und sendet er unterwegs &arednet&r-Nachrichten.",
      ]),
      [anyof("computercraft:pocket_computer_normal",
             ["computercraft:pocket_computer_normal", "computercraft:pocket_computer_advanced"])],
      [xp(75)], deps=["cct.computer"]),

    Q("cct.speaker", 0, -2.5,
      T("Speaker", "Lautsprecher"),
      T("&7Beep boop", "&7Piep"),
      T([
          "A &eSpeaker&r next to a computer plays notes, sounds and audio:",
          "&2local s = peripheral.find(\"speaker\")",
          "&2s.playNote(\"harp\", 1, 12)",
          "",
          "&7Try the &aspeaker&7 program, e.g. &aspeaker sound entity.cat.ambient&7.",
      ], [
          "Ein &eLautsprecher&r neben dem Computer spielt Noten, Sounds und Audio:",
          "&2local s = peripheral.find(\"speaker\")",
          "&2s.playNote(\"harp\", 1, 12)",
          "",
          "&7Probier das Programm &aspeaker&7, z. B. &aspeaker sound entity.cat.ambient&7.",
      ]),
      [item("computercraft:speaker")], [xp(50), give("minecraft:note_block", 1)],
      deps=["cct.computer"]),

    Q("cct.printer", -1.5, -2.5,
      T("Printer", "Drucker"),
      T("&7Paper and ink", "&7Papier und Tinte"),
      T([
          "The &ePrinter&r needs paper and a dye as ink. Print from Lua with &2printer.newPage()&r, &2printer.write(text)&r and &2printer.endPage()&r.",
          "",
          "&7Combine &ePrinted Pages&7 with string and leather into a &ePrinted Book&7.",
      ], [
          "Der &eDrucker&r braucht Papier und einen Farbstoff als Tinte. Drucken in Lua mit &2printer.newPage()&r, &2printer.write(text)&r und &2printer.endPage()&r.",
          "",
          "&7Aus &eGedruckten Seiten&7, Faden und Leder wird ein &eGedrucktes Buch&7.",
      ]),
      [item("computercraft:printer")], [xp(50), give("minecraft:paper", 16), give("minecraft:ink_sac", 4)],
      deps=["cct.computer"]),

    Q("cct.redstone", 3, -2.5,
      T("Redstone and the Redstone Relay", "Redstone und das Redstone-Relais"),
      T("&7Talk to the vanilla world", "&7Mit Vanilla-Redstone reden"),
      T([
          "Every computer reads and writes redstone on all six sides:",
          "&2redstone.setOutput(\"back\", true)",
          "&2print(redstone.getAnalogInput(\"left\"))",
          "",
          "The &eRedstone Relay&r does the same as a peripheral, so one computer can control many places over a wired network.",
      ], [
          "Jeder Computer liest und schreibt Redstone auf allen sechs Seiten:",
          "&2redstone.setOutput(\"back\", true)",
          "&2print(redstone.getAnalogInput(\"left\"))",
          "",
          "Das &eRedstone-Relais&r kann das Gleiche als Peripherie, so steuert ein Computer über ein Kabelnetz viele Orte.",
      ]),
      [item("computercraft:redstone_relay")], [xp(50), give("minecraft:redstone", 16)],
      deps=["cct.advanced"]),

    Q("cct.monitor", 4.5, -2.5,
      T("Monitors", "Monitore"),
      T("&7Bigger screens", "&7Größere Bildschirme"),
      T([
          "Place &eMonitors&r next to each other to form one big screen (up to 8 x 6).",
          "",
          "&amonitor right hello&r runs a program on the monitor on the right. From Lua:",
          "&2local m = peripheral.find(\"monitor\")",
          "&2m.setTextScale(2) m.write(\"Base OK\")",
      ], [
          "Setz &eMonitore&r nebeneinander, dann werden sie ein großer Bildschirm (bis 8 x 6).",
          "",
          "&amonitor right hello&r startet ein Programm auf dem Monitor rechts. In Lua:",
          "&2local m = peripheral.find(\"monitor\")",
          "&2m.setTextScale(2) m.write(\"Basis OK\")",
      ]),
      [item("computercraft:monitor_normal")], [xp(50), give("minecraft:glass_pane", 8)],
      deps=["cct.advanced"]),

    Q("cct.advmonitor", 6, -2.5,
      T("Advanced Monitors", "Erweiterte Monitore"),
      T("&7Colour and touch", "&7Farbe und Touch"),
      T([
          "&eAdvanced Monitors&r show colours and are touch screens. A right-click on them fires an event:",
          "&2local _, side, x, y = os.pullEvent(\"monitor_touch\")",
          "",
          "&7Great for status boards and buttons in your base.",
      ], [
          "&eErweiterte Monitore&r zeigen Farben und sind Touchscreens. Ein Rechtsklick löst ein Event aus:",
          "&2local _, side, x, y = os.pullEvent(\"monitor_touch\")",
          "",
          "&7Ideal für Statusanzeigen und Knöpfe in deiner Basis.",
      ]),
      [item("computercraft:monitor_advanced")], [xp(75), give("minecraft:gold_ingot", 2)],
      deps=["cct.monitor"]),

    Q("cct.wired", 4.5, 0,
      T("Wired Modems and Networking Cable", "Kabelmodems und Netzwerkkabel"),
      T("&7Peripherals far away", "&7Peripherie in der Ferne"),
      T([
          "A &eWired Modem&r on a block plus &eNetworking Cable&r connects peripherals to a computer over any distance.",
          "",
          "Right-click a modem to switch it on (it turns red). The peripheral gets a name like &amonitor_0&r:",
          "&2print(textutils.serialise(peripheral.getNames()))",
          "",
          "&7The full-block Wired Modem connects on all six sides.",
      ], [
          "Ein &eKabelmodem&r an einem Block plus &eNetzwerkkabel&r verbindet Peripherie über jede Entfernung mit einem Computer.",
          "",
          "Rechtsklick schaltet das Modem ein (es wird rot). Die Peripherie bekommt einen Namen wie &amonitor_0&r:",
          "&2print(textutils.serialise(peripheral.getNames()))",
          "",
          "&7Das Kabelmodem als ganzer Block verbindet auf allen sechs Seiten.",
      ]),
      [item("computercraft:wired_modem"), item("computercraft:cable", 8)],
      [xp(50), give("computercraft:cable", 16)], deps=["cct.advanced"]),

    Q("cct.wireless", 6, 0,
      T("Wireless Modem and Rednet", "Kabelloses Modem und Rednet"),
      T("&7Computers talking to computers", "&7Computer reden mit Computern"),
      T([
          "Attach a &eWireless Modem&r and open it with rednet:",
          "&2rednet.open(\"back\")",
          "&2rednet.broadcast(\"hello\")",
          "&2local id, msg = rednet.receive()",
          "",
          "&7Range grows with height. Try the &achat&7 program between two computers.",
      ], [
          "Bring ein &eKabelloses Modem&r an und öffne es mit rednet:",
          "&2rednet.open(\"back\")",
          "&2rednet.broadcast(\"hallo\")",
          "&2local id, msg = rednet.receive()",
          "",
          "&7Die Reichweite wächst mit der Höhe. Probier das Programm &achat&7 zwischen zwei Computern.",
      ]),
      [item("computercraft:wireless_modem_normal")], [xp(50), give("minecraft:ender_pearl", 1)],
      deps=["cct.wired"]),

    Q("cct.ender", 7.5, 0,
      T("Ender Modem", "Ender-Modem"),
      T("&7No range limit", "&7Ohne Reichweitenlimit"),
      T([
          "The &eEnder Modem&r works like a wireless modem, but with unlimited range and across dimensions.",
          "",
          "&7Perfect for controlling an outpost in the Nether or the Mining Dimension from home.",
      ], [
          "Das &eEnder-Modem&r funktioniert wie ein kabelloses Modem, aber mit unbegrenzter Reichweite und über Dimensionen hinweg.",
          "",
          "&7Perfekt, um einen Außenposten im Nether oder in der Mining-Dimension von zu Hause zu steuern.",
      ]),
      [item("computercraft:wireless_modem_advanced")], [xp(100), give("minecraft:ender_eye", 2)],
      deps=["cct.wireless"]),

    Q("cct.gps", 7.5, -1.25,
      T("GPS", "GPS"),
      T("&7Where am I?", "&7Wo bin ich?"),
      T([
          "Four computers with wireless modems, placed high and not in one plane, run &agps host x y z&r with their own coordinates.",
          "",
          "Any other computer, turtle or pocket computer in range can then call &2gps.locate()&r.",
      ], [
          "Vier Computer mit kabellosen Modems, hoch oben und nicht in einer Ebene, starten &agps host x y z&r mit ihren eigenen Koordinaten.",
          "",
          "Jeder andere Computer, Turtle oder Taschencomputer in Reichweite kann dann &2gps.locate()&r aufrufen.",
      ]),
      [check("Set up a GPS cluster", "Einen GPS-Cluster bauen")], [xp(50)],
      deps=["cct.wireless"], optional=True),

    Q("cct.turtle", 3, 4,
      T("Turtles", "Turtles"),
      T("&7Robots that run Lua", "&7Roboter mit Lua"),
      T([
          "A &eTurtle&r is a computer that moves. It needs fuel: put coal in its inventory and run &arefuel all&r.",
          "",
          "&2turtle.forward() turtle.turnLeft() turtle.up()",
          "",
          "&7Check fuel with &2turtle.getFuelLevel()&7.",
      ], [
          "Eine &eTurtle&r ist ein Computer, der sich bewegt. Sie braucht Treibstoff: Kohle ins Inventar und &arefuel all&r ausführen.",
          "",
          "&2turtle.forward() turtle.turnLeft() turtle.up()",
          "",
          "&7Den Treibstoff prüfst du mit &2turtle.getFuelLevel()&7.",
      ]),
      [anyof("computercraft:turtle_normal", ["computercraft:turtle_normal", "computercraft:turtle_advanced"])],
      [xp(75), give("minecraft:coal", 16)], deps=["cct.advanced"]),

    Q("cct.mining", 4.5, 3.5,
      T("Mining Turtle", "Bergbau-Turtle"),
      T("&7Let it dig", "&7Lass sie graben"),
      T([
          "Craft a turtle with a &eDiamond Pickaxe&r to get a &eMining Turtle&r.",
          "",
          "&aexcavate 8&r digs an 8 x 8 hole down to bedrock and brings the loot back. &atunnel 32&r digs a 3 x 2 tunnel.",
          "",
          "&7Your own code: &2turtle.dig()&7, &2turtle.inspect()&7, &2turtle.select(1)&7.",
      ], [
          "Crafte eine Turtle mit einer &eDiamantspitzhacke&r zur &eBergbau-Turtle&r.",
          "",
          "&aexcavate 8&r gräbt ein 8 x 8 Loch bis zum Grundgestein und bringt die Beute zurück. &atunnel 32&r gräbt einen 3 x 2 Tunnel.",
          "",
          "&7Eigener Code: &2turtle.dig()&7, &2turtle.inspect()&7, &2turtle.select(1)&7.",
      ]),
      [filt(turtle_with("minecraft:diamond_pickaxe"), "computercraft:turtle_normal", MINING_TURTLE)],
      [xp(75), give("minecraft:coal_block", 2)], deps=["cct.turtle"],
      icon={"id": "computercraft:turtle_normal", "count": 1, "components": MINING_TURTLE}),

    Q("cct.crafty", 4.5, 4.75,
      T("Crafty Turtle", "Crafting-Turtle"),
      T("&7A crafting table on wheels", "&7Eine Werkbank auf Rädern"),
      T([
          "A turtle with a &eCrafting Table&r can craft from its inventory: lay out the recipe in the top-left 3 x 3 slots and call &2turtle.craft()&r.",
          "",
          "&7Turtles have two upgrade slots: a mining and crafty turtle in one is possible.",
      ], [
          "Eine Turtle mit &eWerkbank&r craftet aus ihrem Inventar: Rezept in die oberen linken 3 x 3 Slots legen und &2turtle.craft()&r aufrufen.",
          "",
          "&7Turtles haben zwei Upgrade-Plätze: Bergbau und Crafting in einer Turtle geht.",
      ]),
      [filt(turtle_with("minecraft:crafting_table"), "computercraft:turtle_normal", CRAFTY_TURTLE)],
      [xp(50)], deps=["cct.turtle"],
      icon={"id": "computercraft:turtle_normal", "count": 1, "components": CRAFTY_TURTLE}),

    Q("cct.inventories", 4.5, 1.5,
      T("Inventories as Peripherals", "Inventare als Peripherie"),
      T("&7Every chest is an API", "&7Jede Truhe ist eine API"),
      T([
          "Any block with an inventory (chests, drawers, machines) becomes a peripheral through a wired modem:",
          "&2local c = peripheral.find(\"inventory\")",
          "&2for slot, it in pairs(c.list()) do print(it.name, it.count) end",
          "",
          "&2c.pushItems(\"minecraft:chest_1\", 1)&r moves items between inventories on the same network.",
      ], [
          "Jeder Block mit Inventar (Truhen, Schubladen, Maschinen) wird über ein Kabelmodem zur Peripherie:",
          "&2local c = peripheral.find(\"inventory\")",
          "&2for slot, it in pairs(c.list()) do print(it.name, it.count) end",
          "",
          "&2c.pushItems(\"minecraft:chest_1\", 1)&r verschiebt Items zwischen Inventaren im selben Netz.",
      ]),
      [check("List a chest from Lua", "Eine Truhe per Lua auslesen")], [xp(50)],
      deps=["cct.wired"], icon={"id": "minecraft:chest", "count": 1}),

    Q("cct.energyfluids", 6, 1.5,
      T("Energy and Fluid Storage", "Energie- und Flüssigkeitsspeicher"),
      T("&7Generic peripherals", "&7Generische Peripherie"),
      T([
          "Blocks that store FE or fluids also work as peripherals - from most mods in the pack, no add-on needed:",
          "&2local e = peripheral.find(\"energy_storage\")",
          "&2print(e.getEnergy() .. \" / \" .. e.getEnergyCapacity())",
          "",
          "Tanks are &afluid_storage&r: &2tanks()&r, &2pushFluid(to)&r.",
      ], [
          "Blöcke, die FE oder Flüssigkeiten speichern, funktionieren ebenfalls als Peripherie - bei den meisten Mods im Pack ohne Add-on:",
          "&2local e = peripheral.find(\"energy_storage\")",
          "&2print(e.getEnergy() .. \" / \" .. e.getEnergyCapacity())",
          "",
          "Tanks sind &afluid_storage&r: &2tanks()&r, &2pushFluid(ziel)&r.",
      ]),
      [check("Read a battery from Lua", "Einen Akku per Lua auslesen")], [xp(50)],
      deps=["cct.inventories"], icon={"id": "minecraft:bucket", "count": 1}),

    Q("cct.energymeter", 7.5, 1.5,
      T("Energy Meter", "Energy Meter"),
      T("&7Measure your power flow", "&7Stromfluss messen"),
      T([
          "&eEnergy Meter&r has native CC: Tweaked support. Its peripheral type is &aenergymeter&r:",
          "&2local m = peripheral.find(\"energymeter\")",
          "&2print(m.getEnergyRate() .. \" FE/t\")",
          "",
          "&7Use it to switch generators on demand or to graph your base's usage.",
      ], [
          "&eEnergy Meter&r unterstützt CC: Tweaked direkt. Der Peripherietyp ist &aenergymeter&r:",
          "&2local m = peripheral.find(\"energymeter\")",
          "&2print(m.getEnergyRate() .. \" FE/t\")",
          "",
          "&7Damit schaltest du Generatoren nach Bedarf oder zeichnest den Verbrauch deiner Basis auf.",
      ]),
      [item("energymeter:meter")], [xp(50)], deps=["cct.energyfluids"], optional=True),

    Q("cct.energizedpower", 7.5, 2.75,
      T("Energized Power", "Energized Power"),
      T("&7Machines with a remote control", "&7Maschinen mit Fernbedienung"),
      T([
          "&eEnergized Power&r machines expose extra methods to computers, e.g. their redstone mode:",
          "&2local b = peripheral.find(\"energizedpower:battery_box\")",
          "&2print(b.getRedstoneMode())",
          "",
          "&7The Time and Weather Controllers can be driven from Lua as well.",
      ], [
          "Maschinen von &eEnergized Power&r bieten Computern zusätzliche Methoden, z. B. ihren Redstone-Modus:",
          "&2local b = peripheral.find(\"energizedpower:battery_box\")",
          "&2print(b.getRedstoneMode())",
          "",
          "&7Auch der Zeit- und der Wetter-Controller lassen sich per Lua steuern.",
      ]),
      [item("energizedpower:battery_box")], [xp(50)], deps=["cct.energyfluids"], optional=True),

    Q("cct.logistics", 6, 2.75,
      T("Logistics Networks", "Logistics Networks"),
      T("&7Watch your item flow", "&7Den Itemfluss beobachten"),
      T([
          "The &eComputer&r block of &eLogistics Networks&r is a CC peripheral of type &alogistics_computer&r:",
          "&2local lc = peripheral.find(\"logistics_computer\")",
          "&2for _, n in ipairs(lc.listNetworks()) do print(n.name) end",
          "",
          "&7After &2lc.watch(net)&7 a &alogistics_flow&7 event arrives every second with live rates.",
      ], [
          "Der &eComputer&r-Block von &eLogistics Networks&r ist eine CC-Peripherie vom Typ &alogistics_computer&r:",
          "&2local lc = peripheral.find(\"logistics_computer\")",
          "&2for _, n in ipairs(lc.listNetworks()) do print(n.name) end",
          "",
          "&7Nach &2lc.watch(net)&7 kommt jede Sekunde ein &alogistics_flow&7-Event mit Live-Raten.",
      ]),
      [item("logisticsnetworks:computer")], [xp(50)], deps=["cct.inventories"], optional=True),
]

# ---------------------------------------------------------------------------------------------
# Chapter 2: CC: Cloud
# ---------------------------------------------------------------------------------------------
CLOUD_CHAPTER = dict(
    key="cloud", filename="cc_cloud", order_index=13, icon="cccloud:cloud_mainframe",
    title=T("CC: Cloud", "CC: Cloud"),
    subtitle=T("Your base on a web dashboard", "Deine Basis im Web-Dashboard"),
)

CLOUD = [
    Q("cloud.core", 0, 0,
      T("Cloud Core", "Cloud-Kern"),
      T("&7Where it all starts", "&7Hier fängt alles an"),
      T([
          "&eCC: Cloud&r puts your CC computers and machines on a &bweb dashboard&r that you open in any browser - phone, tablet or PC.",
          "",
          "Every block of the mod starts with a &eCloud Core&r: gold nuggets, prismarine crystals and an ender pearl.",
      ], [
          "&eCC: Cloud&r bringt deine CC-Computer und Maschinen in ein &bWeb-Dashboard&r, das du in jedem Browser öffnest - Handy, Tablet oder PC.",
          "",
          "Jeder Block der Mod beginnt mit einem &eCloud-Kern&r: Goldnuggets, Prismarinkristalle und eine Enderperle.",
      ]),
      [item("cccloud:cloud_core")], [xp(50), give("minecraft:prismarine_crystals", 4)],
      deps=["ext:cct.advanced"], shape="hexagon", size=1.5),

    Q("cloud.mainframe", 1.75, 0,
      T("Cloud Mainframe", "Cloud-Mainframe"),
      T("&7One per base", "&7Einer pro Basis"),
      T([
          "The &eCloud Mainframe&r is two blocks tall and the gate between your base and the dashboard. The dashboard runs inside the Minecraft server itself - no external service.",
          "",
          "Right-click it to open its screen: status, dashboard on/off, the &bdashboard URL&r and the &bpairing code&r.",
          "",
          "&7Only you, players you trust and server ops can see the code.",
      ], [
          "Der &eCloud-Mainframe&r ist zwei Blöcke hoch und das Tor zwischen Basis und Dashboard. Das Dashboard läuft im Minecraft-Server selbst - kein externer Dienst.",
          "",
          "Rechtsklick öffnet seinen Bildschirm: Status, Dashboard an/aus, die &bDashboard-URL&r und den &bKopplungscode&r.",
          "",
          "&7Nur du, Spieler, denen du vertraust, und Server-OPs sehen den Code.",
      ]),
      [item("cccloud:cloud_mainframe")], [xp(150), table("common")],
      deps=["cloud.core"], shape="hexagon", size=1.5),

    Q("cloud.power", 3.25, -1,
      T("Power the Mainframe", "Strom für den Mainframe"),
      T("&7It runs on FE", "&7Er läuft mit FE"),
      T([
          "The mainframe needs &cFE&r: about 40 FE/t plus a little per linked device and per open dashboard. Any cable or generator in the pack works.",
          "",
          "Without power it goes &cNO POWER&r and the dashboard shows it as offline. A short buffer bridges brownouts.",
      ], [
          "Der Mainframe braucht &cFE&r: etwa 40 FE/t plus etwas pro verbundenem Gerät und offenem Dashboard. Jedes Kabel und jeder Generator im Pack funktioniert.",
          "",
          "Ohne Strom zeigt er &cNO POWER&r und das Dashboard meldet ihn als offline. Ein kleiner Puffer überbrückt kurze Ausfälle.",
      ]),
      [check("Power a mainframe", "Einen Mainframe mit Strom versorgen")], [xp(50)],
      deps=["cloud.mainframe"], icon={"id": "minecraft:redstone_block", "count": 1}),

    Q("cloud.uplink", 3.25, 1,
      T("Cloud Uplink", "Cloud-Uplink"),
      T("&7Plug a computer into the cloud", "&7Einen Computer in die Cloud stecken"),
      T([
          "A &eCloud Uplink&r sticks to a block face like a modem. Put it on a &ecomputer&r, or on a block next to it, and the computer gets a card on the dashboard.",
          "",
          "&7It is also a peripheral: &2peripheral.find(\"cloud_uplink\")&7. It has a limited range to the mainframe in the same dimension.",
      ], [
          "Ein &eCloud-Uplink&r haftet wie ein Modem an einer Blockseite. Setz ihn auf einen &eComputer&r oder einen Block daneben, dann bekommt der Computer eine Karte im Dashboard.",
          "",
          "&7Er ist auch Peripherie: &2peripheral.find(\"cloud_uplink\")&7. Seine Reichweite zum Mainframe ist begrenzt und gilt in derselben Dimension.",
      ]),
      [item("cccloud:cloud_uplink")], [xp(50)], deps=["cloud.mainframe"]),

    Q("cloud.linkcard", 4.75, 1,
      T("Cloud Link Card", "Cloud-Verbindungskarte"),
      T("&7Mainframe first, then the device", "&7Erst Mainframe, dann Gerät"),
      T([
          "Right-click the &emainframe&r with a &eCloud Link Card&r, then right-click the &euplink&r. The uplink is now part of that mainframe's network.",
          "",
          "&7Sneak + right-click in the air clears the card. Right-click an uplink to see its status and to unlink it.",
      ], [
          "Rechtsklick mit der &eCloud-Verbindungskarte&r auf den &eMainframe&r, dann auf den &eUplink&r. Jetzt gehört der Uplink zum Netz dieses Mainframes.",
          "",
          "&7Schleichen + Rechtsklick in die Luft leert die Karte. Rechtsklick auf einen Uplink zeigt seinen Status und erlaubt das Trennen.",
      ]),
      [item("cccloud:cloud_link_card")], [xp(25)], deps=["cloud.uplink"]),

    Q("cloud.pair", 4.75, -1,
      T("Pair a Browser", "Einen Browser koppeln"),
      T("&7Open the dashboard", "&7Das Dashboard öffnen"),
      T([
          "The mainframe screen lists the &bdashboard URL&r, e.g. &ahttp://192.168.1.20:8787&r - the address of the PC or server running Minecraft, port 8787. In singleplayer, a phone on the same Wi-Fi works.",
          "",
          "Open it, enter the &bpairing code&r (XXXX-XXXX, valid 10 minutes), then click &a[Allow]&r in-game to approve the device.",
          "",
          "&7Hosted server? The admin may need to open the port or set publicUrl; by default only LAN addresses can connect.",
      ], [
          "Der Mainframe-Bildschirm zeigt die &bDashboard-URL&r, z. B. &ahttp://192.168.1.20:8787&r - die Adresse des PCs oder Servers, auf dem Minecraft läuft, Port 8787. Im Einzelspieler klappt ein Handy im selben WLAN.",
          "",
          "Öffne sie, gib den &bKopplungscode&r ein (XXXX-XXXX, 10 Minuten gültig) und klick im Spiel auf &a[Erlauben]&r, um das Gerät freizugeben.",
          "",
          "&7Gehosteter Server? Der Admin muss evtl. den Port öffnen oder publicUrl setzen; standardmäßig dürfen nur LAN-Adressen verbinden.",
      ]),
      [check("Pair a browser with your mainframe", "Einen Browser mit dem Mainframe koppeln")],
      [xp(100)], deps=["cloud.power"], shape="hexagon", size=1.25,
      icon={"id": "cccloud:cloud_link_card", "count": 1}),

    Q("cloud.security", 4.75, -2.5,
      T("Kill Switch and Trust", "Not-Aus und Vertrauen"),
      T("&7You stay in control", "&7Du behältst die Kontrolle"),
      T([
          "A &credstone signal&r on the mainframe takes it offline at once - a hard kill switch.",
          "",
          "&a/cccloud trust <player>&r lets friends use your mainframe, &a/cccloud devices&r and &a/cccloud revoke all&r manage browsers, and &a/cccloud pin set 1234&r protects critical buttons with a PIN.",
      ], [
          "Ein &cRedstone-Signal&r am Mainframe nimmt ihn sofort offline - ein echter Not-Aus.",
          "",
          "&a/cccloud trust <spieler>&r lässt Freunde deinen Mainframe nutzen, &a/cccloud devices&r und &a/cccloud revoke all&r verwalten Browser, &a/cccloud pin set 1234&r schützt kritische Knöpfe mit einer PIN.",
      ]),
      [check("Got it", "Verstanden")], [xp(50)], deps=["cloud.pair"], optional=True,
      icon={"id": "minecraft:lever", "count": 1}),

    Q("cloud.autocard", 6.25, 0,
      T("The Computer Card", "Die Computer-Karte"),
      T("&7Zero code", "&7Ganz ohne Code"),
      T([
          "An uplink on a computer that runs no cloud program shows an automatic &eComputer card&r: on/off, reboot, shut down and all six redstone sides.",
          "",
          "Toggle a side on your phone and watch a lamp next to the computer light up.",
      ], [
          "Ein Uplink auf einem Computer ohne Cloud-Programm zeigt automatisch eine &eComputer-Karte&r: an/aus, Neustart, Herunterfahren und alle sechs Redstone-Seiten.",
          "",
          "Schalte eine Seite auf dem Handy und sieh zu, wie eine Lampe neben dem Computer angeht.",
      ]),
      [check("Switch a redstone side from the dashboard", "Eine Redstone-Seite im Dashboard schalten")],
      [xp(50)], deps=["cloud.pair", "cloud.linkcard"],
      icon={"id": "computercraft:computer_advanced", "count": 1}),

    Q("cloud.demo", 7.75, 0,
      T("Your First Cloud Program", "Dein erstes Cloud-Programm"),
      T("&7cloud.ui", "&7cloud.ui"),
      T([
          "Run &acloud-demo&r on the computer to see every widget: gauges, charts, toggles, sliders, pages. Your own dashboard in a few lines:",
          "&2local ui = require(\"cloud.ui\")",
          "&2local dash = ui.connect()",
          "&2local t = dash:gauge(\"t\", \"Temp\", {max=100})",
          "&2dash:every(1, function() t:set(math.random(100)) end)",
          "&2dash:run()",
          "",
          "&7Reference: &ahelp cloud&7.",
      ], [
          "Starte &acloud-demo&r auf dem Computer und sieh alle Widgets: Anzeigen, Diagramme, Schalter, Schieberegler, Seiten. Dein eigenes Dashboard in wenigen Zeilen:",
          "&2local ui = require(\"cloud.ui\")",
          "&2local dash = ui.connect()",
          "&2local t = dash:gauge(\"t\", \"Temp\", {max=100})",
          "&2dash:every(1, function() t:set(math.random(100)) end)",
          "&2dash:run()",
          "",
          "&7Referenz: &ahelp cloud&7.",
      ]),
      [check("Run cloud-demo or your own cloud.ui script", "cloud-demo oder ein eigenes cloud.ui-Skript starten")],
      [xp(100), table("common")], deps=["cloud.autocard"], shape="hexagon", size=1.25,
      icon={"id": "computercraft:disk", "count": 1}),

    Q("cloud.advuplink", 6.25, 2,
      T("Advanced Cloud Uplink", "Erweiterter Cloud-Uplink"),
      T("&7More range", "&7Mehr Reichweite"),
      T([
          "The &eAdvanced Cloud Uplink&r reaches much further than the normal one. For other dimensions, use Ender Relays or an Ender Fiber Link.",
      ], [
          "Der &eErweiterte Cloud-Uplink&r reicht viel weiter als der normale. Für andere Dimensionen nimmst du Ender-Relais oder eine Ender-Glasfaserbrücke.",
      ]),
      [item("cccloud:advanced_cloud_uplink")], [xp(100), give("minecraft:ender_pearl", 2)],
      deps=["cloud.linkcard"]),

    Q("cloud.probe", 9.25, 1.75,
      T("Cloud Probe", "Cloud-Sonde"),
      T("&7For machines built into walls", "&7Für eingebaute Maschinen"),
      T([
          "A machine built into a wall, or with every face taken by pipes and cables, has no free side for an uplink. The &eCloud Probe&r reads &bone&r machine up to &b8 blocks&r away as if an uplink were attached to it.",
          "",
          "&aSneak + right-click&r the machine with the &eLink Card&r, then right-click the probe. The probe's screen shows what it reads and can clear it; the &eCloud Analyzer&r draws a line to the machine.",
          "",
          "&7It is expensive and counts as two devices on the mainframe – normal uplinks stay the everyday choice.",
      ], [
          "Eine in die Wand gebaute Maschine, oder eine, deren Seiten alle mit Rohren und Kabeln belegt sind, hat keinen Platz für einen Uplink. Die &eCloud-Sonde&r liest &beine&r Maschine bis zu &b8 Blöcke&r entfernt, als säße ein Uplink daran.",
          "",
          "&aSchleichen + Rechtsklick&r mit der &eVerbindungskarte&r auf die Maschine, dann Rechtsklick auf die Sonde. Ihr Bildschirm zeigt, was sie liest, und kann das Ziel löschen; der &eCloud-Analysator&r zeichnet eine Linie zur Maschine.",
          "",
          "&7Sie ist teuer und zählt am Mainframe wie zwei Geräte – im Alltag bleibt der normale Uplink die Wahl.",
      ]),
      [item("cccloud:cloud_probe"), check("Point a probe at a machine", "Eine Sonde auf eine Maschine richten")],
      [xp(200), give("minecraft:ender_eye", 1)], deps=["cloud.advuplink"], optional=True),

    Q("cloud.mebridge", 7.75, -1.5,
      T("ME Cloud Bridge", "ME-Cloud-Bridge"),
      T("&7Your AE2 network online", "&7Dein AE2-Netz online"),
      T([
          "The &eME Cloud Bridge&r joins your &bAE2&r network (1 channel). Link it with the Link Card and it shows a ready-made &eStorage&r card: fill level, energy, item count.",
          "",
          "&7As a peripheral: &2listItems()&7, &2findItems(\"iron\")&7, &2craft(name, count)&7. Only exists when AE2 is installed.",
      ], [
          "Die &eME-Cloud-Bridge&r tritt deinem &bAE2&r-Netz bei (1 Kanal). Mit der Verbindungskarte verknüpft zeigt sie eine fertige &eSpeicher&r-Karte: Füllstand, Energie, Itemanzahl.",
          "",
          "&7Als Peripherie: &2listItems()&7, &2findItems(\"iron\")&7, &2craft(name, anzahl)&7. Gibt es nur, wenn AE2 installiert ist.",
      ]),
      [item("cccloud:me_cloud_bridge")], [xp(100)], deps=["cloud.autocard"], optional=True),

    Q("cloud.rsbridge", 7.75, 1.5,
      T("RS Cloud Bridge", "RS-Cloud-Bridge"),
      T("&7Your Refined Storage online", "&7Dein Refined Storage online"),
      T([
          "The &eRS Cloud Bridge&r does the same for &bRefined Storage&r: a Storage card on the dashboard and the same Lua methods as the ME bridge.",
          "",
          "&7Only exists when Refined Storage is installed.",
      ], [
          "Die &eRS-Cloud-Bridge&r macht dasselbe für &bRefined Storage&r: eine Speicher-Karte im Dashboard und dieselben Lua-Methoden wie die ME-Bridge.",
          "",
          "&7Gibt es nur, wenn Refined Storage installiert ist.",
      ]),
      [item("cccloud:rs_cloud_bridge")], [xp(100)], deps=["cloud.autocard"], optional=True),

    Q("cloud.watchlist", 9.25, 0,
      T("Watchlist and Remote Crafting", "Merkliste und Fern-Crafting"),
      T("&7Never run out again", "&7Nie wieder leer"),
      T([
          "On the Storage card, &bsearch&r your whole network, add items to the &bwatchlist&r with a minimum (you get an alert when one drops below) and start &bcrafting jobs&r right from your browser.",
          "",
          "&7Try the &acloud-storage&7 program for a scripted storage dashboard.",
      ], [
          "Auf der Speicher-Karte &bdurchsuchst&r du dein ganzes Netz, setzt Items mit Mindestmenge auf die &bMerkliste&r (Alarm, wenn eines darunter fällt) und startest &bCrafting-Aufträge&r direkt im Browser.",
          "",
          "&7Probier das Programm &acloud-storage&7 für ein geskriptetes Speicher-Dashboard.",
      ]),
      [check("Watch an item and craft from the dashboard", "Ein Item beobachten und im Dashboard craften")],
      [xp(100), table("uncommon")], deps=["cloud.mebridge", "cloud.rsbridge"], dep_req="one_completed",
      shape="hexagon", size=1.25, icon={"id": "minecraft:crafting_table", "count": 1}),
]

UPGRADE_TYPES = ["capacity", "bandwidth", "range", "efficiency", "memory"]
UPGRADE_TIERS = ["allthemodium", "vibranium", "unobtainium"]


def upgrade_ids(tier):
    return [f"cccloud:upgrade_{t}_{tier}" for t in UPGRADE_TYPES]


HANDBOOK = {"id": "guideme:guide", "count": 1, "components": {"guideme:guide_id": "cccloud:handbook"}}

CLOUD += [
    # ---- handbook ----------------------------------------------------------------------------
    Q("cloud.handbook", 0, -1.75,
      T("The Handbook", "Das Handbuch"),
      T("&7Read it in-game", "&7Im Spiel nachlesen"),
      T([
          "Craft a &eBook&r with a &eCloud Core&r to get the &eCC: Cloud Handbook&r: every block, the dashboard, Lua and recipes, in English and German.",
          "",
          "&7Needs GuideMe, which ships with ATM11. The same pages are in the dashboard under Docs.",
      ], [
          "Crafte ein &eBuch&r mit einem &eCloud-Kern&r zum &eCC: Cloud-Handbuch&r: alle Blöcke, das Dashboard, Lua und Rezepte, auf Deutsch und Englisch.",
          "",
          "&7Braucht GuideMe, das in ATM11 enthalten ist. Dieselben Seiten stehen im Dashboard unter Docs.",
      ]),
      [("item", HANDBOOK, 1, {"match_components": "fuzzy"})], [xp(25)], deps=["cloud.core"], optional=True, icon=HANDBOOK),

    # ---- dashboard power features --------------------------------------------------------------
    Q("cloud.terminal", 6.25, -2.5,
      T("Terminal in the Browser", "Terminal im Browser"),
      T("&7Type from anywhere", "&7Von überall tippen"),
      T([
          "Open &bTerminal&r on a computer card: you see the computer's screen live and can type into it, like standing in front of it.",
          "",
          "&7Needs the right role on the mainframe and the PIN if one is set. Server admins can turn it off.",
      ], [
          "Öffne &bTerminal&r auf einer Computer-Karte: Du siehst den Bildschirm des Computers live und kannst tippen, als stündest du davor.",
          "",
          "&7Braucht die passende Rolle am Mainframe und die PIN, falls eine gesetzt ist. Server-Admins können es abschalten.",
      ]),
      [check("Use a computer from the browser terminal", "Einen Computer über das Browser-Terminal bedienen")],
      [xp(75)], deps=["cloud.autocard"], icon={"id": "computercraft:monitor_advanced", "count": 1}),

    Q("cloud.store", 9.25, -2.5,
      T("Script Store and Upload", "Script Store und Upload"),
      T("&7No typing required", "&7Ohne Abtippen"),
      T([
          "&bUpload script&r on a computer card puts a &a.lua&r file onto that computer - drop a file, paste code or pick an example, then run it or make it the startup program.",
          "",
          "The &bStore&r lists community scripts. You see the full source before installing, and the server checks the store's signature.",
          "",
          "&7Scripts run on your CC computer, not on the Minecraft server.",
      ], [
          "&bSkript hochladen&r auf einer Computer-Karte legt eine &a.lua&r-Datei auf den Computer - Datei ablegen, Code einfügen oder ein Beispiel wählen, dann starten oder als Startprogramm setzen.",
          "",
          "Der &bStore&r listet Community-Skripte. Du siehst den ganzen Quelltext vor der Installation, und der Server prüft die Signatur des Stores.",
          "",
          "&7Skripte laufen auf deinem CC-Computer, nicht auf dem Minecraft-Server.",
      ]),
      [check("Upload or install a script", "Ein Skript hochladen oder installieren")],
      [xp(75)], deps=["cloud.demo"], icon={"id": "computercraft:disk", "count": 1}),

    Q("cloud.autostock", 10.75, 0,
      T("Auto-Stock", "Auto-Vorrat"),
      T("&7Your network refills itself", "&7Dein Netz füllt sich selbst auf"),
      T([
          "Give a watchlist entry a &btarget&r and turn on &bauto-craft&r: when the item falls below its minimum, the bridge crafts it back up - no computer needed.",
          "",
          "The Storage card also shows a &bcraft preview&r, your crafting CPUs and running jobs, which you can cancel.",
          "",
          "&7Lua: &2bridge.setAutoStock(name, min, target, true)",
      ], [
          "Gib einem Merklisten-Eintrag ein &bZiel&r und schalte &bAuto-Craft&r ein: Fällt das Item unter sein Minimum, craftet die Bridge es wieder auf - ganz ohne Computer.",
          "",
          "Die Speicher-Karte zeigt auch eine &bCrafting-Vorschau&r, deine Crafting-CPUs und laufende Aufträge, die du abbrechen kannst.",
          "",
          "&7Lua: &2bridge.setAutoStock(name, min, ziel, true)",
      ]),
      [check("Set up auto-stock for an item", "Auto-Vorrat für ein Item einrichten")],
      [xp(100), table("uncommon")], deps=["cloud.watchlist"], icon={"id": "minecraft:hopper", "count": 1}),

    Q("cloud.rules", 10.75, -1.5,
      T("Rules and Notifications", "Regeln und Benachrichtigungen"),
      T("&7Automation without code", "&7Automatisierung ohne Code"),
      T([
          "Automate on the dashboard's &bRules&r page: &bWhen&r (a value crosses a limit, an item runs low, power drops, every N minutes) &bIf&r (conditions) &bThen&r (redstone, press a button, craft, alert).",
          "",
          "Under &bNotifications&r, send alerts to &bDiscord&r, &bntfy&r or a webhook, so your phone buzzes when the reactor gets hot.",
      ], [
          "Automatisiere auf der Seite &bRegeln&r im Dashboard: &bWenn&r (ein Wert eine Grenze überschreitet, ein Item knapp wird, der Strom sinkt, alle N Minuten) &bFalls&r (Bedingungen) &bDann&r (Redstone, Knopf drücken, craften, Alarm).",
          "",
          "Unter &bBenachrichtigungen&r schickst du Alarme an &bDiscord&r, &bntfy&r oder einen Webhook, damit dein Handy vibriert, wenn der Reaktor heiß wird.",
      ]),
      [check("Create a rule", "Eine Regel anlegen"),
       check("Send a test notification", "Eine Testbenachrichtigung senden")],
      [xp(100), table("uncommon")], deps=["cloud.watchlist"], icon={"id": "minecraft:comparator", "count": 1}),

    # ---- network: analyzer, relays, cables ----------------------------------------------------
    Q("cloud.analyzer", 3.25, 3.5,
      T("Cloud Analyzer", "Cloud-Analysator"),
      T("&7See your signal", "&7Sieh dein Signal"),
      T([
          "Every device connects to the nearest &enode&r - the mainframe or a relay - and each has a limited radio range in its dimension.",
          "",
          "Hold the &eCloud Analyzer&r to see range bubbles, links coloured by signal and where a new device would connect. Right-click a device for its route and status.",
          "",
          "&7Sneak + scroll switches between rings, spheres and off.",
      ], [
          "Jedes Gerät verbindet sich mit dem nächsten &eKnoten&r - dem Mainframe oder einem Relais - und jedes hat eine begrenzte Funkreichweite in seiner Dimension.",
          "",
          "Halte den &eCloud-Analysator&r und sieh Reichweiten, nach Signal gefärbte Verbindungen und wo sich ein neues Gerät verbinden würde. Rechtsklick auf ein Gerät zeigt Route und Status.",
          "",
          "&7Schleichen + Mausrad wechselt zwischen Ringen, Kugeln und aus.",
      ]),
      [item("cccloud:cloud_analyzer")], [xp(50)], deps=["cloud.uplink"]),

    Q("cloud.relay", 4.75, 3.5,
      T("Cloud Relay", "Cloud-Relais"),
      T("&7Extend your range", "&7Reichweite erweitern"),
      T([
          "A &eCloud Relay&r extends your network's range; the network finds the best route on its own. Relays are shared and need no linking.",
          "",
          "&7Relays need their own FE. The dashboard's Network page shows every node, link and signal strength.",
      ], [
          "Ein &eCloud-Relais&r erweitert die Reichweite deines Netzes; die beste Route findet es selbst. Relais werden geteilt und müssen nicht verknüpft werden.",
          "",
          "&7Relais brauchen eigenen FE-Strom. Die Netzwerk-Seite im Dashboard zeigt alle Knoten, Verbindungen und Signalstärken.",
      ]),
      [item("cccloud:cloud_relay")], [xp(75)], deps=["cloud.analyzer"]),

    Q("cloud.advrelay", 6.25, 3.5,
      T("Advanced Cloud Relay", "Erweitertes Cloud-Relais"),
      T("&7For spread-out bases", "&7Für weitläufige Basen"),
      T(["More than double the range of a normal relay, for spread-out bases."],
        ["Mehr als die doppelte Reichweite eines normalen Relais, für weitläufige Basen."]),
      [item("cccloud:advanced_cloud_relay")], [xp(100)], deps=["cloud.relay"]),

    Q("cloud.enderrelay", 7.75, 3.5,
      T("Ender Relay", "Ender-Relais"),
      T("&7Other dimensions", "&7Andere Dimensionen"),
      T(["&eEnder Relays&r link to each other over any distance and across dimensions. Join each one to your mainframe with the Link Card."],
        ["&eEnder-Relais&r verbinden sich über jede Entfernung und über Dimensionen hinweg. Verknüpfe jedes mit der Verbindungskarte mit deinem Mainframe."]),
      [item("cccloud:ender_relay")], [xp(150), give("minecraft:ender_eye", 2)], deps=["cloud.advrelay"]),

    Q("cloud.cccable", 3.25, 5,
      T("CC Cables", "CC-Kabel"),
      T("&7Wired is always 100 %", "&7Kabel hat immer 100 %"),
      T([
          "The mainframe, relays, uplinks and bridges also connect through CC: Tweaked's &eNetworking Cable&r and &eWired Modems&r: any length, full signal, no extra hop power.",
          "",
          "&7Computers on the same cable also see the mainframe and bridges as peripherals.",
      ], [
          "Mainframe, Relais, Uplinks und Bridges verbinden sich auch über das &eNetzwerkkabel&r und &eKabelmodems&r von CC: Tweaked: beliebig lang, volles Signal, kein Strom pro Hop.",
          "",
          "&7Computer am selben Kabel sehen Mainframe und Bridges auch als Peripherie.",
      ]),
      [check("Connect a device by cable", "Ein Gerät per Kabel verbinden")],
      [xp(50)], deps=["cloud.analyzer"], icon={"id": "computercraft:cable", "count": 1}),

    Q("cloud.fiber", 4.75, 5,
      T("Fiber Cable", "Glasfaserkabel"),
      T("&7The better cable", "&7Das bessere Kabel"),
      T([
          "&eFiber Cable&r only connects cloud devices: a clean network of its own. Devices on fiber count only half against the mainframe's device limit and use half the power.",
      ], [
          "&eGlasfaserkabel&r verbindet nur Cloud-Geräte: ein eigenes, sauberes Netz. Geräte am Glasfaserkabel zählen nur halb für das Gerätelimit des Mainframes und brauchen halb so viel Strom.",
      ]),
      [item("cccloud:fiber_cable", 8)], [xp(75), give("minecraft:prismarine_crystals", 8)], deps=["cloud.cccable"]),

    Q("cloud.junction", 6.25, 5,
      T("Fiber Junction", "Glasfaser-Verteiler"),
      T("&7Splits and walls", "&7Abzweige und Wände"),
      T(["The &eFiber Junction&r splits a fiber line and takes it through walls."],
        ["Der &eGlasfaser-Verteiler&r teilt eine Glasfaserleitung auf und führt sie durch Wände."]),
      [item("cccloud:fiber_junction")], [xp(50)], deps=["cloud.fiber"]),

    Q("cloud.enderfiber", 7.75, 5,
      T("Ender Fiber Link", "Ender-Glasfaserbrücke"),
      T("&7Fiber across dimensions", "&7Glasfaser über Dimensionen", ),
      T([
          "Two &eEnder Fiber Links&r joined to the same mainframe with the Link Card connect their fiber networks across any distance and dimension.",
      ], [
          "Zwei &eEnder-Glasfaserbrücken&r, die mit der Verbindungskarte am selben Mainframe hängen, verbinden ihre Glasfasernetze über jede Entfernung und Dimension.",
      ]),
      [item("cccloud:ender_fiber_link")], [xp(200), table("uncommon")], deps=["cloud.junction", "cloud.enderrelay"]),

    # ---- mainframe upgrades --------------------------------------------------------------------
    Q("cloud.upg1", 1.75, -4,
      T("Allthemodium Upgrades", "Allthemodium-Upgrades"),
      T("&7Four slots, five kinds", "&7Vier Plätze, fünf Sorten"),
      T([
          "The mainframe has &e4 upgrade slots&r (Upgrades tab in its screen). Cards raise its limits: &bCapacity&r (devices), &bBandwidth&r (browsers), &bRange&r, &bEfficiency&r (less FE) and &bMemory&r (rules, history).",
          "",
          "Craft any one &6Allthemodium&r card.",
      ], [
          "Der Mainframe hat &e4 Upgrade-Plätze&r (Reiter Upgrades in seinem Bildschirm). Karten heben seine Grenzen: &bKapazität&r (Geräte), &bBandbreite&r (Browser), &bReichweite&r, &bEffizienz&r (weniger FE) und &bSpeicher&r (Regeln, Verlauf).",
          "",
          "Crafte eine beliebige &6Allthemodium&r-Karte.",
      ]),
      [anyof("cccloud:upgrade_capacity_allthemodium", upgrade_ids("allthemodium"))], [xp(150)],
      deps=["cloud.mainframe"], icon={"id": "cccloud:upgrade_capacity_allthemodium", "count": 1}),

    Q("cloud.upg2", 3.25, -4,
      T("Vibranium Upgrades", "Vibranium-Upgrades"),
      T("&7The next tier", "&7Die nächste Stufe"),
      T(["The next tier: more devices, more browsers, more range. Only the highest card of each type counts."],
        ["Die nächste Stufe: mehr Geräte, mehr Browser, mehr Reichweite. Pro Typ zählt nur die höchste Karte."]),
      [anyof("cccloud:upgrade_capacity_vibranium", upgrade_ids("vibranium"))], [xp(250)],
      deps=["cloud.upg1"], icon={"id": "cccloud:upgrade_capacity_vibranium", "count": 1}),

    Q("cloud.upg3", 4.75, -4,
      T("Unobtainium Upgrades", "Unobtainium-Upgrades"),
      T("&7The strongest cards", "&7Die stärksten Karten"),
      T(["The strongest cards: up to 512 devices, 64 browsers and half the power use."],
        ["Die stärksten Karten: bis zu 512 Geräte, 64 Browser und halber Stromverbrauch."]),
      [anyof("cccloud:upgrade_capacity_unobtainium", upgrade_ids("unobtainium"))], [xp(400), table("uncommon")],
      deps=["cloud.upg2"], icon={"id": "cccloud:upgrade_capacity_unobtainium", "count": 1}),

    Q("cloud.quantum", 6.25, -4,
      T("Quantum Core", "Quantenkern"),
      T("&7No limits", "&7Keine Grenzen"),
      T(["The &dQuantum Core&r has its own slot. It removes the device and browser limits, turns the mainframe into an Ender node and cuts its FE use to a quarter. Made with an &6ATM Star&r."],
        ["Der &dQuantenkern&r hat einen eigenen Platz. Er hebt die Geräte- und Browser-Grenzen auf, macht den Mainframe zum Ender-Knoten und senkt seinen FE-Verbrauch auf ein Viertel. Hergestellt mit einem &6ATM-Stern&r."]),
      [item("cccloud:quantum_core")], [xp(1000), table("rare")], deps=["cloud.upg3"],
      shape="hexagon", size=1.5),

    Q("cloud.anchor", 1.75, -5.5,
      T("Chunk Anchor Upgrade", "Chunk-Anker-Upgrade"),
      T("&7Keep it loaded", "&7Geladen halten"),
      T([
          "In a card slot, the &eChunk Anchor Upgrade&r keeps the mainframe's chunk and the chunks of its linked devices loaded while it is online and powered. It costs extra FE per chunk.",
          "",
          "&cOff by default:&r the server admin must enable it in the config (upgrades.chunkAnchor). The number of chunks per player is limited.",
      ], [
          "In einem Karten-Platz hält das &eChunk-Anker-Upgrade&r den Chunk des Mainframes und die Chunks seiner verknüpften Geräte geladen, solange er online ist und Strom hat. Es kostet extra FE pro Chunk.",
          "",
          "&cStandardmäßig aus:&r Der Server-Admin muss es in der Config einschalten (upgrades.chunkAnchor). Die Anzahl Chunks pro Spieler ist begrenzt.",
      ]),
      [item("cccloud:upgrade_chunk_anchor")], [xp(100)], deps=["cloud.upg1"], optional=True),

    # ---- in-game devices -----------------------------------------------------------------------
    Q("cloud.wireless", 9.25, 3.5,
      T("Wireless Redstone", "Kabelloses Redstone"),
      T("&7Redstone over the cloud", "&7Redstone über die Cloud"),
      T([
          "In an uplink's screen, set &bWireless redstone&r to &eTransmitter&r or &eReceiver&r and pick a channel name. Receivers output the strongest signal of all transmitters on that channel.",
          "",
          "&7Works over relays, cables and Ender links, even across dimensions. The dashboard shows every channel.",
      ], [
          "Stell im Bildschirm eines Uplinks &bKabelloses Redstone&r auf &eSender&r oder &eEmpfänger&r und wähle einen Kanalnamen. Empfänger geben das stärkste Signal aller Sender auf dem Kanal aus.",
          "",
          "&7Funktioniert über Relais, Kabel und Ender-Verbindungen, sogar über Dimensionen. Das Dashboard zeigt alle Kanäle.",
      ]),
      [check("Send a redstone signal over the cloud", "Ein Redstone-Signal über die Cloud senden")], [xp(75)],
      deps=["cloud.uplink"], hide_lines=True, icon={"id": "minecraft:redstone_torch", "count": 1}),

    Q("cloud.tablet", 10.75, 1.75,
      T("Cloud Tablet", "Cloud-Tablet"),
      T("&7The dashboard in your hand", "&7Das Dashboard in der Hand"),
      T(["The &eCloud Tablet&r opens your dashboard in-game, anywhere in range of your network. Made from a pocket computer and a cloud core."],
        ["Das &eCloud-Tablet&r öffnet dein Dashboard im Spiel, überall in Reichweite deines Netzes. Hergestellt aus Taschencomputer und Cloud-Kern."]),
      [item("cccloud:cloud_tablet")], [xp(100)], deps=["cloud.demo", "ext:cct.pocket"], hide_lines=True),

    Q("cloud.advtablet", 12.25, 1.75,
      T("Advanced Cloud Tablet", "Erweitertes Cloud-Tablet"),
      T("&7Anywhere", "&7Überall"),
      T(["Works anywhere in Ender range, across dimensions."],
        ["Funktioniert überall in Ender-Reichweite, auch über Dimensionen hinweg."]),
      [item("cccloud:advanced_cloud_tablet")], [xp(150)], deps=["cloud.tablet"]),

    Q("cloud.display", 10.75, 3.5,
      T("Cloud Display", "Cloud-Display"),
      T("&7A dashboard on the wall", "&7Ein Dashboard an der Wand"),
      T(["Build &eCloud Displays&r into a wall (up to 8 x 6) and show any dashboard card or page in your base."],
        ["Bau &eCloud-Displays&r zu einer Wand (bis 8 x 6) zusammen und zeig eine beliebige Dashboard-Karte oder Seite in deiner Basis."]),
      [item("cccloud:cloud_display")], [xp(100)], deps=["cloud.demo"], hide_lines=True),

    # ---- Cloud Redstone devices ------------------------------------------------------------------
    Q("cloud.rslamp", 9.25, 5,
      T("Cloud Lamp", "Cloud-Lampe"),
      T("&7A lamp on your phone", "&7Eine Lampe auf dem Handy"),
      T([
          "The &eCloud Lamp&r lights up by itself: place it, right-click the mainframe with the &eCloud Link Card&r, then the lamp. It shows up on the dashboard as a switch with a brightness slider.",
          "",
          "&7No computer, no Lua, no channels. Sneak + right-click opens its screen (name, group, brightness).",
      ], [
          "Die &eCloud-Lampe&r leuchtet von selbst: setzen, mit der &eCloud Link Card&r erst den Mainframe und dann die Lampe anklicken. Sie erscheint im Dashboard als Schalter mit Helligkeitsregler.",
          "",
          "&7Kein Computer, kein Lua, keine Kanäle. Schleichen + Rechtsklick öffnet ihr Fenster (Name, Gruppe, Helligkeit).",
      ]),
      [item("cccloud:cloud_lamp")], [xp(75), give("minecraft:redstone", 8)], deps=["cloud.linkcard"], hide_lines=True),

    Q("cloud.rsswitch", 10.75, 5,
      T("Switches and Buttons", "Schalter und Taster"),
      T("&7One card per group", "&7Eine Karte pro Gruppe"),
      T([
          "A &eCloud Switch&r works like a lever (on/off or analog 0-15), a &eCloud Button&r gives a short pulse. Both stick to any side of a block.",
          "",
          "Give devices the same &bgroup&r in their screen: they share one dashboard card, with &aAll on&r and &aAll off&r. Devices without a group go to the card \"Redstone\".",
      ], [
          "Ein &eCloud-Schalter&r arbeitet wie ein Hebel (an/aus oder analog 0-15), ein &eCloud-Taster&r gibt einen kurzen Impuls. Beide haften an jeder Blockseite.",
          "",
          "Gib Geräten in ihrem Fenster dieselbe &bGruppe&r: sie teilen sich eine Dashboard-Karte mit &aAlle an&r und &aAlle aus&r. Geräte ohne Gruppe landen auf der Karte \"Redstone\".",
      ]),
      [anyof("cccloud:cloud_switch", ["cccloud:cloud_switch", "cccloud:cloud_button"])], [xp(75)], deps=["cloud.rslamp"]),

    Q("cloud.rssensor", 12.25, 5,
      T("Cloud Sensor", "Cloud-Sensor"),
      T("&7When the door opens ...", "&7Wenn die Tür aufgeht ..."),
      T([
          "The &eCloud Sensor&r reads the redstone signal next to it and shows it on the dashboard.",
          "",
          "Use it in a &brule&r: \"when the sensor is on, then turn on the lamp and send a push notification\" - automation without a single line of Lua.",
      ], [
          "Der &eCloud-Sensor&r liest das Redstone-Signal neben sich und zeigt es im Dashboard.",
          "",
          "Nutze ihn in einer &bRegel&r: \"wenn der Sensor an ist, dann Lampe an und Push-Nachricht senden\" - Automatisierung ohne eine Zeile Lua.",
      ]),
      [item("cccloud:cloud_sensor")], [xp(100), table("common")], deps=["cloud.rsswitch", "cloud.rules"], hide_lines=True,
      shape="hexagon"),
]


# ---------------------------------------------------------------------------------------------
# build
# ---------------------------------------------------------------------------------------------
LOCALES = ["en_us", "de_de"]


def build_chapter(ids, chap, quests, link_quests=()):
    cid = ids.get(chap["key"] + ".chapter")
    lang = {loc: {} for loc in LOCALES}
    chapter_lang = {loc: {} for loc in LOCALES}
    for loc in LOCALES:
        chapter_lang[loc][f"chapter.{cid}.title"] = chap["title"][loc]
        chapter_lang[loc][f"chapter.{cid}.chapter_subtitle"] = [chap["subtitle"][loc]]

    out_quests = []
    for q in quests:
        qid = ids.get(q["key"])
        d = {}
        if q["icon"]:
            d["icon"] = q["icon"]
        d["x"] = F(q["x"])
        d["y"] = F(q["y"])
        if q["shape"]:
            d["shape"] = q["shape"]
        if q["hide_lines"]:
            d["hide_dependency_lines"] = True
        if q["deps"]:
            d["dependencies"] = [ids.get(k[4:] if k.startswith("ext:") else k) for k in q["deps"]]
        if q["dep_req"]:
            d["dependency_requirement"] = q["dep_req"]
        d["size"] = F(q["size"])
        if q["optional"]:
            d["optional"] = True
        d["id"] = qid
        tasks = []
        for i, t in enumerate(q["tasks"]):
            tid = ids.get(f"{q['key']}.task{i}")
            if t[0] == "item":
                td = {"id": tid, "type": "item", "item": t[1]}
                if t[2] != 1:
                    td["count"] = t[2]
                if len(t) > 3:
                    td.update(t[3])
            elif t[0] == "filter":
                _, flt, icon_id, icon_comp = t
                icon = {"id": icon_id, "count": 1}
                if icon_comp:
                    icon["components"] = icon_comp
                td = {"id": tid, "type": "item", "icon": icon,
                      "item": {"id": "ftbfiltersystem:smart_filter", "count": 1,
                               "components": {"ftbfiltersystem:filter": flt}}}
            else:
                td = {"id": tid, "type": "checkmark"}
                lang["en_us"][f"task.{tid}.title"] = t[1]
                lang["de_de"][f"task.{tid}.title"] = t[2]
            tasks.append(td)
        d["tasks"] = tasks
        rewards = []
        for i, r in enumerate(q["rewards"]):
            rid = ids.get(f"{q['key']}.reward{i}")
            if r[0] == "xp":
                rewards.append({"id": rid, "type": "xp", "xp": r[1]})
            elif r[0] == "item":
                rd = {"id": rid, "type": "item", "item": {"id": r[1], "count": 1}}
                if r[2] != 1:
                    rd["count"] = r[2]
                rewards.append(rd)
            elif r[0] == "random":
                rewards.append({"id": rid, "type": "random", "exclude_from_claim_all": True, "table_id": r[1]})
        if rewards:
            d["rewards"] = rewards
        out_quests.append(d)
        for loc in LOCALES:
            lang[loc][f"quest.{qid}.title"] = q["title"][loc]
            lang[loc][f"quest.{qid}.quest_subtitle"] = q["sub"][loc]
            lang[loc][f"quest.{qid}.quest_desc"] = q["desc"][loc]

    links = []
    for lk in link_quests:
        links.append({"linked_quest": ids.get(lk["quest"]), "x": F(lk["x"]), "y": F(lk["y"]),
                      "id": ids.get(lk["key"])})

    chapter = {
        "id": cid,
        "group": GROUP_TECH,
        "order_index": chap["order_index"],
        "icon": {"id": chap["icon"], "count": 1},
        "filename": chap["filename"],
        "default_quest_shape": "",
        "default_hide_dependency_lines": False,
        "quests": out_quests,
        "quest_links": links,
        "images": [],
    }
    return chapter, lang, chapter_lang


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(text)
        fh.write("\n")


def write_lang(path, d):
    write(path, emit(d, quote_all=True))


def main():
    pack = None
    if "--pack" in sys.argv:
        pack = sys.argv[sys.argv.index("--pack") + 1]
    ids = Ids(pack)

    link = [{"key": "cloud.link.cct_advanced", "quest": "cct.advanced", "x": -1.75, "y": 0}]

    cct, cct_lang, cct_clang = build_chapter(ids, CCT_CHAPTER, CCT)
    cloud, cloud_lang, cloud_clang = build_chapter(ids, CLOUD_CHAPTER, CLOUD, link)

    q = os.path.join(ROOT, "quests")
    write(os.path.join(q, "chapters", "cc_tweaked.json5"), emit(cct))
    write(os.path.join(q, "chapters", "cc_cloud.json5"), emit(cloud))
    for loc in LOCALES:
        write_lang(os.path.join(q, "lang", loc, "chapters", "cc_tweaked.json5"), cct_lang[loc])
        write_lang(os.path.join(q, "lang", loc, "chapters", "cc_cloud.json5"), cloud_lang[loc])
        merged = dict(cct_clang[loc])
        merged.update(cloud_clang[loc])
        write_lang(os.path.join(q, "lang", loc, "chapter.additions.json5"), merged)

    ids.save()
    print(f"CC: Tweaked: {len(CCT)} quests, CC: Cloud: {len(CLOUD)} quests")


if __name__ == "__main__":
    main()

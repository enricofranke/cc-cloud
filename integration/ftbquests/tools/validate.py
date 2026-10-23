#!/usr/bin/env python3
"""Validates the generated FTB Quests files against an ATM11 instance (read-only).

Checks: every JSON5 file parses (json5 reference parser via `npx json5`), every item id exists in a
jar of the instance (or the vanilla client jar), all our ids are
unique and do not clash with the pack, dependencies resolve and have no cycles, every quest has en_us
and de_de text, reward tables and the chapter group exist.

Usage: python3 tools/validate.py <instance-path> [--mc-jar <minecraft client jar>]
"""
import glob
import json
import os
import re
import subprocess
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
HEX16 = re.compile(r"\b[0-9A-F]{16}\b")
errors = []


def err(msg):
    errors.append(msg)


def parse_json5(path):
    r = subprocess.run(["npx", "--yes", "json5@2.2.3", path], capture_output=True, text=True)
    if r.returncode != 0:
        err(f"JSON5 parse error in {path}: {r.stderr.strip()[:300]}")
        return None
    return json.loads(r.stdout)


def registry(instance, mc_jar):
    ids = set()
    jars = glob.glob(os.path.join(instance, "mods", "*.jar"))
    if mc_jar:
        jars.append(mc_jar)
    pat = re.compile(r"^assets/([a-z0-9_.-]+)/items/([a-z0-9_./-]+)\.json$")
    for j in jars:
        try:
            with zipfile.ZipFile(j) as z:
                for n in z.namelist():
                    m = pat.match(n)
                    if m:
                        ids.add(f"{m.group(1)}:{m.group(2)}")
        except zipfile.BadZipFile:
            pass
    return ids


def collect_items(node, out):
    """Item ids from item stacks, icons and smart-filter expressions."""
    if isinstance(node, dict):
        if "id" in node and "count" in node and isinstance(node["id"], str) and ":" in node["id"]:
            out.add(node["id"])
        comps = node.get("components")
        if isinstance(comps, dict):
            flt = comps.get("ftbfiltersystem:filter")
            if flt:
                out.update(re.findall(r"item\(([a-z0-9_.-]+:[a-z0-9_./-]+)\)", flt))
                out.update(re.findall(r'_turtle_upgrade":"([a-z0-9_.-]+:[a-z0-9_./-]+)"', flt))
            for k, v in comps.items():
                if k.endswith("_turtle_upgrade") and isinstance(v, str):
                    out.add(v)
        for v in node.values():
            collect_items(v, out)
    elif isinstance(node, list):
        for v in node:
            collect_items(v, out)


def all_ids(node, out):
    if isinstance(node, dict):
        for k, v in node.items():
            if k == "id" and isinstance(v, str) and HEX16.fullmatch(v):
                out.append(v)
            all_ids(v, out)
    elif isinstance(node, list):
        for v in node:
            all_ids(v, out)


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    instance = sys.argv[1]
    mc_jar = sys.argv[sys.argv.index("--mc-jar") + 1] if "--mc-jar" in sys.argv else None
    if not mc_jar:
        guess = os.path.expanduser("~/Documents/curseforge/minecraft/Install/versions/26.1.2/26.1.2.jar")
        mc_jar = guess if os.path.exists(guess) else None
    pack_q = os.path.join(instance, "config", "ftbquests", "quests")

    # ---- pack ids (text scan: every 16-hex token in the quest book) and pack quest ids
    pack_tokens = set()
    pack_quest_ids = set()
    ours = json.load(open(os.path.join(HERE, "ids.json")))
    our_chapter_ids = {v for k, v in ours.items() if k.endswith(".chapter")}
    for p in glob.glob(os.path.join(pack_q, "**", "*.json5"), recursive=True):
        if os.path.basename(p) in ("cc_tweaked.json5", "cc_cloud.json5"):
            continue  # our own files when validating an instance where they are already installed
        txt = open(p, encoding="utf-8").read()
        pack_tokens.update(HEX16.findall(txt))
    pack_tokens -= our_chapter_ids  # chapter titles merged into lang/<locale>/chapter.json5
    groups = open(os.path.join(pack_q, "chapter_groups.json5"), encoding="utf-8").read()
    tables_txt = "".join(open(p, encoding="utf-8").read() for p in glob.glob(os.path.join(pack_q, "reward_tables", "*.json5")))
    table_ids = {int(h, 16) for h in HEX16.findall(tables_txt)}

    reg = registry(instance, mc_jar)
    if not mc_jar:
        print("warning: no Minecraft client jar found, minecraft:* ids are not checked")

    variants = {
        "chapters": {
            "chapters": [os.path.join(ROOT, "quests/chapters/cc_tweaked.json5"), os.path.join(ROOT, "quests/chapters/cc_cloud.json5")],
            "lang": {loc: [os.path.join(ROOT, f"quests/lang/{loc}/chapters/cc_tweaked.json5"),
                           os.path.join(ROOT, f"quests/lang/{loc}/chapters/cc_cloud.json5"),
                           os.path.join(ROOT, f"quests/lang/{loc}/chapter.additions.json5")] for loc in ("en_us", "de_de")},
        },
    }

    for vname, v in variants.items():
        chapters = [parse_json5(p) for p in v["chapters"]]
        if any(c is None for c in chapters):
            continue
        ids = []
        for c in chapters:
            all_ids(c, ids)
        dup = {i for i in ids if ids.count(i) > 1}
        if dup:
            err(f"[{vname}] duplicate ids: {sorted(dup)}")
        clash = set(ids) & pack_tokens
        if clash:
            err(f"[{vname}] ids already used in the pack: {sorted(clash)}")

        # table_id is a 64-bit integer: JavaScript (the json5 CLI) rounds it, so check the raw text
        for p in v["chapters"]:
            for t in re.findall(r"table_id: (\d+)", open(p, encoding="utf-8").read()):
                if int(t) not in table_ids:
                    err(f"[{vname}] {os.path.basename(p)}: unknown reward table {t}")
        quests = {}
        tasks = set()
        rewards = set()
        for c in chapters:
            if c["group"] not in groups:
                err(f"[{vname}] chapter group {c['group']} missing in chapter_groups.json5")
            for q in c["quests"]:
                quests[q["id"]] = q
                tasks.update(t["id"] for t in q.get("tasks", []))
                for r in q.get("rewards", []):
                    rewards.add(r["id"])
            for lk in c.get("quest_links", []):
                if lk["linked_quest"] not in quests and lk["linked_quest"] not in pack_tokens:
                    # may be in the other chapter, checked below
                    pass

        # dependencies and cycles
        for qid, q in quests.items():
            for d in q.get("dependencies", []):
                if d not in quests and d not in pack_tokens:
                    err(f"[{vname}] quest {qid}: unknown dependency {d}")
        for c in chapters:
            for lk in c.get("quest_links", []):
                if lk["linked_quest"] not in quests:
                    err(f"[{vname}] quest link {lk['id']} points to unknown quest {lk['linked_quest']}")
        state = {}

        def visit(n, stack):
            if state.get(n) == 1:
                err(f"[{vname}] dependency cycle: {' -> '.join(stack + [n])}")
                return
            if state.get(n) == 2:
                return
            state[n] = 1
            for d in quests.get(n, {}).get("dependencies", []):
                visit(d, stack + [n])
            state[n] = 2

        for n in quests:
            visit(n, [])

        # items
        items = set()
        for c in chapters:
            collect_items(c, items)
        items.discard("ftbfiltersystem:smart_filter") if "ftbfiltersystem:smart_filter" in reg else None
        for it in sorted(items):
            if it in reg:
                continue
            if it.startswith("minecraft:") and not mc_jar:
                continue
            err(f"[{vname}] item id not in any jar: {it}")

        # lang
        for loc, files in v["lang"].items():
            keys = {}
            for p in files:
                d = parse_json5(p)
                if d is None:
                    continue
                for k in d:
                    if k in keys:
                        err(f"[{vname}] {loc}: key {k} defined twice")
                    keys[k] = d[k]
            chapter_ids = {c["id"] for c in chapters}
            for k in keys:
                kind, oid = k.split(".")[0], k.split(".")[1]
                known = {"quest": quests, "task": tasks, "chapter": chapter_ids}.get(kind)
                if known is None or oid not in known:
                    err(f"[{vname}] {loc}: key {k} refers to an unknown object")
            for qid in quests:
                for suffix in ("title", "quest_desc"):
                    if f"quest.{qid}.{suffix}" not in keys:
                        err(f"[{vname}] {loc}: quest {qid} has no {suffix}")
            for qid, q in quests.items():
                for t in q.get("tasks", []):
                    if t["type"] == "checkmark" and f"task.{t['id']}.title" not in keys:
                        err(f"[{vname}] {loc}: checkmark task {t['id']} has no title")
            for cid in chapter_ids:
                if f"chapter.{cid}.title" not in keys:
                    err(f"[{vname}] {loc}: chapter {cid} has no title")
        print(f"[{vname}] {len(chapters)} chapters, {len(quests)} quests, {len(items)} item ids checked")

    # existing chapter files / order_index collisions in the target group
    for c in [parse_json5(p) for p in variants["chapters"]["chapters"]]:
        if c is None:
            continue
        target = os.path.join(pack_q, "chapters", c["filename"] + ".json5")
        if os.path.exists(target) and c["id"] not in open(target, encoding="utf-8").read():
            err(f"a different chapter named {c['filename']}.json5 already exists in the pack")
        for p in glob.glob(os.path.join(pack_q, "chapters", "*.json5")):
            t = open(p, encoding="utf-8").read()
            if f'group: "{c["group"]}"' in t and f"order_index: {c['order_index']}," in t and c["id"] not in t:
                print(f"note: order_index {c['order_index']} also used by {os.path.basename(p)}")

    if errors:
        print("\n".join("ERROR: " + e for e in errors))
        sys.exit(1)
    print("OK")


if __name__ == "__main__":
    main()

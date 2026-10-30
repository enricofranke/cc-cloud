// Advanced Cloud Sensor on the dashboard: its activity widget (parsing untrusted values, chips, events, relative
// time), the card in the layout, the rule trigger "Advanced Sensor detects …", the scene permission of its widgets,
// the map node and the error texts.
import { describe, expect, it } from "vitest";
import { normalizeNetwork, rejectText } from "../web/src/lib/conn.ts";
import { ICON_NAMES, defaultWidgetH, minWidgetW, resolveLayout } from "../web/src/lib/layout.ts";
import { TYPE_LABEL, isRedstoneDevice } from "../web/src/lib/network.ts";
import { describeCheck, toWire, validateRule, type Rule } from "../web/src/lib/rules.ts";
import { stepPerm } from "../web/src/lib/scenes.ts";
import { eventAge, eventText, parseActivity, rowText, sensorUplinks, sensorWidgetPerm } from "../web/src/lib/sensor.ts";
import type { Uplink } from "../web/src/lib/types.ts";

const SENSOR = "5e5e5e5e-0000-4000-8000-000000000001";
const NOW = Date.UTC(2026, 9, 30, 20, 0, 0);

/** The activity as an operator gets it (names) – and as a viewer gets it (the public form). */
const OPERATOR_VALUE = {
  armed: true,
  today: 5,
  zone: "3×3×3 in front",
  summary: "Steve and 2 Zombies inside · 5 today",
  inside: [
    { k: "player", n: "Steve", c: 1 },
    { k: "hostile", n: "Zombie", id: "minecraft:zombie", c: 2 },
  ],
  events: [
    { t: NOW - 5_000, in: true, k: "player", n: "Steve", id: "minecraft:player" },
    { t: NOW - 120_000, in: true, k: "hostile", n: "Zombie", id: "minecraft:zombie" },
    { t: NOW - 7_200_000, in: false, k: "passive", n: "Villager", id: "minecraft:villager" },
  ],
};
const VIEWER_VALUE = {
  ...OPERATOR_VALUE,
  summary: "A player and 2 Zombies inside · 5 today",
  inside: [{ k: "player", c: 1 }, OPERATOR_VALUE.inside[1]],
  events: [{ t: NOW - 5_000, in: true, k: "player", id: "minecraft:player" }, ...OPERATOR_VALUE.events.slice(1)],
};

/** The card as the mod publishes it (template "sensor", the section id is the sensor's device id). */
function card(value: unknown): Uplink {
  return {
    uplinkId: SENSOR,
    title: "Reactor room",
    online: true,
    mode: "direct",
    template: "sensor",
    widgets: [
      { id: "activity", type: "sensor_log", label: "Activity", opts: { order: 0, span: 4, icon: "eye" }, value: value as never },
      { id: "occupied", type: "indicator", label: "Occupied", opts: { order: 1, span: 2, onColor: "red" }, value: true },
      { id: "armed", type: "toggle", label: "Armed", opts: { order: 2, span: 2 }, value: true },
      { id: "detect", type: "select", label: "Detect", opts: { order: 3, span: 2, options: ["Everyone", "Players only", "Mobs only", "Hostile mobs only"] }, value: "Everyone" },
      { id: "allow", type: "text", label: "Only these players", opts: { order: 7, span: 2, maxLength: 256 }, value: "" },
    ],
  };
}

describe("activity widget", () => {
  it("reads the operator's and the viewer's form", () => {
    const op = parseActivity(OPERATOR_VALUE)!;
    expect(op.armed).toBe(true);
    expect(op.today).toBe(5);
    expect(op.inside.map(rowText)).toEqual(["Steve", "2 × Zombie"]);
    expect(op.events.map(eventText)).toEqual(["Steve came in", "Zombie came in", "Villager left"]);
    const viewer = parseActivity(VIEWER_VALUE)!;
    expect(viewer.inside.map(rowText)).toEqual(["A player", "2 × Zombie"]);
    expect(viewer.events.map(eventText)[0]).toBe("A player came in");
    expect(JSON.stringify(viewer)).not.toContain("Steve");
    expect(rowText({ k: "player", c: 3 })).toBe("3 players");
  });

  it("shows relative times", () => {
    const [a, b, c] = parseActivity(OPERATOR_VALUE)!.events;
    expect(eventAge(a, NOW)).toBe("just now");
    expect(eventAge(b, NOW)).toBe("2m ago");
    expect(eventAge(c, NOW)).toBe("2h ago");
    expect(eventAge({ ...a, t: NOW - 45_000 }, NOW)).toBe("45s ago");
  });

  it("drops anything malformed and stays bounded", () => {
    expect(parseActivity(null)).toBeNull();
    expect(parseActivity("Steve")).toBeNull();
    expect(parseActivity([1, 2])).toBeNull();
    const many = Array.from({ length: 500 }, (_, i) => ({ t: NOW - i, in: true, k: "hostile", n: "Zombie", id: "minecraft:zombie" }));
    const a = parseActivity({
      armed: "yes",
      today: -4,
      zone: "x".repeat(500),
      inside: [{ k: "dragon", n: "Ender" }, { k: "player", n: 7, c: "lots" }, null, ...Array.from({ length: 40 }, () => ({ k: "passive", n: "Cow", c: 1 }))],
      events: [{ t: "now", k: "player" }, { t: NOW, k: "boss" }, ...many],
    })!;
    expect(a.armed).toBe(true);
    expect(a.today).toBe(0);
    expect(a.zone.length).toBeLessThanOrEqual(48);
    expect(a.inside[0]).toEqual({ k: "player", c: 1 });
    expect(a.inside.length).toBeLessThanOrEqual(16);
    expect(a.events.length).toBe(18); // 20 looked at, the two broken ones dropped
    expect(a.events.every((e) => e.k === "hostile")).toBe(true);
  });
});

describe("the card on the dashboard", () => {
  it("is laid out with the activity first and full width", () => {
    const [page] = resolveLayout([card(OPERATOR_VALUE)], null);
    const c = page.cards[0];
    expect(c.title).toBe("Reactor room");
    expect(c.widgets[0].w.type).toBe("sensor_log");
    expect(c.widgets[0].box.w).toBeGreaterThanOrEqual(minWidgetW("sensor_log"));
    expect(minWidgetW("sensor_log")).toBe(6);
    expect(defaultWidgetH("sensor_log")).toBe(6);
    expect(ICON_NAMES).toContain("eye");
  });

  it("names the permission of its controls like the server", () => {
    const u = card(OPERATOR_VALUE);
    expect(sensorWidgetPerm("armed")).toBe("act.redstone");
    expect(stepPerm({ type: "widget", uplinkId: SENSOR, widgetId: "armed", value: false }, [u])).toBe("act.redstone");
    expect(stepPerm({ type: "widget", uplinkId: SENSOR, widgetId: "detect", value: "Players only" }, [u])).toBe("network.link");
  });

  it("explains a refused zone", () => {
    expect(rejectText({ ok: false, error: "zone_claimed" })).toMatch(/claim/);
    expect(rejectText({ ok: false, error: "zone_protected" })).toMatch(/spawn protection/);
  });

  it("shows up on the network map as a redstone device with its card", () => {
    const net = normalizeNetwork({
      nodes: [
        { id: "mf", type: "mainframe", name: "Base", dim: "minecraft:overworld", status: "online", range: 96 },
        { id: SENSOR, type: "advanced_cloud_sensor", name: "", dim: "minecraft:overworld", status: "online", range: 32, parent: "mf", uplinkId: SENSOR, redstone: true, group: "" },
      ],
      links: [],
      routes: {},
    })!;
    const node = net.nodes.find((n) => n.id === SENSOR)!;
    expect(node.type).toBe("advanced_cloud_sensor");
    expect(node.name).toBe("Advanced Cloud Sensor");
    expect(isRedstoneDevice(node.type)).toBe(true);
    expect(TYPE_LABEL.advanced_cloud_sensor).toBe("Advanced Cloud Sensor");
  });
});

describe("rule trigger", () => {
  const rule = (when: Rule["when"]): Rule => ({ name: "Reactor", enabled: true, cooldown: 30, notify: true, when, if: [], then: [{ type: "alert", text: "{who} betritt den Reaktorraum", level: "warn" }] });
  const other: Uplink = { uplinkId: "0a1b2c3d-0000-4000-8000-000000000009", title: "Lights", online: true, widgets: [{ id: "t", type: "toggle", value: true }] };

  it("only offers sensor cards and checks the fields like the server", () => {
    const ups = [card(OPERATOR_VALUE), other];
    expect(sensorUplinks(ups).map((u) => u.uplinkId)).toEqual([SENSOR]);
    expect(validateRule(rule({ type: "sensor", uplinkId: SENSOR, kind: "player" }), ups)).toBeNull();
    expect(validateRule(rule({ type: "sensor", uplinkId: SENSOR, kind: "player", player: "Steve" }), ups)).toBeNull();
    expect(validateRule(rule({ type: "sensor", uplinkId: "", kind: "any" }), ups)).toMatch(/Advanced Cloud Sensor/);
    expect(validateRule(rule({ type: "sensor", uplinkId: SENSOR, player: "not a name" }), ups)).toMatch(/player names/);
    expect(validateRule(rule({ type: "sensor", uplinkId: SENSOR, kind: "mob", player: "Steve" }), ups)).toMatch(/a player/);
  });

  it("reads in plain words and goes over the wire without empty fields", () => {
    const ups = [card(OPERATOR_VALUE)];
    expect(describeCheck({ type: "sensor", uplinkId: SENSOR, kind: "hostile" }, ups)).toBe("Reactor room detects a hostile mob");
    expect(describeCheck({ type: "sensor", uplinkId: SENSOR, kind: "player", player: "Steve" }, ups)).toBe("Reactor room detects the player Steve");
    expect(describeCheck({ type: "sensor", uplinkId: SENSOR }, ups)).toBe("Reactor room detects anyone or anything");
    const wire = JSON.parse(JSON.stringify(toWire(rule({ type: "sensor", uplinkId: SENSOR, kind: "any", player: undefined }))));
    expect(wire.when).toEqual({ type: "sensor", uplinkId: SENSOR, kind: "any" });
  });
});

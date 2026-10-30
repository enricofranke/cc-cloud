// v0.10 scenes and schedules on the client: validation like the server, step permissions, summaries in both languages.
import { describe, expect, it } from "vitest";
import { describeStep, missingPerms, newScene, normalizeScenes, pinSteps, runState, sceneToWire, stepPerm, stepsSummary, totalDelay, validateScene, type Scene } from "../web/src/lib/scenes.ts";
import { daysText, describeWhen, inText, nextRunText, nextScheduleRun, presetOf, validAt, zoneOffset } from "../web/src/lib/schedules.ts";
import { addsSchedule, describeAction, describeCheck, newRule, normalizeRules, scheduleRule, validateRule, type Rule } from "../web/src/lib/rules.ts";
import type { Uplink } from "../web/src/lib/types.ts";

const UPS: Uplink[] = [
  {
    uplinkId: "r",
    title: "Reactor",
    online: true,
    widgets: [
      { id: "power", type: "toggle", label: "Power" },
      { id: "scram", type: "button", label: "SCRAM", opts: { pin: true } },
      { id: "rods", type: "slider", label: "Rods", opts: { min: 0, max: 10 } },
    ],
  },
  { uplinkId: "c", title: "Computer #7", online: true, mode: "auto", widgets: [{ id: "redstone", type: "redstone_sides" }, { id: "reboot", type: "button", label: "Reboot" }] },
  { uplinkId: "me", title: "ME", online: true, widgets: [{ id: "search", type: "item_search" }] },
];
const scene = (steps: Scene["steps"], extra: Partial<Scene> = {}): Scene => ({ ...newScene(), name: "Night mode", steps, ...extra });

describe("scenes", () => {
  it("validates like the server (name, 1–16 steps, delays, total wait)", () => {
    const ok = scene([{ type: "widget", uplinkId: "r", widgetId: "power", value: false }, { type: "delay", seconds: 2 }, { type: "alert", text: "Night", level: "info" }]);
    expect(validateScene(ok, UPS)).toBeNull();
    expect(validateScene({ ...ok, name: " " }, UPS)).toMatch(/name/);
    expect(validateScene({ ...ok, name: "x".repeat(33) }, UPS)).toMatch(/32/);
    expect(validateScene({ ...ok, steps: [] }, UPS)).toMatch(/1–16/);
    expect(validateScene({ ...ok, steps: Array(17).fill({ type: "delay", seconds: 1 }) }, UPS)).toMatch(/1–16/);
    expect(validateScene(scene([{ type: "delay", seconds: 0.01 }]), UPS)).toMatch(/Step 1.*0\.05–300/);
    expect(validateScene(scene([{ type: "delay", seconds: 300 }, { type: "delay", seconds: 300 }, { type: "delay", seconds: 1 }]), UPS)).toMatch(/600/);
    expect(validateScene(scene([{ type: "craft", uplinkId: "me", item: "iron", amount: 1 }]), UPS)).toMatch(/Step 1.*minecraft:iron_ingot/);
    expect(validateScene(scene([{ type: "redstone", uplinkId: "c", side: "back", value: 16 }]), UPS)).toMatch(/0–15/);
    expect(validateScene({ ...ok, name: "" }, UPS, "de")).toBe("Gib der Szene einen Namen (bis 32 Zeichen).");
    expect(totalDelay([{ type: "delay", seconds: 1.5 }, { type: "alert", text: "x", level: "info" }, { type: "delay", seconds: 0.5 }])).toBe(2);
  });

  it("knows the permission each step needs (AccessGate.actionPerm) and which steps need the PIN", () => {
    expect(stepPerm({ type: "widget", uplinkId: "r", widgetId: "power", value: true }, UPS)).toBe("act.buttons");
    expect(stepPerm({ type: "widget", uplinkId: "c", widgetId: "reboot", value: true }, UPS)).toBe("act.redstone");
    expect(stepPerm({ type: "widget", uplinkId: "x", widgetId: "lamp_redstone", value: 1 }, UPS)).toBe("act.redstone");
    expect(stepPerm({ type: "redstone", uplinkId: "c", side: "top", value: 1 }, UPS)).toBe("act.redstone");
    expect(stepPerm({ type: "craft", uplinkId: "me", item: "minecraft:stick", amount: 1 }, UPS)).toBe("storage.craft");
    expect(stepPerm({ type: "dashboard", enabled: false }, UPS)).toBe("power.toggle");
    expect(stepPerm({ type: "delay", seconds: 1 }, UPS)).toBeNull();
    const steps: Scene["steps"] = [{ type: "alert", text: "x", level: "info" }, { type: "redstone", uplinkId: "c", side: "top", value: 1 }, { type: "widget", uplinkId: "r", widgetId: "scram", value: true }];
    expect(missingPerms(steps, UPS, (p) => p === "act.buttons")).toEqual([{ step: 2, perm: "act.redstone" }]);
    expect(pinSteps(steps, UPS)).toEqual([3]);
  });

  it("describes steps in English and German", () => {
    const steps: Scene["steps"] = [
      { type: "widget", uplinkId: "r", widgetId: "power", value: false },
      { type: "delay", seconds: 2.5 },
      { type: "redstone", uplinkId: "c", side: "back", value: 0 },
      { type: "widget", uplinkId: "r", widgetId: "scram", value: true },
      { type: "craft", uplinkId: "me", item: "minecraft:iron_ingot", amount: 1024 },
    ];
    expect(steps.map((s) => describeStep(s, UPS, "en"))).toEqual(["turn Power off (Reactor)", "wait 2.5 s", "set redstone back of Computer #7 to 0", "press SCRAM (Reactor)", "craft 1,024 × iron ingot (ME)"]);
    expect(steps.map((s) => describeStep(s, UPS, "de"))).toEqual(["Power ausschalten (Reactor)", "2,5 s warten", "Redstone hinten von Computer #7 auf 0", "SCRAM drücken (Reactor)", "1.024 × iron ingot craften (ME)"]);
    expect(stepsSummary(steps, "en")).toBe("5 steps · 2.5 s of waiting");
    expect(stepsSummary(steps.slice(0, 1), "de")).toBe("1 Schritt");
    expect(stepsSummary(steps, "de")).toBe("5 Schritte · 2,5 s Pausen");
  });

  it("normalizes the scenes document and the run state", () => {
    const doc = normalizeScenes({
      scenes: [
        { id: "0a1b2c3d", name: "Night", icon: "star", color: "pink", steps: [{ type: "alert", text: "x", level: "info" }, { type: "scene" }, { type: "delay", seconds: 1 }], owner: "Enrico", needsPin: true, perms: ["act.buttons"], runs: 2, lastRun: { by: "Lisa", source: "tablet", started: 1, finished: null, steps: [{ ok: true }] } },
        { id: "BAD", name: "x", steps: [] },
      ],
      log: [{ time: 5, scene: "0a1b2c3d", name: "Night", by: "Lisa", source: "evil", result: "ok" }],
      limit: 32,
      sectionId: "abc",
    })!;
    expect(doc.scenes).toHaveLength(1);
    const s = doc.scenes[0];
    expect(s).toMatchObject({ icon: "star", needsPin: true, owner: "Enrico", runs: 2 });
    expect(s.color).toBeUndefined();
    expect(s.steps.map((x) => x.type)).toEqual(["alert", "delay"]);
    expect(s.lastRun!.steps).toEqual([{ ok: true }, null]);
    expect(runState(s.lastRun)).toEqual({ state: "running" });
    expect(runState({ ...s.lastRun!, finished: 2, steps: [{ ok: true }, { ok: false, error: "not_allowed" }] })).toEqual({ state: "failed", error: "not_allowed" });
    expect(doc.log[0].source).toBe("dashboard");
    expect(normalizeScenes(null)).toBeNull();
    expect(Object.keys(sceneToWire({ ...s }))).toEqual(["id", "name", "icon", "confirm", "pin", "steps"]);
  });
});

describe("schedules", () => {
  it("writes days, triggers and next runs in plain words", () => {
    expect(daysText(["mon", "tue", "wed", "thu", "fri"], "en")).toBe("Mon–Fri");
    expect(daysText(["sat", "sun"], "de")).toBe("am Wochenende");
    expect(daysText(["sun", "mon", "tue", "wed", "thu", "fri", "sat"], "en")).toBe("every day");
    expect(daysText(["mon", "wed", "thu", "fri"], "en")).toBe("Mon, Wed–Fri");
    expect(daysText(["tue", "thu"], "de")).toBe("Di, Do");
    expect(describeWhen({ type: "schedule", days: ["mon", "tue", "wed", "thu", "fri"], at: "07:30" }, "en")).toBe("Mon–Fri at 07:30");
    expect(describeWhen({ type: "schedule", days: ["sat"], at: "22:00" }, "de")).toBe("Sa um 22:00");
    expect(describeWhen({ type: "time", time: 12000 }, "en")).toBe("at dusk (in-game)");
    expect(describeWhen({ type: "time", time: 23000 }, "de")).toBe("bei Sonnenaufgang (Spielzeit)");
    expect(describeWhen({ type: "time", time: 13000 }, "en")).toBe("at 19:00 in-game");
    expect(describeWhen({ type: "interval", minutes: 90 }, "de")).toBe("alle 90 Minuten");
    expect(presetOf(6000)).toBe("noon");
    expect(presetOf(18000)).toBe("midnight");
    expect(validAt("07:30")).toBe(true);
    expect(validAt("7:30")).toBe(false);
    expect(validAt("24:00")).toBe(false);
    const now = 1_000_000_000_000;
    expect(nextRunText(now + 2 * 3600_000, "en", now)).toBe("next run in 2 h");
    expect(nextRunText(now + 5 * 60_000, "de", now)).toBe("nächster Lauf in 5 Min.");
    expect(nextRunText(now + 3 * 86_400_000, "de", now)).toBe("nächster Lauf in 3 Tagen");
    expect(nextRunText(null, "en", now)).toBe("");
    expect(nextRunText(now - 1, "en", now)).toBe("due now");
    expect(inText(now + 10_000, "en", now)).toBe("now");
  });

  it("finds the next occurrence in the server's time zone", () => {
    // Wednesday 2026-10-28 10:00 UTC = 11:00 in Berlin (CET after the switch on 25 October)
    const wed = Date.UTC(2026, 9, 28, 10, 0);
    expect(zoneOffset(wed, "Europe/Berlin")).toBe(3600_000);
    expect(new Date(nextScheduleRun(["mon", "tue", "wed", "thu", "fri"], "07:30", "Europe/Berlin", wed)!).toISOString()).toBe("2026-10-29T06:30:00.000Z");
    expect(new Date(nextScheduleRun(["wed"], "12:00", "Europe/Berlin", wed)!).toISOString()).toBe("2026-10-28T11:00:00.000Z");
    expect(new Date(nextScheduleRun(["wed"], "10:59", "Europe/Berlin", wed)!).toISOString()).toBe("2026-11-04T09:59:00.000Z");
    // empty zone: UTC; summer time before the switch
    expect(new Date(nextScheduleRun(["sun"], "08:00", "", wed)!).toISOString()).toBe("2026-11-01T08:00:00.000Z");
    expect(new Date(nextScheduleRun(["sat"], "08:00", "Europe/Berlin", Date.UTC(2026, 9, 20))!).toISOString()).toBe("2026-10-24T06:00:00.000Z");
    expect(nextScheduleRun([], "08:00", "UTC", wed)).toBeNull();
  });

  it("validates schedule triggers and scene actions in rules", () => {
    const scenes = [
      { id: "0a1b2c3d", name: "Night", needsPin: false },
      { id: "11111111", name: "Scram", needsPin: true },
    ];
    const r: Rule = scheduleRule(scenes[0]);
    expect(r).toMatchObject({ name: "Night", when: { type: "schedule", days: ["mon", "tue", "wed", "thu", "fri"], at: "07:00" }, then: [{ type: "scene", sceneId: "0a1b2c3d" }] });
    expect(validateRule(r, UPS, 4096, { scenes })).toBeNull();
    expect(validateRule({ ...r, when: { type: "schedule", days: [], at: "07:00" } }, UPS, 4096, { scenes })).toMatch(/day/);
    expect(validateRule({ ...r, when: { type: "schedule", days: ["mon"], at: "7:00" } }, UPS, 4096, { scenes })).toMatch(/07:30/);
    expect(validateRule({ ...r, then: [{ type: "scene", sceneId: "11111111" }] }, UPS, 4096, { scenes })).toMatch(/needs the PIN/);
    expect(validateRule({ ...r, then: [{ type: "scene", sceneId: "deadbeef" }] }, UPS, 4096, { scenes })).toMatch(/pick a scene/);
    expect(validateRule({ ...r, when: { type: "interval", minutes: 4 } }, UPS, 4096, { scenes, minInterval: 5 })).toMatch(/every 5–1440/);
    expect(describeCheck(r.when, UPS)).toBe("Mon–Fri at 07:00");
    expect(describeAction(r.then[0], UPS, "en", scenes)).toBe("run scene “Night”");
    expect(describeAction(r.then[0], UPS, "de", scenes)).toBe("Szene „Night“ starten");
    expect(addsSchedule(r, null)).toBe(true);
    expect(addsSchedule(r, { ...newRule(UPS), when: { type: "interval", minutes: 5 } })).toBe(false);
    expect(addsSchedule(newRule(UPS), null)).toBe(false);
  });

  it("reads nextRun, lastFire, skipped runs and the schedules limits", () => {
    const doc = normalizeRules({
      rules: [{ id: "0a1b2c3d", name: "x", when: { type: "interval", minutes: 5 }, then: [], nextRun: 123, lastFire: 100 }],
      log: [{ time: 1, rule: "0a1b2c3d", name: "x", result: "skipped_unloaded", manual: false, skipped: true }],
      limit: 8,
      schedules: { count: 3, limit: 16, minInterval: 5, timezone: "Europe/Berlin" },
    })!;
    expect(doc.rules[0]).toMatchObject({ nextRun: 123, lastFire: 100 });
    expect(doc.log[0].skipped).toBe(true);
    expect(doc.schedules).toEqual({ count: 3, limit: 16, minInterval: 5, timezone: "Europe/Berlin" });
    expect(normalizeRules({ rules: [], log: [], limit: 8 })!.schedules).toBeNull();
  });
});

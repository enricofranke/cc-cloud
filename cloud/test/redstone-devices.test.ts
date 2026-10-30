// Cloud Redstone devices on the dashboard: map nodes (type, group), finding a device's card, card icons,
// the rs_device message (mock: rights checked, devices unknown) and the share deny.
import { afterEach, describe, expect, it } from "vitest";
import { createMockServer, type MockServer } from "../dev/mock-server.ts";
import { ICONS } from "../dev/protocol.ts";
import { normalizeNetwork, rejectText } from "../web/src/lib/conn.ts";
import { ICON_NAMES, guessIcon, resolveLayout } from "../web/src/lib/layout.ts";
import { TYPE_LABEL, isRedstoneDevice, redstoneCardOf, redstoneWidgetId } from "../web/src/lib/network.ts";
import type { Uplink } from "../web/src/lib/types.ts";
import { TestSocket } from "./helpers.ts";

const LAMP = "12345678-9abc-def0-1234-56789abcdef0";
const SENSOR = "0fedcba9-8765-4321-0fed-cba987654321";

/** A redstone card as the mod publishes it (mainframe-owned section, template "redstone"). */
function card(id: string, title: string, devices: { id: string; kind: "lamp" | "sensor" | "switch" }[]): Uplink {
  const widgets: Uplink["widgets"] = [];
  if (devices.filter((d) => d.kind !== "sensor").length >= 2) {
    widgets.push({ id: "all_on", type: "button", label: "All on", opts: { color: "green", order: 0 } });
    widgets.push({ id: "all_off", type: "button", label: "All off", opts: { color: "gray", order: 1 } });
  }
  devices.forEach((d, i) => {
    const w = redstoneWidgetId(d.id);
    if (d.kind === "lamp") {
      widgets.push({ id: w, type: "toggle", label: `Lamp ${i}`, opts: { icon: "lamp", order: 10 + 2 * i }, value: true });
      widgets.push({ id: `${w}_b`, type: "slider", label: `Lamp ${i} brightness`, opts: { min: 0, max: 15, step: 1, history: "24h", icon: "lamp" }, value: 15 });
    } else if (d.kind === "sensor") {
      widgets.push({ id: w, type: "indicator", label: "Door", opts: { icon: "sensor", onColor: "green" }, value: false });
      widgets.push({ id: `${w}_l`, type: "gauge", label: "Door level", opts: { min: 0, max: 15, history: "24h", icon: "sensor" }, value: 0 });
    } else widgets.push({ id: w, type: "toggle", label: "Gate", opts: { icon: "lever" }, value: false });
  });
  return { uplinkId: id, title, online: true, mode: "direct", template: "redstone", widgets };
}

describe("redstone devices on the map", () => {
  it("keeps device nodes with their group and labels them", () => {
    const net = normalizeNetwork({
      nodes: [
        { id: "mf", type: "mainframe", name: "Base", dim: "minecraft:overworld", status: "online", range: 96 },
        { id: LAMP, type: "cloud_lamp", name: "Porch", dim: "minecraft:overworld", status: "online", range: 32, parent: "mf", hops: 1, uplinkId: LAMP, redstone: true, group: "Outside" },
        { id: SENSOR, type: "cloud_sensor", name: "", dim: "minecraft:overworld", status: "unloaded", range: 0, redstone: true, group: "" },
      ],
      links: [{ a: "mf", b: LAMP, signal: 0.8, kind: "radio" }],
      routes: {},
    })!;
    const lamp = net.nodes.find((n) => n.id === LAMP)!;
    expect(lamp).toMatchObject({ type: "cloud_lamp", name: "Porch", redstone: true, group: "Outside" });
    expect(net.nodes.find((n) => n.id === SENSOR)).toMatchObject({ type: "cloud_sensor", name: "Cloud Sensor", group: "" });
    expect(isRedstoneDevice("cloud_switch") && !isRedstoneDevice("uplink")).toBe(true);
    expect(TYPE_LABEL.cloud_button).toBe("Cloud Button");
  });

  it("finds the card a device sits on by its widget id (it has no card of its own)", () => {
    expect(redstoneWidgetId(LAMP)).toBe("d_123456789abc");
    const ups = [card("c1", "Redstone", [{ id: SENSOR, kind: "sensor" }]), card("c2", "Outside", [{ id: LAMP, kind: "lamp" }])];
    expect(redstoneCardOf(ups, LAMP)?.uplinkId).toBe("c2");
    expect(redstoneCardOf(ups, SENSOR)?.uplinkId).toBe("c1");
    expect(redstoneCardOf(ups, "00000000-0000-0000-0000-000000000000")).toBeUndefined();
  });
});

describe("redstone cards", () => {
  it("get the icon of their devices and the new icons are part of the shared icon set", () => {
    for (const icon of ["lever", "button", "lamp", "sensor"]) {
      expect(ICON_NAMES).toContain(icon);
      expect(ICONS).toContain(icon);
    }
    expect([...ICONS]).toEqual([...ICON_NAMES]);
    const hall = card("c2", "Halle", [
      { id: LAMP, kind: "lamp" },
      { id: "aaaaaaaa-0000-0000-0000-000000000001", kind: "lamp" },
    ]);
    expect(guessIcon(hall.title, hall.widgets)).toBe("lamp");
    const pages = resolveLayout([hall], null);
    const c = pages[0].cards.find((x) => x.key === "c2")!;
    expect(c.title).toBe("Halle");
    expect(c.icon).toBe("lamp");
    expect(c.widgets.map((w) => w.w.id).slice(0, 2)).toEqual(["all_on", "all_off"]);
  });

  it("explains a device without a connection", () => {
    expect(rejectText({ ok: false, error: "device_offline" })).toMatch(/no connection to the mainframe/);
  });
});

let mock: MockServer | undefined;
const sockets: TestSocket[] = [];
afterEach(async () => {
  sockets.splice(0).forEach((s) => s.close());
  await mock?.close();
  mock = undefined;
});

describe("rs_device in the mock", () => {
  it("checks network.link and answers rs_result", async () => {
    mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, log: () => {} });
    const base = `http://127.0.0.1:${mock.port}`;
    const token = async (body: object) => (await (await fetch(`${base}/dev/token`, { method: "POST", body: JSON.stringify({ name: "Vitest", ...body }) })).json()).token;
    const open = async (body: object) => {
      const c = new TestSocket(`ws://127.0.0.1:${mock!.port}/ws/client`);
      sockets.push(c);
      await c.opened();
      c.send({ t: "auth", token: await token(body) });
      await c.next("snapshot");
      return c;
    };
    const admin = await open({});
    admin.send({ t: "rs_device", reqId: "a", deviceId: LAMP, group: "Hall" });
    expect(await admin.next("rs_result")).toMatchObject({ reqId: "a", ok: false, error: "unknown_node" });
    const viewer = await open({ canControl: false });
    viewer.send({ t: "rs_device", reqId: "v", deviceId: LAMP, name: "Porch" });
    const denied = await viewer.next("rs_result");
    expect(denied.ok).toBe(false);
    expect(["read_only", "not_allowed"]).toContain(denied.error);
  });
});

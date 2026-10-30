// Storage card v2: browse/favourites, item details, craft preview, CPUs, cancel, auto-stock (mock + client).
import { afterEach, describe, expect, it } from "vitest";
import { createMockServer, ME, type MockServer } from "../dev/mock-server.ts";
import { FakeAe2 } from "../dev/ae2-sim.ts";
import { MainframeConn, type CpuInfo, type ItemDoc, type StorageRow } from "../web/src/lib/conn.ts";
import { sleep } from "./helpers.ts";

let mock: MockServer | undefined;
const conns: MainframeConn[] = [];
afterEach(async () => {
  conns.splice(0).forEach((c) => c.stop());
  await mock?.close();
  mock = undefined;
});
const base = () => `http://127.0.0.1:${mock!.port}`;
async function connect(body: object = {}) {
  const t = (await (await fetch(`${base()}/dev/token`, { method: "POST", body: JSON.stringify({ name: "Vitest", ...body }) })).json()).token as string;
  const conn = new MainframeConn({ baseUrl: base(), token: t });
  conns.push(conn);
  conn.start();
  for (let i = 0; i < 200 && conn.status.value !== "open"; i++) await sleep(10);
  return conn;
}

describe("storage v2 (mock)", () => {
  it("browses with favourites pinned, sorts and pages", async () => {
    mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, actionDelayMs: 5, log: () => {} });
    const conn = await connect();
    const r = await conn.storage<{ rows: StorageRow[]; total: number }>(ME, "browse", { sort: "count", desc: true, filter: "all", offset: 0, limit: 10 }, "search");
    expect(r.rows).toHaveLength(10);
    expect(r.total).toBeGreaterThan(10);
    expect(r.rows.slice(0, 2).every((x) => x.favorite)).toBe(true); // diamond + fluix pinned
    const rest = r.rows.slice(2).map((x) => x.count);
    expect(rest).toEqual([...rest].sort((a, b) => b - a));
    const next = await conn.storage<{ rows: StorageRow[] }>(ME, "browse", { sort: "name", desc: false, offset: 10, limit: 10 }, "search");
    expect(next.rows[0].name).not.toBe(r.rows[0].name);
    await conn.storage(ME, "favorite", { name: "minecraft:cobblestone", on: true }, "search");
    const favs = await conn.storage<{ rows: StorageRow[] }>(ME, "browse", { filter: "favorites" }, "search");
    expect(favs.rows.map((x) => x.name)).toContain("minecraft:cobblestone");
    const crafts = await conn.storage<{ rows: StorageRow[] }>(ME, "craftables", { q: "" }, "search");
    expect(crafts.rows.every((x) => x.craftable)).toBe(true);
    const fl = await conn.storage<{ rows: { amount: number }[] }>(ME, "fluids", {}, "search");
    expect(fl.rows[0].amount).toBeGreaterThan(0);
  });

  it("previews, crafts with notify, lists CPUs and cancels", async () => {
    mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, actionDelayMs: 5, log: () => {} });
    const conn = await connect();
    const plan = await conn.craftPreview(ME, "ae2:fluix_crystal", 64, "search");
    expect(plan).toMatchObject({ name: "ae2:fluix_crystal", count: 64, missing: false });
    expect(plan.entries[0]).toMatchObject({ toCraft: 64 });
    expect((await conn.craftPreview(ME, "ae2:singularity", 4, "search")).missing).toBe(true);
    await expect(conn.craftPreview(ME, "minecraft:diamond", 4, "search")).rejects.toThrow("not_craftable");
    const search = conn.uplinks.value.find((u) => u.uplinkId === ME)!.widgets.find((w) => w.id === "search")!;
    const jobId = await new Promise<string>((res) => conn.action(ME, search, { op: "craft", name: "ae2:fluix_crystal", count: 64, notify: true }, false, { onResult: (r) => (res(r.jobId!), true) }));
    const cpus = await conn.storage<CpuInfo>(ME, "cpus", {}, "search");
    expect(cpus.cpus.length).toBe(2);
    expect(cpus.tasks.some((t) => t.id === jobId)).toBe(true);
    await conn.storage(ME, "cancel", { jobId }, "search");
    const doc = await conn.storage<ItemDoc>(ME, "item", { name: "ae2:fluix_crystal" }, "search");
    expect(doc.jobs.find((j) => j.jobId === jobId)).toMatchObject({ status: "cancelled", notify: true, source: "dashboard" });
    expect(doc.history.length).toBe(48);
    expect(doc.history.at(-1)).toBe(doc.count);
    await expect(conn.storage(ME, "item", { name: "nope:nothing" }, "search")).rejects.toThrow("unknown_item");
  });

  it("only operators change things; viewers can look", async () => {
    mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, log: () => {} });
    const viewer = await connect({ role: "viewer" });
    expect((await viewer.storage<{ rows: unknown[] }>(ME, "browse", {}, "search")).rows.length).toBeGreaterThan(0);
    await expect(viewer.storage(ME, "autostock", { name: "minecraft:iron_ingot", min: 10, target: 20, autoCraft: true }, "search")).rejects.toThrow("read_only");
    await expect(viewer.craftPreview(ME, "ae2:fluix_crystal", 1, "search")).rejects.toThrow("read_only");
  });
});

describe("auto-stock (level maintainer)", () => {
  it("crafts when below the minimum, one job at a time, and pauses after 3 failures", () => {
    const ae = new FakeAe2();
    const iron = ae.items.get("minecraft:iron_ingot")!;
    // iron is not craftable in the sim: use a craftable item
    const it = [...ae.items.values()].find((i) => i.craftable && i.name !== "ae2:singularity")!;
    it.count = 10;
    expect(ae.autostock({ name: it.name, min: 100, target: 300, autoCraft: true }).ok).toBe(true);
    ae.tick();
    expect(ae.watchRows().find((w) => w.name === it.name)).toMatchObject({ autoCraft: true, state: "crafting", target: 300 });
    const jobs = ae.jobs.filter((j) => j.name === it.name && j.source === "autostock");
    expect(jobs).toHaveLength(1);
    expect(jobs[0].count).toBe(64); // batch
    ae.tick();
    expect(ae.jobs.filter((j) => j.name === it.name && (j.status === "running" || j.status === "queued"))).toHaveLength(1);
    // missing resources three times -> paused
    const sing = ae.items.get("ae2:singularity");
    if (sing) {
      sing.craftable = true;
      sing.count = 0;
      ae.autostock({ name: "ae2:singularity", min: 1, target: 4, autoCraft: true });
      const w = ae.watch.find((x) => x.name === "ae2:singularity")!;
      for (let i = 0; i < 3; i++) {
        w.cooldownUntil = 0;
        ae.tick();
      }
      expect(w.state).toBe("paused");
    }
    expect(ae.autostock({ name: it.name, min: 50, target: 10, autoCraft: true })).toEqual({ ok: false, error: "invalid" });
    void iron;
  });

  it("waits for a free CPU with one waiting row per item instead of failed rows", () => {
    const ae = new FakeAe2();
    const [filler, ...items] = [...ae.items.values()].filter((i) => i.craftable && i.name !== "ae2:singularity" && i.name !== "enderio:octadic_capacitor").slice(0, 3);
    // Every CPU busy with long jobs (and the queue full): auto-stock gets no_cpu.
    while (ae.craft(filler.name, 4000).ok);
    for (const it of items) {
      it.count = 0;
      expect(ae.autostock({ name: it.name, min: 10, target: 20, autoCraft: true }).ok).toBe(true);
    }
    for (let round = 0; round < 4; round++) {
      for (const w of ae.watch) w.cooldownUntil = 0;
      ae.tick();
    }
    for (const it of items) {
      const rows = ae.jobs.filter((j) => j.name === it.name && j.source === "autostock");
      expect(rows.map((j) => j.status)).toEqual(["waiting"]);
      expect(ae.watch.find((w) => w.name === it.name)).toMatchObject({ state: "waiting_cpu" });
    }
    expect(ae.jobs.some((j) => j.source === "autostock" && j.status === "failed")).toBe(false);
  });
});

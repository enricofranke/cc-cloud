// v0.5 F long-term history: parsing, stats, the SVG path with gaps, and the mock API with permissions.
import { afterEach, describe, expect, it } from "vitest";
import { createMockServer, REACTOR, type MockServer } from "../dev/mock-server.ts";
import { csvName, fetchHistory, fetchHistoryCsv, historyOf, parseHistory, rangesUpTo, seriesPath, stats } from "../web/src/lib/history.ts";

let mock: MockServer | undefined;
afterEach(async () => {
  await mock?.close();
  mock = undefined;
});
const base = () => `http://127.0.0.1:${mock!.port}`;
const accountToken = async (player: string) =>
  (await (await fetch(`${base()}/dev/token`, { method: "POST", body: JSON.stringify({ account: player }) })).json()).token as string;

describe("history helpers", () => {
  it("reads opts.history only on numeric widgets", () => {
    expect(historyOf({ id: "a", type: "gauge", opts: { history: "24h" } })).toBe("24h");
    expect(historyOf({ id: "a", type: "label", opts: { history: "24h" } })).toBeNull();
    expect(historyOf({ id: "a", type: "gauge", opts: { history: "1y" } })).toBeNull();
    expect(rangesUpTo("7d")).toEqual(["1h", "24h", "7d"]);
  });

  it("parses points, keeps gaps and computes stats", () => {
    const d = parseHistory({ key: "w/x/y", range: "1h", kept: "1h", retentionHours: 1, step: 60000, points: [[3, 5], [1, 1], [2, null], ["x", 3], [4, "7"]] });
    expect(d.points).toEqual([[1, 1], [2, null], [3, 5], [4, null]]);
    expect(stats(d.points)).toMatchObject({ min: 1, max: 5, avg: 3, last: 5, count: 2 });
    expect(stats([[1, null]])).toBeNull();
    expect(() => parseHistory({ range: "1y", points: [] })).toThrow();
    // a gap breaks the line into two sub-paths
    expect(seriesPath([[0, 0], [1, 10], [2, null], [3, 10], [4, 0]], 0, 4, 0, 10, 4, 10).match(/M/g)).toHaveLength(2);
    expect(csvName("Fission Reactor", "Core temperature", "24h")).toBe("Fission_Reactor-Core_temperature-24h.csv");
  });
});

describe("history API (mock)", () => {
  it("serves JSON and CSV with the right permissions and retention", async () => {
    mock = await createMockServer({ port: 0, host: "127.0.0.1", tickMs: 0, extras: true, log: () => {} });
    const mf = mock.base.id;
    const owner = await accountToken("Enrico");
    const d = await fetchHistory(base(), owner, mf, "24h", "w", REACTOR, "temp");
    expect(d.kept).toBe("24h");
    expect(d.points.length).toBe(1440);
    expect(d.points.some((p) => p[1] === null)).toBe(true);
    // the base keeps 7 days (Memory upgrade): 30 d is cut to 7 d
    const long = await fetchHistory(base(), owner, mf, "30d", "w", REACTOR, "temp");
    expect(long.kept).toBe("7d");
    expect(long.step).toBe(300000);
    const csv = await fetchHistoryCsv(base(), owner, mf, "1h", "w", REACTOR, "temp");
    expect(csv.split("\n")[0]).toBe("time,value");
    expect(csv.split("\n")[1]).toMatch(/^\d{4}-\d\d-\d\dT.*Z,[\d.]*$/);
    // viewers may look but not export
    const viewer = await accountToken("Sam");
    await expect(fetchHistory(base(), viewer, mf, "1h", "w", REACTOR, "temp")).resolves.toBeTruthy();
    await expect(fetchHistoryCsv(base(), viewer, mf, "1h", "w", REACTOR, "temp")).rejects.toThrow("not_allowed");
    // widgets without opts.history
    await expect(fetchHistory(base(), owner, mf, "1h", "w", REACTOR, "coolant")).rejects.toThrow("no_history");
    expect((await fetch(`${base()}/api/mainframes/${mf}/history/1h/json/w/${REACTOR}/temp`)).status).toBe(401);
  });
});

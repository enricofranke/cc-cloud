// Claim guard on the dashboard: blocked cards and blocked redstone devices are parsed from the section, the text names
// the team only when the server sent it (viewers with view.positions), and refused actions explain why.
import { describe, expect, it } from "vitest";
import { normalizeBlocked, normalizeUplink, rejectText } from "../web/src/lib/conn.ts";
import { blockedText, keepBlockedWidgets } from "../web/src/lib/claims.ts";

describe("claim guard", () => {
  it("parses a blocked card, with and without the team", () => {
    const full = normalizeUplink({ uplinkId: "u", title: "Furnace", online: true, widgets: [], mode: "direct", blocked: { reason: "claim", team: "Rivals" } })!;
    expect(full.blocked).toEqual({ reason: "claim", team: "Rivals" });
    expect(full.widgets).toEqual([]);
    const stripped = normalizeUplink({ uplinkId: "u", title: "Furnace", online: true, widgets: [], blocked: { reason: "claim" } })!;
    expect(stripped.blocked).toEqual({ reason: "claim" });
    expect(normalizeUplink({ uplinkId: "u", title: "", widgets: [] })!.blocked).toBeUndefined();
  });

  it("ignores anything that is not a claim block", () => {
    expect(normalizeBlocked(null)).toBeUndefined();
    expect(normalizeBlocked("claim")).toBeUndefined();
    expect(normalizeBlocked({ reason: "other", team: "x" })).toBeUndefined();
    expect(normalizeBlocked({ reason: "claim", team: 7 })).toEqual({ reason: "claim" });
    expect(normalizeBlocked({ reason: "claim", team: "x".repeat(200) })!.team).toHaveLength(64);
  });

  it("parses the blocked devices of a redstone card", () => {
    const card = normalizeUplink({
      uplinkId: "r",
      title: "Redstone",
      online: true,
      mode: "direct",
      template: "redstone",
      widgets: [{ id: "d_aaaaaaaaaaaa", type: "toggle", label: "Gate", opts: {}, value: false }],
      blockedWidgets: { d_aaaaaaaaaaaa: { reason: "claim", team: "Rivals" }, junk: 5, d_bbbbbbbbbbbb: { reason: "claim" } },
    })!;
    expect(card.blockedWidgets).toEqual({ d_aaaaaaaaaaaa: { reason: "claim", team: "Rivals" }, d_bbbbbbbbbbbb: { reason: "claim" } });
    expect(card.widgets).toHaveLength(1);
    expect(normalizeUplink({ uplinkId: "r", title: "", widgets: [], blockedWidgets: [] })!.blockedWidgets).toBeUndefined();
  });

  it("says who claimed it only when the server named the team", () => {
    expect(blockedText({ reason: "claim", team: "Rivals" }, false, "en")).toBe("Blocked – this machine is in Rivals's claim");
    expect(blockedText({ reason: "claim" }, false, "en")).toBe("Blocked – this machine is in another team's claim");
    expect(blockedText({ reason: "claim", team: "Rivals" }, true, "en")).toBe("Blocked – in Rivals's claim");
    expect(blockedText({ reason: "claim", team: "Rivals" }, false, "de")).toBe("Gesperrt – diese Maschine liegt im Claim von Rivals");
    expect(blockedText({ reason: "claim" }, true, "de")).toBe("Gesperrt – im Claim eines anderen Teams");
  });

  it("explains a refused action", () => {
    expect(rejectText({ ok: false, error: "claim_blocked" } as never)).toMatch(/another team's claim/);
  });
});

describe("layout of a blocked card", () => {
  const doc = (widgets: Record<string, object>) => ({ v: 2 as const, pages: [], cards: {}, widgets });
  it("keeps the saved widget layout of blocked cards when the layout is saved", () => {
    const previous = doc({ "u1/energy": { x: 0, y: 0, w: 2, h: 1, label: "Power" }, "u2/temp": { x: 1, y: 0, w: 1, h: 1 } });
    const uplinks = [
      { uplinkId: "u1", title: "Furnace", online: true, widgets: [], blocked: { reason: "claim" as const } },
      { uplinkId: "u2", title: "Tank", online: true, widgets: [] },
    ];
    const next = keepBlockedWidgets(doc({ "u3/x": { x: 0, y: 0, w: 1, h: 1 } }), previous, uplinks);
    expect(next.widgets["u1/energy"]).toEqual({ x: 0, y: 0, w: 2, h: 1, label: "Power" });
    expect(next.widgets["u2/temp"]).toBeUndefined();
    expect(next.widgets["u3/x"]).toBeDefined();
    // what the editor saved for the card wins, and nothing changes without a blocked card
    expect(keepBlockedWidgets(doc({ "u1/energy": { x: 3, y: 0, w: 1, h: 1 } }), previous, uplinks).widgets["u1/energy"]).toEqual({ x: 3, y: 0, w: 1, h: 1 });
    const plain = doc({});
    expect(keepBlockedWidgets(plain, previous, [uplinks[1]])).toBe(plain);
    expect(keepBlockedWidgets(plain, null, uplinks)).toBe(plain);
  });
});

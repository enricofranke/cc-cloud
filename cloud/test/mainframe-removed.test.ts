import { describe, expect, it } from "vitest";
import { statusOf } from "../web/src/lib/status.ts";
import type { MainframeConn } from "../web/src/lib/conn.ts";

/** Only what statusOf reads from a connection. */
const conn = (info: object) => ({ status: { value: "open" }, info: { value: info }, lanOnly: { value: false } }) as unknown as MainframeConn;

describe("removed mainframe (block broken, data kept)", () => {
  it("is shown as removed with how to restore it and when the data goes", () => {
    const until = Date.UTC(2026, 10, 6, 12, 0);
    const st = statusOf(conn({ id: "m", name: "Mainframe", online: false, enabled: true, reason: "removed", removedUntil: until }));
    expect(st.kind).toBe("removed");
    expect(st.label).toBe("Removed");
    expect(st.text).toMatch(/Place its item again/);
    expect(st.text).toMatch(/\/cccloud mainframe recover/);
    expect(st.text).toMatch(/deleted on/);
  });

  it("an offline mainframe without that reason stays offline", () => {
    expect(statusOf(conn({ id: "m", name: "Mainframe", online: false, enabled: true })).kind).toBe("offline");
  });
});

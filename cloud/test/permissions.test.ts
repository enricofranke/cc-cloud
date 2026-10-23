// Fine-grained permissions on the client: presets, allow/deny diffs, catalog parity with the mock.
import { describe, expect, it } from "vitest";
import { GROUPS, PERMISSIONS, PERM_INFO, applyCustom, customLabel, customOf, presetHas, presetSet } from "../web/src/lib/permissions.ts";
import { PERMS, presetPerms } from "../dev/access-sim.ts";
import { MainframeConn } from "../web/src/lib/conn.ts";

describe("permissions", () => {
  it("has the same catalog and presets as the server side", () => {
    expect([...PERMISSIONS]).toEqual(PERMS.map((p) => p.id));
    for (const p of PERMS) expect(PERM_INFO[p.id as keyof typeof PERM_INFO].group).toBe(p.group);
    for (const r of ["viewer", "operator", "admin", "owner"]) expect([...presetSet(r)].sort()).toEqual([...presetPerms(r)].sort());
    expect(GROUPS.map((g) => g.id)).toEqual([...new Set(PERMS.map((p) => p.group))]);
  });

  it("turns toggles into minimal allow/deny lists and back", () => {
    const set = presetSet("operator");
    set.add("layout.edit");
    set.delete("act.redstone");
    const { allow, deny } = customOf("operator", set);
    expect(allow).toEqual(["layout.edit"]);
    expect(deny).toEqual(["act.redstone"]);
    expect(applyCustom("operator", allow, deny)).toEqual(set);
    expect(customLabel(allow, deny)).toBe("+1 −1");
    expect(customLabel()).toBe("");
    // deny wins
    expect(applyCustom("viewer", ["act.buttons"], ["act.buttons"]).has("act.buttons")).toBe(false);
  });

  it("conn.can() uses the server set, else presets, never for shares", () => {
    const c = new MainframeConn({ url: "ws://x", token: "t", mainframeId: "m" } as never);
    c.role.value = "operator";
    expect(c.can("act.buttons")).toBe(true);
    expect(c.can("layout.edit")).toBe(false);
    c.perms.value = new Set(["view", "layout.edit"]);
    expect(c.can("act.buttons")).toBe(false);
    expect(c.can("layout.edit")).toBe(true);
    expect(c.deniedCode("act.redstone")).toBe("read_only");
    c.perms.value = new Set(["view", "act.buttons"]);
    expect(c.deniedCode("layout.edit")).toBe("not_allowed");
    // older servers without roles: canControl decides
    c.perms.value = null;
    c.role.value = null;
    c.canControl.value = false;
    expect(c.can("act.buttons")).toBe(false);
    expect(c.can("view")).toBe(true);
    expect(presetHas(null, "view")).toBe(false);
    c.role.value = "share";
    expect(c.can("view")).toBe(false);
  });
});

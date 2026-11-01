import { mkdirSync, mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { collectPackages, renderNotices } from "../dev/third-party-notices.ts";

function pkg(root: string, name: string, manifest: object, license?: string) {
  const dir = join(root, "node_modules", name);
  mkdirSync(dir, { recursive: true });
  writeFileSync(join(dir, "package.json"), JSON.stringify(manifest));
  if (license) writeFileSync(join(dir, "LICENSE"), license);
  return join(dir, "index.js");
}

describe("third-party notices", () => {
  it("lists each bundled package once with its licence text and skips own modules", () => {
    const root = mkdtempSync(join(tmpdir(), "notices-"));
    const a = pkg(root, "@scope/a", { version: "1.2.3", license: "MIT" }, "MIT text of a");
    const b = pkg(root, "b", { version: "2.0.0", license: "ISC" });
    const entries = collectPackages([a, `${a}?commonjs`, b, "/repo/web/src/main.tsx", "\0virtual:thing"]);
    expect(entries.map((e) => `${e.name}@${e.version}`)).toEqual(["@scope/a@1.2.3", "b@2.0.0"]);
    const text = renderNotices(entries, "OFL text");
    expect(text).toContain("MIT text of a");
    expect(text).toContain("Licensed under ISC.");
    expect(text).toContain("OFL text");
    expect(text).toContain("https://github.com/enricofranke/cc-cloud");
  });

  it("fails the build for a package without any licence information", () => {
    const root = mkdtempSync(join(tmpdir(), "notices-"));
    const id = pkg(root, "mystery", { version: "0.1.0" });
    expect(() => collectPackages([id])).toThrow("no licence information for mystery@0.1.0");
  });
});

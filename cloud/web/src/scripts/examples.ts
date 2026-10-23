// Example gallery for script import: the repo's examples/*.lua plus the ROM's
// cloud-demo, bundled at build time (raw text, in the lazy scripts chunk).
import { describeScript, titleOf } from "../lib/scripts.ts";

const files = import.meta.glob(["../../../../examples/*.lua", "../../../../src/main/resources/data/computercraft/lua/rom/programs/cloud-demo.lua"], {
  query: "?raw",
  import: "default",
  eager: true,
}) as Record<string, string>;

export interface Example {
  name: string;
  title: string;
  text: string;
  run?: string;
  code: string;
  lines: number;
}

/** Hand-written blurbs win over the file's header comment. */
const BLURB: Record<string, string> = {
  "cloud-demo.lua": "Every widget type in one program – the quickest way to see what a dashboard can do. Also built into the computer as the program cloud-demo.",
};

export const EXAMPLES: Example[] = Object.entries(files)
  .map(([path, code]) => {
    const name = path.split("/").pop()!;
    const d = describeScript(code);
    return { name, title: titleOf(name), text: BLURB[name] ?? d.text, ...(d.run ? { run: d.run } : {}), code, lines: code.split("\n").length };
  })
  .sort((a, b) => a.name.localeCompare(b.name));

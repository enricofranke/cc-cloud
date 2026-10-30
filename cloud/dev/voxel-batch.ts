// DEV-ONLY: writes the voxel batch format of the mod (docs/SPEC.md "Voxel view –
// batch API", VoxelBatch.java) for the mock server and the tests: "CCVB",
// version 1, u32 BE header length, the JSON header, the runs of "ok" sections.
import { createHash } from "node:crypto";
import type { BatchStatus, PaletteEntry } from "../web/src/network/voxels.ts";

export interface EncodedSection {
  palette: PaletteEntry[];
  /** raw runs */
  runs: Uint8Array;
  exposed: number;
}
export interface OutSection {
  cx: number;
  cy: number;
  cz: number;
  status: BatchStatus;
  data?: EncodedSection;
}

/** 16 hex digits over palette and runs (like the mod: the value only has to change with the content). */
export function sectionHash(s: EncodedSection): string {
  const h = createHash("sha256");
  for (const p of s.palette) h.update(`${p.id}|${p.color}|${p.flags};`);
  h.update(s.runs);
  return h.digest("hex").slice(0, 16);
}

export function encodeBatch(dim: string, sections: OutSection[]): Uint8Array {
  const palette: PaletteEntry[] = [];
  const index = new Map<string, number>();
  const runs: Uint8Array[] = [];
  const entries = sections.map((s) => {
    const e: Record<string, unknown> = { c: [s.cx, s.cy, s.cz], s: s.status };
    if (s.data && (s.status === "ok" || s.status === "same")) {
      e.h = sectionHash(s.data);
      e.p = s.data.palette.map((p) => {
        const k = `${p.id}|${p.color}|${p.flags}`;
        let i = index.get(k);
        if (i === undefined) {
          i = palette.length;
          palette.push({ id: p.id, color: p.color, flags: p.flags });
          index.set(k, i);
        }
        return i;
      });
      if (s.status === "ok") {
        e.n = s.data.exposed;
        e.l = s.data.runs.length;
        runs.push(s.data.runs);
      }
    }
    return e;
  });
  const header = new TextEncoder().encode(JSON.stringify({ dim, palette, sections: entries }));
  const total = 9 + header.length + runs.reduce((a, r) => a + r.length, 0);
  const out = new Uint8Array(total);
  out.set([0x43, 0x43, 0x56, 0x42, 1, header.length >>> 24, (header.length >>> 16) & 255, (header.length >>> 8) & 255, header.length & 255]);
  out.set(header, 9);
  let at = 9 + header.length;
  for (const r of runs) {
    out.set(r, at);
    at += r.length;
  }
  return out;
}

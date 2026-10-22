// DEV-ONLY: a few tiny generated item icons for the mock's /api/icon route.
// Drawn here from 16×16 character maps (no Mojang assets), encoded as PNG.
import { crc32, deflateSync } from "node:zlib";

type Rgba = [number, number, number, number];

export function encodePng(w: number, h: number, px: Rgba[]): Buffer {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (w * 4 + 1)] = 0;
    for (let x = 0; x < w; x++) {
      const [r, g, b, a] = px[y * w + x];
      raw.set([r, g, b, a], y * (w * 4 + 1) + 1 + x * 4);
    }
  }
  const chunk = (type: string, data: Buffer) => {
    const len = Buffer.alloc(4);
    len.writeUInt32BE(data.length);
    const td = Buffer.concat([Buffer.from(type, "ascii"), data]);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(crc32(td) >>> 0);
    return Buffer.concat([len, td, crc]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 6; // RGBA
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk("IHDR", ihdr), chunk("IDAT", deflateSync(raw)), chunk("IEND", Buffer.alloc(0))]);
}

const hex = (h: string): Rgba => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16), 255];

function draw(map: string[], pal: Record<string, string>): Buffer {
  const px: Rgba[] = [];
  for (let y = 0; y < 32; y++)
    for (let x = 0; x < 32; x++) {
      const c = map[y >> 1]?.[x >> 1] ?? ".";
      px.push(c === "." ? [0, 0, 0, 0] : hex(pal[c]));
    }
  return encodePng(32, 32, px);
}

const INGOT = [
  "................",
  "................",
  "................",
  "................",
  ".......#####....",
  ".....##hhhhh#...",
  "...##hhlllll#...",
  ".##hhlllllll#...",
  "#hhlllllllll#...",
  "#llllllllll#....",
  "#mmmmmmmmm#.....",
  "#dddddddd#......",
  ".########.......",
  "................",
  "................",
  "................",
];
const GEM = [
  "................",
  "................",
  "....########....",
  "...#hhllllhh#...",
  "..#hllllllllh#..",
  ".#hllllllllllh#.",
  ".#llllllllllll#.",
  "..#mllllllllm#..",
  "...#mllllllm#...",
  "....#mllllm#....",
  ".....#mllm#.....",
  "......#dd#......",
  ".......##.......",
  "................",
  "................",
  "................",
];
const DUST = [
  "................",
  "................",
  "................",
  "................",
  "......h.........",
  "....l..l..h.....",
  "......lll.......",
  "..h..lllll..l...",
  ".....llmlll.....",
  "...lllmmmlll....",
  "..llmmmmmmmll...",
  ".llmmmddmmmmll..",
  "..lmmddddmmml...",
  "................",
  "................",
  "................",
];
const BLOCK = [
  "################",
  "#hhhhhhhhhhhhhh#",
  "#hllllmlllllllm#",
  "#llmllllllmlll##",
  "#lllllllllllll##",
  "#lmllllmllllmll#",
  "#lllllllllllll##",
  "#llllmlllllllll#",
  "#lmlllllllmlll##",
  "#lllllllllllll##",
  "#llllllmlllllll#",
  "#lmllllllllmll##",
  "#lllllllllllll##",
  "#lllmllllllllm##",
  "#mmmmmmmmmmmmmd#",
  "################",
];
const CHIP = [
  "................",
  "...#.#.#.#.#....",
  "..##########....",
  "..#hhhhhhhh##...",
  "###hllllllm###..",
  "..#hlddddlm##...",
  "###hldhhdlm###..",
  "..#hldhhdlm##...",
  "###hlddddlm###..",
  "..#hllllllm##...",
  "###mmmmmmmm###..",
  "..##########....",
  "...#.#.#.#.#....",
  "................",
  "................",
  "................",
];

const palette = (h: string, l: string, m: string, d: string) => ({ "#": "#1b1b1b", h, l, m, d });

/** item id → PNG (32×32) */
export const SAMPLE_ICONS: Record<string, Buffer> = {
  "minecraft:iron_ingot": draw(INGOT, palette("#ffffff", "#d8d8d8", "#a8a8a8", "#727272")),
  "minecraft:gold_ingot": draw(INGOT, palette("#fffde0", "#fcee4b", "#e4a91c", "#b26411")),
  "minecraft:copper_ingot": draw(INGOT, palette("#fcd1b4", "#e77c56", "#c15a36", "#8a3a22")),
  "minecraft:diamond": draw(GEM, palette("#e6fffc", "#4aedd9", "#20c5b5", "#0f8079")),
  "minecraft:emerald": draw(GEM, palette("#e6ffee", "#41f384", "#17c544", "#0a7a2a")),
  "ae2:fluix_crystal": draw(GEM, palette("#f2dcff", "#b67cf2", "#8a4cd2", "#4f2490")),
  "minecraft:redstone": draw(DUST, palette("#ff9a9a", "#ff2020", "#c00000", "#6e0000")),
  "minecraft:cobblestone": draw(BLOCK, palette("#b5b5b5", "#8a8a8a", "#6b6b6b", "#4a4a4a")),
  "minecraft:stone": draw(BLOCK, palette("#9e9e9e", "#7f7f7f", "#757575", "#5c5c5c")),
  "ae2:logic_processor": draw(CHIP, palette("#fff6a0", "#3f3f3f", "#f2d14a", "#e8c547")),
  "ae2:calculation_processor": draw(CHIP, palette("#c9f3ff", "#3f3f3f", "#7fd8f0", "#5ec8e5")),
  "minecraft:gold_nugget": draw(DUST, palette("#fffde0", "#fcee4b", "#e4a91c", "#b26411")),
  "minecraft:ender_pearl": draw(GEM, palette("#c8f5e8", "#2fa37a", "#1d6e54", "#0c3b2c")),
  "minecraft:prismarine_crystals": draw(GEM, palette("#f2fff8", "#a6e3d2", "#6fb8a6", "#467a6d")),
  "minecraft:paper": draw(BLOCK, palette("#ffffff", "#f0efe6", "#dddccf", "#b8b7a8")),
  "cccloud:cloud_core": draw(GEM, palette("#e8fbff", "#5ec8e5", "#2a7f99", "#174c5c")),
  "cccloud:cloud_uplink": draw(CHIP, palette("#e8fbff", "#3f3f3f", "#a8e6f5", "#5ec8e5")),
  "cccloud:cloud_mainframe": draw(BLOCK, palette("#ecec9c", "#2d2d2d", "#5ec8e5", "#ddd771")),
};

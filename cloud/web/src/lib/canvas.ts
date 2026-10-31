// The canvas widget (docs/SPEC.md "Canvas widget"): a frame is a list of draw commands from a Lua script. The mod checks
// every frame (CanvasFrame.java) and this file checks it again: a command that does not match exactly is dropped.
// Nothing from a frame ever becomes markup, CSS or a URL of its own: colours resolve to a fixed palette or a strict hex
// form, icons to the bundled masks, items to the mod's icon route, text goes through fillText.
import { ICON_NAMES } from "./layout.ts";
import { iconUrl, namespaceHue } from "./icons.ts";

export const CANVAS_LIMITS = {
  minSize: 16,
  maxWidth: 480,
  maxHeight: 320,
  defaultWidth: 160,
  defaultHeight: 90,
  commands: 1024,
  coord: 4096,
  text: 128,
  fontMin: 6,
  fontMax: 64,
  iconMin: 4,
  iconMax: 96,
  lineMax: 16,
  items: 64,
  pixels: 512,
  bitmap: 16 * 1024,
} as const;

export interface CanvasValue {
  canvas: 1;
  c: unknown[];
}

export type CanvasCommand =
  | { op: "clear"; color: string }
  | { op: "rect"; x: number; y: number; w: number; h: number; color: string; line?: number }
  | { op: "line"; x1: number; y1: number; x2: number; y2: number; color: string; line: number }
  | { op: "circle"; x: number; y: number; r: number; color: string; line?: number }
  | { op: "pixels"; color: string; points: number[] }
  | { op: "text"; x: number; y: number; text: string; color: string; size: number; align: "l" | "c" | "r" }
  | { op: "gradient"; x: number; y: number; w: number; h: number; from: string; to: string; dir: "h" | "v" }
  | { op: "icon"; x: number; y: number; name: string; size: number; color: string }
  | { op: "item"; x: number; y: number; name: string; size: number }
  | { op: "bitmap"; x: number; y: number; w: number; data: string; scale: number };

/** Colour names that follow the dashboard theme → the CSS variable they read. */
const THEME: Record<string, string> = {
  bg: "--bg", panel: "--panel", text: "--text", dim: "--muted", line: "--line-2", accent: "--gold", gold: "--gold", cyan: "--cyan",
  green: "--green", yellow: "--yellow", red: "--red", purple: "--purple", orange: "--orange", gray: "--gray",
};
/** CC's colours that have no theme meaning (same values as CC's default terminal palette). */
const FIXED: Record<string, string> = {
  white: "#f0f0f0", magenta: "#e57fd8", lightBlue: "#99b2f2", lime: "#7fcc19", pink: "#f2b2cc", lightGray: "#999999", blue: "#3366cc", brown: "#7f664c",
  black: "#111111",
};
/** paint colours 0-f for bitmaps: CC's terminal palette in the order of its blit characters. */
const BLIT = ["#f0f0f0", "#f2b233", "#e57fd8", "#99b2f2", "#dede6c", "#7fcc19", "#f2b2cc", "#4c4c4c", "#999999", "#4c99b2", "#b266e5", "#3366cc", "#7f664c", "#57a64e", "#cc4c4c", "#111111"];
const HEX = /^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$/;
const ITEM = /^[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,128}$/;
const BITMAP = /^[0-9a-f ]+$/;
// eslint-disable-next-line no-control-regex
const CONTROL = /[\u0000-\u001f\u007f-\u009f]/;

export function canvasSize(opts: { width?: unknown; height?: unknown } | undefined) {
  const size = (v: unknown, def: number, max: number) => (typeof v === "number" && Number.isInteger(v) && v >= CANVAS_LIMITS.minSize && v <= max ? v : def);
  return { w: size(opts?.width, CANVAS_LIMITS.defaultWidth, CANVAS_LIMITS.maxWidth), h: size(opts?.height, CANVAS_LIMITS.defaultHeight, CANVAS_LIMITS.maxHeight) };
}

export function isCanvasColor(c: unknown): c is string {
  return typeof c === "string" && (Object.hasOwn(THEME, c) || Object.hasOwn(FIXED, c) || HEX.test(c));
}

const isNum = (v: unknown, min: number, max: number): v is number => typeof v === "number" && Number.isFinite(v) && v >= min && v <= max;
const isCoord = (v: unknown): v is number => isNum(v, -CANVAS_LIMITS.coord, CANVAS_LIMITS.coord);
const isSize = (v: unknown): v is number => isNum(v, 0, CANVAS_LIMITS.coord);
const isWhole = (v: unknown, min: number, max: number): v is number => isNum(v, min, max) && Number.isInteger(v);
const isLine = (v: unknown): v is number => isNum(v, 0.01, CANVAS_LIMITS.lineMax);

function parseCommand(c: unknown[], state: { items: number }): CanvasCommand | null {
  const a = c as any[];
  switch (a[0]) {
    case "c":
      return a.length === 2 && isCanvasColor(a[1]) ? { op: "clear", color: a[1] } : null;
    case "r":
    case "R": {
      const outline = a[0] === "R";
      if (a.length < 6 || a.length > (outline ? 7 : 6) || !isCoord(a[1]) || !isCoord(a[2]) || !isSize(a[3]) || !isSize(a[4]) || !isCanvasColor(a[5])) return null;
      if (a.length === 7 && !isLine(a[6])) return null;
      return { op: "rect", x: a[1], y: a[2], w: a[3], h: a[4], color: a[5], ...(outline ? { line: a[6] ?? 1 } : {}) };
    }
    case "l":
      if (a.length < 6 || a.length > 7 || !isCoord(a[1]) || !isCoord(a[2]) || !isCoord(a[3]) || !isCoord(a[4]) || !isCanvasColor(a[5])) return null;
      if (a.length === 7 && !isLine(a[6])) return null;
      return { op: "line", x1: a[1], y1: a[2], x2: a[3], y2: a[4], color: a[5], line: a[6] ?? 1 };
    case "o":
    case "O": {
      const outline = a[0] === "O";
      if (a.length < 5 || a.length > (outline ? 6 : 5) || !isCoord(a[1]) || !isCoord(a[2]) || !isSize(a[3]) || !isCanvasColor(a[4])) return null;
      if (a.length === 6 && !isLine(a[5])) return null;
      return { op: "circle", x: a[1], y: a[2], r: a[3], color: a[4], ...(outline ? { line: a[5] ?? 1 } : {}) };
    }
    case "p": {
      if (a.length < 4 || a.length % 2 !== 0 || (a.length - 2) / 2 > CANVAS_LIMITS.pixels || !isCanvasColor(a[1])) return null;
      const points = a.slice(2);
      return points.every(isCoord) ? { op: "pixels", color: a[1], points } : null;
    }
    case "t": {
      if (a.length < 5 || a.length > 7 || !isCoord(a[1]) || !isCoord(a[2]) || typeof a[3] !== "string" || a[3].length > CANVAS_LIMITS.text || CONTROL.test(a[3]) || !isCanvasColor(a[4])) return null;
      const size = a[5] ?? 8;
      const align = a[6] ?? "l";
      if (!isWhole(size, CANVAS_LIMITS.fontMin, CANVAS_LIMITS.fontMax) || (align !== "l" && align !== "c" && align !== "r")) return null;
      return { op: "text", x: a[1], y: a[2], text: a[3], color: a[4], size, align };
    }
    case "g": {
      if (a.length < 7 || a.length > 8 || !isCoord(a[1]) || !isCoord(a[2]) || !isSize(a[3]) || !isSize(a[4]) || !isCanvasColor(a[5]) || !isCanvasColor(a[6])) return null;
      const dir = a[7] ?? "h";
      return dir === "h" || dir === "v" ? { op: "gradient", x: a[1], y: a[2], w: a[3], h: a[4], from: a[5], to: a[6], dir } : null;
    }
    case "i": {
      if (a.length < 4 || a.length > 6 || !isCoord(a[1]) || !isCoord(a[2]) || !(ICON_NAMES as readonly unknown[]).includes(a[3])) return null;
      const size = a[4] ?? 16;
      const color = a[5] ?? "text";
      return isWhole(size, CANVAS_LIMITS.iconMin, CANVAS_LIMITS.iconMax) && isCanvasColor(color) ? { op: "icon", x: a[1], y: a[2], name: a[3], size, color } : null;
    }
    case "s": {
      if (a.length < 4 || a.length > 5 || !isCoord(a[1]) || !isCoord(a[2]) || typeof a[3] !== "string" || !ITEM.test(a[3]) || iconUrl(a[3]) === null) return null;
      const size = a[4] ?? 16;
      if (!isWhole(size, CANVAS_LIMITS.iconMin, CANVAS_LIMITS.iconMax) || ++state.items > CANVAS_LIMITS.items) return null;
      return { op: "item", x: a[1], y: a[2], name: a[3], size };
    }
    case "b": {
      if (a.length < 5 || a.length > 6 || !isCoord(a[1]) || !isCoord(a[2]) || !isWhole(a[3], 1, CANVAS_LIMITS.maxWidth)) return null;
      const data = a[4];
      const scale = a[5] ?? 1;
      if (typeof data !== "string" || !data.length || data.length > CANVAS_LIMITS.bitmap || data.length % a[3] !== 0 || !BITMAP.test(data) || !isWhole(scale, 1, 16)) return null;
      return { op: "bitmap", x: a[1], y: a[2], w: a[3], data, scale };
    }
  }
  return null;
}

export function isCanvasValue(v: unknown): v is CanvasValue {
  return !!v && typeof v === "object" && (v as CanvasValue).canvas === 1 && Array.isArray((v as CanvasValue).c);
}

/** The drawable commands of a widget value: anything else in it is dropped, whatever it is. */
export function parseFrame(value: unknown): CanvasCommand[] {
  if (!isCanvasValue(value)) return [];
  const out: CanvasCommand[] = [];
  const state = { items: 0 };
  for (const raw of value.c.slice(0, CANVAS_LIMITS.commands)) {
    if (!Array.isArray(raw)) continue;
    const command = parseCommand(raw, state);
    if (command) out.push(command);
  }
  return out;
}

/** The theme's colours for a frame, read once per frame. */
export function themePalette(read: (cssVar: string) => string): Record<string, string> {
  const palette: Record<string, string> = { ...FIXED };
  for (const [name, cssVar] of Object.entries(THEME)) palette[name] = read(cssVar).trim() || "#888888";
  return palette;
}

export interface CanvasPaint {
  fillStyle: string | CanvasGradient;
  strokeStyle: string;
  lineWidth: number;
  font: string;
  textAlign: CanvasTextAlign;
  textBaseline: CanvasTextBaseline;
  fillRect(x: number, y: number, w: number, h: number): void;
  strokeRect(x: number, y: number, w: number, h: number): void;
  beginPath(): void;
  moveTo(x: number, y: number): void;
  lineTo(x: number, y: number): void;
  arc(x: number, y: number, r: number, a: number, b: number): void;
  fill(): void;
  stroke(): void;
  fillText(text: string, x: number, y: number): void;
  createLinearGradient(x0: number, y0: number, x1: number, y1: number): CanvasGradient;
  drawImage(image: CanvasImageSource, x: number, y: number, w: number, h: number): void;
}

export interface RenderEnv {
  palette: Record<string, string>;
  /** the 8×8 mask of a bundled icon */
  iconMask(name: string): string[] | undefined;
  /** the loaded image of an item, or null while there is none */
  itemImage(name: string): CanvasImageSource | null;
  font: string;
}

/** Draws a frame in logical pixels (the caller scales the context). A frame cannot change anything but these pixels. */
export function renderFrame(ctx: CanvasPaint, frame: CanvasCommand[], width: number, height: number, env: RenderEnv) {
  const colorOf = (c: string) => env.palette[c] ?? (HEX.test(c) ? c : "#888888");
  for (const c of frame) {
    switch (c.op) {
      case "clear":
        ctx.fillStyle = colorOf(c.color);
        ctx.fillRect(0, 0, width, height);
        break;
      case "rect":
        if (c.line === undefined) {
          ctx.fillStyle = colorOf(c.color);
          ctx.fillRect(c.x, c.y, c.w, c.h);
        } else {
          ctx.strokeStyle = colorOf(c.color);
          ctx.lineWidth = c.line;
          ctx.strokeRect(c.x, c.y, c.w, c.h);
        }
        break;
      case "line":
        ctx.strokeStyle = colorOf(c.color);
        ctx.lineWidth = c.line;
        ctx.beginPath();
        ctx.moveTo(c.x1, c.y1);
        ctx.lineTo(c.x2, c.y2);
        ctx.stroke();
        break;
      case "circle":
        ctx.beginPath();
        ctx.arc(c.x, c.y, c.r, 0, Math.PI * 2);
        if (c.line === undefined) {
          ctx.fillStyle = colorOf(c.color);
          ctx.fill();
        } else {
          ctx.strokeStyle = colorOf(c.color);
          ctx.lineWidth = c.line;
          ctx.stroke();
        }
        break;
      case "pixels":
        ctx.fillStyle = colorOf(c.color);
        for (let i = 0; i + 1 < c.points.length; i += 2) ctx.fillRect(Math.floor(c.points[i]), Math.floor(c.points[i + 1]), 1, 1);
        break;
      case "text":
        ctx.fillStyle = colorOf(c.color);
        ctx.font = `${c.size}px ${env.font}`;
        ctx.textAlign = c.align === "c" ? "center" : c.align === "r" ? "right" : "left";
        ctx.textBaseline = "top";
        ctx.fillText(c.text, c.x, c.y);
        break;
      case "gradient": {
        const g = c.dir === "v" ? ctx.createLinearGradient(0, c.y, 0, c.y + c.h) : ctx.createLinearGradient(c.x, 0, c.x + c.w, 0);
        g.addColorStop(0, colorOf(c.from));
        g.addColorStop(1, colorOf(c.to));
        ctx.fillStyle = g;
        ctx.fillRect(c.x, c.y, c.w, c.h);
        break;
      }
      case "icon": {
        const mask = env.iconMask(c.name);
        if (!mask) break;
        const px = c.size / 8;
        ctx.fillStyle = colorOf(c.color);
        mask.forEach((row, y) => {
          for (let x = 0; x < 8; x++) if (row[x] === "#") ctx.fillRect(c.x + x * px, c.y + y * px, px, px);
        });
        break;
      }
      case "item": {
        const image = env.itemImage(c.name);
        if (image) {
          ctx.drawImage(image, c.x, c.y, c.size, c.size);
        } else {
          ctx.fillStyle = `hsl(${namespaceHue(c.name)} 35% 40%)`;
          ctx.fillRect(c.x, c.y, c.size, c.size);
        }
        break;
      }
      case "bitmap": {
        // runs per row, so a big flat area is one rectangle
        const rows = c.data.length / c.w;
        for (let y = 0; y < rows; y++) {
          let x = 0;
          while (x < c.w) {
            const ch = c.data[y * c.w + x];
            let run = 1;
            while (x + run < c.w && c.data[y * c.w + x + run] === ch) run++;
            if (ch !== " ") {
              ctx.fillStyle = BLIT[parseInt(ch, 16)];
              ctx.fillRect(c.x + x * c.scale, c.y + y * c.scale, run * c.scale, c.scale);
            }
            x += run;
          }
        }
        break;
      }
    }
  }
}

/** Logical pixel of a pointer position inside the element's box, kept on the surface. */
export function canvasPoint(px: number, py: number, box: { width: number; height: number }, w: number, h: number) {
  const clamp = (v: number, max: number) => Math.max(0, Math.min(max - 1, Math.floor(v)));
  return { x: clamp((px / Math.max(1, box.width)) * w, w), y: clamp((py / Math.max(1, box.height)) * h, h) };
}

/**
 * Backing store of a surface: a whole multiple of the logical size, so logical pixels stay crisp, big enough for the
 * element on this screen. Bounded, so a canvas costs at most a few MB of memory however big the card is.
 */
export function backingScale(cssWidth: number, width: number, height: number, dpr: number) {
  const wanted = Math.ceil((Math.max(1, cssWidth) * Math.min(3, dpr)) / width);
  return Math.max(1, Math.min(wanted, Math.floor(2048 / Math.max(width, height))));
}

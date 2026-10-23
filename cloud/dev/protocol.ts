// Browser protocol schemas (docs/SPEC.md "Browser protocol"), used by the dev
// mock server to validate what the dashboard sends. The real server is the
// Java mod; this file documents the expected shapes for it too.
import { z } from "zod";

const widgetId = z.string().regex(/^[a-z0-9_]{1,32}$/);
const entityId = z.string().regex(/^[A-Za-z0-9_-]{1,64}$/);
export const scalar = z.union([z.number(), z.boolean(), z.string().max(256), z.null()]);
export type Scalar = z.infer<typeof scalar>;

const itemName = z.string().regex(/^[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,128}$/);
/** Operation values for item_search / watchlist widgets (v0.2). */
export const opValue = z.discriminatedUnion("op", [
  z.object({ op: z.literal("craft"), name: itemName, count: z.number().int() }),
  z.object({ op: z.literal("add"), name: itemName, min: z.number().int().min(0).max(2 ** 31 - 1).optional() }),
  z.object({ op: z.literal("remove"), name: itemName }),
  z.object({ op: z.literal("set_min"), name: itemName, min: z.number().int().min(0).max(2 ** 31 - 1) }),
  // v0.3 redstone_sides
  z.object({ op: z.literal("set_output"), side: z.enum(["top", "bottom", "left", "right", "front", "back"]), value: z.number().int().min(0).max(15) }),
]);
export type OpValue = z.infer<typeof opValue>;

// ---- layout document (docs/SPEC.md "Layout schema") ----
export const ACCENTS = ["gold", "cyan", "green", "yellow", "red", "purple", "orange", "gray"] as const;
export const ICONS = ["home", "bolt", "flame", "drop", "box", "fan", "lock", "warning", "gear", "chart", "star", "leaf", "heart", "cpu", "shield", "pick"] as const;
const layoutKey = z.string().min(1).max(160);
export const layoutDoc = z.strictObject({
  v: z.literal(1),
  pages: z
    .array(z.strictObject({ id: z.string().regex(/^[A-Za-z0-9_-]{1,32}$/), title: z.string().min(1).max(48), icon: z.enum(ICONS).optional() }))
    .max(12)
    .refine((p) => new Set(p.map((x) => x.id)).size === p.length, "duplicate page id"),
  cards: z.record(
    layoutKey,
    z.strictObject({
      page: z.string().max(32).optional(),
      order: z.number().int().min(0).max(10_000).optional(),
      span: z.number().int().min(1).max(4).optional(),
      title: z.string().max(48).optional(),
      hidden: z.boolean().optional(),
      color: z.enum(ACCENTS).optional(),
      icon: z.enum(ICONS).optional(),
    }),
  ).refine((c) => Object.keys(c).length <= 256, "too many cards"),
  widgets: z.record(
    layoutKey,
    z.strictObject({
      order: z.number().int().min(0).max(10_000).optional(),
      span: z.number().int().min(1).max(4).optional(),
      label: z.string().max(48).optional(),
      hidden: z.boolean().optional(),
    }),
  ).refine((w) => Object.keys(w).length <= 2048, "too many widgets"),
});
export type LayoutDocument = z.infer<typeof layoutDoc>;
export const MAX_LAYOUT_BYTES = 64 * 1024;

export const clientMessage = z.discriminatedUnion("t", [
  z.object({ t: z.literal("layout_set"), reqId: z.string().min(1).max(64), layout: z.unknown() }),
  z.object({ t: z.literal("net_unlink"), reqId: z.string().min(1).max(64), nodeId: z.string().min(1).max(64) }),
  z.object({
    t: z.literal("query"),
    reqId: z.string().min(1).max(64),
    uplinkId: entityId,
    id: widgetId,
    q: z.string().max(64),
  }),
  z.object({
    t: z.literal("action"),
    reqId: z.string().min(1).max(64),
    uplinkId: entityId,
    id: widgetId,
    value: z.union([scalar, z.record(z.string(), z.union([z.string().max(200), z.number(), z.boolean()]))]).optional(),
    pin: z.string().regex(/^\d{4,8}$/).optional(),
  }),
  z.object({ t: z.literal("pong") }),
  z.object({ t: z.literal("auth"), token: z.string().min(1).max(128) }),
]);
export type ClientMessage = z.infer<typeof clientMessage>;

export const pairRequest = z.object({
  code: z.string().max(32),
  name: z.string().trim().min(1).max(40),
});

export function parseJson(raw: unknown): unknown {
  try {
    const text =
      typeof raw === "string"
        ? raw
        : Buffer.isBuffer(raw)
          ? raw.toString("utf8")
          : Array.isArray(raw)
            ? Buffer.concat(raw).toString("utf8")
            : Buffer.from(raw as ArrayBuffer).toString("utf8");
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

/** Unambiguous pairing alphabet (no 0/O, 1/I/L). */
export const CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
export const normalizeCode = (s: string) => s.toUpperCase().replace(/[^A-Z0-9]/g, "");

/** Same headers the mod sends (docs/SPEC.md "Web hardening"). */
export const SECURITY_HEADERS = {
  "content-security-policy":
    "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; manifest-src 'self'; worker-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'",
  "x-content-type-options": "nosniff",
  "x-frame-options": "DENY",
  "referrer-policy": "no-referrer",
};


// Browser protocol schemas (docs/SPEC.md "Browser protocol"), used by the dev
// mock server to validate what the dashboard sends. The real server is the
// Java mod; this file documents the expected shapes for it too.
import { z } from "zod";

const widgetId = z.string().regex(/^[a-z0-9_]{1,32}$/);
const entityId = z.string().regex(/^[A-Za-z0-9_-]{1,64}$/);
export const scalar = z.union([z.number(), z.boolean(), z.string().max(256), z.null()]);
export type Scalar = z.infer<typeof scalar>;

export const clientMessage = z.discriminatedUnion("t", [
  z.object({
    t: z.literal("action"),
    reqId: z.string().min(1).max(64),
    uplinkId: entityId,
    id: widgetId,
    value: scalar.optional(),
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


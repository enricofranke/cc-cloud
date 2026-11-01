// Share codes, like Factorio blueprints: a preset as one line of text that works without any server or internet.
//
//   ccp1.<base64url of the raw-deflated JSON>.<checksum>
//
// `ccp1` is the version of the code itself, the checksum the first 8 hex digits of the SHA-256 of the payload text: a
// damaged or cut-off code is noticed before anything is unpacked. A code carries no signature, so whoever pastes one sees
// it marked as community content (edit/Presets.tsx). The registry's tools/lib.mjs writes the same format.
import { sha256Js } from "../scriptstore.ts";
import { PresetError, STORE_ID, parsePreset, type Preset } from "./format.ts";

export const CODE_VERSION = 1;
export const CODE_PREFIX = "ccp1";
/** A pasted code; a preset at the limits of format.ts compresses far below this. */
export const MAX_CODE_LENGTH = 48 * 1024;
/** Unpacked JSON: the limit stops a small code that unpacks to gigabytes (a zip bomb). */
export const MAX_JSON_BYTES = 128 * 1024;

export type CodeErrorCode = "empty" | "not_a_code" | "newer_code" | "damaged" | "too_large" | "unreadable" | "unsupported" | "invalid_preset" | "newer_format";

export class CodeError extends Error {
  readonly code: CodeErrorCode;
  readonly path?: string;
  constructor(code: CodeErrorCode, path?: string) {
    super(code);
    this.code = code;
    this.path = path;
  }
}

const checksum = (payload: string) => sha256Js(new TextEncoder().encode(payload)).slice(0, 8);

function toBase64Url(bytes: Uint8Array) {
  let s = "";
  for (let i = 0; i < bytes.length; i += 0x8000) s += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
function fromBase64Url(text: string) {
  const s = atob(text.replace(/-/g, "+").replace(/_/g, "/"));
  return Uint8Array.from(s, (c) => c.charCodeAt(0));
}

/** Reads a stream until it ends or passes `limit` bytes. */
async function readLimited(stream: ReadableStream<Uint8Array>, limit: number) {
  const reader = stream.getReader();
  const chunks: Uint8Array[] = [];
  let size = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    size += value.length;
    if (size > limit) {
      await reader.cancel().catch(() => {});
      throw new CodeError("too_large");
    }
    chunks.push(value);
  }
  const out = new Uint8Array(size);
  let at = 0;
  for (const c of chunks) {
    out.set(c, at);
    at += c.length;
  }
  return out;
}

/** The feed side of a (de)compression stream, without an unhandled rejection when the data is broken. */
function feed(transform: { writable: WritableStream<any>; readable: ReadableStream<Uint8Array> }, data: Uint8Array) {
  const writer = transform.writable.getWriter();
  writer.write(data).catch(() => {});
  writer.close().catch(() => {});
  return transform.readable;
}

/** The code of a preset. Store fields stay out: a code stands for itself. */
export async function encodePreset(preset: Preset): Promise<string> {
  if (typeof CompressionStream === "undefined") throw new CodeError("unsupported");
  const { id: _id, version: _version, author: _author, license: _license, ...rest } = preset;
  const json = new TextEncoder().encode(JSON.stringify(rest));
  const packed = await readLimited(feed(new CompressionStream("deflate-raw"), json), MAX_JSON_BYTES);
  const payload = toBase64Url(packed);
  if (payload.length > MAX_CODE_LENGTH) throw new CodeError("too_large");
  return `${CODE_PREFIX}.${payload}.${checksum(payload)}`;
}

/** Checks a pasted code and returns the preset inside. Throws {@link CodeError}; every problem has its own code. */
export async function decodePreset(text: string): Promise<Preset> {
  const code = text.replace(/\s+/g, "");
  if (!code) throw new CodeError("empty");
  if (code.length > MAX_CODE_LENGTH + CODE_PREFIX.length + 10) throw new CodeError("too_large");
  const version = /^ccp(\d{1,4})\./.exec(code)?.[1];
  if (!version) throw new CodeError("not_a_code");
  if (Number(version) > CODE_VERSION) throw new CodeError("newer_code");
  const m = /^ccp1\.([A-Za-z0-9_-]+)\.([0-9a-f]{8})$/.exec(code);
  if (!m || checksum(m[1]) !== m[2]) throw new CodeError("damaged");
  if (typeof DecompressionStream === "undefined") throw new CodeError("unsupported");
  let json: string;
  try {
    const bytes = await readLimited(feed(new DecompressionStream("deflate-raw"), fromBase64Url(m[1])), MAX_JSON_BYTES);
    json = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
  } catch (e) {
    if (e instanceof CodeError) throw e;
    throw new CodeError("unreadable");
  }
  let raw: unknown;
  try {
    raw = JSON.parse(json);
  } catch {
    throw new CodeError("unreadable");
  }
  try {
    const { id: _id, version: _version, author: _author, license: _license, ...rest } = parsePreset(raw);
    return rest;
  } catch (e) {
    if (e instanceof PresetError) throw new CodeError(e.code, e.path);
    throw e;
  }
}

export type PastedInput = { kind: "code"; code: string } | { kind: "store"; id: string; version?: string };

/** What was pasted: a code, or a short code (the id of a store preset, also `store:<id>` and `<id>@<version>`). */
export function readInput(text: string): PastedInput {
  const t = text.trim();
  const short = /^(?:store:)?([a-z0-9-]{3,40})(?:@(\d+\.\d+\.\d+))?$/.exec(t);
  if (short && STORE_ID.test(short[1])) return { kind: "store", id: short[1], ...(short[2] ? { version: short[2] } : {}) };
  return { kind: "code", code: t };
}

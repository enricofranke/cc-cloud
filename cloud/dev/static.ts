// Minimal static file server for the built dashboard (SPA fallback to index.html).
import { createReadStream } from "node:fs";
import { stat } from "node:fs/promises";
import { extname, join, normalize, sep } from "node:path";
import type { IncomingMessage, ServerResponse } from "node:http";
import { SECURITY_HEADERS } from "./protocol.ts";

const TYPES: Record<string, string> = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json",
  ".webmanifest": "application/manifest+json",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".ico": "image/x-icon",
  ".woff2": "font/woff2",
  ".woff": "font/woff",
  ".txt": "text/plain; charset=utf-8",
};

async function fileAt(path: string) {
  try {
    const s = await stat(path);
    return s.isFile() ? s : undefined;
  } catch {
    return undefined;
  }
}

export async function serveStatic(root: string, pathname: string, req: IncomingMessage, res: ServerResponse) {
  let rel: string;
  try {
    rel = normalize(decodeURIComponent(pathname)).replace(/^([/\\])+/, "");
  } catch {
    res.writeHead(400).end();
    return;
  }
  let file = join(root, rel);
  if (!file.startsWith(root.endsWith(sep) ? root : root + sep) && file !== root) {
    res.writeHead(403).end();
    return;
  }
  let s = rel ? await fileAt(file) : undefined;
  if (!s) {
    // Unknown asset -> 404; anything else is an app route -> index.html
    if (rel.startsWith("assets/") || extname(rel)) {
      res.writeHead(404, { "content-type": "text/plain" }).end("not found");
      return;
    }
    file = join(root, "index.html");
    s = await fileAt(file);
    if (!s) {
      res.writeHead(404, { "content-type": "text/plain" }).end("web app not built - run `pnpm build`");
      return;
    }
  }
  const ext = extname(file);
  const immutable = rel.startsWith("assets/");
  res.writeHead(200, {
    ...SECURITY_HEADERS,
    "content-type": TYPES[ext] ?? "application/octet-stream",
    "content-length": s.size,
    "cache-control": immutable ? "public, max-age=31536000, immutable" : "no-cache",
  });
  if (req.method === "HEAD") return void res.end();
  createReadStream(file).pipe(res);
}

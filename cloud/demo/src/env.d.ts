/// <reference types="vite/client" />

/** The module a swap stands in for (dev: demo/vite.config.ts). Only lib/icons.ts is wrapped this way. */
declare module "*?original" {
  export function iconUrl(name: string): string | null;
  export function namespaceHue(name: string): number;
}

const SEGMENT = /^[a-z0-9_.-]+$/;

/** `/api/icon/minecraft/iron_ingot.png`, or null for ids that cannot be valid. */
export function iconUrl(name: string): string | null {
  const i = name.indexOf(":");
  if (i <= 0) return null;
  const ns = name.slice(0, i);
  const segs = name.slice(i + 1).split("/");
  if (!SEGMENT.test(ns) || !segs.every((s) => SEGMENT.test(s) && s !== "." && s !== "..")) return null;
  return `/api/icon/${ns}/${segs.map(encodeURIComponent).join("/")}.png`;
}

/** Stable hue per namespace, so all items of one mod share a letter-tile colour. */
export function namespaceHue(name: string) {
  const ns = name.split(":")[0] || name;
  let h = 0;
  for (let i = 0; i < ns.length; i++) h = (h * 31 + ns.charCodeAt(i)) >>> 0;
  return h % 360;
}

// Item icons from the mod (/api/icon/<ns>/<path>.png) with a letter-tile fallback.
// Failed icons are remembered for the session and never requested again.
import { useState } from "preact/hooks";
import { iconUrl, namespaceHue } from "../lib/icons.ts";

const FAILED_KEY = "cccloud.icon.failed";
const failed: Set<string> = (() => {
  try {
    return new Set(JSON.parse(sessionStorage.getItem(FAILED_KEY) ?? "[]") as string[]);
  } catch {
    return new Set<string>();
  }
})();

function markFailed(name: string) {
  failed.add(name);
  try {
    sessionStorage.setItem(FAILED_KEY, JSON.stringify([...failed].slice(-2000)));
  } catch {}
}

export function ItemIcon({ name, displayName, size = 24 }: { name: string; displayName?: string; size?: 24 | 48 }) {
  const url = iconUrl(name);
  const [broken, setBroken] = useState(() => !url || failed.has(name));
  const letter = (displayName || name.split(":").pop() || "?").trim().charAt(0).toUpperCase() || "?";
  return (
    <span class={`item-icon s${size}`} aria-hidden="true">
      {broken ? (
        <span class="letter-tile" style={{ "--hue": String(namespaceHue(name)) }}>
          {letter}
        </span>
      ) : (
        <img
          src={url!}
          width={size}
          height={size}
          alt=""
          loading="lazy"
          decoding="async"
          draggable={false}
          onError={() => {
            markFailed(name);
            setBroken(true);
          }}
        />
      )}
    </span>
  );
}

// lib/icons.ts in the demo: item icons are static files under the demo's base path (<img> does not go through fetch).
// Items without a file stay letter tiles right away, instead of a 404 for every row.
import { iconUrl as real, namespaceHue } from "../../../web/src/lib/icons.ts?original";

declare const __DEMO_ICONS__: string[];
const known = new Set(__DEMO_ICONS__);

export { namespaceHue };

export const iconUrl = (name: string) => {
  const url = known.has(name) ? real(name) : null;
  return url && `${import.meta.env.BASE_URL}${url.slice(1)}`;
};

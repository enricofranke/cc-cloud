// The presets that ship with the dashboard: they work offline and need no store. Each one is a plain JSON file in the
// same format as a store preset, so the website can show them as well.
import type { Preset } from "../lib/presets/format.ts";
import { parsePreset } from "../lib/presets/format.ts";
import alerts from "./official/alerts-status.json" with { type: "json" };
import overview from "./official/base-overview.json" with { type: "json" };
import doors from "./official/doors-redstone.json" with { type: "json" };
import farm from "./official/farm-production.json" with { type: "json" };
import storage from "./official/me-command-centre.json" with { type: "json" };
import phone from "./official/phone-quick-panel.json" with { type: "json" };
import power from "./official/power-grid.json" with { type: "json" };
import reactor from "./official/reactor-control.json" with { type: "json" };

let cached: Preset[] | undefined;

/** In the order the picker shows them. Parsed on first use: the check is the same as for any other preset. */
export function officialPresets(): Preset[] {
  return (cached ??= [overview, power, storage, reactor, farm, doors, alerts, phone].map((raw) => parsePreset(raw)));
}

// Claim guard (docs/SECURITY.md "Claims"): a card or a Cloud Redstone device whose machine is in another team's claim
// reads nothing and refuses every control. The team's name only reaches viewers allowed to see positions.
import { lang, tr, type Lang } from "./i18n.ts";
import type { LayoutDoc } from "./layout.ts";
import type { Blocked, Uplink } from "./types.ts";

/** "Blocked – this machine is in Rivals's claim" (a card) or "Blocked – in Rivals's claim" (one device on a card). */
export function blockedText(b: Blocked, widget = false, l: Lang = lang.value): string {
  if (widget) return b.team ? tr(l, "claim.widget", { team: b.team }) : tr(l, "claim.widgetOther");
  return b.team ? tr(l, "claim.blocked", { team: b.team }) : tr(l, "claim.blockedOther");
}

/**
 * A blocked card has no widgets while the claim lasts. Saving the layout then would drop its widgets' positions, names
 * and colours: they are carried over from the layout saved before, so the card looks the same once it works again.
 */
export function keepBlockedWidgets(doc: LayoutDoc, previous: LayoutDoc | null | undefined, uplinks: Uplink[]): LayoutDoc {
  if (!previous) return doc;
  const blocked = new Set(uplinks.filter((u) => u.blocked).map((u) => u.uplinkId));
  if (!blocked.size) return doc;
  let widgets: LayoutDoc["widgets"] | null = null;
  for (const [key, patch] of Object.entries(previous.widgets ?? {})) {
    const slash = key.indexOf("/");
    if (slash < 0 || !blocked.has(key.slice(0, slash)) || doc.widgets[key]) continue;
    widgets ??= { ...doc.widgets };
    widgets[key] = patch;
  }
  return widgets ? { ...doc, widgets } : doc;
}

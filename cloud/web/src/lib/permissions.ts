// Fine-grained permissions (docs/SPEC.md "Fine-grained permissions per mainframe"):
// the catalog with labels and explanations, the role presets, and allow/deny diffs.

export const PERMISSIONS = [
  "view",
  "view.positions",
  "act.buttons",
  "act.redstone",
  "power.toggle",
  "storage.search",
  "storage.craft",
  "storage.autostock",
  "scripts.upload",
  "scripts.run",
  "scripts.delete",
  "terminal",
  "layout.edit",
  "rules.edit",
  "rules.toggle",
  "push.manage",
  "devices.manage",
  "network.link",
  "access.manage",
  "shares.create",
  "pin.manage",
] as const;
export type Perm = (typeof PERMISSIONS)[number];

export const GROUPS: { id: string; label: string }[] = [
  { id: "view", label: "Watching" },
  { id: "act", label: "Controls" },
  { id: "storage", label: "Storage" },
  { id: "scripts", label: "Scripts & terminal" },
  { id: "automation", label: "Layout & automation" },
  { id: "manage", label: "Management" },
];

export const PERM_INFO: Record<Perm, { label: string; help: string; group: string; risky?: boolean }> = {
  view: { label: "View the dashboard", help: "Values, cards and alerts. Without it the player has no access at all.", group: "view" },
  "view.positions": { label: "See coordinates", help: "Network map, 2D/3D world view and the server/claims/backup cards.", group: "view" },
  "act.buttons": { label: "Use controls", help: "Buttons, toggles, sliders and inputs on cards.", group: "act" },
  "act.redstone": { label: "Switch redstone", help: "Redstone sides and computer power (turn on, reboot, shut down).", group: "act" },
  "power.toggle": { label: "Turn the dashboard on/off", help: "The switch on the mainframe block.", group: "act" },
  "storage.search": { label: "Search storage", help: "Find items and see amounts in ME/RS networks.", group: "storage" },
  "storage.craft": { label: "Craft", help: "Start and cancel crafting jobs (uses your resources).", group: "storage" },
  "storage.autostock": { label: "Auto-stock", help: "Watchlist targets and automatic crafting when stock runs low.", group: "storage" },
  "scripts.upload": { label: "Upload scripts", help: "Code runs on your computers and can control everything attached.", group: "scripts", risky: true },
  "scripts.run": { label: "Run scripts", help: "Start programs that are already on a computer.", group: "scripts" },
  "scripts.delete": { label: "Delete scripts", help: "Remove files from computers.", group: "scripts" },
  terminal: { label: "Terminal", help: "Type into a computer's shell from the browser – full control of that computer.", group: "scripts", risky: true },
  "layout.edit": { label: "Edit the layout", help: "Pages, cards and widgets for everyone on this mainframe.", group: "automation" },
  "rules.edit": { label: "Edit rules", help: "Create and change automation rules (if X then do Y).", group: "automation" },
  "rules.toggle": { label: "Enable rules", help: "Switch existing rules on/off and run them once.", group: "automation" },
  "push.manage": { label: "Push targets", help: "Send alerts to Discord, ntfy or webhooks.", group: "automation" },
  "devices.manage": { label: "Manage devices", help: "Pairing codes, approvals and revoking browsers.", group: "manage" },
  "network.link": { label: "Link devices", help: "Link and unlink blocks with the Link Card.", group: "manage" },
  "access.manage": { label: "Manage access", help: "Change who may do what – including this page.", group: "manage", risky: true },
  "shares.create": { label: "Share links", help: "Create read-only links for people without an account.", group: "manage" },
  "pin.manage": { label: "Manage the PIN", help: "Set or change the mainframe PIN.", group: "manage" },
};

const VIEWER: readonly string[] = ["view", "storage.search"];
const OPERATOR: readonly string[] = [...VIEWER, "view.positions", "act.buttons", "act.redstone", "storage.craft", "rules.toggle"];

/** The presets: viewer ⊂ operator ⊂ admin = owner = all. */
export function presetHas(role: string | null | undefined, perm: string) {
  if (role === "admin" || role === "owner") return true;
  if (role === "operator") return OPERATOR.includes(perm);
  if (role === "viewer") return VIEWER.includes(perm);
  return false;
}
export const presetSet = (role: string | null | undefined) => new Set<string>(PERMISSIONS.filter((p) => presetHas(role, p)));

/** preset ∪ allow − deny (deny wins). */
export function applyCustom(role: string, allow: readonly string[] = [], deny: readonly string[] = []) {
  const s = presetSet(role);
  allow.forEach((p) => s.add(p));
  deny.forEach((p) => s.delete(p));
  return s;
}

/** The minimal allow/deny lists that turn a preset into `perms`. */
export function customOf(role: string, perms: ReadonlySet<string>) {
  const allow: string[] = [];
  const deny: string[] = [];
  for (const p of PERMISSIONS) {
    const inPreset = presetHas(role, p);
    if (perms.has(p) && !inPreset) allow.push(p);
    if (!perms.has(p) && inPreset) deny.push(p);
  }
  return { allow, deny };
}

/** "+2 −1" style summary of a customization ("" for a plain preset). */
export function customLabel(allow: readonly string[] = [], deny: readonly string[] = []) {
  return [allow.length ? `+${allow.length}` : "", deny.length ? `−${deny.length}` : ""].filter(Boolean).join(" ");
}

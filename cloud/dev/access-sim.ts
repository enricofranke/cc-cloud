// DEV-ONLY: players, roles, teams and share links for the mock (docs/SPEC.md v0.6).
import { createHash, randomBytes, randomUUID } from "node:crypto";

export type Role = "viewer" | "operator" | "admin" | "owner";
export const RANK: Record<string, number> = { none: 0, share: 0, viewer: 1, operator: 2, admin: 3, owner: 4 };
export const higher = (a: Role | undefined, b: Role | undefined): Role | undefined => ((RANK[a ?? "none"] ?? 0) >= (RANK[b ?? "none"] ?? 0) ? a : b);

export interface Player {
  uuid: string;
  player: string;
}

/** Players known to the mock server. */
export const PLAYERS: Player[] = [
  { uuid: "8e2f6c4a-0000-4000-8000-000000000001", player: "Enrico" },
  { uuid: "8e2f6c4a-0000-4000-8000-000000000002", player: "Lisa" },
  { uuid: "8e2f6c4a-0000-4000-8000-000000000003", player: "Tom" },
  { uuid: "8e2f6c4a-0000-4000-8000-000000000004", player: "Sam" },
  { uuid: "8e2f6c4a-0000-4000-8000-000000000005", player: "Mia" },
];
export const playerByName = (name: string) => PLAYERS.find((p) => p.player.toLowerCase() === name.toLowerCase());
export const playerByUuid = (uuid: string) => PLAYERS.find((p) => p.uuid === uuid);

export interface Team {
  provider: "ftbteams" | "opac" | "scoreboard";
  id: string;
  name: string;
  /** uuid → rank */
  members: Record<string, string>;
}
export const RANKS: Record<Team["provider"], string[]> = {
  ftbteams: ["owner", "officer", "member", "ally"],
  opac: ["leader", "member", "ally"],
  scoreboard: ["member"],
};
export const DEFAULT_MAPPING: Record<Team["provider"], Record<string, Role | "none">> = {
  ftbteams: { owner: "admin", officer: "admin", member: "operator", ally: "viewer" },
  opac: { leader: "admin", member: "operator", ally: "viewer" },
  scoreboard: { member: "operator" },
};
export const TEAMS: Team[] = [
  {
    provider: "ftbteams",
    id: "ftb-builders",
    name: "Builders",
    members: { [PLAYERS[0].uuid]: "owner", [PLAYERS[2].uuid]: "member", [PLAYERS[3].uuid]: "ally" },
  },
  { provider: "opac", id: "opac-nightshift", name: "Night Shift", members: { [PLAYERS[1].uuid]: "leader", [PLAYERS[4].uuid]: "member" } },
  { provider: "scoreboard", id: "sb-red", name: "red", members: { [PLAYERS[4].uuid]: "member" } },
];

/** docs/SPEC.md "Fine-grained permissions": the catalog in display order, with its group. */
export const PERMS: { id: string; group: string }[] = [
  ["view", "view"], ["view.positions", "view"],
  ["act.buttons", "act"], ["act.redstone", "act"], ["power.toggle", "act"],
  ["storage.search", "storage"], ["storage.craft", "storage"], ["storage.autostock", "storage"],
  ["scripts.upload", "scripts"], ["scripts.run", "scripts"], ["scripts.delete", "scripts"], ["terminal", "scripts"],
  ["layout.edit", "automation"], ["rules.edit", "automation"], ["rules.toggle", "automation"], ["push.manage", "automation"],
  ["devices.manage", "manage"], ["network.link", "manage"], ["access.manage", "manage"], ["shares.create", "manage"], ["pin.manage", "manage"], ["api.keys", "manage"],
].map(([id, group]) => ({ id, group }));
const ALL = PERMS.map((x) => x.id);
const VIEWER = ["view", "storage.search"];
const OPERATOR = [...VIEWER, "view.positions", "act.buttons", "act.redstone", "storage.craft", "rules.toggle"];
export function presetPerms(role: string | undefined): string[] {
  if (role === "viewer") return VIEWER;
  if (role === "operator") return OPERATOR;
  if (role === "admin" || role === "owner") return ALL;
  return [];
}
export const isPerm = (x: unknown): x is string => typeof x === "string" && ALL.includes(x);

export interface Grant {
  role: Role;
  allow: string[];
  deny: string[];
}
export interface Access {
  owner: string;
  direct: Map<string, Grant>;
  team: { provider: Team["provider"]; id: string; mapping: Record<string, Role | "none">; overrides: Record<string, { allow: string[]; deny: string[] }> } | null;
}

export function newAccess(ownerUuid: string): Access {
  return { owner: ownerUuid, direct: new Map(), team: null };
}
export const grant = (role: Role, allow: string[] = [], deny: string[] = []): Grant => ({ role, allow, deny });

/** preset ∪ allow − deny (deny wins within a source) */
const apply = (role: string | undefined, allow: string[] = [], deny: string[] = []) => {
  const set = new Set([...presetPerms(role), ...allow]);
  for (const d of deny) set.delete(d);
  return set;
};

function teamRank(a: Access, uuid: string) {
  if (!a.team) return undefined;
  const team = TEAMS.find((t) => t.provider === a.team!.provider && t.id === a.team!.id);
  return team?.members[uuid];
}

/** Highest role from every source (owner, direct grant, team rank). */
export function resolveRole(a: Access, uuid: string): Role | undefined {
  let role: Role | undefined = a.owner === uuid ? "owner" : undefined;
  role = higher(role, a.direct.get(uuid)?.role);
  const rank = teamRank(a, uuid);
  const mapped = rank ? a.team!.mapping[rank] : undefined;
  if (mapped && mapped !== "none") role = higher(role, mapped);
  return role;
}

/** Effective permissions: the union over all sources. Without `view` there is no access. */
export function resolvePerms(a: Access, uuid: string): string[] {
  const out = new Set<string>();
  if (a.owner === uuid) presetPerms("owner").forEach((x) => out.add(x));
  const g = a.direct.get(uuid);
  if (g) apply(g.role, g.allow, g.deny).forEach((x) => out.add(x));
  const rank = teamRank(a, uuid);
  const mapped = rank ? a.team!.mapping[rank] : undefined;
  if (rank && mapped && mapped !== "none") apply(mapped, a.team!.overrides[rank]?.allow, a.team!.overrides[rank]?.deny).forEach((x) => out.add(x));
  return out.has("view") ? ALL.filter((x) => out.has(x)) : [];
}

type Row = { uuid: string; player: string; role: Role; preset: Role; source: string; teamRank?: string; allow: string[]; deny: string[]; perms: string[] };
export function accessDoc(a: Access, you: { player: string; role: Role; perms: string[] }) {
  const players = new Map<string, Row>();
  const ownerP = playerByUuid(a.owner);
  if (ownerP) players.set(a.owner, { uuid: a.owner, player: ownerP.player, role: "owner", preset: "owner", source: "owner", allow: [], deny: [], perms: ALL });
  for (const [uuid, g] of a.direct) {
    const p = playerByUuid(uuid);
    if (p && !players.has(uuid))
      players.set(uuid, { uuid, player: p.player, role: g.role, preset: g.role, source: "direct", allow: g.allow, deny: g.deny, perms: ALL.filter((x) => apply(g.role, g.allow, g.deny).has(x)) });
  }
  if (a.team) {
    const team = TEAMS.find((t) => t.provider === a.team!.provider && t.id === a.team!.id);
    for (const [uuid, rank] of Object.entries(team?.members ?? {})) {
      const mapped = a.team.mapping[rank];
      const p = playerByUuid(uuid);
      if (!p || !mapped || mapped === "none") continue;
      const cur = players.get(uuid);
      const o = a.team.overrides[rank] ?? { allow: [], deny: [] };
      if (!cur || RANK[mapped] > RANK[cur.role])
        players.set(uuid, { uuid, player: p.player, role: mapped, preset: mapped, source: "team", teamRank: rank, allow: o.allow, deny: o.deny, perms: ALL.filter((x) => apply(mapped, o.allow, o.deny).has(x)) });
    }
  }
  const team = a.team ? TEAMS.find((t) => t.provider === a.team!.provider && t.id === a.team!.id) : undefined;
  return {
    you,
    catalog: PERMS.map((x) => ({ ...x, presets: (["viewer", "operator", "admin", "owner"] as const).filter((r) => presetPerms(r).includes(x.id)) })),
    players: [...players.values()].sort((x, y) => RANK[y.role] - RANK[x.role] || x.player.localeCompare(y.player)),
    team: a.team && team ? { provider: a.team.provider, id: team.id, name: team.name, mapping: a.team.mapping, overrides: a.team.overrides } : null,
    teams: TEAMS.map((t) => ({ provider: t.provider, id: t.id, name: t.name, ranks: RANKS[t.provider] })),
    providers: ["ftbteams", "opac", "scoreboard"],
  };
}

// --------------------------------------------------------------- shares
export interface Share {
  id: string;
  mainframeId: string;
  secretHash: string;
  label: string;
  createdBy: string;
  created: number;
  expires: number | null;
  maxViewers: number;
  pages: string[];
  cards: string[];
  hideNumbers: boolean;
}

export const sha = (s: string) => createHash("sha256").update(s).digest("hex");

export function createShare(input: Omit<Share, "id" | "secretHash" | "created" | "expires"> & { expiresIn: number | null }) {
  const id = randomUUID().replace(/-/g, "").slice(0, 16);
  const secret = randomBytes(16).toString("base64url"); // 128 bit
  const share: Share = {
    id,
    mainframeId: input.mainframeId,
    secretHash: sha(secret),
    label: input.label,
    createdBy: input.createdBy,
    created: Date.now(),
    expires: input.expiresIn ? Date.now() + input.expiresIn * 1000 : null,
    maxViewers: input.maxViewers,
    pages: input.pages,
    cards: input.cards,
    hideNumbers: input.hideNumbers,
  };
  return { share, secret };
}

/** Device type from the User-Agent (informational only). */
export function deviceType(ua: string): "desktop" | "phone" | "tablet" | "other" {
  if (/iPad|Tablet|Android(?!.*Mobile)/i.test(ua)) return "tablet";
  if (/iPhone|Android.*Mobile|Mobile/i.test(ua)) return "phone";
  if (/Windows|Macintosh|Linux|X11|CrOS/i.test(ua)) return "desktop";
  return "other";
}

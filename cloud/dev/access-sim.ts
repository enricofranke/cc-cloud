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

export interface Access {
  owner: string;
  direct: Map<string, Role>;
  team: { provider: Team["provider"]; id: string; mapping: Record<string, Role | "none"> } | null;
}

export function newAccess(ownerUuid: string): Access {
  return { owner: ownerUuid, direct: new Map(), team: null };
}

/** Highest role from every source (owner, direct grant, team rank). */
export function resolveRole(a: Access, uuid: string): Role | undefined {
  let role: Role | undefined = a.owner === uuid ? "owner" : undefined;
  role = higher(role, a.direct.get(uuid));
  if (a.team) {
    const team = TEAMS.find((t) => t.provider === a.team!.provider && t.id === a.team!.id);
    const rank = team?.members[uuid];
    const mapped = rank ? a.team.mapping[rank] : undefined;
    if (mapped && mapped !== "none") role = higher(role, mapped);
  }
  return role;
}

export function accessDoc(a: Access, you: { player: string; role: Role }) {
  const players = new Map<string, { uuid: string; player: string; role: Role; source: string; teamRank?: string }>();
  const ownerP = playerByUuid(a.owner);
  if (ownerP) players.set(a.owner, { uuid: a.owner, player: ownerP.player, role: "owner", source: "owner" });
  for (const [uuid, role] of a.direct) {
    const p = playerByUuid(uuid);
    if (p && !players.has(uuid)) players.set(uuid, { uuid, player: p.player, role, source: "direct" });
  }
  if (a.team) {
    const team = TEAMS.find((t) => t.provider === a.team!.provider && t.id === a.team!.id);
    for (const [uuid, rank] of Object.entries(team?.members ?? {})) {
      const mapped = a.team.mapping[rank];
      const p = playerByUuid(uuid);
      if (!p || !mapped || mapped === "none") continue;
      const cur = players.get(uuid);
      if (!cur || RANK[mapped] > RANK[cur.role]) players.set(uuid, { uuid, player: p.player, role: mapped, source: "team", teamRank: rank });
    }
  }
  const team = a.team ? TEAMS.find((t) => t.provider === a.team!.provider && t.id === a.team!.id) : undefined;
  return {
    you,
    players: [...players.values()].sort((x, y) => RANK[y.role] - RANK[x.role] || x.player.localeCompare(y.player)),
    team: a.team && team ? { provider: a.team.provider, id: team.id, name: team.name, mapping: a.team.mapping } : null,
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

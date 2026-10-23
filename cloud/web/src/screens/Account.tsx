// v0.6: Devices (all browsers of the player), Access (roles per player and team), Share links.
import { useEffect, useMemo, useState } from "preact/hooks";
import { Fragment, type ComponentChildren } from "preact";
import {
  ApiError,
  createShare,
  deleteShare,
  getAccess,
  listDevices,
  listShares,
  postAccess,
  revokeDevice,
  type AccessDoc,
  type DeviceEntry,
  type RoleName,
  type ShareEntry,
} from "../lib/api.ts";
import { account, saved } from "../lib/store.ts";
import { getConn, refreshAccount } from "../lib/conns.ts";
import { navigate, pushToast, timeAgo } from "../lib/ui.ts";
import { resolveLayout } from "../lib/layout.ts";
import { IconBack, IconPlus } from "../components/Icons.tsx";
import { Sheet } from "../components/Overlays.tsx";
import { CodeBlock } from "../components/CodeBlock.tsx";
import { DocsLink } from "../components/DocsLink.tsx";

export const ROLE_INFO: Record<RoleName, string> = {
  viewer: "Watches values and alerts. No controls, no coordinates.",
  operator: "Viewer + buttons, toggles, sliders, crafting, enabling rules.",
  admin: "Operator + devices, roles, layout, share links, PIN, rules, terminal.",
  owner: "Placed the mainframe. Admin + transfer and delete.",
};
const ROLE_ORDER: RoleName[] = ["viewer", "operator", "admin", "owner"];

function PageHead({ title, back, children }: { title: string; back: () => void; children?: ComponentChildren }) {
  return (
    <div class="page-head acc-head">
      <div>
        <button type="button" class="link-back" onClick={back}>
          <IconBack size={18} /> Back
        </button>
        <h1 class="px-heading">{title}</h1>
      </div>
      {children}
    </div>
  );
}

function errorText(e: unknown) {
  const m = e instanceof Error ? e.message : String(e);
  if (m === "not_allowed") return "Only admins of this mainframe can do this.";
  if (m === "no_access") return "Your player has no role on this mainframe.";
  return e instanceof ApiError ? e.message : "Something went wrong.";
}

/** Token to use for a mainframe's admin APIs: the entry's own (account or device) token. */
function tokenFor(mainframeId: string) {
  return saved.value.find((m) => m.mainframeId === mainframeId)?.token;
}

// ------------------------------------------------------------------ devices
const TYPE_LABEL: Record<DeviceEntry["type"], string> = { desktop: "Computer", phone: "Phone", tablet: "Tablet", other: "Browser" };

export function DevicesScreen() {
  const acc = account.value;
  const token = acc?.token ?? saved.value[0]?.token;
  const [devices, setDevices] = useState<DeviceEntry[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const load = () =>
    token &&
    listDevices(token)
      .then((r) => setDevices(r.devices))
      .catch((e) => setError(errorText(e)));
  useEffect(() => {
    load();
  }, [token]);
  const revoke = async (d: DeviceEntry) => {
    if (!token) return;
    if (!confirm(d.current ? "Unlink THIS browser? You will have to link it again." : `Revoke “${d.name}”? It loses access immediately.`)) return;
    try {
      await revokeDevice(token, d.id);
      pushToast({ level: "info", title: "Devices", text: `${d.name} was revoked.` });
      if (d.current) location.reload();
      else load();
    } catch (e) {
      setError(errorText(e));
    }
  };
  return (
    <main class="page page-narrow-wide" id="main" tabIndex={-1}>
      <PageHead title="Devices" back={() => navigate("/")} />
      <p class="muted">
        {acc ? (
          <>
            Every browser linked to <b>{acc.player}</b> – computers, tablets and phones. Revoke one here or in-game with <code>/cccloud devices</code>.
          </>
        ) : (
          <>
            This browser is paired with single mainframes. <a href="#/pair">Link your player account</a> (<code>/cccloud link</code>) to see and manage all your browsers here.
          </>
        )}
      </p>
      {error && <p class="form-error">{error}</p>}
      {!devices && !error && <div class="card skeleton" style={{ height: "160px" }} />}
      {devices && (
        <ul class="dev-list">
          {devices.map((d) => (
            <li key={d.id} class={`dev-row ${d.current ? "current" : ""}`}>
              <span class={`dev-type t-${d.type}`} aria-hidden="true">
                {d.type === "desktop" ? "▭" : d.type === "tablet" ? "▯" : d.type === "phone" ? "▮" : "◻"}
              </span>
              <div class="dev-main">
                <div class="dev-name">
                  {d.name} {d.current && <span class="tag tag-auto">this browser</span>}
                </div>
                <div class="muted small">
                  {TYPE_LABEL[d.type]} · {d.mainframes ? `${d.mainframes.length} mainframe` : "player account"}
                  {d.lastIp ? ` · ${d.lastIp}` : ""} · last seen {d.lastSeen ? timeAgo(d.lastSeen) : "–"}
                </div>
              </div>
              <button type="button" class="btn btn-small btn-danger-ghost" onClick={() => revoke(d)}>
                {d.current ? "Unlink" : "Revoke"}
              </button>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}

// ------------------------------------------------------------------- access
export function AccessScreen({ id }: { id: string }) {
  const token = tokenFor(id);
  const conn = getConn(id);
  const [doc, setDoc] = useState<AccessDoc | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [newPlayer, setNewPlayer] = useState("");
  const [newRole, setNewRole] = useState<RoleName>("operator");
  useEffect(() => {
    if (token) getAccess(token, id).then(setDoc).catch((e) => setError(errorText(e)));
  }, [token, id]);
  const op = async (body: Record<string, unknown>) => {
    if (!token) return;
    setBusy(true);
    setError(null);
    try {
      setDoc(await postAccess(token, id, body));
      refreshAccount();
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  };
  const name = conn?.info.value?.name ?? saved.value.find((m) => m.mainframeId === id)?.name ?? "Mainframe";
  return (
    <main class="page page-narrow-wide" id="main" tabIndex={-1}>
      <PageHead title="Access" back={() => navigate(`/m/${encodeURIComponent(id)}`)} />
      <p class="muted">
        Who may do what on <b>{name}</b>. The highest role from all sources counts. <DocsLink to="/docs/accounts#roles">About roles</DocsLink>
      </p>
      {error && (
        <p class="form-error" role="alert">
          {error}
        </p>
      )}
      {doc && (
        <>
          <section class="acc-sec" aria-labelledby="acc-players">
            <h2 class="sec-title" id="acc-players">
              Players
            </h2>
            <div class="table-wrap">
              <table class="acc-table">
                <thead>
                  <tr>
                    <th>Player</th>
                    <th>Role</th>
                    <th>Source</th>
                    <th>
                      <span class="sr-only">Actions</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {doc.players.map((p) => (
                    <tr key={p.uuid}>
                      <td>
                        <b>{p.player}</b> {p.player === doc.you.player && <span class="tag tag-auto">you</span>}
                      </td>
                      <td>
                        {p.source === "direct" ? (
                          <select
                            class="select select-sm"
                            value={p.role}
                            disabled={busy}
                            aria-label={`Role of ${p.player}`}
                            onChange={(e) => op({ op: "set_role", player: p.player, role: (e.target as HTMLSelectElement).value })}
                          >
                            {(["viewer", "operator", "admin"] as const).map((r) => (
                              <option key={r} value={r}>
                                {r}
                              </option>
                            ))}
                          </select>
                        ) : (
                          <span class={`role-badge r-${p.role}`}>{p.role}</span>
                        )}
                      </td>
                      <td class="muted small">
                        {p.source === "owner" ? "placed the mainframe" : p.source === "team" ? `team ${doc.team?.name ?? ""} (${p.teamRank})` : p.source === "server_admin" ? "server permission" : "assigned here"}
                      </td>
                      <td>
                        {p.source === "direct" && (
                          <button type="button" class="btn btn-small btn-danger-ghost" disabled={busy} onClick={() => op({ op: "remove", player: p.player })}>
                            Remove
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <form
              class="acc-add"
              onSubmit={(e) => {
                e.preventDefault();
                if (newPlayer.trim()) op({ op: "set_role", player: newPlayer.trim(), role: newRole }).then(() => setNewPlayer(""));
              }}
            >
              <input class="input" placeholder="Player name" value={newPlayer} maxLength={16} aria-label="Player name" onInput={(e) => setNewPlayer((e.target as HTMLInputElement).value)} />
              <select class="select" value={newRole} aria-label="Role" onChange={(e) => setNewRole((e.target as HTMLSelectElement).value as RoleName)}>
                {(["viewer", "operator", "admin"] as const).map((r) => (
                  <option key={r} value={r}>
                    {r}
                  </option>
                ))}
              </select>
              <button type="submit" class="btn" disabled={busy || !newPlayer.trim()}>
                <IconPlus size={16} /> Add
              </button>
            </form>
            <p class="muted small">{ROLE_INFO[newRole]}</p>
          </section>

          <section class="acc-sec" aria-labelledby="acc-team">
            <h2 class="sec-title" id="acc-team">
              Team
            </h2>
            <label class="field">
              <span class="field-label">Assigned team</span>
              <select
                class="select"
                disabled={busy}
                value={doc.team ? `${doc.team.provider}:${doc.team.id}` : ""}
                onChange={(e) => {
                  const v = (e.target as HTMLSelectElement).value;
                  if (!v) op({ op: "set_team", teamId: null });
                  else {
                    const [provider, teamId] = v.split(":");
                    op({ op: "set_team", provider, teamId });
                  }
                }}
              >
                <option value="">No team</option>
                {doc.teams.map((t) => (
                  <option key={`${t.provider}:${t.id}`} value={`${t.provider}:${t.id}`}>
                    {t.name} ({PROVIDER[t.provider] ?? t.provider})
                  </option>
                ))}
              </select>
            </label>
            {doc.team && (
              <div class="table-wrap">
                <table class="acc-table">
                  <thead>
                    <tr>
                      <th>Team rank</th>
                      <th>Role here</th>
                    </tr>
                  </thead>
                  <tbody>
                    {Object.entries(doc.team.mapping).map(([rank, role]) => (
                      <tr key={rank}>
                        <td>{rank}</td>
                        <td>
                          <select class="select select-sm" disabled={busy} value={role} aria-label={`Role for team rank ${rank}`} onChange={(e) => op({ op: "set_mapping", rank, role: (e.target as HTMLSelectElement).value })}>
                            {(["none", "viewer", "operator", "admin"] as const).map((r) => (
                              <option key={r} value={r}>
                                {r}
                              </option>
                            ))}
                          </select>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            <p class="muted small">Membership changes in {doc.providers.map((p) => PROVIDER[p] ?? p).join(", ")} apply live.</p>
          </section>

          <section class="acc-sec">
            <h2 class="sec-title">Roles</h2>
            <dl class="facts">
              {ROLE_ORDER.map((r) => (
                <Fragment key={r}>
                  <dt>
                    <span class={`role-badge r-${r}`}>{r}</span>
                  </dt>
                  <dd>{ROLE_INFO[r]}</dd>
                </Fragment>
              ))}
            </dl>
          </section>
        </>
      )}
    </main>
  );
}
const PROVIDER: Record<string, string> = { ftbteams: "FTB Teams", opac: "Open Parties and Claims", scoreboard: "scoreboard team" };

// ------------------------------------------------------------------- shares
const EXPIRY: { label: string; value: number | null }[] = [
  { label: "1 hour", value: 3600 },
  { label: "24 hours", value: 86400 },
  { label: "7 days", value: 604800 },
  { label: "Never", value: null },
];

export function SharesScreen({ id }: { id: string }) {
  const token = tokenFor(id);
  const conn = getConn(id);
  const [shares, setShares] = useState<ShareEntry[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const load = () =>
    token &&
    listShares(token, id)
      .then((r) => setShares(r.shares))
      .catch((e) => setError(errorText(e)));
  useEffect(() => {
    load();
  }, [token, id]);
  const revoke = async (s: ShareEntry) => {
    if (!token || !confirm(`Revoke “${s.label}”? Everyone viewing it is disconnected immediately.`)) return;
    try {
      await deleteShare(token, id, s.id);
      load();
    } catch (e) {
      setError(errorText(e));
    }
  };
  const name = conn?.info.value?.name ?? "Mainframe";
  return (
    <main class="page page-narrow-wide" id="main" tabIndex={-1}>
      <PageHead title="Share links" back={() => navigate(`/m/${encodeURIComponent(id)}`)}>
        <button type="button" class="pxbtn pxbtn-gold pxbtn-small" onClick={() => setCreating(true)} disabled={!conn}>
          <span>New link</span>
        </button>
      </PageHead>
      <p class="muted">
        Read-only links to selected pages or cards of <b>{name}</b> – for a stream overlay, a friend or a wall display. Nobody with a link can press anything.{" "}
        <DocsLink to="/docs/accounts#shares">About share links</DocsLink>
      </p>
      {error && <p class="form-error">{error}</p>}
      {shares && shares.length === 0 && <div class="empty">No share links yet.</div>}
      {shares && shares.length > 0 && (
        <ul class="dev-list">
          {shares.map((s) => (
            <li key={s.id} class="dev-row">
              <span class="dev-type" aria-hidden="true">
                ⇗
              </span>
              <div class="dev-main">
                <div class="dev-name">
                  {s.label} {s.hideNumbers && <span class="tag tag-gray">no numbers</span>}
                </div>
                <div class="muted small">
                  {s.pages.length ? `${s.pages.length} page${s.pages.length > 1 ? "s" : ""}` : ""}
                  {s.pages.length && s.cards.length ? " + " : ""}
                  {s.cards.length ? `${s.cards.length} card${s.cards.length > 1 ? "s" : ""}` : ""} · by {s.createdBy} · {s.expires ? (s.expires < Date.now() ? "expired" : `expires ${new Date(s.expires).toLocaleString()}`) : "never expires"} · {s.viewers}/
                  {s.maxViewers} viewing
                </div>
              </div>
              <button type="button" class="btn btn-small btn-danger-ghost" onClick={() => revoke(s)}>
                Revoke
              </button>
            </li>
          ))}
        </ul>
      )}
      {creating && conn && token && (
        <NewShare
          token={token}
          id={id}
          pages={resolveLayout(conn.uplinks.value, conn.layout.value)}
          onClose={() => {
            setCreating(false);
            load();
          }}
        />
      )}
    </main>
  );
}

function NewShare(props: { token: string; id: string; pages: ReturnType<typeof resolveLayout>; onClose: () => void }) {
  const [label, setLabel] = useState("Base status");
  const [pages, setPages] = useState<string[]>([]);
  const [cards, setCards] = useState<string[]>([]);
  const [expiresIn, setExpiresIn] = useState<number | null>(86400);
  const [maxViewers, setMaxViewers] = useState(5);
  const [hideNumbers, setHideNumbers] = useState(false);
  const [result, setResult] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const toggle = (list: string[], set: (v: string[]) => void, v: string) => set(list.includes(v) ? list.filter((x) => x !== v) : [...list, v]);
  const valid = (pages.length || cards.length) && maxViewers >= 1 && maxViewers <= 50;
  const create = async () => {
    setError(null);
    try {
      const r = await createShare(props.token, props.id, { label: label.trim() || "Shared view", pages, cards, expiresIn, maxViewers, hideNumbers });
      setResult(`${location.origin}${r.url}`);
    } catch (e) {
      setError(errorText(e));
    }
  };
  const total = useMemo(() => props.pages.reduce((n, p) => n + p.cards.length, 0), [props.pages]);
  return (
    <Sheet title="New share link" onClose={props.onClose}>
      {result ? (
        <>
          <p>
            <b>Copy this link now</b> – it is shown only once. The secret part after # never reaches the server's logs.
          </p>
          <CodeBlock code={result} label="Share link" />
          <button type="button" class="pxbtn pxbtn-gold pxbtn-wide" onClick={props.onClose}>
            <span>Done</span>
          </button>
        </>
      ) : (
        <>
          <label class="field">
            <span class="field-label">Name</span>
            <input class="input" value={label} maxLength={48} onInput={(e) => setLabel((e.target as HTMLInputElement).value)} />
          </label>
          <fieldset class="field share-scope">
            <legend class="field-label">What to share</legend>
            {props.pages.map((p) => (
              <div key={p.id} class="scope-page">
                <label class="check-row">
                  <input type="checkbox" checked={pages.includes(p.id)} onChange={() => toggle(pages, setPages, p.id)} />
                  <b>Whole page “{p.title}”</b>
                </label>
                {!pages.includes(p.id) &&
                  p.cards.map((c) => (
                    <label key={c.key} class="check-row scope-card">
                      <input type="checkbox" checked={cards.includes(c.key)} onChange={() => toggle(cards, setCards, c.key)} />
                      {c.title}
                    </label>
                  ))}
              </div>
            ))}
            {total === 0 && <p class="muted small">Nothing to share yet.</p>}
          </fieldset>
          <fieldset class="field">
            <legend class="field-label">Expires</legend>
            <div class="seg-ctl seg-wide" role="radiogroup" aria-label="Expires">
              {EXPIRY.map((x) => (
                <button key={x.label} type="button" role="radio" aria-checked={expiresIn === x.value} class={expiresIn === x.value ? "on" : ""} onClick={() => setExpiresIn(x.value)}>
                  {x.label}
                </button>
              ))}
            </div>
          </fieldset>
          <label class="field">
            <span class="field-label">Max. viewers at the same time</span>
            <input class="input" type="number" min={1} max={50} value={maxViewers} onInput={(e) => setMaxViewers(Number((e.target as HTMLInputElement).value))} />
          </label>
          <label class="check-row">
            <input type="checkbox" checked={hideNumbers} onChange={(e) => setHideNumbers((e.target as HTMLInputElement).checked)} />
            Hide exact numbers (show only gauges)
          </label>
          <p class="muted small">Never included: coordinates, item search, the network map, history export. Viewers cannot press anything.</p>
          {error && <p class="form-error">{error}</p>}
          <button type="button" class="pxbtn pxbtn-gold pxbtn-wide sheet-cta" disabled={!valid} onClick={create}>
            <span>Create link</span>
          </button>
        </>
      )}
    </Sheet>
  );
}

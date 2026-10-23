// Script Store (lazy chunk): browse the store index, read the full source
// (SHA-256 verified) and the "uses" list, pick a computer, install via the
// Script import API. Admins with scripts.upload; PIN asked when set.
import { useEffect, useMemo, useRef, useState } from "preact/hooks";
import type { MainframeConn } from "../lib/conn.ts";
import { ScriptError, scriptApi, scriptErrorText, type StoreRecord } from "../lib/scripts.ts";
import { fetchIndex, fetchVerified, readInstalled, recordInstall, compareVersions, updatesFor, type SignatureState, type StoreIndex, type StoreScript } from "../lib/scriptstore.ts";
import { publicConfig } from "../lib/config.ts";
import { pushToast } from "../lib/ui.ts";
import { ConfirmDialog, PinDialog, Sheet, type PinState } from "../components/Overlays.tsx";
import { DocsLink } from "../components/DocsLink.tsx";

const USES: Record<string, string> = {
  redstone: "Sets redstone outputs of the computer – can switch whatever is wired to it (doors, reactors, machines).",
  peripherals: "Talks to peripherals next to the computer or on its wired network (e.g. a reactor, a bridge, a monitor).",
  http: "Connects to the internet (CC's HTTP API, if the server allows it).",
  fs: "Reads or writes files on the computer.",
  shell: "Starts other programs on the computer.",
};
const ERR: Record<string, string> = {
  unreachable: "The Script Store cannot be reached from this browser.",
  bad_index: "The store index is not valid.",
  hash_mismatch: "A file does not match the store index – nothing was installed.",
  too_large: "A file is too large.",
};
const SIG: Record<SignatureState, { text: string; cls: string; title: string }> = {
  signed: { text: "signed ✓", cls: "ok", title: "This browser checked the store's Ed25519 signature over the index." },
  bad: { text: "signature invalid", cls: "bad", title: "The index does not match the store's signature – installing is blocked." },
  unsigned: { text: "not signed", cls: "warn", title: "The store publishes no signature; the server refuses store installs it cannot verify." },
  unsupported: { text: "signature checked by the server", cls: "muted", title: "This browser cannot check Ed25519 signatures; the server verifies every install." },
  no_key: { text: "signature checked by the server", cls: "muted", title: "The server checks the store's signature before every install." },
};
export function SignatureBadge({ s }: { s: SignatureState }) {
  return (
    <span class={`sig-badge sig-${SIG[s].cls}`} title={SIG[s].title}>
      {SIG[s].text}
    </span>
  );
}

const errText = (e: unknown) => {
  const c = e instanceof Error ? e.message : String(e);
  return ERR[c] ?? (e instanceof ScriptError ? scriptErrorText(e) : `Error: ${c}`);
};

export default function StoreSheet({ conn, want, onClose }: { conn: MainframeConn; want?: { id: string; version?: string } | null; onClose: () => void }) {
  const url = conn.store.value?.url ?? "";
  const mf = conn.info.value?.id ?? "";
  const lang = (document.documentElement.lang || navigator.language || "en").startsWith("de") ? "de" : "en";
  const key = conn.store.value?.key ?? publicConfig.value.store.key ?? null;
  const [index, setIndex] = useState<StoreIndex | null>(null);
  const [signature, setSignature] = useState<SignatureState>("no_key");
  const [error, setError] = useState<string | null>(null);
  const [q, setQ] = useState("");
  const [tag, setTag] = useState<string | null>(null);
  const [open, setOpen] = useState<StoreScript | null>(null);
  useEffect(() => {
    if (!url) return setError("The Script Store is switched off on this server.");
    fetchIndex(url, key)
      .then((i) => {
        setIndex(i);
        setSignature(i.signature);
        if (want) {
          const sc = i.scripts.find((s) => s.id === want.id);
          if (sc) setOpen(sc);
          else setError(`“${want.id}” is not in the store.`);
        }
      })
      .catch((e) => setError(errText(e)));
  }, [url]);
  const installed = useMemo(() => readInstalled(mf), [mf, open]);
  const updates = index ? updatesFor(installed, index) : [];
  const list = (index?.scripts ?? []).filter(
    (s) => (!tag || s.tags.includes(tag)) && (!q || `${s.id} ${s.name.en} ${s.name.de} ${s.description.en} ${s.description.de} ${s.tags.join(" ")}`.toLowerCase().includes(q.toLowerCase())),
  );
  if (open) return <ScriptDetail conn={conn} url={url} sc={open} lang={lang} signature={signature} wantVersion={want?.id === open.id ? want.version : undefined} onBack={() => setOpen(null)} onClose={onClose} />;
  return (
    <Sheet title="Script Store" onClose={onClose} wide>
      <div class="store">
        <p class="muted small">
          Community programs for your computers, reviewed before they are listed. They run on a CC computer and can control what that computer controls – never the Minecraft server itself.{" "}
          <DocsLink to="/docs/lua#store">More</DocsLink>
        </p>
        {index && (
          <p class="small store-sig">
            Index: <SignatureBadge s={signature} />
          </p>
        )}
        {signature === "bad" && (
          <div class="banner banner-warn" role="alert">
            <div class="banner-text">The store index does not match its signature. Something between you and the store changed it – installing is blocked.</div>
          </div>
        )}
        {updates.length > 0 && (
          <div class="banner banner-readonly" role="status">
            <div class="banner-text">
              Updates available:{" "}
              {updates.map((u, i) => (
                <span key={u.uplinkId + u.id}>
                  {i > 0 && ", "}
                  <button type="button" class="link-btn" onClick={() => setOpen(u.to)}>
                    {u.to.name[lang]} {u.from} → {u.to.version}
                  </button>
                </span>
              ))}
            </div>
          </div>
        )}
        <div class="sb-tools">
          <input class="input sb-search" type="search" placeholder="Search scripts…" value={q} onInput={(e) => setQ((e.target as HTMLInputElement).value)} aria-label="Search scripts" />
        </div>
        {index && (
          <div class="tag-row" role="radiogroup" aria-label="Tags">
            <button type="button" role="radio" aria-checked={!tag} class={`chip ${!tag ? "on" : ""}`} onClick={() => setTag(null)}>
              All
            </button>
            {index.tags.map((t) => (
              <button key={t} type="button" role="radio" aria-checked={tag === t} class={`chip ${tag === t ? "on" : ""}`} onClick={() => setTag(t === tag ? null : t)}>
                {t}
              </button>
            ))}
          </div>
        )}
        {error && <p class="form-error">{error}</p>}
        {!index && !error && <p class="muted small">Loading the store…</p>}
        <ul class="ex-grid">
          {list.map((s) => {
            const has = Object.values(installed).some((files) => Object.values(files).some((r) => r.id === s.id));
            const upd = updates.some((u) => u.id === s.id);
            return (
              <li key={s.id} class="ex-card store-card">
                <div class="ex-head">
                  <b>
                    {s.name[lang]} {s.reviewed && <span class="mini-badge run">reviewed</span>}
                  </b>
                  <span class="mono small muted">
                    {s.id} · v{s.version} · {s.lines} lines · {s.author.name}
                  </span>
                </div>
                <p class="ex-text">{s.description[lang]}</p>
                <div class="tag-row small">
                  {s.uses.map((u) => (
                    <span key={u} class="uses-tag" title={USES[u] ?? u}>
                      {u}
                    </span>
                  ))}
                </div>
                <div class="ex-foot">
                  <span class="muted small">{upd ? "Update available" : has ? "Installed" : s.tags.join(" · ")}</span>
                  <button type="button" class="btn" onClick={() => setOpen(s)}>
                    {upd ? "Update…" : "View & install"}
                  </button>
                </div>
              </li>
            );
          })}
        </ul>
        {index && list.length === 0 && <p class="store-note">No script matches.</p>}
      </div>
    </Sheet>
  );
}

function ScriptDetail(props: { conn: MainframeConn; url: string; sc: StoreScript; lang: "en" | "de"; signature: SignatureState; wantVersion?: string; onBack: () => void; onClose: () => void }) {
  const { conn, sc, lang } = props;
  const mf = conn.info.value?.id ?? "";
  const [files, setFiles] = useState<Record<string, string> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const computers = conn.uplinks.value.filter((u) => u.mode !== "direct" && u.online && u.loaded !== false);
  const [target, setTarget] = useState(computers[0]?.uplinkId ?? "");
  const [runNow, setRunNow] = useState(true);
  const [startup, setStartup] = useState(false);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<{ title: string; text: string; resolve: (ok: boolean) => void } | null>(null);
  const [pinAsk, setPinAsk] = useState<{ state: PinState; resolve: (p: string | null) => void } | null>(null);
  const pin = useRef<string | undefined>(undefined);
  // installed store scripts: the server's records on that computer (v0.9), the browser cache as fallback
  const [records, setRecords] = useState<Record<string, StoreRecord> | null>(null);
  useEffect(() => {
    setRecords(null);
    if (!target) return;
    let stop = false;
    scriptApi(conn.baseUrl.replace(/\/$/, ""), conn.token, mf, target)
      .list()
      .then((l) => !stop && setRecords(l.store ?? null))
      .catch(() => {});
    return () => {
      stop = true;
    };
  }, [target]);
  const installedHere: Record<string, { id: string; version: string; sha256: string; changed?: boolean }> = records ?? readInstalled(mf)[target] ?? {};
  useEffect(() => {
    let stop = false;
    Promise.all(sc.files.map(async (f) => [f.path, await fetchVerified(props.url, sc, f)] as const))
      .then((pairs) => !stop && setFiles(Object.fromEntries(pairs)))
      .catch((e) => !stop && setError(errText(e)));
    return () => {
      stop = true;
    };
  }, [sc.id, sc.version]);
  const ask = (title: string, text: string) => new Promise<boolean>((resolve) => setConfirm({ title, text, resolve }));
  async function withPin<T>(fn: (p?: string) => Promise<T>): Promise<T> {
    for (;;) {
      try {
        const r = await fn(pin.current);
        setPinAsk(null);
        return r;
      } catch (e) {
        if (!(e instanceof ScriptError) || !["pin_required", "wrong_pin", "pin_locked"].includes(e.code)) {
          setPinAsk(null);
          throw e;
        }
        if (e.code !== "pin_required") pin.current = undefined;
        const p = await new Promise<string | null>((resolve) => setPinAsk({ state: e.code === "pin_locked" ? { kind: "locked", text: scriptErrorText(e) } : e.code === "wrong_pin" ? { kind: "error", text: "Wrong PIN." } : { kind: "idle" }, resolve }));
        if (p === null) throw new ScriptError(0, "cancelled");
        pin.current = p;
      }
    }
  }
  const install = async () => {
    if (!files || !target || props.signature === "bad") return;
    setBusy(true);
    setError(null);
    const api = scriptApi(conn.baseUrl.replace(/\/$/, ""), conn.token, mf, target);
    try {
      const list = await api.list();
      const existing = new Set(list.files.map((f) => f.path));
      const clash = sc.files.filter((f) => existing.has(f.path));
      if (clash.length) {
        // changed on the computer since it was installed from the store? (the server knows; older servers: compare here)
        const changed: string[] = [];
        for (const f of clash) {
          const server = list.store?.[f.path];
          if (server) {
            if (server.changed) changed.push(f.path);
            continue;
          }
          const rec = installedHere[f.path];
          if (!rec) continue;
          const now = await api.read(f.path).catch(() => "");
          const { sha256Hex } = await import("../lib/scriptstore.ts");
          if ((await sha256Hex(new TextEncoder().encode(now))) !== rec.sha256) changed.push(f.path);
        }
        const ok = await ask(
          changed.length ? "Overwrite local changes?" : "Replace existing files?",
          changed.length
            ? `${changed.join(", ")} changed on the computer since it was installed from the store. Installing replaces it.`
            : `${clash.map((f) => f.path).join(", ")} already exist on the computer and will be replaced.`,
        );
        if (!ok) return;
      }
      // store=<id>@<version>: the server verifies the signed index and the file's SHA-256 before writing
      for (const f of sc.files) await withPin((p) => api.write(f.path, files[f.path], { overwrite: true, startup: startup && f.path === sc.entry, pin: p, store: `${sc.id}@${sc.version}`, storeFile: f.path }));
      recordInstall(mf, target, sc);
      let msg = `${sc.name[lang]} ${sc.version} installed on ${computers.find((c) => c.uplinkId === target)?.title || "the computer"}.`;
      if (runNow) {
        try {
          await withPin((p) => api.run(sc.entry, "auto", p));
          msg += " It runs in a new tab.";
        } catch (e) {
          if (e instanceof ScriptError && e.code === "needs_reboot" && (await ask("Restart the computer?", "This basic computer cannot start programs in the background. Restart it to run the script?"))) {
            await withPin((p) => api.run(sc.entry, "reboot", p));
            msg += " The computer restarts and runs it.";
          } else if (!(e instanceof ScriptError && e.code === "needs_reboot")) throw e;
        }
      }
      setDone(msg);
      pushToast({ level: "info", title: "Script installed", text: msg });
    } catch (e) {
      if (!(e instanceof ScriptError && e.code === "cancelled")) setError(errText(e));
    } finally {
      setBusy(false);
    }
  };
  const vNote = props.wantVersion && props.wantVersion !== sc.version ? `The link asked for version ${props.wantVersion}; the store has ${sc.version} now.` : null;
  const prev = Object.values(installedHere).find((r) => r.id === sc.id);
  return (
    <Sheet title={sc.name[lang]} onClose={props.onClose} wide>
      <div class="store">
        <button type="button" class="link-btn small" onClick={props.onBack}>
          ← All scripts
        </button>
        <p class="ex-text">{sc.description[lang]}</p>
        <p class="muted small">
          v{sc.version} · {sc.license} · by {sc.author.name}
          {sc.reviewed ? " · reviewed" : " · not reviewed"} · <SignatureBadge s={props.signature} />
          {prev ? ` · installed here: v${prev.version}${compareVersions(sc.version, prev.version) > 0 ? " (update available)" : ""}` : ""}
        </p>
        {vNote && <p class="form-error">{vNote}</p>}
        <div class="uses-box">
          <b>This script…</b>
          <ul>
            {sc.uses.map((u) => (
              <li key={u}>
                <b>{u}</b> – {USES[u] ?? "uses this capability"}
              </li>
            ))}
          </ul>
          <p class="muted small">It runs on the computer you pick and can control everything that computer controls. It cannot touch the Minecraft server.</p>
        </div>
        <div class="banner banner-warn store-safety" role="note">
          <div class="banner-text">
            <b>Read the code before you install it.</b> {props.signature === "signed" ? "This browser checked the store's signature, and the files match the signed index" : "The files match the store's index"}; the Minecraft server checks the signature again before it writes anything. That proves the files come from the store – not that the code is safe. “Reviewed” means a maintainer looked at this version.
          </div>
        </div>
        {error && <p class="form-error">{error}</p>}
        {!files && !error && <p class="muted small">Downloading and checking the files…</p>}
        {files &&
          sc.files.map((f) => (
            <details key={f.path} class="src" open={sc.files.length === 1}>
              <summary>
                <span class="mono">{f.path}</span> <span class="muted small">{f.size} bytes · matches the store index</span>
              </summary>
              <pre class="code src-code" tabIndex={0}>{files[f.path]}</pre>
            </details>
          ))}
        {done ? (
          <p class="form-ok">✓ {done}</p>
        ) : (
          <div class="store-install">
            <label class="field">
              <span class="field-label">Install on</span>
              <select class="select" value={target} onChange={(e) => setTarget((e.target as HTMLSelectElement).value)}>
                {computers.map((c) => (
                  <option key={c.uplinkId} value={c.uplinkId}>
                    {c.title || c.uplinkId}
                  </option>
                ))}
              </select>
            </label>
            <label class="check-row">
              <input type="checkbox" checked={runNow} onChange={(e) => setRunNow((e.target as HTMLInputElement).checked)} />
              Run it now
            </label>
            <label class="check-row">
              <input type="checkbox" checked={startup} onChange={(e) => setStartup((e.target as HTMLInputElement).checked)} />
              Start it automatically (startup.lua)
            </label>
            <button type="button" class="pxbtn pxbtn-gold pxbtn-wide" disabled={!files || !target || busy || !conn.can("scripts.upload") || props.signature === "bad"} onClick={install}>
              <span>{busy ? "Installing…" : prev ? `Update to ${sc.version}` : "Install"}</span>
            </button>
            {!computers.length && <p class="muted small">No computer with a Cloud Uplink is online on this mainframe.</p>}
          </div>
        )}
      </div>
      {confirm && (
        <ConfirmDialog
          title={confirm.title}
          text={confirm.text}
          confirmLabel="Continue"
          color="yellow"
          onCancel={() => (confirm.resolve(false), setConfirm(null))}
          onConfirm={() => (confirm.resolve(true), setConfirm(null))}
        />
      )}
      {pinAsk && (
        <PinDialog
          title="Mainframe PIN"
          text="Installing scripts needs the mainframe PIN."
          color="gold"
          state={pinAsk.state}
          onCancel={() => (pinAsk.resolve(null), setPinAsk(null))}
          onSubmit={(p) => (setPinAsk({ ...pinAsk, state: { kind: "checking" } }), pinAsk.resolve(p))}
        />
      )}
    </Sheet>
  );
}

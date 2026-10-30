// v0.10 public API keys (lazy route #/m/<id>/apikeys, needs api.keys): keys for external tools such as a
// Discord bot or Home Assistant. A key belongs to one player and this mainframe, acts with that player's
// live rights limited to its scopes, and is shown exactly once.
import { useEffect, useState } from "preact/hooks";
import { getConn } from "../lib/conns.ts";
import { saved } from "../lib/store.ts";
import { navigate, pushToast } from "../lib/ui.ts";
import { feature } from "../lib/config.ts";
import { lang, locale, t, type Key } from "../lib/i18n.ts";
import { agoText, inText } from "../lib/schedules.ts";
import { EXPIRY, MAX_IPS, MAX_NAME, SCOPES, SCOPE_PERM, createApiKey, getApiKeys, ipProblem, parseIps, revokeApiKey, type ApiKey, type ApiKeysDoc, type Expiry, type NewKey, type Scope } from "../lib/apikeys.ts";
import { IconBack, IconPlus } from "../components/Icons.tsx";
import { Sheet } from "../components/Overlays.tsx";
import { CodeBlock } from "../components/CodeBlock.tsx";
import { DocsLink } from "../components/DocsLink.tsx";

const ERR_KEYS: Record<string, Key> = {
  invalid: "ak.err.invalid",
  not_allowed: "ak.err.not_allowed",
  no_access: "ak.err.not_allowed",
  too_many: "ak.err.too_many",
  disabled: "ak.err.disabled",
  unreachable: "ak.err.unreachable",
  unsupported: "ak.err.unsupported",
  not_found: "ak.err.not_found",
};
const err = (e: unknown) => {
  const m = (e as Error).message;
  return ERR_KEYS[m] ? t(ERR_KEYS[m]) : m;
};
const expiryLabel = (e: Expiry) => t(`ak.exp.${e ?? "never"}` as Key);

export default function ApiKeysScreen({ id }: { id: string }) {
  const conn = getConn(id);
  const token = saved.value.find((m) => m.mainframeId === id)?.token ?? "";
  const [doc, setDoc] = useState<ApiKeysDoc | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [form, setForm] = useState(false);
  const [created, setCreated] = useState<{ key: string; name: string } | null>(null);
  const L = lang.value;
  const load = () =>
    getApiKeys(location.origin, token, id)
      .then((d) => (setDoc(d), setError(null)))
      .catch((e) => setError(err(e)));
  useEffect(() => {
    if (token) load();
  }, [id, token]);
  const revoke = async (k: ApiKey) => {
    if (!confirm(t("ak.revokeQ", { name: k.name }))) return;
    setBusy(k.id);
    try {
      await revokeApiKey(location.origin, token, id, k.id);
      pushToast({ level: "info", title: t("ak.title"), text: t("ak.revoked", { name: k.name }) });
      await load();
    } catch (e) {
      setError(err(e));
    } finally {
      setBusy(null);
    }
  };
  const name = conn?.info.value?.name ?? "Mainframe";
  const own = doc?.keys.filter((k) => k.own).length ?? 0;
  return (
    <main class="page page-narrow-wide keys-page" id="main" tabIndex={-1} lang={L}>
      <div class="page-head acc-head">
        <div>
          <button type="button" class="link-back" onClick={() => navigate(`/m/${encodeURIComponent(id)}`)}>
            <IconBack size={18} /> {t("c.back")}
          </button>
          <h1 class="px-heading">{t("ak.title")}</h1>
        </div>
        {doc && (
          <button type="button" class="pxbtn pxbtn-gold" disabled={!doc.enabled || own >= doc.limit} onClick={() => setForm(true)}>
            <span>
              <IconPlus size={14} /> {t("ak.new")}
            </span>
          </button>
        )}
      </div>
      <p class="muted">
        {t("ak.intro", { name })} <DocsLink to="/docs/api">{t("ak.docs")}</DocsLink>
      </p>
      {!feature("api") && !doc && !error && <p class="muted">{t("ak.err.unsupported")}</p>}
      {error && (
        <p class="form-error" role="alert">
          {error}
        </p>
      )}
      {doc && !doc.enabled && (
        <div class="banner banner-warn api-off" role="status">
          <div class="banner-text">
            <b>{t("ak.offTitle")}</b> {t("ak.offText")}
            <CodeBlock code={"[api]\napiEnabled = true"} label="serverconfig/cccloud-server.toml" />
          </div>
        </div>
      )}
      {!doc && !error && token && <div class="card skeleton" style={{ height: "140px" }} />}
      {doc && (
        <>
          <p class="muted small">{t("ak.count", { n: own, limit: doc.limit })}</p>
          {doc.keys.length === 0 ? (
            <div class="rules-empty">
              <b>{t("ak.empty")}</b>
              <span class="muted small">{t("ak.emptyHint")}</span>
            </div>
          ) : (
            <ul class="rule-list">
              {doc.keys.map((k) => {
                const expired = k.expired;
                return (
                  <li key={k.id} class={`rule-row key-row ${expired ? "off" : ""}`}>
                    <div class="rule-top">
                      <span class="key-glyph mono" aria-hidden="true">
                        cck_
                      </span>
                      <b class="rule-name">{k.name}</b>
                      {k.own ? <span class="tag tag-gray">{t("ak.yours")}</span> : <span class="tag tag-auto">{k.player}</span>}
                      {expired && <span class="tag tag-red">{t("ak.expired")}</span>}
                      <span class="rule-tools">
                        <button type="button" class="btn btn-small btn-danger-ghost" disabled={busy === k.id} onClick={() => revoke(k)}>
                          {t("ak.revoke")}
                        </button>
                      </span>
                    </div>
                    <p class="key-scopes">
                      {k.scopes.map((s) => (
                        <span key={s} class={`scope-chip ${s.startsWith("read") ? "read" : "write"}`}>
                          {s}
                        </span>
                      ))}
                    </p>
                    <p class="muted small rule-meta">
                      {t("ak.created", { date: new Date(k.created).toLocaleDateString(locale(L)) })}
                      {" · "}
                      {k.expires === null ? t("ak.noExpiry") : expired ? t("ak.expiredAt", { date: new Date(k.expires).toLocaleString(locale(L)) }) : t("ak.expires", { in: inText(k.expires, L) })}
                      {" · "}
                      {k.lastUsed
                        ? k.lastIp
                          ? t("ak.lastUsed", { ago: agoText(k.lastUsed, L), ip: k.lastIp })
                          : t("ak.lastUsedNoIp", { ago: agoText(k.lastUsed, L) })
                        : t("ak.neverUsed")}
                    </p>
                    {k.ips.length > 0 && <p class="muted small">{t("ak.onlyFrom", { list: k.ips.join(", ") })}</p>}
                  </li>
                );
              })}
            </ul>
          )}
          <p class="muted small">{t("ak.security")}</p>
        </>
      )}
      {form && doc && (
        <KeyForm
          doc={doc}
          can={(p) => !!conn?.can(p)}
          onClose={() => setForm(false)}
          onCreate={async (input) => {
            const r = await createApiKey(location.origin, token, id, input);
            setForm(false);
            setCreated({ key: r.key, name: input.name.trim() });
            load();
          }}
        />
      )}
      {created && <NewKeySheet created={created} mainframeId={id} onClose={() => setCreated(null)} />}
    </main>
  );
}

function KeyForm({ doc, can, onClose, onCreate }: { doc: ApiKeysDoc; can: (perm: string) => boolean; onClose: () => void; onCreate: (k: NewKey) => Promise<void> }) {
  const [name, setName] = useState("");
  const [scopes, setScopesState] = useState<Scope[]>(["read:sections"]);
  const setScopes = (list: Scope[]) => setScopesState(list.filter((x) => can(SCOPE_PERM[x])));
  const [expiry, setExpiry] = useState<Expiry>(2592000);
  const [ips, setIps] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const list = parseIps(ips);
  const ipErr = ipProblem(list);
  const problem = !name.trim() || name.length > MAX_NAME ? t("ak.v.name") : !scopes.length ? t("ak.v.scopes") : ipErr ? (ipErr.kind === "too_many" ? t("ak.v.tooManyIps", { n: MAX_IPS }) : t("ak.v.badIp", { entry: ipErr.entry })) : null;
  const save = async () => {
    if (problem) return setError(problem);
    setBusy(true);
    setError(null);
    try {
      await onCreate({ name, scopes: scopes.filter((x) => can(SCOPE_PERM[x])), expiresIn: expiry, ips: list });
    } catch (e) {
      setError(err(e));
    } finally {
      setBusy(false);
    }
  };
  const offered = SCOPES.filter((s) => doc.scopes.includes(s));
  return (
    <Sheet title={t("ak.newTitle")} onClose={onClose}>
      <form
        class="push-form key-form"
        lang={lang.value}
        onSubmit={(e) => {
          e.preventDefault();
          save();
        }}
      >
        <label class="field">
          <span class="field-label">{t("ak.name")}</span>
          <input class="input" maxLength={MAX_NAME} value={name} placeholder={t("ak.namePh")} autoFocus onInput={(e) => setName((e.target as HTMLInputElement).value)} />
        </label>
        <fieldset class="perm-group">
          <legend>{t("ak.scopes")}</legend>
          {offered.map((s) => {
            const lacks = !can(SCOPE_PERM[s]);
            return (
              <label key={s} class={`perm-row ${lacks ? "cannot" : ""}`}>
                <input type="checkbox" disabled={lacks} checked={scopes.includes(s) && !lacks} onChange={(e) => setScopes((e.target as HTMLInputElement).checked ? SCOPES.filter((x) => x === s || scopes.includes(x)) : scopes.filter((x) => x !== s))} />
                <span class="perm-text">
                  <span class="perm-name">
                    <code>{s}</code>
                  </span>
                  <span class="muted small">{t(`ak.scope.${s}` as Key)}</span>
                  {lacks && <span class="field-hint warn">{t("ak.scopeLacks")}</span>}
                </span>
              </label>
            );
          })}
        </fieldset>
        <label class="field">
          <span class="field-label">{t("ak.expiry")}</span>
          <select class="select" value={String(expiry)} onChange={(e) => setExpiry(((v) => (v === "null" ? null : (Number(v) as Expiry)))((e.target as HTMLSelectElement).value))}>
            {EXPIRY.map((x) => (
              <option key={String(x)} value={String(x)}>
                {expiryLabel(x)}
              </option>
            ))}
          </select>
        </label>
        <label class="field">
          <span class="field-label">{t("ak.ips")}</span>
          <textarea class="input mono key-ips" rows={3} spellcheck={false} value={ips} placeholder={"203.0.113.7\n192.168.1.0/24"} onInput={(e) => setIps((e.target as HTMLTextAreaElement).value)} />
          <span class={`field-hint ${ipErr ? "err" : ""}`}>{ipErr ? (ipErr.kind === "too_many" ? t("ak.v.tooManyIps", { n: MAX_IPS }) : t("ak.v.badIp", { entry: ipErr.entry })) : t("ak.ipsHint", { n: MAX_IPS })}</span>
        </label>
        <p class="field-hint warn">{t("ak.formWarn")}</p>
        {error && (
          <p class="form-error" role="alert">
            {error}
          </p>
        )}
        <div class="perm-actions">
          <span class="perm-spacer" />
          <button type="button" class="btn" onClick={onClose}>
            {t("c.cancel")}
          </button>
          <button type="submit" class="pxbtn pxbtn-gold" disabled={busy}>
            <span>{busy ? t("ak.creating") : t("ak.create")}</span>
          </button>
        </div>
      </form>
    </Sheet>
  );
}

/** The new key, exactly once: copy it now. */
function NewKeySheet({ created, mainframeId, onClose }: { created: { key: string; name: string }; mainframeId: string; onClose: () => void }) {
  const example = `curl -H "Authorization: Bearer ${created.key}" \\\n  ${location.origin}/api/v1/mainframes/${mainframeId}/sections`;
  return (
    <Sheet title={t("ak.createdTitle", { name: created.name })} onClose={onClose}>
      <div class="new-key" lang={lang.value}>
        <div class="banner banner-warn" role="alert">
          <div class="banner-text">
            <b>{t("ak.onceTitle")}</b> {t("ak.onceText")}
          </div>
        </div>
        <CodeBlock code={created.key} label={t("ak.keyLabel")} />
        <p class="muted small">{t("ak.tryIt")}</p>
        <CodeBlock code={example} label="curl" />
        <div class="perm-actions">
          <span class="perm-spacer" />
          <button type="button" class="pxbtn pxbtn-gold" onClick={onClose}>
            <span>{t("ak.savedIt")}</span>
          </button>
        </div>
      </div>
    </Sheet>
  );
}

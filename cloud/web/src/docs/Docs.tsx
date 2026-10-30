// /docs – bilingual documentation inside the dashboard bundle (lazy-loaded chunk).
import { useEffect, useMemo, useRef, useState } from "preact/hooks";
import { Fragment, type ComponentChildren } from "preact";
import { signal } from "@preact/signals";
import type { Block, Inline, Recipe, RecipeLive } from "./md.ts";
import { CONTENT, MODS, PAGES, UI, itemName, iconItem, type Lang } from "./content/index.ts";
import { pageBlocks, search } from "./search.ts";
import { applyLive, liveSummary, parseCatalog, type LiveCatalog, type LiveRecipe } from "./live.ts";
import { CodeBlock } from "../components/CodeBlock.tsx";
import { ItemIcon } from "../components/ItemIcon.tsx";
import { PixelIcon } from "../components/PixelIcon.tsx";
import { Logo, IconBack, IconClose, IconMoon, IconSun, IconAuto } from "../components/Icons.tsx";
import { copyText } from "../lib/clipboard.ts";
import { cycleTheme, themePref, pushToast } from "../lib/ui.ts";
import { Toasts } from "../components/Overlays.tsx";
import { lang, setLang } from "../lib/i18n.ts";

// ------------------------------------------------------------------ state
// the language preference is shared with the v0.10 screens (lib/i18n.ts)
export { lang };

/** /docs, /docs/<page>, plus #anchor */
function current() {
  const m = /^\/docs\/?([a-z0-9-]*)/.exec(location.pathname);
  const page = PAGES.some((p) => p.id === m?.[1]) ? m![1] : PAGES[0].id;
  return { page, anchor: decodeURIComponent(location.hash.replace(/^#/, "")) };
}
const loc = signal(current());
addEventListener("popstate", () => (loc.value = current()));

/**
 * The server's live recipes (GET /api/recipes): undefined while loading, null when there are none (dev mock, offline,
 * an older server) – then the docs show the mod's default recipes as generated.
 */
const live = signal<LiveCatalog | null | undefined>(undefined);
let liveRequested = false;
function loadLive() {
  if (liveRequested) return;
  liveRequested = true;
  fetch("/api/recipes", { headers: { accept: "application/json" } })
    .then((r) => (r.ok ? r.json() : null))
    .then((j) => (live.value = parseCatalog(j)))
    .catch(() => (live.value = null));
}

export function docsNavigate(href: string) {
  history.pushState(null, "", href);
  loc.value = current();
  if (!loc.value.anchor) window.scrollTo(0, 0);
}

function useScrollToAnchor(page: string, anchor: string) {
  useEffect(() => {
    if (!anchor) return;
    // wait for the page to render
    requestAnimationFrame(() => document.getElementById(anchor)?.scrollIntoView({ block: "start" }));
  }, [page, anchor]);
}

// --------------------------------------------------------------- rendering
function InlineView({ c }: { c: Inline[] }) {
  return (
    <>
      {c.map((x, i) => {
        switch (x.t) {
          case "text":
            return x.v;
          case "code":
            return <code key={i}>{x.v}</code>;
          case "strong":
            return (
              <strong key={i}>
                <InlineView c={x.c} />
              </strong>
            );
          case "em":
            return (
              <em key={i}>
                <InlineView c={x.c} />
              </em>
            );
          case "link":
            return <DocLink key={i} href={x.href} c={x.c} />;
        }
      })}
    </>
  );
}

function DocLink({ href, c }: { href: string; c: Inline[] }) {
  const external = /^https?:/.test(href);
  if (external)
    return (
      <a href={href} target="_blank" rel="noopener noreferrer">
        <InlineView c={c} />
      </a>
    );
  return (
    <a
      href={href}
      onClick={(e) => {
        if (e.metaKey || e.ctrlKey || e.shiftKey) return;
        e.preventDefault();
        if (href.startsWith("#")) docsNavigate(`${location.pathname}${href}`);
        else if (href.startsWith("/docs")) docsNavigate(href);
        else location.assign(href);
      }}
    >
      <InlineView c={c} />
    </a>
  );
}

function Heading({ b }: { b: Extract<Block, { t: "h" }> }) {
  const Tag = (`h${b.level}` as "h1" | "h2" | "h3");
  const copy = async (e: Event) => {
    e.preventDefault();
    const url = `${location.origin}${location.pathname}#${b.id}`;
    history.replaceState(null, "", `#${b.id}`);
    if (await copyText(url)) pushToast({ level: "info", title: UI[lang.value].docs, text: UI[lang.value].linkCopied }, 2500);
  };
  return (
    <Tag id={b.id} class={`dh dh${b.level}`}>
      <InlineView c={b.c} />
      {b.level > 1 && (
        <a href={`#${b.id}`} class="anchor" aria-label={UI[lang.value].copyLink} title={UI[lang.value].copyLink} onClick={copy}>
          #
        </a>
      )}
    </Tag>
  );
}

/** "Needs AllTheModium" / "Only without AllTheModium" for a conditional recipe. */
function requiresText(c: string, l: Lang) {
  const mod = c.replace(/^!?mod:/, "");
  return `${c.startsWith("!") ? UI[l].without : UI[l].requires} ${MODS[mod] ?? mod}`;
}

function Slot({ id, l, cat }: { id: string | null; l: Lang; cat?: LiveCatalog }) {
  return (
    <span class={`slot ${id ? "full" : ""}`} title={id ? itemName(id, l, cat) : undefined}>
      {id && <ItemIcon name={iconItem(id, cat)} displayName={itemName(id, l, cat)} />}
    </span>
  );
}

/**
 * A crafting grid. With live recipes it shows what the server uses: the server's version with a badge where it differs
 * from the mod's default (the default one click away), the default greyed out where the server removed it, and a short
 * line for a conditional variant that is not loaded on this server.
 */
function RecipeView({ r, state }: { r: Recipe; state?: RecipeLive }) {
  const l = lang.value;
  const cat = live.value ?? undefined;
  if (state?.status === "inactive") {
    return (
      <p class="recipe-inactive">
        <ItemIcon name={r.result} displayName={itemName(r.result, l, cat)} size={20} />
        <span>
          {itemName(r.result, l, cat)}: {r.requires?.length ? `${r.requires.map((c) => requiresText(c, l)).join(", ")} – ` : ""}
          {UI[l].inactive}
        </span>
      </p>
    );
  }
  const shown: Recipe & Partial<LiveRecipe> = state && "live" in state ? state.live : r;
  const other = !shown.grid && !shown.shapeless;
  const cells: (string | null)[] = [];
  if (other) {
    for (const it of shown.items ?? []) cells.push(it);
  } else if (shown.shapeless) {
    for (let i = 0; i < 9; i++) cells.push(shown.items?.[i] ?? null);
  } else {
    const rows = shown.grid ?? [];
    for (let y = 0; y < 3; y++) for (let x = 0; x < 3; x++) {
      const ch = rows[y]?.[x];
      cells.push(ch && ch !== " " && ch !== "." ? shown.key?.[ch] ?? null : null);
    }
  }
  const used = [...new Set(cells.filter(Boolean) as string[])];
  const result = shown.result || r.result;
  const status = state?.status;
  const badge =
    status === "changed" ? UI[l].badgeChanged : status === "added" ? UI[l].badgeAdded : status === "removed" ? UI[l].badgeRemoved : null;
  const station = other && shown.station && shown.station !== "minecraft:crafting_table" ? shown.station : null;
  return (
    <figure class={`recipe ${status === "removed" ? "is-removed" : ""} ${status ? `live-${status}` : ""}`}>
      {badge && <p class={`recipe-badge badge-${status}`}>{badge}</p>}
      <div class="recipe-main">
        <div
          class={other ? "craft-row" : "craft-grid"}
          role="img"
          aria-label={`${itemName(result, l, cat)}: ${used.map((u) => itemName(u, l, cat)).join(", ")}`}
        >
          {cells.map((c, i) => (
            <Slot key={i} id={c} l={l} cat={cat} />
          ))}
        </div>
        <span class="craft-arrow" aria-hidden="true">
          →
        </span>
        <div class="craft-result">
          <span class="slot full result" title={itemName(result, l, cat)}>
            <ItemIcon name={result} displayName={itemName(result, l, cat)} />
            {shown.count && shown.count > 1 && <span class="slot-count">{shown.count}</span>}
          </span>
          <span class="craft-result-name">{itemName(result, l, cat)}</span>
        </div>
      </div>
      <figcaption>
        <ul class="recipe-legend">
          {used.map((u) => (
            <li key={u}>
              <ItemIcon name={iconItem(u, cat)} displayName={itemName(u, l, cat)} /> {itemName(u, l, cat)}
            </li>
          ))}
        </ul>
        {shown.shapeless && <p class="muted small">{UI[l].shapeless}</p>}
        {station && (
          <p class="muted small">
            {UI[l].madeWith} {itemName(station, l, cat)}
          </p>
        )}
        {other && !station && shown.type && <p class="muted small">{shown.type}</p>}
        {status !== "added" &&
          r.requires?.map((c) => (
            <p key={c} class={`recipe-req ${c.startsWith("!") ? "without" : ""}`}>
              {requiresText(c, l)}
            </p>
          ))}
      </figcaption>
      {status === "changed" && (
        <details class="recipe-default">
          <summary>{UI[l].defaultRecipe}</summary>
          <RecipeView r={r} />
        </details>
      )}
    </figure>
  );
}

/** Above the recipes: whether they are this server's or the defaults. */
function LiveBanner() {
  const l = lang.value;
  const cat = live.value;
  if (cat === undefined) return null;
  if (cat === null)
    return (
      <aside class="callout callout-note recipe-live-banner">
        <p>{UI[l].liveFallback}</p>
      </aside>
    );
  const n = liveSummary(cat);
  const parts = [
    n.changed && `${n.changed} ${UI[l].liveChanged}`,
    n.added && `${n.added} ${UI[l].liveAdded}`,
    n.removed && `${n.removed} ${UI[l].liveRemoved}`,
  ].filter(Boolean);
  return (
    <aside class="callout callout-tip recipe-live-banner">
      <p>
        <strong>{UI[l].liveTitle}</strong> {parts.length ? parts.join(" · ") : UI[l].liveAllDefault}
      </p>
    </aside>
  );
}

function BlockView({ b }: { b: Block }) {
  switch (b.t) {
    case "h":
      return <Heading b={b} />;
    case "p":
      return (
        <p>
          <InlineView c={b.c} />
        </p>
      );
    case "ul":
    case "ol": {
      const Tag = b.t;
      return (
        <Tag>
          {b.items.map((it, i) => (
            <li key={i}>
              <InlineView c={it} />
            </li>
          ))}
        </Tag>
      );
    }
    case "callout":
      return (
        <aside class={`callout callout-${b.kind}`}>
          {b.c.map((p, i) => (
            <p key={i}>
              <InlineView c={p} />
            </p>
          ))}
        </aside>
      );
    case "table":
      return (
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                {b.head.map((h, i) => (
                  <th key={i}>
                    <InlineView c={h} />
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {b.rows.map((r, i) => (
                <tr key={i}>
                  {r.map((c, j) => (
                    <td key={j}>
                      <InlineView c={c} />
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      );
    case "code":
      return <CodeBlock code={b.code} label={b.lang || "shell"} />;
    case "recipe":
      return <RecipeView r={b.recipe} state={b.live} />;
  }
}

// --------------------------------------------------------------- shell
function SearchBox({ onPick, autoFocus }: { onPick?: () => void; autoFocus?: boolean }) {
  const [q, setQ] = useState("");
  const l = lang.value;
  const hits = useMemo(() => (q.trim().length >= 2 ? search(l, q) : []), [q, l]);
  return (
    <div class="docs-search">
      <input
        type="search"
        class="input docs-search-input"
        placeholder={UI[l].search}
        value={q}
        autoFocus={autoFocus}
        aria-label={UI[l].search}
        onInput={(e) => setQ((e.target as HTMLInputElement).value)}
      />
      {q.trim().length >= 2 && (
        <div class="docs-hits" role="listbox" aria-label={UI[l].results}>
          {hits.length === 0 ? (
            <p class="muted small">
              {UI[l].noResults} „{q}“
            </p>
          ) : (
            hits.map((h) => (
              <a
                key={`${h.page}#${h.anchor}`}
                class="docs-hit"
                href={`/docs/${h.page}${h.anchor ? `#${h.anchor}` : ""}`}
                onClick={(e) => {
                  e.preventDefault();
                  docsNavigate(`/docs/${h.page}${h.anchor ? `#${h.anchor}` : ""}`);
                  setQ("");
                  onPick?.();
                }}
              >
                <span class="docs-hit-title">
                  {h.heading} <span class="muted">· {h.pageTitle}</span>
                </span>
                <span class="docs-hit-snippet">{h.snippet}</span>
              </a>
            ))
          )}
        </div>
      )}
    </div>
  );
}

function Nav({ page, onPick }: { page: string; onPick?: () => void }) {
  const l = lang.value;
  const toc = pageBlocks(l, page).filter((b): b is Extract<Block, { t: "h" }> => b.t === "h" && b.level === 2);
  return (
    <nav class="docs-nav" aria-label={UI[l].docs}>
      {PAGES.map((p) => (
        <div key={p.id}>
          <a
            href={`/docs/${p.id}`}
            class={`docs-nav-page ${p.id === page ? "on" : ""}`}
            aria-current={p.id === page ? "page" : undefined}
            onClick={(e) => {
              e.preventDefault();
              docsNavigate(`/docs/${p.id}`);
              onPick?.();
            }}
          >
            <PixelIcon name={p.icon} size={14} />
            {p.title[l]}
          </a>
          {p.id === page && (
            <div class="docs-toc">
              {toc.map((h) => (
                <a
                  key={h.id}
                  href={`#${h.id}`}
                  onClick={(e) => {
                    e.preventDefault();
                    docsNavigate(`/docs/${p.id}#${h.id}`);
                    onPick?.();
                  }}
                >
                  {h.text}
                </a>
              ))}
            </div>
          )}
        </div>
      ))}
    </nav>
  );
}

function LangToggle() {
  const l = lang.value;
  return (
    <div class="seg-ctl lang-toggle" role="radiogroup" aria-label={UI[l].language}>
      {(["de", "en"] as const).map((x) => (
        <button key={x} type="button" role="radio" aria-checked={l === x} class={l === x ? "on" : ""} onClick={() => setLang(x)}>
          {x.toUpperCase()}
        </button>
      ))}
    </div>
  );
}

function MenuSheet({ page, onClose, children }: { page: string; onClose: () => void; children?: ComponentChildren }) {
  return (
    <div class="backdrop backdrop-sheet" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <section class="sheet docs-sheet" role="dialog" aria-modal="true" aria-label={UI[lang.value].menu}>
        <header class="sheet-head">
          <h2 class="px-title">{UI[lang.value].docs}</h2>
          <button type="button" class="icon-btn" aria-label="Close" onClick={onClose}>
            <IconClose />
          </button>
        </header>
        <div class="sheet-body">
          <SearchBox onPick={onClose} />
          <Nav page={page} onPick={onClose} />
          {children}
        </div>
      </section>
    </div>
  );
}

export default function Docs() {
  const { page, anchor } = loc.value;
  const l = lang.value;
  const [menu, setMenu] = useState(false);
  const cat = live.value;
  const blocks = useMemo(
    () =>
      applyLive(pageBlocks(l, page), cat, {
        leftovers: page === "recipes" ? { title: UI[l].addedHere, name: (id) => itemName(id, l, cat ?? undefined), before: "pack-makers" } : undefined,
      }),
    [l, page, cat],
  );
  const idx = PAGES.findIndex((p) => p.id === page);
  const prev = PAGES[idx - 1];
  const next = PAGES[idx + 1];
  const mainRef = useRef<HTMLElement>(null);
  useScrollToAnchor(page, anchor);
  useEffect(loadLive, []);
  useEffect(() => {
    document.documentElement.lang = l;
    document.title = `${PAGES[idx].title[l]} – CC: Cloud ${UI[l].docs}`;
  }, [page, l]);
  useEffect(() => {
    if (!CONTENT[l][page]) docsNavigate("/docs");
  }, [page, l]);
  const pref = themePref.value;

  return (
    <div class="docs">
      <header class="topbar docs-top">
        <a
          href="/"
          class="brand"
          onClick={(e) => {
            e.preventDefault();
            history.pushState(null, "", "/");
            dispatchEvent(new PopStateEvent("popstate"));
          }}
          aria-label="CC: Cloud dashboard"
        >
          <Logo size={26} />
          <span class="brand-text">
            <span class="brand-cc">CC:</span>
            <span class="brand-cloud">Cloud</span>
            <span class="docs-badge">{UI[l].docs}</span>
          </span>
        </a>
        <div class="docs-top-actions">
          <LangToggle />
          <button type="button" class="icon-btn" onClick={cycleTheme} aria-label={`Theme: ${pref}`}>
            {pref === "dark" ? <IconMoon /> : pref === "light" ? <IconSun /> : <IconAuto />}
          </button>
          <button type="button" class="btn btn-small docs-menu-btn" onClick={() => setMenu(true)}>
            ☰ {UI[l].menu}
          </button>
        </div>
      </header>
      <div class="docs-layout">
        <aside class="docs-side">
          <a
            class="link-back"
            href="/"
            onClick={(e) => {
              e.preventDefault();
              history.pushState(null, "", "/");
              dispatchEvent(new PopStateEvent("popstate"));
            }}
          >
            <IconBack size={16} /> {UI[l].back}
          </a>
          <SearchBox />
          <Nav page={page} />
        </aside>
        <main class="docs-main" ref={mainRef}>
          <article class="docs-article" lang={l}>
            {blocks.map((b, i) => (
              <Fragment key={`${page}-${l}-${i}`}>
                <BlockView b={b} />
                {i === 0 && page === "recipes" && <LiveBanner />}
              </Fragment>
            ))}
          </article>
          <nav class="docs-pager">
            {prev ? (
              <a
                href={`/docs/${prev.id}`}
                onClick={(e) => {
                  e.preventDefault();
                  docsNavigate(`/docs/${prev.id}`);
                }}
              >
                <span class="muted small">← {UI[l].prev}</span>
                <span>{prev.title[l]}</span>
              </a>
            ) : (
              <span />
            )}
            {next && (
              <a
                class="next"
                href={`/docs/${next.id}`}
                onClick={(e) => {
                  e.preventDefault();
                  docsNavigate(`/docs/${next.id}`);
                }}
              >
                <span class="muted small">{UI[l].next} →</span>
                <span>{next.title[l]}</span>
              </a>
            )}
          </nav>
        </main>
      </div>
      {menu && <MenuSheet page={page} onClose={() => setMenu(false)} />}
      <Toasts />
    </div>
  );
}

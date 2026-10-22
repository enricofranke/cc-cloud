// /docs – bilingual documentation inside the dashboard bundle (lazy-loaded chunk).
import { useEffect, useMemo, useRef, useState } from "preact/hooks";
import type { ComponentChildren } from "preact";
import { signal } from "@preact/signals";
import type { Block, Inline, Recipe } from "./md.ts";
import { CONTENT, PAGES, UI, detectLang, itemName, type Lang } from "./content/index.ts";
import { pageBlocks, search } from "./search.ts";
import { CodeBlock } from "../components/CodeBlock.tsx";
import { ItemIcon } from "../components/ItemIcon.tsx";
import { iconItem } from "./content/index.ts";
import { PixelIcon } from "../components/PixelIcon.tsx";
import { Logo, IconBack, IconClose, IconMoon, IconSun, IconAuto } from "../components/Icons.tsx";
import { copyText } from "../lib/clipboard.ts";
import { cycleTheme, themePref, pushToast } from "../lib/ui.ts";
import { Toasts } from "../components/Overlays.tsx";
import { getPref, setPref } from "../lib/store.ts";

// ------------------------------------------------------------------ state
const initialLang = (): Lang => {
  const saved = getPref("lang", "");
  return saved === "de" || saved === "en" ? saved : detectLang();
};
export const lang = signal<Lang>(initialLang());
const setLang = (l: Lang) => {
  lang.value = l;
  setPref("lang", l);
  document.documentElement.lang = l;
};

/** /docs, /docs/<page>, plus #anchor */
function current() {
  const m = /^\/docs\/?([a-z0-9-]*)/.exec(location.pathname);
  const page = PAGES.some((p) => p.id === m?.[1]) ? m![1] : PAGES[0].id;
  return { page, anchor: decodeURIComponent(location.hash.replace(/^#/, "")) };
}
const loc = signal(current());
addEventListener("popstate", () => (loc.value = current()));

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

function RecipeView({ r }: { r: Recipe }) {
  const l = lang.value;
  const cells: (string | null)[] = [];
  if (r.shapeless) {
    for (let i = 0; i < 9; i++) cells.push(r.items?.[i] ?? null);
  } else {
    const rows = r.grid ?? [];
    for (let y = 0; y < 3; y++) for (let x = 0; x < 3; x++) {
      const ch = rows[y]?.[x];
      cells.push(ch && ch !== " " && ch !== "." ? r.key?.[ch] ?? null : null);
    }
  }
  const used = [...new Set(cells.filter(Boolean) as string[])];
  return (
    <figure class="recipe">
      <div class="recipe-main">
        <div class="craft-grid" role="img" aria-label={`${itemName(r.result, l)}: ${used.map((u) => itemName(u, l)).join(", ")}`}>
          {cells.map((c, i) => (
            <span key={i} class={`slot ${c ? "full" : ""}`} title={c ? itemName(c, l) : undefined}>
              {c && <ItemIcon name={iconItem(c)} displayName={itemName(c, l)} />}
            </span>
          ))}
        </div>
        <span class="craft-arrow" aria-hidden="true">
          →
        </span>
        <div class="craft-result">
          <span class="slot full result" title={itemName(r.result, l)}>
            <ItemIcon name={r.result} displayName={itemName(r.result, l)} />
            {r.count && r.count > 1 && <span class="slot-count">{r.count}</span>}
          </span>
          <span class="craft-result-name">{itemName(r.result, l)}</span>
        </div>
      </div>
      <figcaption>
        <ul class="recipe-legend">
          {used.map((u) => (
            <li key={u}>
              <ItemIcon name={iconItem(u)} displayName={itemName(u, l)} /> {itemName(u, l)}
            </li>
          ))}
        </ul>
        {r.shapeless && <p class="muted small">{UI[l].shapeless}</p>}
      </figcaption>
    </figure>
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
      return <RecipeView r={b.recipe} />;
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
  const blocks = pageBlocks(l, page);
  const idx = PAGES.findIndex((p) => p.id === page);
  const prev = PAGES[idx - 1];
  const next = PAGES[idx + 1];
  const mainRef = useRef<HTMLElement>(null);
  useScrollToAnchor(page, anchor);
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
              <BlockView key={`${page}-${l}-${i}`} b={b} />
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

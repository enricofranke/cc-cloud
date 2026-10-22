// Client-side full-text search over the parsed docs (both languages are indexed separately).
import { blockText, parse, type Block } from "./md.ts";
import { CONTENT, PAGES, type Lang } from "./content/index.ts";

export interface Section {
  page: string;
  pageTitle: string;
  anchor: string;
  heading: string;
  text: string;
}

const parsed = new Map<string, Block[]>();
export function pageBlocks(lang: Lang, page: string): Block[] {
  const key = `${lang}/${page}`;
  let b = parsed.get(key);
  if (!b) {
    b = parse(CONTENT[lang][page] ?? "");
    parsed.set(key, b);
  }
  return b;
}

const indexes = new Map<Lang, Section[]>();
export function sections(lang: Lang): Section[] {
  let idx = indexes.get(lang);
  if (idx) return idx;
  idx = [];
  for (const meta of PAGES) {
    let cur: Section = { page: meta.id, pageTitle: meta.title[lang], anchor: "", heading: meta.title[lang], text: "" };
    for (const b of pageBlocks(lang, meta.id)) {
      if (b.t === "h" && b.level > 1) {
        if (cur.text || cur.anchor) idx.push(cur);
        cur = { page: meta.id, pageTitle: meta.title[lang], anchor: b.id, heading: b.text, text: "" };
      } else if (b.t !== "h") cur.text += " " + blockText(b);
    }
    idx.push(cur);
  }
  indexes.set(lang, idx);
  return idx;
}

const norm = (s: string) => s.toLowerCase().normalize("NFKD").replace(/[̀-ͯ]/g, "");

export interface Hit extends Section {
  score: number;
  snippet: string;
}

/** All terms must match; headings weigh more than body text. */
export function search(lang: Lang, query: string, limit = 20): Hit[] {
  const terms = norm(query).split(/\s+/).filter((t) => t.length >= 2);
  if (!terms.length) return [];
  const hits: Hit[] = [];
  for (const s of sections(lang)) {
    const head = norm(s.heading);
    const body = norm(s.text);
    let score = 0;
    let ok = true;
    for (const t of terms) {
      const inHead = head.includes(t);
      const inBody = body.includes(t);
      if (!inHead && !inBody) {
        ok = false;
        break;
      }
      score += (inHead ? 10 : 0) + (inBody ? 1 + Math.min(4, body.split(t).length - 2) : 0);
    }
    if (!ok) continue;
    const at = body.indexOf(terms[0]);
    const start = Math.max(0, at - 50);
    const raw = s.text.replace(/\s+/g, " ").trim();
    const snippet = (start > 0 ? "…" : "") + raw.slice(start, start + 140).trim() + (raw.length > start + 140 ? "…" : "");
    hits.push({ ...s, score, snippet });
  }
  return hits.sort((a, b) => b.score - a.score).slice(0, limit);
}

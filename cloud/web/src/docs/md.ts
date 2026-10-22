// A tiny Markdown subset parser for the bundled docs. It produces a plain
// tree that the renderer turns into Preact nodes – never HTML strings – so it
// works under the strict CSP and cannot inject markup.
//
// Blocks: # / ## / ### headings (optional {#id}), paragraphs, - lists,
// 1. lists, > callouts (> **Tip:** …), | tables |, ``` fenced code ```
// (```recipe = a crafting grid as JSON).
// Inline: `code`, **bold**, *italic*, [text](href).

export type Inline =
  | { t: "text"; v: string }
  | { t: "code"; v: string }
  | { t: "strong"; c: Inline[] }
  | { t: "em"; c: Inline[] }
  | { t: "link"; href: string; c: Inline[] };

export interface Recipe {
  result: string;
  count?: number;
  shapeless?: boolean;
  /** 1–3 rows of up to 3 characters; space or "." = empty */
  grid?: string[];
  key?: Record<string, string>;
  /** shapeless ingredients */
  items?: string[];
  note?: string;
}

export type Block =
  | { t: "h"; level: 1 | 2 | 3; id: string; c: Inline[]; text: string }
  | { t: "p"; c: Inline[] }
  | { t: "ul" | "ol"; items: Inline[][] }
  | { t: "callout"; kind: "tip" | "warn" | "note"; c: Inline[][] }
  | { t: "table"; head: Inline[][]; rows: Inline[][][] }
  | { t: "code"; lang: string; code: string }
  | { t: "recipe"; recipe: Recipe };

export function slug(s: string) {
  return (
    s
      .toLowerCase()
      .normalize("NFKD")
      .replace(/[̀-ͯ]/g, "")
      .replace(/ß/g, "ss")
      .replace(/[^a-z0-9]+/g, "-")
      .replace(/^-+|-+$/g, "")
      .slice(0, 64) || "section"
  );
}

// ------------------------------------------------------------------ inline
export function parseInline(src: string): Inline[] {
  const out: Inline[] = [];
  let text = "";
  const flush = () => {
    if (text) out.push({ t: "text", v: text });
    text = "";
  };
  let i = 0;
  while (i < src.length) {
    const ch = src[i];
    if (ch === "\\" && i + 1 < src.length) {
      text += src[i + 1];
      i += 2;
      continue;
    }
    if (ch === "`") {
      const end = src.indexOf("`", i + 1);
      if (end > i) {
        flush();
        out.push({ t: "code", v: src.slice(i + 1, end) });
        i = end + 1;
        continue;
      }
    }
    if (ch === "*" && src[i + 1] === "*") {
      const end = src.indexOf("**", i + 2);
      if (end > i + 2) {
        flush();
        out.push({ t: "strong", c: parseInline(src.slice(i + 2, end)) });
        i = end + 2;
        continue;
      }
    }
    if (ch === "*" && src[i + 1] !== " ") {
      const end = src.indexOf("*", i + 1);
      if (end > i + 1 && src[end - 1] !== " ") {
        flush();
        out.push({ t: "em", c: parseInline(src.slice(i + 1, end)) });
        i = end + 1;
        continue;
      }
    }
    if (ch === "[") {
      const close = src.indexOf("](", i);
      const end = close > 0 ? src.indexOf(")", close + 2) : -1;
      if (close > i && end > close) {
        const href = src.slice(close + 2, end).trim();
        if (safeHref(href)) {
          flush();
          out.push({ t: "link", href, c: parseInline(src.slice(i + 1, close)) });
          i = end + 1;
          continue;
        }
      }
    }
    text += ch;
    i++;
  }
  flush();
  return out;
}

/** Only internal docs links, anchors and http(s) – no javascript:, data: … */
export function safeHref(href: string) {
  return /^(\/docs(\/[a-z0-9-]*)?(#[a-z0-9-]+)?|#[a-z0-9-]+|https?:\/\/[^\s"'<>]+|\/|\/#\/[a-z0-9/-]*)$/i.test(href);
}

export function inlineText(c: Inline[]): string {
  return c.map((x) => (x.t === "text" || x.t === "code" ? x.v : inlineText(x.c))).join("");
}

// ------------------------------------------------------------------- blocks
export function parse(src: string): Block[] {
  const lines = src.replace(/\r\n?/g, "\n").split("\n");
  const blocks: Block[] = [];
  const ids = new Set<string>();
  let i = 0;
  const uniq = (id: string) => {
    let u = id;
    for (let n = 2; ids.has(u); n++) u = `${id}-${n}`;
    ids.add(u);
    return u;
  };
  while (i < lines.length) {
    const line = lines[i];
    if (!line.trim()) {
      i++;
      continue;
    }
    // fenced code
    const fence = /^```\s*([a-z0-9_-]*)\s*$/i.exec(line);
    if (fence) {
      const body: string[] = [];
      i++;
      while (i < lines.length && !/^```\s*$/.test(lines[i])) body.push(lines[i++]);
      i++; // closing fence
      const code = body.join("\n");
      if (fence[1] === "recipe") {
        try {
          blocks.push({ t: "recipe", recipe: JSON.parse(code) as Recipe });
        } catch {
          blocks.push({ t: "code", lang: "json", code });
        }
      } else blocks.push({ t: "code", lang: fence[1] || "", code });
      continue;
    }
    // heading
    const h = /^(#{1,3})\s+(.*?)(?:\s+\{#([a-z0-9-]+)\})?\s*$/.exec(line);
    if (h) {
      const c = parseInline(h[2]);
      const text = inlineText(c);
      blocks.push({ t: "h", level: h[1].length as 1 | 2 | 3, id: uniq(h[3] ?? slug(text)), c, text });
      i++;
      continue;
    }
    // table
    if (/^\|.*\|\s*$/.test(line)) {
      const rows: string[][] = [];
      while (i < lines.length && /^\|.*\|\s*$/.test(lines[i])) {
        const cells = lines[i]
          .trim()
          .slice(1, -1)
          .split(/(?<!\\)\|/)
          .map((c) => c.trim().replace(/\\\|/g, "|"));
        if (!cells.every((c) => /^:?-{2,}:?$/.test(c))) rows.push(cells);
        i++;
      }
      const [head, ...rest] = rows;
      blocks.push({ t: "table", head: (head ?? []).map(parseInline), rows: rest.map((r) => r.map(parseInline)) });
      continue;
    }
    // callout
    if (/^>\s?/.test(line)) {
      const paras: string[] = [];
      let cur: string[] = [];
      while (i < lines.length && /^>\s?/.test(lines[i])) {
        const l = lines[i].replace(/^>\s?/, "");
        if (!l.trim()) {
          if (cur.length) paras.push(cur.join(" "));
          cur = [];
        } else cur.push(l.trim());
        i++;
      }
      if (cur.length) paras.push(cur.join(" "));
      const first = paras[0] ?? "";
      const kind = /^\*\*(warning|achtung|warnung)/i.test(first) ? "warn" : /^\*\*(tip|tipp)/i.test(first) ? "tip" : "note";
      blocks.push({ t: "callout", kind, c: paras.map(parseInline) });
      continue;
    }
    // lists
    const ul = /^\s*[-*]\s+/;
    const ol = /^\s*\d+[.)]\s+/;
    if (ul.test(line) || ol.test(line)) {
      const ordered = ol.test(line);
      const re = ordered ? ol : ul;
      const items: string[] = [];
      while (i < lines.length && (re.test(lines[i]) || (/^\s{2,}\S/.test(lines[i]) && items.length))) {
        if (re.test(lines[i])) items.push(lines[i].replace(re, ""));
        else items[items.length - 1] += " " + lines[i].trim();
        i++;
      }
      blocks.push({ t: ordered ? "ol" : "ul", items: items.map(parseInline) });
      continue;
    }
    // paragraph
    const para: string[] = [];
    while (
      i < lines.length &&
      lines[i].trim() &&
      !/^(#{1,3}\s|```|>|\|)/.test(lines[i]) &&
      !ul.test(lines[i]) &&
      !ol.test(lines[i])
    )
      para.push(lines[i++].trim());
    blocks.push({ t: "p", c: parseInline(para.join(" ")) });
  }
  return blocks;
}

/** Plain text of a block (for search). */
export function blockText(b: Block): string {
  switch (b.t) {
    case "h":
      return b.text;
    case "p":
      return inlineText(b.c);
    case "ul":
    case "ol":
      return b.items.map(inlineText).join(" ");
    case "callout":
      return b.c.map(inlineText).join(" ");
    case "table":
      return [...b.head, ...b.rows.flat()].map(inlineText).join(" ");
    case "code":
      return b.code;
    case "recipe":
      return [b.recipe.result, ...Object.values(b.recipe.key ?? {}), ...(b.recipe.items ?? [])].join(" ");
  }
}

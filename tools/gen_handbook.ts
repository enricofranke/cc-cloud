// Generates the in-game handbook (GuideMe) from the dashboard docs.
//
//   node tools/gen_handbook.ts           writes src/main/resources/assets/cccloud/guides/cccloud/handbook/
//   node tools/gen_handbook.ts --check   exits 1 if the committed pages differ from the docs
//
// Source: cloud/web/src/docs/content (read-only). One page per docs page; English is the default language,
// German goes to the _de_de/ folder (GuideMe picks the page of the client language, else English).
// Conversion: GuideMe parses MDX, so `<`, `{` and `}` outside code are escaped; custom heading ids {#x} are dropped;
// ```recipe blocks become <RecipeFor> (the real in-game recipe, with a fallback text if the recipe is missing,
// e.g. the ME bridge without AE2); /docs/page#anchor links become page.md links.

import { mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync, existsSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { CONTENT, PAGES, type Lang } from "../cloud/web/src/docs/content/index.ts";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const OUT = join(ROOT, "src/main/resources/assets/cccloud/guides/cccloud/handbook");
const LANG_DIR: Record<Lang, string> = { en: "", de: "_de_de" };
const FALLBACK: Record<Lang, string> = {
  en: "This recipe is not available in this modpack (a required mod is missing or the pack changed it).",
  de: "Dieses Rezept gibt es in diesem Modpack nicht (eine benötigte Mod fehlt oder das Pack hat es geändert).",
};
const GENERATED: Record<Lang, string> = {
  en: "Generated from the dashboard docs by tools/gen_handbook.ts – edit cloud/web/src/docs/content instead.",
  de: "Aus der Dashboard-Doku erzeugt (tools/gen_handbook.ts) – Änderungen in cloud/web/src/docs/content machen.",
};

/** Icon item per docs page (shown in the guide's navigation). */
const ICONS: Record<string, string> = {
  start: "cccloud:cloud_mainframe",
  "cc-basics": "computercraft:computer_advanced",
  dashboard: "cccloud:cloud_uplink",
  redstone: "cccloud:cloud_lamp",
  lua: "computercraft:computer_advanced",
  "ui-library": "cccloud:cloud_uplink",
  network: "cccloud:cloud_relay",
  upgrades: "cccloud:cloud_tablet",
  bridges: "cccloud:cloud_core",
  accounts: "cccloud:cloud_link_card",
  security: "cccloud:quantum_core",
  faq: "cccloud:cloud_analyzer",
};

/** The page file of a docs page id: the start page is the guide's index. */
export const fileOf = (id: string) => (id === "start" ? "index.md" : `${id}.md`);

/** Escapes MDX-significant characters in plain text, leaving `inline code` alone. */
export function escapeText(line: string): string {
  return line
    .split(/(`[^`]*`)/)
    .map((part, i) => (i % 2 === 1 ? part : part.replace(/([<{}])/g, "\\$1")))
    .join("");
}

/** Rewrites /docs/page#anchor links to page.md (anchors are dropped: GuideMe uses its own heading ids). */
export function rewriteLinks(line: string): string {
  return line.replace(/\]\(\/docs\/([a-z0-9-]+)(?:#[a-z0-9-]+)?\)/g, (_m, id: string) => `](${fileOf(id)})`);
}

const yamlString = (s: string) => JSON.stringify(s);

/** Converts one docs page (the web's Markdown subset) to a GuideMe page. */
export function convert(id: string, lang: Lang, markdown: string, position: number, title: string): string {
  const out: string[] = [
    "---",
    `# ${GENERATED[lang]}`,
    "navigation:",
    `  title: ${yamlString(title)}`,
    `  position: ${position}`,
    ...(ICONS[id] ? [`  icon: ${ICONS[id]}`] : []),
    "---",
    "",
  ];
  const lines = markdown.replace(/\r\n?/g, "\n").split("\n");
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const fence = /^```(\w*)\s*$/.exec(line);
    if (fence) {
      const body: string[] = [];
      let j = i + 1;
      while (j < lines.length && !/^```\s*$/.test(lines[j])) body.push(lines[j++]);
      i = j;
      if (fence[1] === "recipe") {
        const recipe = JSON.parse(body.join("\n")) as { result: string };
        out.push(`<RecipeFor id="${recipe.result}" fallbackText="${FALLBACK[lang]}" />`);
      } else {
        out.push("```" + fence[1], ...body, "```");
      }
      continue;
    }
    let text = line.replace(/^(#{1,6} .*?)\s*\{#[a-z0-9-]+\}\s*$/, "$1");
    text = rewriteLinks(escapeText(text));
    out.push(text);
  }
  out.push("");
  return out.join("\n").replace(/\n{3,}/g, "\n\n");
}

/** All generated files: relative path → content. */
export function generate(): Map<string, string> {
  const files = new Map<string, string>();
  PAGES.forEach((page, position) => {
    for (const lang of Object.keys(LANG_DIR) as Lang[]) {
      const markdown = CONTENT[lang][page.id];
      if (markdown === undefined) throw new Error(`docs page ${page.id} missing in ${lang}`);
      files.set(join(LANG_DIR[lang], fileOf(page.id)), convert(page.id, lang, markdown, position, page.title[lang]));
    }
  });
  return files;
}

function listExisting(dir: string, base = dir): string[] {
  if (!existsSync(dir)) return [];
  return readdirSync(dir, { withFileTypes: true }).flatMap((e) =>
    e.isDirectory() ? listExisting(join(dir, e.name), base) : e.name.endsWith(".md") ? [relative(base, join(dir, e.name))] : [],
  );
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const files = generate();
  if (process.argv.includes("--check")) {
    const stale = [...files].filter(([p, c]) => !existsSync(join(OUT, p)) || readFileSync(join(OUT, p), "utf8") !== c).map(([p]) => p);
    const extra = listExisting(OUT).filter((p) => !files.has(p));
    if (stale.length || extra.length) {
      console.error(`handbook out of date: ${[...stale, ...extra].join(", ")} – run node tools/gen_handbook.ts`);
      process.exit(1);
    }
    console.log(`handbook up to date (${files.size} pages)`);
  } else {
    rmSync(OUT, { recursive: true, force: true });
    for (const [p, c] of files) {
      mkdirSync(dirname(join(OUT, p)), { recursive: true });
      writeFileSync(join(OUT, p), c);
    }
    console.log(`wrote ${files.size} pages to ${relative(ROOT, OUT)}`);
  }
}

// The Cloud Scripts templates that ship with the mod (cccloud_code/templates): index.json for the names and blurbs,
// the .lua files as raw text. Bundled into the lazy Scripts chunk at build time.
import type { Lang } from "../lib/lang.ts";
import index from "../../../../src/main/resources/cccloud_code/templates/index.json" with { type: "json" };

const files = import.meta.glob("../../../../src/main/resources/cccloud_code/templates/*.lua", { query: "?raw", import: "default", eager: true }) as Record<string, string>;

export interface Template {
  id: string;
  /** the script name a new script gets */
  name: string;
  title: string;
  doc: string;
  source: string;
}

interface Entry {
  id: string;
  file: string;
  name: string;
  title: { en: string; de: string };
  doc: { en: string; de: string };
}

/** The templates in index order; an entry whose file is missing is left out. */
export function templates(lang: Lang): Template[] {
  const byFile = new Map(Object.entries(files).map(([path, text]) => [path.slice(path.lastIndexOf("/") + 1), text]));
  return (index as Entry[])
    .filter((e) => byFile.has(e.file))
    .map((e) => ({ id: e.id, name: e.name, title: e.title[lang] || e.title.en, doc: e.doc[lang] || e.doc.en, source: byFile.get(e.file)! }));
}

/** What a blank script starts with. */
export const BLANK_SOURCE = `-- Runs inside the mainframe. Reads come from the dashboard, actions use your rights.
cloud.log("hello from", cloud.script.name)
`;

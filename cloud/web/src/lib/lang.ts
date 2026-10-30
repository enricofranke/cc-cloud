// The language preference (docs and the v0.10 screens): German for German browsers, otherwise English.
// Kept free of imports so the docs content and the handbook generator can use it.

export type Lang = "de" | "en";
export const LANGS: Lang[] = ["en", "de"];

/** German for German browsers, otherwise English. */
export function detectLang(languages: readonly string[] = typeof navigator === "undefined" ? [] : navigator.languages ?? [navigator.language]): Lang {
  for (const l of languages) {
    const p = l.toLowerCase().slice(0, 2);
    if (p === "de") return "de";
    if (p === "en") return "en";
  }
  return "en";
}

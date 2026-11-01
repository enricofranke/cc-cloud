// The demo's own texts. The dashboard's older screens are English in the product itself.
export type Lang = "en" | "de";

const EN = {
  bar: "Live demo: simulated base, nothing here is a real server",
  barShort: "Live demo: simulated base, nothing here is a real server",
  download: "Download",
  reset: "Reset",
  resetTitle: "Start over with a fresh base",
  tour: "Tour",
  tourTitle: "Start the guided tour",
  pinHint: "Demo PIN: 1234",
  loading: "Starting the simulated base",
  failed: "The demo could not start in this browser.",
  retry: "Try again",
  pairTitle: "Pairing happens in the game",
  pairLead: "This demo has no server to connect to, so it opens already linked. On your own server it takes a minute:",
  pairSteps: [
    "Build a Cloud Mainframe and give it power.",
    "Right-click it for an 8-character code.",
    "Enter the code on the dashboard and allow the device in game.",
  ],
  pairBack: "Back to the demo base",
  pairDownload: "Download CC: Cloud",
};
const DE: typeof EN = {
  bar: "Live-Demo: simulierte Basis, hier läuft kein echter Server",
  barShort: "Live-Demo: simulierte Basis, hier läuft kein echter Server",
  download: "Download",
  reset: "Zurücksetzen",
  resetTitle: "Neu anfangen mit frischer Basis",
  tour: "Tour",
  tourTitle: "Geführte Tour starten",
  pinHint: "Demo-PIN: 1234",
  loading: "Simulierte Basis startet",
  failed: "Die Demo konnte in diesem Browser nicht starten.",
  retry: "Nochmal versuchen",
  pairTitle: "Gekoppelt wird im Spiel",
  pairLead: "Diese Demo hat keinen Server, mit dem sie sich verbinden könnte, sie startet deshalb schon verbunden. Auf deinem eigenen Server dauert das eine Minute:",
  pairSteps: [
    "Bau einen Cloud Mainframe und versorge ihn mit Strom.",
    "Rechtsklick auf den Mainframe zeigt einen 8-stelligen Code.",
    "Code im Dashboard eingeben und das Gerät im Spiel erlauben.",
  ],
  pairBack: "Zurück zur Demo-Basis",
  pairDownload: "CC: Cloud herunterladen",
};

export type Strings = typeof EN;

/** `?lang=` from the website, else the browser. */
export function pickLang(search: string, languages: readonly string[]): Lang {
  const asked = new URLSearchParams(search).get("lang");
  if (asked === "de" || asked === "en") return asked;
  for (const l of languages) {
    const p = l.toLowerCase().slice(0, 2);
    if (p === "de" || p === "en") return p;
  }
  return "en";
}

export const lang: Lang = pickLang(location.search, navigator.languages ?? [navigator.language]);
export const s: Strings = lang === "de" ? DE : EN;

// The demo's own texts. The dashboard's older screens are English in the product itself.
export type Lang = "en" | "de";

const EN = {
  bar: "Live demo: simulated base, nothing here is a real server",
  barLabel: "About this demo",
  download: "Download",
  reset: "Reset",
  resetTitle: "Start over with a fresh base",
  tour: "Tour",
  tourTitle: "Start the guided tour",
  tourLabel: "Guided tour",
  tourSkip: "Skip tour",
  tourBack: "Back",
  tourNext: "Next",
  tourShow: "Show me",
  tourFinish: "Keep exploring",
  tourDownload: "Download CC: Cloud",
  steps: [
    {
      title: "Search your storage",
      body: "Every item in your ME or RS network, in one box. Type “iron” and watch the list narrow down.",
      ok: "Found. Open any row for counts, crafting and the watchlist.",
    },
    {
      title: "Craft from your phone",
      body: "Search for something marked craftable, open it and pick an amount. The job runs in your base while you do something else.",
      ok: "Job started. Clear the search and open CPUs to follow it.",
    },
    {
      title: "Emergency stop with a PIN",
      body: "Dangerous buttons can ask for a PIN, so a borrowed phone cannot shut down your base. Press SCRAM. The demo PIN is 1234.",
      ok: "Reactor down. The Reactor power switch brings it back.",
    },
    {
      title: "Build your own panel",
      body: "The pencil opens the layout editor: move cards, resize them and add widgets from the toolbox. No code needed.",
      ok: "You are editing. Change something, or press the pencil again to leave.",
    },
    {
      title: "Your base in 3D",
      body: "The Network tab maps every uplink, relay and link. Switch to 3D and fly through your world.",
      ok: "Drag to look around, scroll or pinch to zoom.",
    },
    {
      title: "That was CC: Cloud",
      body: "The real dashboard, here on a simulated base. In the game your own Minecraft server hosts it: no external service, no account.",
      ok: "",
    },
  ],
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
  noteShares: "Simulated: on a real server a share link opens a live, read-only view for anyone who has it. Here it only leads back to this demo.",
  noteKeys: "Simulated: keys made here work on nothing. On a real server they open the public API for your own tools.",
  notePush: "Simulated: nothing is sent. A real mainframe posts to Discord, ntfy or your webhook.",
  noteDevices: "Simulated: on a real server this lists the browsers linked to your player.",
  noteDisplays: "Simulated: Cloud Displays are screens in the game world. This list shows what you would manage.",
  noteAccess: "Simulated players and teams. On a real server these come from your game, FTB Teams or LuckPerms.",
};
const DE: typeof EN = {
  bar: "Live-Demo: simulierte Basis, hier läuft kein echter Server",
  barLabel: "Über diese Demo",
  download: "Download",
  reset: "Neustart",
  resetTitle: "Neu anfangen mit frischer Basis",
  tour: "Tour",
  tourTitle: "Geführte Tour starten",
  tourLabel: "Geführte Tour",
  tourSkip: "Tour überspringen",
  tourBack: "Zurück",
  tourNext: "Weiter",
  tourShow: "Zeig's mir",
  tourFinish: "Weiter erkunden",
  tourDownload: "CC: Cloud herunterladen",
  steps: [
    {
      title: "Durchsuche dein Lager",
      body: "Jedes Item aus deinem ME- oder RS-Netz in einem Suchfeld. Tipp „iron“ ein und sieh zu, wie die Liste schrumpft.",
      ok: "Gefunden. Jede Zeile öffnet Bestand, Crafting und Watchlist.",
    },
    {
      title: "Crafte vom Handy aus",
      body: "Such etwas mit dem Hinweis „craftable“, öffne es und wähl eine Menge. Der Auftrag läuft in der Basis, während du etwas anderes machst.",
      ok: "Auftrag läuft. Leere die Suche und öffne CPUs, um ihn zu verfolgen.",
    },
    {
      title: "Not-Aus mit PIN",
      body: "Gefährliche Knöpfe können eine PIN verlangen, damit ein geliehenes Handy nicht deine Basis abschaltet. Drück SCRAM. Die Demo-PIN ist 1234.",
      ok: "Reaktor aus. Mit dem Schalter „Reactor power“ fährst du ihn wieder hoch.",
    },
    {
      title: "Bau dein eigenes Panel",
      body: "Der Stift öffnet den Layout-Editor: Karten verschieben, Größe ändern, Widgets aus der Toolbox hinzufügen. Ganz ohne Code.",
      ok: "Du bearbeitest jetzt. Ändere etwas oder drück wieder den Stift.",
    },
    {
      title: "Deine Basis in 3D",
      body: "Der Network-Tab zeigt jeden Uplink, jedes Relay und jede Verbindung. Schalte auf 3D und flieg durch deine Welt.",
      ok: "Ziehen zum Umsehen, Scrollen oder Pinch zum Zoomen.",
    },
    {
      title: "Das war CC: Cloud",
      body: "Das echte Dashboard, hier auf einer simulierten Basis. Im Spiel liefert es dein eigener Minecraft-Server aus: ohne externen Dienst, ohne Konto.",
      ok: "",
    },
  ],
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
  noteShares: "Simuliert: Auf einem echten Server öffnet ein Freigabe-Link eine Live-Ansicht nur zum Lesen, für alle mit dem Link. Hier führt er nur zurück in diese Demo.",
  noteKeys: "Simuliert: Hier erzeugte Keys funktionieren nirgends. Auf einem echten Server öffnen sie die öffentliche API für deine Tools.",
  notePush: "Simuliert: Es wird nichts gesendet. Ein echter Mainframe schreibt an Discord, ntfy oder deinen Webhook.",
  noteDevices: "Simuliert: Auf einem echten Server stehen hier die Browser, die mit deinem Spieler verknüpft sind.",
  noteDisplays: "Simuliert: Cloud Displays sind Bildschirme in der Spielwelt. Die Liste zeigt, was du verwalten würdest.",
  noteAccess: "Simulierte Spieler und Teams. Auf einem echten Server kommen sie aus deinem Spiel, FTB Teams oder LuckPerms.",
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

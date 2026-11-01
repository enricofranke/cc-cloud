// The slim bar that stays on top of every screen: what this is, the way out and a fresh start.
import { render } from "preact";
import { useEffect, useRef } from "preact/hooks";
import { route } from "../../web/src/lib/ui.ts";
import { lang, s, type Strings } from "./strings.ts";

const NOTES: Partial<Record<string, keyof Strings>> = {
  shares: "noteShares",
  apikeys: "noteKeys",
  push: "notePush",
  devices: "noteDevices",
  displays: "noteDisplays",
  access: "noteAccess",
};

/** A fresh base: the worker and everything in memory start over. */
export function resetDemo() {
  history.replaceState(null, "", `${import.meta.env.BASE_URL}${location.search}`);
  location.reload();
}

function Bar({ onTour }: { onTour: () => void }) {
  const self = useRef<HTMLDivElement>(null);
  const note = NOTES[route.value.name];
  // sticky headers below take their offset from the bar's real height (it wraps on phones)
  useEffect(() => {
    const el = self.current!;
    const set = () => document.documentElement.style.setProperty("--demo-bar", `${Math.round(el.getBoundingClientRect().height)}px`);
    const ro = new ResizeObserver(set);
    ro.observe(el);
    set();
    return () => ro.disconnect();
  }, []);
  return (
    <div class="demo-bar" ref={self} role="region" aria-label={s.barLabel}>
      <div class="demo-bar-row">
        <span class="demo-dot" aria-hidden="true" />
        <p class="demo-bar-text">{s.bar}</p>
        <button type="button" class="demo-tool" onClick={onTour} title={s.tourTitle}>
          {s.tour}
        </button>
        <button type="button" class="demo-tool" onClick={resetDemo} title={s.resetTitle}>
          {s.reset}
        </button>
        <a class="demo-tool demo-get" href={`/${lang}/download/`}>
          {s.download}
        </a>
      </div>
      {note && <p class="demo-note">{s[note] as string}</p>}
    </div>
  );
}

export function mountBar(onTour: () => void) {
  render(<Bar onTour={onTour} />, document.getElementById("demo-bar")!);
}

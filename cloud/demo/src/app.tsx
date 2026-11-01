// Loaded after the shims: importing the dashboard reads its storage and opens its connections.
import { render } from "preact";
import { effect } from "@preact/signals";
import { App } from "../../web/src/app.tsx";
import { getConn } from "../../web/src/lib/conns.ts";
import { dismissToast, toasts } from "../../web/src/lib/ui.ts";
import "../../web/src/styles.css";
import { mountBar } from "./Bar.tsx";
import { BASE_ID } from "./session.ts";
import { s } from "./strings.ts";
import { greetOnce, mountTour, openTour } from "./Tour.tsx";
import "./demo.css";

/** The boot screen stays until the base has answered, so nobody sees "Connecting". */
function hideBoot() {
  const boot = document.getElementById("demo-boot");
  if (!boot) return;
  const gone = () => boot.remove();
  const stop = effect(() => {
    if (getConn(BASE_ID)?.status.value !== "open") return;
    queueMicrotask(() => stop());
    boot.classList.add("done");
    setTimeout(gone, 260);
    setTimeout(greetOnce, 700);
  });
  setTimeout(gone, 8000);
}

/** Alerts stay in the bell. On a demo nobody should have to clear them from the screen first. */
function fadeToasts() {
  const timed = new Set<number>();
  effect(() => {
    for (const t of toasts.value) {
      if (!t.sticky || timed.has(t.id)) continue;
      timed.add(t.id);
      setTimeout(() => dismissToast(t.id), 7000);
    }
  });
}

export function mount() {
  document.documentElement.style.setProperty("--demo-pin", JSON.stringify(s.pinHint));
  mountBar(openTour);
  render(<App />, document.getElementById("app")!);
  mountTour();
  fadeToasts();
  hideBoot();
}

// The guided tour: a small card of the dashboard that walks through what the product is for. It watches the real screens
// to see what the visitor did, and "Show me" does the step with the real controls. Nothing here talks to the simulation.
import { effect, signal } from "@preact/signals";
import { render } from "preact";
import { useEffect, useRef, useState } from "preact/hooks";
import { navigate, toasts } from "../../web/src/lib/ui.ts";
import { BASE_ID } from "./session.ts";
import { visitorStorage } from "./shims.ts";
import { lang, s } from "./strings.ts";

const SEEN = "cccloud.demo.tour";

const current = signal<number | null>(null);
let crafted = false;
let scrammed = false;

const calm = () => matchMedia("(prefers-reduced-motion: reduce)").matches;
const shown = (el: Element) => el.getClientRects().length > 0;
const first = (selector: string, test: (el: HTMLElement) => boolean = () => true) => [...document.querySelectorAll<HTMLElement>(selector)].find((el) => shown(el) && test(el)) ?? null;
const hasText = (re: RegExp) => (el: HTMLElement) => re.test(el.textContent ?? "");
const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, calm() ? Math.min(ms, 30) : ms));

async function until<T>(get: () => T | null | undefined, ms = 2500): Promise<T | null> {
  const end = performance.now() + ms;
  for (;;) {
    const v = get();
    if (v) return v;
    if (performance.now() > end) return null;
    await sleep(40);
  }
}

async function type(input: HTMLInputElement, text: string, live: () => boolean) {
  const put = (v: string) => {
    input.value = v;
    input.dispatchEvent(new Event("input", { bubbles: true }));
  };
  put("");
  for (let i = 1; i <= text.length && live(); i++) {
    put(text.slice(0, i));
    await sleep(70);
  }
}

const leaveEditor = () => {
  if (first('button[aria-label="Leave layout editor"]')) window.dispatchEvent(new Event("cccloud:edit-exit"));
};

/** Back on the base's first page: what steps one to three point at. */
async function overview() {
  leaveEditor();
  if (location.hash !== `#/m/${BASE_ID}`) navigate(`/m/${BASE_ID}`);
  (await until(() => first(".toptabs .tab, .tabbar .tab")))?.click();
}

interface Step {
  enter?(): Promise<void> | void;
  target?(): HTMLElement | null;
  done?(): boolean;
  show?(live: () => boolean): Promise<void>;
}

const craftButton = () => first(".backdrop .pxbtn-gold, .backdrop .btn-wide", hasText(/^(Craft|Order anyway)/i));
const cpusButton = () => first(".store-quick .btn", hasText(/^CPUs$/));
const craftableRow = (name = /./) => first(".item-row", (el) => !!el.querySelector(".craft-badge") && name.test(el.textContent ?? ""));
const scramButton = () => first(".pxbtn-red", hasText(/scram/i));
const threeButton = () => first(".seg-ctl button", hasText(/^3D$/));

const STEPS: Step[] = [
  {
    enter: overview,
    target: () => first(".search-bar"),
    done: () => !!first(".item-row"),
    async show(live) {
      const input = first(".search-input") as HTMLInputElement | null;
      if (input) await type(input, "iron", live);
    },
  },
  {
    enter: overview,
    target: () => craftButton() ?? (crafted ? (cpusButton() ?? first(".search-clear")) : (craftableRow() ?? first(".search-bar"))),
    done: () => crafted,
    async show(live) {
      const input = first(".search-input") as HTMLInputElement | null;
      if (input) await type(input, "logic processor", live);
      const row = await until(() => craftableRow(/logic processor/i));
      await sleep(350);
      if (!live()) return;
      row?.click();
      // item details, then the plan: its button waits for the preview
      for (let i = 0; i < 2 && live(); i++) {
        const go = await until(() => first(".backdrop .pxbtn-wide:not([disabled]), .backdrop .btn-wide:not([disabled]), .backdrop .detail-actions .pxbtn"));
        await sleep(500);
        go?.click();
        await sleep(100);
        if (crafted) break;
      }
      await until(() => !document.querySelector(".backdrop") && crafted);
      await sleep(400);
      if (!live()) return;
      first(".search-clear")?.click();
      (await until(cpusButton))?.click();
    },
  },
  {
    enter: overview,
    target: scramButton,
    done: () => scrammed,
    async show(live) {
      scramButton()?.click();
      if (!(await until(() => first(".pin-dialog"), 700))) return;
      for (const digit of "1234") {
        await sleep(150);
        if (!live()) return;
        first(".keypad .key", (el) => el.textContent === digit)?.click();
      }
      await sleep(150);
      first(".key-ok")?.click();
    },
  },
  {
    enter: overview,
    target: () => first('button[aria-label="Edit layout"]') ?? first('button[aria-label="Leave layout editor"]'),
    done: () => !!first('button[aria-label="Leave layout editor"]'),
    async show() {
      first('button[aria-label="Edit layout"]')?.click();
    },
  },
  {
    enter: leaveEditor,
    target: () => threeButton() ?? first(".tab-network"),
    done: () => !!first(".n3d canvas"),
    async show(live) {
      first(".tab-network")?.click();
      const three = await until(threeButton);
      await sleep(350);
      if (live()) three?.click();
    },
  },
  {},
];

export function openTour() {
  current.value = 0;
}

function remember() {
  try {
    visitorStorage?.setItem(SEEN, "1");
  } catch {}
}

function closeTour() {
  remember();
  current.value = null;
}

/** First visit only: everyone else has the button in the bar. */
export function greetOnce() {
  try {
    if (visitorStorage?.getItem(SEEN)) return;
  } catch {}
  if (current.value === null) openTour();
}

/** Brings a target between the header and the card, without moving a page that already shows it. */
function reveal(el: HTMLElement, card: HTMLElement | null) {
  if (el.closest(".backdrop")) return;
  const top = (document.querySelector(".dash-head, .topbar")?.getBoundingClientRect().bottom ?? 0) + 8;
  const bottom = (card?.getBoundingClientRect().top ?? innerHeight) - 8;
  const r = el.getBoundingClientRect();
  if (r.top >= top && r.bottom <= bottom) return;
  window.scrollBy({ top: r.top + r.height / 2 - (top + bottom) / 2, behavior: calm() ? "auto" : "smooth" });
}

function Tour() {
  const index = current.value;
  const card = useRef<HTMLElement>(null);
  const ring = useRef<HTMLDivElement>(null);
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);
  const step = index === null ? undefined : STEPS[index];

  useEffect(() => {
    if (index === null || !step) return;
    setDone(false);
    setBusy(false);
    void step.enter?.();
    remember();
    let frame = 0;
    let checked = 0;
    let target: HTMLElement | null = null;
    const loop = (now: number) => {
      frame = requestAnimationFrame(loop);
      if (now - checked > 160) {
        checked = now;
        const next = step.target?.() ?? null;
        if (next !== target && next) reveal(next, card.current);
        target = next;
        setDone(!!step.done?.());
      }
      const el = ring.current;
      if (!el) return;
      const layer = document.querySelector(".backdrop");
      const r = target?.isConnected && (!layer || layer.contains(target)) ? target.getBoundingClientRect() : null;
      if (!r?.width) {
        el.style.opacity = "0";
        return;
      }
      el.style.opacity = "1";
      el.style.width = `${r.width + 8}px`;
      el.style.height = `${r.height + 8}px`;
      el.style.transform = `translate(${r.left - 4}px, ${r.top - 4}px)`;
    };
    frame = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(frame);
  }, [index]);

  useEffect(() => {
    if (index === null) return;
    const key = (e: KeyboardEvent) => {
      if (e.key === "Escape" && !document.querySelector(".backdrop")) closeTour();
    };
    // capture: a sheet's own Esc must not look like "nothing open" by the time this runs
    window.addEventListener("keydown", key, true);
    return () => window.removeEventListener("keydown", key, true);
  }, [index === null]);

  if (index === null || !step) return null;
  const text = s.steps[index];
  const last = index === STEPS.length - 1;
  const show = async () => {
    setBusy(true);
    await step.show?.(() => current.value === index).catch(() => {});
    setBusy(false);
  };
  return (
    <>
      <div class="demo-ring" ref={ring} aria-hidden="true" />
      <section class="demo-tour" ref={card} aria-label={s.tourLabel}>
        <div class="demo-tour-top">
          {STEPS.map((_, i) => (
            <span key={i} class={`demo-pip ${i === index ? "on" : i < index ? "done" : ""}`} aria-hidden="true" />
          ))}
          {!last && (
            <button type="button" class="demo-skip" onClick={closeTour}>
              {s.tourSkip}
            </button>
          )}
        </div>
        <div aria-live="polite">
          <h2 class="px-title">{text.title}</h2>
          <p>{text.body}</p>
          {done && text.ok && <p class="demo-ok">{text.ok}</p>}
        </div>
        <div class="demo-tour-actions">
          {index > 0 && !last && (
            <button type="button" class="demo-tool demo-back" onClick={() => (current.value = index - 1)}>
              {s.tourBack}
            </button>
          )}
          {step.show && !done && (
            <button type="button" class="demo-tool" disabled={busy} onClick={show}>
              {s.tourShow}
            </button>
          )}
          {last ? (
            <>
              <button type="button" class="demo-tool" onClick={closeTour}>
                {s.tourFinish}
              </button>
              <a class="demo-tool demo-get" href={`/${lang}/download/`}>
                {s.tourDownload}
              </a>
            </>
          ) : (
            <button type="button" class={`demo-tool ${done ? "demo-get" : ""}`} onClick={() => (current.value = index + 1)}>
              {s.tourNext}
            </button>
          )}
        </div>
      </section>
    </>
  );
}

export function mountTour() {
  const host = document.createElement("div");
  document.body.append(host);
  render(<Tour />, host);
  // what the base reports is the proof that a step worked
  effect(() => {
    for (const t of toasts.value) {
      if (/^(Crafting|Ordered) /.test(t.title)) crafted = true;
      if (t.text.startsWith("SCRAM triggered")) scrammed = true;
    }
  });
}

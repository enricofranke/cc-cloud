// The guided tour: a small card of the dashboard that walks through what the product is for. It watches the real screens
// to see what the visitor did, and "Show me" does the step with the real controls. Nothing here talks to the simulation.
import { effect, signal } from "@preact/signals";
import { render } from "preact";
import { useEffect, useRef, useState } from "preact/hooks";
import { navigate, toasts } from "../../web/src/lib/ui.ts";
import { placeCard, type Rect, type Side } from "./place.ts";
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
  /** more of the screen the step needs: the card keeps clear of it too */
  avoid?: string;
  /** the sides of the target to try for the card, best first */
  sides?: Side[];
  /** pixels below the target the step needs to show as well */
  room?: number;
  done?(): boolean;
  /** Once, the moment done() turns true. */
  arrive?(): Promise<void>;
  show?(live: () => boolean): Promise<void>;
}

const craftButton = () => first(".backdrop .pxbtn-gold, .backdrop .btn-wide", hasText(/^(Craft|Order anyway)/i));
const cpusButton = () => first(".store-quick .btn", hasText(/^CPUs$/));
const craftableRow = (name = /./) => first(".item-row", (el) => !!el.querySelector(".craft-badge") && name.test(el.textContent ?? ""));
const scramButton = () => first(".pxbtn-red", hasText(/scram/i));
const threeButton = () => first(".seg-ctl button", hasText(/^3D$/));
/** what the 3D view needs to stay readable: its legend, the zoom buttons and the details of the picked block */
const MAP_PARTS = ".net-legend, .pz-controls, .net-panel";

const STEPS: Step[] = [
  {
    enter: overview,
    target: () => first(".search-bar"),
    // the results show up below the search field: room for two rows
    sides: ["above", "right", "left"],
    room: 130,
    done: () => !!first(".item-row"),
    async show(live) {
      const input = first(".search-input") as HTMLInputElement | null;
      if (input) await type(input, "iron", live);
    },
  },
  {
    enter: overview,
    target: () => craftButton() ?? (crafted ? (cpusButton() ?? first(".search-clear")) : (craftableRow() ?? first(".search-bar"))),
    // the visitor searches first, then picks a row
    avoid: ".search-bar",
    sides: ["right", "left", "below", "above"],
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
    // the editor's bar and its floating toolbox are what the visitor works with next
    avoid: ".ed-bar, .ed-side",
    sides: ["below", "left"],
    done: () => !!first('button[aria-label="Leave layout editor"]'),
    async show() {
      first('button[aria-label="Edit layout"]')?.click();
    },
  },
  {
    enter: leaveEditor,
    target: () => threeButton() ?? first(".tab-network"),
    avoid: MAP_PARTS,
    sides: ["below", "right", "above", "left"],
    done: () => !!first(".n3d canvas"),
    // the 3D view opens high above the world: the first look belongs to the base
    async arrive() {
      const input = await until(() => first(".fly-input") as HTMLInputElement | null);
      if (!input) return;
      await type(input, "Mainframe", () => true);
      await sleep(120);
      input.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
      input.blur();
      // on a phone the device's details open as a sheet, right over what the visitor just arrived at
      if (await until(() => first(".backdrop-sheet"), 900)) document.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    },
    async show(live) {
      first(".tab-network")?.click();
      const three = await until(threeButton);
      await sleep(350);
      if (live()) three?.click();
    },
  },
  // the end: the 3D view is still open behind it
  { avoid: MAP_PARTS },
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

const box = (r: DOMRect): Rect => ({ x: r.left, y: r.top, w: r.width, h: r.height });

/** The part of the screen the card may use: below the sticky header, above a phone's tab bar, inside the margins. */
function freeArea(): Rect {
  const width = document.documentElement.clientWidth;
  const margin = width < 720 ? 8 : 16;
  const top = Math.max(document.getElementById("demo-bar")?.getBoundingClientRect().bottom ?? 0, document.querySelector(".dash-head, .topbar")?.getBoundingClientRect().bottom ?? 0) + 8;
  const tabbar = first(".tabbar");
  const bottom = tabbar ? tabbar.getBoundingClientRect().top - 4 : innerHeight - margin;
  return { x: margin, y: top, w: width - 2 * margin, h: Math.max(0, bottom - top) };
}

/** Targets in a sheet, a sticky header or the tab bar do not move when the page scrolls. */
const scrolls = (el: HTMLElement) => !el.closest(".backdrop, .dash-head, .topbar, .tabbar, #demo-bar, .ed-bar, .ed-side") && document.documentElement.scrollHeight > innerHeight;

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
    let arrived = false;
    // where the card goes: its side sticks while it works. The page only scrolls for a new target or a card that
    // changed its size (or when nothing else keeps the target free), and never right after the visitor scrolled.
    let side: Side | undefined;
    let planned: { el: HTMLElement | null; h: number; at: number } = { el: null, h: 0, at: 0 };
    let hold = 0;
    let touched = 0;
    const touch = () => (touched = performance.now());
    const INPUT = ["wheel", "touchmove", "keydown"] as const;
    for (const ev of INPUT) window.addEventListener(ev, touch, { passive: true });
    const put = (c: HTMLElement, x: number, y: number) => {
      c.style.left = `${Math.round(x)}px`;
      c.style.top = `${Math.round(y)}px`;
      c.style.right = c.style.bottom = "auto";
    };
    const loop = (now: number) => {
      frame = requestAnimationFrame(loop);
      if (now - checked > 160) {
        checked = now;
        target = step.target?.() ?? null;
        const ok = !!step.done?.();
        if (ok && !arrived) {
          arrived = true;
          void step.arrive?.();
        }
        setDone(ok);
      }
      const layer = document.querySelector(".backdrop");
      const r = target?.isConnected && (!layer || layer.contains(target)) ? target.getBoundingClientRect() : null;
      const lit = !!r?.width;
      const el = ring.current;
      if (el && !lit) el.style.opacity = "0";
      else if (el && r) {
        el.style.opacity = "1";
        el.style.width = `${r.width + 8}px`;
        el.style.height = `${r.height + 8}px`;
        el.style.transform = `translate(${r.left - 4}px, ${r.top - 4}px)`;
      }
      const c = card.current;
      // a target behind a sheet or still loading: the card stays where it is; while the page scrolls for it, too
      if (!c || (step.target && !lit) || now < hold) return;
      const avoid = step.avoid ? [...document.querySelectorAll<HTMLElement>(step.avoid)].filter(shown).map((e) => box(e.getBoundingClientRect())) : [];
      const input = { card: { w: c.offsetWidth, h: c.offsetHeight }, view: freeArea(), target: r && lit ? box(r) : null, avoid, sides: step.sides, room: step.room, last: side };
      let p = placeCard(input);
      const fresh = planned.el !== target || Math.abs(planned.h - input.card.h) > 8;
      if (target && lit && (fresh || (!p.clear && now - planned.at > 1000)) && now - touched > 1200 && scrolls(target)) {
        planned = { el: target, h: input.card.h, at: now };
        const q = placeCard({ ...input, scrollable: true });
        if (q.scroll) {
          window.scrollBy({ top: q.scroll, behavior: calm() ? "auto" : "smooth" });
          // where the card ends up once the target arrives
          p = q;
          hold = now + (calm() ? 0 : 650);
        }
      }
      side = p.side;
      put(c, p.x, p.y);
    };
    frame = requestAnimationFrame(loop);
    return () => {
      cancelAnimationFrame(frame);
      for (const ev of INPUT) window.removeEventListener(ev, touch);
    };
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
      <section class={`demo-tour ${done && text.ok ? "is-done" : ""}`} ref={card} aria-label={s.tourLabel}>
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
          <p class="demo-tour-body">{text.body}</p>
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

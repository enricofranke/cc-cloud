// Lazy chunk: the browser terminal of the computer behind a card (v0.5 E) – live picture,
// keyboard input like standing in front of it, power controls and paste.
import { useEffect, useRef, useState } from "preact/hooks";
import type { MainframeConn } from "../lib/conn.ts";
import { colourRuns, glfwKey, isPrintable, pasteText, type TermEvent, type TermState } from "../lib/terminal.ts";
import { PinDialog, Sheet, type PinState } from "../components/Overlays.tsx";
import { DocsLink } from "../components/DocsLink.tsx";

const ERR: Record<string, string> = {
  not_allowed: "You need the “Terminal” permission on this mainframe.",
  read_only: "This device is read-only.",
  unknown_computer: "There is no computer behind this card (or its chunk is not loaded).",
  disabled: "The server admin switched the browser terminal off.",
  too_many: "Too many terminals open – close another one first.",
  rate_limited: "Too many requests – wait a moment.",
  unsupported: "This computer cannot be shown in the browser.",
  forbidden: "You lost the permission for this terminal.",
  disconnected: "Not connected – trying again…",
  timeout: "No answer from the server.",
};

export default function TerminalSheet({ conn, uplinkId, title, onClose }: { conn: MainframeConn; uplinkId: string; title: string; onClose: () => void }) {
  const [error, setError] = useState<string | null>(null);
  const [pinAsk, setPinAsk] = useState<PinState | null>(null);
  const pin = useRef<string | undefined>(undefined);
  const screen = useRef<HTMLDivElement>(null);
  const [typed, setTyped] = useState("");
  const status = conn.status.value;
  const term: TermState | undefined = conn.terminals.value[uplinkId];
  const closed = conn.termClosed.value[uplinkId];

  const open = async () => {
    setError(null);
    try {
      await conn.termOpen(uplinkId, pin.current);
      setPinAsk(null);
      screen.current?.focus();
    } catch (e) {
      const x = e as Error & { attemptsLeft?: number; retryAfter?: number };
      if (x.message === "pin_required") setPinAsk({ kind: "idle" });
      else if (x.message === "wrong_pin") {
        pin.current = undefined;
        setPinAsk({ kind: "error", text: `Wrong PIN${x.attemptsLeft !== undefined ? ` – ${x.attemptsLeft} attempts left` : ""}.` });
      } else if (x.message === "pin_locked") {
        pin.current = undefined;
        setPinAsk(null);
        setError(`Too many wrong PINs – locked${x.retryAfter ? ` for ${Math.ceil(x.retryAfter / 60)} min` : ""}.`);
      } else setError(ERR[x.message] ?? x.message);
    }
  };
  // (re)open on mount and after every reconnect; close when leaving
  useEffect(() => {
    if (status === "open") open();
  }, [status]);
  useEffect(() => () => conn.termClose(uplinkId), [uplinkId]);

  const send = (events: TermEvent[]) => conn.termInput(uplinkId, events);
  const onKey = (e: KeyboardEvent, up: boolean) => {
    if (!term?.on) return;
    // let the browser keep its own shortcuts for copy and the sheet's Esc
    if ((e.ctrlKey || e.metaKey) && ["c", "v"].includes(e.key.toLowerCase()) && !up) return;
    if (e.key === "Escape" && !up) return;
    const key = glfwKey(e.code);
    if (key === null && !isPrintable(e.key)) return;
    e.preventDefault();
    e.stopPropagation();
    if (up) return key !== null && send([{ type: "key_up", key }]);
    const events: TermEvent[] = [];
    if (key !== null) events.push({ type: "key", key, held: e.repeat });
    if (isPrintable(e.key) && !e.ctrlKey && !e.metaKey && !e.altKey) events.push({ type: "char", c: e.key });
    send(events);
  };
  const cellOf = (e: MouseEvent) => {
    const r = screen.current!.getBoundingClientRect();
    return { x: Math.max(1, Math.min(term!.w, Math.floor(((e.clientX - r.left) / r.width) * term!.w) + 1)), y: Math.max(1, Math.min(term!.h, Math.floor(((e.clientY - r.top) / r.height) * term!.h) + 1)) };
  };
  const pal = term?.palette ?? [];
  const colour = term?.colour !== false;
  return (
    <Sheet title={`${title} – terminal`} onClose={onClose} wide>
      <div class="term-wrap">
        <div class="term-bar">
          <span class={`led ${term?.on ? "led-on" : "led-off"}`} aria-hidden="true" />
          <span class="small muted">{!term ? "Connecting…" : term.on ? `${term.w}×${term.h}${colour ? " · colour" : ""}` : "Computer is off"}</span>
          <span class="perm-spacer" />
          {term && !term.on && (
            <button type="button" class="btn btn-small" onClick={() => send([{ type: "turn_on" }])}>
              Turn on
            </button>
          )}
          {term?.on && (
            <>
              <button type="button" class="btn btn-small" title="Like holding Ctrl+T" onClick={() => send([{ type: "terminate" }])}>
                Terminate
              </button>
              <button type="button" class="btn btn-small" onClick={() => confirm("Reboot this computer?") && send([{ type: "reboot" }])}>
                Reboot
              </button>
              <button type="button" class="btn btn-small btn-danger-ghost" onClick={() => confirm("Shut this computer down?") && send([{ type: "shutdown" }])}>
                Shut down
              </button>
            </>
          )}
        </div>
        {(error || closed) && (
          <p class="form-error" role="alert">
            {error ?? ERR[closed!] ?? closed}{" "}
            <button type="button" class="link-btn" onClick={open}>
              Try again
            </button>
          </p>
        )}
        <div
          ref={screen}
          class={`term-screen ${term?.on ? "" : "off"} ${colour ? "" : "mono"}`}
          tabIndex={0}
          role="application"
          aria-roledescription="terminal"
          aria-label={`Terminal of ${title}. Click and type; Esc leaves.`}
          onKeyDown={(e) => onKey(e, false)}
          onKeyUp={(e) => onKey(e, true)}
          onPaste={(e) => {
            const t = pasteText(e.clipboardData?.getData("text") ?? "");
            if (t) {
              e.preventDefault();
              send([{ type: "paste", text: t }]);
            }
          }}
          onMouseDown={(e) => term?.on && colour && (e.currentTarget.focus(), send([{ type: "mouse_click", button: e.button === 2 ? 2 : e.button === 1 ? 3 : 1, ...cellOf(e) }]))}
          onMouseUp={(e) => term?.on && colour && send([{ type: "mouse_up", button: e.button === 2 ? 2 : e.button === 1 ? 3 : 1, ...cellOf(e) }])}
          onWheel={(e) => term?.on && colour && (e.preventDefault(), send([{ type: "mouse_scroll", dir: e.deltaY > 0 ? 1 : -1, ...cellOf(e) }]))}
          onContextMenu={(e) => e.preventDefault()}
        >
          {term?.on &&
            term.text.map((row, y) => (
              <div key={y} class="term-row">
                {colourRuns(row, term.fg[y], term.bg[y]).map((r, i) => (
                  <span
                    key={i}
                    ref={(el) => {
                      if (!el) return;
                      el.style.color = pal[parseInt(r.fg, 16)] ?? "#f0f0f0";
                      el.style.background = pal[parseInt(r.bg, 16)] ?? "#111111";
                    }}
                  >
                    {r.text}
                  </span>
                ))}
                {term.cursor.blink && term.cursor.y === y + 1 && (
                  <span
                    class="term-cursor"
                    aria-hidden="true"
                    ref={(el) => {
                      if (el) el.style.left = `${((term.cursor.x - 1) / term.w) * 100}%`;
                    }}
                  />
                )}
              </div>
            ))}
          {term && !term.on && <div class="term-offline">The computer is off.</div>}
        </div>
        {/* phones: the soft keyboard needs a real input field */}
        <form
          class="term-type"
          onSubmit={(e) => {
            e.preventDefault();
            const t = pasteText(typed);
            send([...(t ? [{ type: "paste" as const, text: t }] : []), { type: "key", key: 257, held: false }]);
            setTyped("");
          }}
        >
          <input class="input mono" value={typed} placeholder="Type a command, Enter sends it" aria-label="Type into the terminal" autoCapitalize="off" autoCorrect="off" spellcheck={false} onInput={(e) => setTyped((e.target as HTMLInputElement).value)} />
          <button type="submit" class="btn" disabled={!term?.on}>
            Enter
          </button>
          <button type="button" class="btn" disabled={!term?.on} onClick={() => send([{ type: "key", key: 259, held: false }])} aria-label="Backspace">
            ⌫
          </button>
        </form>
        <p class="muted small">
          Click the screen and type – everything goes to the computer, like standing in front of it. Opening the terminal is logged. <DocsLink to="/docs/automation#terminal">More</DocsLink>
        </p>
      </div>
      {pinAsk && (
        <PinDialog
          title="Mainframe PIN"
          text="The terminal needs the mainframe PIN."
          color="gold"
          state={pinAsk}
          onCancel={() => setPinAsk(null)}
          onSubmit={(p) => {
            pin.current = p;
            setPinAsk({ kind: "checking" });
            open();
          }}
        />
      )}
    </Sheet>
  );
}

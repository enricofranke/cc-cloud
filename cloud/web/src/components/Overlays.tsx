import { useEffect, useRef, useState } from "preact/hooks";
import type { ComponentChildren } from "preact";
import { dismissToast, toasts } from "../lib/ui.ts";
import { IconAlert, IconClose, IconInfo } from "./Icons.tsx";
import { canSubmit, knownLength, pinKey, pinPaste, pinSlots, pinStatus, type PinStep } from "../lib/pin.ts";

function useEscape(onClose: () => void) {
  useEffect(() => {
    const h = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    addEventListener("keydown", h);
    return () => removeEventListener("keydown", h);
  }, [onClose]);
}

export function ConfirmDialog(props: {
  title: string;
  text: string;
  confirmLabel: string;
  color: string;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  useEscape(props.onCancel);
  const cancelRef = useRef<HTMLButtonElement>(null);
  useEffect(() => cancelRef.current?.focus(), []);
  return (
    <div class="backdrop" onClick={(e) => e.target === e.currentTarget && props.onCancel()}>
      <div class="dialog" role="alertdialog" aria-modal="true" aria-labelledby="dlg-title">
        <div class={`dialog-stripe stripe-${props.color}`} />
        <h2 id="dlg-title" class="dialog-title">{props.title}</h2>
        <p class="dialog-text">{props.text}</p>
        <div class="dialog-actions">
          <button ref={cancelRef} type="button" class="btn btn-ghost" onClick={props.onCancel}>
            Cancel
          </button>
          <button type="button" class={`pxbtn pxbtn-${props.color}`} onClick={props.onConfirm}>
            <span>{props.confirmLabel}</span>
          </button>
        </div>
      </div>
    </div>
  );
}

/** Keyboard: focus the dialog on open, keep Tab inside it, give focus back on close. */
export function useFocusTrap(ref: { current: HTMLElement | null }) {
  useEffect(() => {
    const before = document.activeElement as HTMLElement | null;
    const el = ref.current;
    const focusables = () =>
      [...(el?.querySelectorAll<HTMLElement>('button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea, [tabindex]:not([tabindex="-1"])') ?? [])];
    const first = focusables().find((x) => !x.closest(".sheet-head")) ?? focusables()[0];
    if (el && !el.contains(document.activeElement)) (first ?? el).focus();
    const trap = (e: KeyboardEvent) => {
      if (e.key !== "Tab" || !el) return;
      const list = focusables();
      if (!list.length) return;
      const i = list.indexOf(document.activeElement as HTMLElement);
      if (e.shiftKey && (i <= 0 || i === -1)) {
        e.preventDefault();
        list[list.length - 1].focus();
      } else if (!e.shiftKey && i === list.length - 1) {
        e.preventDefault();
        list[0].focus();
      }
    };
    addEventListener("keydown", trap);
    return () => {
      removeEventListener("keydown", trap);
      if (before && document.contains(before)) before.focus();
    };
  }, []);
}

/** `tall`: a sheet with a live filter keeps its full height, so its top edge does not jump while results change. */
/** One rule for every sheet: X, Esc and the backdrop leave the whole flow, {@code onBack} (shown as ←) goes one step back. */
export function Sheet(props: { title: string; onClose: () => void; onBack?: () => void; children: ComponentChildren; wide?: boolean; tall?: boolean }) {
  useEscape(props.onClose);
  const ref = useRef<HTMLElement>(null);
  useFocusTrap(ref);
  return (
    <div class="backdrop backdrop-sheet" onClick={(e) => e.target === e.currentTarget && props.onClose()}>
      <section ref={ref} class={`sheet ${props.wide ? "sheet-wide" : ""} ${props.tall ? "sheet-tall" : ""}`} role="dialog" aria-modal="true" aria-label={props.title} tabIndex={-1}>
        <header class="sheet-head">
          {props.onBack && (
            <button type="button" class="icon-btn sheet-back" aria-label="Back" title="Back" onClick={props.onBack}>
              ←
            </button>
          )}
          <h2 class="px-title">{props.title}</h2>
          <button type="button" class="icon-btn" aria-label="Close" onClick={props.onClose}>
            <IconClose />
          </button>
        </header>
        <div class="sheet-body">{props.children}</div>
      </section>
    </div>
  );
}

export function Toasts() {
  return (
    <div class="toasts" aria-live="polite">
      {toasts.value.map((t) => (
        <div key={t.id} class={`toast toast-${t.level}`} role={t.level === "crit" ? "alert" : "status"}>
          <span class="toast-icon">{t.level === "info" ? <IconInfo size={18} /> : <IconAlert size={18} />}</span>
          <div class="toast-body">
            <div class="toast-title">{t.title}</div>
            <div class="toast-text">{t.text}</div>
          </div>
          {t.action && (
            <button
              type="button"
              class="btn btn-small"
              onClick={() => {
                t.action!.run();
                dismissToast(t.id);
              }}
            >
              {t.action.label}
            </button>
          )}
          <button type="button" class="icon-btn toast-close" aria-label="Dismiss" onClick={() => dismissToast(t.id)}>
            <IconClose size={16} />
          </button>
        </div>
      ))}
    </div>
  );
}

export type PinState = { kind: "idle" } | { kind: "checking" } | { kind: "error"; text: string } | { kind: "locked"; text: string };

/**
 * Numeric keypad for the mainframe PIN (lib/pin.ts has the rules). `length` (conn.pinLength): with the PIN's 4–8 digits
 * the dialog shows exactly that many slots and sends as soon as the last one is filled; without it the field grows with
 * the input and OK sends. Digits, Backspace, Enter and paste work from the keyboard too.
 */
export function PinDialog(props: {
  title: string;
  text: string;
  color: string;
  state: PinState;
  /** digits of the mainframe PIN; 0 or undefined = not known */
  length?: number;
  onCancel: () => void;
  onSubmit: (pin: string) => void;
}) {
  const [pin, setPin] = useState("");
  const [errors, setErrors] = useState(0);
  const { state } = props;
  const length = knownLength(props.length);
  const blocked = state.kind === "checking" || state.kind === "locked";
  useEscape(props.onCancel);
  useEffect(() => {
    if (state.kind !== "error") return;
    setPin("");
    setErrors((n) => n + 1);
  }, [state]);
  const step = (next: PinStep) => {
    if (blocked) return;
    setPin(next.pin);
    if (next.submit) props.onSubmit(next.pin);
  };
  const key = (k: string) => step(pinKey(pin, k, length));
  useEffect(() => {
    const down = (e: KeyboardEvent) => {
      if (e.ctrlKey || e.metaKey || e.altKey || e.repeat) return;
      // Enter on a focused button presses that button (Tab to Cancel, a keypad key); elsewhere it sends
      if (e.key === "Enter" && document.activeElement instanceof HTMLButtonElement) return;
      if (/^\d$/.test(e.key) || e.key === "Backspace" || e.key === "Enter") {
        e.preventDefault();
        key(e.key);
      }
    };
    const paste = (e: ClipboardEvent) => {
      const text = e.clipboardData?.getData("text") ?? "";
      if (!/\d/.test(text)) return;
      e.preventDefault();
      step(pinPaste(pin, text, length));
    };
    addEventListener("keydown", down);
    addEventListener("paste", paste);
    return () => {
      removeEventListener("keydown", down);
      removeEventListener("paste", paste);
    };
  });

  const slots = pinSlots(length, pin.length);
  // red until the next digit is typed
  const look = state.kind === "error" && !pin ? "is-error" : state.kind === "checking" ? "is-checking" : state.kind === "locked" ? "is-locked" : "";
  return (
    <div class="backdrop" onClick={(e) => e.target === e.currentTarget && props.onCancel()}>
      <div class={`dialog pin-dialog accent-${props.color}`} role="dialog" aria-modal="true" aria-labelledby="pin-title">
        <div class={`dialog-stripe stripe-${props.color}`} />
        <h2 id="pin-title" class="dialog-title">{props.title}</h2>
        <p class="dialog-text">{props.text}</p>
        <div
          key={errors}
          class={`pin-slots ${length === undefined ? "grow" : "fixed"} ${look} ${errors && state.kind === "error" ? "shake" : ""}`}
          style={{ "--n": String(length ?? Math.max(1, pin.length)) }}
          role="img"
          aria-label={pinStatus(length, pin.length)}
        >
          {slots.map((s, i) => (
            <span key={i} class={`pin-slot ${s}`} aria-hidden="true" />
          ))}
          {length === undefined && !blocked && <span class="pin-caret" aria-hidden="true" />}
        </div>
        <p class={`pin-msg ${state.kind === "error" ? "err" : state.kind === "locked" ? "locked" : ""}`} role="alert">
          {state.kind === "error" || state.kind === "locked"
            ? state.text
            : state.kind === "checking"
              ? "Checking…"
              : length
                ? `Enter the ${length}-digit mainframe PIN`
                : "Enter the mainframe PIN, then OK"}
        </p>
        <div class="keypad">
          {["1", "2", "3", "4", "5", "6", "7", "8", "9"].map((d) => (
            <button key={d} type="button" class="key" disabled={blocked} onClick={() => key(d)}>
              {d}
            </button>
          ))}
          {length === undefined ? (
            <button type="button" class="key key-fn" disabled={blocked || !pin} onClick={() => key("Backspace")} aria-label="Delete digit">
              ⌫
            </button>
          ) : (
            <span class="key-spacer" aria-hidden="true" />
          )}
          <button type="button" class="key" disabled={blocked} onClick={() => key("0")}>
            0
          </button>
          {length === undefined ? (
            <button type="button" class="key key-ok" disabled={blocked || !canSubmit(pin, length)} onClick={() => key("Enter")} aria-label="Send">
              OK
            </button>
          ) : (
            <button type="button" class="key key-fn" disabled={blocked || !pin} onClick={() => key("Backspace")} aria-label="Delete digit">
              ⌫
            </button>
          )}
        </div>
        <button type="button" class="btn btn-ghost pin-cancel" onClick={props.onCancel}>
          {state.kind === "locked" ? "Close" : "Cancel"}
        </button>
      </div>
    </div>
  );
}

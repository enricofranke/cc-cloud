import { useEffect, useRef, useState } from "preact/hooks";
import type { ComponentChildren } from "preact";
import { dismissToast, toasts } from "../lib/ui.ts";
import { IconAlert, IconClose, IconInfo } from "./Icons.tsx";

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

export function Sheet(props: { title: string; onClose: () => void; children: ComponentChildren }) {
  useEscape(props.onClose);
  return (
    <div class="backdrop backdrop-sheet" onClick={(e) => e.target === e.currentTarget && props.onClose()}>
      <section class="sheet" role="dialog" aria-modal="true" aria-label={props.title}>
        <header class="sheet-head">
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
          <button type="button" class="icon-btn toast-close" aria-label="Dismiss" onClick={() => dismissToast(t.id)}>
            <IconClose size={16} />
          </button>
        </div>
      ))}
    </div>
  );
}

export type PinState = { kind: "idle" } | { kind: "checking" } | { kind: "error"; text: string } | { kind: "locked"; text: string };

/** Numeric keypad for PIN-protected widgets (opts.pin). 4–8 digits. */
export function PinDialog(props: {
  title: string;
  text: string;
  color: string;
  state: PinState;
  onCancel: () => void;
  onSubmit: (pin: string) => void;
}) {
  const [pin, setPin] = useState("");
  const { state } = props;
  const blocked = state.kind === "checking" || state.kind === "locked";
  useEscape(props.onCancel);
  useEffect(() => {
    if (state.kind === "error") setPin("");
  }, [state]);
  const press = (d: string) => !blocked && setPin((p) => (p.length < 8 ? p + d : p));
  const back = () => !blocked && setPin((p) => p.slice(0, -1));
  const submit = () => !blocked && pin.length >= 4 && props.onSubmit(pin);
  useEffect(() => {
    const h = (e: KeyboardEvent) => {
      if (/^\d$/.test(e.key)) press(e.key);
      else if (e.key === "Backspace") back();
      else if (e.key === "Enter") submit();
    };
    addEventListener("keydown", h);
    return () => removeEventListener("keydown", h);
  });

  return (
    <div class="backdrop" onClick={(e) => e.target === e.currentTarget && props.onCancel()}>
      <div class={`dialog pin-dialog ${state.kind === "error" ? "shake" : ""}`} role="dialog" aria-modal="true" aria-labelledby="pin-title">
        <div class={`dialog-stripe stripe-${props.color}`} />
        <h2 id="pin-title" class="dialog-title">{props.title}</h2>
        <p class="dialog-text">{props.text}</p>
        <div class="pin-dots" aria-label={`${pin.length} digits entered`}>
          {Array.from({ length: Math.max(4, Math.min(8, pin.length + (pin.length >= 4 ? 1 : 0))) }, (_, i) => (
            <span key={i} class={`pin-dot ${i < pin.length ? "filled" : ""}`} />
          ))}
        </div>
        <p class={`pin-msg ${state.kind === "error" ? "err" : state.kind === "locked" ? "locked" : ""}`} role="alert">
          {state.kind === "error" || state.kind === "locked" ? state.text : state.kind === "checking" ? "Checking…" : "Enter the mainframe PIN"}
        </p>
        <div class="keypad">
          {["1", "2", "3", "4", "5", "6", "7", "8", "9"].map((d) => (
            <button key={d} type="button" class="key" disabled={blocked} onClick={() => press(d)}>
              {d}
            </button>
          ))}
          <button type="button" class="key key-fn" disabled={blocked || !pin} onClick={back} aria-label="Delete digit">
            ⌫
          </button>
          <button type="button" class="key" disabled={blocked} onClick={() => press("0")}>
            0
          </button>
          <button type="button" class="key key-ok" disabled={blocked || pin.length < 4} onClick={submit} aria-label="Send">
            OK
          </button>
        </div>
        <button type="button" class="btn btn-ghost pin-cancel" onClick={props.onCancel}>
          {state.kind === "locked" ? "Close" : "Cancel"}
        </button>
      </div>
    </div>
  );
}

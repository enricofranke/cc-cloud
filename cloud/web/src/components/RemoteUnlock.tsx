// Plain-HTTP internet access (internetAccess = http, docs "Internet access in 2 minutes"): writes from an internet
// address need the mainframe PIN. The server refuses them with remote_locked; this dialog sends the PIN once
// ({"t":"unlock"}) and the controls work for the next 10 minutes. remote_no_pin: the mainframe has no PIN at all.
import { useEffect, useState } from "preact/hooks";
import type { MainframeConn } from "../lib/conn.ts";
import { formatDuration, rejectText } from "../lib/conn.ts";
import { pushToast } from "../lib/ui.ts";
import { PinDialog, type PinState } from "./Overlays.tsx";

export function RemoteUnlock({ conn }: { conn: MainframeConn }) {
  const lock = conn.remoteLock.value;
  const [state, setState] = useState<PinState>({ kind: "idle" });
  useEffect(() => {
    if (lock === "no_pin") {
      pushToast({ level: "warn", title: "No PIN on this mainframe", text: rejectText({ ok: false, error: "remote_no_pin" }) });
      conn.remoteLock.value = null;
    }
    if (lock === "locked") setState({ kind: "idle" });
  }, [lock]);
  if (lock !== "locked") return null;

  const submit = async (pin: string) => {
    setState({ kind: "checking" });
    const r = await conn.unlock(pin);
    if (r.ok) {
      conn.remoteLock.value = null;
      pushToast({
        level: "info",
        title: "Controls unlocked",
        text: r.seconds ? `For ${formatDuration(r.seconds)} from this browser – try your action again.` : "Try your action again.",
      });
      return;
    }
    if (r.error === "remote_no_pin") {
      conn.remoteLock.value = "no_pin";
      return;
    }
    const text = rejectText({ ok: false, error: r.error, attemptsLeft: r.attemptsLeft, retryAfter: r.retryAfter });
    setState(r.error === "pin_locked" || r.error === "rate_limited" ? { kind: "locked", text } : { kind: "error", text });
  };

  return (
    <PinDialog
      title="Control from the internet"
      text="This dashboard runs over plain HTTP from the internet. Enter the mainframe PIN to use the controls for the next 10 minutes."
      color="red"
      state={state}
      onCancel={() => (conn.remoteLock.value = null)}
      onSubmit={submit}
    />
  );
}

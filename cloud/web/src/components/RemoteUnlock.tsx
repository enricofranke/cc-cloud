// Internet access (internetAccess = http or connect) with internetRequirePin: writes from an internet address need the
// mainframe PIN. The server refuses them with remote_locked; this dialog sends the PIN once
// ({"t":"unlock"}) and the controls work for the next 10 minutes. remote_no_pin: the mainframe has no PIN at all.
import { useEffect, useState } from "preact/hooks";
import type { MainframeConn } from "../lib/conn.ts";
import { formatDuration, rejectText } from "../lib/conn.ts";
import { pushToast } from "../lib/ui.ts";
import { PinDialog, type PinState } from "./Overlays.tsx";
import { publicConfig } from "../lib/config.ts";

export function RemoteUnlock({ conn }: { conn: MainframeConn }) {
  const lock = conn.remoteLock.value;
  const [state, setState] = useState<PinState>({ kind: "idle" });
  useEffect(() => {
    if (lock === "no_pin") {
      pushToast({ level: "warn", title: "Read-only from outside", text: rejectText({ ok: false, error: "remote_no_pin" }) });
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

  // CC: Cloud Connect is HTTPS (through the relay); plain HTTP is the insecure easy mode
  const connect = publicConfig.value.internet.mode === "connect";
  return (
    <PinDialog
      title="Control from the internet"
      text={
        connect
          ? "You are using this dashboard from the internet (CC: Cloud Connect). Enter the mainframe PIN to use the controls for the next 10 minutes."
          : "This dashboard runs over plain HTTP from the internet. Enter the mainframe PIN to use the controls for the next 10 minutes."
      }
      color={connect ? "gold" : "red"}
      state={state}
      onCancel={() => (conn.remoteLock.value = null)}
      onSubmit={submit}
    />
  );
}

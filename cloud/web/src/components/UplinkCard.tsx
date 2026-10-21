import type { MainframeConn } from "../lib/conn.ts";
import type { Uplink } from "../lib/types.ts";
import { ActionButton, Gauge, LabelRow, Slider, Toggle } from "./Widgets.tsx";

/**
 * One card per uplink. Widgets are grouped for a tidy layout regardless of the
 * order the Lua script declared them in: gauges, readouts, controls, buttons.
 */
export function UplinkCard({ uplink, conn, locked }: { uplink: Uplink; conn: MainframeConn; locked: boolean }) {
  const cardLocked = locked || !uplink.online;
  const props = (w: Uplink["widgets"][number]) => ({ key: w.id, w, uplink, conn, locked: cardLocked });
  const gauges = uplink.widgets.filter((w) => w.type === "gauge");
  const labels = uplink.widgets.filter((w) => w.type === "label");
  const controls = uplink.widgets.filter((w) => w.type === "toggle" || w.type === "slider");
  const buttons = uplink.widgets.filter((w) => w.type === "button");

  return (
    <article class={`card ${uplink.online ? "" : "card-offline"}`} aria-label={uplink.title || "Uplink"}>
      <header class="card-head">
        <span class={`led ${uplink.online ? "led-on" : "led-off"}`} aria-hidden="true" />
        <h3 class="card-title">{uplink.title || "Untitled uplink"}</h3>
        {!uplink.online ? <span class="tag tag-red">Uplink offline</span> : <span class="card-count">{uplink.widgets.length}</span>}
      </header>
      {uplink.widgets.length === 0 ? (
        <p class="card-empty">No widgets yet. Use cloud.button(), cloud.gauge() … on the computer next to this uplink.</p>
      ) : (
        <div class="card-body">
          {gauges.length > 0 && <div class={`screen gauges n${Math.min(gauges.length, 3)}`}>{gauges.map((w) => <Gauge {...props(w)} />)}</div>}
          {labels.length > 0 && <div class="readouts">{labels.map((w) => <LabelRow {...props(w)} />)}</div>}
          {controls.length > 0 && (
            <div class="controls">{controls.map((w) => (w.type === "toggle" ? <Toggle {...props(w)} /> : <Slider {...props(w)} />))}</div>
          )}
          {buttons.length > 0 && <div class="buttons">{buttons.map((w) => <ActionButton {...props(w)} />)}</div>}
        </div>
      )}
    </article>
  );
}

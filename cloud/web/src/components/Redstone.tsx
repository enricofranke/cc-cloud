// redstone_sides (v0.3): the six sides of a computer in a compact grid.
// Tap a side to switch its output (0 ↔ 15); the LED shows the input level.
// Analog outputs (0–15) live under "Advanced".
import { useEffect, useState } from "preact/hooks";
import type { MainframeConn } from "../lib/conn.ts";
import { SIDES, type RedstoneRow, type Side, type Uplink, type Widget } from "../lib/types.ts";
import { legacyTarget, redstoneRows } from "../lib/redstone.ts";

/** Grid order that reads like the block: top / front / left · right / back / bottom. */
const GRID: Side[] = ["top", "front", "left", "right", "back", "bottom"];

export function RedstoneSides({ w, uplink, conn, locked }: { w: Widget; uplink: Uplink; conn: MainframeConn; locked: boolean }) {
  const rows = redstoneRows(w);
  const [pending, setPending] = useState<Partial<Record<Side, number>>>({});
  const [advanced, setAdvanced] = useState(false);
  const [drag, setDrag] = useState<Partial<Record<Side, number>>>({});

  // a server value for a side settles its pending state
  useEffect(() => {
    setPending((p) => {
      const next = { ...p };
      for (const r of rows) if (next[r.side] === r.out) delete next[r.side];
      return next;
    });
  }, [JSON.stringify(rows)]);

  const send = (side: Side, value: number) => {
    setPending((p) => ({ ...p, [side]: value }));
    // adapted cards (plain toggles/sliders per side) send to the original widgets
    const legacy = (w.opts as { legacy?: boolean } | undefined)?.legacy === true;
    const raw = conn.uplinks.value.find((x) => x.uplinkId === uplink.uplinkId);
    const t = legacy && raw ? legacyTarget(raw, side, value) : null;
    const target: Widget = t ? { id: t.id, type: t.type, label: `${side} output` } : w;
    const reqId = conn.action(uplink.uplinkId, target, t ? t.value : { op: "set_output", side, value }, false, {
      onResult: (r) => {
        if (!r.ok)
          setPending((p) => {
            const { [side]: _, ...rest } = p;
            return rest;
          });
        return false; // let the default toast explain errors
      },
    });
    if (!reqId)
      setPending((p) => {
        const { [side]: _, ...rest } = p;
        return rest;
      });
  };

  const shown = (r: RedstoneRow) => pending[r.side] ?? r.out;

  return (
    <section class="rs" aria-label={w.label ?? "Redstone"}>
      <div class="rs-head">
        <span class="w2-label">{w.label ?? "Redstone"}</span>
        <span class="rs-legend" aria-hidden="true">
          <span class="rs-led on" /> in <span class="rs-out-key" /> out
        </span>
      </div>
      <div class="rs-grid">
        {GRID.map((side) => {
          const r = rows.find((x) => x.side === side)!;
          const out = shown(r);
          return (
            <button
              key={side}
              type="button"
              class={`rs-side ${out > 0 ? "out-on" : ""} ${side in pending ? "pending" : ""}`}
              aria-pressed={out > 0}
              aria-label={`${side}: output ${out}, input ${r.in}. Tap to switch the output ${out > 0 ? "off" : "on"}.`}
              disabled={locked}
              onClick={() => send(side, out > 0 ? 0 : 15)}
            >
              <span class="rs-name">{side}</span>
              <span class="rs-row">
                <span class={`rs-led ${r.in > 0 ? "on" : ""}`} style={{ "--lvl": String(r.in / 15) }} title={`input ${r.in}`} />
                <span class="rs-in">{r.in}</span>
                <span class="rs-out">{out > 0 ? (out === 15 ? "ON" : out) : "OFF"}</span>
              </span>
            </button>
          );
        })}
      </div>
      <button type="button" class="rs-adv-toggle" aria-expanded={advanced} onClick={() => setAdvanced(!advanced)}>
        {advanced ? "▾" : "▸"} Advanced: analog output (0–15)
      </button>
      {advanced && (
        <div class="rs-adv">
          {SIDES.map((side) => {
            const r = rows.find((x) => x.side === side)!;
            const v = drag[side] ?? shown(r);
            return (
              <label key={side} class="rs-adv-row">
                <span class="rs-adv-name">{side}</span>
                <input
                  type="range"
                  class="range"
                  min={0}
                  max={15}
                  step={1}
                  value={v}
                  disabled={locked}
                  style={{ "--pct": `${(v / 15) * 100}%` }}
                  aria-label={`${side} analog output`}
                  onInput={(e) => setDrag((d) => ({ ...d, [side]: Number((e.target as HTMLInputElement).value) }))}
                  onChange={(e) => {
                    const val = Number((e.target as HTMLInputElement).value);
                    setDrag((d) => {
                      const { [side]: _, ...rest } = d;
                      return rest;
                    });
                    if (val !== shown(r)) send(side, val);
                  }}
                />
                <span class="rs-adv-val">{v}</span>
              </label>
            );
          })}
        </div>
      )}
    </section>
  );
}

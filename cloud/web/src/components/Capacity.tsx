// v0.7 device capacity of a mainframe (upgrade cards): used / limit, fiber weighting, over-capacity count.
import type { NetCapacity } from "../lib/types.ts";

export const fmtCap = (n: number) => (Number.isInteger(n) ? String(n) : n.toFixed(1));

/** "12.5 / 48 devices · 3 by fiber" with a small bar; limit null = unlimited (Quantum Core). */
export function CapacityLine({ c }: { c: NetCapacity }) {
  const pct = c.limit ? Math.min(100, (c.used / c.limit) * 100) : 0;
  const tone = c.over > 0 || (c.limit != null && c.used > c.limit) ? "crit" : c.limit != null && c.used >= c.limit * 0.9 ? "warn" : "ok";
  return (
    <span class={`cap-line ${tone}`}>
      <span>
        {c.limit == null ? (
          <>
            {c.devices} devices · <b>unlimited</b>
          </>
        ) : (
          <>
            <b>{fmtCap(c.used)}</b> / {c.limit} used
            {c.used !== c.devices && <span class="muted"> ({c.devices} devices)</span>}
          </>
        )}
      </span>
      {c.limit != null && (
        <span class="cap-bar" aria-hidden="true">
          <span
            class="cap-fill"
            ref={(el) => {
              if (el) el.style.width = `${pct}%`;
            }}
          />
        </span>
      )}
      {(c.wired > 0 || c.fiber > 0) && (
        <span class="muted small">
          {[c.wired - c.fiber > 0 ? `${c.wired - c.fiber} by CC cable` : "", c.fiber > 0 ? `${c.fiber} by fiber (count half)` : ""].filter(Boolean).join(" · ")}
        </span>
      )}
      {c.over > 0 && <span class="cap-over">{c.over} stopped – over capacity</span>}
    </span>
  );
}

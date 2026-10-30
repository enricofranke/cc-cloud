// Advanced Cloud Sensor activity (sensor_log): armed or not, who is inside right now, today's count and the last
// events with relative time. Read-only; arming and the filters are ordinary widgets on the same card. Player names
// only arrive for roles that may see them – everyone else gets "A player" from the server.
import { useEffect, useState } from "preact/hooks";
import type { Widget } from "../lib/types.ts";
import { PROBLEM_TEXT, eventAge, eventText, parseActivity, rowText } from "../lib/sensor.ts";

export function SensorLog({ w }: { w: Widget }) {
  const a = parseActivity(w.value);
  // relative times ("2m ago") move on without new data
  const [, setTick] = useState(0);
  useEffect(() => {
    const t = setInterval(() => setTick((n) => n + 1), 15_000);
    return () => clearInterval(t);
  }, []);
  if (!a) return <p class="muted small">No data yet.</p>;
  return (
    <section class="sensor" aria-label={w.label ?? "Activity"}>
      <div class="sensor-head">
        <span class="w2-label">{w.label ?? "Activity"}</span>
        <span class={`tag ${a.armed ? "tag-red" : "tag-gray"}`}>{a.armed ? "Armed" : "Disarmed"}</span>
        <span class="sensor-today" title="Entries since midnight (server time)">
          <b>{a.today}</b> today
        </span>
      </div>
      {a.problem && (
        <p class="field-hint warn" role="status">
          {PROBLEM_TEXT[a.problem] ?? a.problem}
        </p>
      )}
      <div class="sensor-inside" aria-label="Inside now">
        {a.inside.length === 0 ? (
          <span class="muted small">Nobody inside</span>
        ) : (
          a.inside.map((r, i) => (
            <span key={i} class={`sensor-chip k-${r.k}`}>
              {rowText(r)}
            </span>
          ))
        )}
        {a.more > 0 && <span class="sensor-chip">+{a.more} more</span>}
      </div>
      {a.events.length > 0 ? (
        <ol class="sensor-events" aria-label="Last events">
          {a.events.map((e, i) => (
            <li key={`${e.t}-${i}`} class={`sensor-event ${e.in ? "in" : "out"} k-${e.k}`}>
              <span class="sensor-dot" aria-hidden="true" />
              <span class="sensor-what">{eventText(e)}</span>
              <time class="sensor-when" dateTime={new Date(e.t).toISOString()} title={new Date(e.t).toLocaleString()}>
                {eventAge(e)}
              </time>
            </li>
          ))}
        </ol>
      ) : (
        <p class="muted small">Nothing came in yet.</p>
      )}
      {a.zone && <p class="muted small sensor-zone">Zone {a.zone}</p>}
    </section>
  );
}

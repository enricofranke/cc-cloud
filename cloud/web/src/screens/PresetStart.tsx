// The start of a dashboard nobody has arranged yet: a ready-made preset for this base, one click away. Its own chunk,
// because every dashboard that has a layout never needs it.
import { useMemo, useState } from "preact/hooks";
import { PixelIcon } from "../components/PixelIcon.tsx";
import type { MainframeConn } from "../lib/conn.ts";
import { lang, t } from "../lib/i18n.ts";
import { emptyLayout, resolveLayout, snapshotLayout } from "../lib/layout.ts";
import { matchPreset, mergeLayout, recommend } from "../lib/presets/expand.ts";
import { textOf } from "../lib/presets/format.ts";
import { setPref } from "../lib/store.ts";
import type { Uplink } from "../lib/types.ts";
import { pushToast } from "../lib/ui.ts";
import { officialPresets } from "../presets/official.ts";

export const dismissKey = (mainframe: string) => `presets.dismissed.${mainframe}`;

export default function PresetStart(props: { conn: MainframeConn; uplinks: Uplink[]; onPage: (id: string) => void; onMore: () => void; onDismiss: () => void }) {
  const { conn, uplinks } = props;
  const l = lang.value;
  const [busy, setBusy] = useState(false);
  const best = useMemo(() => recommend(officialPresets().map((p) => matchPreset(p, uplinks, l)), uplinks), [uplinks, l]);
  if (!best) return null;
  const dismiss = () => {
    setPref(dismissKey(conn.info.value?.id ?? ""), "1");
    props.onDismiss();
  };
  const apply = async () => {
    setBusy(true);
    try {
      // the automatic cards keep the page they are on: they get a place in the layout, the preset's pages go in front
      const base = snapshotLayout(resolveLayout(uplinks, null, { includeHidden: true }), uplinks);
      const expanded = matchPreset(best.preset, uplinks, l, undefined, base).expanded;
      const merged = mergeLayout(base, expanded, true);
      if (!("doc" in merged)) throw new Error("too_large");
      await conn.saveLayout(merged.doc);
      props.onPage(expanded.pages[0].id);
      pushToast({
        level: "info",
        title: t("ps.appliedTitle", { name: textOf(best.preset.name, l) }),
        text: t("ps.appliedText"),
        action: {
          label: t("ps.undo"),
          // back to no layout at all, so the dashboard arranges itself as before
          run: () => void conn.saveLayout(emptyLayout()).catch(() => {}),
        },
      }, 12_000);
    } catch {
      pushToast({ level: "warn", title: t("ps.applyFailed"), text: t("ps.applyFailedText") });
    } finally {
      setBusy(false);
    }
  };
  return (
    <section class="pr-start card" aria-label={t("ps.startTitle")}>
      <PixelIcon name={best.preset.icon ?? "star"} size={24} />
      <div class="pr-start-text">
        <h2 class="px-title">{t("ps.startTitle")}</h2>
        <p>
          {t("ps.startText", { name: textOf(best.preset.name, l), devices: best.devices === 1 ? t("ps.device1") : t("ps.deviceN", { n: best.devices }) })} <span class="muted">{textOf(best.preset.description, l)}</span>
        </p>
      </div>
      <div class="pr-start-actions">
        <button type="button" class="pxbtn pxbtn-gold" disabled={busy} onClick={apply}>
          {t("ps.startApply", { name: textOf(best.preset.name, l) })}
        </button>
        <button type="button" class="btn btn-small" onClick={props.onMore}>
          {t("ps.startMore")}
        </button>
        <button type="button" class="btn btn-small" onClick={dismiss}>
          {t("ps.startDismiss")}
        </button>
      </div>
    </section>
  );
}

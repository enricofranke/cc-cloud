// Keeps the resolved layout stable across value deltas (docs/SPEC.md "Scaling").
//
// The layout is resolved only when the structure changes (MainframeConn.structure); value deltas then only swap in the
// current widget and uplink objects. The connection gives unchanged sections and widgets their old identity, so a card
// whose widgets did not change keeps its object too and a memoised <Card> is not rendered again: a value delta costs
// one pass over the widgets of the current page instead of resolving and rendering every card.
import type { ResolvedCard, ResolvedWidget } from "./layout.ts";
import type { Uplink, Widget } from "./types.ts";

interface Live {
  u: Uplink;
  byId: Map<string, Widget>;
}

export class Hydrator {
  private live = new WeakMap<Uplink, Live>();
  private cards = new WeakMap<ResolvedCard, ResolvedCard>();

  private readonly adapt: (u: Uplink) => Uplink;

  constructor(adapt: (u: Uplink) => Uplink) {
    this.adapt = adapt;
  }

  /** The adapted uplink (e.g. the auto Computer card), once per uplink object. */
  adapted(u: Uplink): Uplink {
    return this.entry(u).u;
  }

  private entry(u: Uplink): Live {
    let e = this.live.get(u);
    if (!e) {
      const a = this.adapt(u);
      e = { u: a, byId: new Map(a.widgets.map((w) => [w.id, w])) };
      this.live.set(u, e);
    }
    return e;
  }

  /** The card with the current widget values; the same object as last time if nothing in it changed. */
  card(c: ResolvedCard, byUplink: Map<string, Uplink>): ResolvedCard {
    const prev = this.cards.get(c) ?? c;
    const raw = byUplink.get(c.uplink.uplinkId);
    const uplink = raw ? this.entry(raw).u : prev.uplink;
    let changed = uplink !== prev.uplink;
    const widgets = prev.widgets.map((rw): ResolvedWidget => {
      const r = byUplink.get(rw.uplink.uplinkId);
      if (!r) return rw;
      const e = this.entry(r);
      const w = e.byId.get(rw.w.id) ?? rw.w;
      if (w === rw.w && e.u === rw.uplink) return rw;
      changed = true;
      return { ...rw, w, uplink: e.u };
    });
    if (!changed) return prev;
    const next = { ...prev, uplink, widgets };
    this.cards.set(c, next);
    return next;
  }
}

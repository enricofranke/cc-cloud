import type { ComponentChildren } from "preact";
import { openDocs } from "../lib/ui.ts";

/** A link into /docs that stays inside the single-page app. */
export function DocsLink({ to = "/docs", class: cls, children, label }: { to?: string; class?: string; children: ComponentChildren; label?: string }) {
  return (
    <a
      href={to}
      class={cls}
      aria-label={label}
      onClick={(e) => {
        if (e.metaKey || e.ctrlKey || e.shiftKey) return;
        e.preventDefault();
        openDocs(to);
      }}
    >
      {children}
    </a>
  );
}

// components/DocsLink.tsx in the demo: help links open the website's docs in a new tab, the simulated base keeps running.
import type { ComponentChildren } from "preact";
import { docsUrl } from "../shims.ts";
import { lang } from "../strings.ts";

export function DocsLink({ to = "/docs", class: cls, children, label }: { to?: string; class?: string; children: ComponentChildren; label?: string }) {
  return (
    <a href={docsUrl(to, lang)} class={cls} aria-label={label} target="_blank" rel="noopener">
      {children}
    </a>
  );
}

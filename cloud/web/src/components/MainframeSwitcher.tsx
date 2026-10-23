// v0.6: header menu – switch between mainframes, open Devices / Access / Share links.
import { useEffect, useRef, useState } from "preact/hooks";
import { account, saved } from "../lib/store.ts";
import { getConn } from "../lib/conns.ts";
import { statusOf } from "../lib/status.ts";
import { navigate } from "../lib/ui.ts";

export function MainframeSwitcher({ currentId, name, canAccess, canShare }: { currentId: string; name: string; canAccess: boolean; canShare: boolean }) {
  const [open, setOpen] = useState(false);
  const btn = useRef<HTMLButtonElement>(null);
  const menu = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const items = () => [...(menu.current?.querySelectorAll<HTMLElement>("[role=menuitem]") ?? [])];
    items()[0]?.focus();
    const key = (e: KeyboardEvent) => {
      const list = items();
      const i = list.indexOf(document.activeElement as HTMLElement);
      if (e.key === "Escape") {
        setOpen(false);
        btn.current?.focus();
      } else if (e.key === "ArrowDown") {
        e.preventDefault();
        list[(i + 1) % list.length]?.focus();
      } else if (e.key === "ArrowUp") {
        e.preventDefault();
        list[(i - 1 + list.length) % list.length]?.focus();
      } else if (e.key === "Tab") setOpen(false);
    };
    const click = (e: MouseEvent) => {
      if (!menu.current?.contains(e.target as Node) && !btn.current?.contains(e.target as Node)) setOpen(false);
    };
    addEventListener("keydown", key);
    addEventListener("mousedown", click);
    return () => {
      removeEventListener("keydown", key);
      removeEventListener("mousedown", click);
    };
  }, [open]);

  const go = (to: string) => {
    setOpen(false);
    navigate(to);
  };
  const acc = account.value;
  return (
    <>
      <button ref={btn} type="button" class="switcher" aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen(!open)}>
        <h1 class="dash-name">{name}</h1>
        <span class="switcher-caret" aria-hidden="true">
          ▾
        </span>
      </button>
      {open && (
        <div ref={menu} class="switch-menu" role="menu" aria-label="Mainframes">
          <div class="sm-label">{acc ? `Signed in as ${acc.player}` : "This browser"}</div>
          {saved.value.map((m) => {
            const conn = getConn(m.mainframeId);
            const st = statusOf(conn);
            const role = conn?.role.value ?? m.role;
            return (
              <button
                key={m.mainframeId}
                type="button"
                role="menuitem"
                class={`sm-item ${m.mainframeId === currentId ? "on" : ""}`}
                aria-current={m.mainframeId === currentId ? "true" : undefined}
                onClick={() => go(`/m/${encodeURIComponent(m.mainframeId)}`)}
              >
                <span class={`sm-dot st-${st.kind}`} aria-hidden="true" />
                <span class="sm-name">{conn?.info.value?.name ?? m.name}</span>
                {role && <span class={`role-badge r-${role}`}>{role}</span>}
              </button>
            );
          })}
          <div class="sm-sep" />
          <button type="button" role="menuitem" class="sm-item" onClick={() => go("/")}>
            All mainframes
          </button>
          <button type="button" role="menuitem" class="sm-item" onClick={() => go("/devices")}>
            Devices
          </button>
          {canAccess && (
            <button type="button" role="menuitem" class="sm-item" onClick={() => go(`/m/${encodeURIComponent(currentId)}/access`)}>
              Access &amp; roles
            </button>
          )}
          {canShare && (
            <button type="button" role="menuitem" class="sm-item" onClick={() => go(`/m/${encodeURIComponent(currentId)}/shares`)}>
              Share links
            </button>
          )}
          {!acc && (
            <button type="button" role="menuitem" class="sm-item sm-link" onClick={() => go("/pair")}>
              Link my player account…
            </button>
          )}
        </div>
      )}
    </>
  );
}

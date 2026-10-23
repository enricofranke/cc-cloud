import { useEffect, useRef, useState } from "preact/hooks";
import { copyText } from "../lib/clipboard.ts";

/** A code block with a copy button (works on plain HTTP too, see clipboard.ts). */
export function CodeBlock({ code, label = "Code" }: { code: string; label?: string }) {
  const [state, setState] = useState<"idle" | "copied" | "failed">("idle");
  const timer = useRef<ReturnType<typeof setTimeout>>();
  useEffect(() => () => clearTimeout(timer.current), []);
  const copy = async () => {
    const ok = await copyText(code);
    setState(ok ? "copied" : "failed");
    clearTimeout(timer.current);
    timer.current = setTimeout(() => setState("idle"), 2000);
  };
  return (
    <div class="codeblock">
      <div class="codeblock-bar">
        <span class="codeblock-label">{label}</span>
        <button type="button" class={`copy-btn ${state}`} onClick={copy} aria-live="polite">
          {state === "copied" ? "✓ Copied" : state === "failed" ? "Select & copy manually" : "Copy"}
        </button>
      </div>
      <pre class="code" tabIndex={0}>{code}</pre>
    </div>
  );
}

/** Just the copy button (for one-liners). */
export function CopyButton({ text }: { text: string }) {
  const [state, setState] = useState<"idle" | "copied" | "failed">("idle");
  const timer = useRef<ReturnType<typeof setTimeout>>();
  useEffect(() => () => clearTimeout(timer.current), []);
  return (
    <button
      type="button"
      class={`copy-btn ${state}`}
      aria-live="polite"
      onClick={async () => {
        setState((await copyText(text)) ? "copied" : "failed");
        clearTimeout(timer.current);
        timer.current = setTimeout(() => setState("idle"), 2000);
      }}
    >
      {state === "copied" ? "✓" : state === "failed" ? "!" : "Copy"}
    </button>
  );
}

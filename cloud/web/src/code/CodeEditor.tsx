// Lazy chunk (CodeMirror 6): the Cloud Scripts editor. Lua highlighting, completions for cloud.* and the Lua
// libraries the sandbox keeps, hover docs and the signature of the call under the cursor (all from api.json, in the
// dashboard language), and error markers from the server's check and the last runtime error.
// Mounted in a shadow root like scripts/Editor.tsx: CodeMirror's styles go into a constructed style sheet there,
// which the dashboard's strict CSP allows (a <style> element would be blocked).
import { useEffect, useRef } from "preact/hooks";
import { EditorState, StateField, type Extension } from "@codemirror/state";
import { EditorView, hoverTooltip, keymap, lineNumbers, highlightActiveLine, highlightActiveLineGutter, drawSelection, showTooltip, type Tooltip } from "@codemirror/view";
import { defaultKeymap, history, historyKeymap, indentWithTab } from "@codemirror/commands";
import { StreamLanguage, HighlightStyle, bracketMatching, indentOnInput, syntaxHighlighting } from "@codemirror/language";
import { lua } from "@codemirror/legacy-modes/mode/lua";
import { searchKeymap, highlightSelectionMatches } from "@codemirror/search";
import { autocompletion, closeBrackets, closeBracketsKeymap, completionKeymap, type CompletionContext, type CompletionResult } from "@codemirror/autocomplete";
import { lintGutter, setDiagnostics, type Diagnostic as LintDiagnostic } from "@codemirror/lint";
import { tags as t } from "@lezer/highlight";
import type { Lang } from "../lib/lang.ts";
import type { Marker } from "./ide.ts";
import descriptor from "../../../../src/main/resources/cccloud_code/api.json" with { type: "json" };
import { completionsAt, hoverAt, signatureAt, type ApiDescriptor, type HoverDoc } from "./api.ts";
import { ctr } from "./strings.ts";

const API = descriptor as unknown as ApiDescriptor;

export interface EditorHandle {
  text(): string;
  setText(s: string): void;
  focus(): void;
  gotoLine(n: number): void;
}

const highlight = HighlightStyle.define([
  { tag: t.keyword, color: "var(--cm-kw)" },
  { tag: [t.string, t.special(t.string)], color: "var(--cm-str)" },
  { tag: t.number, color: "var(--cm-num)" },
  { tag: t.comment, color: "var(--cm-com)", fontStyle: "italic" },
  { tag: [t.variableName, t.propertyName], color: "var(--cm-var)" },
  { tag: [t.bool, t.null, t.atom], color: "var(--cm-num)" },
  { tag: t.operator, color: "var(--cm-op)" },
  { tag: t.definitionKeyword, color: "var(--cm-kw)" },
  { tag: t.standard(t.variableName), color: "var(--cm-builtin)" },
]);
const mono = 'ui-monospace, "SF Mono", Menlo, Consolas, monospace';
const theme = EditorView.theme({
  "&": { height: "100%", fontSize: "14px", backgroundColor: "var(--cm-bg)", color: "var(--cm-text)" },
  ".cm-scroller": { overflow: "auto", fontFamily: mono },
  ".cm-content": { caretColor: "var(--cyan)", paddingBottom: "40px" },
  ".cm-gutters": { backgroundColor: "var(--cm-gutter)", color: "var(--cm-gutter-text)", border: "none" },
  ".cm-activeLine": { backgroundColor: "color-mix(in srgb, var(--cyan) 7%, transparent)" },
  ".cm-activeLineGutter": { backgroundColor: "color-mix(in srgb, var(--cyan) 12%, transparent)" },
  "&.cm-focused .cm-selectionBackground, .cm-selectionBackground": { backgroundColor: "color-mix(in srgb, var(--cyan) 28%, transparent)" },
  ".cm-cursor": { borderLeftColor: "var(--cyan)" },
  ".cm-tooltip": { backgroundColor: "var(--panel)", color: "var(--text)", border: "1px solid var(--line-2)", borderRadius: "6px", boxShadow: "var(--shadow)", fontFamily: "system-ui, sans-serif", fontSize: "13px" },
  ".cm-tooltip-autocomplete > ul": { fontFamily: mono, maxHeight: "16em" },
  ".cm-tooltip-autocomplete > ul > li": { padding: "3px 8px" },
  ".cm-tooltip-autocomplete > ul > li[aria-selected]": { backgroundColor: "color-mix(in srgb, var(--cyan) 26%, var(--panel))", color: "var(--text)" },
  // On a narrow screen the info box would sit on top of the next options; the option already shows its parameters
  // and the signature tooltip follows once the call is typed.
  ".cm-completionInfo.cm-completionInfo-left-narrow, .cm-completionInfo.cm-completionInfo-right-narrow": { display: "none" },
  ".cm-completionDetail": { color: "var(--muted)", fontStyle: "normal", marginLeft: "0.6em" },
  ".cm-completionInfo": { maxWidth: "340px", whiteSpace: "pre-wrap", padding: "6px 9px" },
  ".cm-completionMatchedText": { textDecoration: "none", color: "var(--cyan)", fontWeight: "600" },
  ".cx-hover": { maxWidth: "420px", padding: "8px 10px", display: "flex", flexDirection: "column", gap: "6px" },
  ".cx-info": { maxWidth: "360px", display: "flex", flexDirection: "column", gap: "6px" },
  ".cx-sig": { fontFamily: mono, fontSize: "12.5px", color: "var(--cyan)", overflowWrap: "anywhere" },
  ".cx-sig .cx-on": { color: "var(--text)", fontWeight: "700", textDecoration: "underline" },
  ".cx-doc": { margin: "0", lineHeight: "1.4" },
  ".cx-params": { margin: "0", padding: "0", listStyle: "none", display: "flex", flexDirection: "column", gap: "2px" },
  ".cx-params code, .cx-ex, .cx-doc code": { fontFamily: mono, fontSize: "12px" },
  ".cx-doc code": { color: "var(--cyan)" },
  ".cx-tag": { alignSelf: "flex-start", fontSize: "11px", padding: "0 6px", borderRadius: "3px", border: "1px solid var(--line-2)", color: "var(--muted)" },
  ".cx-ex": { margin: "0", padding: "6px 8px", borderRadius: "4px", backgroundColor: "var(--panel-2)", whiteSpace: "pre-wrap" },
  ".cx-sigtip": { padding: "4px 9px", maxWidth: "min(520px, 90vw)" },
  ".cx-sigtip .cx-doc": { color: "var(--muted)", fontSize: "12px" },
  ".cm-diagnostic": { padding: "4px 8px" },
  ".cm-diagnostic-error": { borderLeftColor: "var(--red)" },
  ".cm-diagnostic-warning": { borderLeftColor: "var(--yellow)" },
  ".cm-diagnosticSource": { color: "var(--muted)" },
  ".cm-lintRange-error": { backgroundImage: "none", textDecoration: "underline wavy var(--red)", textUnderlineOffset: "3px" },
  ".cm-lintRange-warning": { backgroundImage: "none", textDecoration: "underline wavy var(--yellow)", textUnderlineOffset: "3px" },
});

function el<K extends keyof HTMLElementTagNameMap>(tag: K, cls: string, text?: string) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text !== undefined) e.textContent = text;
  return e;
}

/** Descriptor text with its `code` spans as <code>, the rest as plain text (never HTML). */
function richText<K extends keyof HTMLElementTagNameMap>(tag: K, cls: string, text: string) {
  const e = el(tag, cls);
  text.split("`").forEach((part, i) => part && e.append(i % 2 ? el("code", "", part) : document.createTextNode(part)));
  return e;
}

/** A completion's info: its first paragraph (a signature) in mono, the rest as text. */
function infoDom(info: string) {
  const box = el("div", "cx-info");
  const [first, ...rest] = info.split("\n\n");
  if (rest.length && first.startsWith("cloud.")) box.append(el("div", "cx-sig", first));
  else rest.unshift(first);
  for (const p of rest) box.append(richText("p", "cx-doc", p));
  return box;
}

/** The hover card: signature, text, parameters, permission, what kind of call it is, an example. */
function hoverDom(h: HoverDoc, lang: Lang) {
  const box = el("div", "cx-hover");
  box.append(el("div", "cx-sig", h.signature));
  if (h.doc) box.append(richText("p", "cx-doc", h.doc));
  if (h.params.length) {
    const ul = el("ul", "cx-params");
    for (const p of h.params) {
      const li = el("li", "");
      li.append(el("code", "", `${p.name}: ${p.type}`));
      if (p.doc) li.append(richText("span", "", ` – ${p.doc}`));
      ul.append(li);
    }
    box.append(ul);
  }
  const ret = h.returns.filter((r) => r.doc);
  if (ret.length) box.append(richText("p", "cx-doc", `→ ${ret.map((r) => `${r.type}: ${r.doc}`).join("; ")}`));
  const tags: string[] = [];
  if (h.kind === "await") tags.push(lang === "de" ? "wartet auf den Server (einen Tick)" : "waits for the server (one tick)");
  if (h.kind === "yield") tags.push(lang === "de" ? "pausiert das Skript" : "pauses the script");
  if (h.perm) tags.push(lang === "de" ? `braucht ${h.perm}` : `needs ${h.perm}`);
  for (const x of tags) box.append(el("span", "cx-tag", x));
  if (h.example) box.append(el("pre", "cx-ex", h.example));
  return box;
}

/** The signature tooltip above the cursor while it is inside a cloud.* call. */
function signatureField(lang: () => Lang) {
  const compute = (state: EditorState): Tooltip | null => {
    const sel = state.selection.main;
    if (!sel.empty) return null;
    const help = signatureAt(API, state.doc.toString(), sel.head);
    if (!help) return null;
    return {
      pos: help.open + 1,
      above: true,
      strictSide: false,
      arrow: false,
      create: () => {
        const dom = el("div", "cx-sigtip");
        const sig = el("div", "cx-sig");
        const f = help.fn;
        sig.append(document.createTextNode(`${API.name}.${f.name}(`));
        f.params.forEach((p, i) => {
          if (i) sig.append(document.createTextNode(", "));
          sig.append(el("span", i === help.param ? "cx-on" : "", p.variadic ? "..." : `${p.name}${p.optional ? "?" : ""}`));
        });
        sig.append(document.createTextNode(")"));
        dom.append(sig);
        const p = f.params[help.param];
        const doc = p?.doc ? p.doc[lang()] || p.doc.en : "";
        if (doc) dom.append(richText("div", "cx-doc", doc));
        return { dom };
      },
    };
  };
  return StateField.define<Tooltip | null>({
    create: compute,
    update: (v, tr) => (tr.docChanged || tr.selection ? compute(tr.state) : v),
    provide: (f) => showTooltip.from(f),
  });
}

export default function CodeEditor(props: {
  initial: string;
  lang: Lang;
  readOnly: boolean;
  label: string;
  /** markers and the text they were computed for: they are only applied while the editor shows that text */
  markers: Marker[];
  markersText: string | null;
  onChange: (text: string) => void;
  onSave: () => void;
  handle: { current: EditorHandle | null };
}) {
  const host = useRef<HTMLDivElement>(null);
  const view = useRef<EditorView | null>(null);
  // the sources read the latest props without rebuilding the editor
  const latest = useRef(props);
  latest.current = props;

  const extensions = (): Extension[] => [
    lineNumbers(),
    highlightActiveLineGutter(),
    highlightActiveLine(),
    drawSelection(),
    history(),
    indentOnInput(),
    bracketMatching(),
    closeBrackets(),
    highlightSelectionMatches(),
    StreamLanguage.define(lua),
    syntaxHighlighting(highlight),
    EditorState.tabSize.of(2),
    EditorView.lineWrapping,
    EditorView.contentAttributes.of({ "aria-label": latest.current.label, spellcheck: "false", autocapitalize: "off", autocorrect: "off" }),
    EditorState.readOnly.of(latest.current.readOnly),
    autocompletion({
      override: [
        (ctx: CompletionContext): CompletionResult | null => {
          const r = completionsAt(API, latest.current.lang, ctx.state.doc.toString(), ctx.pos, ctx.explicit);
          if (!r) return null;
          const options = r.options.map((o) => (o.info ? { ...o, info: () => infoDom(o.info as string) } : o));
          return { from: r.from, options, validFor: /^\w*$/ };
        },
      ],
      icons: true,
    }),
    hoverTooltip(
      (v, pos) => {
        const h = hoverAt(API, latest.current.lang, v.state.doc.toString(), pos);
        return h ? { pos: h.from, end: h.to, above: true, create: () => ({ dom: hoverDom(h, latest.current.lang) }) } : null;
      },
      { hoverTime: 350 },
    ),
    signatureField(() => latest.current.lang),
    lintGutter(),
    keymap.of([
      { key: "Mod-s", preventDefault: true, run: () => (latest.current.onSave(), true) },
      ...closeBracketsKeymap,
      ...completionKeymap,
      indentWithTab,
      ...defaultKeymap,
      ...historyKeymap,
      ...searchKeymap,
    ]),
    EditorView.updateListener.of((u) => {
      if (u.docChanged) latest.current.onChange(u.state.doc.toString());
    }),
    theme,
  ];

  useEffect(() => {
    const shadow = host.current!.shadowRoot ?? host.current!.attachShadow({ mode: "open" });
    const v = new EditorView({ parent: shadow, root: shadow, state: EditorState.create({ doc: props.initial, extensions: extensions() }) });
    view.current = v;
    props.handle.current = {
      text: () => v.state.doc.toString(),
      // a new state: no undo across loads, no markers of the old text
      setText: (s) => v.setState(EditorState.create({ doc: s, extensions: extensions() })),
      focus: () => v.focus(),
      gotoLine: (n) => {
        const line = v.state.doc.line(Math.min(Math.max(1, n), v.state.doc.lines));
        v.dispatch({ selection: { anchor: line.from }, effects: EditorView.scrollIntoView(line.from, { y: "center" }) });
        v.focus();
      },
    };
    return () => {
      props.handle.current = null;
      v.destroy();
      view.current = null;
    };
  }, []);

  useEffect(() => {
    const v = view.current;
    if (!v) return;
    if (props.markersText !== null && v.state.doc.toString() !== props.markersText) return;
    const len = v.state.doc.length;
    const diags: LintDiagnostic[] = props.markers.map((m) => ({
      from: Math.min(m.from, len),
      to: Math.min(Math.max(m.to, m.from), len),
      severity: m.severity,
      message: m.message,
      source: m.source === "runtime" ? ctr(props.lang, "runtimeError") : "Lua",
    }));
    v.dispatch(setDiagnostics(v.state, diags));
  }, [props.markers, props.markersText]);

  return <div ref={host} class="cx-host" />;
}

// DEV-ONLY: a tiny CraftOS-like shell for the browser terminal (docs/SPEC.md "E. Browser terminal").
// Enough for screenshots and tests: prompt, typing, history, a few programs, colours on advanced computers.

const W = 51;
const H = 19;
type Row = { text: string; fg: string; bg: string };

export class FakeTerminal {
  on = true;
  readonly w = W;
  readonly h = H;
  rows: Row[] = [];
  cx = 1;
  cy = 1;
  line = "";
  history: string[] = [];
  private dirty = new Set<number>();
  private fg = "0";
  readonly label: string;
  readonly advanced: boolean;
  private files: () => string[];
  constructor(label: string, advanced: boolean, files: () => string[]) {
    this.label = label;
    this.advanced = advanced;
    this.files = files;
    this.boot();
  }
  private blank(): Row {
    return { text: " ".repeat(W), fg: "0".repeat(W), bg: "f".repeat(W) };
  }
  private boot() {
    this.rows = Array.from({ length: H }, () => this.blank());
    this.cx = 1;
    this.cy = 1;
    this.dirty = new Set(this.rows.map((_, i) => i));
    this.print("CraftOS 1.9", this.advanced ? "4" : "0");
    this.newline();
    this.prompt();
  }
  private put(ch: string) {
    if (this.cx > W) this.newline();
    const r = this.rows[this.cy - 1];
    const i = this.cx - 1;
    r.text = r.text.slice(0, i) + ch + r.text.slice(i + 1);
    r.fg = r.fg.slice(0, i) + this.fg + r.fg.slice(i + 1);
    this.dirty.add(this.cy - 1);
    this.cx++;
  }
  private print(s: string, fg = "0") {
    const keep = this.fg;
    this.fg = this.advanced ? fg : "0";
    for (const ch of s) this.put(ch);
    this.fg = keep;
  }
  private newline() {
    this.cx = 1;
    if (this.cy < H) this.cy++;
    else {
      this.rows.shift();
      this.rows.push(this.blank());
      this.dirty = new Set(this.rows.map((_, i) => i));
    }
  }
  private prompt() {
    this.print("> ", "4");
  }
  private run(cmd: string) {
    const [prog, ...args] = cmd.trim().split(/\s+/);
    const out = (s: string, fg = "0") => {
      this.print(s, fg);
      this.newline();
    };
    if (!prog) return;
    this.history.push(cmd);
    if (prog === "clear") {
      this.rows = Array.from({ length: H }, () => this.blank());
      this.cx = 1;
      this.cy = 1;
      this.dirty = new Set(this.rows.map((_, i) => i));
    } else if (prog === "ls" || prog === "dir") {
      const names = ["rom", ...this.files()];
      out(names.map((n) => n.padEnd(16)).join("").trimEnd(), "5");
    } else if (prog === "help") out("Try: ls, clear, label, id, echo <text>");
    else if (prog === "id") out(`This is computer #${this.label.replace(/\D+/g, "") || 0}`);
    else if (prog === "label") out(`Computer label is "${this.label}"`);
    else if (prog === "echo") out(args.join(" "));
    else out("No such program", "e");
  }
  input(e: { type: string; c?: string; key?: number; text?: string }): boolean {
    if (e.type === "turn_on") {
      if (!this.on) (this.on = true), this.boot();
      return true;
    }
    if (e.type === "shutdown") {
      this.on = false;
      return true;
    }
    if (e.type === "reboot") {
      this.on = true;
      this.boot();
      return true;
    }
    if (!this.on) return false;
    if (e.type === "char" && e.c) {
      this.line += e.c;
      this.print(e.c);
    } else if (e.type === "paste" && e.text) {
      this.line += e.text;
      this.print(e.text);
    } else if (e.type === "terminate") {
      this.print("Terminated", "e");
      this.newline();
      this.line = "";
      this.prompt();
    } else if (e.type === "key" && e.key === 257) {
      const cmd = this.line;
      this.line = "";
      this.newline();
      this.run(cmd);
      this.prompt();
    } else if (e.type === "key" && e.key === 259 && this.line) {
      this.line = this.line.slice(0, -1);
      this.cx--;
      const r = this.rows[this.cy - 1];
      r.text = r.text.slice(0, this.cx - 1) + " " + r.text.slice(this.cx);
      this.dirty.add(this.cy - 1);
    }
    return true;
  }
  /** A `term` message: everything (full) or the changed rows since the last frame. */
  frame(uplinkId: string, full: boolean) {
    if (!this.on) return { t: "term", uplinkId, on: false };
    const ys = full ? this.rows.map((_, i) => i) : [...this.dirty];
    this.dirty.clear();
    return {
      t: "term",
      uplinkId,
      on: true,
      full,
      w: W,
      h: H,
      ...(full ? { colour: this.advanced } : {}),
      cursor: { x: this.cx, y: this.cy, blink: true },
      rows: ys.map((y) => ({ y: y + 1, ...this.rows[y] })),
    };
  }
}

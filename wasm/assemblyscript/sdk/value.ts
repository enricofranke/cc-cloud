// A JSON-like value: what the cloud API hands to scripts and takes from them (cards, widgets, events, options).

export enum Kind { Null, Bool, Number, String, Array, Object, Function }

export class Value {
  kind: Kind = Kind.Null;
  private b: bool = false;
  private n: f64 = 0;
  private s: string = "";
  private items: Array<Value> | null = null;
  private fields: Map<string, Value> | null = null;
  private keyOrder: Array<string> | null = null;

  static null(): Value {
    return new Value();
  }

  static bool(b: bool): Value {
    const v = new Value();
    v.kind = Kind.Bool;
    v.b = b;
    return v;
  }

  static number(n: f64): Value {
    const v = new Value();
    v.kind = Kind.Number;
    v.n = n;
    return v;
  }

  static string(s: string): Value {
    const v = new Value();
    v.kind = Kind.String;
    v.s = s;
    return v;
  }

  static array(items: Array<Value> = []): Value {
    const v = new Value();
    v.kind = Kind.Array;
    v.items = items;
    return v;
  }

  static object(): Value {
    const v = new Value();
    v.kind = Kind.Object;
    v.fields = new Map<string, Value>();
    v.keyOrder = [];
    return v;
  }

  /** A handler reference (internal: see handlers.ts). */
  static handler(id: i32): Value {
    const v = new Value();
    v.kind = Kind.Function;
    v.n = <f64>id;
    return v;
  }

  /** Any AssemblyScript value: Value, string, bool, a number, an array of those, or null. */
  static from<T>(x: T): Value {
    if (isNullable<T>()) {
      if (x == null) return Value.null();
    }
    if (isBoolean<T>()) return Value.bool(<bool>x);
    if (isInteger<T>() || isFloat<T>()) return Value.number(<f64>x);
    if (isString<T>()) return Value.string(changetype<string>(x));
    if (x instanceof Value) return changetype<Value>(x);
    if (isArray<T>()) {
      const list = new Array<Value>();
      // @ts-ignore: T is an array here
      for (let i = 0; i < x.length; i++) list.push(Value.from(x[i]));
      return Value.array(list);
    }
    ERROR("Value.from: unsupported type");
    return Value.null();
  }

  // ---- reading -----------------------------------------------------------------------------------------------------

  get isNull(): bool {
    return this.kind == Kind.Null;
  }

  get isBool(): bool {
    return this.kind == Kind.Bool;
  }

  get isNumber(): bool {
    return this.kind == Kind.Number;
  }

  get isString(): bool {
    return this.kind == Kind.String;
  }

  get isArray(): bool {
    return this.kind == Kind.Array;
  }

  get isObject(): bool {
    return this.kind == Kind.Object;
  }

  /** true only for the boolean true (like Lua: nil and false are false, everything else is not a boolean). */
  asBool(): bool {
    return this.kind == Kind.Bool && this.b;
  }

  /** The number, a boolean as 0/1, else 0. */
  asNumber(): f64 {
    if (this.kind == Kind.Number) return this.n;
    if (this.kind == Kind.Bool) return this.b ? 1 : 0;
    return 0;
  }

  /** The string, or the value as text. */
  asString(): string {
    if (this.kind == Kind.String) return this.s;
    return this.toString();
  }

  /** Array items (empty for anything else). */
  asArray(): Array<Value> {
    const items = this.items;
    return items != null && this.kind == Kind.Array ? items : [];
  }

  get length(): i32 {
    if (this.kind == Kind.Array) return this.asArray().length;
    if (this.kind == Kind.Object) return this.keys().length;
    if (this.kind == Kind.String) return this.s.length;
    return 0;
  }

  /** The array item at i, or null. */
  at(i: i32): Value {
    const items = this.asArray();
    return i >= 0 && i < items.length ? items[i] : Value.null();
  }

  /** The object field, or null. */
  get(key: string): Value {
    const fields = this.fields;
    if (fields == null || this.kind != Kind.Object || !fields.has(key)) return Value.null();
    return fields.get(key);
  }

  has(key: string): bool {
    const fields = this.fields;
    return fields != null && this.kind == Kind.Object && fields.has(key) && !fields.get(key).isNull;
  }

  /** Object keys in insertion order. */
  keys(): Array<string> {
    const order = this.keyOrder;
    return order != null && this.kind == Kind.Object ? order : [];
  }

  // ---- building ----------------------------------------------------------------------------------------------------

  /** Sets an object field (any value; Value.from rules) and returns this object, for chaining. */
  set<T>(key: string, x: T): Value {
    if (this.kind != Kind.Object) throw new Error("set on a value that is not an object");
    const fields = changetype<Map<string, Value>>(this.fields);
    if (!fields.has(key)) changetype<Array<string>>(this.keyOrder).push(key);
    fields.set(key, Value.from(x));
    return this;
  }

  /** Appends to an array and returns this array. */
  push<T>(x: T): Value {
    if (this.kind != Kind.Array) throw new Error("push on a value that is not an array");
    changetype<Array<Value>>(this.items).push(Value.from(x));
    return this;
  }

  // ---- JSON --------------------------------------------------------------------------------------------------------

  toString(): string {
    const out = new Array<string>();
    this.write(out);
    return out.join("");
  }

  toJSON(): string {
    return this.toString();
  }

  private write(out: Array<string>): void {
    switch (this.kind) {
      case Kind.Null:
        out.push("null");
        break;
      case Kind.Bool:
        out.push(this.b ? "true" : "false");
        break;
      case Kind.Number:
        out.push(formatNumber(this.n));
        break;
      case Kind.String:
        quote(this.s, out);
        break;
      case Kind.Array: {
        const items = this.asArray();
        out.push("[");
        for (let i = 0; i < items.length; i++) {
          if (i > 0) out.push(",");
          items[i].write(out);
        }
        out.push("]");
        break;
      }
      case Kind.Object: {
        const keys = this.keys();
        const fields = changetype<Map<string, Value>>(this.fields);
        out.push("{");
        for (let i = 0; i < keys.length; i++) {
          if (i > 0) out.push(",");
          quote(keys[i], out);
          out.push(":");
          fields.get(keys[i]).write(out);
        }
        out.push("}");
        break;
      }
      case Kind.Function:
        out.push('{"$fn":' + (<i32>this.n).toString() + "}");
        break;
    }
  }

  static parse(text: string): Value {
    const p = new Parser(text);
    p.space();
    const v = p.value(0);
    p.space();
    if (p.pos != text.length) throw new Error("JSON: unexpected text after the value");
    return v;
  }
}

function formatNumber(n: f64): string {
  if (!isFinite(n)) return "null";
  if (n == Math.floor(n) && Math.abs(n) < 9007199254740992) return (<i64>n).toString();
  return n.toString();
}

function quote(s: string, out: Array<string>): void {
  out.push('"');
  let start = 0;
  for (let i = 0; i < s.length; i++) {
    const c = s.charCodeAt(i);
    if (c == 0x22 || c == 0x5c || c < 0x20) {
      if (i > start) out.push(s.substring(start, i));
      if (c == 0x22) out.push('\\"');
      else if (c == 0x5c) out.push("\\\\");
      else if (c == 0x0a) out.push("\\n");
      else if (c == 0x0d) out.push("\\r");
      else if (c == 0x09) out.push("\\t");
      else out.push("\\u" + c.toString(16).padStart(4, "0"));
      start = i + 1;
    }
  }
  if (start < s.length) out.push(s.substring(start));
  out.push('"');
}

const MAX_DEPTH = 64;

class Parser {
  pos: i32 = 0;

  constructor(public text: string) {}

  space(): void {
    while (this.pos < this.text.length) {
      const c = this.text.charCodeAt(this.pos);
      if (c != 0x20 && c != 0x0a && c != 0x0d && c != 0x09) break;
      this.pos++;
    }
  }

  value(depth: i32): Value {
    if (depth > MAX_DEPTH) throw new Error("JSON: nested too deep");
    if (this.pos >= this.text.length) throw new Error("JSON: unexpected end");
    const c = this.text.charCodeAt(this.pos);
    if (c == 0x7b) return this.object(depth);
    if (c == 0x5b) return this.array(depth);
    if (c == 0x22) return Value.string(this.string());
    if (this.word("true")) return Value.bool(true);
    if (this.word("false")) return Value.bool(false);
    if (this.word("null")) return Value.null();
    return Value.number(this.number());
  }

  private word(w: string): bool {
    if (this.text.startsWith(w, this.pos)) {
      this.pos += w.length;
      return true;
    }
    return false;
  }

  private object(depth: i32): Value {
    const v = Value.object();
    this.pos++;
    this.space();
    if (this.peek() == 0x7d) {
      this.pos++;
      return v;
    }
    while (true) {
      this.space();
      if (this.peek() != 0x22) throw new Error("JSON: name expected");
      const key = this.string();
      this.space();
      this.expect(0x3a);
      this.space();
      v.set(key, this.value(depth + 1));
      this.space();
      const c = this.peek();
      this.pos++;
      if (c == 0x7d) return v;
      if (c != 0x2c) throw new Error("JSON: , or } expected");
    }
  }

  private array(depth: i32): Value {
    const items = new Array<Value>();
    this.pos++;
    this.space();
    if (this.peek() == 0x5d) {
      this.pos++;
      return Value.array(items);
    }
    while (true) {
      this.space();
      items.push(this.value(depth + 1));
      this.space();
      const c = this.peek();
      this.pos++;
      if (c == 0x5d) return Value.array(items);
      if (c != 0x2c) throw new Error("JSON: , or ] expected");
    }
  }

  private string(): string {
    this.pos++; // opening quote
    const parts = new Array<string>();
    let start = this.pos;
    while (true) {
      if (this.pos >= this.text.length) throw new Error("JSON: unterminated string");
      const c = this.text.charCodeAt(this.pos);
      if (c == 0x22) break;
      if (c == 0x5c) {
        if (this.pos > start) parts.push(this.text.substring(start, this.pos));
        this.pos++;
        const e = this.peek();
        this.pos++;
        if (e == 0x6e) parts.push("\n");
        else if (e == 0x74) parts.push("\t");
        else if (e == 0x72) parts.push("\r");
        else if (e == 0x62) parts.push("\b");
        else if (e == 0x66) parts.push("\f");
        else if (e == 0x75) {
          if (this.pos + 4 > this.text.length) throw new Error("JSON: bad \\u escape");
          const code = I32.parseInt(this.text.substring(this.pos, this.pos + 4), 16);
          if (isNaN(<f64>code)) throw new Error("JSON: bad \\u escape");
          parts.push(String.fromCharCode(code));
          this.pos += 4;
        } else parts.push(String.fromCharCode(e));
        start = this.pos;
        continue;
      }
      this.pos++;
    }
    if (this.pos > start) parts.push(this.text.substring(start, this.pos));
    this.pos++; // closing quote
    return parts.join("");
  }

  private number(): f64 {
    const start = this.pos;
    while (this.pos < this.text.length) {
      const c = this.text.charCodeAt(this.pos);
      if ((c >= 0x30 && c <= 0x39) || c == 0x2d || c == 0x2b || c == 0x2e || c == 0x65 || c == 0x45) this.pos++;
      else break;
    }
    if (this.pos == start) throw new Error("JSON: unexpected character");
    return parseFloat(this.text.substring(start, this.pos));
  }

  private peek(): i32 {
    return this.pos < this.text.length ? this.text.charCodeAt(this.pos) : -1;
  }

  private expect(c: i32): void {
    if (this.peek() != c) throw new Error("JSON: unexpected character");
    this.pos++;
  }
}

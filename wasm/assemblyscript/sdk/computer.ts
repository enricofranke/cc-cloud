// Cloud Computer programs in AssemblyScript: the computer's APIs (term, os, fs, redstone, peripheral) for a program
// that runs as the computer's boot program. The most used functions are typed here; every other one is reachable with
// call("module.function", [args]). Strings are the terminal's 8-bit characters (one byte each).
import { Value } from "./value";
import { invokeProgram } from "./host";

/** Any function of the computer's modules: call("fs.list", [Value.string("/")]). Returns the results as an array. */
export function call(name: string, args: Array<Value> = []): Value {
  return invokeProgram(name, args);
}

export namespace term {
  export function write(text: string): void {
    invokeProgram("term.write", [Value.string(text)]);
  }

  export function clear(): void {
    invokeProgram("term.clear", []);
  }

  export function clearLine(): void {
    invokeProgram("term.clearLine", []);
  }

  /** 1-based, like the Lua API. */
  export function setCursorPos(x: i32, y: i32): void {
    invokeProgram("term.setCursorPos", [Value.number(x), Value.number(y)]);
  }

  /** [width, height] */
  export function getSize(): Value {
    return invokeProgram("term.getSize", []);
  }

  /** A colour as in the colors API (1, 2, 4, … 32768). */
  export function setTextColor(colour: i32): void {
    invokeProgram("term.setTextColor", [Value.number(colour)]);
  }

  export function setBackgroundColor(colour: i32): void {
    invokeProgram("term.setBackgroundColor", [Value.number(colour)]);
  }
}

export namespace os {
  /**
   * Waits for the next event and returns it as an array: [name, args…], e.g. ["char", "a"] or ["timer", 3].
   * With a filter, other events are dropped while waiting (like os.pullEventRaw with a filter).
   */
  export function pullEvent(filter: string | null = null): Value {
    while (true) {
      const event = invokeProgram("__native.pull", []);
      if (filter == null || event.at(0).asString() == filter) return event;
    }
  }

  export function startTimer(seconds: f64): i64 {
    return <i64>invokeProgram("os.startTimer", [Value.number(seconds)]).at(0).asNumber();
  }

  export function queueEvent(name: string): void {
    invokeProgram("os.queueEvent", [Value.string(name)]);
  }

  export function getComputerID(): i64 {
    return <i64>invokeProgram("os.getComputerID", []).at(0).asNumber();
  }

  export function sleep(seconds: f64): void {
    const id = startTimer(seconds);
    while (true) {
      const e = pullEvent("timer");
      if (<i64>e.at(1).asNumber() == id) return;
    }
  }

  export function shutdown(): void {
    invokeProgram("os.shutdown", []);
  }
}

export namespace redstone {
  export function setOutput(side: string, on: bool): void {
    invokeProgram("redstone.setOutput", [Value.string(side), Value.bool(on)]);
  }

  export function getInput(side: string): bool {
    return invokeProgram("redstone.getInput", [Value.string(side)]).at(0).asBool();
  }
}

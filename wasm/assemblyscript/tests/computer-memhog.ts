// A Cloud Computer program that keeps allocating (waiting now and then): stopped by its memory cap.
import { term, os } from "../sdk/computer";

const keep = new Array<ArrayBuffer>();

export function main(): void {
  term.write("eating");
  while (true) {
    keep.push(new ArrayBuffer(1 << 20));
    os.queueEvent("y");
    os.pullEvent("y");
  }
}

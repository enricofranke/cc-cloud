// A Cloud Computer program that never waits: stopped with "too long without yielding".
import { term } from "../sdk/computer";

export function main(): void {
  term.write("looping");
  let n: i64 = 0;
  while (true) n++;
}

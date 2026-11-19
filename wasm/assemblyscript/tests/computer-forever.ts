// A program that waits forever: only Ctrl+T, shutdown or reboot end it.
import { term, os } from "../sdk/computer";

export function main(): void {
  term.write("waiting");
  while (true) os.sleep(30);
}

// A program started from the shell: prints its arguments, waits for a typed character, then ends.
import { term, os } from "../sdk/computer";

export function main(): void {
  const args = os.args();
  let line = "args";
  for (let i = 0; i < args.length; i++) line += " " + args.at(i).asString();
  term.write(line);
  const e = os.pullEvent("char");
  term.write(" got " + e.at(1).asString());
}

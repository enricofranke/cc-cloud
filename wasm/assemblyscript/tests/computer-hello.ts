// A Cloud Computer program: writes to the terminal, waits for a timer and a typed character.
import { term, os, redstone } from "../sdk/computer";

export function main(): void {
  term.clear();
  term.setCursorPos(1, 1);
  term.write("Hello from WebAssembly #" + os.getComputerID().toString());
  os.sleep(0.1);
  term.setCursorPos(1, 2);
  term.write("timer ok");
  redstone.setOutput("top", true);
  const e = os.pullEvent("char");
  term.setCursorPos(1, 3);
  term.write("got " + e.at(1).asString());
  os.sleep(60); // stays on until shut down (a computer whose program ended turns off and clears its screen)
}

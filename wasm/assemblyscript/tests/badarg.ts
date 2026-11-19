// A wrong call: the host's argument error ends the script (AssemblyScript cannot catch it).
import { cloud, Value } from "../sdk";
export { cccloud_handle } from "../sdk";

function nothing(e: Value): void {}

export function main(): void {
  cloud.on("nonsense", null, nothing);
}

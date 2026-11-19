// CC: Cloud scripts in AssemblyScript. A script imports what it needs from here and must re-export the handler entry:
//
//   import { cloud, Value } from "../sdk";
//   export { cccloud_handle } from "../sdk";
//
//   export function main(): void {
//     cloud.log("hello from " + cloud.getMainframe().get("name").asString());
//   }
export { Value, Kind } from "./value";
export { Result, EventHandler, TimerHandler, invoke, cccloud_handle } from "./host";
export { cloud } from "./cloud";
// Cloud Computer programs: import { term, os } from "../sdk/computer";

// An endless loop: stopped by the CPU limit.
import { cloud } from "../sdk";
export { cccloud_handle } from "../sdk";

export function main(): void {
  let n: i64 = 0;
  cloud.log("looping");
  while (true) n++;
}

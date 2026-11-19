// Unbounded recursion: a stack overflow error, not a crash.
import { cloud } from "../sdk";
export { cccloud_handle } from "../sdk";

function down(n: i32): i32 {
  return down(n + 1) + 1;
}

export function main(): void {
  cloud.log("recursing");
  cloud.log(down(0).toString());
}

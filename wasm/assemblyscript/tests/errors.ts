// An uncaught error at a known line.
import { cloud } from "../sdk";
export { cccloud_handle } from "../sdk";

export function main(): void {
  cloud.log("before");
  throw new Error("boom");
}

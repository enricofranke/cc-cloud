// Allocates until the memory cap is reached.
import { cloud } from "../sdk";
export { cccloud_handle } from "../sdk";

const keep = new Array<ArrayBuffer>();

export function main(): void {
  cloud.log("allocating");
  while (true) {
    keep.push(new ArrayBuffer(1 << 20));
    if (keep.length % 4 == 0) cloud.sleep(0);
  }
}

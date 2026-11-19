// Calls the host as fast as it can.
import { cloud } from "../sdk";
export { cccloud_handle } from "../sdk";

export function main(): void {
  let n = 0;
  while (true) {
    cloud.getEnergy();
    n++;
  }
}

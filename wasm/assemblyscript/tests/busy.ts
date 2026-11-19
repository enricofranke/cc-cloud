// Computes in short bursts between sleeps forever: throttled to its CPU share, never stopped by the limit per stretch.
import { cloud } from "../sdk";
export { cccloud_handle } from "../sdk";

export function main(): void {
  let n: f64 = 0;
  while (true) {
    for (let i = 0; i < 200000; i++) n += Math.sqrt(<f64>i);
    cloud.sleep(0);
  }
}

// Waits forever for events and also has a timer: only a stop ends it.
import { cloud, Value } from "../sdk";
export { cccloud_handle } from "../sdk";

function every(): void {
  cloud.log("every");
}

export function main(): void {
  cloud.every(0.25, every);
  while (true) {
    const e = cloud.wait("change", null, 5);
    cloud.log("woke " + e.isNull.toString());
  }
}

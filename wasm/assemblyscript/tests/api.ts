// Unit test module: reads the snapshot, acts, uses handlers, timers, the store and sleep.
import { cloud, Value } from "../sdk";
export { cccloud_handle } from "../sdk";

let changes = 0;

function onChange(e: Value): void {
  changes++;
  cloud.log("change " + e.get("widget").asString() + " " + e.get("value").toString());
}

function tick(): void {
  cloud.log("tick");
}

export function main(): void {
  const c = cloud.getCard("reactor");
  cloud.log("card " + c.get("title").asString() + " " + c.get("online").toString() + " " + c.get("widgets").length.toString()
    + " " + c.get("widgets").at(0).get("id").asString() + " " + c.get("widgets").at(0).get("unit").asString());
  cloud.log("value " + cloud.getValue("Reactor", "heat").toString() + " " + cloud.getValue("Reactor", "nope").toString() + " " + cloud.getCard("nope").toString());
  cloud.log("energy " + cloud.getEnergy().toString() + " " + cloud.getEnergyCapacity().toString() + " " + cloud.getMainframe().get("percent").toString());
  cloud.log("script " + cloud.script().get("name").asString() + " " + cloud.script().get("language").asString());
  const ok = cloud.set("Reactor", "active", false);
  cloud.log("set " + ok.ok.toString());
  const pin = cloud.press("Reactor", "scram");
  cloud.log("press " + pin.ok.toString() + " " + (pin.error != null ? pin.error! : "-"));
  cloud.log("items " + cloud.getItemCount("Reactor", "minecraft:iron_ingot").toString());
  cloud.store.set("count", 7);
  cloud.log("store " + cloud.store.get("count").toString() + " " + cloud.store.keys().toString());
  const obj = Value.object().set("a", "x\"y\n").set("n", 1.5).set("list", [1, 2, 3]);
  cloud.store.set("obj", obj);
  cloud.log("obj " + cloud.store.get("obj").toString());
  cloud.on("change", Value.object().set("widget", "heat"), onChange);
  cloud.after(0.1, tick);
  cloud.sleep(0.3);
  cloud.log("slept " + changes.toString());
}

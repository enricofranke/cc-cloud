// Porch light: when a Cloud Sensor on a card sees something, the Cloud Lamp on the same card turns on (and off again).
// Finds the first card with a sensor (indicator widget) and a lamp (toggle widget) by itself.
import { cloud, Value } from "../sdk";
export { cccloud_handle } from "../sdk";

let card = "";
let lamp = "";

function sensorChanged(e: Value): void {
  const value = e.get("value");
  const on = value.asBool() || (value.isNumber && value.asNumber() > 0);
  const r = cloud.set(card, lamp, on);
  cloud.log("lamp " + on.toString() + " " + r.ok.toString() + " " + (r.error != null ? r.error! : "nil"));
}

export function main(): void {
  const cards = cloud.getCards();
  for (let i = 0; i < cards.length; i++) {
    const c = cloud.getCard(cards.at(i).get("id").asString());
    let sensor = "";
    let toggle = "";
    const widgets = c.get("widgets");
    for (let w = 0; w < widgets.length; w++) {
      const type = widgets.at(w).get("type").asString();
      if (type == "indicator" && sensor == "") sensor = widgets.at(w).get("id").asString();
      if (type == "toggle" && toggle == "") toggle = widgets.at(w).get("id").asString();
    }
    if (sensor != "" && toggle != "") {
      card = c.get("id").asString();
      lamp = toggle;
      cloud.log("sees " + c.get("title").asString() + " " + widgets.length.toString());
      cloud.on("change", Value.object().set("card", card).set("widget", sensor), sensorChanged);
      return;
    }
  }
  cloud.warn("no card with a sensor and a lamp");
}

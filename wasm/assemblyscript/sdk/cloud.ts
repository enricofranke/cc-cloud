// Generated from src/main/resources/cccloud_code/api.json by tools/gen-api.mjs (npm run gen). Do not edit.
// API version 1.
import { Value } from "./value";
import { EventHandler, Result, TimerHandler, eventHandler, first, invoke, result, timerHandler } from "./host";

export namespace cloud {
  /**
   * Lists the dashboard's cards, at most 256.
   */
  export function getCards(): Value {
    return first(invoke("cloud.getCards", []));
  }

  /**
   * One card with all its widgets and values.
   */
  export function getCard(card: string): Value {
    return first(invoke("cloud.getCard", [Value.string(card)]));
  }

  /**
   * Reads one widget's current value.
   */
  export function getValue(card: string, widget: string): Value {
    return first(invoke("cloud.getValue", [Value.string(card), Value.string(widget)]));
  }

  /**
   * One widget with its type, label, value and limits.
   */
  export function getWidget(card: string, widget: string): Value {
    return first(invoke("cloud.getWidget", [Value.string(card), Value.string(widget)]));
  }

  /**
   * Without an argument: the mainframe is online. With a card: that device is online.
   */
  export function isOnline(card: string | null = null): bool {
    return first(invoke("cloud.isOnline", [Value.from(card)])).asBool();
  }

  /**
   * The devices linked to the mainframe (uplinks, bridges, relays, redstone devices).
   */
  export function getDevices(): Value {
    return first(invoke("cloud.getDevices", []));
  }

  /**
   * The mainframe: name, online, dashboard switch and power.
   */
  export function getMainframe(): Value {
    return first(invoke("cloud.getMainframe", []));
  }

  /**
   * The dashboard switch (as on the cloud_mainframe peripheral).
   */
  export function isEnabled(): bool {
    return first(invoke("cloud.isEnabled", [])).asBool();
  }

  /**
   * Stored FE in the mainframe's buffer.
   */
  export function getEnergy(): i64 {
    return <i64>first(invoke("cloud.getEnergy", [])).asNumber();
  }

  /**
   * Buffer size in FE.
   */
  export function getEnergyCapacity(): i64 {
    return <i64>first(invoke("cloud.getEnergyCapacity", [])).asNumber();
  }

  /**
   * Consumption in FE per tick.
   */
  export function getEnergyUsage(): i64 {
    return <i64>first(invoke("cloud.getEnergyUsage", [])).asNumber();
  }

  /**
   * Counts an item in the storage behind a storage card (AE2, Refined Storage). Waits for the server (one tick).
   */
  export function getItemCount(card: string, item: string): Value {
    return first(invoke("cloud.getItemCount", [Value.string(card), Value.string(item)]));
  }

  /**
   * The level of a wireless redstone channel of the network.
   */
  export function getRedstone(channel: string): Value {
    return first(invoke("cloud.getRedstone", [Value.string(channel)]));
  }

  /**
   * The mainframe's rules.
   */
  export function getRules(): Value {
    return first(invoke("cloud.getRules", []));
  }

  /**
   * The mainframe's scenes.
   */
  export function getScenes(): Value {
    return first(invoke("cloud.getScenes", []));
  }

  /**
   * Presses a button, like a click on the dashboard.
   * @param options {confirm=true} for widgets that ask for confirmation
   */
  export function press(card: string, widget: string, options: Value | null = null): Result {
    return result(invoke("cloud.press", [Value.string(card), Value.string(widget), Value.from(options)]));
  }

  /**
   * Sets a toggle, slider, select, number or text widget. Same checks as a person on the dashboard; PIN widgets are refused.
   * @param value boolean, number or string, as the widget takes it
   * @param options {confirm=true} for widgets that ask for confirmation
   */
  export function set<T>(card: string, widget: string, value: T, options: Value | null = null): Result {
    return result(invoke("cloud.set", [Value.string(card), Value.string(widget), Value.from(value), Value.from(options)]));
  }

  /**
   * Sets a redstone output of a computer card (automatic mode).
   * @param side top, bottom, left, right, front or back
   * @param level 0–15
   */
  export function setRedstone(card: string, side: string, level: i64): Result {
    return result(invoke("cloud.setRedstone", [Value.string(card), Value.string(side), Value.number(<f64>level)]));
  }

  /**
   * Requests a craft on a storage card, bounded by the server's craft limit.
   */
  export function craft(card: string, item: string, amount: i64): Result {
    return result(invoke("cloud.craft", [Value.string(card), Value.string(item), Value.number(<f64>amount)]));
  }

  /**
   * Switches the dashboard on or off (as on the cloud_mainframe peripheral). Scripts keep running.
   */
  export function setEnabled(enabled: bool): Result {
    return result(invoke("cloud.setEnabled", [Value.bool(enabled)]));
  }

  /**
   * Starts a scene. Every step is checked with the script's rights; scenes that need the PIN are refused.
   * @param scene Scene id or name
   * @param options {confirm=true} for scenes that ask for confirmation
   */
  export function runScene(scene: string, options: Value | null = null): Result {
    return result(invoke("cloud.runScene", [Value.string(scene), Value.from(options)]));
  }

  /**
   * Switches a rule on or off (as on the cloud_mainframe peripheral).
   * @param rule Rule id or name
   */
  export function setRuleEnabled(rule: string, enabled: bool): Result {
    return result(invoke("cloud.setRuleEnabled", [Value.string(rule), Value.bool(enabled)]));
  }

  /**
   * Runs a rule's actions now, like "Run now" on the dashboard.
   * @param rule Rule id or name
   */
  export function runRule(rule: string): Result {
    return result(invoke("cloud.runRule", [Value.string(rule)]));
  }

  /**
   * Sets a card's design in the dashboard layout, like the layout editor: background, custom CSS and card parts, checked like the editor's. It replaces the card's whole design, including the one its program sets with dash:style.
   * @param design {bg=, bgColor=, css=, parts={title=, gauge_fill=, button=, …}}; nil removes the card's own design
   */
  export function setStyle(card: string, design: Value | null = null): Result {
    return result(invoke("cloud.setStyle", [Value.string(card), Value.from(design)]));
  }

  /**
   * Calls the handler whenever the event happens. above/below fire once when the value crosses the threshold.
   * @param event change, above, below, online, offline or redstone
   * @param filter {card=, widget=} (above/below also value=), {channel=} for redstone
   * @param handler Called with the Event
   */
  export function on(event: string, filter: Value | null, handler: EventHandler): i64 {
    return <i64>first(invoke("cloud.on", [Value.string(event), Value.from(filter), eventHandler(handler)])).asNumber();
  }

  /**
   * Pauses until the event happens (like os.pullEvent, with a filter).
   * @param timeout Seconds; nil waits forever
   */
  export function wait(event: string, filter: Value | null = null, timeout: f64 = NaN): Value {
    return first(invoke("cloud.wait", [Value.string(event), Value.from(filter), Value.number(timeout)]));
  }

  /**
   * Removes an event handler or cancels a timer.
   */
  export function off(id: i64): bool {
    return first(invoke("cloud.off", [Value.number(<f64>id)])).asBool();
  }

  /**
   * Cancels a timer (the same as cloud.off).
   */
  export function cancel(id: i64): bool {
    return first(invoke("cloud.cancel", [Value.number(<f64>id)])).asBool();
  }

  /**
   * Pauses this script (or this handler) without using CPU. At least one tick (0.05 s).
   */
  export function sleep(seconds: f64): void {
    invoke("cloud.sleep", [Value.number(seconds)]);
  }

  /**
   * Calls the handler again and again. A run that is still busy is not started twice.
   * @param seconds At least 0.25
   */
  export function every(seconds: f64, handler: TimerHandler): i64 {
    return <i64>first(invoke("cloud.every", [Value.number(seconds), timerHandler(handler)])).asNumber();
  }

  /**
   * Calls the handler once after the delay.
   */
  export function after(seconds: f64, handler: TimerHandler): i64 {
    return <i64>first(invoke("cloud.after", [Value.number(seconds), timerHandler(handler)])).asNumber();
  }

  /**
   * Calls the handler at a time of day: real time, or in-game time (0 = 6:00, 13000 = dusk).
   * @param time "HH:MM" (every day, server time zone), {time="HH:MM", days={"mon","fri"}} or {gameTime=13000}
   */
  export function at<T>(time: T, handler: TimerHandler): i64 {
    return <i64>first(invoke("cloud.at", [Value.from(time), timerHandler(handler)])).asNumber();
  }

  /**
   * A toast on every dashboard and a push notification (as on the cloud_uplink peripheral).
   * @param level info (default), warn or crit
   */
  export function notify(text: string, level: string | null = null): bool {
    return first(invoke("cloud.notify", [Value.string(text), Value.from(level)])).asBool();
  }

  /**
   * The same as cloud.notify.
   */
  export function alert(text: string, level: string | null = null): bool {
    return first(invoke("cloud.alert", [Value.string(text), Value.from(level)])).asBool();
  }

  /**
   * Only a toast on the open dashboards, no push.
   */
  export function toast(text: string, level: string | null = null): bool {
    return first(invoke("cloud.toast", [Value.string(text), Value.from(level)])).asBool();
  }

  /**
   * Only a push notification to the mainframe's push targets.
   * @param title Default: the script's name
   */
  export function push(text: string, level: string | null = null, title: string | null = null): bool {
    return first(invoke("cloud.push", [Value.string(text), Value.from(level), Value.from(title)])).asBool();
  }

  /**
   * Writes a line to the script's log (print does the same).
   */
  export function log<T>(message: T): void {
    invoke("cloud.log", [Value.from(message)]);
  }

  /**
   * Writes a warning to the script's log.
   */
  export function warn<T>(message: T): void {
    invoke("cloud.warn", [Value.from(message)]);
  }

  /**
   * The real time.
   */
  export function time(): i64 {
    return <i64>first(invoke("cloud.time", [])).asNumber();
  }

  /**
   * Seconds since the script started.
   */
  export function clock(): f64 {
    return first(invoke("cloud.clock", [])).asNumber();
  }

  /**
   * The in-game time of day.
   */
  export function gameTime(): i64 {
    return <i64>first(invoke("cloud.gameTime", [])).asNumber();
  }

  /**
   * This script: id, name, author, version.
   */
  export function script(): Value {
    return first(invoke("cloud.script", []));
  }

  export namespace store {
    /**
     * Reads a stored value (it survives restarts and new versions).
     */
    export function get(key: string): Value {
      return first(invoke("cloud.store.get", [Value.string(key)]));
    }

    /**
     * Stores a value for this script (nil deletes it). Small: see the limits.
     * @param value nil, boolean, number, string or a table of those
     */
    export function set<T>(key: string, value: T): bool {
      return first(invoke("cloud.store.set", [Value.string(key), Value.from(value)])).asBool();
    }

    /**
     * Deletes a stored value.
     */
    export function remove(key: string): bool {
      return first(invoke("cloud.store.delete", [Value.string(key)])).asBool();
    }

    /**
     * Every stored key.
     */
    export function keys(): Value {
      return first(invoke("cloud.store.keys", []));
    }
  }
}

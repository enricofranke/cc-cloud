package ai.ezfox.cccloud.production;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;

/**
 * The energy flow of a mainframe's energy cards (docs/SPEC.md v0.10): every sample sums what the cards report (FE,
 * FE/t) and keeps rings of the total in/out. Pure Java, unit tested.
 */
public final class EnergyTotals {
    /** One energy card at sample time. */
    public record Card(String sectionId, String title, long stored, long capacity, double in, double out, boolean measured) {
    }

    private final RateRing in;
    private final RateRing out;
    private List<Card> cards = List.of();

    public EnergyTotals(int window) {
        this.in = new RateRing(window);
        this.out = new RateRing(window);
    }

    public void sample(List<Card> readings) {
        cards = List.copyOf(readings);
        double i = 0, o = 0;
        for (var card : readings) {
            i += card.in();
            o += card.out();
        }
        in.add(RateRing.round(i));
        out.add(RateRing.round(o));
    }

    public boolean empty() {
        return cards.isEmpty();
    }

    /** Net FE/t of the last sample (pinned charts). */
    public double lastNet() {
        return in.size() == 0 ? 0 : RateRing.round(in.get(in.size() - 1) - out.get(out.size() - 1));
    }

    /** {@code {cards:[…], stored, capacity, in, out, series:{in, out}}} (FE and FE/t). */
    public JsonObject json() {
        var json = new JsonObject();
        var array = new JsonArray();
        long stored = 0, capacity = 0;
        double i = 0, o = 0;
        for (var card : cards) {
            var c = new JsonObject();
            c.addProperty("uplinkId", card.sectionId());
            c.addProperty("title", card.title());
            c.addProperty("stored", card.stored());
            c.addProperty("capacity", card.capacity());
            c.addProperty("in", RateRing.round(card.in()));
            c.addProperty("out", RateRing.round(card.out()));
            c.addProperty("measured", card.measured());
            array.add(c);
            stored += card.stored();
            capacity += card.capacity();
            i += card.in();
            o += card.out();
        }
        json.add("cards", array);
        json.addProperty("stored", stored);
        json.addProperty("capacity", capacity);
        json.addProperty("in", RateRing.round(i));
        json.addProperty("out", RateRing.round(o));
        var series = new JsonObject();
        series.add("in", values(in));
        series.add("out", values(out));
        json.add("series", series);
        return json;
    }

    private static JsonArray values(RateRing ring) {
        var array = new JsonArray();
        for (int k = 0; k < ring.size(); k++) array.add(ring.get(k));
        return array;
    }
}

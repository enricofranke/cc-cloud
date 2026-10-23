package ai.ezfox.cccloud.compat.merequester;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.bridge.ItemNames;
import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.uplink.Limits;
import ai.ezfox.cccloud.uplink.Widget;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.almostreliable.merequester.requester.Request;
import com.almostreliable.merequester.requester.RequesterBlockEntity;
import com.google.gson.JsonElement;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * ME Requester: the requests of one requester block with their target amounts. Each configured request gets an
 * enable toggle, an amount input and its status; enabling/disabling and changing amounts work from the dashboard
 * (canControl). Which items are requested is still chosen in-game; when that changes, the card rebuilds itself.
 */
public final class MeRequesterCards {
    /** At most this many requests on the card (3 widgets each, 32 widgets per uplink). */
    public static final int MAX_SHOWN = 10;
    static final Pattern WIDGET = Pattern.compile("req(\\d+)_(on|amount)");

    private MeRequesterCards() {
    }

    public static void init() {
        DeviceCards.register("merequester", DeviceCards.SPECIFIC, MeRequesterCards::detect);
        Integrations.test("merequester_requests", () -> MeRequesterGameTests::requests);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof RequesterBlockEntity requester ? new Requester(level, pos, side, requester) : null;
    }

    static String status(Request request) {
        return switch (request.getClientStatus().name()) {
            case "IDLE" -> request.getState() ? "Stocked" : "Disabled";
            case "MISSING" -> "Missing ingredients";
            case "CPU" -> "No crafting CPU";
            case "REQUEST", "PLAN" -> "Planning";
            case "LINK" -> "Crafting";
            case "EXPORT" -> "Exporting";
            default -> Fmt.title(request.getClientStatus().name().toLowerCase(java.util.Locale.ROOT));
        };
    }

    static String name(AEKey key) {
        String name = key instanceof AEItemKey item ? ItemNames.displayName(item.getItem()) : key.getDisplayName().getString();
        return name.length() > Limits.LABEL ? name.substring(0, Limits.LABEL) : name;
    }

    static final class Requester extends BlockCard<RequesterBlockEntity> {
        Requester(ServerLevel level, BlockPos pos, Direction side, RequesterBlockEntity requester) {
            super(level, pos, side, requester);
        }

        @Override
        public String kind() {
            return "merequester";
        }

        @Override
        public String title() {
            return "ME Requester";
        }

        /** Changes when a request slot gets a different item (or is cleared). */
        @Override
        public int revision() {
            var requests = be.getRequestManager();
            int hash = 1;
            for (int i = 0; i < requests.size(); i++) hash = 31 * hash + Objects.hashCode(requests.getKey(i));
            return hash & 0x7fffffff;
        }

        @Override
        public List<Widget> widgets() {
            var w = new CardWidgets();
            var requests = be.getRequestManager();
            int shown = 0;
            for (int i = 0; i < requests.size() && shown < MAX_SHOWN; i++) {
                var key = requests.getKey(i);
                if (key == null) continue;
                shown++;
                w.toggle("req" + i + "_on", name(key), "order", i * 3);
                w.number("req" + i + "_amount", "Keep in stock", 1, Integer.MAX_VALUE, 1, "order", i * 3 + 1);
                w.label("req" + i + "_status", "Status", "order", i * 3 + 2);
            }
            if (shown == 0) w.label("empty", "Requests");
            return w.build();
        }

        @Override
        public void refresh(CardOutput out) {
            var requests = be.getRequestManager();
            int shown = 0;
            for (int i = 0; i < requests.size() && shown < MAX_SHOWN; i++) {
                var request = requests.get(i);
                if (request.getKey() == null) continue;
                shown++;
                out.bool("req" + i + "_on", request.getState());
                out.number("req" + i + "_amount", Math.min(request.getAmount(), Integer.MAX_VALUE));
                out.text("req" + i + "_status", status(request));
            }
            if (shown == 0) out.text("empty", "No requests configured – choose items in the requester");
        }

        @Override
        public @Nullable String action(String widgetId, JsonElement value, String user) {
            Matcher m = WIDGET.matcher(widgetId);
            if (!m.matches()) return "not_interactive";
            int index = Integer.parseInt(m.group(1));
            var requests = be.getRequestManager();
            if (index >= requests.size() || requests.getKey(index) == null) return "unavailable";
            var request = requests.get(index);
            if (request.getClientStatus().locksRequest()) return "busy";
            if (m.group(2).equals("on")) {
                request.updateState(value.getAsBoolean());
            } else {
                long amount = value.getAsLong();
                if (amount < 1) return "invalid_value";
                request.updateAmount(amount);
            }
            CCCloud.LOGGER.info("[cccloud] {} set ME request {} ({}) at {} to {}", user, index, name(requests.getKey(index)), pos.toShortString(), value);
            return null;
        }
    }
}

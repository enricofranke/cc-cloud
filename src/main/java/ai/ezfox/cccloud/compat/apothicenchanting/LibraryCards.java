package ai.ezfox.cccloud.compat.apothicenchanting;

import ai.ezfox.cccloud.compat.Integrations;
import ai.ezfox.cccloud.compat.card.BlockCard;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.DeviceCard;
import ai.ezfox.cccloud.compat.card.DeviceCards;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.uplink.Widget;
import dev.shadowsoffire.apothic_enchanting.library.EnchLibraryTile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jspecify.annotations.Nullable;

/**
 * Apothic Enchanting (wave 5): the <b>Library of Alexandria</b> and <b>Library of the End</b> – how many enchantments are
 * stored and the fullest ones: level that can be taken out against the highest level seen, and the points behind it.
 * The widget set follows the number of rows (only rows with data). Read-only: books are deposited and taken at the block.
 */
public final class LibraryCards {
    public static final String COUNT = "count";
    public static final String POINTS = "points";
    public static final String ROW = "row";
    public static final String EMPTY = "empty";
    static final int ROWS = 8;

    private record Row(Holder<Enchantment> enchantment, int points, int level) {
    }

    private LibraryCards() {
    }

    public static void init() {
        DeviceCards.register("apothic_enchanting", DeviceCards.SPECIFIC, LibraryCards::detect);
        Integrations.test("apothicenchanting_library", () -> LibraryGameTests::library);
    }

    static @Nullable DeviceCard detect(ServerLevel level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof EnchLibraryTile library ? new Library(level, pos, side, library) : null;
    }

    static String name(Holder<Enchantment> enchantment) {
        return enchantment.unwrapKey().map(key -> Fmt.title(key.identifier().getPath())).orElse("?");
    }

    static final class Library extends BlockCard<EnchLibraryTile> {
        private int rows;

        Library(ServerLevel level, BlockPos pos, Direction side, EnchLibraryTile library) {
            super(level, pos, side, library);
        }

        @Override
        public String kind() {
            return "apothicenchanting_library";
        }

        @Override
        public String title() {
            return blockTitle();
        }

        @Override
        public int revision() {
            return rows;
        }

        @Override
        public List<Widget> widgets() {
            var w = new CardWidgets()
                .label(COUNT, "Enchantments", "span", 2)
                .label(POINTS, "Points", "span", 2);
            if (rows == 0) w.label(EMPTY, "Stored", "span", 2);
            for (int i = 1; i <= rows; i++) w.label(ROW + i, i == 1 ? "Fullest" : "Then", "span", 2);
            return w.build();
        }

        @Override
        public void refresh(CardOutput out) {
            var sorted = new ArrayList<Row>();
            long total = 0;
            for (var entry : be.getPointsMap().object2IntEntrySet()) {
                int points = entry.getIntValue();
                if (points <= 0) continue;
                total += points;
                sorted.add(new Row(entry.getKey(), points, be.getMax(entry.getKey())));
            }
            sorted.sort(Comparator.comparingInt(Row::points).reversed().thenComparing(r -> name(r.enchantment())));
            rows = Math.min(ROWS, sorted.size());
            out.text(COUNT, String.valueOf(sorted.size()));
            out.text(POINTS, Fmt.si(total));
            if (rows == 0) out.text(EMPTY, "No enchantments yet");
            for (int i = 1; i <= rows; i++) {
                var row = sorted.get(i - 1);
                out.text(ROW + i, name(row.enchantment()) + " " + row.level() + " (" + Fmt.si(row.points()) + " pts)");
            }
        }
    }
}

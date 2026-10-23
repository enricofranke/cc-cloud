package ai.ezfox.cccloud.compat.card;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class GenericCardsTest {
    @Test
    void configListMatchesBlocksAndWholeMods() {
        var list = List.of("evilcraft:dark_tank", "stellaris:*");
        assertTrue(GenericCards.excluded("evilcraft:dark_tank", list));
        assertFalse(GenericCards.excluded("evilcraft:blood_infuser", list));
        assertTrue(GenericCards.excluded("stellaris:fuel_tank", list));
        assertFalse(GenericCards.excluded("stellarisx:fuel_tank", list));
        assertFalse(GenericCards.excluded("minecraft:cauldron", List.of()));
    }
}

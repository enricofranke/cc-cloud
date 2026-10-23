package ai.ezfox.cccloud.compat.card;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.compat.IntegrationConfig;
import java.util.Collection;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Opt-out for the generic cards (Energy, Tank): pack makers exclude false positives with the block tag
 * {@code #cccloud:no_generic_card} or the config list {@code cards.genericCardExcludes} ({@code mod:block} or {@code mod:*}).
 */
public final class GenericCards {
    public static final TagKey<Block> NO_GENERIC_CARD = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(CCCloud.MODID, "no_generic_card"));

    private GenericCards() {
    }

    public static boolean excluded(BlockState state, Identifier blockId) {
        return state.is(NO_GENERIC_CARD) || excluded(blockId.toString(), IntegrationConfig.get(IntegrationConfig.GENERIC_CARD_EXCLUDES));
    }

    /** Pure: {@code mod:block} matches exactly, {@code mod:*} every block of the mod. */
    public static boolean excluded(String blockId, Collection<? extends String> list) {
        String namespace = blockId.substring(0, Math.max(0, blockId.indexOf(':')));
        for (String entry : list) {
            if (entry.equals(blockId) || entry.equals(namespace + ":*")) return true;
        }
        return false;
    }
}

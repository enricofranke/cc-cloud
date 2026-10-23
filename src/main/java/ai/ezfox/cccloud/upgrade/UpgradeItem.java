package ai.ezfox.cccloud.upgrade;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

/**
 * A mainframe upgrade: one of the 15 cards ({@code type}+{@code tier}), the Quantum Core or the Chunk Anchor card.
 * Goes into the mainframe's upgrade slots (screen, Upgrades tab); hoppers cannot insert.
 */
public class UpgradeItem extends Item {
    public enum Kind { CARD, QUANTUM_CORE, CHUNK_ANCHOR }

    private final Kind kind;
    private final @Nullable UpgradeType type;
    private final @Nullable UpgradeTier tier;

    public UpgradeItem(Kind kind, @Nullable UpgradeType type, @Nullable UpgradeTier tier, Properties properties) {
        super(properties);
        this.kind = kind;
        this.type = type;
        this.tier = tier;
    }

    public Kind kind() {
        return kind;
    }

    public @Nullable UpgradeType type() {
        return type;
    }

    public @Nullable UpgradeTier tier() {
        return tier;
    }

    /** Registry name of a card, e.g. {@code upgrade_capacity_vibranium}. */
    public static String cardId(UpgradeType type, UpgradeTier tier) {
        return "upgrade_" + type.id() + "_" + tier.id();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        String key = switch (kind) {
            case CARD -> "cccloud.upgrade.effect." + type.id() + "." + tier.id();
            case QUANTUM_CORE -> "cccloud.upgrade.effect.quantum_core";
            case CHUNK_ANCHOR -> "cccloud.upgrade.effect.chunk_anchor";
        };
        out.accept(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("cccloud.upgrade.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}

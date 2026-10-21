package ai.ezfox.cccloud.client;

import ai.ezfox.cccloud.CCCloud;
import dan200.computercraft.api.client.StandaloneModel;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.cuboid.MissingCuboidModel;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import org.jspecify.annotations.Nullable;

/**
 * The separately animated parts (see docs/MODELS.md). They are loaded as standalone models; a part whose model file
 * is missing resolves to {@code null} and is simply not drawn.
 */
public enum CloudModels {
    MAINFRAME_FAN("block/mainframe_fan"),
    MAINFRAME_HOLOGRAM("block/mainframe_hologram"),
    UPLINK_RING("block/uplink_ring"),
    UPLINK_ADVANCED_CORE("block/uplink_advanced_core");

    private final Identifier id;
    private final StandaloneModelKey<Optional<StandaloneModel>> key;

    CloudModels(String path) {
        this.id = Identifier.fromNamespaceAndPath(CCCloud.MODID, path);
        this.key = new StandaloneModelKey<>(id::toString);
    }

    public @Nullable StandaloneModel get() {
        Optional<StandaloneModel> model = Minecraft.getInstance().getModelManager().getStandaloneModel(key);
        return model == null ? null : model.orElse(null);
    }

    static void register(ModelEvent.RegisterStandalone event) {
        for (var part : values()) {
            event.register(part.key, new SimpleUnbakedStandaloneModel<>(part.id, (model, baker, name) ->
                model.debugName().equals(MissingCuboidModel.LOCATION.toString())
                    ? Optional.empty()
                    : Optional.of(StandaloneModel.of(model, baker))));
        }
    }
}

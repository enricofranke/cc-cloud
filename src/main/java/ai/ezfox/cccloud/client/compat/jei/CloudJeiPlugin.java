package ai.ezfox.cccloud.client.compat.jei;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.client.gui.CloudScreen;
import ai.ezfox.cccloud.client.gui.MainframeScreen;
import ai.ezfox.cccloud.client.gui.UplinkScreen;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;

/**
 * Soft JEI integration (only loaded by JEI itself): our screens report the panel they draw so JEI's item list stays
 * out of it. compileOnly against the JEI API; without JEI this class is never touched.
 */
@JeiPlugin
public final class CloudJeiPlugin implements IModPlugin {
    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(CCCloud.MODID, "screens");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(MainframeScreen.class, new Handler<>());
        registration.addGuiContainerHandler(UplinkScreen.class, new Handler<>());
    }

    private static final class Handler<T extends CloudScreen<?>> implements IGuiContainerHandler<T> {
        @Override
        public List<Rect2i> getGuiExtraAreas(T screen) {
            return List.of(screen.panelArea());
        }
    }
}

package ai.ezfox.cccloud.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;

/**
 * KubeJS plugin (listed in {@code kubejs.plugins.txt}, so KubeJS only loads it when KubeJS is installed): the event group
 * {@code CCCloudEvents} (server scripts) and the binding {@code CCCloud} (server scripts).
 */
public final class CCCloudKubeJSPlugin implements KubeJSPlugin {
    @Override
    public void init() {
        CCCloudKubeEvents.hook();
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(CCCloudKubeEvents.GROUP);
    }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        if (bindings.type().isServer()) bindings.add("CCCloud", CCCloudJS.INSTANCE);
    }
}

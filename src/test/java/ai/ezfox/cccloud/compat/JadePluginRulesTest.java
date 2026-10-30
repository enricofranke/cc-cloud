package ai.ezfox.cccloud.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;

/**
 * Jade (26.1.12, the ATM11 version) refuses a server data provider that also implements {@code IComponentProvider}
 * since Minecraft 1.21.6, and then loads none of the plugin – only on a physical client, so dev servers and GameTests
 * never see it. This reads the compiled classes of {@code compat/jade} (Jade is compile-only, so nothing is loaded) and
 * checks that rule.
 */
class JadePluginRulesTest {
    private static final String PACKAGE = "ai/ezfox/cccloud/compat/jade/";
    private static final String DATA = "snownee/jade/api/IServerDataProvider";
    private static final List<String> COMPONENTS = List.of("snownee/jade/api/IComponentProvider", "snownee/jade/api/IBlockComponentProvider",
        "snownee/jade/api/IEntityComponentProvider");

    @Test
    void noDataProviderIsAlsoAComponentProvider() throws Exception {
        var url = getClass().getClassLoader().getResource(PACKAGE + "CloudJadePlugin.class");
        assertNotNull(url, "compat/jade classes not on the test classpath");
        var dir = Path.of(URI.create(url.toString())).getParent();
        var checked = new ArrayList<String>();
        try (var files = Files.list(dir)) {
            for (var file : files.filter(f -> f.toString().endsWith(".class")).toList()) {
                List<String> interfaces;
                try (InputStream in = Files.newInputStream(file)) {
                    interfaces = List.of(new ClassReader(in).getInterfaces());
                }
                checked.add(file.getFileName().toString());
                if (interfaces.contains(DATA)) {
                    for (var component : COMPONENTS) {
                        assertFalse(interfaces.contains(component), file.getFileName() + " is a data provider and implements " + component);
                    }
                }
            }
        }
        assertTrue(checked.contains("NetworkJadeProvider.class") && checked.contains("NetworkJadeProvider$Tooltip.class"), "classes checked: " + checked);
    }
}

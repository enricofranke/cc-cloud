package ai.ezfox.cccloud.computer.block;

import ai.ezfox.cccloud.CCCloud;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * World SavedData ({@code data/cccloud/cloud_computer_owners.dat}): the owner of each Cloud Computer id, so a computer
 * item carried to another place only restores its computer for that owner ({@link ComputerAccess#mayRestore}). Server
 * thread only.
 */
public final class ComputerOwners extends SavedData {
    private static final Codec<ComputerOwners> CODEC = Codec.unboundedMap(Codec.STRING, UUIDUtil.STRING_CODEC)
        .xmap(ComputerOwners::new, ComputerOwners::encode);

    public static final SavedDataType<ComputerOwners> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(CCCloud.MODID, "cloud_computer_owners"),
        ComputerOwners::new, CODEC);

    private final Map<Integer, UUID> owners = new HashMap<>();

    public ComputerOwners() {
    }

    private ComputerOwners(Map<String, UUID> saved) {
        for (var e : saved.entrySet()) {
            try {
                owners.put(Integer.parseInt(e.getKey()), e.getValue());
            } catch (NumberFormatException ignored) {
                // a hand-edited key: skip it
            }
        }
    }

    private Map<String, UUID> encode() {
        var out = new HashMap<String, UUID>();
        owners.forEach((id, owner) -> out.put(Integer.toString(id), owner));
        return out;
    }

    public static ComputerOwners get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public @Nullable UUID owner(int id) {
        return owners.get(id);
    }

    public void set(int id, @Nullable UUID owner) {
        var old = owner == null ? owners.remove(id) : owners.put(id, owner);
        if (!java.util.Objects.equals(old, owner)) setDirty();
    }
}

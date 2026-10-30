package ai.ezfox.cccloud.map;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The map tile disk cache ({@code <world>/cccloud/maptiles}): a purged mainframe's slice folders are deleted in every
 * dimension, everything else is capped by age ({@code mapTileMaxAgeDays}); tiles in the memory cache, other files and
 * anything behind a symbolic link stay.
 */
class MapTileDiskTest {
    private static final long DAY = 86_400_000L;

    private static Path tile(Path root, String path, long ageDays) throws IOException {
        var file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[]{1, 2, 3});
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() - ageDays * DAY));
        return file;
    }

    @Test
    void purgeDeletesOnlyThatMainframesSlices(@TempDir Path root) throws IOException {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), player = UUID.randomUUID();
        tile(root, "minecraft_overworld/slice_-60_" + a + "_" + player + "/0_0.png", 0);
        tile(root, "minecraft_overworld/slice_-60_" + a + "_" + player + "/-1_3.png", 0);
        tile(root, "minecraft_overworld/slice_12_" + a + "_" + UUID.randomUUID() + "/0_0.png", 0);
        tile(root, "minecraft_the_nether/slice_40_" + a + "_" + player + "/2_2.png", 0);
        var other = tile(root, "minecraft_overworld/slice_-60_" + b + "_" + player + "/0_0.png", 0);
        var surface = tile(root, "minecraft_overworld/surface/0_0.png", 0);

        assertEquals(4, MapTileService.deleteSlices(root, a));
        try (var left = Files.list(root.resolve("minecraft_overworld"))) {
            assertTrue(left.noneMatch(p -> p.getFileName().toString().contains(a.toString())), "no slice folder of the purged mainframe left");
        }
        assertFalse(Files.exists(root.resolve("minecraft_the_nether")), "an emptied dimension folder goes too");
        assertTrue(Files.exists(other), "another mainframe's slices stay");
        assertTrue(Files.exists(surface), "surface tiles are shared and stay");
        assertEquals(0, MapTileService.deleteSlices(root, a), "nothing left the second time");
        assertEquals(0, MapTileService.deleteSlices(root.resolve("missing"), a), "no cache folder yet");
    }

    @Test
    void purgeNeverFollowsLinks(@TempDir Path root, @TempDir Path outside) throws IOException {
        UUID a = UUID.randomUUID();
        var precious = tile(outside, "keep.png", 0);
        Files.createDirectories(root.resolve("minecraft_overworld"));
        try {
            Files.createSymbolicLink(root.resolve("minecraft_overworld/slice_0_" + a + "_x"), outside);
        } catch (UnsupportedOperationException | IOException e) {
            return; // no symbolic links on this file system
        }
        MapTileService.deleteSlices(root, a);
        assertTrue(Files.exists(precious), "a linked folder is not entered");
    }

    @Test
    void sweepDeletesTilesNotUsedForTheMaxAge(@TempDir Path root) throws IOException {
        long cutoff = System.currentTimeMillis() - 30 * DAY;
        UUID mf = UUID.randomUUID(), player = UUID.randomUUID();
        var oldSurface = tile(root, "minecraft_overworld/surface/0_0.png", 45);
        var recentSurface = tile(root, "minecraft_overworld/surface/1_0.png", 2);
        var oldSlice = tile(root, "minecraft_overworld/slice_-60_" + mf + "_" + player + "/0_0.png", 31);
        var oldTemp = tile(root, "minecraft_overworld/surface/2_0.png.tmp", 40);
        var inMemory = tile(root, "minecraft_overworld/surface/3_0.png", 90);
        var notATile = tile(root, "minecraft_overworld/surface/readme.txt", 400);

        int deleted = MapTileService.sweep(root, cutoff, Set.of(inMemory));
        assertEquals(3, deleted);
        assertFalse(Files.exists(oldSurface));
        assertFalse(Files.exists(oldSlice));
        assertFalse(Files.exists(oldTemp), "left-over temp files go too");
        assertFalse(Files.exists(oldSlice.getParent()), "the emptied slice folder is removed");
        assertTrue(Files.exists(recentSurface), "recently used tiles stay");
        assertTrue(Files.exists(inMemory), "tiles in the memory cache stay");
        assertTrue(Files.exists(notATile), "only tiles are deleted");
        assertTrue(Files.isDirectory(root), "the cache folder itself stays");

        // everything old: the folders go, the root stays
        Files.delete(notATile);
        assertEquals(2, MapTileService.sweep(root, System.currentTimeMillis() + DAY, Set.of()));
        try (var left = Files.list(root)) {
            assertEquals(0, left.count());
        }
        assertEquals(0, MapTileService.sweep(root.resolve("missing"), cutoff, Set.of()), "no cache folder yet");
    }
}

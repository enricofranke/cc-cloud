package ai.ezfox.cccloud.voxel;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The voxel disk cache ({@code <world>/cccloud/voxels}): round trip, strict reading of damaged files, which build stays,
 * the age and size caps, the purge and the writer's bounded queue.
 */
class VoxelDiskTest {
    private static final String DIM = "minecraft:overworld";
    private static final long DAY = 86_400_000L;
    private static final VoxelDisk.Limits NO_LIMITS = new VoxelDisk.Limits(0, Long.MAX_VALUE, Long.MAX_VALUE);

    private static VoxelDisk disk(Path root, VoxelDisk.Limits limits) {
        return new VoxelDisk(root, 3, () -> limits);
    }

    private static VoxelDisk.Stored stored(long self, long content, int known) {
        return new VoxelDisk.Stored(self, content, known, VoxelBatchTest.room(true));
    }

    private static void put(VoxelDisk disk, UUID mainframe, int cx, int cz, Map<Integer, VoxelDisk.Stored> sections) {
        assertTrue(disk.put(mainframe, DIM, cx, cz, sections));
        assertTrue(disk.flush(10_000));
    }

    private static List<Path> columns(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".bin")).toList();
        }
    }

    private static void age(Path file, long days) throws IOException {
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() - days * DAY));
    }

    @Test
    void roundTrip(@TempDir Path root) {
        var disk = disk(root, NO_LIMITS);
        UUID mf = UUID.randomUUID();
        var section = stored(1, 2, 0b111111);
        put(disk, mf, -3, 7, Map.of(-4, section, 5, stored(3, 4, 0b1)));
        var read = disk.column(mf, DIM, -3, 7);
        assertEquals(2, read.size());
        var back = read.get(-4);
        assertEquals(section.self(), back.self());
        assertEquals(section.content(), back.content());
        assertEquals(section.known(), back.known());
        assertEquals(section.section().hash(), back.section().hash(), "the content hash comes back");
        assertEquals(section.section().exposed(), back.section().exposed());
        assertEquals(section.section().palette(), back.section().palette());
        assertArrayEquals(section.section().runs(), back.section().runs());
        assertTrue(disk.column(mf, DIM, -3, 8).isEmpty(), "nothing for another column");
        assertTrue(disk.column(UUID.randomUUID(), DIM, -3, 7).isEmpty(), "nothing for another mainframe");
        assertTrue(disk.column(mf, "minecraft:the_nether", -3, 7).isEmpty(), "nothing for another dimension");
        disk.close(1000);
    }

    @Test
    void anEmptySectionRoundTrips(@TempDir Path root) {
        var disk = disk(root, NO_LIMITS);
        UUID mf = UUID.randomUUID();
        var air = VoxelSection.encodeGrid(new int[VoxelSection.GRID * VoxelSection.GRID * VoxelSection.GRID], List.of());
        put(disk, mf, 0, 0, Map.of(2, new VoxelDisk.Stored(1, 1, 63, air)));
        var back = disk.column(mf, DIM, 0, 0).get(2);
        assertEquals(0, back.section().exposed());
        assertEquals(air.hash(), back.section().hash());
        disk.close(1000);
    }

    @Test
    void dimensionsNeverShareAFolder(@TempDir Path root) {
        var disk = disk(root, NO_LIMITS);
        UUID mf = UUID.randomUUID();
        assertTrue(disk.put(mf, "a_b:c", 0, 0, Map.of(1, stored(1, 1, 63))));
        assertTrue(disk.flush(10_000));
        assertTrue(disk.column(mf, "a:b_c", 0, 0).isEmpty(), "the replacement of ':' must not collide");
        assertFalse(disk.column(mf, "a_b:c", 0, 0).isEmpty());
        disk.close(1000);
    }

    @Test
    void sectionsMergeIntoTheColumn(@TempDir Path root) throws IOException {
        var disk = disk(root, NO_LIMITS);
        UUID mf = UUID.randomUUID();
        put(disk, mf, 1, 1, Map.of(0, stored(1, 1, 63)));
        put(disk, mf, 1, 1, Map.of(1, stored(2, 2, 63)));
        put(disk, mf, 1, 1, Map.of(0, stored(9, 9, 63)));
        var read = disk.column(mf, DIM, 1, 1);
        assertEquals(2, read.size(), "one file per column");
        assertEquals(9, read.get(0).self(), "a changed section replaces the old one");
        assertEquals(2, read.get(1).self(), "the other section stays");
        assertEquals(1, columns(root).size());
        disk.close(1000);
    }

    @Test
    void aSectionWithFewerNeighborsKeepsTheBetterBuild() {
        var old = stored(1, 10, 0b111111);
        assertTrue(VoxelDisk.keeps(old, stored(1, 10, 0b111111)), "nothing changed");
        assertTrue(VoxelDisk.keeps(old, stored(1, 11, 0b110111)), "same blocks, one neighbor is not loaded now");
        assertFalse(VoxelDisk.keeps(old, stored(2, 12, 0b110111)), "other blocks always win");
        assertFalse(VoxelDisk.keeps(old, stored(1, 11, 0b111111)), "same blocks, a neighbor changed");
        assertFalse(VoxelDisk.keeps(stored(1, 10, 0b110111), stored(1, 11, 0b111111)), "a build that knows more wins");
        assertFalse(VoxelDisk.keeps(stored(1, 10, 0b110111), stored(1, 11, 0b101111)), "it knows another neighbor");
    }

    @Test
    void damagedFilesCountAsEmptyAndAreReplaced(@TempDir Path root) throws IOException {
        var disk = disk(root, NO_LIMITS);
        UUID mf = UUID.randomUUID();
        put(disk, mf, 2, 2, Map.of(0, stored(1, 1, 63)));
        var file = columns(root).getFirst();
        byte[] good = Files.readAllBytes(file);
        Files.write(file, java.util.Arrays.copyOf(good, good.length / 2));
        assertTrue(disk.column(mf, DIM, 2, 2).isEmpty(), "cut off");
        Files.write(file, new byte[]{'C', 'C', 'V'});
        assertTrue(disk.column(mf, DIM, 2, 2).isEmpty(), "too short");
        byte[] bad = good.clone();
        bad[0] = 'X';
        Files.write(file, bad);
        assertTrue(disk.column(mf, DIM, 2, 2).isEmpty(), "not our file");
        put(disk, mf, 2, 2, Map.of(0, stored(5, 5, 63)));
        assertEquals(5, disk.column(mf, DIM, 2, 2).get(0).self(), "the next build replaces it");
        disk.close(1000);
    }

    @Test
    void anotherLogicVersionCountsAsEmpty(@TempDir Path root) {
        var disk = disk(root, NO_LIMITS);
        UUID mf = UUID.randomUUID();
        put(disk, mf, 0, 0, Map.of(0, stored(1, 1, 63)));
        disk.close(1000);
        assertTrue(new VoxelDisk(root, 4, () -> NO_LIMITS).column(mf, DIM, 0, 0).isEmpty(), "sections of an older build logic are not served");
    }

    @Test
    void decodingIsStrict() throws IOException {
        byte[] good = VoxelDisk.encode(new TreeMap<>(Map.of(0, stored(1, 1, 63))), 3);
        assertEquals(1, VoxelDisk.decode(good, 3).size());
        // a palette entry fewer than the runs use
        var room = VoxelBatchTest.room(true);
        var tooSmall = new VoxelSection.Encoded(room.palette().subList(0, 1), room.runs(), room.exposed(), room.hash());
        byte[] bad = VoxelDisk.encode(new TreeMap<>(Map.of(0, new VoxelDisk.Stored(1, 1, 63, tooSmall))), 3);
        assertThrows(IOException.class, () -> VoxelDisk.decode(bad, 3), "an index out of the palette");
        var broken = new VoxelSection.Encoded(room.palette(), new byte[]{1, 1}, room.exposed(), room.hash());
        byte[] shortRuns = VoxelDisk.encode(new TreeMap<>(Map.of(0, new VoxelDisk.Stored(1, 1, 63, broken))), 3);
        assertThrows(RuntimeException.class, () -> VoxelDisk.decode(shortRuns, 3), "runs that do not cover the section");
    }

    @Test
    void purgeDeletesOnlyThatMainframe(@TempDir Path root) {
        var disk = disk(root, NO_LIMITS);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        put(disk, a, 0, 0, Map.of(0, stored(1, 1, 63)));
        put(disk, b, 0, 0, Map.of(0, stored(2, 2, 63)));
        disk.saveArea(a, () -> VoxelBatch.gzip(new byte[]{1}));
        disk.purge(a);
        assertTrue(disk.flush(10_000));
        assertFalse(Files.exists(root.resolve(a.toString())), "its folder goes");
        assertTrue(Files.exists(root.resolve(b.toString())), "another mainframe's stays");
        assertFalse(disk.put(a, DIM, 0, 0, Map.of(0, stored(1, 1, 63))), "builds still running for it are not written");
        assertTrue(disk.column(a, DIM, 0, 0).isEmpty());
        disk.close(1000);
    }

    @Test
    void purgeNeverFollowsLinks(@TempDir Path root, @TempDir Path outside) throws IOException {
        var precious = outside.resolve("keep.bin");
        Files.write(precious, new byte[]{1});
        UUID mf = UUID.randomUUID();
        try {
            Files.createSymbolicLink(root.resolve(mf.toString()), outside);
        } catch (UnsupportedOperationException | IOException e) {
            return; // no symbolic links on this file system
        }
        var disk = disk(root, new VoxelDisk.Limits(1, 0, 0));
        disk.purge(mf);
        assertTrue(disk.flush(10_000));
        age(precious, 30);
        assertEquals(0, disk.sweepNow(System.currentTimeMillis()), "a linked folder is neither purged nor swept");
        assertTrue(Files.exists(precious));
        disk.close(1000);
    }

    @Test
    void sweepDeletesWhatNobodyUsedForTheMaxAge(@TempDir Path root) throws IOException {
        var disk = disk(root, new VoxelDisk.Limits(30 * DAY, Long.MAX_VALUE, Long.MAX_VALUE));
        UUID mf = UUID.randomUUID(), gone = UUID.randomUUID();
        put(disk, mf, 0, 0, Map.of(0, stored(1, 1, 63)));
        put(disk, mf, 1, 0, Map.of(0, stored(2, 2, 63)));
        put(disk, gone, 0, 0, Map.of(0, stored(3, 3, 63)));
        disk.saveArea(gone, () -> VoxelBatch.gzip(new byte[]{1}));
        assertTrue(disk.flush(10_000));
        var unused = root.resolve(mf.toString()).resolve("minecraft%3Aoverworld").resolve("0_0.bin");
        assertTrue(Files.exists(unused));
        age(unused, 45);
        var temp = unused.resolveSibling("leftover.tmp");
        Files.write(temp, new byte[]{1});
        age(temp, 2);
        var other = root.resolve(mf.toString()).resolve("minecraft%3Aoverworld").resolve("notes.txt");
        Files.write(other, new byte[]{1});
        age(other, 45);
        for (var file : columns(root.resolve(gone.toString()))) age(file, 45);
        age(root.resolve(gone.toString()).resolve("area.gz"), 45);

        assertEquals(4, disk.sweepNow(System.currentTimeMillis()), "old column, temp file, a mainframe's last column and its old area");
        assertFalse(Files.exists(unused));
        assertFalse(Files.exists(temp));
        assertTrue(Files.exists(other), "files that are not ours stay");
        assertFalse(disk.column(mf, DIM, 1, 0).isEmpty(), "a recent column stays");
        assertFalse(Files.exists(root.resolve(gone.toString())), "the mainframe's folder goes with its last file");
        disk.close(1000);
    }

    @Test
    void anAreaStaysAsLongAsItsTerrain(@TempDir Path root) throws IOException {
        var disk = disk(root, new VoxelDisk.Limits(30 * DAY, Long.MAX_VALUE, Long.MAX_VALUE));
        UUID mf = UUID.randomUUID();
        put(disk, mf, 0, 0, Map.of(0, stored(1, 1, 63)));
        disk.saveArea(mf, () -> VoxelBatch.gzip(new byte[]{1}));
        assertTrue(disk.flush(10_000));
        age(root.resolve(mf.toString()).resolve("area.gz"), 45);
        assertEquals(0, disk.sweepNow(System.currentTimeMillis()), "a mainframe that stays offline keeps its area while its terrain is in use");
        assertEquals(1, disk.areas().size());
        disk.close(1000);
    }

    @Test
    void sweepWithoutAMaxAgeKeepsEverything(@TempDir Path root) throws IOException {
        var disk = disk(root, NO_LIMITS);
        UUID mf = UUID.randomUUID();
        put(disk, mf, 0, 0, Map.of(0, stored(1, 1, 63)));
        age(columns(root).getFirst(), 3000);
        assertEquals(0, disk.sweepNow(System.currentTimeMillis()));
        assertEquals(1, columns(root).size());
        disk.close(1000);
    }

    @Test
    void reachingTheMainframeCapDeletesItsLeastUsedColumns(@TempDir Path root) throws IOException {
        // every column takes one 4 KB block
        var disk = disk(root, new VoxelDisk.Limits(0, 12 * 1024, Long.MAX_VALUE));
        UUID mf = UUID.randomUUID(), other = UUID.randomUUID();
        put(disk, other, 0, 0, Map.of(0, stored(1, 1, 63)));
        for (int x = 0; x < 3; x++) {
            put(disk, mf, x, 0, Map.of(0, stored(x, x, 63)));
            age(root.resolve(mf.toString()).resolve("minecraft%3Aoverworld").resolve(x + "_0.bin"), 10 - x);
        }
        assertEquals(3, columns(root.resolve(mf.toString())).size(), "exactly at the cap");
        put(disk, mf, 3, 0, Map.of(0, stored(3, 3, 63)));
        var left = disk.column(mf, DIM, 0, 0).isEmpty() && disk.column(mf, DIM, 1, 0).isEmpty();
        assertTrue(left, "the two oldest columns go (down to 90 % of the cap)");
        assertFalse(disk.column(mf, DIM, 2, 0).isEmpty());
        assertFalse(disk.column(mf, DIM, 3, 0).isEmpty());
        assertFalse(disk.column(other, DIM, 0, 0).isEmpty(), "another mainframe keeps its columns");
        disk.close(1000);
    }

    @Test
    void reachingTheTotalCapDeletesTheLeastUsedColumnsOfAnyMainframe(@TempDir Path root) throws IOException {
        var disk = disk(root, new VoxelDisk.Limits(0, Long.MAX_VALUE, 12 * 1024));
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        put(disk, a, 0, 0, Map.of(0, stored(1, 1, 63)));
        age(root.resolve(a.toString()).resolve("minecraft%3Aoverworld").resolve("0_0.bin"), 5);
        put(disk, b, 0, 0, Map.of(0, stored(2, 2, 63)));
        put(disk, b, 1, 0, Map.of(0, stored(3, 3, 63)));
        assertEquals(3, columns(root).size());
        put(disk, a, 1, 0, Map.of(0, stored(4, 4, 63)));
        assertTrue(disk.bytesOnDisk() <= 12 * 1024, "at most the cap: " + disk.bytesOnDisk());
        assertTrue(disk.column(a, DIM, 0, 0).isEmpty(), "the oldest column of all goes first");
        disk.close(1000);
    }

    @Test
    void sizesAreReadFromDiskAfterARestart(@TempDir Path root) throws IOException {
        var first = disk(root, NO_LIMITS);
        UUID mf = UUID.randomUUID();
        for (int x = 0; x < 4; x++) put(first, mf, x, 0, Map.of(0, stored(x, x, 63)));
        first.close(1000);
        for (var file : columns(root)) age(file, 3);
        var disk = disk(root, new VoxelDisk.Limits(0, 8 * 1024, Long.MAX_VALUE));
        put(disk, mf, 9, 0, Map.of(0, stored(9, 9, 63)));
        assertTrue(columns(root).size() <= 2, "the cap counts what earlier runs wrote: " + columns(root));
        assertFalse(disk.column(mf, DIM, 9, 0).isEmpty(), "the newest column stays");
        disk.close(1000);
    }

    @Test
    void aSizeCapIsEnforcedBySweepsToo(@TempDir Path root) throws IOException {
        var first = disk(root, NO_LIMITS);
        UUID mf = UUID.randomUUID();
        for (int x = 0; x < 5; x++) put(first, mf, x, 0, Map.of(0, stored(x, x, 63)));
        first.close(1000);
        var disk = disk(root, new VoxelDisk.Limits(0, 8 * 1024, Long.MAX_VALUE));
        assertTrue(disk.sweepNow(System.currentTimeMillis()) >= 3, "a lower cap applies at the next sweep (config reload)");
        assertTrue(columns(root).size() <= 2);
        disk.close(1000);
    }

    @Test
    void aTotalOfZeroMeansNothingOnDisk(@TempDir Path root) throws IOException {
        var first = disk(root, NO_LIMITS);
        UUID mf = UUID.randomUUID();
        put(first, mf, 0, 0, Map.of(0, stored(1, 1, 63)));
        first.close(1000);
        var off = disk(root, new VoxelDisk.Limits(0, Long.MAX_VALUE, 0));
        assertFalse(off.put(mf, DIM, 1, 0, Map.of(0, stored(2, 2, 63))));
        off.sweepNow(System.currentTimeMillis());
        assertTrue(columns(root).isEmpty(), "what was there goes at the next sweep");
        off.close(1000);
    }

    @Test
    void theQueueIsBounded(@TempDir Path root) throws Exception {
        var disk = disk(root, NO_LIMITS);
        UUID mf = UUID.randomUUID();
        var gate = new java.util.concurrent.CountDownLatch(1);
        // the writer waits, so everything queued stays queued
        disk.saveArea(mf, () -> {
            try {
                gate.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        });
        var section = stored(1, 1, 63);
        var many = new TreeMap<Integer, VoxelDisk.Stored>();
        for (int y = 0; y < 512; y++) many.put(y, section);
        int accepted = 0;
        for (int x = 0; x < VoxelDisk.MAX_PENDING / 512 + 5; x++) if (disk.put(mf, DIM, x, 0, many)) accepted++;
        assertEquals(VoxelDisk.MAX_PENDING / 512, accepted, "more than the queue holds is dropped");
        assertTrue(disk.put(mf, DIM, 0, 0, Map.of(3, section)), "a section of a waiting column replaces its waiting copy");
        gate.countDown();
        assertTrue(disk.flush(30_000));
        assertFalse(disk.column(mf, DIM, 0, 0).isEmpty(), "what was accepted is written");
        disk.close(1000);
    }

    @Test
    void aColumnThatIsReadCountsAsUsed(@TempDir Path root) throws IOException {
        var disk = disk(root, NO_LIMITS);
        UUID mf = UUID.randomUUID();
        put(disk, mf, 0, 0, Map.of(0, stored(1, 1, 63)));
        var file = columns(root).getFirst();
        age(file, 10);
        disk.touch(mf, DIM, 0, 0);
        assertTrue(disk.flush(10_000));
        assertTrue(System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis() < DAY, "last used: now");
        age(file, 10);
        disk.touch(mf, DIM, 0, 0);
        assertTrue(disk.flush(10_000));
        assertTrue(System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis() > 5 * DAY, "looked at once between two sweeps");
        disk.close(1000);
    }

    @Test
    void anUnchangedColumnIsNotWrittenAgain(@TempDir Path root) throws IOException {
        var disk = disk(root, NO_LIMITS);
        UUID mf = UUID.randomUUID();
        put(disk, mf, 0, 0, Map.of(0, stored(1, 1, 63)));
        var file = columns(root).getFirst();
        var before = FileTime.fromMillis(System.currentTimeMillis() - 3_600_000L);
        Files.setLastModifiedTime(file, before);
        put(disk, mf, 0, 0, Map.of(0, stored(1, 1, 63)));
        assertEquals(before.toMillis(), Files.getLastModifiedTime(file).toMillis());
        age(file, 3);
        put(disk, mf, 0, 0, Map.of(0, stored(1, 1, 63)));
        assertTrue(System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis() < DAY, "but it counts as used");
        disk.close(1000);
    }

    @Test
    void areasRoundTrip(@TempDir Path root) {
        var disk = disk(root, NO_LIMITS);
        UUID mf = UUID.randomUUID();
        disk.saveArea(mf, () -> VoxelBatch.gzip("{\"points\":{}}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertTrue(disk.flush(10_000));
        assertEquals("{\"points\":{}}", new String(disk.areas().get(mf), java.nio.charset.StandardCharsets.UTF_8));
        disk.close(1000);
    }
}

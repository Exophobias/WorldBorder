package com.wimbli.WorldBorder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldFileDataTest {
    @TempDir
    Path temp;

    @Test
    void resolvesDirectAndPaperOverworldFolders() throws IOException {
        Path direct = Files.createDirectories(temp.resolve("direct/region"));
        assertEquals(direct, WorldFileData.resolveDataFolder(
            direct.getParent(), "minecraft", "overworld", null, "region"));

        Path nested = Files.createDirectories(temp.resolve(
            "nested/dimensions/minecraft/overworld/region"));
        assertEquals(nested, WorldFileData.resolveDataFolder(
            temp.resolve("nested"), "minecraft", "overworld", null, "region"));
    }

    @Test
    void rejectsAmbiguousDataFolders() throws IOException {
        Path root = Files.createDirectories(temp.resolve("ambiguous"));
        Files.createDirectories(root.resolve("region"));
        Files.createDirectories(root.resolve("dimensions/minecraft/overworld/region"));
        assertThrows(IOException.class, () -> WorldFileData.resolveDataFolder(
            root, "minecraft", "overworld", null, "region"));
    }

    @Test
    void rejectsLinkedDataFolder() throws IOException {
        Path linkedRoot = Files.createDirectories(temp.resolve("linked"));
        Path target = Files.createDirectories(temp.resolve("other-region"));
        createSymlinkOrSkip(linkedRoot.resolve("region"), target);
        assertThrows(IOException.class, () -> WorldFileData.resolveDataFolder(
            linkedRoot, "minecraft", "overworld", null, "region"));
    }

    @Test
    void parsesOnlyRegionCoordinateFileNames() throws IOException {
        CoordXZ coordinates = WorldFileData.parseRegionFileName("r.-3.47.mca");
        assertEquals(-3, coordinates.x);
        assertEquals(47, coordinates.z);
        assertThrows(IOException.class, () -> WorldFileData.parseRegionFileName("region.mca"));
        assertThrows(IOException.class, () -> WorldFileData.parseRegionFileName("r.0.0.mca.tmp"));
        assertThrows(IOException.class, () -> WorldFileData.parseRegionFileName("r.999999999999.0.mca"));
    }

    @Test
    void readsValidChunkPointersAndRejectsDamagedHeaders() throws IOException {
        Path valid = temp.resolve("r.0.0.mca");
        byte[] data = ByteBuffer.allocate(3 * 4096).putInt(0x00000201).array();
        Files.write(valid, data);

        List<Boolean> locations = WorldFileData.readChunkLocations(valid);
        assertEquals(1024, locations.size());
        assertTrue(locations.get(0));
        assertFalse(locations.get(1));

        Path truncated = temp.resolve("r.1.0.mca");
        Files.write(truncated, new byte[8191]);
        assertThrows(IOException.class, () -> WorldFileData.readChunkLocations(truncated));

        Path badPointer = temp.resolve("r.2.0.mca");
        Files.write(badPointer, ByteBuffer.allocate(8192).putInt(0x00000201).array());
        assertThrows(IOException.class, () -> WorldFileData.readChunkLocations(badPointer));

        Path overlapping = temp.resolve("r.3.0.mca");
        Files.write(overlapping, ByteBuffer.allocate(3 * 4096)
            .putInt(0x00000201).putInt(0x00000201).array());
        assertThrows(IOException.class, () -> WorldFileData.readChunkLocations(overlapping));
    }

    @Test
    void rejectsLinkedRegionFile() throws IOException {
        Path target = temp.resolve("source.mca");
        Files.write(target, new byte[8192]);
        Path link = temp.resolve("r.0.0.mca");
        createSymlinkOrSkip(link, target);
        assertThrows(IOException.class, () -> WorldFileData.readChunkLocations(link));
    }

    private static void createSymlinkOrSkip(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (FileSystemException | UnsupportedOperationException ex) {
            Assumptions.assumeTrue(false, "Symlink creation unavailable: " + ex.getMessage());
        }
    }
}

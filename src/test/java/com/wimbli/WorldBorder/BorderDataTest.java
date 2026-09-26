package com.wimbli.WorldBorder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Map;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BorderDataTest {
    private static final BorderData RECTANGLE =
        new BorderData(8680, 15384, 4184, 7128, false);

    @Test
    void specifiedCornersAreInsideRectangularBorder() {
        assertTrue(RECTANGLE.insideBorder(4496, 8256, false));
        assertTrue(RECTANGLE.insideBorder(12864, 22512, false));
        assertFalse(RECTANGLE.insideBorder(4495.99, 15384, false));
        assertFalse(RECTANGLE.insideBorder(12864.01, 15384, false));
        assertFalse(RECTANGLE.insideBorder(8680, 8255.99, false));
        assertFalse(RECTANGLE.insideBorder(8680, 22512.01, false));
    }

    @Test
    void safeShortGrassAtNegativeYIsNotMistakenForFailure() {
        World world = world(-2, Map.of(
            -2, block(Material.STONE, false),
            -1, block(Material.SHORT_GRASS, true)));

        Location corrected = RECTANGLE.correctedPosition(
            new Location(world, 4490, -1, 10000), false, false);

        assertNotNull(corrected);
        assertEquals(4499.5, corrected.getX());
        assertEquals(-1.0, corrected.getY());
        assertEquals(10000.5, corrected.getZ());
    }

    @Test
    void searchesDownToWorldMinimumWithoutReadingBelowIt() {
        World world = world(-64, Map.of(-64, block(Material.STONE, false)));

        Location corrected = RECTANGLE.correctedPosition(
            new Location(world, 4490, -64, 10000), false, false);

        assertNotNull(corrected);
        assertEquals(-63.0, corrected.getY());
    }

    @Test
    void closedTrapdoorIsNotTreatedAsOpenAtFeet() {
        World world = world(-1, Map.of(
            -2, block(Material.STONE, false),
            -1, block(Material.OAK_TRAPDOOR, false)));

        Location corrected = RECTANGLE.correctedPosition(
            new Location(world, 4490, -1, 10000), false, false);

        assertNotNull(corrected);
        assertEquals(0.0, corrected.getY());
    }

    @Test
    void hazardousLandingWithoutAlternativeReturnsNoPosition() {
        World world = world(-2, Map.of(-2, block(Material.MAGMA_BLOCK, false)));

        assertNull(RECTANGLE.correctedPosition(
            new Location(world, 4490, -1, 10000), false, false));
    }

    private static World world(int highestBlockY, Map<Integer, Block> blocks) {
        Block air = block(Material.AIR, true);
        Chunk loadedChunk = proxy(Chunk.class, (proxy, method, args) -> {
            if (method.getName().equals("isLoaded")) return true;
            throw new AssertionError("Unexpected Chunk call: " + method);
        });

        return proxy(World.class, (proxy, method, args) -> switch (method.getName()) {
            case "getChunkAt" -> loadedChunk;
            case "getBlockAt" -> blocks.getOrDefault((Integer) args[1], air);
            case "getHighestBlockYAt" -> highestBlockY;
            case "getMinHeight" -> -64;
            case "getMaxHeight" -> 320;
            case "getEnvironment" -> World.Environment.NORMAL;
            case "getName" -> "test";
            default -> throw new AssertionError("Unexpected World call: " + method);
        });
    }

    private static Block block(Material material, boolean passable) {
        return proxy(Block.class, (proxy, method, args) -> switch (method.getName()) {
            case "getType" -> material;
            case "isPassable" -> passable;
            default -> throw new AssertionError("Unexpected Block call: " + method);
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}

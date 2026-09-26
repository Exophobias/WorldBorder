package com.wimbli.WorldBorder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BorderCheckTaskTest {
    private static final BorderData RECTANGLE = new BorderData(8680, 15384, 4184, 7128, false);

    @Test
    void safeSpawnInsideBorderIsKept() {
        World world = world(true);
        Location spawn = new Location(world, 9000.5, 64, 16000.5);

        Location fallback = BorderCheckTask.safeSpawnFallback(world, spawn, RECTANGLE, false, false);

        assertNotNull(fallback);
        assertEquals(spawn.getX(), fallback.getX());
        assertEquals(spawn.getY(), fallback.getY());
        assertEquals(spawn.getZ(), fallback.getZ());
    }

    @Test
    void unsafeSpawnInsideBorderIsRejected() {
        World world = world(false);
        Location spawn = new Location(world, 9000.5, 64, 16000.5);

        assertNull(BorderCheckTask.safeSpawnFallback(world, spawn, RECTANGLE, false, false));
    }

    @Test
    void spawnOutsideBorderIsCorrectedToSafeInsidePosition() {
        World world = world(true);
        Location spawn = new Location(world, 0.5, 64, 0.5);

        Location fallback = BorderCheckTask.safeSpawnFallback(world, spawn, RECTANGLE, false, false);

        assertNotNull(fallback);
        assertEquals(4499.5, fallback.getX());
        assertEquals(64, fallback.getY());
        assertEquals(8259.5, fallback.getZ());
    }

    @Test
    void unsafeCorrectedPositionIsRejected() {
        World world = world(false);
        Location spawn = new Location(world, 0.5, 64, 0.5);

        assertNull(BorderCheckTask.safeSpawnFallback(world, spawn, RECTANGLE, false, false));
    }

    @Test
    void spawnFromOtherWorldIsRejected() {
        World target = world(true);
        Location spawn = new Location(world(true), 9000.5, 64, 16000.5);

        assertNull(BorderCheckTask.safeSpawnFallback(target, spawn, RECTANGLE, false, false));
    }

    @Test
    void missingCurrentLocationIsHandled() {
        Player player = proxy(Player.class, (proxy, method, args) -> switch (method.getName()) {
            case "isOnline" -> true;
            case "getLocation" -> null;
            default -> throw new AssertionError("Unexpected Player call: " + method);
        });

        assertNull(BorderCheckTask.checkPlayer(player, null, true, false));
    }

    private static World world(boolean safeGround) {
        Block air = block(Material.AIR, true);
        Block stone = block(Material.STONE, false);
        Chunk loadedChunk = proxy(Chunk.class, (proxy, method, args) -> {
            if (method.getName().equals("isLoaded")) return true;
            throw new AssertionError("Unexpected Chunk call: " + method);
        });
        return proxy(World.class, (proxy, method, args) -> switch (method.getName()) {
            case "getChunkAt" -> loadedChunk;
            case "getBlockAt" -> safeGround && (Integer) args[1] == 63 ? stone : air;
            case "getHighestBlockYAt" -> 63;
            case "getMinHeight" -> -64;
            case "getMaxHeight" -> 320;
            case "getEnvironment" -> World.Environment.NORMAL;
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

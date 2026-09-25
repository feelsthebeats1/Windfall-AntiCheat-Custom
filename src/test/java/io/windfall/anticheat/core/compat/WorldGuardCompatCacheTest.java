package io.windfall.anticheat.core.compat;

import org.junit.jupiter.api.Test;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorldGuardCompatCacheTest {

    /** WorldGuardCompat's constructor is private and only reachable via the static load(). */
    private WorldGuardCompat newCompat() throws Exception {
        Constructor<WorldGuardCompat> ctor = WorldGuardCompat.class.getDeclaredConstructor(Object.class);
        ctor.setAccessible(true);
        return ctor.newInstance(new Object());
    }

    @SuppressWarnings("rawtypes")
    private Map cacheOf(WorldGuardCompat compat) throws Exception {
        Field field = WorldGuardCompat.class.getDeclaredField("regionCache");
        field.setAccessible(true);
        return (Map) field.get(compat);
    }

    @Test
    void regionCache_isConcurrentHashMap() throws Exception {
        assertTrue(cacheOf(newCompat()) instanceof ConcurrentHashMap);
    }

    @Test
    void isInRegionCached_nullPlayerIsSafe() throws Exception {
        assertFalse(newCompat().isInRegionCached(null));
    }

    @Test
    void isInRegionCached_unknownPlayerIsFalseNotThrow() throws Exception {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getLocation()).thenReturn(null);

        // A broken/foreign region query must degrade to "not exempt" rather than throw
        // on the Netty packet thread.
        assertFalse(newCompat().isInRegionCached(player));
    }

    @Test
    void isInRegionCached_storesEntryForQueriedPlayer() throws Exception {
        WorldGuardCompat compat = newCompat();
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getLocation()).thenReturn(null);

        compat.isInRegionCached(player);

        assertNotNull(cacheOf(compat).get(uuid), "expected a cache entry to be written");
    }

    @Test
    void isInRegionCached_reusesEntryWithinTtl() throws Exception {
        WorldGuardCompat compat = newCompat();
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getLocation()).thenReturn(null);

        compat.isInRegionCached(player);
        // A second call inside the TTL must short-circuit: verifying no further
        // getLocation() call proves the reflective query was skipped.
        verify(player, times(1)).getLocation();

        compat.isInRegionCached(player);
        verify(player, times(1)).getLocation();
    }

    @Test
    void invalidateCache_removesEntry() throws Exception {
        WorldGuardCompat compat = newCompat();
        UUID uuid = UUID.randomUUID();
        cacheOf(compat).put(uuid, new Object());
        assertNotNull(cacheOf(compat).get(uuid));

        compat.invalidateCache(uuid);

        assertTrue(cacheOf(compat).isEmpty());
    }

    @Test
    void invalidateCache_nullIsSafe() throws Exception {
        newCompat().invalidateCache(null);
    }
}

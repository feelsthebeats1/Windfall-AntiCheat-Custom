package io.windfall.anticheat.core.check.movement;

import io.windfall.anticheat.core.check.CheckTestBase;
import io.windfall.anticheat.core.check.impl.movement.NukerCheck;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class NukerCheckTest extends CheckTestBase {

    @Test
    void constructor_readsCheckData() {
        NukerCheck check = new NukerCheck();
        assertEquals("Nuker A", check.getName());
        assertEquals("windfall.movement.nuker", check.getStableKey());
        assertEquals(20, check.getSetbackVl());
    }

    @Test
    void stateMap_isConcurrentHashMap() throws Exception {
        NukerCheck check = new NukerCheck();
        Field field = NukerCheck.class.getDeclaredField("stateMap");
        field.setAccessible(true);
        assertInstanceOf(ConcurrentHashMap.class, field.get(check));
    }
}

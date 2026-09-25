package io.windfall.anticheat.core.check.movement;

import io.windfall.anticheat.core.check.CheckTestBase;
import io.windfall.anticheat.core.check.impl.movement.FarBreakCheck;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarBreakCheckTest extends CheckTestBase {

    private FarBreakCheck createCheck() {
        return new FarBreakCheck();
    }

    private static double constant(FarBreakCheck check, String name) throws Exception {
        Field field = FarBreakCheck.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getDouble(check);
    }

    @Test
    void constructor_readsCheckData() {
        FarBreakCheck check = createCheck();
        assertEquals("Far Break A", check.getName());
        assertEquals("windfall.movement.farbreak", check.getStableKey());
        assertEquals(15, check.getSetbackVl());
    }

    /**
     * Regression guard: the tolerance is a LINEAR block distance, so it must be squared
     * into the threshold. Adding it straight onto the squared distance made the severe
     * tier fire at sqrt(25.5) ~= 5.05 blocks instead of the intended 5.3.
     */
    @Test
    void severeThreshold_squaresTheLinearTolerance() throws Exception {
        FarBreakCheck check = createCheck();

        double maxReach = constant(check, "MAX_REACH");
        double tolerance = constant(check, "TOLERANCE");
        double severeSq = constant(check, "SEVERE_REACH_SQ");

        assertEquals(5.0, maxReach, 1e-9);
        assertEquals(0.3, tolerance, 1e-9);
        assertEquals((maxReach + tolerance) * (maxReach + tolerance), severeSq, 1e-9);
        assertEquals(28.09, severeSq, 1e-9);
    }

    @Test
    void severeThreshold_isNotToleranceAddedToSquaredDistance() throws Exception {
        FarBreakCheck check = createCheck();

        double maxReachSq = constant(check, "MAX_REACH_SQ");
        double tolerance = constant(check, "TOLERANCE");
        double severeSq = constant(check, "SEVERE_REACH_SQ");

        assertEquals(25.0, maxReachSq, 1e-9);
        // The old (wrong) form: 25.0 + 0.5 == 25.5
        assertNotEquals(maxReachSq + tolerance, severeSq, 1e-9);
        // ...and it must not correspond to a ~5.05 block reach either.
        assertTrue(Math.sqrt(severeSq) > 5.2, "severe tier should start above 5.2 blocks");
    }

    @Test
    void severeThreshold_isAboveMaxReachThreshold() throws Exception {
        FarBreakCheck check = createCheck();

        assertTrue(constant(check, "SEVERE_REACH_SQ") > constant(check, "MAX_REACH_SQ"));
    }
}

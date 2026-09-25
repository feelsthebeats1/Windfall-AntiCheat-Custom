package io.windfall.anticheat.core.check.impl.movement;

import io.windfall.anticheat.WindfallPlugin;
import io.windfall.anticheat.core.config.WindfallConfig;
import io.windfall.anticheat.core.player.WindfallPlayer;

/**
 * Shared block-check exemption helper used by FastBreak and Nuker.
 *
 * <p><b>Thread safety:</b> this runs from the Netty packet thread, so it must not touch the
 * Bukkit world or WorldGuard. All inputs come from the tick-thread snapshot in
 * {@link WindfallPlayer} — see {@link WindfallPlayer#getCachedWorldName()} and
 * {@link WindfallPlayer#isCachedBlockCheckRegionExempt()}.
 *
 * <p>Reading only cached state is fail-safe: if the snapshot has not been refreshed yet the
 * player is treated as <i>not</i> exempt, so detection still runs rather than silently
 * switching off.
 */
final class BlockCheckExempt {

    private BlockCheckExempt() {
    }

    static boolean isExempt(WindfallPlayer player) {
        WindfallConfig cfg = WindfallPlugin.getInstance().getWindfallConfig();
        if (cfg == null) return false;

        String worldName = player.getCachedWorldName();
        // Snapshot not ready yet (first tick of a session) — treat as not exempt.
        if (worldName == null) return false;

        if (cfg.isBlockCheckWorldExempt(worldName)) return true;
        return cfg.isBlockCheckRegionExempt() && player.isCachedBlockCheckRegionExempt();
    }
}

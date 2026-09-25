package io.windfall.anticheat.core.check.impl.movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import io.windfall.anticheat.core.check.Check;
import io.windfall.anticheat.WindfallPlugin;
import io.windfall.anticheat.core.check.CheckData;
import io.windfall.anticheat.core.check.CompatFlag;
import io.windfall.anticheat.core.check.type.PacketCheck;
import io.windfall.anticheat.core.player.WindfallPlayer;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;

/**
 * Detects players who break blocks faster than vanilla survival allows.
 * Measures the elapsed wall-clock time between {@code START_DIGGING} and
 * {@code FINISHED_DIGGING} packets and compares it against a per-material
 * vanilla break-time table.
 *
 * <p><b>Algorithm:</b> On START_DIGGING the block type and timestamp are
 * recorded. On FINISHED_DIGGING the elapsed time is compared with the
 * configured tool-aware baseline. Repeated violations accumulate in a buffer
 * and trigger a flag at the configured threshold.</p>
 *
 * <p>Block types are resolved off-thread: this handler enqueues the coordinates via
 * {@link WindfallPlayer#requestBlockType} and reads the result on FINISHED_DIGGING from
 * the tick-thread snapshot, so {@code World#getBlockAt} is never called from Netty.</p>
 *
 * <p>Marked {@link CompatFlag#FOLIA_UNSAFE} because the exemption check still consults the
 * Bukkit player and WorldGuard from the packet thread.</p>
 *
 * @see Check
 * @see PacketCheck
 */
@CheckData(name = "Fast Break A", stableKey = "windfall.movement.fastbreak", decay = 0.02,
    setbackVl = 20, compat = {CompatFlag.FOLIA_UNSAFE})
public class FastBreakCheck extends Check implements PacketCheck {

    /** Per-player state tracking the in-progress block break. */
    private static final class PlayerState {
        /** {@link System#currentTimeMillis()} when START_DIGGING was received. */
        long breakStartTime;
        /** Whether a break is currently in progress (START received, not yet FINISHED). */
        boolean breaking;
        int blockX;
        int blockY;
        int blockZ;
        Material toolType;
        int efficiencyLevel;
        double toolSpeed;
        long startTime;

        void reset() {
            breaking = false;
            breakStartTime = 0;
            toolType = null;
            efficiencyLevel = 0;
            toolSpeed = 1.0;
            startTime = 0;
        }
    }

    private final ConcurrentHashMap<UUID, PlayerState> stateMap = new ConcurrentHashMap<>();

    private PlayerState getState(WindfallPlayer player) {
        return stateMap.computeIfAbsent(player.getUuid(), k -> new PlayerState());
    }

    @Override
    public void removePlayer(java.util.UUID uuid) {
        stateMap.remove(uuid);
    }

    /**
     * Processes PLAYER_DIGGING packets to track break start, cancel, and finish
     * events. Compares actual break duration against the vanilla baseline.
     *
     * @param player the player associated with this packet
     * @param event  the incoming digging packet
     */
    @Override
    public void onPacketReceive(WindfallPlayer player, PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) return;

        WrapperPlayClientPlayerDigging wrapper = new WrapperPlayClientPlayerDigging(event);
        DiggingAction action = wrapper.getAction();
        PlayerState state = getState(player);

        if (action == DiggingAction.START_DIGGING) {
            if (BlockCheckExempt.isExempt(player)) {
                state.reset();
                return;
            }
            state.breaking = true;
            state.breakStartTime = System.currentTimeMillis();
            state.startTime = state.breakStartTime;
            state.blockX = wrapper.getBlockPosition().getX();
            state.blockY = wrapper.getBlockPosition().getY();
            state.blockZ = wrapper.getBlockPosition().getZ();
            // The world must not be touched from this thread. Ask the tick thread to resolve
            // the block type; it is read back on FINISHED_DIGGING, by which point at least one
            // tick has elapsed. Requesting every dig also keeps the queue warm for a cheat
            // that sends START+FINISHED back to back within a single tick.
            player.requestBlockType(state.blockX, state.blockY, state.blockZ);
            captureTool(player, state,
                    io.windfall.anticheat.WindfallPlugin.getInstance().getWindfallConfig().isFastBreakToolAware(),
                    io.windfall.anticheat.WindfallPlugin.getInstance().getWindfallConfig().isFastBreakEfficiencyAware());
        } else if (action == DiggingAction.CANCELLED_DIGGING) {
            state.reset();
        } else if (action == DiggingAction.FINISHED_DIGGING) {
            if (!state.breaking) {
                state.reset();
                return;
            }

            /** Read the tick-thread resolved type; fall back to stone if it never arrived. */
            Material blockType = player.getResolvedBlockType(state.blockX, state.blockY, state.blockZ);
            if (blockType == null) blockType = Material.STONE;

            long elapsed = System.currentTimeMillis() - state.breakStartTime;
            double vanillaTime = getVanillaBreakTime(blockType, state.toolType,
                    state.efficiencyLevel, state.toolSpeed);
            io.windfall.anticheat.core.config.WindfallConfig cfg = WindfallPlugin.getInstance().getWindfallConfig();
            double maxAllowed = Math.max(cfg.getFastBreakMinimumCheckMs(),
                    vanillaTime * 1000.0 * cfg.getFastBreakTimeMultiplier()) + cfg.getFastBreakNetworkGraceMs();

            if (elapsed < maxAllowed && vanillaTime > 0) {
                increaseBuffer(player, cfg.getFastBreakBufferIncrease());
                if (getBuffer(player) >= cfg.getFastBreakMinimumFlagBuffer()) {
                    flag(player, "block=" + blockType
                            + " tool=" + (state.toolType == null ? "HAND" : state.toolType)
                            + " eff=" + state.efficiencyLevel
                            + " expected=" + Math.round(vanillaTime * 1000.0) + "ms"
                            + " maxAllowed=" + Math.round(maxAllowed) + "ms"
                            + " actual=" + elapsed + "ms");
                    resetBuffer(player);
                }
            } else {
                decreaseBuffer(player, cfg.getFastBreakBufferDecrease());
            }
            state.reset();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onPacketSend(WindfallPlayer player, PacketSendEvent event) {
    }

    /**
     * Returns the approximate vanilla break time (in seconds) for a given material
     * using an unenchanted diamond pickaxe. Values are approximate and cover common
     * block types. Unrecognised solid blocks default to 1.0 s; non-solid to 0.5 s.
     *
     * @param material the block material to look up
     * @return vanilla break time in seconds
     */
    private double getVanillaBreakTime(Material material, Material toolType,
                                       int efficiencyLevel, double toolSpeed) {
        String name = material.name();
        double baseTime;
        if (name.equals("OBSIDIAN") || name.equals("END_PORTAL_FRAME")) baseTime = 50.0;
        else if (name.equals("ENCHANTING_TABLE") || name.equals("ANVIL")) baseTime = 5.0;
        else if (name.contains("IRON_ORE")) baseTime = 3.0;
        else if (name.contains("DIAMOND_ORE") || name.contains("EMERALD_ORE")) baseTime = 5.0;
        else if (name.equals("STONE") || name.equals("COBBLESTONE") || name.equals("DEEPSLATE")) baseTime = 1.5;
        else if (name.equals("DIRT") || name.equals("GRASS_BLOCK") || name.equals("SAND")) baseTime = 0.5;
        else if (name.contains("PLANKS") || name.contains("_LOG")) baseTime = 2.0;
        else if (material.isBlock() && material.isSolid()) baseTime = 1.0;
        else baseTime = 0.5;

        double override = io.windfall.anticheat.WindfallPlugin.getInstance()
                .getWindfallConfig().getFastBreakBlockTimeOverride(name);
        if (override > 0.0) {
            baseTime = override;
        }

        if (!isCorrectTool(name, toolType)) {
            return baseTime;
        }
        double speed = toolSpeed + efficiencyLevel * efficiencyLevel + 1.0;
        return Math.max(0.05, baseTime * 8.0 / speed);
    }

    private static boolean isCorrectTool(String blockName, Material toolType) {
        if (toolType == null) return false;
        String tool = toolType.name();
        if (blockName.contains("ORE") || blockName.equals("STONE")
                || blockName.equals("COBBLESTONE") || blockName.equals("DEEPSLATE")
                || blockName.equals("OBSIDIAN") || blockName.equals("END_PORTAL_FRAME")) {
            return tool.contains("PICKAXE");
        }
        if (blockName.equals("DIRT") || blockName.equals("SAND") || blockName.equals("GRAVEL")
                || blockName.equals("CLAY") || blockName.equals("SOUL_SAND")
                || blockName.equals("SOUL_SOIL")) {
            return tool.contains("SHOVEL");
        }
        if (blockName.contains("PLANKS") || blockName.contains("_LOG")
                || blockName.contains("WOOD") || blockName.equals("BOOKSHELF")) {
            return tool.contains("AXE");
        }
        if (blockName.contains("LEAVES") || blockName.contains("WHEAT")
                || blockName.contains("CROPS") || blockName.equals("GRASS_BLOCK")) {
            return tool.contains("HOE") || tool.contains("SHEARS");
        }
        return toolSpeedApplicable(tool);
    }

    private static boolean toolSpeedApplicable(String tool) {
        return tool.contains("PICKAXE") || tool.contains("AXE") || tool.contains("SHOVEL")
                || tool.contains("HOE") || tool.contains("SHEARS");
    }

    private void captureTool(WindfallPlayer player, PlayerState state,
                             boolean toolAware, boolean efficiencyAware) {
        // Read the main-thread snapshot instead of touching the Bukkit inventory from
        // the Netty packet thread. Refreshed once per tick by WindfallPlayer#updateCachedState.
        state.toolType = toolAware ? player.getCachedToolType() : null;
        state.efficiencyLevel = efficiencyAware ? player.getCachedEfficiencyLevel() : 0;
        state.toolSpeed = toolAware ? player.getCachedToolSpeed() : 8.0;
    }
}

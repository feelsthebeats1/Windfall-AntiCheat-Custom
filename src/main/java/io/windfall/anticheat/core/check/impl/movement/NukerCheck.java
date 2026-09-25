package io.windfall.anticheat.core.check.impl.movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import io.windfall.anticheat.core.check.Check;
import io.windfall.anticheat.core.check.CheckData;
import io.windfall.anticheat.core.check.CompatFlag;
import io.windfall.anticheat.core.config.WindfallConfig;
import io.windfall.anticheat.core.check.type.PacketCheck;
import io.windfall.anticheat.core.player.WindfallPlayer;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects high-throughput block breaking that is consistent with Nuker clients.
 *
 * <p>The check intentionally does not flag throughput alone. It requires a combination
 * of unique targets, same-tick completions, and target switches inside a short window.
 * It is disabled from global punishment by default so operators can tune it first.</p>
 *
 * <p>Marked {@link CompatFlag#FOLIA_UNSAFE}: exemption checks consult the Bukkit player
 * and WorldGuard from the packet thread, which is unsafe on Folia's region threads.</p>
 */
@CheckData(name = "Nuker A", stableKey = "windfall.movement.nuker", decay = 0.02, setbackVl = 20,
    compat = {CompatFlag.FOLIA_UNSAFE})
public class NukerCheck extends Check implements PacketCheck {

    private static final class BreakEvent {
        final int x;
        final int y;
        final int z;
        final int tick;
        final long timestamp;
        final boolean fast;

        BreakEvent(int x, int y, int z, int tick, long timestamp, boolean fast) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.tick = tick;
            this.timestamp = timestamp;
            this.fast = fast;
        }
    }

    private static final class PlayerState {
        final ArrayDeque<BreakEvent> events = new ArrayDeque<>();
        long lastStartTime;
        int lastStartTick;
        int lastX, lastY, lastZ;
    }

    private final ConcurrentHashMap<UUID, PlayerState> stateMap = new ConcurrentHashMap<>();

    private PlayerState getState(WindfallPlayer player) {
        return stateMap.computeIfAbsent(player.getUuid(), k -> new PlayerState());
    }

    @Override
    public void removePlayer(UUID uuid) {
        stateMap.remove(uuid);
    }

    @Override
    public void onPacketReceive(WindfallPlayer player, PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) return;
        WrapperPlayClientPlayerDigging wrapper = new WrapperPlayClientPlayerDigging(event);
        DiggingAction action = wrapper.getAction();
        WindfallConfig cfg = io.windfall.anticheat.WindfallPlugin.getInstance().getWindfallConfig();
        PlayerState state = getState(player);
        int x = wrapper.getBlockPosition().getX();
        int y = wrapper.getBlockPosition().getY();
        int z = wrapper.getBlockPosition().getZ();
        int tick = player.getTickCount();
        long now = System.currentTimeMillis();

        if (action == DiggingAction.START_DIGGING) {
            if (BlockCheckExempt.isExempt(player)) {
                return;
            }
            state.lastStartTime = now;
            state.lastStartTick = tick;
            state.lastX = x;
            state.lastY = y;
            state.lastZ = z;
            return;
        }
        if (action != DiggingAction.FINISHED_DIGGING) return;

        long windowMs = Math.max(250L, cfg.getNukerWindowMs());
        boolean fast = state.lastStartTime > 0
                && now - state.lastStartTime <= Math.max(100L, cfg.getFastBreakNetworkGraceMs())
                && state.lastX == x && state.lastY == y && state.lastZ == z;
        state.lastStartTime = 0;
        state.events.addLast(new BreakEvent(x, y, z, tick, now, fast));
        while (!state.events.isEmpty() && now - state.events.peekFirst().timestamp > windowMs) {
            state.events.removeFirst();
        }
        evaluate(player, state, cfg);
    }

    private void evaluate(WindfallPlayer player, PlayerState state, WindfallConfig cfg) {
        if (state.events.size() < 2) return;

        Set<String> unique = new HashSet<>();
        int sameTick = 1;
        int switches = 0;
        int fastBlocks = 0;
        BreakEvent previous = null;
        for (BreakEvent event : state.events) {
            unique.add(event.x + ":" + event.y + ":" + event.z);
            if (event.fast) fastBlocks++;
            if (previous != null && event.tick == previous.tick) sameTick++;
            if (previous != null) {
                double dx = event.x - previous.x;
                double dy = event.y - previous.y;
                double dz = event.z - previous.z;
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (distance >= cfg.getNukerMinimumTargetSwitchDistance()
                        && distance <= cfg.getNukerMaximumTargetSwitchDistance()) {
                    switches++;
                }
            }
            previous = event;
        }

        boolean throughput = unique.size() > cfg.getNukerSuspiciousBlocks()
                || state.events.size() > cfg.getNukerMaximumBlocks();
        boolean burst = sameTick > cfg.getNukerMaximumSameTick();
        boolean enoughFast = fastBlocks >= cfg.getNukerMinimumFastBlocks();
        boolean targetPattern = switches >= Math.max(3, unique.size() / 2);
        if (throughput && enoughFast && (burst || targetPattern)) {
            increaseBuffer(player, 1.0);
            if (getBuffer(player) >= cfg.getNukerMinimumFlagBuffer()) {
                flag(player, "blocks=" + state.events.size()
                        + " unique=" + unique.size()
                        + " fast=" + fastBlocks
                        + " sameTick=" + sameTick
                        + " switches=" + switches
                        + " window=" + cfg.getNukerWindowMs() + "ms");
                resetBuffer(player);
            }
        } else {
            decreaseBuffer(player, 0.2);
        }
    }

    @Override
    public void onPacketSend(WindfallPlayer player, PacketSendEvent event) {
    }
}

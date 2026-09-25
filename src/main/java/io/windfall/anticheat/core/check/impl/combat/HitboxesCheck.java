package io.windfall.anticheat.core.check.impl.combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import io.windfall.anticheat.core.check.Check;
import io.windfall.anticheat.core.check.CheckData;
import io.windfall.anticheat.core.check.CompatFlag;
import io.windfall.anticheat.core.check.type.PacketCheck;
import io.windfall.anticheat.core.physics.VersionPhysics;
import io.windfall.anticheat.core.player.WindfallPlayer;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects hitbox expansion cheats that artificially enlarge a player's or
 * entity's bounding box to make hits register at impossible distances.
 *
 * <p>This check uses the entity positions and bounding boxes observed in outgoing server
 * packets. Each attack is measured from the attacker's eye to the closest point on the
 * target AABB; attacks are ignored until target geometry is known.</p>
 *
 * <p><b>Hit ratio:</b> Repeated samples inside the protocol reach window are accumulated.
 * A consistent abnormal ratio can indicate client-side target selection or hitbox abuse.</p>
 *
 * @see Check
 * @see PacketCheck
 */
@CheckData(name = "Hitboxes A", stableKey = "windfall.combat.hitboxes", decay = 0.01, setbackVl = 15, compat = {CompatFlag.RELAX_ON_MISMATCH}, relaxMultiplier = 1.2)
public class HitboxesCheck extends Check implements PacketCheck {

    /**
     * Extra margin applied to the protocol reach before distance is considered anomalous.
     * Reach and Hitboxes are intentionally kept separate: Hitboxes accumulates repeated
     * borderline samples while Reach applies the stricter immediate limit.
     */
    private static final double PLAYER_BOX_EXPANSION = 0.15;

    /** Minimum attack count before the hit-ratio evaluation is performed. */
    private static final int MIN_ATTACKS_PER_EVAL = 16;

    /** Ratio of attacks inside the packet-tracked target AABB. */
    private static final double HIT_RATIO_FLAG_THRESHOLD = 0.8;

    /** Hard limit used only when target geometry is known. */
    private static final double BLATANT_FLAG_THRESHOLD = 5.0;

    /** Buffer level at which a hitboxes flag is triggered. */
    private static final double FLAG_BUFFER_THRESHOLD = 5.0;

    /** Per-player mutable state for tracking hit-ratio statistics. */
    private static final class PlayerState {
        int attacksOnTarget;
        int totalAttacks;
    }

    /** Player state lookup keyed by UUID. */
    private final ConcurrentHashMap<UUID, PlayerState> stateMap = new ConcurrentHashMap<>();

    /**
     * Retrieves or lazily initialises the per-player state.
     *
     * @param player the player whose state is requested
     * @return the current {@link PlayerState}
     */
    private PlayerState getState(WindfallPlayer player) {
        return stateMap.computeIfAbsent(player.getUuid(), k -> new PlayerState());
    }

    /**
     * Evicts cached state when a player disconnects.
     *
     * @param uuid UUID of the departing player
     */
    @Override
    public void removePlayer(UUID uuid) {
        stateMap.remove(uuid);
    }

    /**
     * Processes attack-entity packets to evaluate hit-ratio consistency.
     *
     * <p>Evaluates attacks only when the target's packet-tracked AABB is available. The
     * shortest eye-to-AABB distance is compared with protocol reach and a rolling hit ratio.
     *
     * @param player the player performing the attack
     * @param event  the raw packet event
     */
    @Override
    public void onPacketReceive(WindfallPlayer player, PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
        if (wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        PlayerState state = getState(player);
        int targetId = wrapper.getEntityId();
        double eyeX = player.getX();
        double eyeY = player.getY() + player.getEyeHeight();
        double eyeZ = player.getZ();
        double hitDistance = ReachCheck.distanceToTrackedEntity(targetId, eyeX, eyeY, eyeZ);

        // Entity spawn/move packets may lag behind the first attack. Unknown geometry is
        // ignored instead of treating a fixed-length look vector as a real hit result.
        if (Double.isNaN(hitDistance)) {
            decreaseBuffer(player, 0.1);
            return;
        }

        state.totalAttacks++;
        double protocolReach = VersionPhysics.getMaxReach(player.getProtocolVersion());
        if (VersionPhysics.hasAttackCooldown(player.getProtocolVersion())) {
            protocolReach += VersionPhysics.getCooldownReachBonus(player.getProtocolVersion())
                    * Math.min(player.getAttackCooldown() / 20.0, 1.0);
        }
        double hardLimit = protocolReach + 0.35 + Math.min(player.getTransactionPing() * 0.001, 0.2);
        if (hitDistance > BLATANT_FLAG_THRESHOLD || hitDistance > hardLimit) {
            flag(player);
            resetBuffer(player);
            state.attacksOnTarget = 0;
            state.totalAttacks = 0;
            return;
        }

        if (hitDistance <= protocolReach + PLAYER_BOX_EXPANSION) {
            state.attacksOnTarget++;
        }

        if (state.totalAttacks >= MIN_ATTACKS_PER_EVAL) {
            double hitRatio = (double) state.attacksOnTarget / state.totalAttacks;
            if (hitRatio > HIT_RATIO_FLAG_THRESHOLD && state.totalAttacks > 20) {
                increaseBuffer(player, 0.3);
                if (getBuffer(player) > FLAG_BUFFER_THRESHOLD) {
                    flag(player);
                    resetBuffer(player);
                }
            } else {
                decreaseBuffer(player, 0.1);
            }
            /* Reset counters for the next evaluation window. */
            state.attacksOnTarget = 0;
            state.totalAttacks = 0;
        }
    }

    /** No outbound packets are relevant to this check. */
    @Override
    public void onPacketSend(WindfallPlayer player, PacketSendEvent event) {
    }
}

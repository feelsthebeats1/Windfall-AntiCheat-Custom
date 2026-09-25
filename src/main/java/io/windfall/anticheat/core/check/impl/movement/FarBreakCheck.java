package io.windfall.anticheat.core.check.impl.movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import io.windfall.anticheat.core.check.Check;
import io.windfall.anticheat.core.check.CheckData;
import io.windfall.anticheat.core.check.type.PacketCheck;
import io.windfall.anticheat.core.player.WindfallPlayer;

/**
 * Detects players breaking blocks from distances exceeding vanilla reach limits.
 *
 * <p>This is the single consolidated break-reach check. It previously had a near-duplicate
 * ({@code PositionBreakCheck}) measuring the same distance with the same two-tier buffer
 * model; the two are merged here. The squared-distance form is kept because this runs on
 * the Netty packet thread for every dig packet, and it avoids {@code Math.sqrt} in the
 * hot path.
 *
 * <p><b>Algorithm:</b> On START_DIGGING and FINISHED_DIGGING, the squared Euclidean distance
 * from the player's eye position to the block centre (X+0.5, Y+0.5, Z+0.5) is compared
 * against two squared thresholds. The tolerance is a <b>linear</b> distance in blocks and is
 * squared into the threshold — it is deliberately not added directly to the squared distance,
 * which would make the severe tier fire at ~5.05 blocks instead of the intended ~5.3.</p>
 * <ul>
 *   <li><b>Severe (dist &gt; 5.3):</b> Buffer +1.0, flags at buffer &gt; 3.0.</li>
 *   <li><b>Moderate (5.0 &lt; dist &le; 5.3):</b> Buffer +0.3, flags at buffer &gt; 5.0.</li>
 *   <li><b>In reach:</b> Buffer decays by 0.1.</li>
 * </ul>
 *
 * <p>Eye height is approximated as {@code 0.9 * height} (1.62 blocks standing, 1.35 crouching),
 * which is close to the real Minecraft eye offsets.</p>
 *
 * @see Check
 * @see PacketCheck
 */
@CheckData(name = "Far Break A", stableKey = "windfall.movement.farbreak", decay = 0.01, setbackVl = 15)
public class FarBreakCheck extends Check implements PacketCheck {

    /** Maximum reach distance in blocks — slightly above vanilla's ~4.5 to allow latency. */
    private static final double MAX_REACH = 5.0;

    /** Additional linear tolerance beyond {@link #MAX_REACH} before the severe tier activates. */
    private static final double TOLERANCE = 0.3;

    /** {@link #MAX_REACH} squared, to avoid sqrt in the hot path. */
    private static final double MAX_REACH_SQ = MAX_REACH * MAX_REACH;

    /** {@code (MAX_REACH + TOLERANCE)^2} — squared, so the tolerance stays a block distance. */
    private static final double SEVERE_REACH_SQ = (MAX_REACH + TOLERANCE) * (MAX_REACH + TOLERANCE);

    /** Buffer level at which the player is flagged in the severe tier. */
    private static final double SEVERE_BUFFER_THRESHOLD = 3.0;

    /** Buffer level at which the player is flagged in the moderate tier. */
    private static final double MODERATE_BUFFER_THRESHOLD = 5.0;

    /**
     * Evaluates the distance to the targeted block on dig start/finish packets.
     * Uses a two-tier severity model based on how far beyond vanilla reach the
     * player reaches.
     *
     * @param player the player associated with this packet
     * @param event  the incoming digging packet
     */
    @Override
    public void onPacketReceive(WindfallPlayer player, PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) return;

        WrapperPlayClientPlayerDigging wrapper = new WrapperPlayClientPlayerDigging(event);
        DiggingAction action = wrapper.getAction();

        if (action != DiggingAction.START_DIGGING && action != DiggingAction.FINISHED_DIGGING) {
            return;
        }

        int blockX = wrapper.getBlockPosition().getX();
        int blockY = wrapper.getBlockPosition().getY();
        int blockZ = wrapper.getBlockPosition().getZ();

        /** Block center coordinates — targeting is evaluated to the center of each block face. */
        double centerX = blockX + 0.5;
        double centerY = blockY + 0.5;
        double centerZ = blockZ + 0.5;

        /** Approximate eye position, matching Minecraft's eye offset. */
        double eyeY = player.getY() + player.getHeight() * 0.9;

        /** Component deltas from eye position to block center. */
        double dx = player.getX() - centerX;
        double dy = eyeY - centerY;
        double dz = player.getZ() - centerZ;
        /** Squared distance — avoids Math.sqrt for performance. */
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq > SEVERE_REACH_SQ) {
            increaseBuffer(player, 1.0);
            if (getBuffer(player) > SEVERE_BUFFER_THRESHOLD) {
                flag(player, String.format(
                        "break at %d,%d,%d dist=%.2f severeThreshold=%.2f",
                        blockX, blockY, blockZ, Math.sqrt(distSq), MAX_REACH + TOLERANCE));
                resetBuffer(player);
            }
        } else if (distSq > MAX_REACH_SQ) {
            /** Moderate tier — needs more consecutive hits to flag. */
            increaseBuffer(player, 0.3);
            if (getBuffer(player) > MODERATE_BUFFER_THRESHOLD) {
                flag(player, String.format(
                        "break at %d,%d,%d dist=%.2f reachThreshold=%.2f",
                        blockX, blockY, blockZ, Math.sqrt(distSq), MAX_REACH));
                resetBuffer(player);
            }
        } else {
            decreaseBuffer(player, 0.1);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onPacketSend(WindfallPlayer player, PacketSendEvent event) {
    }
}
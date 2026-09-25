package io.windfall.anticheat.core.check.impl.combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import io.windfall.anticheat.core.check.Check;
import io.windfall.anticheat.core.check.CheckData;
import io.windfall.anticheat.core.check.type.PacketCheck;
import io.windfall.anticheat.core.player.WindfallPlayer;

/**
 * Detects self-interaction packets and impossible entity interaction cursor geometry.
 *
 * <p>Self-targeting attack packets are impossible in vanilla. For other targets, the
 * check uses the target's packet-tracked AABB and the attacker's last rotation. Sustained
 * extreme centre-angle mismatches add cursor evidence, while normal large-entity hits
 * are tolerated.</p>
 *
 * <p><b>Why this matters:</b> Crafted self-interaction or cursor-mismatched packets can
 * trigger unintended combat, interaction, or server-side state behavior.</p>
 */
@CheckData(name = "Self Interact A", stableKey = "windfall.combat.selfinteract", decay = 0.0, setbackVl = 5)
public class SelfInteractCheck extends Check implements PacketCheck {

    /**
     * Inspects attack packets and compares the target entity ID to the player's own
     * entity ID. If they match, the player is flagged and kicked.
     *
     * @param player the player associated with the packet
     * @param event  the incoming packet event
     */
    @Override
    public void onPacketReceive(WindfallPlayer player, PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
        if (wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        int targetId = wrapper.getEntityId();
        int selfId = player.getPlayer().getEntityId();

        if (targetId == selfId) {
            flag(player);
            player.getPlayer().kickPlayer("[Windfall] Self-interaction detected");
            return;
        }

        double[] box = ReachCheck.getTrackedEntityBoundingBox(targetId);
        if (box == null || player.getPlayer() == null) return;

        double eyeX = player.getX();
        double eyeY = player.getY() + player.getEyeHeight();
        double eyeZ = player.getZ();
        double targetX = (box[0] + box[3]) * 0.5;
        double targetY = (box[1] + box[4]) * 0.5;
        double targetZ = (box[2] + box[5]) * 0.5;
        double dx = targetX - eyeX;
        double dy = targetY - eyeY;
        double dz = targetZ - eyeZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double expectedYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double expectedPitch = Math.toDegrees(Math.atan2(-dy, horizontal));
        double deltaYaw = Math.abs(normalizeDegrees(player.getYaw() - expectedYaw));
        double deltaPitch = Math.abs(player.getPitch() - expectedPitch);

        // Large entities can be hit while looking away from their centre. Sustained
        // extreme mismatch is treated as evidence of an impossible interact cursor.
        if (deltaYaw > 75.0 || deltaPitch > 75.0) {
            increaseBuffer(player, 0.5);
            if (getBuffer(player) > 5.0) {
                flag(player);
                resetBuffer(player);
            }
        } else {
            decreaseBuffer(player, 0.1);
        }
    }

    @Override
    public void onPacketSend(WindfallPlayer player, PacketSendEvent event) {
    }

    private static double normalizeDegrees(double angle) {
        angle %= 360.0;
        if (angle > 180.0) angle -= 360.0;
        if (angle < -180.0) angle += 360.0;
        return angle;
    }
}

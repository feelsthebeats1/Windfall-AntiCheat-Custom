package io.windfall.anticheat.core.player;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import io.windfall.anticheat.core.player.WindfallPlayer.Pose;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WindfallPlayerTest {

    @Mock private Player mockBukkitPlayer;
    @Mock private User mockUser;
    @Mock private ClientVersion mockClientVersion;

    private MockedStatic<io.windfall.anticheat.WindfallPlugin> pluginStaticMock;
    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        pluginStaticMock = mockStatic(io.windfall.anticheat.WindfallPlugin.class);

        when(mockBukkitPlayer.getUniqueId()).thenReturn(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
        when(mockBukkitPlayer.getName()).thenReturn("TestPlayer");
        when(mockUser.getClientVersion()).thenReturn(mockClientVersion);
    }

    @AfterEach
    void tearDown() throws Exception {
        pluginStaticMock.close();
        closeable.close();
    }

    private WindfallPlayer createPlayer(int protocolVersion) {
        when(mockClientVersion.getProtocolVersion()).thenReturn(protocolVersion);
        return new WindfallPlayer(mockBukkitPlayer, mockUser);
    }

    // === getHeight() per Pose ===

    @Test
    void getHeight_standing_returns18() {
        WindfallPlayer player = createPlayer(767);
        assertEquals(1.8, player.getHeight());
    }

    @Test
    void getHeight_sneaking_post114_returns15() {
        WindfallPlayer player = createPlayer(767);
        player.setPose(Pose.SNEAKING);
        assertEquals(1.5, player.getHeight());
    }

    @Test
    void getHeight_sneaking_pre114_returns162() {
        WindfallPlayer player = createPlayer(47);
        player.setPose(Pose.SNEAKING);
        assertEquals(1.62, player.getHeight());
    }

    @Test
    void getHeight_swimming_returns06() {
        WindfallPlayer player = createPlayer(767);
        player.setPose(Pose.SWIMMING);
        assertEquals(0.6, player.getHeight());
    }

    @Test
    void getHeight_fallFlying_returns06() {
        WindfallPlayer player = createPlayer(767);
        player.setPose(Pose.FALL_FLYING);
        assertEquals(0.6, player.getHeight());
    }

    @Test
    void getHeight_spinAttack_returns06() {
        WindfallPlayer player = createPlayer(767);
        player.setPose(Pose.SPIN_ATTACK);
        assertEquals(0.6, player.getHeight());
    }

    @Test
    void getHeight_sleeping_returns02() {
        WindfallPlayer player = createPlayer(767);
        player.setPose(Pose.SLEEPING);
        assertEquals(0.2, player.getHeight());
    }

    @Test
    void getHeight_dying_returns00() {
        WindfallPlayer player = createPlayer(767);
        player.setPose(Pose.DYING);
        assertEquals(0.0, player.getHeight());
    }

    @Test
    void getHeight_longJumping_post114_returns15() {
        WindfallPlayer player = createPlayer(767);
        player.setPose(Pose.LONG_JUMPING);
        assertEquals(1.5, player.getHeight());
    }

    @Test
    void getHeight_longJumping_pre114_returns18() {
        WindfallPlayer player = createPlayer(47);
        player.setPose(Pose.LONG_JUMPING);
        assertEquals(1.8, player.getHeight());
    }

    // === getEyeHeight() per Pose ===

    @Test
    void getEyeHeight_standing_returns162() {
        WindfallPlayer player = createPlayer(767);
        assertEquals(1.62, player.getEyeHeight());
    }

    @Test
    void getEyeHeight_sneaking_post114_returns127() {
        WindfallPlayer player = createPlayer(767);
        player.setPose(Pose.SNEAKING);
        assertEquals(1.27, player.getEyeHeight());
    }

    @Test
    void getEyeHeight_sneaking_pre114_returns154() {
        WindfallPlayer player = createPlayer(47);
        player.setPose(Pose.SNEAKING);
        assertEquals(1.54, player.getEyeHeight());
    }

    @Test
    void getEyeHeight_swimming_returns04() {
        WindfallPlayer player = createPlayer(767);
        player.setPose(Pose.SWIMMING);
        assertEquals(0.4, player.getEyeHeight());
    }

    @Test
    void getEyeHeight_fallFlying_returns04() {
        WindfallPlayer player = createPlayer(767);
        player.setPose(Pose.FALL_FLYING);
        assertEquals(0.4, player.getEyeHeight());
    }

    @Test
    void getEyeHeight_spinAttack_returns04() {
        WindfallPlayer player = createPlayer(767);
        player.setPose(Pose.SPIN_ATTACK);
        assertEquals(0.4, player.getEyeHeight());
    }

    @Test
    void getEyeHeight_sleeping_returns02() {
        WindfallPlayer player = createPlayer(767);
        player.setPose(Pose.SLEEPING);
        assertEquals(0.2, player.getEyeHeight());
    }

    @Test
    void getEyeHeight_dying_returns00() {
        WindfallPlayer player = createPlayer(767);
        player.setPose(Pose.DYING);
        assertEquals(0.0, player.getEyeHeight());
    }

    @Test
    void getEyeHeight_longJumping_post114_returns127() {
        WindfallPlayer player = createPlayer(767);
        player.setPose(Pose.LONG_JUMPING);
        assertEquals(1.27, player.getEyeHeight());
    }

    @Test
    void getEyeHeight_longJumping_pre114_returns162() {
        WindfallPlayer player = createPlayer(47);
        player.setPose(Pose.LONG_JUMPING);
        assertEquals(1.62, player.getEyeHeight());
    }

    // === setPosition() rolls positions correctly ===

    @Test
    void setPosition_rollsPositions() {
        WindfallPlayer player = createPlayer(767);

        player.setPosition(1.0, 64.0, 2.0);
        assertEquals(1.0, player.getX());
        assertEquals(64.0, player.getY());
        assertEquals(2.0, player.getZ());

        // After first setPosition, last should be 0 (default) and lastLast should be 0
        assertEquals(0.0, player.getLastX());
        assertEquals(0.0, player.getLastY());
        assertEquals(0.0, player.getLastZ());

        player.setPosition(3.0, 65.0, 4.0);
        assertEquals(3.0, player.getX());
        assertEquals(65.0, player.getY());
        assertEquals(4.0, player.getZ());

        assertEquals(1.0, player.getLastX());
        assertEquals(64.0, player.getLastY());
        assertEquals(2.0, player.getLastZ());

        assertEquals(0.0, player.getLastLastX());
        assertEquals(0.0, player.getLastLastY());
        assertEquals(0.0, player.getLastLastZ());

        player.setPosition(5.0, 66.0, 6.0);
        assertEquals(5.0, player.getX());
        assertEquals(66.0, player.getY());
        assertEquals(6.0, player.getZ());

        assertEquals(3.0, player.getLastX());
        assertEquals(65.0, player.getLastY());
        assertEquals(4.0, player.getLastZ());

        assertEquals(1.0, player.getLastLastX());
        assertEquals(64.0, player.getLastLastY());
        assertEquals(2.0, player.getLastLastZ());
    }

    // === delta calculation after setPosition ===

    @Test
    void setPosition_calculatesDeltas() {
        WindfallPlayer player = createPlayer(767);

        player.setPosition(10.0, 64.0, 20.0);
        // delta = current - last, last was 0
        assertEquals(10.0, player.getDeltaX(), 0.001);
        assertEquals(64.0, player.getDeltaY(), 0.001);
        assertEquals(20.0, player.getDeltaZ(), 0.001); // z: 0 -> 20

        player.setPosition(11.0, 65.0, 22.0);
        assertEquals(1.0, player.getDeltaX(), 0.001);
        assertEquals(1.0, player.getDeltaY(), 0.001);
        assertEquals(2.0, player.getDeltaZ(), 0.001);
    }

    // === resetTickState ===

    @Test
    void resetTickState_copiesOnGroundToLastOnGround() {
        WindfallPlayer player = createPlayer(767);

        player.setOnGround(true);
        assertTrue(player.isOnGround());
        assertFalse(player.isLastOnGround());

        player.resetTickState();
        assertTrue(player.isLastOnGround());
    }

    @Test
    void resetTickState_resetsMovedSinceTick() {
        WindfallPlayer player = createPlayer(767);
        player.setMovedSinceTick(true);

        player.resetTickState();
        assertFalse(player.isMovedSinceTick());
    }

    // === isRespawned flag ===

    @Test
    void isRespawned_defaultsToFalse() {
        WindfallPlayer player = createPlayer(767);
        assertFalse(player.isRespawned());
    }

    @Test
    void setRespawned_setsFlag() {
        WindfallPlayer player = createPlayer(767);
        player.setRespawned(true);
        assertTrue(player.isRespawned());
    }

    // === Constructor basics ===

    @Test
    void constructor_setsUuidAndName() {
        WindfallPlayer player = createPlayer(767);
        assertEquals(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"), player.getUuid());
        assertEquals("TestPlayer", player.getName());
    }

    @Test
    void cachedWorldName_isNullBeforeFirstRefresh() {
        WindfallPlayer player = createPlayer(767);
        assertNull(player.getCachedWorldName());
    }

    @Test
    void updateCachedState_cachesWorldName() {
        WindfallPlayer player = createPlayer(767);
        World world = mock(World.class);
        when(mockBukkitPlayer.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("world_survival");

        player.updateCachedState();

        assertEquals("world_survival", player.getCachedWorldName());
    }

    @Test
    void updateCachedState_cachesWorldNameEvenWithoutPluginInstance() {
        WindfallPlayer player = createPlayer(767);
        World world = mock(World.class);
        when(mockBukkitPlayer.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("world");

        // getInstance() is unstubbed here, so it returns null. The world name is cached
        // before the plugin lookup, so it must still be populated, and the region cache
        // must degrade to false rather than throw on the tick thread.
        player.updateCachedState();

        assertEquals("world", player.getCachedWorldName());
        assertFalse(player.isCachedBlockCheckRegionExempt());
    }

    @Test
    void blockCheckRegionExempt_defaultsToFalse() {
        WindfallPlayer player = createPlayer(767);
        assertFalse(player.isCachedBlockCheckRegionExempt());
    }

    @Test
    void updateCachedState_doesNotThrowWithoutWorld() {
        WindfallPlayer player = createPlayer(767);
        when(mockBukkitPlayer.getWorld()).thenReturn(null);

        player.updateCachedState();

        assertNull(player.getCachedWorldName());
    }

    @Test
    void resolvedBlockType_isNullBeforeTheTickResolvesIt() {
        WindfallPlayer player = createPlayer(767);
        // Nothing has been resolved yet — FastBreak must see null and fall back.
        assertNull(player.getResolvedBlockType(10, 64, 20));
    }

    @Test
    void requestBlockType_doesNotTouchTheWorld() {
        WindfallPlayer player = createPlayer(767);
        player.requestBlockType(10, 64, 20);
        // The enqueue must not call the world; only resolvePendingBlockLookups() may.
        verify(mockBukkitPlayer, never()).getWorld();
    }

    @Test
    void resolvePendingBlockLookups_publishesResolvedType() {
        WindfallPlayer player = createPlayer(767);
        World world = mock(World.class);
        Block block = mock(Block.class);
        when(mockBukkitPlayer.getWorld()).thenReturn(world);
        when(world.getBlockAt(10, 64, 20)).thenReturn(block);
        when(block.getType()).thenReturn(Material.STONE);

        player.requestBlockType(10, 64, 20);
        player.resolvePendingBlockLookups();

        assertEquals(Material.STONE, player.getResolvedBlockType(10, 64, 20));
    }

    @Test
    void resolvePendingBlockLookups_distinguishesCoordinates() {
        WindfallPlayer player = createPlayer(767);
        World world = mock(World.class);
        // Build the blocks first: calling a stubbing helper inside when(...).thenReturn(...)
        // leaves Mockito with unfinished stubbing.
        Block dirt = blockOf(Material.DIRT);
        Block obsidian = blockOf(Material.OBSIDIAN);
        when(mockBukkitPlayer.getWorld()).thenReturn(world);
        when(world.getBlockAt(1, 64, 1)).thenReturn(dirt);
        when(world.getBlockAt(2, 64, 2)).thenReturn(obsidian);

        player.requestBlockType(1, 64, 1);
        player.requestBlockType(2, 64, 2);
        player.resolvePendingBlockLookups();

        assertEquals(Material.DIRT, player.getResolvedBlockType(1, 64, 1));
        assertEquals(Material.OBSIDIAN, player.getResolvedBlockType(2, 64, 2));
        assertNull(player.getResolvedBlockType(3, 64, 3));
    }

    @Test
    void resolvePendingBlockLookups_handlesNegativeCoordinates() {
        WindfallPlayer player = createPlayer(767);
        World world = mock(World.class);
        Block bedrock = blockOf(Material.BEDROCK);
        when(mockBukkitPlayer.getWorld()).thenReturn(world);
        when(world.getBlockAt(-134217728, 300, -134217728)).thenReturn(bedrock);

        player.requestBlockType(-134217728, 300, -134217728);
        player.resolvePendingBlockLookups();

        assertEquals(Material.BEDROCK, player.getResolvedBlockType(-134217728, 300, -134217728));
    }

    @Test
    void resolvePendingBlockLookups_fallsBackToAirWhenWorldThrows() {
        WindfallPlayer player = createPlayer(767);
        World world = mock(World.class);
        when(mockBukkitPlayer.getWorld()).thenReturn(world);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("chunk unloaded"));

        player.requestBlockType(5, 5, 5);
        player.resolvePendingBlockLookups();

        // Must degrade, not throw on the tick thread.
        assertEquals(Material.AIR, player.getResolvedBlockType(5, 5, 5));
    }

    @Test
    void requestBlockType_dropsRequestsBeyondTheQueueLimit() {
        WindfallPlayer player = createPlayer(767);
        // Flood far past the 8-entry cap; nothing should blow up and the queue stays bounded.
        for (int i = 0; i < 500; i++) {
            player.requestBlockType(i, 64, i);
        }
        player.resolvePendingBlockLookups();
        assertNull(player.getResolvedBlockType(400, 64, 400));
    }

    private static Block blockOf(Material material) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        return block;
    }

    @Test
    void constructor_setsProtocolVersion() {
        WindfallPlayer player = createPlayer(47);
        assertEquals(47, player.getProtocolVersion());
        assertEquals(mockClientVersion, player.getClientVersion());
    }
}

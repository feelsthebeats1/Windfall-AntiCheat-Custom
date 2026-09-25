package io.windfall.anticheat.core.config;

import io.windfall.anticheat.WindfallPlugin;
import io.windfall.anticheat.core.util.MaterialUtils;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.Set;

/**
 * Typed accessors for every config value — keeps config keys out of business logic.
 *
 * <p>Defaults are set once in {@link #setDefaults()} and merged into the file on first load.
 * The {@code copyDefaults(true)} call fills missing keys without overwriting user edits.
 *
 * <p>Per-check config follows a fallback pattern: if a per-check key (e.g.,
 * {@code checks.windfall.movement.speed.enabled}) is not set, it falls back to
 * {@code checks.default.enabled}. This lets operators override individual checks
 * while maintaining sane defaults.
 *
 * <p>Config sections:
 * <ul>
 *   <li><b>Alerts</b>: in-game chat notifications for staff</li>
 *   <li><b>Discord</b>: webhook integration for remote alerts</li>
 *   <li><b>Bedrock</b>: tolerance multipliers for Bedrock/Geyser players</li>
 *   <li><b>Severity</b>: VL escalation multiplier tiers</li>
 *   <li><b>Punishments</b>: warn → kick → tempban → permban thresholds</li>
 *   <li><b>Adaptive</b>: TPS-aware tolerance scaling during lag spikes</li>
 *   <li><b>Pattern</b>: repeat offender detection across sessions</li>
 *   <li><b>Fingerprint</b>: client behavioral identification vectors</li>
 *   <li><b>Checks</b>: per-check enable/disable, maxVL, setbackVL, decay, punishable</li>
 * </ul>
 *
 * @see io.windfall.anticheat.core.check.Check for config key usage
 * @see io.windfall.anticheat.core.punishment.PunishmentEngine for punishment thresholds
 */
// Typed accessors for every config value — keeps config keys out of business logic
// Defaults are set once in setDefaults() and merged into the file on first load
public class WindfallConfig {

    private final WindfallPlugin plugin;
    private FileConfiguration config;

    public WindfallConfig(WindfallPlugin plugin) {
        this.plugin = plugin;
        if (plugin == null) return; // Skip init for test mocks
        plugin.saveDefaultConfig();
        this.config = plugin.getConfig();
        setDefaults();
        // copyDefaults(true) fills missing keys without overwriting user edits
        config.options().copyDefaults(true);
        plugin.saveConfig();
    }

    private void setDefaults() {
        // Alerts
        config.addDefault("alerts.enabled", true);
        config.addDefault("alerts.prefix", "&8[&bWindfall&8] &7");
        config.addDefault("alerts.staff-permission", "windfall.alerts");
        config.addDefault("alerts.broadcast-to-all-staff", true);

        // Discord
        config.addDefault("discord.enabled", false);
        config.addDefault("discord.webhook-url", "");
        config.addDefault("discord.server-name", "My Server");
        config.addDefault("discord.mention-on-high-vl", true);
        config.addDefault("discord.mention-threshold", 25);
        config.addDefault("discord.avatar-url", "");
        config.addDefault("discord.embed-color-low", 16776960);
        config.addDefault("discord.embed-color-med", 16744448);
        config.addDefault("discord.embed-color-high", 16711680);
        config.addDefault("discord.rate-limit-ms", 5000);

        // Bedrock
        config.addDefault("bedrock.enabled", true);
        config.addDefault("bedrock.geyser-plugin-name", "Geyser-Spigot");
        config.addDefault("bedrock.use-floodgate-api", true);
        config.addDefault("bedrock.bedrock-reach-multiplier", 1.5);
        config.addDefault("bedrock.bedrock-speed-tolerance", 1.15);
        config.addDefault("bedrock.bedrock-scaffold-threshold", 0.8);
        config.addDefault("bedrock.bedrock-aim-snap-threshold", 200.0);
        config.addDefault("bedrock.bedrock-controller-aim-snap", 185.0);
        config.addDefault("bedrock.bedrock-touch-aim-snap", 200.0);
        config.addDefault("bedrock.bedrock-cps-limit", 20);
        config.addDefault("bedrock.bedrock-tolerance", 1.10);

        // Verbose
        config.addDefault("verbose", true);

        // Severity
        config.addDefault("severity.enabled", true);
        config.addDefault("severity.moderate-vl", 10);
        config.addDefault("severity.high-vl", 25);
        config.addDefault("severity.extreme-vl", 50);
        config.addDefault("severity.moderate-multiplier", 1.3);
        config.addDefault("severity.high-multiplier", 1.6);
        config.addDefault("severity.extreme-multiplier", 2.0);
        config.addDefault("severity.bedrock-discount", 0.6);

        // Punishments
        config.addDefault("punishments.enabled", true);
        config.addDefault("punishments.warn-vl", 5);
        config.addDefault("punishments.kick-vl", 10);
        config.addDefault("punishments.tempban-vl", 20);
        config.addDefault("punishments.tempban-duration", "1d");
        config.addDefault("punishments.permban-vl", 30);
        config.addDefault("punishments.warn-message", "&c[Windfall] &eWarning: further cheating will result in a kick.");
        config.addDefault("punishments.kick-message", "&c[Windfall] Kicked for cheating.");
        config.addDefault("punishments.tempban-reason", "[Windfall] Temporarily banned for cheating.");
        config.addDefault("punishments.permban-reason", "[Windfall] Permanently banned for cheating.");

        // Adaptive Threshold — TPS-aware tolerance scaling
        config.addDefault("adaptive.enabled", true);
        config.addDefault("adaptive.tps-threshold", 19.0);
        config.addDefault("adaptive.scale-factor", 0.02);
        config.addDefault("adaptive.max-tolerance-multiplier", 2.0);
        config.addDefault("adaptive.safe-mode-threshold", 12.0);

        // Violation Pattern — repeat offender tracking
        config.addDefault("pattern.enabled", true);
        config.addDefault("pattern.history-days", 30);
        config.addDefault("pattern.repeat-threshold", 3);
        config.addDefault("pattern.toggle-detection-window", 6000);

        // Packet Fingerprint — client behavioral fingerprinting
        config.addDefault("fingerprint.enabled", true);
        config.addDefault("fingerprint.min-severity-to-flag", 60);
        config.addDefault("fingerprint.max-fingerprint-age-ticks", 6000);

        // Prometheus Metrics
        config.addDefault("prometheus.enabled", false);
        config.addDefault("prometheus.host", "127.0.0.1");
        config.addDefault("prometheus.port", 9211);

        // Check defaults — per-check values override these if set
        config.addDefault("checks.default.enabled", true);
        config.addDefault("checks.default.max-vl", 100);
        config.addDefault("checks.default.setback-vl", 20);
        config.addDefault("checks.default.decay", 0.02);
        config.addDefault("checks.default.punishable", true);

        // Per-check entries are optional overrides. Do not register defaults here:
        // registering them would make annotation setback/decay values indistinguishable
        // from explicit operator settings. The bundled config.yml documents every key.
    }

    // === Alert config ===
    public boolean isAlertsEnabled() {
        return config.getBoolean("alerts.enabled", true);
    }

    /** Alert-only mode: flags/alerts still run, but punishment and setback are suppressed. */
    public boolean isAlertOnlyMode() {
        return config.getBoolean("alerts.alert-only", false);
    }

    public String getAlertPrefix() {
        return config.getString("alerts.prefix", "&8[&bWindfall&8] &7");
    }

    public String getAlertsStaffPermission() {
        return config.getString("alerts.staff-permission", "windfall.alerts");
    }

    public boolean isBroadcastToAllStaff() {
        return config.getBoolean("alerts.broadcast-to-all-staff", true);
    }

    // === Discord config ===
    public boolean isDiscordEnabled() {
        return config.getBoolean("discord.enabled", false);
    }

    public String getDiscordWebhookUrl() {
        return config.getString("discord.webhook-url", "");
    }

    public String getDiscordServerName() {
        return config.getString("discord.server-name", "My Server");
    }

    public boolean isDiscordMentionOnHighVl() {
        return config.getBoolean("discord.mention-on-high-vl", true);
    }

    public int getDiscordMentionThreshold() {
        return config.getInt("discord.mention-threshold", 25);
    }

    public String getDiscordAvatarUrl() {
        return config.getString("discord.avatar-url", "");
    }

    public int getDiscordEmbedColor(int vl) {
        if (vl >= getDiscordMentionThreshold()) {
            return config.getInt("discord.embed-color-high", 16711680);
        } else if (vl >= 10) {
            return config.getInt("discord.embed-color-med", 16744448);
        }
        return config.getInt("discord.embed-color-low", 16776960);
    }

    public long getDiscordRateLimitMs() {
        return config.getLong("discord.rate-limit-ms", 5000);
    }

    // === Bedrock config ===
    public boolean isBedrockEnabled() {
        return config.getBoolean("bedrock.enabled", true);
    }

    public String getBedrockGeyserPluginName() {
        return config.getString("bedrock.geyser-plugin-name", "Geyser-Spigot");
    }

    public boolean isBedrockUseFloodgateApi() {
        return config.getBoolean("bedrock.use-floodgate-api", true);
    }

    public double getBedrockReachMultiplier() {
        return config.getDouble("bedrock.bedrock-reach-multiplier", 1.5);
    }

    public double getBedrockSpeedTolerance() {
        return config.getDouble("bedrock.bedrock-speed-tolerance", 1.15);
    }

    public double getBedrockScaffoldThreshold() {
        return config.getDouble("bedrock.bedrock-scaffold-threshold", 0.8);
    }

    public double getBedrockAimSnapThreshold() {
        return config.getDouble("bedrock.bedrock-aim-snap-threshold", 200.0);
    }

    public double getBedrockControllerAimSnap() {
        return config.getDouble("bedrock.bedrock-controller-aim-snap", 185.0);
    }

    public double getBedrockTouchAimSnap() {
        return config.getDouble("bedrock.bedrock-touch-aim-snap", 200.0);
    }

    public int getBedrockCpsLimit() {
        return config.getInt("bedrock.bedrock-cps-limit", 20);
    }

    public double getBedrockTolerance() {
        return config.getDouble("bedrock.bedrock-tolerance", 1.10);
    }

    // === Verbose ===
    public boolean isVerboseEnabled() {
        return config.getBoolean("verbose", true);
    }

    // === Severity config ===
    public boolean isSeverityEnabled() {
        return config.getBoolean("severity.enabled", true);
    }

    public int getSeverityModerateVl() {
        return config.getInt("severity.moderate-vl", 10);
    }

    public int getSeverityHighVl() {
        return config.getInt("severity.high-vl", 25);
    }

    public int getSeverityExtremeVl() {
        return config.getInt("severity.extreme-vl", 50);
    }

    public double getSeverityModerateMultiplier() {
        return config.getDouble("severity.moderate-multiplier", 1.3);
    }

    public double getSeverityHighMultiplier() {
        return config.getDouble("severity.high-multiplier", 1.6);
    }

    public double getSeverityExtremeMultiplier() {
        return config.getDouble("severity.extreme-multiplier", 2.0);
    }

    public double getSeverityBedrockDiscount() {
        return config.getDouble("severity.bedrock-discount", 0.6);
    }

    // === Punishment config ===
    public boolean isPunishmentsEnabled() {
        return config.getBoolean("punishments.enabled", true);
    }

    public int getPunishmentWarnVl() {
        return config.getInt("punishments.warn-vl", 5);
    }

    public int getPunishmentKickVl() {
        return config.getInt("punishments.kick-vl", 10);
    }

    public int getPunishmentTempbanVl() {
        return config.getInt("punishments.tempban-vl", 20);
    }

    public String getPunishmentTempbanDuration() {
        return config.getString("punishments.tempban-duration", "1d");
    }

    public int getPunishmentPermbanVl() {
        return config.getInt("punishments.permban-vl", 30);
    }

    public String getPunishmentWarnMessage() {
        return config.getString("punishments.warn-message",
            "&c[Windfall] &eWarning: further cheating will result in a kick.");
    }

    public String getPunishmentKickMessage() {
        return config.getString("punishments.kick-message", "&c[Windfall] Kicked for cheating.");
    }

    public String getPunishmentTempbanReason() {
        return config.getString("punishments.tempban-reason",
            "[Windfall] Temporarily banned for cheating.");
    }

    public String getPunishmentPermbanReason() {
        return config.getString("punishments.permban-reason",
            "[Windfall] Permanently banned for cheating.");
    }

    // === Adaptive Threshold config ===
    public boolean isAdaptiveEnabled() {
        return config.getBoolean("adaptive.enabled", true);
    }

    public double getAdaptiveTpsThreshold() {
        return config.getDouble("adaptive.tps-threshold", 19.0);
    }

    public double getAdaptiveScaleFactor() {
        return config.getDouble("adaptive.scale-factor", 0.02);
    }

    public double getAdaptiveMaxToleranceMultiplier() {
        return config.getDouble("adaptive.max-tolerance-multiplier", 2.0);
    }

    public double getAdaptiveSafeModeThreshold() {
        return config.getDouble("adaptive.safe-mode-threshold", 12.0);
    }

    // === Violation Pattern config ===
    public boolean isPatternEnabled() {
        return config.getBoolean("pattern.enabled", true);
    }

    public int getPatternHistoryDays() {
        return config.getInt("pattern.history-days", 30);
    }

    public int getPatternRepeatThreshold() {
        return config.getInt("pattern.repeat-threshold", 3);
    }

    public int getPatternToggleDetectionWindow() {
        return config.getInt("pattern.toggle-detection-window", 6000);
    }

    // === Packet Fingerprint config ===
    public boolean isFingerprintEnabled() {
        return config.getBoolean("fingerprint.enabled", true);
    }

    public int getFingerprintMinSeverityToFlag() {
        return config.getInt("fingerprint.min-severity-to-flag", 60);
    }

    public int getFingerprintMaxAgeTicks() {
        return config.getInt("fingerprint.max-fingerprint-age-ticks", 6000);
    }

    // === Prometheus Metrics config ===
    public boolean isPrometheusEnabled() {
        return config.getBoolean("prometheus.enabled", false);
    }

    public String getPrometheusHost() {
        return config.getString("prometheus.host", "127.0.0.1");
    }

    public int getPrometheusPort() {
        return config.getInt("prometheus.port", 9211);
    }

    // === FastBreak config ===
    public double getFastBreakTimeMultiplier() {
        return config.getDouble("checks.windfall.movement.fastbreak.detection.time-multiplier", 0.85);
    }

    public double getFastBreakMinimumFlagBuffer() {
        return config.getDouble("checks.windfall.movement.fastbreak.detection.minimum-flag-buffer", 3.0);
    }

    public long getFastBreakNetworkGraceMs() {
        return config.getLong("checks.windfall.movement.fastbreak.detection.network-grace-ms", 100L);
    }

    public long getFastBreakMinimumCheckMs() {
        return config.getLong("checks.windfall.movement.fastbreak.detection.minimum-check-ms", 50L);
    }

    public double getFastBreakBufferIncrease() {
        return config.getDouble("checks.windfall.movement.fastbreak.detection.buffer-increase", 1.0);
    }

    public double getFastBreakBufferDecrease() {
        return config.getDouble("checks.windfall.movement.fastbreak.detection.buffer-decrease", 1.0);
    }

    public boolean isFastBreakToolAware() {
        return config.getBoolean("checks.windfall.movement.fastbreak.detection.account-for-tools", true);
    }

    public boolean isFastBreakEfficiencyAware() {
        return config.getBoolean("checks.windfall.movement.fastbreak.detection.account-for-enchantments", true);
    }

    public double getFastBreakBlockTimeOverride(String materialName) {
        return config.getDouble(
                "checks.windfall.movement.fastbreak.detection.block-time-overrides." + materialName, 0.0);
    }

    public boolean isBlockCheckWorldExempt(String worldName) {
        if (worldName == null || !config.getBoolean("block-checks.exempt-enabled", false)) return false;
        return config.getStringList("block-checks.exempt-worlds").stream()
                .anyMatch(w -> w.equalsIgnoreCase(worldName));
    }

    public boolean isBlockCheckRegionExempt() {
        return config.getBoolean("block-checks.exempt-enabled", false)
                && config.getBoolean("block-checks.exempt-in-worldguard-regions", false);
    }

    // === Nuker config ===
    public long getNukerWindowMs() {
        return config.getLong("checks.windfall.movement.nuker.detection.window-ms", 1000L);
    }

    public int getNukerMaximumBlocks() {
        return config.getInt("checks.windfall.movement.nuker.detection.maximum-blocks", 12);
    }

    public int getNukerSuspiciousBlocks() {
        return config.getInt("checks.windfall.movement.nuker.detection.suspicious-blocks", 7);
    }

    public int getNukerMinimumFastBlocks() {
        return config.getInt("checks.windfall.movement.nuker.detection.minimum-fast-blocks", 3);
    }

    public int getNukerMaximumSameTick() {
        return config.getInt("checks.windfall.movement.nuker.detection.maximum-same-tick", 1);
    }

    public double getNukerMinimumTargetSwitchDistance() {
        return config.getDouble("checks.windfall.movement.nuker.detection.minimum-target-switch-distance", 2.0);
    }

    public double getNukerMaximumTargetSwitchDistance() {
        return config.getDouble("checks.windfall.movement.nuker.detection.maximum-target-switch-distance", 7.0);
    }

    public double getNukerMinimumFlagBuffer() {
        return config.getDouble("checks.windfall.movement.nuker.detection.minimum-flag-buffer", 3.0);
    }

    // === Check config — falls back to default.* if per-check key not set ===
    public boolean isCheckEnabled(String checkKey) {
        String path = "checks." + checkKey + ".enabled";
        if (config.isSet(path)) {
            return config.getBoolean(path);
        }
        return config.getBoolean("checks.default.enabled", true);
    }

    public int getCheckMaxVl(String checkKey) {
        String path = "checks." + checkKey + ".max-vl";
        if (config.isSet(path)) {
            return config.getInt(path);
        }
        return config.getInt("checks.default.max-vl", 100);
    }

    public int getCheckSetbackVl(String checkKey) {
        String path = "checks." + checkKey + ".setback-vl";
        if (config.isSet(path)) {
            return config.getInt(path);
        }
        return config.getInt("checks.default.setback-vl", 20);
    }

    public double getCheckDecay(String checkKey) {
        String path = "checks." + checkKey + ".decay";
        if (config.isSet(path)) {
            return config.getDouble(path);
        }
        return config.getDouble("checks.default.decay", 0.02);
    }

    /** Returns true when a per-check option was explicitly present in config.yml. */
    public boolean hasCheckOverride(String checkKey, String option) {
        return config.isSet("checks." + checkKey + "." + option);
    }

    public boolean isCheckPunishable(String checkKey) {
        String path = "checks." + checkKey + ".punishable";
        if (config.isSet(path)) {
            return config.getBoolean(path);
        }
        return config.getBoolean("checks.default.punishable", true);
    }

    /** Returns all registered check stableKeys (e.g., "windfall.movement.speed") */
    public Set<String> getCheckKeys() {
        if (config.isConfigurationSection("checks")) {
            return config.getConfigurationSection("checks").getKeys(false);
        }
        return Collections.emptySet();
    }

    // === Config persistence (for GUI sync) ===

    /** Persists the enabled state for a specific check to config.yml */
    public void saveCheckEnabled(String checkKey, boolean enabled) {
        config.set("checks." + checkKey + ".enabled", enabled);
        plugin.saveConfig();
    }

    /** Persists the punishable state for a specific check to config.yml */
    public void saveCheckPunishable(String checkKey, boolean punishable) {
        config.set("checks." + checkKey + ".punishable", punishable);
        plugin.saveConfig();
    }

    /**
     * Reloads config from disk and clears material caches.
     * Called by {@link io.windfall.anticheat.core.check.CheckManager#reloadChecks()}.
     */
    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        MaterialUtils.clearCaches();
    }
}

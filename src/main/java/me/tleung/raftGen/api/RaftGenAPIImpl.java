package me.tleung.raftGen.api;

import me.tleung.raftGen.RaftGen;
import me.tleung.raftGen.RaftManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.Material;
import java.util.Set;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RaftGenAPIImpl implements RaftGenAPI {
    private final RaftGen plugin;
    private final RaftManager raftManager;

    public RaftGenAPIImpl(@NotNull RaftGen plugin) {
        this.plugin = plugin;
        this.raftManager = plugin.getRaftManager();
    }

    @NotNull
    @Override
    public Plugin getPlugin() {
        return plugin;
    }

    @Override
    public boolean createRaft(@NotNull Player player, @Nullable Location location) {
        if (location != null) {
            return raftManager.createRaftAtLocation(player, location);
        }
        raftManager.createRaft(player);
        return true;
    }

    @Nullable
    @Override
    public Location getRaftLocation(@NotNull UUID playerId) {
        return raftManager.getPlayerRaftLocation(playerId);
    }

    @Override
    public int getRaftLevel(@NotNull UUID playerId) {
        return raftManager.getPlayerRaftLevel(playerId);
    }

    @Override
    public boolean setRaftLevel(@NotNull UUID playerId, int level) {
        if (level < 1) return false;
        raftManager.setPlayerRaftLevel(playerId, level);
        return true;
    }

    @Override
    public boolean deleteRaft(@NotNull UUID playerId) {
        if (!raftManager.hasRaft(playerId)) return false;

        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            raftManager.deleteRaft(player, null);
        } else {
            raftManager.forceDeleteRaft(playerId);
        }
        return true;
    }

    @Override
    public boolean hasRaft(@NotNull UUID playerId) {
        return raftManager.hasRaft(playerId);
    }

    @NotNull
    @Override
    public org.bukkit.World getRaftWorld() {
        return raftManager.getRaftWorld();
    }

    @Override
    public double calculateRaftValue(@NotNull Location location, int radius) {
        return raftManager.calculateRaftValue(location, radius);
    }

    @Override
    public void updateRaftLevel(@NotNull UUID playerId) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            raftManager.handleCalculateCommand(player);
        }
    }

    @Override
    public boolean teleportToRaft(@NotNull Player player) {
        if (!raftManager.hasRaft(player.getUniqueId())) {
            return false;
        }
        raftManager.teleportToRaft(player);
        return true;
    }

    @Override
    public boolean resetRaft(@NotNull Player player) {
        if (!raftManager.hasRaft(player.getUniqueId())) {
            return false;
        }
        raftManager.resetRaft(player);
        return true;
    }

    @Nullable
    @Override
    public String getRaftName(@NotNull UUID playerId) {
        return raftManager.getRaftName(playerId);
    }

    @Override
    public boolean setRaftName(@NotNull UUID playerId, @NotNull String name) {
        if (!raftManager.hasRaft(playerId)) {
            return false;
        }
        raftManager.setRaftName(playerId, name);
        return true;
    }

    @Override
    public double getRaftValue(@NotNull UUID playerId) {
        return raftManager.getRaftValue(playerId);
    }

    @Override
    public int getRaftRadius(@NotNull UUID playerId) {
        int level = getRaftLevel(playerId);
        return 1 + (level / 5);
    }

    @NotNull
    @Override
    public Map<UUID, Location> getAllRafts() {
        return raftManager.getAllRafts();
    }

    @Override
    public int getTotalRaftCount() {
        return raftManager.getRaftCount();
    }

    @Override
    public boolean isAutoScanEnabled() {
        return plugin.getConfig().getBoolean("level.auto-scan-enabled", true);
    }

    @Override
    public void setAutoScanEnabled(boolean enabled) {
        plugin.getConfig().set("level.auto-scan-enabled", enabled);
        plugin.saveConfig();
    }

    @NotNull
    @Override
    public TeamAPI getTeamAPI() {
        // 返回一個基本的實現
        return new BasicTeamAPI();
    }

    @NotNull
    @Override
    public LevelAPI getLevelAPI() {
        // 返回一個基本的實現
        return new BasicLevelAPI();
    }

    // 基本的 TeamAPI 實現
    private class BasicTeamAPI implements TeamAPI {
        @Override
        public boolean createTeam(Player leader) { return false; }
        @Override
        public boolean disbandTeam(Player leader) { return false; }
        @Override
        public boolean invitePlayer(Player leader, String targetName) { return false; }
        @Override
        public boolean acceptInvite(Player player) { return false; }
        @Override
        public boolean denyInvite(Player player) { return false; }
        @Override
        public boolean leaveTeam(Player player) { return false; }
        @Override
        public boolean kickPlayer(Player leader, String targetName) { return false; }
        @Override
        public UUID getPlayerTeamLeader(UUID playerId) { return null; }
        @Override
        public Set<UUID> getTeamMembers(UUID leaderId) { return Set.of(); }
        @Override
        public boolean isTeamLeader(UUID playerId) { return false; }
        @Override
        public boolean isInTeam(UUID playerId) { return false; }
        @Override
        public void broadcastToTeam(UUID leaderId, String message) {}
        @Override
        public int getTeamCount() { return 0; }
        @Override
        public String getTeamInfo(Player player) { return "團隊功能暫不可用"; }
    }

    // 基本的 LevelAPI 實現
    private class BasicLevelAPI implements LevelAPI {
        @Override
        public double calculateValue(Location location, int radius) { return 0; }
        @Override
        public int calculateLevel(double totalValue) { return 1; }
        @Override
        public double getValueForNextLevel(int currentLevel) { return 0; }
        @Override
        public double getValueForLevel(int level) { return 0; }
        @Override
        public double getBlockValue(Material material) { return 0; }
        @Override
        public void setBlockValue(Material material, double value) {}
        @Override
        public Map<Material, Double> getAllBlockValues() { return Map.of(); }
        @Override
        public double getLevelProgress(UUID playerId) { return 0; }
        @Override
        public double getValueNeededForNextLevel(UUID playerId) { return 0; }
    }
}
package me.tleung.raftGen.api;

import me.tleung.raftGen.LevelCalculator;
import me.tleung.raftGen.RaftGen;
import me.tleung.raftGen.RaftManager;
import me.tleung.raftGen.TeamManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RaftGenAPIImpl implements RaftGenAPI {
    private final RaftGen plugin;
    private final RaftManager raftManager;
    private final TeamManager teamManager;
    private final LevelCalculator levelCalculator;
    private final TeamAPIImpl teamAPI;
    private final LevelAPIImpl levelAPI;

    public RaftGenAPIImpl(@NotNull RaftGen plugin) {
        this.plugin = plugin;
        this.raftManager = plugin.getRaftManager();
        this.teamManager = raftManager.getTeamManager();
        this.levelCalculator = new LevelCalculator();
        this.teamAPI = new TeamAPIImpl();
        this.levelAPI = new LevelAPIImpl();
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
        return teamAPI;
    }

    @NotNull
    @Override
    public LevelAPI getLevelAPI() {
        return levelAPI;
    }

    @NotNull
    @Override
    public String getPluginStatus() {
        return plugin.getPluginStatus();
    }

    @Override
    public boolean isInRaftWorld(@NotNull Player player) {
        return player.getWorld().equals(getRaftWorld());
    }

    @Nullable
    @Override
    public UUID getPlayerTeamLeader(@NotNull UUID playerId) {
        return teamManager.getPlayerTeamLeader(playerId);
    }

    @Override
    public boolean isTeamLeader(@NotNull UUID playerId) {
        return teamManager.isTeamLeader(playerId);
    }

    @NotNull
    @Override
    public Map<UUID, Integer> getAllRaftLevels() {
        return raftManager.getAllRaftLevels();
    }

    @NotNull
    @Override
    public Map<UUID, String> getAllRaftNames() {
        return raftManager.getAllRaftNames();
    }

    // TeamAPI 實現類
    private class TeamAPIImpl implements TeamAPI {
        @Override
        public boolean createTeam(@NotNull Player leader) {
            return teamManager.createTeam(leader);
        }

        @Override
        public boolean disbandTeam(@NotNull Player leader) {
            return teamManager.disbandTeam(leader);
        }

        @Override
        public boolean invitePlayer(@NotNull Player leader, @NotNull String targetName) {
            return teamManager.invitePlayer(leader, targetName);
        }

        @Override
        public boolean acceptInvite(@NotNull Player player) {
            return teamManager.acceptInvite(player);
        }

        @Override
        public boolean denyInvite(@NotNull Player player) {
            return teamManager.denyInvite(player);
        }

        @Override
        public boolean leaveTeam(@NotNull Player player) {
            return teamManager.leaveTeam(player);
        }

        @Override
        public boolean kickPlayer(@NotNull Player leader, @NotNull String targetName) {
            return teamManager.kickPlayer(leader, targetName);
        }

        @Nullable
        @Override
        public UUID getPlayerTeamLeader(@NotNull UUID playerId) {
            return teamManager.getPlayerTeamLeader(playerId);
        }

        @NotNull
        @Override
        public Set<UUID> getTeamMembers(@NotNull UUID leaderId) {
            return teamManager.getTeamMembers(leaderId);
        }

        @Override
        public boolean isTeamLeader(@NotNull UUID playerId) {
            return teamManager.isTeamLeader(playerId);
        }

        @Override
        public boolean isInTeam(@NotNull UUID playerId) {
            return teamManager.isInTeam(playerId);
        }

        @Override
        public void broadcastToTeam(@NotNull UUID leaderId, @NotNull String message) {
            teamManager.broadcastToTeam(leaderId, message);
        }

        @Override
        public int getTeamCount() {
            return teamManager.getTeamCount();
        }

        @NotNull
        @Override
        public String getTeamInfo(@NotNull Player player) {
            teamManager.showTeamInfo(player);
            return "團隊信息已發送給玩家";
        }

        @Override
        public int getTeamMemberCount(@NotNull UUID leaderId) {
            return teamManager.getTeamMembers(leaderId).size();
        }

        @Override
        public boolean hasPendingInvite(@NotNull UUID playerId) {
            // 需要在 TeamManager 中添加此方法
            // 暫時返回 false
            return false;
        }

        @Nullable
        @Override
        public UUID getInviteLeader(@NotNull UUID playerId) {
            // 需要在 TeamManager 中添加此方法
            // 暫時返回 null
            return null;
        }

        @Override
        public boolean cancelInvite(@NotNull UUID playerId) {
            // 需要在 TeamManager 中添加此方法
            // 暫時返回 false
            return false;
        }
    }

    // LevelAPI 實現類
    private class LevelAPIImpl implements LevelAPI {
        @Override
        public double calculateValue(@NotNull Location location, int radius) {
            return levelCalculator.calculateRaftValue(location, radius);
        }

        @Override
        public int calculateLevel(double totalValue) {
            return levelCalculator.calculateLevel(totalValue);
        }

        @Override
        public double getValueForNextLevel(int currentLevel) {
            return levelCalculator.getValueForNextLevel(currentLevel);
        }

        @Override
        public double getValueForLevel(int level) {
            return levelCalculator.getValueForLevel(level);
        }

        @Override
        public double getBlockValue(@NotNull Material material) {
            return levelCalculator.getBlockValue(material);
        }

        @Override
        public void setBlockValue(@NotNull Material material, double value) {
            levelCalculator.setBlockValue(material, value);
        }

        @NotNull
        @Override
        public Map<Material, Double> getAllBlockValues() {
            return levelCalculator.getAllBlockValues();
        }

        @Override
        public double getLevelProgress(@NotNull UUID playerId) {
            double currentValue = getRaftValue(playerId);
            int currentLevel = getRaftLevel(playerId);
            double nextLevelValue = getValueForNextLevel(currentLevel);

            if (nextLevelValue <= 0) return 100.0;
            double progress = (currentValue / nextLevelValue) * 100;
            return Math.min(progress, 100.0);
        }

        @Override
        public double getValueNeededForNextLevel(@NotNull UUID playerId) {
            double currentValue = getRaftValue(playerId);
            int currentLevel = getRaftLevel(playerId);
            double nextLevelValue = getValueForNextLevel(currentLevel);
            return Math.max(0, nextLevelValue - currentValue);
        }

        @Override
        public void setBlockValues(@NotNull Map<Material, Double> values) {
            levelCalculator.setBlockValues(values);
        }

        @Override
        public void resetBlockValues() {
            levelCalculator.resetBlockValues();
        }

        @Override
        public int getMaxLevel() {
            // 暫時返回固定值
            return 20;
        }

        @Override
        public void setMaxLevel(int maxLevel) {
            // 需要在 LevelCalculator 中添加此方法
        }

        @Override
        public int getRadiusForLevel(int level) {
            return 1 + (level / 5);
        }

        @NotNull
        @Override
        public String getProgressBar(@NotNull UUID playerId) {
            double progress = getLevelProgress(playerId);
            int bars = (int) (progress / 5);

            StringBuilder bar = new StringBuilder("§a[");
            for (int i = 0; i < 20; i++) {
                if (i < bars) {
                    bar.append("█");
                } else {
                    bar.append("§7█");
                }
            }
            bar.append("§a] §e").append(String.format("%.1f", progress)).append("%");
            return bar.toString();
        }
    }
}
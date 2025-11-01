package me.tleung.raftGen.api;

import me.tleung.raftGen.RaftGen;
import me.tleung.raftGen.RaftManager;
import me.tleung.raftGen.TeamManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RaftGenAPIImpl implements RaftGenAPI {
    private final RaftGen plugin;
    private final RaftManager raftManager;
    private final TeamManager teamManager;
    private final TeamAPIImpl teamAPI;

    public RaftGenAPIImpl(@NotNull RaftGen plugin) {
        this.plugin = plugin;
        this.raftManager = plugin.getRaftManager();
        this.teamManager = raftManager.getTeamManager();
        this.teamAPI = new TeamAPIImpl();
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

    @NotNull
    @Override
    public Map<UUID, Location> getAllRafts() {
        return raftManager.getAllRafts();
    }

    @Override
    public int getTotalRaftCount() {
        return raftManager.getRaftCount();
    }

    @NotNull
    @Override
    public TeamAPI getTeamAPI() {
        return teamAPI;
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
            return teamManager.hasPendingInvite(playerId);
        }

        @Nullable
        @Override
        public UUID getInviteLeader(@NotNull UUID playerId) {
            return teamManager.getInviteLeader(playerId);
        }

        @Override
        public boolean cancelInvite(@NotNull UUID playerId) {
            return teamManager.cancelInvite(playerId);
        }
    }

    // 以下方法已移除，因为等级系统已被删除：
    // - getRaftLevel()
    // - setRaftLevel()
    // - calculateRaftValue()
    // - updateRaftLevel()
    // - getRaftValue()
    // - getRaftRadius()
    // - isAutoScanEnabled()
    // - setAutoScanEnabled()
    // - getLevelAPI()
    // - getAllRaftLevels()
    // - LevelAPIImpl 内部类
}
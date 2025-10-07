package me.tleung.raftGen;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class TeamManager {

    private final RaftGen plugin;
    private final HashMap<UUID, UUID> playerTeams;
    private final HashMap<UUID, Set<UUID>> teamMembers;
    private final HashMap<UUID, UUID> teamInvites;

    public TeamManager(RaftGen plugin) {
        this.plugin = plugin;
        this.playerTeams = new HashMap<>();
        this.teamMembers = new HashMap<>();
        this.teamInvites = new HashMap<>();
    }

    public boolean createTeam(Player leader) {
        UUID leaderId = leader.getUniqueId();

        if (playerTeams.containsKey(leaderId)) {
            leader.sendMessage("§c你已經在一個隊伍中了!");
            return false;
        }

        playerTeams.put(leaderId, leaderId);
        teamMembers.put(leaderId, new HashSet<>());
        teamMembers.get(leaderId).add(leaderId);

        leader.sendMessage("§a隊伍創建成功! 你是隊長");
        leader.sendMessage("§6使用 §a/raft team invite <玩家名稱> §6來邀請隊員");
        return true;
    }

    public boolean disbandTeam(Player leader) {
        UUID leaderId = leader.getUniqueId();

        if (!isTeamLeader(leaderId)) {
            leader.sendMessage("§c只有隊長可以解散隊伍!");
            return false;
        }

        Set<UUID> members = teamMembers.get(leaderId);
        if (members != null) {
            for (UUID memberId : members) {
                playerTeams.remove(memberId);
                Player member = Bukkit.getPlayer(memberId);
                if (member != null && member.isOnline()) {
                    member.sendMessage("§c隊伍已被隊長解散!");
                }
            }
            teamMembers.remove(leaderId);
        }

        leader.sendMessage("§a隊伍已解散!");
        return true;
    }

    public boolean invitePlayer(Player leader, String targetName) {
        UUID leaderId = leader.getUniqueId();

        if (!isTeamLeader(leaderId)) {
            leader.sendMessage("§c只有隊長可以邀請隊員!");
            return false;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            leader.sendMessage("§c找不到玩家: " + targetName);
            return false;
        }

        if (target.getUniqueId().equals(leaderId)) {
            leader.sendMessage("§c你不能邀請自己!");
            return false;
        }

        if (playerTeams.containsKey(target.getUniqueId())) {
            leader.sendMessage("§c該玩家已經在一個隊伍中了!");
            return false;
        }

        teamInvites.put(target.getUniqueId(), leaderId);

        leader.sendMessage("§a已向 " + target.getName() + " 發送組隊邀請!");
        target.sendMessage("§6=== 組隊邀請 ===");
        target.sendMessage("§a玩家 " + leader.getName() + " 邀請你加入隊伍!");
        target.sendMessage("§a使用 §e/raft team accept §a接受邀請");
        target.sendMessage("§c使用 §e/raft team deny §c拒絕邀請");
        target.sendMessage("§6邀請將在 60 秒後過期");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (teamInvites.remove(target.getUniqueId()) != null) {
                target.sendMessage("§c組隊邀請已過期");
                leader.sendMessage("§c對 " + target.getName() + " 的組隊邀請已過期");
            }
        }, 20 * 60);

        return true;
    }

    public boolean acceptInvite(Player player) {
        UUID playerId = player.getUniqueId();
        UUID leaderId = teamInvites.get(playerId);

        if (leaderId == null) {
            player.sendMessage("§c你沒有待處理的組隊邀請!");
            return false;
        }

        Player leader = Bukkit.getPlayer(leaderId);
        if (leader == null || !leader.isOnline()) {
            player.sendMessage("§c邀請你的玩家已離線!");
            teamInvites.remove(playerId);
            return false;
        }

        teamInvites.remove(playerId);
        playerTeams.put(playerId, leaderId);
        teamMembers.get(leaderId).add(playerId);

        player.sendMessage("§a你已成功加入 " + leader.getName() + " 的隊伍!");
        leader.sendMessage("§a玩家 " + player.getName() + " 已加入你的隊伍!");
        broadcastToTeam(leaderId, "§6玩家 " + player.getName() + " 加入了隊伍!");
        return true;
    }

    public boolean denyInvite(Player player) {
        UUID playerId = player.getUniqueId();
        UUID leaderId = teamInvites.remove(playerId);

        if (leaderId == null) {
            player.sendMessage("§c你沒有待處理的組隊邀請!");
            return false;
        }

        Player leader = Bukkit.getPlayer(leaderId);
        if (leader != null && leader.isOnline()) {
            leader.sendMessage("§c玩家 " + player.getName() + " 拒絕了你的組隊邀請");
        }

        player.sendMessage("§a已拒絕組隊邀請");
        return true;
    }

    public boolean leaveTeam(Player player) {
        UUID playerId = player.getUniqueId();
        UUID leaderId = playerTeams.get(playerId);

        if (leaderId == null) {
            player.sendMessage("§c你不在任何隊伍中!");
            return false;
        }

        if (leaderId.equals(playerId)) {
            player.sendMessage("§c隊長不能離開隊伍! 使用 /raft team disband 解散隊伍");
            return false;
        }

        playerTeams.remove(playerId);
        teamMembers.get(leaderId).remove(playerId);

        player.sendMessage("§a你已離開隊伍!");
        broadcastToTeam(leaderId, "§6玩家 " + player.getName() + " 離開了隊伍!");
        return true;
    }

    public boolean kickPlayer(Player leader, String targetName) {
        UUID leaderId = leader.getUniqueId();

        if (!isTeamLeader(leaderId)) {
            leader.sendMessage("§c只有隊長可以踢出隊員!");
            return false;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            leader.sendMessage("§c找不到玩家: " + targetName);
            return false;
        }

        UUID targetId = target.getUniqueId();
        if (!teamMembers.get(leaderId).contains(targetId)) {
            leader.sendMessage("§c該玩家不在你的隊伍中!");
            return false;
        }

        if (targetId.equals(leaderId)) {
            leader.sendMessage("§c你不能踢出自己!");
            return false;
        }

        playerTeams.remove(targetId);
        teamMembers.get(leaderId).remove(targetId);

        leader.sendMessage("§a已將 " + target.getName() + " 踢出隊伍!");
        target.sendMessage("§c你已被隊長踢出隊伍!");
        broadcastToTeam(leaderId, "§6玩家 " + target.getName() + " 被踢出隊伍!");
        return true;
    }

    public void showTeamInfo(Player player) {
        UUID playerId = player.getUniqueId();
        UUID leaderId = playerTeams.get(playerId);

        if (leaderId == null) {
            player.sendMessage("§c你不在任何隊伍中!");
            return;
        }

        Player leader = Bukkit.getPlayer(leaderId);
        String leaderName = leader != null ? leader.getName() : "未知";

        Set<UUID> members = teamMembers.get(leaderId);
        player.sendMessage("§6=== 隊伍資訊 ===");
        player.sendMessage("§a隊長: §e" + leaderName);
        player.sendMessage("§a隊員數量: §e" + members.size());
        player.sendMessage("§a隊員列表:");

        int index = 1;
        for (UUID memberId : members) {
            Player member = Bukkit.getPlayer(memberId);
            String memberName = member != null ? member.getName() : "未知";
            String status = member != null && member.isOnline() ? "§a在線" : "§c離線";
            String role = memberId.equals(leaderId) ? " §6[隊長]" : "";
            player.sendMessage("§e" + index + ". §f" + memberName + role + " - " + status);
            index++;
        }

        if (playerId.equals(leaderId)) {
            player.sendMessage("");
            player.sendMessage("§6隊長指令:");
            player.sendMessage("§a/raft team invite <玩家> §7- 邀請玩家");
            player.sendMessage("§a/raft team kick <玩家> §7- 踢出隊員");
            player.sendMessage("§a/raft team disband §7- 解散隊伍");
        } else {
            player.sendMessage("");
            player.sendMessage("§6隊員指令:");
            player.sendMessage("§a/raft team leave §7- 離開隊伍");
        }
    }

    public void broadcastToTeam(UUID leaderId, String message) {
        Set<UUID> members = teamMembers.get(leaderId);
        if (members != null) {
            for (UUID memberId : members) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null && member.isOnline()) {
                    member.sendMessage(message);
                }
            }
        }
    }

    public UUID getPlayerTeamLeader(UUID playerId) {
        return playerTeams.get(playerId);
    }

    public Set<UUID> getTeamMembers(UUID leaderId) {
        return teamMembers.getOrDefault(leaderId, new HashSet<>());
    }

    public boolean isTeamLeader(UUID playerId) {
        UUID leaderId = playerTeams.get(playerId);
        return leaderId != null && leaderId.equals(playerId);
    }

    public boolean isInTeam(UUID playerId) {
        return playerTeams.containsKey(playerId);
    }

    /**
     * 獲取團隊數量 (API使用)
     */
    public int getTeamCount() {
        return teamMembers.size();
    }

    /**
     * 清理過期邀請
     */
    public void cleanupExpiredInvites() {
        teamInvites.entrySet().removeIf(entry -> {
            UUID playerId = entry.getKey();
            Player player = Bukkit.getPlayer(playerId);
            return player == null || !player.isOnline();
        });
    }
}
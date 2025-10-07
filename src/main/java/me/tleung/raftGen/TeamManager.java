package me.tleung.raftGen;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class TeamManager {

    private final RaftGen plugin;
    private final HashMap<UUID, UUID> playerTeams; // 玩家ID -> 隊長ID
    private final HashMap<UUID, Set<UUID>> teamMembers; // 隊長ID -> 隊員集合
    private final HashMap<UUID, UUID> teamInvites; // 被邀請玩家ID -> 邀請者隊長ID
    private final HashMap<UUID, Long> inviteExpiry; // 邀請過期時間

    public TeamManager(RaftGen plugin) {
        this.plugin = plugin;
        this.playerTeams = new HashMap<>();
        this.teamMembers = new HashMap<>();
        this.teamInvites = new HashMap<>();
        this.inviteExpiry = new HashMap<>();
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

        // 清除相關邀請
        clearTeamInvites(leaderId);

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

        // 檢查是否已經有邀請
        if (teamInvites.containsKey(target.getUniqueId())) {
            leader.sendMessage("§c該玩家已經有待處理的邀請了!");
            return false;
        }

        teamInvites.put(target.getUniqueId(), leaderId);
        long expiryTime = System.currentTimeMillis() + (60 * 1000); // 60秒後過期
        inviteExpiry.put(target.getUniqueId(), expiryTime);

        leader.sendMessage("§a已向 " + target.getName() + " 發送組隊邀請!");
        target.sendMessage("§6=== 組隊邀請 ===");
        target.sendMessage("§a玩家 " + leader.getName() + " 邀請你加入隊伍!");
        target.sendMessage("§a使用 §e/raft team accept §a接受邀請");
        target.sendMessage("§c使用 §e/raft team deny §c拒絕邀請");
        target.sendMessage("§6邀請將在 60 秒後過期");

        // 設置過期檢查
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (teamInvites.remove(target.getUniqueId()) != null) {
                inviteExpiry.remove(target.getUniqueId());
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

        // 檢查邀請是否過期
        Long expiryTime = inviteExpiry.get(playerId);
        if (expiryTime == null || System.currentTimeMillis() > expiryTime) {
            teamInvites.remove(playerId);
            inviteExpiry.remove(playerId);
            player.sendMessage("§c組隊邀請已過期!");
            return false;
        }

        Player leader = Bukkit.getPlayer(leaderId);
        if (leader == null || !leader.isOnline()) {
            player.sendMessage("§c邀請你的玩家已離線!");
            teamInvites.remove(playerId);
            inviteExpiry.remove(playerId);
            return false;
        }

        // 檢查隊長是否還有隊伍
        if (!teamMembers.containsKey(leaderId)) {
            player.sendMessage("§c邀請你的隊伍已不存在!");
            teamInvites.remove(playerId);
            inviteExpiry.remove(playerId);
            return false;
        }

        teamInvites.remove(playerId);
        inviteExpiry.remove(playerId);
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
        inviteExpiry.remove(playerId);

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

        Player leader = Bukkit.getPlayer(leaderId);
        if (leader != null && leader.isOnline()) {
            leader.sendMessage("§c玩家 " + player.getName() + " 離開了你的隊伍");
        }

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
        return (int) teamMembers.keySet().stream()
                .filter(leaderId -> teamMembers.get(leaderId).size() > 0)
                .count();
    }

    /**
     * 清理過期邀請
     */
    public void cleanupExpiredInvites() {
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> iterator = inviteExpiry.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (currentTime > entry.getValue()) {
                UUID playerId = entry.getKey();
                teamInvites.remove(playerId);
                iterator.remove();

                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    player.sendMessage("§c你的組隊邀請已過期");
                }
            }
        }
    }

    /**
     * 檢查是否有待處理的邀請 (API使用)
     */
    public boolean hasPendingInvite(UUID playerId) {
        Long expiryTime = inviteExpiry.get(playerId);
        if (expiryTime == null) {
            return false;
        }

        if (System.currentTimeMillis() > expiryTime) {
            // 邀請已過期，清除
            teamInvites.remove(playerId);
            inviteExpiry.remove(playerId);
            return false;
        }

        return teamInvites.containsKey(playerId);
    }

    /**
     * 獲取邀請的隊長 (API使用)
     */
    public UUID getInviteLeader(UUID playerId) {
        if (!hasPendingInvite(playerId)) {
            return null;
        }
        return teamInvites.get(playerId);
    }

    /**
     * 取消團隊邀請 (API使用)
     */
    public boolean cancelInvite(UUID playerId) {
        if (!teamInvites.containsKey(playerId)) {
            return false;
        }

        UUID leaderId = teamInvites.remove(playerId);
        inviteExpiry.remove(playerId);

        Player leader = Bukkit.getPlayer(leaderId);
        if (leader != null && leader.isOnline()) {
            leader.sendMessage("§c你對 " + getPlayerName(playerId) + " 的邀請已取消");
        }

        Player target = Bukkit.getPlayer(playerId);
        if (target != null && target.isOnline()) {
            target.sendMessage("§c來自 " + getPlayerName(leaderId) + " 的組隊邀請已被取消");
        }

        return true;
    }

    /**
     * 獲取團隊成員數量 (API使用)
     */
    public int getTeamMemberCount(UUID leaderId) {
        Set<UUID> members = teamMembers.get(leaderId);
        return members != null ? members.size() : 0;
    }

    /**
     * 獲取所有在團隊中的玩家 (數據持久化使用)
     */
    public Set<UUID> getAllPlayersInTeams() {
        return new HashSet<>(playerTeams.keySet());
    }

    /**
     * 加載團隊數據 (數據持久化使用)
     */
    public void loadTeamData(Map<UUID, UUID> teamMembersMap) {
        this.playerTeams.putAll(teamMembersMap);

        // 重建團隊成員映射
        for (Map.Entry<UUID, UUID> entry : teamMembersMap.entrySet()) {
            UUID memberId = entry.getKey();
            UUID leaderId = entry.getValue();

            if (!this.teamMembers.containsKey(leaderId)) {
                this.teamMembers.put(leaderId, new HashSet<>());
            }
            this.teamMembers.get(leaderId).add(memberId);
        }

        plugin.getLogger().info("加載團隊數據: " + teamMembersMap.size() + " 個團隊成員");
    }

    /**
     * 獲取所有團隊數據 (數據持久化使用)
     */
    public Map<UUID, UUID> getAllTeamData() {
        return new HashMap<>(playerTeams);
    }

    /**
     * 清除團隊的所有邀請
     */
    private void clearTeamInvites(UUID leaderId) {
        Iterator<Map.Entry<UUID, UUID>> iterator = teamInvites.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, UUID> entry = iterator.next();
            if (entry.getValue().equals(leaderId)) {
                UUID invitedPlayerId = entry.getKey();
                iterator.remove();
                inviteExpiry.remove(invitedPlayerId);

                Player invitedPlayer = Bukkit.getPlayer(invitedPlayerId);
                if (invitedPlayer != null && invitedPlayer.isOnline()) {
                    invitedPlayer.sendMessage("§c你的組隊邀請已被取消（隊伍已解散）");
                }
            }
        }
    }

    /**
     * 獲取玩家名稱 (輔助方法)
     */
    private String getPlayerName(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            return player.getName();
        }

        // 嘗試從離線玩家獲取
        try {
            String name = Bukkit.getOfflinePlayer(playerId).getName();
            return name != null ? name : "未知玩家";
        } catch (Exception e) {
            return "未知玩家";
        }
    }

    /**
     * 檢查團隊是否有效
     */
    public boolean isTeamValid(UUID leaderId) {
        return teamMembers.containsKey(leaderId) && !teamMembers.get(leaderId).isEmpty();
    }

    /**
     * 獲取團隊信息字符串 (API使用)
     */
    public String getTeamInfoString(UUID leaderId) {
        if (!isTeamValid(leaderId)) {
            return "無效的團隊";
        }

        StringBuilder info = new StringBuilder();
        Set<UUID> members = teamMembers.get(leaderId);

        info.append("§6=== 團隊信息 ===\n");
        info.append("§a隊長: §e").append(getPlayerName(leaderId)).append("\n");
        info.append("§a成員數量: §e").append(members.size()).append("\n");
        info.append("§a成員列表:\n");

        int index = 1;
        for (UUID memberId : members) {
            String memberName = getPlayerName(memberId);
            String role = memberId.equals(leaderId) ? " §6[隊長]" : "";
            String status = Bukkit.getPlayer(memberId) != null ? "§a在線" : "§c離線";
            info.append("§e").append(index).append(". §f").append(memberName).append(role).append(" - ").append(status).append("\n");
            index++;
        }

        return info.toString();
    }

    /**
     * 轉移隊長權限
     */
    public boolean transferLeadership(Player currentLeader, String newLeaderName) {
        UUID currentLeaderId = currentLeader.getUniqueId();

        if (!isTeamLeader(currentLeaderId)) {
            currentLeader.sendMessage("§c只有隊長可以轉移權限!");
            return false;
        }

        Player newLeader = Bukkit.getPlayer(newLeaderName);
        if (newLeader == null) {
            currentLeader.sendMessage("§c找不到玩家: " + newLeaderName);
            return false;
        }

        UUID newLeaderId = newLeader.getUniqueId();
        if (!teamMembers.get(currentLeaderId).contains(newLeaderId)) {
            currentLeader.sendMessage("§c該玩家不在你的隊伍中!");
            return false;
        }

        if (newLeaderId.equals(currentLeaderId)) {
            currentLeader.sendMessage("§c你已經是隊長了!");
            return false;
        }

        // 轉移隊長權限
        teamMembers.get(currentLeaderId).remove(currentLeaderId);
        teamMembers.get(currentLeaderId).add(currentLeaderId); // 重新添加為普通成員

        // 更新隊長
        playerTeams.put(currentLeaderId, newLeaderId);
        playerTeams.put(newLeaderId, newLeaderId);

        // 更新團隊成員映射
        teamMembers.put(newLeaderId, teamMembers.get(currentLeaderId));
        teamMembers.remove(currentLeaderId);

        broadcastToTeam(newLeaderId, "§6隊長權限已轉移給 " + newLeader.getName() + "!");
        currentLeader.sendMessage("§a你已將隊長權限轉移給 " + newLeader.getName());
        newLeader.sendMessage("§a你現在是隊伍的隊長!");

        return true;
    }

    /**
     * 獲取所有團隊列表 (管理用)
     */
    public List<UUID> getAllTeams() {
        return new ArrayList<>(teamMembers.keySet());
    }

    /**
     * 檢查玩家是否有邀請特定玩家
     */
    public boolean hasInvitedPlayer(UUID leaderId, UUID targetPlayerId) {
        return teamInvites.containsKey(targetPlayerId) && teamInvites.get(targetPlayerId).equals(leaderId);
    }

    /**
     * 獲取團隊的所有待處理邀請
     */
    public Set<UUID> getTeamPendingInvites(UUID leaderId) {
        Set<UUID> pendingInvites = new HashSet<>();
        for (Map.Entry<UUID, UUID> entry : teamInvites.entrySet()) {
            if (entry.getValue().equals(leaderId)) {
                pendingInvites.add(entry.getKey());
            }
        }
        return pendingInvites;
    }
}
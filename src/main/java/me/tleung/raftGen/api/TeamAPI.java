package me.tleung.raftGen.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

public interface TeamAPI {

    /**
     * 創建團隊
     * @param leader 隊長
     * @return 是否成功創建
     */
    boolean createTeam(@NotNull Player leader);

    /**
     * 解散團隊
     * @param leader 隊長
     * @return 是否成功解散
     */
    boolean disbandTeam(@NotNull Player leader);

    /**
     * 邀請玩家加入團隊
     * @param leader 隊長
     * @param targetName 目標玩家名稱
     * @return 是否成功邀請
     */
    boolean invitePlayer(@NotNull Player leader, @NotNull String targetName);

    /**
     * 接受團隊邀請
     * @param player 玩家
     * @return 是否成功接受
     */
    boolean acceptInvite(@NotNull Player player);

    /**
     * 拒絕團隊邀請
     * @param player 玩家
     * @return 是否成功拒絕
     */
    boolean denyInvite(@NotNull Player player);

    /**
     * 離開團隊
     * @param player 玩家
     * @return 是否成功離開
     */
    boolean leaveTeam(@NotNull Player player);

    /**
     * 踢出團隊成員
     * @param leader 隊長
     * @param targetName 目標玩家名稱
     * @return 是否成功踢出
     */
    boolean kickPlayer(@NotNull Player leader, @NotNull String targetName);

    /**
     * 獲取玩家的團隊隊長
     * @param playerId 玩家UUID
     * @return 隊長UUID，如果不在團隊中返回null
     */
    @Nullable
    UUID getPlayerTeamLeader(@NotNull UUID playerId);

    /**
     * 獲取團隊成員
     * @param leaderId 隊長UUID
     * @return 團隊成員集合
     */
    @NotNull
    Set<UUID> getTeamMembers(@NotNull UUID leaderId);

    /**
     * 檢查玩家是否是隊長
     * @param playerId 玩家UUID
     * @return 是否是隊長
     */
    boolean isTeamLeader(@NotNull UUID playerId);

    /**
     * 檢查玩家是否在團隊中
     * @param playerId 玩家UUID
     * @return 是否在團隊中
     */
    boolean isInTeam(@NotNull UUID playerId);

    /**
     * 向團隊廣播消息
     * @param leaderId 隊長UUID
     * @param message 消息
     */
    void broadcastToTeam(@NotNull UUID leaderId, @NotNull String message);

    /**
     * 獲取團隊數量
     * @return 團隊數量
     */
    int getTeamCount();

    /**
     * 獲取團隊信息
     * @param player 玩家
     * @return 團隊信息字符串
     */
    @NotNull
    String getTeamInfo(@NotNull Player player);
}
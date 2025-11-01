package me.tleung.raftGen.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

public interface RaftGenAPI {

    /**
     * 獲取插件實例
     */
    @NotNull
    Plugin getPlugin();

    /**
     * 為玩家創建木筏
     * @param player 玩家
     * @param location 指定位置（可選，為null時自動生成）
     * @return 是否成功創建
     */
    boolean createRaft(@NotNull Player player, @Nullable Location location);

    /**
     * 獲取玩家的木筏位置
     * @param playerId 玩家UUID
     * @return 木筏位置，如果沒有木筏則返回null
     */
    @Nullable
    Location getRaftLocation(@NotNull UUID playerId);

    /**
     * 刪除玩家的木筏
     * @param playerId 玩家UUID
     * @return 是否成功刪除
     */
    boolean deleteRaft(@NotNull UUID playerId);

    /**
     * 檢查玩家是否有木筏
     * @param playerId 玩家UUID
     * @return 是否有木筏
     */
    boolean hasRaft(@NotNull UUID playerId);

    /**
     * 獲取木筏世界的實例
     * @return 木筏世界
     */
    @NotNull
    org.bukkit.World getRaftWorld();

    /**
     * 傳送玩家到木筏
     * @param player 玩家
     * @return 是否成功傳送
     */
    boolean teleportToRaft(@NotNull Player player);

    /**
     * 重置玩家的木筏
     * @param player 玩家
     * @return 是否成功重置
     */
    boolean resetRaft(@NotNull Player player);

    /**
     * 獲取木筏名稱
     * @param playerId 玩家UUID
     * @return 木筏名稱
     */
    @Nullable
    String getRaftName(@NotNull UUID playerId);

    /**
     * 設置木筏名稱
     * @param playerId 玩家UUID
     * @param name 新名稱
     * @return 是否成功設置
     */
    boolean setRaftName(@NotNull UUID playerId, @NotNull String name);

    /**
     * 獲取所有木筏數據
     * @return 木筏數據映射
     */
    @NotNull
    Map<UUID, Location> getAllRafts();

    /**
     * 獲取木筏總數量
     * @return 木筏數量
     */
    int getTotalRaftCount();

    /**
     * 獲取團隊API
     * @return 團隊API
     */
    @NotNull
    TeamAPI getTeamAPI();

    /**
     * 獲取插件狀態信息
     * @return 狀態信息
     */
    @NotNull
    String getPluginStatus();

    /**
     * 檢查玩家是否在木筏世界中
     * @param player 玩家
     * @return 是否在木筏世界
     */
    boolean isInRaftWorld(@NotNull Player player);

    /**
     * 獲取玩家的團隊隊長（如果有的話）
     * @param playerId 玩家UUID
     * @return 隊長UUID，如果沒有團隊返回null
     */
    @Nullable
    UUID getPlayerTeamLeader(@NotNull UUID playerId);

    /**
     * 檢查玩家是否是團隊隊長
     * @param playerId 玩家UUID
     * @return 是否是隊長
     */
    boolean isTeamLeader(@NotNull UUID playerId);

    /**
     * 獲取木筏名稱映射
     * @return 名稱映射
     */
    @NotNull
    Map<UUID, String> getAllRaftNames();
}
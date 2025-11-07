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
     * 获取插件实例
     */
    @NotNull
    Plugin getPlugin();

    /**
     * 为玩家创建木筏
     * @param player 玩家
     * @param location 指定位置（可选，为null时自动生成）
     * @return 是否成功创建
     */
    boolean createRaft(@NotNull Player player, @Nullable Location location);

    /**
     * 获取玩家的木筏位置
     * @param playerId 玩家UUID
     * @return 木筏位置，如果没有木筏则返回null
     */
    @Nullable
    Location getRaftLocation(@NotNull UUID playerId);

    /**
     * 删除玩家的木筏
     * @param playerId 玩家UUID
     * @return 是否成功删除
     */
    boolean deleteRaft(@NotNull UUID playerId);

    /**
     * 检查玩家是否有木筏
     * @param playerId 玩家UUID
     * @return 是否有木筏
     */
    boolean hasRaft(@NotNull UUID playerId);

    /**
     * 获取木筏世界的实例
     * @return 木筏世界
     */
    @NotNull
    org.bukkit.World getRaftWorld();

    /**
     * 传送玩家到木筏
     * @param player 玩家
     * @return 是否成功传送
     */
    boolean teleportToRaft(@NotNull Player player);

    /**
     * 重置玩家的木筏
     * @param player 玩家
     * @return 是否成功重置
     */
    boolean resetRaft(@NotNull Player player);

    /**
     * 获取木筏名称
     * @param playerId 玩家UUID
     * @return 木筏名称
     */
    @Nullable
    String getRaftName(@NotNull UUID playerId);

    /**
     * 设置木筏名称
     * @param playerId 玩家UUID
     * @param name 新名称
     * @return 是否成功设置
     */
    boolean setRaftName(@NotNull UUID playerId, @NotNull String name);

    /**
     * 获取所有木筏数据
     * @return 木筏数据映射
     */
    @NotNull
    Map<UUID, Location> getAllRafts();

    /**
     * 获取木筏总数量
     * @return 木筏数量
     */
    int getTotalRaftCount();

    /**
     * 获取团队API
     * @return 团队API
     */
    @NotNull
    TeamAPI getTeamAPI();

    /**
     * 获取插件状态信息
     * @return 状态信息
     */
    @NotNull
    String getPluginStatus();

    /**
     * 检查玩家是否在木筏世界中
     * @param player 玩家
     * @return 是否在木筏世界
     */
    boolean isInRaftWorld(@NotNull Player player);

    /**
     * 获取玩家的团队队长（如果有的话）
     * @param playerId 玩家UUID
     * @return 队长UUID，如果没有团队返回null
     */
    @Nullable
    UUID getPlayerTeamLeader(@NotNull UUID playerId);

    /**
     * 检查玩家是否是团队队长
     * @param playerId 玩家UUID
     * @return 是否是队长
     */
    boolean isTeamLeader(@NotNull UUID playerId);

    /**
     * 获取木筏名称映射
     * @return 名称映射
     */
    @NotNull
    Map<UUID, String> getAllRaftNames();
}
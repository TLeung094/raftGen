package me.tleung.raftGen.api;

import org.bukkit.Location;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

public interface LevelAPI {

    /**
     * 計算位置的價值
     * @param location 位置
     * @param radius 半徑
     * @return 總價值
     */
    double calculateValue(@NotNull Location location, int radius);

    /**
     * 根據價值計算等級
     * @param totalValue 總價值
     * @return 等級
     */
    int calculateLevel(double totalValue);

    /**
     * 獲取升級到下一級所需的價值
     * @param currentLevel 當前等級
     * @return 所需價值
     */
    double getValueForNextLevel(int currentLevel);

    /**
     * 獲取指定等級的價值
     * @param level 等級
     * @return 等級價值
     */
    double getValueForLevel(int level);

    /**
     * 獲取方塊的價值
     * @param material 方塊材料
     * @return 方塊價值
     */
    double getBlockValue(@NotNull Material material);

    /**
     * 設置方塊的價值
     * @param material 方塊材料
     * @param value 價值
     */
    void setBlockValue(@NotNull Material material, double value);

    /**
     * 獲取所有方塊價值映射
     * @return 方塊價值映射
     */
    @NotNull
    Map<Material, Double> getAllBlockValues();

    /**
     * 獲取升級進度百分比
     * @param playerId 玩家UUID
     * @return 進度百分比 (0-100)
     */
    double getLevelProgress(@NotNull UUID playerId);

    /**
     * 獲取距離下一級還需要的價值
     * @param playerId 玩家UUID
     * @return 還需要的價值
     */
    double getValueNeededForNextLevel(@NotNull UUID playerId);

    /**
     * 批量設置方塊價值
     * @param values 方塊價值映射
     */
    void setBlockValues(@NotNull Map<Material, Double> values);

    /**
     * 重置所有方塊價值為默認值
     */
    void resetBlockValues();

    /**
     * 獲取等級上限
     * @return 等級上限
     */
    int getMaxLevel();

    /**
     * 設置等級上限
     * @param maxLevel 等級上限
     */
    void setMaxLevel(int maxLevel);

    /**
     * 獲取等級對應的半徑
     * @param level 等級
     * @return 半徑
     */
    int getRadiusForLevel(int level);

    /**
     * 獲取等級進度條
     * @param playerId 玩家UUID
     * @return 進度條字符串
     */
    @NotNull
    String getProgressBar(@NotNull UUID playerId);
}
package me.tleung.raftGen;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;

public class LevelCalculator {

    private final Map<Material, Double> blockValues;

    public LevelCalculator() {
        this.blockValues = new HashMap<>();
        setupDefaultBlockValues();
    }

    private void setupDefaultBlockValues() {
        // 基礎建築方塊 - 低價值
        blockValues.put(Material.OAK_PLANKS, 1.0);
        blockValues.put(Material.SPRUCE_PLANKS, 1.0);
        blockValues.put(Material.BIRCH_PLANKS, 1.0);
        blockValues.put(Material.COBBLESTONE, 1.5);
        blockValues.put(Material.STONE, 2.0);
        blockValues.put(Material.STONE_BRICKS, 2.5);

        // 裝飾性方塊 - 中等價值
        blockValues.put(Material.GLASS, 3.0);
        blockValues.put(Material.GLASS_PANE, 1.5);
        blockValues.put(Material.TORCH, 1.0);
        blockValues.put(Material.LANTERN, 4.0);
        blockValues.put(Material.FLOWER_POT, 2.0);
        blockValues.put(Material.PAINTING, 2.0);

        // 功能性方塊 - 較高價值
        blockValues.put(Material.CRAFTING_TABLE, 5.0);
        blockValues.put(Material.FURNACE, 6.0);
        blockValues.put(Material.CHEST, 4.0);
        blockValues.put(Material.ENCHANTING_TABLE, 15.0);
        blockValues.put(Material.ANVIL, 12.0);
        blockValues.put(Material.BREWING_STAND, 10.0);

        // 高級材料 - 高價值
        blockValues.put(Material.IRON_BLOCK, 20.0);
        blockValues.put(Material.GOLD_BLOCK, 25.0);
        blockValues.put(Material.DIAMOND_BLOCK, 50.0);
        blockValues.put(Material.EMERALD_BLOCK, 40.0);
        blockValues.put(Material.NETHERITE_BLOCK, 100.0);

        // 稀有裝飾 - 中高價值
        blockValues.put(Material.BOOKSHELF, 8.0);
        blockValues.put(Material.LECTERN, 10.0);
        blockValues.put(Material.LOOM, 6.0);
        blockValues.put(Material.SMITHING_TABLE, 8.0);

        // 負價值方塊（防止刷等級）
        blockValues.put(Material.DIRT, 0.1);
        blockValues.put(Material.GRAVEL, 0.1);
        blockValues.put(Material.SAND, 0.1);
    }

    /**
     * 計算木筏的總價值
     */
    public double calculateRaftValue(Location center, int radius) {
        World world = center.getWorld();
        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();

        double totalValue = 0.0;

        // 掃描木筏區域內的所有方塊
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = centerY - 5; y <= centerY + 10; y++) {
                    Block block = world.getBlockAt(centerX + x, y, centerZ + z);
                    Material material = block.getType();

                    // 只計算非空氣和非水方塊
                    if (material != Material.AIR && material != Material.WATER && material != Material.LAVA) {
                        double blockValue = getBlockValue(material);
                        totalValue += blockValue;
                    }
                }
            }
        }

        return totalValue;
    }

    /**
     * 獲取方塊的基準價值
     */
    public double getBlockValue(Material material) {
        return blockValues.getOrDefault(material, 1.0);
    }

    /**
     * 將總價值轉換為等級
     * 使用對數函數使升級曲線更合理
     */
    public int calculateLevel(double totalValue) {
        if (totalValue <= 0) return 1;

        // 使用對數函數使升級曲線更合理
        // 早期升級快，後期升級慢
        double level = Math.log(totalValue) * 2.5;

        // 確保最小等級為1
        return Math.max(1, (int) Math.floor(level));
    }

    /**
     * 獲取升級到下一級所需的價值
     */
    public double getValueForNextLevel(int currentLevel) {
        // 反向計算：價值 = exp(等級 / 2.5)
        return Math.exp((currentLevel + 1) / 2.5);
    }

    /**
     * 獲取當前等級的價值範圍
     */
    public double getValueForLevel(int level) {
        return Math.exp(level / 2.5);
    }

    /**
     * 設置方塊價值 (API使用)
     */
    public void setBlockValue(Material material, double value) {
        blockValues.put(material, value);
    }

    /**
     * 獲取所有方塊價值映射 (API使用)
     */
    public Map<Material, Double> getAllBlockValues() {
        return new HashMap<>(blockValues);
    }

    /**
     * 清除所有方塊價值並重新設置默認值 (API使用)
     */
    public void resetBlockValues() {
        blockValues.clear();
        setupDefaultBlockValues();
    }

    /**
     * 批量設置方塊價值 (API使用)
     */
    public void setBlockValues(Map<Material, Double> values) {
        blockValues.putAll(values);
    }
}
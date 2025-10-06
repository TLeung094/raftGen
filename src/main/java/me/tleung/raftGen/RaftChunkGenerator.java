package me.tleung.raftGen;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

public class RaftChunkGenerator extends ChunkGenerator {

    private RaftGen plugin;

    public RaftChunkGenerator() {
    }

    public RaftChunkGenerator(RaftGen plugin) {
        this.plugin = plugin;
    }

    public void setPlugin(RaftGen plugin) {
        this.plugin = plugin;
    }

    // 檢查是否在木筏範圍內
    private boolean isInRaftArea(int worldX, int worldZ) {
        int spacing = 200;
        int size = 3;

        for (int i = 0; i < 100; i++) {
            int centerX = i * spacing;
            int centerZ = i * spacing;

            if (worldX >= centerX - 1 && worldX <= centerX + 1 &&
                    worldZ >= centerZ - 1 && worldZ <= centerZ + 1) {
                return true;
            }
        }
        return false;
    }

    // 獲取木筏中心位置
    private int[] getNearestRaftCenter(int worldX, int worldZ) {
        int spacing = 200;
        int minDistance = Integer.MAX_VALUE;
        int[] nearestCenter = new int[]{0, 0};

        for (int i = 0; i < 100; i++) {
            int centerX = i * spacing;
            int centerZ = i * spacing;

            int distance = (int) Math.sqrt(Math.pow(worldX - centerX, 2) + Math.pow(worldZ - centerZ, 2));
            if (distance < minDistance) {
                minDistance = distance;
                nearestCenter[0] = centerX;
                nearestCenter[1] = centerZ;
            }
        }

        return nearestCenter;
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        int seaLevel = 62;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                boolean isRaft = isInRaftArea(worldX, worldZ);

                if (isRaft) {
                    generateRaftAtLocation(chunkData, x, z, worldX, worldZ, seaLevel, random);
                } else {
                    generateOceanAtLocation(chunkData, x, z, worldX, worldZ, seaLevel, random);
                }
            }
        }
    }

    @Override
    public void generateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        // 表面生成邏輯 - 使用溫暖地區的材料
    }

    @Override
    public void generateBedrock(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        // 基岩生成邏輯
    }

    @Override
    public void generateCaves(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        // 洞穴生成邏輯 - 停用洞穴
    }

    private void generateRaftAtLocation(ChunkData chunkData, int x, int z, int worldX, int worldZ, int seaLevel, Random random) {
        generateRaftTerrain(chunkData, x, z, worldX, worldZ, seaLevel, random);
    }

    private void generateRaftTerrain(ChunkData chunkData, int x, int z, int worldX, int worldZ, int seaLevel, Random random) {
        int[] raftCenter = getNearestRaftCenter(worldX, worldZ);
        int centerX = raftCenter[0];
        int centerZ = raftCenter[1];

        int relX = worldX - centerX;
        int relZ = worldZ - centerZ;

        int raftHeight = 62;

        // 基岩
        chunkData.setBlock(x, 0, z, Material.BEDROCK);
        chunkData.setBlock(x, 1, z, Material.BEDROCK);

        // 海底地形 - 使用溫暖地區的材料
        int seaBedHeight = 30;
        for (int y = 2; y < seaBedHeight; y++) {
            if (y < 10) {
                chunkData.setBlock(x, y, z, Material.DEEPSLATE);
            } else {
                chunkData.setBlock(x, y, z, Material.STONE);
            }
        }

        // 沙礫和沙層 - 使用溫暖地區的沙材質（普通沙）
        for (int y = seaBedHeight; y < seaBedHeight + 3; y++) {
            if (random.nextDouble() < 0.7) {
                chunkData.setBlock(x, y, z, Material.GRAVEL);
            } else {
                chunkData.setBlock(x, y, z, Material.SAND);
            }
        }
        chunkData.setBlock(x, seaBedHeight + 3, z, Material.SAND);

        // 海底到木筏之間全部是水
        for (int y = seaBedHeight + 4; y < raftHeight; y++) {
            chunkData.setBlock(x, y, z, Material.WATER);
        }

        // 生成 3x3 木筏 - 使用橡木材質
        if (Math.abs(relX) <= 1 && Math.abs(relZ) <= 1) {
            chunkData.setBlock(x, raftHeight, z, Material.OAK_PLANKS);
        }

        // 木筏上方的空氣
        for (int y = raftHeight + 1; y < chunkData.getMaxHeight(); y++) {
            chunkData.setBlock(x, y, z, Material.AIR);
        }

        // 確保木筏周圍是水
        for (int y = seaLevel; y < raftHeight; y++) {
            if (chunkData.getType(x, y, z) == Material.AIR) {
                chunkData.setBlock(x, y, z, Material.WATER);
            }
        }
    }

    private void generateOceanAtLocation(ChunkData chunkData, int x, int z, int worldX, int worldZ, int seaLevel, Random random) {
        int baseSeaBedHeight = 30;
        int seaBedHeight = baseSeaBedHeight;
        seaBedHeight = Math.max(25, Math.min(35, seaBedHeight));

        // 基岩
        chunkData.setBlock(x, 0, z, Material.BEDROCK);
        chunkData.setBlock(x, 1, z, Material.BEDROCK);

        // 深層石頭
        for (int y = 2; y < 10; y++) {
            chunkData.setBlock(x, y, z, Material.DEEPSLATE);
        }

        // 石頭層
        for (int y = 10; y < seaBedHeight - 2; y++) {
            chunkData.setBlock(x, y, z, Material.STONE);
        }

        // 沙礫層
        for (int y = seaBedHeight - 2; y < seaBedHeight; y++) {
            chunkData.setBlock(x, y, z, Material.GRAVEL);
        }

        // 沙層 - 使用普通沙而不是紅沙或其他寒冷地區材質
        chunkData.setBlock(x, seaBedHeight, z, Material.SAND);

        // 水
        for (int y = seaBedHeight + 1; y <= seaLevel; y++) {
            chunkData.setBlock(x, y, z, Material.WATER);
        }

        // 空氣
        for (int y = seaLevel + 1; y < chunkData.getMaxHeight(); y++) {
            chunkData.setBlock(x, y, z, Material.AIR);
        }
    }

    @Override
    public boolean shouldGenerateNoise() {
        return true;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return true;
    }

    @Override
    public boolean shouldGenerateBedrock() {
        return true;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }
}
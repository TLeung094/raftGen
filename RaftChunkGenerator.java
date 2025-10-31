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

    // 改进的平滑Perlin噪声函数
    private double getSmoothPerlinNoise(int x, int z, double frequency, long seed) {
        // 使用网格化的方法创造更平滑的噪声
        double xf = x * frequency;
        double zf = z * frequency;

        // 网格点
        int x0 = (int) Math.floor(xf);
        int x1 = x0 + 1;
        int z0 = (int) Math.floor(zf);
        int z1 = z0 + 1;

        // 小数部分
        double xs = xf - x0;
        double zs = zf - z0;

        // 使用五次多项式曲线进行更平滑的插值
        double fadeX = xs * xs * xs * (xs * (xs * 6 - 15) + 10);
        double fadeZ = zs * zs * zs * (zs * (zs * 6 - 15) + 10);

        // 网格点上的随机梯度
        Random rand00 = new Random(seed + x0 * 1619L + z0 * 31337L);
        double grad00 = rand00.nextDouble() * 2 - 1;

        Random rand10 = new Random(seed + x1 * 1619L + z0 * 31337L);
        double grad10 = rand10.nextDouble() * 2 - 1;

        Random rand01 = new Random(seed + x0 * 1619L + z1 * 31337L);
        double grad01 = rand01.nextDouble() * 2 - 1;

        Random rand11 = new Random(seed + x1 * 1619L + z1 * 31337L);
        double grad11 = rand11.nextDouble() * 2 - 1;

        // 双线性插值
        double n00 = grad00 * (xs);
        double n10 = grad10 * (xs - 1);
        double n01 = grad01 * (xs);
        double n11 = grad11 * (xs - 1);

        double nx0 = n00 + fadeX * (n10 - n00);
        double nx1 = n01 + fadeX * (n11 - n01);

        return nx0 + fadeZ * (nx1 - nx0);
    }

    // 平滑的分形布朗运动
    private double getSmoothFBM(int x, int z, double frequency, int octaves, double lacunarity, double gain, long seed) {
        double value = 0.0;
        double amplitude = 1.0;
        double maxAmplitude = 0.0;

        for (int i = 0; i < octaves; i++) {
            double noise = getSmoothPerlinNoise(x, z, frequency, seed + i * 1000);
            value += noise * amplitude;
            maxAmplitude += amplitude;
            frequency *= lacunarity;
            amplitude *= gain;
        }

        // 确保值在合理范围内
        return Math.max(-1.0, Math.min(1.0, value / maxAmplitude));
    }

    // 超平滑的大陆噪声 - 使用非常低的频率和高的octaves
    private double getSmoothContinentNoise(int x, int z, long seed) {
        // 使用非常低的频率创造平滑的大陆级别变化
        double continent1 = getSmoothFBM(x, z, 0.00002, 12, 1.8, 0.7, seed + 10000) * 50;
        double continent2 = getSmoothFBM(x, z, 0.00005, 8, 2.0, 0.6, seed + 11000) * 30;
        double continent3 = getSmoothFBM(x, z, 0.0001, 6, 2.2, 0.5, seed + 12000) * 20;

        return (continent1 + continent2 + continent3) / 3.0;
    }

    // 平滑的区域基础高度
    private int getSmoothRegionalBaseHeight(int x, int z, long seed) {
        double continentNoise = getSmoothContinentNoise(x, z, seed);

        // 使用平滑的映射函数
        double normalizedNoise = (continentNoise + 50) / 100.0; // 映射到 0-1
        normalizedNoise = Math.max(0.0, Math.min(1.0, normalizedNoise));

        // 使用平滑曲线映射到高度范围
        double heightFactor = normalizedNoise * normalizedNoise; // 平方曲线创造更自然的分布
        int baseHeight = 10 + (int)(heightFactor * 40); // 10-50的范围

        return baseHeight;
    }

    // 平滑的局部地形噪声
    private double getSmoothLocalTerrainNoise(int x, int z, long seed) {
        // 使用中等频率创造平滑的局部地形
        double terrain1 = getSmoothFBM(x, z, 0.0005, 8, 1.9, 0.65, seed + 20000) * 25;
        double terrain2 = getSmoothFBM(x, z, 0.001, 6, 2.1, 0.6, seed + 21000) * 15;
        double terrain3 = getSmoothFBM(x, z, 0.002, 4, 2.3, 0.55, seed + 22000) * 10;

        return (terrain1 + terrain2 + terrain3) / 3.0;
    }

    // 平滑的细节噪声
    private double getSmoothDetailNoise(int x, int z, long seed) {
        // 使用较高频率添加平滑细节
        double detail1 = getSmoothFBM(x, z, 0.01, 4, 2.0, 0.5, seed + 30000) * 6;
        double detail2 = getSmoothFBM(x, z, 0.02, 3, 2.2, 0.6, seed + 31000) * 4;
        double detail3 = getSmoothFBM(x, z, 0.05, 2, 2.5, 0.7, seed + 32000) * 2;

        return (detail1 + detail2 + detail3) / 3.0;
    }

    // 平滑的山脉噪声
    private double getSmoothMountainNoise(int x, int z, long seed) {
        double mountain = getSmoothFBM(x, z, 0.001, 6, 2.0, 0.6, seed + 40000) * 20;
        // 使用平滑的山脊函数
        double ridge = 1.0 - Math.abs(getSmoothFBM(x, z, 0.002, 5, 2.1, 0.55, seed + 41000)) * 15;
        return Math.max(mountain, ridge) * 0.7; // 降低强度
    }

    // 平滑的峡谷噪声
    private double getSmoothCanyonNoise(int x, int z, long seed) {
        double canyon = getSmoothFBM(x, z, 0.0008, 5, 2.0, 0.6, seed + 50000) * 15;
        // 使用平滑的绝对值函数
        return -Math.abs(canyon) * 1.2; // 降低强度
    }

    // 平滑的地形选择器
    private double getSmoothTerrainSelector(int x, int z, long seed) {
        return getSmoothFBM(x, z, 0.0003, 6, 2.0, 0.6, seed + 60000);
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        int seaLevel = 62;
        long worldSeed = worldInfo.getSeed();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                boolean isRaft = isInRaftArea(worldX, worldZ);

                if (isRaft) {
                    generateRaftAtLocation(chunkData, x, z, worldX, worldZ, seaLevel, random);
                } else {
                    generateSmoothOceanTerrain(chunkData, x, z, worldX, worldZ, seaLevel, random, worldSeed);
                }
            }
        }
    }

    @Override
    public void generateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long worldSeed = worldInfo.getSeed();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                if (!isInRaftArea(worldX, worldZ)) {
                    generateSeaBedDetails(chunkData, x, z, worldX, worldZ, random, worldSeed);
                    generateSmoothOceanFeatures(chunkData, x, z, worldX, worldZ, random, worldSeed);
                }
            }
        }
    }

    @Override
    public void generateBedrock(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                chunkData.setBlock(x, 0, z, Material.BEDROCK);
                if (random.nextDouble() < 0.7) {
                    chunkData.setBlock(x, 1, z, Material.BEDROCK);
                }
                if (random.nextDouble() < 0.3) {
                    chunkData.setBlock(x, 2, z, Material.BEDROCK);
                }
            }
        }
    }

    @Override
    public void generateCaves(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        // 完全移除洞穴生成
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
        int seaBedHeight = 30;

        // 基岩
        chunkData.setBlock(x, 0, z, Material.BEDROCK);
        chunkData.setBlock(x, 1, z, Material.BEDROCK);

        // 石頭層
        for (int y = 2; y < seaBedHeight; y++) {
            if (y < 10) {
                chunkData.setBlock(x, y, z, Material.DEEPSLATE);
            } else {
                chunkData.setBlock(x, y, z, Material.STONE);
            }
        }

        // 沙層
        chunkData.setBlock(x, seaBedHeight, z, Material.SAND);

        // 水層
        for (int y = seaBedHeight + 1; y < raftHeight; y++) {
            chunkData.setBlock(x, y, z, Material.WATER);
        }

        // 木筏
        if (Math.abs(relX) <= 1 && Math.abs(relZ) <= 1) {
            chunkData.setBlock(x, raftHeight, z, Material.OAK_PLANKS);
        }

        // 空氣
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

    // 平滑的海洋地形生成
    private void generateSmoothOceanTerrain(ChunkData chunkData, int x, int z, int worldX, int worldZ, int seaLevel, Random random, long worldSeed) {
        // 获取平滑的区域基础高度
        int regionalBaseHeight = getSmoothRegionalBaseHeight(worldX, worldZ, worldSeed);

        // 获取平滑的局部地形变化
        double localTerrain = getSmoothLocalTerrainNoise(worldX, worldZ, worldSeed + 70000);

        // 获取平滑的细节变化
        double detail = getSmoothDetailNoise(worldX, worldZ, worldSeed + 80000);

        // 获取平滑的特殊地形特征
        double mountain = getSmoothMountainNoise(worldX, worldZ, worldSeed + 90000);
        double canyon = getSmoothCanyonNoise(worldX, worldZ, worldSeed + 100000);

        // 获取平滑的地形选择器
        double terrainSelector = getSmoothTerrainSelector(worldX, worldZ, worldSeed + 110000);

        // 使用平滑的混合函数计算最终高度
        double finalHeight;

        // 使用平滑的过渡函数
        double mountainWeight = smoothStep(Math.max(0, (terrainSelector - 0.2) / 0.6));
        double canyonWeight = smoothStep(Math.max(0, (-terrainSelector - 0.2) / 0.6));
        double plainWeight = 1.0 - mountainWeight - canyonWeight;

        finalHeight = regionalBaseHeight +
                localTerrain * 0.4 +
                detail * 0.2 +
                mountain * mountainWeight * 0.4 +
                canyon * canyonWeight * 0.4;

        int seaBedHeight = (int) finalHeight;

        // 确保高度在合理范围内
        seaBedHeight = Math.max(-15, Math.min(55, seaBedHeight));

        // 生成地形层
        generateSmoothTerrainLayers(chunkData, x, z, worldX, worldZ, seaBedHeight, seaLevel, random, worldSeed);
    }

    // 平滑的步进函数
    private double smoothStep(double x) {
        return x * x * (3 - 2 * x);
    }

    // 平滑的地形层生成
    private void generateSmoothTerrainLayers(ChunkData chunkData, int x, int z, int worldX, int worldZ, int seaBedHeight, int seaLevel, Random random, long worldSeed) {
        // 基岩
        chunkData.setBlock(x, 0, z, Material.BEDROCK);
        chunkData.setBlock(x, 1, z, Material.BEDROCK);

        // 深層石頭 - 使用平滑的过渡
        for (int y = 2; y < Math.min(10, seaBedHeight); y++) {
            Material deepMaterial = getSmoothDeepMaterial(worldX, y, worldZ, seaBedHeight, random, worldSeed);
            chunkData.setBlock(x, y, z, deepMaterial);
        }

        // 石頭層 - 平滑的材料过渡
        for (int y = 10; y < seaBedHeight; y++) {
            Material layerMaterial = getSmoothLayerMaterial(worldX, y, worldZ, seaBedHeight, random, worldSeed);
            chunkData.setBlock(x, y, z, layerMaterial);
        }

        // 表面層 - 平滑的表面材料
        Material topMaterial = getSmoothSurfaceMaterial(worldX, worldZ, seaBedHeight, random, worldSeed);
        chunkData.setBlock(x, seaBedHeight, z, topMaterial);

        // 水 - 平滑的水面过渡
        for (int y = seaBedHeight + 1; y <= seaLevel; y++) {
            chunkData.setBlock(x, y, z, Material.WATER);
        }

        // 空氣
        for (int y = seaLevel + 1; y < chunkData.getMaxHeight(); y++) {
            chunkData.setBlock(x, y, z, Material.AIR);
        }
    }

    // 平滑的深層材料
    private Material getSmoothDeepMaterial(int worldX, int y, int worldZ, int seaBedHeight, Random random, long worldSeed) {
        double noise = getSmoothPerlinNoise(worldX, y, 0.05, worldSeed + 120000);

        // 使用平滑的概率分布
        if (seaBedHeight < 15) {
            // 深海区域
            if (noise > 0.3 && random.nextDouble() < smoothProbability(noise, 0.3, 0.12)) {
                return Material.DEEPSLATE_DIAMOND_ORE;
            } else if (noise < -0.3 && random.nextDouble() < smoothProbability(noise, -0.3, 0.18)) {
                return Material.DEEPSLATE_IRON_ORE;
            }
        }

        // 基础材料
        return Material.DEEPSLATE;
    }

    // 平滑的概率函数
    private double smoothProbability(double noise, double threshold, double baseProbability) {
        double distance = Math.abs(noise - threshold);
        return baseProbability * Math.max(0, 1 - distance * 2);
    }

    // 平滑的地層材料
    private Material getSmoothLayerMaterial(int worldX, int y, int worldZ, int seaBedHeight, Random random, long worldSeed) {
        double noise = getSmoothPerlinNoise(worldX, y, 0.03, worldSeed + 130000);

        // 根据海底高度平滑调整材料分布
        if (seaBedHeight > 40) {
            // 高山区域 - 平滑的岩石分布
            if (noise > 0.2 && random.nextDouble() < smoothProbability(noise, 0.2, 0.3)) {
                return Material.ANDESITE;
            }
        } else if (seaBedHeight < 15) {
            // 深海区域 - 平滑的沉积岩分布
            if (noise > 0.1 && random.nextDouble() < smoothProbability(noise, 0.1, 0.25)) {
                return Material.TUFF;
            }
        }

        // 平滑的矿石分布
        if (random.nextDouble() < 0.04) {
            return Material.COAL_ORE;
        } else if (random.nextDouble() < 0.02 && y > 15) {
            return Material.IRON_ORE;
        }

        return Material.STONE;
    }

    // 平滑的表面材料
    private Material getSmoothSurfaceMaterial(int worldX, int worldZ, int seaBedHeight, Random random, long worldSeed) {
        double noise = getSmoothPerlinNoise(worldX, worldZ, 0.01, worldSeed + 140000);

        // 根据高度平滑过渡表面材料
        if (seaBedHeight > 45) {
            return Material.STONE;
        } else if (seaBedHeight > 35) {
            // 平滑的沙砾到沙的过渡
            double gravelProbability = (seaBedHeight - 35) / 10.0;
            return random.nextDouble() < gravelProbability ? Material.GRAVEL : Material.SAND;
        } else if (seaBedHeight > 20) {
            return Material.SAND;
        } else if (seaBedHeight > 10) {
            // 平滑的沙到粘土的过渡
            double clayProbability = (20 - seaBedHeight) / 10.0;
            return random.nextDouble() < clayProbability ? Material.CLAY : Material.SAND;
        } else {
            return Material.CLAY;
        }
    }

    // 平滑的海洋特徵生成
    private void generateSmoothOceanFeatures(ChunkData chunkData, int x, int z, int worldX, int worldZ, Random random, long worldSeed) {
        int seaLevel = 62;

        // 找到海底表面
        int surfaceY = -1;
        for (int y = seaLevel - 1; y >= 5; y--) {
            Material blockType = chunkData.getType(x, y, z);
            if (blockType == Material.SAND || blockType == Material.GRAVEL || blockType == Material.CLAY || blockType == Material.STONE) {
                surfaceY = y;
                break;
            }
        }

        if (surfaceY == -1 || surfaceY >= seaLevel - 1) return;

        // 使用平滑的噪声决定特征类型
        double featureNoise = getSmoothPerlinNoise(worldX, worldZ, 0.003, worldSeed + 150000);

        // 平滑的特征生成概率
        if (featureNoise > 0.3 && random.nextDouble() < smoothProbability(featureNoise, 0.3, 0.3)) {
            // 生成平滑的山脉
            int mountainHeight = 3 + random.nextInt(12);
            for (int i = 1; i <= mountainHeight; i++) {
                if (surfaceY + i < seaLevel - 1) {
                    Material material = getSmoothMountainMaterial(surfaceY + i, random);
                    chunkData.setBlock(x, surfaceY + i, z, material);
                }
            }
        } else if (featureNoise < -0.3 && random.nextDouble() < smoothProbability(featureNoise, -0.3, 0.25)) {
            // 生成平滑的峡谷
            int canyonDepth = 3 + random.nextInt(15);
            for (int i = 1; i <= canyonDepth; i++) {
                if (surfaceY - i >= 5) {
                    chunkData.setBlock(x, surfaceY - i, z, Material.WATER);
                }
            }
        }
    }

    // 平滑的山脉材料
    private Material getSmoothMountainMaterial(int height, Random random) {
        // 平滑的材料过渡
        if (height > 40 && random.nextDouble() < 0.3) {
            return Material.ANDESITE;
        } else if (random.nextDouble() < 0.1) {
            return Material.GRAVEL;
        } else {
            return Material.STONE;
        }
    }

    // 以下方法保持不变...
    private void generateSeaBedDetails(ChunkData chunkData, int x, int z, int worldX, int worldZ, Random random, long worldSeed) {
        int seaLevel = 62;
        int seaBedY = -1;
        for (int y = seaLevel - 1; y >= 5; y--) {
            Material blockType = chunkData.getType(x, y, z);
            if (blockType == Material.SAND || blockType == Material.GRAVEL || blockType == Material.CLAY || blockType == Material.STONE) {
                seaBedY = y;
                break;
            }
        }

        if (seaBedY == -1 || seaBedY >= seaLevel - 1) return;

        double slope = calculateSmoothSlope(chunkData, x, z, seaBedY);

        if (slope < 0.2) {
            if (random.nextDouble() < 0.7) {
                generateWarmOceanFeatures(chunkData, x, z, seaBedY, random, worldSeed);
            } else {
                generateNormalOceanFeatures(chunkData, x, z, seaBedY, random, worldSeed);
            }
        } else if (slope < 0.5) {
            if (random.nextDouble() < 0.4) {
                generateNormalOceanFeatures(chunkData, x, z, seaBedY, random, worldSeed);
            }
        }
    }

    // 平滑的坡度计算
    private double calculateSmoothSlope(ChunkData chunkData, int x, int z, int centerY) {
        double totalSlope = 0.0;
        int samples = 0;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;

                int checkX = Math.max(0, Math.min(15, x + dx));
                int checkZ = Math.max(0, Math.min(15, z + dz));

                // 查找周围区块的表面高度
                int neighborY = -1;
                for (int y = centerY + 10; y >= centerY - 10; y--) {
                    Material blockType = chunkData.getType(checkX, y, checkZ);
                    if (blockType == Material.SAND || blockType == Material.GRAVEL || blockType == Material.CLAY || blockType == Material.STONE) {
                        neighborY = y;
                        break;
                    }
                }

                if (neighborY != -1) {
                    double slope = Math.abs(neighborY - centerY) / Math.sqrt(dx * dx + dz * dz);
                    totalSlope += slope;
                    samples++;
                }
            }
        }

        if (samples == 0) return 0;
        return totalSlope / samples / 3.0; // 标准化
    }

    private void generateWarmOceanFeatures(ChunkData chunkData, int x, int z, int seaBedY, Random random, long worldSeed) {
        // 保持原有实现，但使用平滑的概率
        if (random.nextDouble() < 0.15) {
            Material[] coralBlocks = {
                    Material.TUBE_CORAL_BLOCK,
                    Material.BRAIN_CORAL_BLOCK,
                    Material.BUBBLE_CORAL_BLOCK,
                    Material.FIRE_CORAL_BLOCK,
                    Material.HORN_CORAL_BLOCK
            };
            Material coralBlock = coralBlocks[random.nextInt(coralBlocks.length)];
            chunkData.setBlock(x, seaBedY, z, coralBlock);
        }

        if (random.nextDouble() < 0.4 && seaBedY < 58) {
            int height = 1 + random.nextInt(2);
            for (int i = 1; i <= height; i++) {
                if (seaBedY + i < 62) {
                    chunkData.setBlock(x, seaBedY + i, z, Material.SEAGRASS);
                }
            }
        }
    }

    private void generateNormalOceanFeatures(ChunkData chunkData, int x, int z, int seaBedY, Random random, long worldSeed) {
        if (random.nextDouble() < 0.25) {
            int height = 1 + random.nextInt(2);
            for (int i = 1; i <= height; i++) {
                if (seaBedY + i < 62) {
                    chunkData.setBlock(x, seaBedY + i, z, Material.SEAGRASS);
                }
            }
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
        return true;
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
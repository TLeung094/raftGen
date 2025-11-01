package me.tleung.raftGen;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class MarineLifeManager {
    private final RaftGen plugin;
    private RaftManager raftManager;
    private final Map<UUID, Set<Location>> activeMarineLife;
    private final Random random;
    private boolean enabled = false;
    private boolean initialized = false;

    public MarineLifeManager(RaftGen plugin) {
        this.plugin = plugin;
        this.raftManager = plugin.getRaftManager();
        this.activeMarineLife = new HashMap<>();
        this.random = new Random();

        // 延迟启动，确保 raftManager 已完全初始化
        Bukkit.getScheduler().runTaskLater(plugin, this::delayedStart, 40L); // 延迟2秒启动
    }

    /**
     * 延迟启动海洋生物生成任务
     */
    private void delayedStart() {
        if (raftManager == null) {
            plugin.getLogger().warning("RaftManager 未就绪，尝试重新获取...");
            this.raftManager = plugin.getRaftManager();
            if (raftManager == null) {
                plugin.getLogger().severe("无法获取 RaftManager，海洋生物系统启动失败");
                return;
            }
        }

        World raftWorld = getRaftWorldSafely();
        if (raftWorld == null) {
            plugin.getLogger().warning("木筏世界未就绪，海洋生物系统无法启动");
            // 尝试在下次检查时启动
            Bukkit.getScheduler().runTaskLater(plugin, this::delayedStart, 100L); // 5秒后重试
            return;
        }

        this.enabled = true;
        this.initialized = true;
        startMarineLifeSpawning();
        plugin.getLogger().info("海洋生物系统已成功启动");
    }

    /**
     * 安全获取木筏世界
     */
    private World getRaftWorldSafely() {
        if (raftManager == null) {
            plugin.getLogger().warning("RaftManager 为 null，尝试重新获取");
            this.raftManager = plugin.getRaftManager();
            if (raftManager == null) {
                return null;
            }
        }

        World world = raftManager.getRaftWorld();
        if (world == null) {
            plugin.getLogger().warning("木筏世界为 null，尝试重新初始化");
            raftManager.initializeRaftWorld();
            world = raftManager.getRaftWorld();
        }

        return world;
    }

    /**
     * 启动海洋生物生成任务 - 调整为更自然的频率
     */
    private void startMarineLifeSpawning() {
        if (!enabled) {
            plugin.getLogger().warning("海洋生物系统未启用，无法启动生成任务");
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!enabled) {
                    this.cancel();
                    plugin.getLogger().info("海洋生物生成任务已停止");
                    return;
                }
                try {
                    spawnMarineLifeNearPlayers();
                    cleanupFarMarineLife();

                    // 每10次执行一次调试日志，减少日志输出
                    if (System.currentTimeMillis() % 10 == 0) {
                        int count = getActiveMarineLifeCount();
                        if (count > 0) {
                            plugin.getLogger().info("海洋生物系统运行中 - 活跃生物: " + count);
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("海洋生物生成任务出错: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskTimer(plugin, 100L, 300L); // 5秒后开始，每15秒执行一次（减少频率）

        plugin.getLogger().info("海洋生物生成任务已启动 - 间隔: 15秒");
    }

    /**
     * 在玩家附近生成海洋生物 - 减少生成密度
     */
    private void spawnMarineLifeNearPlayers() {
        if (!enabled) {
            return;
        }

        World raftWorld = getRaftWorldSafely();
        if (raftWorld == null) {
            plugin.getLogger().warning("木筏世界为null，无法生成海洋生物");
            return;
        }

        List<Player> players = raftWorld.getPlayers();
        if (players.isEmpty()) {
            return; // 没有玩家在线，跳过生成
        }

        for (Player player : players) {
            // 放宽条件：玩家只需要在水附近，不一定完全在水中
            Location playerLoc = player.getLocation();
            if (isNearWater(playerLoc, 20)) {
                // 减少生成数量和范围，使其更自然
                spawnMarineLifeAround(playerLoc, 30, 3); // 减少最大数量，增加范围
            }
        }
    }

    /**
     * 检查位置是否在水附近
     */
    private boolean isNearWater(Location location, int radius) {
        World world = location.getWorld();
        if (world == null) return false;

        int centerX = location.getBlockX();
        int centerY = location.getBlockY();
        int centerZ = location.getBlockZ();

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                for (int y = centerY - 5; y <= centerY + 5; y++) {
                    if (world.getBlockAt(x, y, z).getType() == Material.WATER) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 在位置周围生成海洋生物 - 减少密度，增加分散度
     */
    public void spawnMarineLifeAround(Location center, int radius, int maxSpawns) {
        if (!enabled) {
            plugin.getLogger().warning("海洋生物系统未启用，无法生成海洋生物");
            return;
        }

        World world = center.getWorld();
        if (world == null) return;

        int spawnCount = 0;
        int attempts = 0;
        int maxAttempts = maxSpawns * 3; // 减少尝试次数

        // 检查当前区域已有的海洋生物数量，避免过度生成
        int existingMarineLife = countNearbyMarineLife(center, radius);
        if (existingMarineLife >= maxSpawns * 2) {
            plugin.getLogger().info("区域已有 " + existingMarineLife + " 个海洋生物，跳过生成");
            return;
        }

        while (spawnCount < maxSpawns && attempts < maxAttempts) {
            attempts++;

            // 增加分散度，使用更大的随机范围
            int dx = random.nextInt(radius * 2) - radius;
            int dz = random.nextInt(radius * 2) - radius;

            // 增加位置随机性，避免过于集中
            if (Math.abs(dx) < 5 && Math.abs(dz) < 5) {
                dx = random.nextInt(radius) - radius/2;
                dz = random.nextInt(radius) - radius/2;
            }

            Location spawnLoc = center.clone().add(dx, 0, dz);
            spawnLoc.setY(findWaterSurface(world, spawnLoc.getBlockX(), spawnLoc.getBlockZ()));

            if (isValidMarineLifeLocation(spawnLoc)) {
                // 检查生成位置附近是否已有同类生物，避免过于密集
                if (!isTooCloseToOtherMarineLife(spawnLoc, 8)) {
                    if (spawnMarineLife(spawnLoc)) {
                        spawnCount++;
                    }
                }
            }
        }

        if (spawnCount > 0) {
            plugin.getLogger().info("在位置 " + center + " 周围生成了 " + spawnCount + " 个海洋生物");
        }
    }

    /**
     * 计算附近海洋生物的数量
     */
    private int countNearbyMarineLife(Location center, int radius) {
        World world = center.getWorld();
        if (world == null) return 0;

        int count = 0;
        for (EntityType marineType : getAvailableMarineTypes()) {
            count += world.getNearbyEntities(center, radius, radius, radius).stream()
                    .filter(entity -> entity.getType() == marineType)
                    .mapToInt(e -> 1)
                    .sum();
        }
        return count;
    }

    /**
     * 检查是否太靠近其他海洋生物
     */
    private boolean isTooCloseToOtherMarineLife(Location location, double minDistance) {
        World world = location.getWorld();
        if (world == null) return false;

        for (EntityType marineType : getAvailableMarineTypes()) {
            boolean tooClose = world.getNearbyEntities(location, minDistance, minDistance, minDistance).stream()
                    .anyMatch(entity -> entity.getType() == marineType);
            if (tooClose) {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成单个海洋生物
     */
    private boolean spawnMarineLife(Location location) {
        if (!isValidMarineLifeLocation(location)) {
            return false;
        }

        EntityType marineType = getRandomMarineType(location);
        if (marineType == null) {
            return false;
        }

        try {
            location.getWorld().spawnEntity(location, marineType);

            // 记录生成的生物
            UUID worldId = location.getWorld().getUID();
            activeMarineLife.computeIfAbsent(worldId, k -> new HashSet<>()).add(location);

            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("生成海洋生物时出错: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取随机的海洋生物类型 - 调整为更自然的分布
     */
    private EntityType getRandomMarineType(Location location) {
        double depth = location.getY();
        double rand = random.nextDouble();

        // 根据深度调整生成概率，使其更接近原版 Minecraft
        if (depth > 50) {
            // 浅水区域：鱼类为主，但减少热带鱼概率
            if (rand < 0.5) return EntityType.COD;        // 50% 鳕鱼
            if (rand < 0.7) return EntityType.SALMON;     // 20% 鲑鱼
            if (rand < 0.85) return EntityType.PUFFERFISH; // 15% 河豚
            return EntityType.TROPICAL_FISH;              // 15% 热带鱼
        } else if (depth > 35) {
            // 中等深度：鱿鱼和鱼类混合
            if (rand < 0.3) return EntityType.SQUID;      // 30% 鱿鱼
            if (rand < 0.5) return EntityType.COD;        // 20% 鳕鱼
            if (rand < 0.7) return EntityType.SALMON;     // 20% 鲑鱼
            if (rand < 0.85) return EntityType.TROPICAL_FISH; // 15% 热带鱼
            return EntityType.PUFFERFISH;                 // 15% 河豚
        } else {
            // 深水区域：发光鱿鱼和鱿鱼为主
            if (rand < 0.4) return EntityType.SQUID;      // 40% 鱿鱼
            if (rand < 0.7) return EntityType.GLOW_SQUID; // 30% 发光鱿鱼
            if (rand < 0.85) return EntityType.DOLPHIN;   // 15% 海豚
            return EntityType.COD;                        // 15% 鳕鱼
        }
    }

    /**
     * 检查位置是否适合生成海洋生物 - 放宽条件
     */
    private boolean isValidMarineLifeLocation(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }

        // 检查当前位置是否是水
        Material currentMaterial = location.getBlock().getType();
        if (currentMaterial != Material.WATER) {
            return false;
        }

        // 放宽检查：只检查上方一格是否有足够空间
        Location aboveLoc = location.clone().add(0, 1, 0);
        Material aboveMaterial = aboveLoc.getBlock().getType();

        // 允许上方是水或空气（生物可以游到水面）
        if (aboveMaterial != Material.WATER && aboveMaterial != Material.AIR) {
            return false;
        }

        // 检查下方是否有固体方块（海底）
        Location belowLoc = location.clone().subtract(0, 1, 0);
        Material belowMaterial = belowLoc.getBlock().getType();

        return belowMaterial.isSolid() || belowMaterial == Material.WATER;
    }

    /**
     * 寻找水面位置 - 改进版本
     */
    private int findWaterSurface(World world, int x, int z) {
        // 从海平面开始向下寻找合适的水中位置
        for (int y = 62; y >= 10; y--) {
            Material current = world.getBlockAt(x, y, z).getType();
            Material above = world.getBlockAt(x, y + 1, z).getType();

            // 找到水中位置，且上方是水或空气
            if (current == Material.WATER && (above == Material.WATER || above == Material.AIR)) {
                return y;
            }
        }
        return 55; // 默认返回中等深度
    }

    /**
     * 检查玩家是否在水中
     */
    private boolean isInWater(Player player) {
        return player.getLocation().getBlock().getType() == Material.WATER ||
                player.getEyeLocation().getBlock().getType() == Material.WATER;
    }

    /**
     * 清理远离玩家的海洋生物
     */
    private void cleanupFarMarineLife() {
        // 这里可以添加逻辑来清理距离玩家太远的海洋生物
        // 以优化性能
        long currentTime = System.currentTimeMillis();

        // 简单的清理：每5分钟清理一次过期的记录
        if (currentTime % 300000 < 1000) { // 大约每5分钟执行一次
            activeMarineLife.clear();
        }
    }

    /**
     * 在木筏周围生成海洋生物（创建木筏时调用）- 减少密度
     */
    public void spawnMarineLifeAroundRaft(Location raftCenter) {
        if (!enabled) {
            plugin.getLogger().info("海洋生物系统未启用，尝试自动启用...");
            delayedStart();

            // 如果启用成功，延迟生成海洋生物
            if (enabled) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    spawnMarineLifeAround(raftCenter, 20, 4); // 减少生成数量
                    plugin.getLogger().info("海洋生物已在木筏周围生成");
                }, 40L); // 延迟2秒
            } else {
                plugin.getLogger().warning("无法启用海洋生物系统，跳过生成");
            }
            return;
        }

        // 系统已启用，正常生成
        spawnMarineLifeAround(raftCenter, 20, 4); // 减少生成数量
        plugin.getLogger().info("海洋生物已在木筏周围生成");
    }

    /**
     * 获取活跃的海洋生物数量
     */
    public int getActiveMarineLifeCount() {
        if (!enabled) return 0;

        int count = activeMarineLife.values().stream()
                .mapToInt(Set::size)
                .sum();

        // 如果记录为空，返回一个估计值
        if (count == 0) {
            World raftWorld = getRaftWorldSafely();
            if (raftWorld != null) {
                // 估算世界中的海洋生物数量
                return raftWorld.getEntities().stream()
                        .filter(entity -> isMarineEntity(entity.getType()))
                        .mapToInt(e -> 1)
                        .sum();
            }
        }

        return count;
    }

    /**
     * 检查实体类型是否为海洋生物
     */
    public boolean isMarineEntity(EntityType type) {
        return type == EntityType.COD ||
                type == EntityType.SALMON ||
                type == EntityType.PUFFERFISH ||
                type == EntityType.TROPICAL_FISH ||
                type == EntityType.SQUID ||
                type == EntityType.GLOW_SQUID ||
                type == EntityType.DOLPHIN;
    }

    /**
     * 手动在指定位置生成海洋生物
     */
    public boolean spawnMarineLifeAt(Location location, EntityType type) {
        if (!enabled) {
            plugin.getLogger().warning("海洋生物系统未启用");
            return false;
        }

        if (!isValidMarineLifeLocation(location)) {
            return false;
        }

        try {
            location.getWorld().spawnEntity(location, type);

            // 记录生成的生物
            UUID worldId = location.getWorld().getUID();
            activeMarineLife.computeIfAbsent(worldId, k -> new HashSet<>()).add(location);

            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("手动生成海洋生物时出错: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取海洋生物类型列表
     */
    public List<EntityType> getAvailableMarineTypes() {
        return Arrays.asList(
                EntityType.COD,
                EntityType.SALMON,
                EntityType.PUFFERFISH,
                EntityType.TROPICAL_FISH,
                EntityType.SQUID,
                EntityType.GLOW_SQUID,
                EntityType.DOLPHIN
        );
    }

    /**
     * 清理所有海洋生物
     */
    public void clearAllMarineLife() {
        World raftWorld = getRaftWorldSafely();
        if (raftWorld != null) {
            int count = 0;
            for (EntityType marineType : getAvailableMarineTypes()) {
                int typeCount = raftWorld.getEntitiesByClass(marineType.getEntityClass()).size();
                count += typeCount;
                raftWorld.getEntitiesByClass(marineType.getEntityClass()).forEach(entity -> entity.remove());
            }
            plugin.getLogger().info("总共清理了 " + count + " 个海洋生物");
        } else {
            plugin.getLogger().warning("无法清理海洋生物: 木筏世界为 null");
        }
        activeMarineLife.clear();
    }

    /**
     * 获取海洋生物分布信息
     */
    public Map<EntityType, Integer> getMarineLifeDistribution() {
        Map<EntityType, Integer> distribution = new HashMap<>();

        if (!enabled) {
            return distribution;
        }

        World raftWorld = getRaftWorldSafely();
        if (raftWorld != null) {
            for (EntityType marineType : getAvailableMarineTypes()) {
                int count = raftWorld.getEntitiesByClass(marineType.getEntityClass()).size();
                distribution.put(marineType, count);
            }
        }

        return distribution;
    }

    /**
     * 启用/禁用海洋生物系统
     */
    public void setEnabled(boolean enabled) {
        boolean previousState = this.enabled;
        this.enabled = enabled;

        if (enabled && !previousState) {
            plugin.getLogger().info("海洋生物系统已启用");
            // 如果之前未启用，现在尝试启动
            if (!initialized) {
                delayedStart();
            } else {
                startMarineLifeSpawning();
            }
        } else if (!enabled && previousState) {
            plugin.getLogger().info("海洋生物系统已禁用");
            clearAllMarineLife();
        }
    }

    /**
     * 检查系统是否已启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 检查系统是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * 安全地重新启动海洋生物系统
     */
    public void restart() {
        plugin.getLogger().info("重新启动海洋生物系统...");
        setEnabled(false);
        clearAllMarineLife();

        // 延迟重新启动
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            World raftWorld = getRaftWorldSafely();
            if (raftWorld != null) {
                setEnabled(true);
                plugin.getLogger().info("海洋生物系统已重新启动");
            } else {
                plugin.getLogger().warning("无法重新启动海洋生物系统：木筏世界未就绪");
                // 继续尝试初始化
                this.initialized = false;
                delayedStart();
            }
        }, 60L); // 延迟3秒
    }

    /**
     * 强制重新初始化系统
     */
    public void reinitialize() {
        plugin.getLogger().info("强制重新初始化海洋生物系统...");
        this.enabled = false;
        this.initialized = false;
        this.raftManager = plugin.getRaftManager();
        clearAllMarineLife();

        // 延迟重新初始化
        Bukkit.getScheduler().runTaskLater(plugin, this::delayedStart, 40L);
    }

    /**
     * 调试方法：在指定位置强制生成海洋生物
     */
    public void debugSpawnMarineLife(Location location, int count) {
        if (!enabled) {
            plugin.getLogger().warning("海洋生物系统未启用，无法调试生成");
            return;
        }

        plugin.getLogger().info("=== 海洋生物调试生成 ===");
        plugin.getLogger().info("位置: " + location);
        plugin.getLogger().info("世界: " + (location.getWorld() != null ? location.getWorld().getName() : "null"));
        plugin.getLogger().info("方块类型: " + location.getBlock().getType());

        // 检查周围的水方块
        World world = location.getWorld();
        int waterBlocks = 0;
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                for (int y = -3; y <= 3; y++) {
                    Location checkLoc = location.clone().add(x, y, z);
                    if (checkLoc.getBlock().getType() == Material.WATER) {
                        waterBlocks++;
                    }
                }
            }
        }
        plugin.getLogger().info("周围水方块数量: " + waterBlocks);

        // 强制生成
        int spawned = 0;
        for (int i = 0; i < count * 3 && spawned < count; i++) {
            int dx = random.nextInt(20) - 10;
            int dz = random.nextInt(20) - 10;

            Location spawnLoc = location.clone().add(dx, 0, dz);
            spawnLoc.setY(findWaterSurface(world, spawnLoc.getBlockX(), spawnLoc.getBlockZ()));

            if (isValidMarineLifeLocation(spawnLoc)) {
                EntityType type = getRandomMarineType(spawnLoc);
                if (spawnMarineLifeAt(spawnLoc, type)) {
                    spawned++;
                    plugin.getLogger().info("成功生成: " + type + " 于 " + spawnLoc);
                }
            }
        }

        plugin.getLogger().info("调试生成完成: " + spawned + "/" + count + " 个海洋生物");
    }

    /**
     * 获取系统状态信息
     */
    public String getStatusInfo() {
        StringBuilder status = new StringBuilder();
        status.append("§6=== 海洋生物系统状态 ===\n");
        status.append("§a系统状态: §e").append(enabled ? "已启用" : "未启用").append("\n");
        status.append("§a初始化状态: §e").append(initialized ? "已初始化" : "未初始化").append("\n");
        status.append("§a活跃生物数量: §e").append(getActiveMarineLifeCount()).append("\n");
        status.append("§aRaftManager状态: §e").append(raftManager != null ? "正常" : "异常").append("\n");

        World raftWorld = getRaftWorldSafely();
        status.append("§a木筏世界状态: §e").append(raftWorld != null ? "已加载" : "未加载").append("\n");

        if (raftWorld != null) {
            int marineCount = raftWorld.getEntities().stream()
                    .filter(entity -> isMarineEntity(entity.getType()))
                    .mapToInt(e -> 1)
                    .sum();
            status.append("§a世界中海洋生物: §e").append(marineCount).append("\n");
        }

        return status.toString();
    }

    /**
     * 获取海洋生物生成统计
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("enabled", enabled);
        stats.put("initialized", initialized);
        stats.put("activeMarineLifeCount", getActiveMarineLifeCount());
        stats.put("recordedLocations", activeMarineLife.values().stream().mapToInt(Set::size).sum());

        World raftWorld = getRaftWorldSafely();
        if (raftWorld != null) {
            Map<EntityType, Integer> distribution = new HashMap<>();
            for (EntityType marineType : getAvailableMarineTypes()) {
                int count = raftWorld.getEntitiesByClass(marineType.getEntityClass()).size();
                distribution.put(marineType, count);
            }
            stats.put("worldDistribution", distribution);
        }

        return stats;
    }
}
package me.tleung.raftGen;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.api.mobs.MobManager;  // 修改这里的导入
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Optional;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;

public class SharkEventManager {
    private final RaftGen plugin;
    private final Random random = new Random();
    private final String MYTHIC_MOBS_SHARK_NAME = "nm_shark_great_white";
    private final Map<UUID, Entity> activeSharks = new HashMap<>();
    private BukkitTask sharkSpawnTask;
    private boolean enabled = true;

    // MythicMobs API 相关字段
    private MobManager mobManager;
    private boolean isMythicMobsAvailable = false;

    public SharkEventManager(RaftGen plugin) {
        this.plugin = plugin;
        setupMythicMobsIntegration();
        startSharkSpawnTask();
    }

    /**
     * 初始化MythicMobs集成（使用官方API）
     */
    private void setupMythicMobsIntegration() {
        try {
            // 检查MythicMobs插件是否存在
            if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
                plugin.getLogger().warning("✗ MythicMobs 插件未找到");
                isMythicMobsAvailable = false;
                return;
            }

            plugin.getLogger().info("✓ 检测到 MythicMobs 插件，尝试初始化集成...");

            // 使用官方API获取MobManager
            mobManager = MythicBukkit.inst().getMobManager();

            if (mobManager == null) {
                plugin.getLogger().warning("✗ 无法获取MythicMobs MobManager");
                isMythicMobsAvailable = false;
                return;
            }

            // 检查目标生物是否存在
            if (!mobManager.getMobNames().contains(MYTHIC_MOBS_SHARK_NAME)) {
                plugin.getLogger().warning("✗ MythicMobs生物不存在: " + MYTHIC_MOBS_SHARK_NAME);
                plugin.getLogger().info("可用生物: " + mobManager.getMobNames());
                isMythicMobsAvailable = false;
                return;
            }

            isMythicMobsAvailable = true;
            plugin.getLogger().info("✓ MythicMobs " + getMythicVersion() + " 集成成功，目标生物: " + MYTHIC_MOBS_SHARK_NAME);

            // 测试API可用性
            testMythicMobsAPI();

        } catch (Exception e) {
            isMythicMobsAvailable = false;
            plugin.getLogger().warning("✗ MythicMobs 初始化异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 测试MythicMobs API可用性
     */
    private void testMythicMobsAPI() {
        try {
            plugin.getLogger().info("✓ MythicMobs API 测试:");
            plugin.getLogger().info("  - MobManager: " + (mobManager != null ? "可用" : "不可用"));
            plugin.getLogger().info("  - 生物列表: " + (mobManager != null ? mobManager.getMobNames().size() + " 个生物" : "不可用"));
            plugin.getLogger().info("  - 目标生物: " + MYTHIC_MOBS_SHARK_NAME + " - " +
                    (mobManager != null && mobManager.getMobNames().contains(MYTHIC_MOBS_SHARK_NAME) ? "存在" : "不存在"));
        } catch (Exception e) {
            plugin.getLogger().warning("测试MythicMobs API时出错: " + e.getMessage());
        }
    }

    /**
     * 获取MythicMobs版本
     */
    private String getMythicVersion() {
        try {
            org.bukkit.plugin.Plugin mythicPlugin = Bukkit.getPluginManager().getPlugin("MythicMobs");
            return mythicPlugin != null ? mythicPlugin.getDescription().getVersion() : "未知版本";
        } catch (Exception e) {
            return "未知版本";
        }
    }

    /**
     * 检查系统是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置系统启用状态
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            stop();
        } else {
            startSharkSpawnTask();
        }
    }

    /**
     * 获取活跃鲨鱼数量
     */
    public int getActiveSharkCount() {
        return activeSharks.size();
    }

    /**
     * 重新启动系统
     */
    public void restart() {
        stop();
        setupMythicMobsIntegration();
        startSharkSpawnTask();
        plugin.getLogger().info("鲨鱼事件系统已重新启动");
    }

    /**
     * 获取状态信息
     */
    public String getStatusInfo() {
        StringBuilder status = new StringBuilder();
        status.append("§6=== 鲨鱼事件系统状态 ===\n");
        status.append("§a系统状态: §e").append(enabled ? "已启用" : "已禁用").append("\n");
        status.append("§a活跃鲨鱼数量: §e").append(getActiveSharkCount()).append("\n");
        status.append("§aMythicMobs集成: §e").append(isMythicMobsAvailable ? "已连接" : "未连接").append("\n");
        status.append("§a生成任务: §e").append(sharkSpawnTask != null && !sharkSpawnTask.isCancelled() ? "运行中" : "已停止").append("\n");

        if (isMythicMobsAvailable) {
            status.append("§a目标生物: §e").append(MYTHIC_MOBS_SHARK_NAME).append("\n");
            status.append("§aMythicMobs版本: §e").append(getMythicVersion()).append("\n");
        }

        // 添加活跃鲨鱼列表
        if (!activeSharks.isEmpty()) {
            status.append("§a活跃鲨鱼列表:\n");
            activeSharks.forEach((playerId, shark) -> {
                String playerName = Bukkit.getOfflinePlayer(playerId).getName();
                if (playerName == null) playerName = "未知玩家";
                status.append("  §7- ").append(playerName).append(": §e")
                        .append(shark != null && shark.isValid() ? "存活" : "无效").append("\n");
            });
        }

        return status.toString();
    }

    /**
     * 为指定玩家UUID生成鲨鱼（重载方法）
     */
    public void spawnSharkForRaft(UUID playerId) {
        Location raftLocation = plugin.getRaftManager().getPlayerRaftLocation(playerId);
        if (raftLocation != null) {
            spawnSharkForRaft(raftLocation, playerId);
        } else {
            plugin.getLogger().warning("无法找到玩家 " + playerId + " 的木筏位置");
        }
    }

    /**
     * 启动鲨鱼生成任务
     */
    private void startSharkSpawnTask() {
        // 每15秒尝试生成鲨鱼
        sharkSpawnTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!enabled) return;

            try {
                plugin.getRaftManager().getAllRafts().forEach((playerId, location) -> {
                    if (shouldSpawnShark()) {
                        spawnSharkForRaft(location, playerId);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "鲨鱼生成任务出错", e);
            }
        }, 20L * 15, 20L * 15); // 初始延迟15秒，间隔15秒
    }

    /**
     * 判断是否应该生成鲨鱼（概率控制）
     */
    private boolean shouldSpawnShark() {
        return random.nextDouble() < 0.3; // 30%概率
    }

    /**
     * 为指定木筏生成鲨鱼
     */
    public void spawnSharkForRaft(Location raftLocation, UUID raftOwnerId) {
        if (!enabled) return;
        if (raftLocation == null || raftLocation.getWorld() == null) {
            plugin.getLogger().warning("木筏位置无效，无法生成鲨鱼");
            return;
        }

        // 检查是否已经有活跃的鲨鱼
        if (activeSharks.containsKey(raftOwnerId)) {
            Entity existingShark = activeSharks.get(raftOwnerId);
            if (existingShark != null && existingShark.isValid()) {
                plugin.getLogger().info("木筏 " + raftOwnerId + " 已经有活跃的鲨鱼");
                return;
            } else {
                activeSharks.remove(raftOwnerId);
            }
        }

        Entity shark = null;

        // 优先使用MythicMobs生成
        if (isMythicMobsAvailable) {
            shark = spawnMythicMobsShark(raftLocation, raftOwnerId);
            if (shark != null) {
                plugin.getLogger().info("使用MythicMobs成功生成鲨鱼: " + shark.getUniqueId());
                return;
            }
        }

        // 如果MythicMobs生成失败，使用原版生物
        plugin.getLogger().info("MythicMobs生成失败，使用原版生物替代");
        shark = spawnVanillaShark(raftLocation);
        if (shark != null) {
            activeSharks.put(raftOwnerId, shark);
            plugin.getLogger().info("为木筏 " + raftOwnerId + " 生成原版鲨鱼替代品");
        }
    }

    /**
     * 使用MythicMobs API生成鲨鱼
     */
    private Entity spawnMythicMobsShark(Location location, UUID raftOwnerId) {
        if (!isMythicMobsAvailable || mobManager == null) {
            return null;
        }

        try {
            Location spawnLocation = getRandomSharkSpawnLocation(location);
            if (!isValidWaterLocation(spawnLocation)) {
                plugin.getLogger().warning("生成位置不是有效水域: " + spawnLocation);
                return null;
            }

            plugin.getLogger().info("尝试生成MythicMobs鲨鱼: " + MYTHIC_MOBS_SHARK_NAME + " 在 " + spawnLocation);

            Entity shark = null;

            // 使用反射调用 spawnMob 方法（MythicMobs 4.x 和 5.x 都支持）
            try {
                java.lang.reflect.Method spawnMethod = mobManager.getClass().getMethod("spawnMob", String.class, Location.class);
                Object result = spawnMethod.invoke(mobManager, MYTHIC_MOBS_SHARK_NAME, spawnLocation);

                if (result instanceof ActiveMob) {
                    ActiveMob activeMob = (ActiveMob) result;
                    shark = activeMob.getEntity().getBukkitEntity();
                }
            } catch (Exception e) {
                plugin.getLogger().warning("使用 spawnMob 方法生成失败: " + e.getMessage());
            }

            if (shark != null && shark.isValid()) {
                activeSharks.put(raftOwnerId, shark);
                plugin.getLogger().info("✓ MythicMobs鲨鱼生成成功: " + shark.getUniqueId());
                return shark;
            }

            plugin.getLogger().warning("MythicMobs生成鲨鱼失败");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "MythicMobs生成鲨鱼失败", e);
        }

        return null;
    }

    /**
     * 生成原版替代鲨鱼（守卫者）
     */
    private Entity spawnVanillaShark(Location location) {
        try {
            Location spawnLocation = getRandomSharkSpawnLocation(location);
            if (!isValidWaterLocation(spawnLocation)) {
                plugin.getLogger().warning("原版鲨鱼生成位置无效: " + spawnLocation);
                return null;
            }

            Entity shark = spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.GUARDIAN);
            shark.setCustomName("§3🦈 深海鲨鱼");
            shark.setCustomNameVisible(true);

            // 设置实体属性
            if (shark instanceof LivingEntity) {
                LivingEntity livingShark = (LivingEntity) shark;
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (livingShark.isValid()) {
                            livingShark.setMaxHealth(40);
                            livingShark.setHealth(40);
                        }
                    }
                }.runTaskLater(plugin, 1L);
            }

            plugin.getLogger().info("成功生成原版鲨鱼: " + spawnLocation);
            return shark;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "原版鲨鱼生成失败", e);
            return null;
        }
    }

    /**
     * 获取随机鲨鱼生成位置（木筏周围10-20格）
     */
    private Location getRandomSharkSpawnLocation(Location raftLocation) {
        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = 10 + random.nextDouble() * 10; // 10-20格距离

        double x = raftLocation.getX() + Math.cos(angle) * distance;
        double z = raftLocation.getZ() + Math.sin(angle) * distance;
        double y = findWaterSurface(new Location(raftLocation.getWorld(), x, 62, z));

        Location spawnLocation = new Location(raftLocation.getWorld(), x, y, z);

        // 验证并修正位置
        if (!isValidWaterLocation(spawnLocation)) {
            plugin.getLogger().warning("生成位置无效，重新计算: " + spawnLocation);
            for (int i = 0; i < 3; i++) {
                angle = random.nextDouble() * 2 * Math.PI;
                distance = 10 + random.nextDouble() * 10;
                x = raftLocation.getX() + Math.cos(angle) * distance;
                z = raftLocation.getZ() + Math.sin(angle) * distance;
                y = findWaterSurface(new Location(raftLocation.getWorld(), x, 62, z));
                spawnLocation = new Location(raftLocation.getWorld(), x, y, z);

                if (isValidWaterLocation(spawnLocation)) {
                    plugin.getLogger().info("重新计算后找到有效位置: " + spawnLocation);
                    break;
                }
            }
        }

        plugin.getLogger().info("生成鲨鱼位置: " + spawnLocation);
        plugin.getLogger().info("世界: " + spawnLocation.getWorld().getName());
        return spawnLocation;
    }

    /**
     * 查找水面高度
     */
    private double findWaterSurface(Location location) {
        if (location.getWorld() == null) return 62;

        int x = location.getBlockX();
        int z = location.getBlockZ();
        World world = location.getWorld();

        // 从海平面向下查找水源
        for (int y = 62; y >= 10; y--) {
            Material type = world.getBlockAt(x, y, z).getType();
            if (type == Material.WATER || type == Material.BUBBLE_COLUMN) {
                return y;
            }
        }

        // 未找到水源，返回默认高度
        plugin.getLogger().warning("未找到水面，使用默认高度62");
        return 62;
    }

    /**
     * 验证是否为有效水域位置
     */
    private boolean isValidWaterLocation(Location loc) {
        if (loc.getWorld() == null) return false;

        Material blockType = loc.getBlock().getType();
        // 检查是否为水源方块且上方有空间
        return (blockType == Material.WATER || blockType == Material.BUBBLE_COLUMN)
                && loc.getBlockY() > 0
                && loc.getBlockY() < 255
                && loc.getWorld().getBlockAt(loc.getBlockX(), loc.getBlockY() + 1, loc.getBlockZ()).isPassable();
    }

    /**
     * 清理鲨鱼实体
     */
    public void cleanupSharks() {
        int count = 0;
        for (Entity shark : activeSharks.values()) {
            if (shark != null && shark.isValid()) {
                shark.remove();
                count++;
            }
        }
        activeSharks.clear();
        plugin.getLogger().info("清理了 " + count + " 个鲨鱼实体");
    }

    /**
     * 停止鲨鱼生成任务
     */
    public void stop() {
        if (sharkSpawnTask != null) {
            sharkSpawnTask.cancel();
            sharkSpawnTask = null;
        }
        cleanupSharks();
        plugin.getLogger().info("§a鲨鱼事件系统已禁用");
    }

    // Getter方法
    public Map<UUID, Entity> getActiveSharks() {
        return activeSharks;
    }

    public boolean isMythicMobsAvailable() {
        return isMythicMobsAvailable;
    }
}
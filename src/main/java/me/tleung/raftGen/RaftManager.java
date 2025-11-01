package me.tleung.raftGen;

import me.tleung.raftGen.event.RaftCreateEvent;
import me.tleung.raftGen.event.RaftDeleteEvent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.logging.Level;

public class RaftManager {

    private final RaftGen plugin;
    private final HashMap<UUID, Location> playerRafts;
    private final HashMap<UUID, String> raftNames;
    private final HashMap<UUID, Long> deleteConfirmations;
    private World raftWorld;
    private final Random random;
    private final TeamManager teamManager;
    private final DataManager dataManager;
    private final MarineLifeManager marineLifeManager;

    public RaftManager(RaftGen plugin) {
        this.plugin = plugin;
        this.playerRafts = new HashMap<>();
        this.raftNames = new HashMap<>();
        this.deleteConfirmations = new HashMap<>();
        this.raftWorld = null;
        this.random = new Random();
        this.teamManager = new TeamManager(plugin);
        this.dataManager = new DataManager(plugin);
        this.marineLifeManager = new MarineLifeManager(plugin);

        initializeRaftWorld();
    }

    /**
     * 初始化木筏世界（改为公共方法供其他类调用）
     */
    public void initializeRaftWorld() {
        String worldName = plugin.getConfig().getString("raft.world-name", "raft_world");
        boolean enableSeparateWorld = plugin.getConfig().getBoolean("raft.enable-separate-world", true);

        if (!enableSeparateWorld) {
            raftWorld = Bukkit.getWorld("world");
            plugin.getLogger().info("使用主世界作為木筏世界");
            if (raftWorld != null) {
                setupWorldRules();
            }
            return;
        }

        raftWorld = Bukkit.getWorld(worldName);
        if (raftWorld != null) {
            plugin.getLogger().info("木筏世界 '" + worldName + "' 已載入");
            setupWorldRules();
            return;
        }

        plugin.getLogger().info("正在創建木筏世界: " + worldName);

        WorldCreator worldCreator = new WorldCreator(worldName);
        worldCreator.environment(World.Environment.NORMAL);
        worldCreator.type(WorldType.NORMAL);

        RaftChunkGenerator generator = new RaftChunkGenerator(plugin);
        worldCreator.generator(generator);

        try {
            raftWorld = worldCreator.createWorld();
            if (raftWorld != null) {
                setupWorldRules();
                plugin.getLogger().info("木筏世界創建成功: " + worldName);
            } else {
                plugin.getLogger().warning("木筏世界創建失敗，使用主世界");
                raftWorld = Bukkit.getWorld("world");
                if (raftWorld != null) {
                    setupWorldRules();
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("創建木筏世界時發生錯誤: " + e.getMessage());
            raftWorld = Bukkit.getWorld("world");
            if (raftWorld != null) {
                setupWorldRules();
            }
        }
    }

    private void setupWorldRules() {
        if (raftWorld == null) return;

        raftWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
        raftWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        raftWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        raftWorld.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        raftWorld.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        raftWorld.setGameRule(GameRule.DO_FIRE_TICK, false);
        raftWorld.setTime(6000);
    }

    public void createRaft(Player player) {
        createRaftAtLocation(player, null);
    }

    /**
     * 在指定位置創建木筏 (API使用)
     */
    public boolean createRaftAtLocation(Player player, Location customLocation) {
        UUID playerId = player.getUniqueId();

        UUID teamLeaderId = teamManager.getPlayerTeamLeader(playerId);
        if (teamLeaderId != null && !teamLeaderId.equals(playerId)) {
            if (playerRafts.containsKey(teamLeaderId)) {
                player.sendMessage("§a你已加入隊伍，將使用隊長的木筏...");
                teleportToRaft(player);
                return true;
            } else {
                player.sendMessage("§c你的隊長還沒有創建木筏! 請等待隊長創建");
                return false;
            }
        }

        if (playerRafts.containsKey(playerId)) {
            player.sendMessage("§c你已經有一個木筏了! 使用 /raft home 傳送過去");
            return false;
        }

        player.sendMessage("§a正在生成你的木筏...");

        if (raftWorld == null) {
            initializeRaftWorld();
            if (raftWorld == null) {
                player.sendMessage("§c木筏世界載入失敗，請聯繫管理員");
                return false;
            }
        }

        int spacing = 200;
        int baseHeight = 62;

        Location raftLocation;
        if (customLocation != null) {
            // 使用自定義位置
            raftLocation = customLocation;
        } else {
            // 自動生成位置
            int raftIndex = playerRafts.size();
            int raftX = raftIndex * spacing;
            int raftZ = raftIndex * spacing;
            raftLocation = new Location(raftWorld, raftX, baseHeight, raftZ);
        }

        // 調用創建事件
        RaftCreateEvent event = new RaftCreateEvent(player, raftLocation);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            player.sendMessage("§c木筏創建被取消!");
            return false;
        }
        raftLocation = event.getLocation();

        playerRafts.put(playerId, raftLocation);
        raftNames.put(playerId, player.getName() + "的木筏");

        if (teamManager.isTeamLeader(playerId)) {
            Set<UUID> teamMembers = teamManager.getTeamMembers(playerId);
            for (UUID memberId : teamMembers) {
                if (!memberId.equals(playerId)) {
                    playerRafts.put(memberId, raftLocation);
                    raftNames.put(memberId, player.getName() + "的隊伍木筏");
                }
            }
            teamManager.broadcastToTeam(playerId, "§a隊長已創建隊伍木筏! 使用 §e/raft home §a傳送過去");
        }

        player.sendMessage("§e你的木筏位於獨立世界: §b" + raftWorld.getName());
        player.sendMessage("§e木筏位置: §aX: " + raftLocation.getBlockX() + " §aZ: " + raftLocation.getBlockZ());

        preGenerateRaftArea(raftLocation);

        // 使用 final 變量來解決內部類問題
        final Location finalRaftLocation = raftLocation;
        final int finalBaseHeight = baseHeight;
        final UUID finalPlayerId = playerId;

        new BukkitRunnable() {
            @Override
            public void run() {
                // 生成木筏方块
                generateRaftBlocks(finalRaftLocation);

                // 在木筏周围生成海洋生物 - 添加检查确保系统就绪
                if (marineLifeManager != null && marineLifeManager.isEnabled()) {
                    marineLifeManager.spawnMarineLifeAroundRaft(finalRaftLocation);
                } else {
                    plugin.getLogger().info("海洋生物系统未就绪，跳过初始生成");
                    // 尝试重新启动海洋生物系统
                    if (marineLifeManager != null) {
                        marineLifeManager.restart();
                        // 延迟生成海洋生物
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (marineLifeManager.isEnabled()) {
                                marineLifeManager.spawnMarineLifeAroundRaft(finalRaftLocation);
                                plugin.getLogger().info("延迟生成木筏周围的海洋生物");
                            }
                        }, 40L); // 延迟2秒
                    }
                }

                // 直接使用 baseHeight + 1 生成玩家
                Location spawnLocation = new Location(raftWorld, finalRaftLocation.getX() + 0.5, finalBaseHeight + 1, finalRaftLocation.getZ() + 0.5);
                spawnLocation.setYaw(180);

                Chunk chunk = spawnLocation.getChunk();
                if (!chunk.isLoaded()) {
                    chunk.load();
                }

                ensureSafeSpawnArea(spawnLocation);
                clearSpawnArea(spawnLocation);
                safeTeleport(player, spawnLocation);

                player.sendMessage("§a=== 你的木筏已生成完成! ===");
                player.sendMessage("§6木筏名稱: §e" + raftNames.get(finalPlayerId));
                player.sendMessage("§6世界: §b" + raftWorld.getName());
                player.sendMessage("§6木筏大小: §e3x3 木筏");
                player.sendMessage("§6海洋生態: §a" + (marineLifeManager != null && marineLifeManager.isEnabled() ? "已啟用海洋生物生成" : "海洋生物生成待處理"));

                if (teamManager.isTeamLeader(finalPlayerId)) {
                    player.sendMessage("§6隊伍成員: §e" + (teamManager.getTeamMembers(finalPlayerId).size()) + " 人");
                }

                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.spawnParticle(Particle.HEART, player.getLocation(), 10);

                plugin.getLogger().info("為玩家 " + player.getName() + " 生成木筏於: " + finalRaftLocation.getBlockX() + ", " + finalRaftLocation.getBlockZ());

                // 創建木筏後保存數據
                saveData();
            }
        }.runTask(plugin);

        return true;
    }

    /**
     * 生成木筏方块 - 将水方块替换为木筏并清除上方方块
     */
    private void generateRaftBlocks(Location center) {
        World world = center.getWorld();
        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();
        int baseHeight = 62;

        plugin.getLogger().info("生成木筏方块于位置: " + centerX + ", " + baseHeight + ", " + centerZ);

        // 在中心3x3区域生成木筏
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                int blockX = centerX + x;
                int blockZ = centerZ + z;

                // 将水方块替换为橡木板
                Block block = world.getBlockAt(blockX, baseHeight, blockZ);
                if (block.getType() == Material.WATER || block.getType() == Material.AIR) {
                    block.setType(Material.OAK_PLANKS);
                    plugin.getLogger().info("設置木筏方块: " + blockX + ", " + baseHeight + ", " + blockZ);
                }

                // 清除木筏上方的方块（确保是空气）
                for (int y = baseHeight + 1; y <= baseHeight + 3; y++) {
                    Block aboveBlock = world.getBlockAt(blockX, y, blockZ);
                    if (aboveBlock.getType() != Material.AIR) {
                        aboveBlock.setType(Material.AIR);
                    }
                }
            }
        }

        // 强制重新载入区块以确保客户端更新
        int chunkX = centerX >> 4;
        int chunkZ = centerZ >> 4;
        world.refreshChunk(chunkX, chunkZ);

        plugin.getLogger().info("木筏方块生成完成");
    }

    /**
     * 删除木筏方块 - 将木筏方块替换为水并清除上方方块
     */
    private void removeRaftBlocks(Location center) {
        World world = center.getWorld();
        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();
        int baseHeight = 62;

        plugin.getLogger().info("删除木筏方块于位置: " + centerX + ", " + baseHeight + ", " + centerZ);

        // 在中心3x3区域删除木筏
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                int blockX = centerX + x;
                int blockZ = centerZ + z;

                // 将木筏方块替换为水
                Block block = world.getBlockAt(blockX, baseHeight, blockZ);
                if (block.getType() == Material.OAK_PLANKS) {
                    block.setType(Material.WATER);
                    plugin.getLogger().info("移除木筏方块: " + blockX + ", " + baseHeight + ", " + blockZ);
                }

                // 清除木筏上方的方块
                for (int y = baseHeight + 1; y <= baseHeight + 3; y++) {
                    Block aboveBlock = world.getBlockAt(blockX, y, blockZ);
                    if (aboveBlock.getType() != Material.WATER && aboveBlock.getType() != Material.AIR) {
                        aboveBlock.setType(Material.AIR);
                    }
                }
            }
        }

        // 强制重新载入区块以确保客户端更新
        int chunkX = centerX >> 4;
        int chunkZ = centerZ >> 4;
        world.refreshChunk(chunkX, chunkZ);

        plugin.getLogger().info("木筏方块删除完成");
    }

    private void preGenerateRaftArea(Location center) {
        World world = center.getWorld();
        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();

        // 在更大的範圍內預生成區塊 (5x5區域)
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                int chunkX = (centerX + x) >> 4;
                int chunkZ = (centerZ + z) >> 4;
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    world.loadChunk(chunkX, chunkZ);
                }
            }
        }
    }

    private void ensureRaftGenerated(Location center) {
        World world = center.getWorld();
        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();
        int baseHeight = 62;

        // 检查木筏是否已经存在
        boolean raftExists = false;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Block block = world.getBlockAt(centerX + x, baseHeight, centerZ + z);
                if (block.getType() == Material.OAK_PLANKS) {
                    raftExists = true;
                    break;
                }
            }
            if (raftExists) break;
        }

        // 如果木筏不存在，重新生成
        if (!raftExists) {
            plugin.getLogger().info("木筏不存在，重新生成木筏方块");
            generateRaftBlocks(center);
        } else {
            plugin.getLogger().info("木筏已存在，无需重新生成");
        }
    }

    private void ensureSafeSpawnArea(Location spawnLocation) {
        World world = spawnLocation.getWorld();
        int centerX = spawnLocation.getBlockX();
        int centerY = spawnLocation.getBlockY();
        int centerZ = spawnLocation.getBlockZ();

        Block standBlock = world.getBlockAt(centerX, centerY - 1, centerZ);
        if (!standBlock.getType().isSolid()) {
            standBlock.setType(Material.OAK_PLANKS);
        }

        for (int y = centerY; y <= centerY + 2; y++) {
            Block headBlock = world.getBlockAt(centerX, y, centerZ);
            if (headBlock.getType() != Material.AIR) {
                headBlock.setType(Material.AIR);
            }
        }
    }

    private void safeTeleport(Player player, Location location) {
        player.setFallDistance(0f);
        Location safeLocation = ensureSafeRaftLocation(location);

        giveTemporaryFallProtection(player);
        player.teleport(safeLocation);
        player.sendMessage("§a安全傳送完成!");

        // 二次檢查，確保玩家站在安全位置
        new BukkitRunnable() {
            @Override
            public void run() {
                Location currentLoc = player.getLocation();
                Block belowBlock = currentLoc.getWorld().getBlockAt(
                        currentLoc.getBlockX(),
                        currentLoc.getBlockY() - 1,
                        currentLoc.getBlockZ()
                );

                // 如果下方不是木筏，調整位置
                if (belowBlock.getType() != Material.OAK_PLANKS) {
                    Location adjustedLoc = findSafeRaftLocation(currentLoc);
                    player.teleport(adjustedLoc);
                    player.setFallDistance(0f);
                    player.sendMessage("§e位置已調整至安全木筏區域");
                }
                player.setFallDistance(0f);
            }
        }.runTaskLater(plugin, 5L);
    }

    /**
     * 確保木筏位置安全
     */
    private Location ensureSafeRaftLocation(Location targetLocation) {
        World world = targetLocation.getWorld();
        int x = targetLocation.getBlockX();
        int z = targetLocation.getBlockZ();

        // 在木筏中心3x3區域內尋找安全位置
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int checkX = x + dx;
                int checkZ = z + dz;

                Block standBlock = world.getBlockAt(checkX, 62, checkZ);
                Block feetBlock = world.getBlockAt(checkX, 63, checkZ);
                Block headBlock = world.getBlockAt(checkX, 64, checkZ);

                // 檢查是否在木筏上且有足夠的空間
                if (standBlock.getType() == Material.OAK_PLANKS &&
                        feetBlock.getType() == Material.AIR &&
                        headBlock.getType() == Material.AIR) {
                    return new Location(world, checkX + 0.5, 63, checkZ + 0.5, targetLocation.getYaw(), targetLocation.getPitch());
                }
            }
        }

        // 如果沒有找到理想位置，強制生成木筏並返回中心
        world.getBlockAt(x, 62, z).setType(Material.OAK_PLANKS);
        world.getBlockAt(x, 63, z).setType(Material.AIR);
        world.getBlockAt(x, 64, z).setType(Material.AIR);

        return new Location(world, x + 0.5, 63, z + 0.5, targetLocation.getYaw(), targetLocation.getPitch());
    }

    /**
     * 尋找安全木筏位置
     */
    private Location findSafeRaftLocation(Location currentLoc) {
        World world = currentLoc.getWorld();
        int x = currentLoc.getBlockX();
        int z = currentLoc.getBlockZ();

        // 在周圍尋找木筏方塊
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Block block = world.getBlockAt(x + dx, 62, z + dz);
                if (block.getType() == Material.OAK_PLANKS) {
                    return new Location(world, x + dx + 0.5, 63, z + dz + 0.5, currentLoc.getYaw(), currentLoc.getPitch());
                }
            }
        }

        // 如果找不到，回到原始位置上方
        return currentLoc.clone().add(0, 5, 0);
    }

    private void giveTemporaryFallProtection(Player player) {
        player.setFallDistance(0f);
        new BukkitRunnable() {
            @Override
            public void run() {
                player.setFallDistance(0f);
            }
        }.runTaskLater(plugin, 100L);
    }

    private void clearSpawnArea(Location spawnLocation) {
        int centerX = spawnLocation.getBlockX();
        int centerY = spawnLocation.getBlockY();
        int centerZ = spawnLocation.getBlockZ();

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 0; y <= 2; y++) {
                    Block block = spawnLocation.getWorld().getBlockAt(centerX + x, centerY + y, centerZ + z);
                    if (block.getType() != Material.AIR && block.getType() != Material.WATER) {
                        block.setType(Material.AIR);
                    }
                }
            }
        }
    }

    public void teleportToRaft(Player player) {
        UUID playerId = player.getUniqueId();

        UUID teamLeaderId = teamManager.getPlayerTeamLeader(playerId);
        UUID targetPlayerId = (teamLeaderId != null) ? teamLeaderId : playerId;

        if (!playerRafts.containsKey(targetPlayerId)) {
            player.sendMessage("§c你還沒有木筏! 使用 /raft create 創建一個");
            return;
        }

        Location raftLoc = playerRafts.get(targetPlayerId);
        preGenerateRaftArea(raftLoc);
        // 直接使用 63 生成玩家
        Location spawnLocation = new Location(raftWorld, raftLoc.getX() + 0.5, 63, raftLoc.getZ() + 0.5);

        player.sendMessage("§e正在傳送到木筏世界: §b" + raftLoc.getWorld().getName());
        preloadChunks(raftLoc);

        Chunk chunk = spawnLocation.getChunk();
        if (!chunk.isLoaded()) {
            chunk.load();
        }

        ensureRaftGenerated(raftLoc);
        ensureSafeSpawnArea(spawnLocation);
        clearSpawnArea(spawnLocation);
        safeTeleport(player, spawnLocation);

        if (teamLeaderId != null && !teamLeaderId.equals(playerId)) {
            player.sendMessage("§a已傳送到隊伍木筏!");
        } else {
            player.sendMessage("§a已傳送到你的木筏!");
        }

        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
    }

    public void resetRaft(Player player) {
        UUID playerId = player.getUniqueId();

        UUID teamLeaderId = teamManager.getPlayerTeamLeader(playerId);
        if (teamLeaderId != null && !teamLeaderId.equals(playerId)) {
            player.sendMessage("§c只有隊長可以重置木筏!");
            return;
        }

        UUID targetPlayerId = (teamLeaderId != null) ? teamLeaderId : playerId;

        if (!playerRafts.containsKey(targetPlayerId)) {
            player.sendMessage("§c你還沒有木筏!");
            return;
        }

        player.sendMessage("§e正在重置你的木筏...");
        Location raftLoc = playerRafts.get(targetPlayerId);
        preloadChunks(raftLoc);

        // 使用 final 變量
        final UUID finalPlayerId = playerId;
        final Location finalRaftLoc = raftLoc;

        new BukkitRunnable() {
            @Override
            public void run() {
                resetRaftStructures(finalRaftLoc);
                ensureRaftGenerated(finalRaftLoc);

                // 重置后在木筏周围重新生成海洋生物 - 添加检查
                if (marineLifeManager != null && marineLifeManager.isEnabled()) {
                    marineLifeManager.spawnMarineLifeAroundRaft(finalRaftLoc);
                } else {
                    plugin.getLogger().info("海洋生物系统未就绪，跳过重置后的生成");
                    // 尝试重新启动海洋生物系统
                    if (marineLifeManager != null) {
                        marineLifeManager.restart();
                    }
                }

                player.sendMessage("§a木筏重置完成!");
                player.sendMessage("§6木筏已恢復為純淨地形");
                player.sendMessage("§6海洋生態: §e" + (marineLifeManager != null && marineLifeManager.isEnabled() ? "已重新生成" : "生成待處理"));

                if (teamManager.isTeamLeader(finalPlayerId)) {
                    teamManager.broadcastToTeam(finalPlayerId, "§a隊伍木筏已重置!");
                }

                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);

                // 重置後保存數據
                saveData();
            }
        }.runTask(plugin);
    }

    public void showRaftInfo(Player player) {
        UUID playerId = player.getUniqueId();

        UUID teamLeaderId = teamManager.getPlayerTeamLeader(playerId);
        UUID targetPlayerId = (teamLeaderId != null) ? teamLeaderId : playerId;

        if (!playerRafts.containsKey(targetPlayerId)) {
            player.sendMessage("§c你還沒有木筏! 使用 /raft create 創建一個");
            return;
        }

        Location raftLoc = playerRafts.get(targetPlayerId);
        player.sendMessage("§6=== 木筏資訊 ===");
        player.sendMessage("§a名稱: §f" + raftNames.get(targetPlayerId));
        player.sendMessage("§a位置: §f" + formatLocation(raftLoc));
        player.sendMessage("§a世界: §b" + raftLoc.getWorld().getName());
        player.sendMessage("§a大小: §f3x3 木筏");
        player.sendMessage("§a海洋生態: §" + (marineLifeManager != null && marineLifeManager.isEnabled() ? "a已啟用" : "c未啟用"));

        if (teamLeaderId != null) {
            Player leader = Bukkit.getPlayer(teamLeaderId);
            String leaderName = leader != null ? leader.getName() : "未知";
            if (teamLeaderId.equals(playerId)) {
                player.sendMessage("§a隊伍: §f你是隊長，隊伍成員: " + teamManager.getTeamMembers(teamLeaderId).size() + " 人");
            } else {
                player.sendMessage("§a隊伍: §f你屬於 " + leaderName + " 的隊伍");
            }
        }
    }

    public void deleteRaft(Player player, String targetPlayerName) {
        // 調用刪除事件
        RaftDeleteEvent event = new RaftDeleteEvent(player);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            player.sendMessage("§c木筏刪除被取消!");
            return;
        }

        UUID playerId = player.getUniqueId();

        if (targetPlayerName != null) {
            if (!player.hasPermission("raftgen.delete.others")) {
                player.sendMessage("§c你沒有權限刪除其他玩家的木筏!");
                return;
            }

            Player targetPlayer = plugin.getServer().getPlayer(targetPlayerName);
            if (targetPlayer == null) {
                player.sendMessage("§c找不到玩家: " + targetPlayerName);
                return;
            }

            UUID targetPlayerId = targetPlayer.getUniqueId();
            if (!playerRafts.containsKey(targetPlayerId)) {
                player.sendMessage("§c玩家 " + targetPlayerName + " 沒有木筏!");
                return;
            }

            if (targetPlayer.isOnline()) {
                teleportToSpawn(targetPlayer);
            }

            completelyClearRaftArea(targetPlayerId);
            playerRafts.remove(targetPlayerId);
            raftNames.remove(targetPlayerId);

            player.sendMessage("§a已成功刪除玩家 " + targetPlayerName + " 的木筏!");
            if (targetPlayer.isOnline()) {
                targetPlayer.sendMessage("§c你的木筏已被管理員刪除!");
            }

            // 刪除後保存數據
            saveData();
            return;
        }

        UUID teamLeaderId = teamManager.getPlayerTeamLeader(playerId);
        if (teamLeaderId != null && !teamLeaderId.equals(playerId)) {
            player.sendMessage("§c只有隊長可以刪除木筏!");
            return;
        }

        UUID targetPlayerId = (teamLeaderId != null) ? teamLeaderId : playerId;

        if (!playerRafts.containsKey(targetPlayerId)) {
            player.sendMessage("§c你還沒有木筏!");
            return;
        }

        long currentTime = System.currentTimeMillis();

        if (deleteConfirmations.containsKey(playerId)) {
            long lastConfirmTime = deleteConfirmations.get(playerId);
            if (currentTime - lastConfirmTime < 30000) {
                confirmDeleteRaft(player);
                return;
            } else {
                deleteConfirmations.remove(playerId);
            }
        }

        deleteConfirmations.put(playerId, currentTime);
        player.sendMessage("§c⚠ 警告! 你即將刪除你的木筏!");
        player.sendMessage("§c所有木筏上的建築和物品將會永久消失!");

        if (teamManager.isTeamLeader(playerId)) {
            player.sendMessage("§c這將影響所有隊伍成員!");
        }

        player.sendMessage("§c請在 30 秒內使用 §e/raft delete confirm §c來確認刪除!");
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
    }

    public void confirmDeleteRaft(Player player) {
        UUID playerId = player.getUniqueId();

        if (!deleteConfirmations.containsKey(playerId)) {
            player.sendMessage("§c沒有待確認的刪除操作! 請先使用 /raft delete");
            return;
        }

        long lastConfirmTime = deleteConfirmations.get(playerId);
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastConfirmTime > 30000) {
            player.sendMessage("§c確認時間已過期! 請重新使用 /raft delete");
            deleteConfirmations.remove(playerId);
            return;
        }

        UUID teamLeaderId = teamManager.getPlayerTeamLeader(playerId);
        if (teamLeaderId != null && !teamLeaderId.equals(playerId)) {
            player.sendMessage("§c只有隊長可以刪除木筏!");
            deleteConfirmations.remove(playerId);
            return;
        }

        UUID targetPlayerId = (teamLeaderId != null) ? teamLeaderId : playerId;

        if (!playerRafts.containsKey(targetPlayerId)) {
            player.sendMessage("§c你還沒有木筏!");
            deleteConfirmations.remove(playerId);
            return;
        }

        player.sendMessage("§e正在刪除你的木筏...");

        // 使用 final 變量
        final UUID finalPlayerId = playerId;
        final UUID finalTargetPlayerId = targetPlayerId;

        new BukkitRunnable() {
            @Override
            public void run() {
                Location raftLoc = playerRafts.get(finalTargetPlayerId);
                plugin.getLogger().info("開始刪除木筏，位置: " + raftLoc);

                boolean raftExistsBefore = isRaftStillExists(finalTargetPlayerId);
                if (raftExistsBefore) {
                    plugin.getLogger().info("刪除前檢測到木筏仍然存在，開始清除...");
                }

                // 先傳送玩家
                teleportToSpawn(player);

                if (teamManager.isTeamLeader(finalPlayerId)) {
                    Set<UUID> teamMembers = teamManager.getTeamMembers(finalPlayerId);
                    for (UUID memberId : teamMembers) {
                        Player member = Bukkit.getPlayer(memberId);
                        if (member != null && member.isOnline() && !memberId.equals(finalPlayerId)) {
                            teleportToSpawn(member);
                        }
                    }
                }

                // 清除木筏區域
                completelyClearRaftArea(finalTargetPlayerId);

                // 等待一會後再次檢查和清除
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        // 再次清除以確保完全清除
                        completelyClearRaftArea(finalTargetPlayerId);

                        boolean raftExistsAfter = isRaftStillExists(finalTargetPlayerId);
                        if (raftExistsAfter) {
                            plugin.getLogger().warning("刪除後木筏仍然存在，進行最終清除...");
                            completelyClearRaftArea(finalTargetPlayerId);

                            // 強制重新載入區塊
                            World world = raftLoc.getWorld();
                            int chunkX = raftLoc.getBlockX() >> 4;
                            int chunkZ = raftLoc.getBlockZ() >> 4;
                            world.refreshChunk(chunkX, chunkZ);
                        }

                        // 移除數據
                        if (teamManager.isTeamLeader(finalPlayerId)) {
                            Set<UUID> teamMembers = teamManager.getTeamMembers(finalPlayerId);
                            for (UUID memberId : teamMembers) {
                                playerRafts.remove(memberId);
                                raftNames.remove(memberId);
                                deleteConfirmations.remove(memberId);
                            }
                            teamManager.broadcastToTeam(finalPlayerId, "§c隊伍木筏已被隊長刪除!");
                        } else {
                            playerRafts.remove(finalTargetPlayerId);
                            raftNames.remove(finalTargetPlayerId);
                            deleteConfirmations.remove(finalPlayerId);
                        }

                        player.sendMessage("§a木筏刪除完成!");
                        if (raftExistsAfter) {
                            player.sendMessage("§6注意: 木筏區域可能需要重新載入區塊才能完全顯示清除效果");
                        } else {
                            player.sendMessage("§6木筏區域已完全清除，恢復為海洋");
                        }
                        player.sendMessage("§6你可以使用 §a/raft create §6來創建一個新的木筏");
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.5f);

                        // 刪除後保存數據
                        saveData();
                    }
                }.runTaskLater(plugin, 20L); // 等待 1 秒後執行
            }
        }.runTask(plugin);
    }

    public void deleteOtherPlayerRaft(CommandSender sender, String targetPlayerName) {
        Player targetPlayer = plugin.getServer().getPlayer(targetPlayerName);
        if (targetPlayer == null) {
            for (UUID playerId : playerRafts.keySet()) {
                OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(playerId);
                if (offlinePlayer.getName() != null && offlinePlayer.getName().equalsIgnoreCase(targetPlayerName)) {
                    completelyClearRaftArea(playerId);
                    playerRafts.remove(playerId);
                    raftNames.remove(playerId);

                    sender.sendMessage("§a已成功刪除玩家 " + targetPlayerName + " 的木筏!");

                    // 刪除後保存數據
                    saveData();
                    return;
                }
            }
            sender.sendMessage("§c找不到玩家: " + targetPlayerName);
            return;
        }

        UUID targetPlayerId = targetPlayer.getUniqueId();
        if (!playerRafts.containsKey(targetPlayerId)) {
            sender.sendMessage("§c玩家 " + targetPlayerName + " 沒有木筏!");
            return;
        }

        if (targetPlayer.isOnline()) {
            teleportToSpawn(targetPlayer);
        }

        completelyClearRaftArea(targetPlayerId);
        playerRafts.remove(targetPlayerId);
        raftNames.remove(targetPlayerId);

        sender.sendMessage("§a已成功刪除玩家 " + targetPlayerName + " 的木筏!");
        if (targetPlayer.isOnline()) {
            targetPlayer.sendMessage("§c你的木筏已被管理員刪除!");
        }

        // 刪除後保存數據
        saveData();
    }

    /**
     * 強制刪除木筏 (API使用)
     */
    public void forceDeleteRaft(UUID playerId) {
        completelyClearRaftArea(playerId);
        playerRafts.remove(playerId);
        raftNames.remove(playerId);
        deleteConfirmations.remove(playerId);

        // 刪除後保存數據
        saveData();
    }

    public void forceClearRaftArea(Player player) {
        UUID playerId = player.getUniqueId();

        if (!playerRafts.containsKey(playerId)) {
            player.sendMessage("§c你還沒有木筏!");
            return;
        }

        player.sendMessage("§e正在強制清除木筏區域...");

        // 使用 final 變量
        final UUID finalPlayerId = playerId;

        new BukkitRunnable() {
            @Override
            public void run() {
                Location raftLoc = playerRafts.get(finalPlayerId);
                completelyClearRaftArea(finalPlayerId);
                World world = raftLoc.getWorld();
                int chunkX = raftLoc.getBlockX() >> 4;
                int chunkZ = raftLoc.getBlockZ() >> 4;
                world.refreshChunk(chunkX, chunkZ);
                player.sendMessage("§a強制清除完成!");
                player.sendMessage("§6木筏區域已強制清除並重新載入");

                // 清除後保存數據
                saveData();
            }
        }.runTask(plugin);
    }

    private void completelyClearRaftArea(UUID playerId) {
        if (!playerRafts.containsKey(playerId)) {
            return;
        }

        Location raftLoc = playerRafts.get(playerId);
        plugin.getLogger().info("开始完全清除玩家 " + playerId + " 的木筏区域，位置: " + raftLoc);

        // 移除木筏方块
        removeRaftBlocks(raftLoc);

        // 额外清理周围区域（防止有残留建筑）
        World world = raftLoc.getWorld();
        int centerX = raftLoc.getBlockX();
        int centerZ = raftLoc.getBlockZ();
        int baseHeight = 62;

        // 扩大清除范围到 7x7 区域
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                for (int y = baseHeight + 1; y <= baseHeight + 10; y++) {
                    Block block = world.getBlockAt(centerX + x, y, centerZ + z);
                    if (block.getType() != Material.WATER && block.getType() != Material.AIR) {
                        block.setType(Material.AIR);
                    }
                }
            }
        }

        plugin.getLogger().info("完成清除玩家 " + playerId + " 的木筏区域");
    }

    private boolean isRaftStillExists(UUID playerId) {
        if (!playerRafts.containsKey(playerId)) {
            return false;
        }

        Location raftLoc = playerRafts.get(playerId);
        World world = raftLoc.getWorld();
        int centerX = raftLoc.getBlockX();
        int centerZ = raftLoc.getBlockZ();
        int baseHeight = 62;

        // 檢查木筏核心區域是否有木筏方塊
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Block block = world.getBlockAt(centerX + x, baseHeight, centerZ + z);
                if (block.getType() == Material.OAK_PLANKS) {
                    plugin.getLogger().warning("檢測到木筏方塊仍然存在: " + (centerX + x) + ", " + baseHeight + ", " + (centerZ + z));
                    return true;
                }
            }
        }

        // 檢查是否有其他殘留方塊
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                for (int y = baseHeight; y <= baseHeight + 5; y++) {
                    Block block = world.getBlockAt(centerX + x, y, centerZ + z);
                    if (block.getType() != Material.AIR && block.getType() != Material.WATER) {
                        plugin.getLogger().warning("檢測到殘留方塊: " + (centerX + x) + ", " + y + ", " + (centerZ + z) + " - " + block.getType());
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void teleportToSpawn(Player player) {
        Location spawnLocation = getSpawnLocation();
        player.teleport(spawnLocation);
        player.sendMessage("§e你已被傳送到重生點");
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
    }

    private Location getSpawnLocation() {
        World world = Bukkit.getWorld("world");
        if (world != null) {
            Location spawnLocation = world.getSpawnLocation();
            int safeY = findSafeY(spawnLocation.getWorld(), spawnLocation.getBlockX(), spawnLocation.getBlockZ());
            spawnLocation.setY(safeY + 1);
            return spawnLocation;
        }
        return new Location(Bukkit.getWorlds().get(0), 0, 64, 0);
    }

    private int findSafeY(World world, int x, int z) {
        for (int y = world.getMaxHeight() - 1; y >= 0; y--) {
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if (type.isSolid() && type != Material.WATER && type != Material.LAVA) {
                return y;
            }
        }
        return 64;
    }

    public void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupExpiredConfirmations();
                teamManager.cleanupExpiredInvites();
            }
        }.runTaskTimer(plugin, 6000L, 6000L);
    }

    public void listAllRafts(CommandSender sender) {
        if (!sender.hasPermission("raftgen.admin")) {
            sender.sendMessage("§c你沒有權限使用此指令!");
            return;
        }

        if (playerRafts.isEmpty()) {
            sender.sendMessage("§6目前沒有任何木筏記錄");
            return;
        }

        sender.sendMessage("§6=== 所有木筏記錄 ===");
        sender.sendMessage("§a總數: §f" + playerRafts.size());
        sender.sendMessage("§a木筏間距: §f200 格");
        sender.sendMessage("§a木筏大小: §f3x3 木筏");
        sender.sendMessage("§a木筏類型: §f純淨浮島，無裝飾");
        sender.sendMessage("§a海洋生態: §f" + (marineLifeManager != null && marineLifeManager.isEnabled() ? "已啟用海洋生物生成" : "海洋生物生成未啟用"));
        sender.sendMessage("");

        for (UUID playerId : playerRafts.keySet()) {
            if (teamManager.isTeamLeader(playerId) || !teamManager.isInTeam(playerId)) {
                OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerId);
                String playerName = player.getName() != null ? player.getName() : "未知玩家";
                Location loc = playerRafts.get(playerId);
                String raftName = raftNames.get(playerId);

                sender.sendMessage("§e" + playerName + " §7- §f" + raftName);
                sender.sendMessage("  §7位置: §f" + formatLocation(loc));

                if (teamManager.isTeamLeader(playerId)) {
                    sender.sendMessage("  §7隊伍: §f隊長，成員: " + teamManager.getTeamMembers(playerId).size() + " 人");
                }
                sender.sendMessage("");
            }
        }
    }

    public void reloadRaftWorld(Player player) {
        player.sendMessage("§e正在重新載入木筏世界...");
        if (raftWorld != null) {
            String worldName = raftWorld.getName();
            boolean success = Bukkit.unloadWorld(raftWorld, true);
            if (success) {
                player.sendMessage("§a成功卸載世界: " + worldName);
            } else {
                player.sendMessage("§c卸載世界失敗: " + worldName);
            }
            raftWorld = null;
        }
        initializeRaftWorld();
        player.sendMessage("§a木筏世界重新載入完成: " + (raftWorld != null ? raftWorld.getName() : "失敗"));

        // 重新启动海洋生物系统
        if (marineLifeManager != null) {
            marineLifeManager.restart();
            player.sendMessage("§a海洋生物系统已重新启动");
        }
    }

    private void cleanupExpiredConfirmations() {
        long currentTime = System.currentTimeMillis();
        deleteConfirmations.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > 30000
        );
    }

    private void preloadChunks(Location center) {
        World world = center.getWorld();
        int centerChunkX = center.getBlockX() >> 4;
        int centerChunkZ = center.getBlockZ() >> 4;

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                int chunkX = centerChunkX + x;
                int chunkZ = centerChunkZ + z;
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    world.loadChunk(chunkX, chunkZ);
                }
            }
        }
    }

    private String formatLocation(Location loc) {
        return String.format("X: %d, Y: %d, Z: %d", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    private void resetRaftStructures(Location center) {
        World world = center.getWorld();
        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();
        int radius = 5;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = 60; y <= 100; y++) {
                    double distance = Math.sqrt(x * x + z * z);
                    if (distance <= radius) {
                        Block block = world.getBlockAt(centerX + x, y, centerZ + z);
                        if (isPlayerStructure(block.getType())) {
                            block.setType(Material.AIR);
                        }
                    }
                }
            }
        }
    }

    private boolean isPlayerStructure(Material material) {
        return material == Material.CHEST ||
                material == Material.CRAFTING_TABLE ||
                material == Material.FURNACE ||
                material == Material.TORCH ||
                material == Material.OAK_PLANKS;
    }

    // === 數據持久化方法 ===

    /**
     * 加載保存的數據
     */
    public void loadSavedData() {
        DataManager.RaftData raftData = dataManager.loadAllData();

        this.playerRafts.putAll(raftData.playerRafts);
        this.raftNames.putAll(raftData.raftNames);

        // 加載團隊數據到 TeamManager
        teamManager.loadTeamData(raftData.teamMembers);
    }

    /**
     * 保存當前數據
     */
    public void saveData() {
        // 创建空的映射来替代等级相关数据
        Map<UUID, Integer> emptyLevels = new HashMap<>();
        Map<UUID, Double> emptyValues = new HashMap<>();
        Map<UUID, Long> emptyScanTimes = new HashMap<>();

        dataManager.saveAllData(playerRafts, emptyLevels, raftNames, emptyValues, emptyScanTimes, teamManager);
    }

    /**
     * 啟動自動保存
     */
    public void startAutoSave() {
        dataManager.startAutoSave();
    }

    // === API 支持方法 ===

    /**
     * 獲取所有木筏數據 (API使用)
     */
    public Map<UUID, Location> getAllRafts() {
        return new HashMap<>(playerRafts);
    }

    /**
     * 獲取木筏名稱 (API使用)
     */
    public String getRaftName(UUID playerId) {
        return raftNames.getOrDefault(playerId, "未知木筏");
    }

    /**
     * 設置木筏名稱 (API使用)
     */
    public void setRaftName(UUID playerId, String name) {
        if (playerRafts.containsKey(playerId)) {
            raftNames.put(playerId, name);
            saveData(); // 設置名稱後保存數據
        }
    }

    /**
     * 獲取木筏名稱映射 (API使用)
     */
    public Map<UUID, String> getAllRaftNames() {
        return new HashMap<>(raftNames);
    }

    // === 海洋生物系統方法 ===

    /**
     * 獲取海洋生物管理器
     */
    public MarineLifeManager getMarineLifeManager() {
        return marineLifeManager;
    }

    /**
     * 在指定位置生成海洋生物
     */
    public void spawnMarineLifeAtLocation(Location location, int count) {
        if (marineLifeManager != null && marineLifeManager.isEnabled()) {
            marineLifeManager.spawnMarineLifeAround(location, 10, count);
        } else {
            plugin.getLogger().warning("海洋生物系统未启用，无法生成海洋生物");
        }
    }

    // === 基本Getter方法 ===

    public World getRaftWorld() {
        return raftWorld;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public int getRaftCount() {
        return playerRafts.size();
    }

    public boolean hasRaft(UUID playerId) {
        return playerRafts.containsKey(playerId);
    }

    public Location getPlayerRaftLocation(UUID playerId) {
        return playerRafts.get(playerId);
    }

    /**
     * 检查海洋生物系统状态
     */
    public boolean isMarineLifeEnabled() {
        return marineLifeManager != null && marineLifeManager.isEnabled();
    }
}
package me.tleung.raftGen;

// 添加這些導入
import java.util.Map;
import java.util.HashMap;

import me.tleung.raftGen.event.RaftCreateEvent;
import me.tleung.raftGen.event.RaftDeleteEvent;
import me.tleung.raftGen.event.RaftLevelUpEvent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;
import java.util.UUID;
import java.util.Set;

public class RaftManager {

    private final RaftGen plugin;
    private final HashMap<UUID, Location> playerRafts;
    private final HashMap<UUID, Integer> raftLevels;
    private final HashMap<UUID, String> raftNames;
    private final HashMap<UUID, Long> deleteConfirmations;
    private World raftWorld;
    private final Random random;
    private final TeamManager teamManager;

    private final LevelCalculator levelCalculator;
    private final HashMap<UUID, Double> raftValues;
    private final HashMap<UUID, Long> lastScanTime;
    private boolean autoScanEnabled;

    public RaftManager(RaftGen plugin) {
        this.plugin = plugin;
        this.playerRafts = new HashMap<>();
        this.raftLevels = new HashMap<>();
        this.raftNames = new HashMap<>();
        this.deleteConfirmations = new HashMap<>();
        this.raftWorld = null;
        this.random = new Random();
        this.teamManager = new TeamManager(plugin);

        this.levelCalculator = new LevelCalculator();
        this.raftValues = new HashMap<>();
        this.lastScanTime = new HashMap<>();
        this.autoScanEnabled = plugin.getConfig().getBoolean("level.auto-scan-enabled", true);

        initializeRaftWorld();
    }

    private void initializeRaftWorld() {
        String worldName = plugin.getConfig().getString("raft.world-name", "raft_world");
        boolean enableSeparateWorld = plugin.getConfig().getBoolean("raft.enable-separate-world", true);

        if (!enableSeparateWorld) {
            raftWorld = Bukkit.getWorld("world");
            plugin.getLogger().info("使用主世界作為木筏世界");
            return;
        }

        raftWorld = Bukkit.getWorld(worldName);
        if (raftWorld != null) {
            plugin.getLogger().info("木筏世界 '" + worldName + "' 已載入");
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
            }
        } catch (Exception e) {
            plugin.getLogger().warning("創建木筏世界時發生錯誤: " + e.getMessage());
            raftWorld = Bukkit.getWorld("world");
        }
    }

    private void setupWorldRules() {
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
        raftLevels.put(playerId, 1);
        raftNames.put(playerId, player.getName() + "的木筏");

        raftValues.put(playerId, 0.0);
        lastScanTime.put(playerId, System.currentTimeMillis());

        if (teamManager.isTeamLeader(playerId)) {
            Set<UUID> teamMembers = teamManager.getTeamMembers(playerId);
            for (UUID memberId : teamMembers) {
                if (!memberId.equals(playerId)) {
                    playerRafts.put(memberId, raftLocation);
                    raftLevels.put(memberId, 1);
                    raftNames.put(memberId, player.getName() + "的隊伍木筏");
                    raftValues.put(memberId, 0.0);
                    lastScanTime.put(memberId, System.currentTimeMillis());
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
                ensureRaftGenerated(finalRaftLocation);

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
                player.sendMessage("§6木筏等級: §e1");
                player.sendMessage("§6世界: §b" + raftWorld.getName());
                player.sendMessage("§6木筏大小: §e3x3 木筏");

                if (teamManager.isTeamLeader(finalPlayerId)) {
                    player.sendMessage("§6隊伍成員: §e" + (teamManager.getTeamMembers(finalPlayerId).size()) + " 人");
                }

                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.spawnParticle(Particle.HEART, player.getLocation(), 10);

                plugin.getLogger().info("為玩家 " + player.getName() + " 生成木筏於: " + finalRaftLocation.getBlockX() + ", " + finalRaftLocation.getBlockZ());
            }
        }.runTask(plugin);

        return true;
    }

    private void preGenerateRaftArea(Location center) {
        World world = center.getWorld();
        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
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

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Block block = world.getBlockAt(centerX + x, baseHeight, centerZ + z);
                if (block.getType() != Material.OAK_PLANKS) {
                    block.setType(Material.OAK_PLANKS);
                    plugin.getLogger().info("強制生成木筏方塊: " + (centerX + x) + ", " + baseHeight + ", " + (centerZ + z));
                }
            }
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
        Location safeLocation = findSafeTeleportLocation(location);
        giveTemporaryFallProtection(player);
        player.teleport(safeLocation);
        player.sendMessage("§a安全傳送完成!");

        new BukkitRunnable() {
            @Override
            public void run() {
                Location currentLoc = player.getLocation();
                Block belowBlock = currentLoc.getWorld().getBlockAt(
                        currentLoc.getBlockX(),
                        currentLoc.getBlockY() - 1,
                        currentLoc.getBlockZ()
                );

                if (!belowBlock.getType().isSolid()) {
                    Location adjustedLoc = findSafeLocation(currentLoc);
                    player.teleport(adjustedLoc);
                    player.setFallDistance(0f);
                    player.sendMessage("§e位置已調整至安全區域");
                }
                player.setFallDistance(0f);
            }
        }.runTaskLater(plugin, 5L);
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

    private Location findSafeTeleportLocation(Location targetLocation) {
        World world = targetLocation.getWorld();
        int x = targetLocation.getBlockX();
        int z = targetLocation.getBlockZ();

        for (int y = targetLocation.getBlockY(); y <= world.getMaxHeight(); y++) {
            Block standBlock = world.getBlockAt(x, y - 1, z);
            Block feetBlock = world.getBlockAt(x, y, z);
            Block headBlock = world.getBlockAt(x, y + 1, z);

            if (standBlock.getType().isSolid() &&
                    feetBlock.getType() == Material.AIR &&
                    headBlock.getType() == Material.AIR) {
                return new Location(world, x + 0.5, y, z + 0.5, targetLocation.getYaw(), targetLocation.getPitch());
            }
        }

        for (int y = targetLocation.getBlockY(); y >= 60; y--) {
            Block standBlock = world.getBlockAt(x, y - 1, z);
            Block feetBlock = world.getBlockAt(x, y, z);
            Block headBlock = world.getBlockAt(x, y + 1, z);

            if (standBlock.getType().isSolid() &&
                    feetBlock.getType() == Material.AIR &&
                    headBlock.getType() == Material.AIR) {
                return new Location(world, x + 0.5, y, z + 0.5, targetLocation.getYaw(), targetLocation.getPitch());
            }
        }

        return targetLocation.clone().add(0, 5, 0);
    }

    private Location findSafeLocation(Location currentLoc) {
        World world = currentLoc.getWorld();
        int x = currentLoc.getBlockX();
        int z = currentLoc.getBlockZ();

        for (int y = currentLoc.getBlockY(); y >= 60; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (block.getType().isSolid()) {
                return new Location(world, x + 0.5, y + 1, z + 0.5, currentLoc.getYaw(), currentLoc.getPitch());
            }
        }
        return new Location(world, x + 0.5, 63, z + 0.5, currentLoc.getYaw(), currentLoc.getPitch());
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
                player.sendMessage("§a木筏重置完成!");
                player.sendMessage("§6木筏已恢復為純淨地形");

                if (teamManager.isTeamLeader(finalPlayerId)) {
                    teamManager.broadcastToTeam(finalPlayerId, "§a隊伍木筏已重置!");
                }

                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
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
        player.sendMessage("§a等級: §f" + raftLevels.get(targetPlayerId));
        player.sendMessage("§a位置: §f" + formatLocation(raftLoc));
        player.sendMessage("§a世界: §b" + raftLoc.getWorld().getName());
        player.sendMessage("§a大小: §f3x3 木筏");

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
            raftLevels.remove(targetPlayerId);
            raftNames.remove(targetPlayerId);
            raftValues.remove(targetPlayerId);
            lastScanTime.remove(targetPlayerId);

            player.sendMessage("§a已成功刪除玩家 " + targetPlayerName + " 的木筏!");
            if (targetPlayer.isOnline()) {
                targetPlayer.sendMessage("§c你的木筏已被管理員刪除!");
            }
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
                                raftLevels.remove(memberId);
                                raftNames.remove(memberId);
                                raftValues.remove(memberId);
                                lastScanTime.remove(memberId);
                                deleteConfirmations.remove(memberId);
                            }
                            teamManager.broadcastToTeam(finalPlayerId, "§c隊伍木筏已被隊長刪除!");
                        } else {
                            playerRafts.remove(finalTargetPlayerId);
                            raftLevels.remove(finalTargetPlayerId);
                            raftNames.remove(finalTargetPlayerId);
                            raftValues.remove(finalTargetPlayerId);
                            lastScanTime.remove(finalTargetPlayerId);
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
                    raftLevels.remove(playerId);
                    raftNames.remove(playerId);
                    raftValues.remove(playerId);
                    lastScanTime.remove(playerId);

                    sender.sendMessage("§a已成功刪除玩家 " + targetPlayerName + " 的木筏!");
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
        raftLevels.remove(targetPlayerId);
        raftNames.remove(targetPlayerId);
        raftValues.remove(targetPlayerId);
        lastScanTime.remove(targetPlayerId);

        sender.sendMessage("§a已成功刪除玩家 " + targetPlayerName + " 的木筏!");
        if (targetPlayer.isOnline()) {
            targetPlayer.sendMessage("§c你的木筏已被管理員刪除!");
        }
    }

    /**
     * 強制刪除木筏 (API使用)
     */
    public void forceDeleteRaft(UUID playerId) {
        completelyClearRaftArea(playerId);
        playerRafts.remove(playerId);
        raftLevels.remove(playerId);
        raftNames.remove(playerId);
        raftValues.remove(playerId);
        lastScanTime.remove(playerId);
        deleteConfirmations.remove(playerId);
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
            }
        }.runTask(plugin);
    }

    private void completelyClearRaftArea(UUID playerId) {
        if (!playerRafts.containsKey(playerId)) {
            return;
        }

        Location raftLoc = playerRafts.get(playerId);
        World world = raftLoc.getWorld();
        int centerX = raftLoc.getBlockX();
        int centerZ = raftLoc.getBlockZ();
        int baseHeight = 62;

        plugin.getLogger().info("開始完全清除玩家 " + playerId + " 的木筏區域，位置: " + centerX + ", " + centerZ);

        // 擴大清除範圍到 15x15 區域
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                for (int y = baseHeight - 5; y <= baseHeight + 10; y++) {
                    Block block = world.getBlockAt(centerX + x, y, centerZ + z);

                    // 如果是原始木筏位置，設置為水
                    boolean isOriginalRaft = Math.abs(x) <= 1 && Math.abs(z) <= 1 && y == baseHeight;

                    if (isOriginalRaft) {
                        block.setType(Material.WATER);
                    }
                    // 清除木筏上方的所有方塊
                    else if (y >= baseHeight) {
                        if (block.getType() != Material.WATER && block.getType() != Material.AIR) {
                            block.setType(Material.AIR);
                        }
                    }
                    // 確保木筏下方是水
                    else if (y == baseHeight - 1) {
                        if (block.getType() != Material.WATER) {
                            block.setType(Material.WATER);
                        }
                    }
                }
            }
        }

        // 特別處理木筏核心區域
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Block block = world.getBlockAt(centerX + x, baseHeight, centerZ + z);
                // 確保木筏方塊被移除
                if (block.getType() != Material.WATER) {
                    block.setType(Material.WATER);
                }

                // 清除木筏上方的所有方塊
                for (int y = baseHeight + 1; y <= baseHeight + 10; y++) {
                    Block aboveBlock = world.getBlockAt(centerX + x, y, centerZ + z);
                    if (aboveBlock.getType() != Material.AIR && aboveBlock.getType() != Material.WATER) {
                        aboveBlock.setType(Material.AIR);
                    }
                }
            }
        }

        // 強制重新載入區塊以確保客戶端更新
        int chunkX = centerX >> 4;
        int chunkZ = centerZ >> 4;
        world.refreshChunk(chunkX, chunkZ);

        plugin.getLogger().info("完成清除玩家 " + playerId + " 的木筏區域");
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

    public void startAutoScanTask() {
        if (!autoScanEnabled) {
            plugin.getLogger().info("自動等級掃描功能已停用");
            return;
        }

        int scanInterval = plugin.getConfig().getInt("level.auto-scan-interval", 10);
        long intervalTicks = 20L * 60L * scanInterval;

        new BukkitRunnable() {
            @Override
            public void run() {
                autoScanAllRafts();
            }
        }.runTaskTimer(plugin, intervalTicks, intervalTicks);

        plugin.getLogger().info("自動等級掃描任務已啟動，間隔: " + scanInterval + " 分鐘");
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
        sender.sendMessage("");

        for (UUID playerId : playerRafts.keySet()) {
            if (teamManager.isTeamLeader(playerId) || !teamManager.isInTeam(playerId)) {
                OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerId);
                String playerName = player.getName() != null ? player.getName() : "未知玩家";
                Location loc = playerRafts.get(playerId);
                int level = raftLevels.get(playerId);
                String raftName = raftNames.get(playerId);

                sender.sendMessage("§e" + playerName + " §7- §f" + raftName);
                sender.sendMessage("  §7等級: §f" + level + " §7| 位置: §f" + formatLocation(loc));

                if (teamManager.isTeamLeader(playerId)) {
                    sender.sendMessage("  §7隊伍: §f隊長，成員: " + teamManager.getTeamMembers(playerId).size() + " 人");
                }
                sender.sendMessage("");
            }
        }
    }

    public void handleCalculateCommand(Player player) {
        UUID playerId = player.getUniqueId();

        long currentTime = System.currentTimeMillis();
        long lastScan = lastScanTime.getOrDefault(playerId, 0L);
        long cooldown = plugin.getConfig().getLong("level.manual-scan-cooldown", 30000);

        if (currentTime - lastScan < cooldown) {
            long remaining = (cooldown - (currentTime - lastScan)) / 1000;
            player.sendMessage("§c請等待 " + remaining + " 秒後再進行掃描");
            return;
        }

        player.sendMessage("§e正在掃描木筏並計算等級...");
        lastScanTime.put(playerId, currentTime);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            updateRaftLevel(player, false);
        });
    }

    public void sendDetailedStats(Player player) {
        UUID playerId = player.getUniqueId();

        if (!raftValues.containsKey(playerId)) {
            player.sendMessage("§c請先使用 §e/raft calculate §c掃描你的木筏");
            return;
        }

        double currentValue = raftValues.get(playerId);
        int currentLevel = getPlayerRaftLevel(playerId);
        double nextLevelValue = levelCalculator.getValueForNextLevel(currentLevel);
        double neededValue = Math.max(0, nextLevelValue - currentValue);

        player.sendMessage("§6=== 木筏詳細統計 ===");
        player.sendMessage("§a當前等級: §e" + currentLevel);
        player.sendMessage("§a木筏總價值: §e" + String.format("%.1f", currentValue) + " 點");
        player.sendMessage("§a距離下一級: §e" + String.format("%.1f", neededValue) + " 點");
        player.sendMessage("§a木筏大小: §e" + ((getRaftRadius(playerId) * 2) + 1) + "x" + ((getRaftRadius(playerId) * 2) + 1));
        player.sendMessage("§a升級提示: §f放置更有價值的方塊來提升等級!");

        int progress = (int) ((currentValue / nextLevelValue) * 100);
        displayProgressBar(player, progress);

        if (neededValue > 0) {
            player.sendMessage("§e💡 升級建議:");
            player.sendMessage("  §7- 放置 §6鐵塊 §7(+20點)");
            player.sendMessage("  §7- 放置 §b鑽石塊 §7(+50點)");
            player.sendMessage("  §7- 放置 §a綠寶石塊 §7(+40點)");
            player.sendMessage("  §7- 建造 §e附魔台 §7(+15點)");
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
    }

    /**
     * 計算木筏價值 (API使用)
     */
    public double calculateRaftValue(Location location, int radius) {
        return levelCalculator.calculateRaftValue(location, radius);
    }

    private void cleanupExpiredConfirmations() {
        long currentTime = System.currentTimeMillis();
        deleteConfirmations.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > 30000
        );
    }

    private void autoScanAllRafts() {
        int scannedCount = 0;

        for (UUID playerId : playerRafts.keySet()) {
            if (teamManager.isTeamLeader(playerId) || !teamManager.isInTeam(playerId)) {
                scannedCount++;
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null && player.isOnline()) {
                        updateRaftLevel(player, true);
                    }
                });
            }
        }
        plugin.getLogger().info("自動掃描完成: 掃描 " + scannedCount + " 個木筏");
    }

    private void updateRaftLevel(Player player, boolean isAutoScan) {
        UUID playerId = player.getUniqueId();

        if (!playerRafts.containsKey(playerId)) {
            if (!isAutoScan) {
                player.sendMessage("§c你還沒有木筏! 使用 /raft create 創建一個");
            }
            return;
        }

        Location raftLocation = playerRafts.get(playerId);
        int raftRadius = getRaftRadius(playerId);

        double totalValue = levelCalculator.calculateRaftValue(raftLocation, raftRadius);
        raftValues.put(playerId, totalValue);

        int newLevel = levelCalculator.calculateLevel(totalValue);
        int oldLevel = raftLevels.getOrDefault(playerId, 1);

        // 調用等級提升事件
        if (newLevel > oldLevel) {
            RaftLevelUpEvent event = new RaftLevelUpEvent(player, oldLevel, newLevel);
            Bukkit.getPluginManager().callEvent(event);
        }

        raftLevels.put(playerId, newLevel);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (newLevel > oldLevel) {
                onLevelUp(player, oldLevel, newLevel);
            }

            if (!isAutoScan || newLevel > oldLevel) {
                sendLevelUpdateMessage(player, totalValue, newLevel, oldLevel);
            }
        });
    }

    private void onLevelUp(Player player, int oldLevel, int newLevel) {
        player.sendMessage("§6🎉 恭喜! 木筏等級提升! §e" + oldLevel + " → " + newLevel);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        player.spawnParticle(Particle.HEART, player.getLocation(), 30);

        applyLevelBenefits(player, newLevel);

        UUID teamLeaderId = teamManager.getPlayerTeamLeader(player.getUniqueId());
        if (teamLeaderId != null) {
            teamManager.broadcastToTeam(teamLeaderId, "§a隊伍木筏已升級到等級 " + newLevel + "!");
        }
    }

    private void applyLevelBenefits(Player player, int newLevel) {
        switch (newLevel) {
            case 5:
                player.sendMessage("§b✨ 解鎖: 木筏擴大到 7x7!");
                break;
            case 10:
                player.sendMessage("§b✨ 解鎖: 木筏擴大到 9x9!");
                break;
            case 15:
                player.sendMessage("§b✨ 解鎖: 木筏擴大到 11x11!");
                break;
            case 20:
                player.sendMessage("§b✨ 解鎖: 木筏擴大到 13x13!");
                player.sendMessage("§6🎊 恭喜達到最大等級!");
                break;
        }
    }

    private void sendLevelUpdateMessage(Player player, double totalValue, int newLevel, int oldLevel) {
        double nextLevelValue = levelCalculator.getValueForNextLevel(newLevel);
        double currentValue = raftValues.get(player.getUniqueId());
        int progress = (int) ((currentValue / nextLevelValue) * 100);

        player.sendMessage("§6=== 木筏等級資訊 ===");
        player.sendMessage("§a當前等級: §e" + newLevel + " §7(之前: " + oldLevel + ")");
        player.sendMessage("§a木筏價值: §e" + String.format("%.1f", totalValue) + " 點");
        player.sendMessage("§a下一等級: §e" + (newLevel + 1) + " §7(需要: " + String.format("%.1f", nextLevelValue) + " 點)");
        player.sendMessage("§a進度: §e" + progress + "%");
        displayProgressBar(player, progress);
    }

    private void displayProgressBar(Player player, int progress) {
        StringBuilder bar = new StringBuilder("§a[");
        int bars = progress / 5;

        for (int i = 0; i < 20; i++) {
            if (i < bars) {
                bar.append("█");
            } else {
                bar.append("§7█");
            }
        }
        bar.append("§a] §e").append(progress).append("%");
        player.sendMessage(bar.toString());
    }

    private int getRaftRadius(UUID playerId) {
        int level = getPlayerRaftLevel(playerId);
        return 1 + (level / 5);
    }

    private void removeRaftStructures(UUID playerId) {
        if (!playerRafts.containsKey(playerId)) {
            return;
        }

        Location raftLoc = playerRafts.get(playerId);
        World world = raftLoc.getWorld();
        int centerX = raftLoc.getBlockX();
        int centerZ = raftLoc.getBlockZ();
        int baseHeight = 62;
        int radius = 5;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = baseHeight - 10; y <= baseHeight + 20; y++) {
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
        plugin.getLogger().info("已清理玩家 " + playerId + " 的木筏區域");
    }

    private boolean isPlayerStructure(Material material) {
        return material == Material.CHEST ||
                material == Material.CRAFTING_TABLE ||
                material == Material.FURNACE ||
                material == Material.TORCH ||
                material == Material.OAK_PLANKS;
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

    private int findSurfaceHeight(World world, int x, int z, int defaultHeight) {
        for (int y = defaultHeight + 10; y >= 60; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (block.getType() == Material.GRASS_BLOCK || block.getType() == Material.SAND || block.getType() == Material.STONE) {
                return y + 1;
            }
        }
        return defaultHeight;
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

    public int getPlayerRaftLevel(UUID playerId) {
        return raftLevels.getOrDefault(playerId, 0);
    }

    public void setPlayerRaftLevel(UUID playerId, int level) {
        if (playerRafts.containsKey(playerId)) {
            raftLevels.put(playerId, level);
        }
    }

    public boolean upgradeRaft(UUID playerId) {
        if (playerRafts.containsKey(playerId)) {
            int currentLevel = raftLevels.get(playerId);
            raftLevels.put(playerId, currentLevel + 1);
            return true;
        }
        return false;
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
        }
    }

    /**
     * 獲取木筏價值 (API使用)
     */
    public double getRaftValue(UUID playerId) {
        return raftValues.getOrDefault(playerId, 0.0);
    }

    /**
     * 獲取木筏等級映射 (API使用)
     */
    public Map<UUID, Integer> getAllRaftLevels() {
        return new HashMap<>(raftLevels);
    }

    /**
     * 獲取木筏名稱映射 (API使用)
     */
    public Map<UUID, String> getAllRaftNames() {
        return new HashMap<>(raftNames);
    }
}
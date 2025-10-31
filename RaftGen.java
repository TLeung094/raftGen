package me.tleung.raftGen;

import me.tleung.raftGen.api.RaftGenAPI;
import me.tleung.raftGen.api.RaftGenAPIImpl;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class RaftGen extends JavaPlugin implements Listener {

    private static RaftGen instance;
    private RaftGenAPI api;
    private RaftManager raftManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        this.raftManager = new RaftManager(this);

        // 先初始化 raftManager，再創建 API
        this.api = new RaftGenAPIImpl(this);

        // 先加載數據，再註冊其他功能
        raftManager.loadSavedData();

        Objects.requireNonNull(getCommand("raft")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        raftManager.startCleanupTask();
        raftManager.startAutoScanTask();
        raftManager.startAutoSave(); // 啟動自動保存

        getLogger().info("§a木筏生成插件已啟用!");
        getLogger().info("§a加載了 " + raftManager.getRaftCount() + " 個木筏數據");
        getLogger().info("§a團隊數量: " + raftManager.getTeamManager().getTeamCount());
        getLogger().info("§aRaftGen API 已就緒，版本: " + getDescription().getVersion());
    }

    @Override
    public void onDisable() {
        // 插件禁用時保存數據
        if (raftManager != null) {
            raftManager.saveData();
            getLogger().info("§a木筏數據已保存");
        }

        instance = null;
        api = null;
        getLogger().info("§c木筏生成插件已停用!");
    }

    /**
     * 獲取API實例
     */
    public static RaftGenAPI getAPI() {
        if (instance == null) {
            throw new IllegalStateException("RaftGen插件未啟用!");
        }
        return instance.api;
    }

    /**
     * 檢查API是否可用
     */
    public static boolean isAPIEnabled() {
        return instance != null && instance.api != null;
    }

    /**
     * 獲取插件實例
     */
    public static RaftGen getInstance() {
        return instance;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("raft")) {
            return false;
        }

        if (raftManager == null) {
            sender.sendMessage("§c插件尚未完全載入，請稍後再試!");
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player) {
                sendHelpMessage((Player) sender);
            } else {
                sendConsoleHelpMessage(sender);
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();

        try {
            switch (subCommand) {
                case "create":
                    handleCreateCommand(sender);
                    break;
                case "home":
                    handleHomeCommand(sender);
                    break;
                case "reset":
                    handleResetCommand(sender);
                    break;
                case "delete":
                    handleDeleteCommand(sender, args);
                    break;
                case "info":
                    handleInfoCommand(sender);
                    break;
                case "reloadworld":
                    handleReloadWorldCommand(sender);
                    break;
                case "list":
                    handleListCommand(sender);
                    break;
                case "help":
                    handleHelpCommand(sender);
                    break;
                case "version":
                    handleVersionCommand(sender);
                    break;
                case "team":
                    handleTeamCommand(sender, args);
                    break;
                case "calculate":
                case "scan":
                case "update":
                    handleCalculateCommand(sender);
                    break;
                case "stats":
                case "level":
                case "exp":
                    handleStatsCommand(sender);
                    break;
                case "status":
                    handleStatusCommand(sender);
                    break;
                case "api":
                    handleAPICommand(sender);
                    break;
                case "save":
                    handleSaveCommand(sender);
                    break;
                case "reload":
                    handleReloadCommand(sender);
                    break;
                default:
                    sender.sendMessage("§c未知指令! 使用 §a/raft help §c查看可用指令");
            }
        } catch (Exception e) {
            sender.sendMessage("§c執行指令時發生錯誤: " + e.getMessage());
            getLogger().warning("執行指令時發生錯誤: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    private void handleCreateCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以使用此指令!");
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("raftgen.create")) {
            player.sendMessage("§c你沒有權限創建木筏!");
            return;
        }
        raftManager.createRaft(player);
    }

    private void handleHomeCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以使用此指令!");
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("raftgen.home")) {
            player.sendMessage("§c你沒有權限傳送到木筏!");
            return;
        }
        raftManager.teleportToRaft(player);
    }

    private void handleResetCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以使用此指令!");
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("raftgen.reset")) {
            player.sendMessage("§c你沒有權限重置木筏!");
            return;
        }
        raftManager.resetRaft(player);
    }

    private void handleDeleteCommand(CommandSender sender, String[] args) {
        if (args.length == 1) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§c控制台請使用: /raft delete <玩家名稱>");
                return;
            }
            Player player = (Player) sender;
            if (!player.hasPermission("raftgen.delete.self")) {
                player.sendMessage("§c你沒有權限刪除木筏!");
                return;
            }
            raftManager.deleteRaft(player, null);
        } else if (args.length == 2) {
            if (args[1].equalsIgnoreCase("confirm")) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c只有玩家可以使用此指令!");
                    return;
                }
                Player player = (Player) sender;
                raftManager.confirmDeleteRaft(player);
            } else {
                if (!sender.hasPermission("raftgen.delete.others")) {
                    sender.sendMessage("§c你沒有權限刪除其他玩家的木筏!");
                    return;
                }
                String targetPlayerName = args[1];
                raftManager.deleteOtherPlayerRaft(sender, targetPlayerName);
            }
        } else {
            sender.sendMessage("§c用法: /raft delete [confirm|玩家名稱]");
        }
    }

    private void handleInfoCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以使用此指令!");
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("raftgen.info")) {
            player.sendMessage("§c你沒有權限查看木筏資訊!");
            return;
        }
        raftManager.showRaftInfo(player);
    }

    private void handleReloadWorldCommand(CommandSender sender) {
        if (!sender.hasPermission("raftgen.admin")) {
            sender.sendMessage("§c你沒有權限使用此指令!");
            return;
        }
        if (sender instanceof Player) {
            raftManager.reloadRaftWorld((Player) sender);
        } else {
            sender.sendMessage("§e正在重新載入木筏世界...");
            sender.sendMessage("§a木筏世界重新載入功能需要在遊戲內執行!");
        }
    }

    private void handleListCommand(CommandSender sender) {
        if (!sender.hasPermission("raftgen.admin")) {
            sender.sendMessage("§c你沒有權限使用此指令!");
            return;
        }
        raftManager.listAllRafts(sender);
    }

    private void handleHelpCommand(CommandSender sender) {
        if (sender instanceof Player) {
            sendHelpMessage((Player) sender);
        } else {
            sendConsoleHelpMessage(sender);
        }
    }

    private void handleVersionCommand(CommandSender sender) {
        String version = getDescription().getVersion();
        String authors = String.join(", ", getDescription().getAuthors());
        sender.sendMessage("§6=== 木筏插件 ===");
        sender.sendMessage("§a版本: §e" + version);
        sender.sendMessage("§a作者: §e" + authors);
        sender.sendMessage("§a描述: §e" + getDescription().getDescription());
        sender.sendMessage("§aAPI狀態: §e" + (isAPIEnabled() ? "已啟用" : "未啟用"));
        sender.sendMessage("§a木筏數量: §e" + raftManager.getRaftCount());
        sender.sendMessage("§a團隊數量: §e" + raftManager.getTeamManager().getTeamCount());
    }

    private void handleTeamCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以使用此指令!");
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("raftgen.team")) {
            player.sendMessage("§c你沒有權限使用團隊功能!");
            return;
        }
        TeamManager teamManager = raftManager.getTeamManager();

        if (args.length == 1) {
            teamManager.showTeamInfo(player);
            return;
        }

        String subCommand = args[1].toLowerCase();
        switch (subCommand) {
            case "create":
                if (!player.hasPermission("raftgen.team.create")) {
                    player.sendMessage("§c你沒有權限創建隊伍!");
                    return;
                }
                teamManager.createTeam(player);
                break;
            case "invite":
                if (!player.hasPermission("raftgen.team.invite")) {
                    player.sendMessage("§c你沒有權限邀請玩家!");
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage("§c用法: /raft team invite <玩家名稱>");
                    return;
                }
                teamManager.invitePlayer(player, args[2]);
                break;
            case "accept":
                teamManager.acceptInvite(player);
                break;
            case "deny":
                teamManager.denyInvite(player);
                break;
            case "leave":
                teamManager.leaveTeam(player);
                break;
            case "kick":
                if (!player.hasPermission("raftgen.team.kick")) {
                    player.sendMessage("§c你沒有權限踢出玩家!");
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage("§c用法: /raft team kick <玩家名稱>");
                    return;
                }
                teamManager.kickPlayer(player, args[2]);
                break;
            case "disband":
                if (!player.hasPermission("raftgen.team.disband")) {
                    player.sendMessage("§c你沒有權限解散隊伍!");
                    return;
                }
                teamManager.disbandTeam(player);
                break;
            case "info":
                teamManager.showTeamInfo(player);
                break;
            case "transfer":
                if (!player.hasPermission("raftgen.team.transfer")) {
                    player.sendMessage("§c你沒有權限轉移隊長權限!");
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage("§c用法: /raft team transfer <玩家名稱>");
                    return;
                }
                teamManager.transferLeadership(player, args[2]);
                break;
            default:
                player.sendMessage("§c未知的團隊指令!");
                sendTeamHelpMessage(player);
                break;
        }
    }

    private void handleCalculateCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以使用此指令!");
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("raftgen.level.calculate")) {
            player.sendMessage("§c你沒有權限使用此指令!");
            return;
        }
        raftManager.handleCalculateCommand(player);
    }

    private void handleStatsCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以使用此指令!");
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("raftgen.level.stats")) {
            player.sendMessage("§c你沒有權限查看等級資訊!");
            return;
        }
        raftManager.sendDetailedStats(player);
    }

    private void handleStatusCommand(CommandSender sender) {
        if (!sender.hasPermission("raftgen.admin")) {
            sender.sendMessage("§c你沒有權限使用此指令!");
            return;
        }
        sender.sendMessage(getPluginStatus());
    }

    private void handleAPICommand(CommandSender sender) {
        if (!sender.hasPermission("raftgen.admin")) {
            sender.sendMessage("§c你沒有權限使用此指令!");
            return;
        }
        sender.sendMessage("§6=== RaftGen API 資訊 ===");
        sender.sendMessage("§aAPI狀態: §e" + (isAPIEnabled() ? "已啟用" : "未啟用"));
        sender.sendMessage("§a插件版本: §e" + getDescription().getVersion());
        sender.sendMessage("§a木筏數量: §e" + raftManager.getRaftCount());
        sender.sendMessage("§a團隊數量: §e" + raftManager.getTeamManager().getTeamCount());
        sender.sendMessage("§a可用事件:");
        sender.sendMessage("  §7- RaftCreateEvent");
        sender.sendMessage("  §7- RaftLevelUpEvent");
        sender.sendMessage("  §7- RaftDeleteEvent");
        sender.sendMessage("§a其他插件可通過 RaftGen.getAPI() 訪問API");
    }

    private void handleSaveCommand(CommandSender sender) {
        if (!sender.hasPermission("raftgen.admin")) {
            sender.sendMessage("§c你沒有權限使用此指令!");
            return;
        }

        raftManager.saveData();
        sender.sendMessage("§a木筏數據已手動保存!");
        getLogger().info("管理員手動保存了木筏數據");
    }

    private void handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("raftgen.admin")) {
            sender.sendMessage("§c你沒有權限使用此指令!");
            return;
        }

        // 先保存當前數據
        raftManager.saveData();

        // 重新加載配置
        reloadConfig();

        // 重新加載數據
        raftManager.loadSavedData();

        sender.sendMessage("§a插件配置和數據已重新載入!");
        sender.sendMessage("§a木筏數量: §e" + raftManager.getRaftCount());
        sender.sendMessage("§a團隊數量: §e" + raftManager.getTeamManager().getTeamCount());

        getLogger().info("插件配置和數據已重新載入");
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage("§6=== 木筏插件 ===");
        player.sendMessage("§a/raft create §7- 創建一個新木筏");
        player.sendMessage("§a/raft home §7- 傳送到你的木筏");
        player.sendMessage("§a/raft reset §7- 重置你的木筏");
        player.sendMessage("§c/raft delete §7- 刪除你的木筏");
        player.sendMessage("§a/raft info §7- 查看木筏資訊");
        player.sendMessage("§b/raft team §7- 團隊系統");
        player.sendMessage("§e/raft calculate §7- 掃描木筏並更新等級");
        player.sendMessage("§e/raft stats §7- 查看詳細等級資訊");
        player.sendMessage("§a/raft help §7- 顯示此幫助");

        if (player.hasPermission("raftgen.admin")) {
            player.sendMessage("§6=== 管理員指令 ===");
            player.sendMessage("§c/raft delete <玩家名稱> §7- 刪除其他玩家木筏");
            player.sendMessage("§c/raft list §7- 列出所有木筏");
            player.sendMessage("§c/raft reloadworld §7- 重新載入木筏世界");
            player.sendMessage("§a/raft status §7- 查看插件狀態");
            player.sendMessage("§a/raft version §7- 顯示插件版本資訊");
            player.sendMessage("§a/raft api §7- 顯示API資訊");
            player.sendMessage("§a/raft save §7- 手動保存數據");
            player.sendMessage("§a/raft reload §7- 重新載入配置和數據");
        }
    }

    private void sendTeamHelpMessage(Player player) {
        player.sendMessage("§6=== 團隊指令幫助 ===");
        player.sendMessage("§b/raft team §7- 查看隊伍資訊");
        player.sendMessage("§b/raft team create §7- 創建隊伍");
        player.sendMessage("§b/raft team invite <玩家> §7- 邀請玩家");
        player.sendMessage("§b/raft team accept §7- 接受邀請");
        player.sendMessage("§b/raft team deny §7- 拒絕邀請");
        player.sendMessage("§b/raft team leave §7- 離開隊伍");
        player.sendMessage("§b/raft team kick <玩家> §7- 踢出隊員");
        player.sendMessage("§b/raft team disband §7- 解散隊伍");
        player.sendMessage("§b/raft team info §7- 隊伍資訊");

        if (player.hasPermission("raftgen.team.transfer")) {
            player.sendMessage("§b/raft team transfer <玩家> §7- 轉移隊長權限");
        }
    }

    private void sendConsoleHelpMessage(CommandSender sender) {
        sender.sendMessage("§6=== 控制台指令 ===");
        sender.sendMessage("§a/raft delete <玩家名稱> §7- 刪除指定玩家的木筏");
        sender.sendMessage("§a/raft list §7- 列出所有木筏");
        sender.sendMessage("§a/raft reloadworld §7- 重新載入木筏世界");
        sender.sendMessage("§a/raft status §7- 查看插件狀態");
        sender.sendMessage("§a/raft version §7- 顯示插件版本資訊");
        sender.sendMessage("§a/raft api §7- 顯示API資訊");
        sender.sendMessage("§a/raft save §7- 手動保存數據");
        sender.sendMessage("§a/raft reload §7- 重新載入配置和數據");
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        // 預留方塊放置事件處理
        // 可以在這裡添加木筏區域的方塊放置限制
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        getLogger().info("為世界 '" + worldName + "' 創建 RaftChunkGenerator");
        return new RaftChunkGenerator(this);
    }

    public RaftManager getRaftManager() {
        return raftManager;
    }

    public String getPluginStatus() {
        StringBuilder status = new StringBuilder();
        status.append("§6=== 插件狀態 ===\n");
        status.append("§a版本: §e").append(getDescription().getVersion()).append("\n");
        status.append("§a狀態: §e運行中\n");
        status.append("§aAPI狀態: §e").append(isAPIEnabled() ? "已啟用" : "未啟用").append("\n");

        if (raftManager != null) {
            World raftWorld = raftManager.getRaftWorld();
            status.append("§a木筏世界: §e").append(raftWorld != null ? raftWorld.getName() : "未載入").append("\n");
            status.append("§a木筏數量: §e").append(raftManager.getRaftCount()).append("\n");
            status.append("§a團隊數量: §e").append(raftManager.getTeamManager().getTeamCount()).append("\n");
            status.append("§a自動掃描: §e").append(raftManager.isAutoScanEnabled() ? "啟用" : "停用").append("\n");

            // 添加配置信息
            status.append("§a木筏間距: §e").append(getConfig().getInt("raft.spacing", 200)).append(" 格\n");
            status.append("§a掃描間隔: §e").append(getConfig().getInt("level.auto-scan-interval", 10)).append(" 分鐘\n");
        }

        // 添加數據持久化狀態
        status.append("§a數據持久化: §e已啟用\n");
        status.append("§a自動保存: §e每5分鐘\n");

        return status.toString();
    }

    public int getRaftCount() {
        if (raftManager != null) {
            return raftManager.getRaftCount();
        }
        return 0;
    }

    /**
     * 獲取團隊數量
     */
    public int getTeamCount() {
        if (raftManager != null) {
            return raftManager.getTeamManager().getTeamCount();
        }
        return 0;
    }

    /**
     * 手動觸發數據保存（用於測試或緊急情況）
     */
    public void manualSave() {
        if (raftManager != null) {
            raftManager.saveData();
            getLogger().info("手動觸發數據保存完成");
        }
    }

    /**
     * 重新加載所有數據
     */
    public void reloadAllData() {
        if (raftManager != null) {
            // 先保存當前數據
            raftManager.saveData();

            // 重新加載配置
            reloadConfig();

            // 重新加載數據
            raftManager.loadSavedData();

            getLogger().info("所有數據已重新載入");
        }
    }

    /**
     * 檢查插件健康狀態
     */
    public boolean isHealthy() {
        return instance != null &&
                raftManager != null &&
                api != null &&
                Bukkit.getScheduler().isQueued(raftManager.hashCode());
    }

    /**
     * 獲取插件診斷信息
     */
    public String getDiagnostics() {
        StringBuilder diag = new StringBuilder();
        diag.append("§6=== 插件診斷 ===\n");
        diag.append("§a實例狀態: §e").append(instance != null ? "正常" : "異常").append("\n");
        diag.append("§aAPI狀態: §e").append(api != null ? "正常" : "異常").append("\n");
        diag.append("§a管理器狀態: §e").append(raftManager != null ? "正常" : "異常").append("\n");

        if (raftManager != null) {
            diag.append("§a木筏世界: §e").append(raftManager.getRaftWorld() != null ? "已載入" : "未載入").append("\n");
            diag.append("§a數據完整性: §e").append(raftManager.getRaftCount() >= 0 ? "正常" : "異常").append("\n");
        }

        diag.append("§a調度器狀態: §e").append(Bukkit.getScheduler().isQueued(raftManager.hashCode()) ? "運行中" : "停止").append("\n");

        return diag.toString();
    }
}
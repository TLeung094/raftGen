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
    private MarineLifeManager marineLifeManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        // 初始化 RaftManager
        this.raftManager = new RaftManager(this);

        // 先加载数据，确保 raftManager 完全初始化
        if (raftManager != null) {
            raftManager.loadSavedData();
            getLogger().info("§a木筏管理器初始化完成，加载了 " + raftManager.getRaftCount() + " 個木筏數據");
        } else {
            getLogger().severe("§c木筏管理器初始化失败!");
            return;
        }

        // 然后再创建 MarineLifeManager
        this.marineLifeManager = new MarineLifeManager(this);

        // 先初始化 raftManager，再創建 API
        this.api = new RaftGenAPIImpl(this);

        Objects.requireNonNull(getCommand("raft")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);

        // 启动清理任务和自动保存
        if (raftManager != null) {
            raftManager.startCleanupTask();
            raftManager.startAutoSave(); // 啟動自動保存
        }

        getLogger().info("§a木筏生成插件已啟用!");
        getLogger().info("§a加載了 " + raftManager.getRaftCount() + " 個木筏數據");
        getLogger().info("§a團隊數量: " + raftManager.getTeamManager().getTeamCount());

        // 检查海洋生物系统状态
        if (marineLifeManager != null && marineLifeManager.isEnabled()) {
            getLogger().info("§a海洋生物系統已加載!");
        } else {
            getLogger().warning("§c海洋生物系統未正常啟動!");
        }

        getLogger().info("§aRaftGen API 已就緒，版本: " + getDescription().getVersion());

        // 延迟检查插件健康状态
        Bukkit.getScheduler().runTaskLater(this, this::checkPluginHealth, 100L); // 5秒后检查
    }

    @Override
    public void onDisable() {
        // 插件禁用時保存數據
        if (raftManager != null) {
            raftManager.saveData();
            getLogger().info("§a木筏數據已保存");
        }

        // 禁用海洋生物系统
        if (marineLifeManager != null) {
            marineLifeManager.setEnabled(false);
            getLogger().info("§a海洋生物系統已禁用");
        }

        instance = null;
        api = null;
        getLogger().info("§c木筏生成插件已停用!");
    }

    /**
     * 检查插件健康状态
     */
    private void checkPluginHealth() {
        getLogger().info("§6=== 插件健康检查 ===");
        getLogger().info("§aRaftManager: " + (raftManager != null ? "正常" : "异常"));

        if (raftManager != null) {
            getLogger().info("§a木筏世界: " + (raftManager.getRaftWorld() != null ? "已加载" : "未加载"));
            getLogger().info("§a木筏数量: " + raftManager.getRaftCount());
        }

        getLogger().info("§aMarineLifeManager: " + (marineLifeManager != null ? "正常" : "异常"));

        if (marineLifeManager != null) {
            getLogger().info("§a海洋系统状态: " + (marineLifeManager.isEnabled() ? "已启用" : "未启用"));
            getLogger().info("§a海洋系统初始化: " + (marineLifeManager.isInitialized() ? "已初始化" : "未初始化"));
            getLogger().info("§a海洋生物数量: " + marineLifeManager.getActiveMarineLifeCount());
        }

        getLogger().info("§aAPI: " + (api != null ? "正常" : "异常"));
        getLogger().info("§6=== 健康检查完成 ===");
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
                case "marine":
                    handleMarineCommand(sender, args);
                    break;
                case "health":
                    handleHealthCommand(sender);
                    break;
                case "diagnose":
                    handleDiagnoseCommand(sender);
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
        sender.sendMessage("§a海洋生物: §e" + (marineLifeManager != null && marineLifeManager.isEnabled() ? "已啟用" : "未啟用"));
        if (marineLifeManager != null) {
            sender.sendMessage("§a海洋生物數量: §e" + marineLifeManager.getActiveMarineLifeCount());
        }
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
        sender.sendMessage("§a海洋生物系統: §e" + (marineLifeManager != null && marineLifeManager.isEnabled() ? "已啟用" : "未啟用"));
        if (marineLifeManager != null) {
            sender.sendMessage("§a海洋生物數量: §e" + marineLifeManager.getActiveMarineLifeCount());
            sender.sendMessage("§a海洋系統初始化: §e" + (marineLifeManager.isInitialized() ? "已初始化" : "未初始化"));
        }
        sender.sendMessage("§a可用事件:");
        sender.sendMessage("  §7- RaftCreateEvent");
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

        // 重新啟動海洋生物系統
        if (marineLifeManager != null) {
            marineLifeManager.restart();
        }

        sender.sendMessage("§a插件配置和數據已重新載入!");
        sender.sendMessage("§a木筏數量: §e" + raftManager.getRaftCount());
        sender.sendMessage("§a團隊數量: §e" + raftManager.getTeamManager().getTeamCount());
        sender.sendMessage("§a海洋生物系統: §e已重新載入");

        getLogger().info("插件配置和數據已重新載入");
    }

    private void handleMarineCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("raftgen.admin")) {
            sender.sendMessage("§c你沒有權限使用此指令!");
            return;
        }

        if (args.length == 1) {
            // 顯示海洋生物系統狀態
            if (marineLifeManager != null) {
                sender.sendMessage(marineLifeManager.getStatusInfo());
            } else {
                sender.sendMessage("§c海洋生物管理器未初始化!");
            }
            sender.sendMessage("§a用法:");
            sender.sendMessage("§e/raft marine spawn §7- 在您周圍生成海洋生物");
            sender.sendMessage("§e/raft marine info §7- 顯示詳細信息");
            sender.sendMessage("§e/raft marine clear §7- 清理所有海洋生物");
            sender.sendMessage("§e/raft marine stats §7- 顯示海洋生物統計");
            sender.sendMessage("§e/raft marine restart §7- 重新啟動海洋生物系統");
            sender.sendMessage("§e/raft marine reinit §7- 強制重新初始化系統");
            sender.sendMessage("§e/raft marine enable §7- 啟用海洋生物系統");
            sender.sendMessage("§e/raft marine disable §7- 禁用海洋生物系統");
            sender.sendMessage("§e/raft marine debug §7- 調試生成海洋生物");
            sender.sendMessage("§e/raft marine status §7- 顯示系統狀態");
            return;
        }

        String subCommand = args[1].toLowerCase();
        switch (subCommand) {
            case "spawn":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c只有玩家可以使用此指令!");
                    return;
                }
                Player player = (Player) sender;
                if (marineLifeManager != null && marineLifeManager.isEnabled()) {
                    marineLifeManager.spawnMarineLifeAround(player.getLocation(), 15, 10);
                    player.sendMessage("§a已在您周圍生成海洋生物!");
                } else {
                    player.sendMessage("§c海洋生物系統未啟用!");
                }
                break;
            case "info":
                if (marineLifeManager != null) {
                    sender.sendMessage("§6=== 海洋生物詳細信息 ===");
                    sender.sendMessage("§a活躍生物數量: §e" + marineLifeManager.getActiveMarineLifeCount());
                    sender.sendMessage("§a生成間隔: §e" + getConfig().getInt("marine-life.spawn-interval", 200) + " ticks");
                    sender.sendMessage("§a最大生成數量: §e" + getConfig().getInt("marine-life.max-near-player", 10));
                    sender.sendMessage("§a系統狀態: §e" + (marineLifeManager.isEnabled() ? "已啟用" : "未啟用"));
                    sender.sendMessage("§a初始化狀態: §e" + (marineLifeManager.isInitialized() ? "已初始化" : "未初始化"));

                    // 顯示海洋生物分佈
                    sender.sendMessage("§a海洋生物分佈:");
                    marineLifeManager.getMarineLifeDistribution().forEach((type, count) -> {
                        sender.sendMessage("  §7- " + type.name() + ": §e" + count);
                    });
                } else {
                    sender.sendMessage("§c海洋生物系統未啟用!");
                }
                break;
            case "clear":
                if (marineLifeManager != null) {
                    marineLifeManager.clearAllMarineLife();
                    sender.sendMessage("§a已清理所有海洋生物!");
                } else {
                    sender.sendMessage("§c海洋生物系統未啟用!");
                }
                break;
            case "stats":
                if (marineLifeManager != null) {
                    sender.sendMessage("§6=== 海洋生物統計 ===");
                    marineLifeManager.getMarineLifeDistribution().forEach((type, count) -> {
                        sender.sendMessage("§a" + type.name() + ": §e" + count);
                    });
                } else {
                    sender.sendMessage("§c海洋生物系統未啟用!");
                }
                break;
            case "restart":
                if (marineLifeManager != null) {
                    marineLifeManager.restart();
                    sender.sendMessage("§a海洋生物系統正在重新啟動...");
                } else {
                    sender.sendMessage("§c海洋生物系統未初始化!");
                }
                break;
            case "reinit":
                if (marineLifeManager != null) {
                    marineLifeManager.reinitialize();
                    sender.sendMessage("§a海洋生物系統正在強制重新初始化...");
                } else {
                    sender.sendMessage("§c海洋生物系統未初始化!");
                }
                break;
            case "enable":
                if (marineLifeManager != null) {
                    marineLifeManager.setEnabled(true);
                    sender.sendMessage("§a海洋生物系統已啟用!");
                } else {
                    sender.sendMessage("§c海洋生物系統未初始化!");
                }
                break;
            case "disable":
                if (marineLifeManager != null) {
                    marineLifeManager.setEnabled(false);
                    sender.sendMessage("§a海洋生物系統已禁用!");
                } else {
                    sender.sendMessage("§c海洋生物系統未初始化!");
                }
                break;
            case "debug":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c只有玩家可以使用此指令!");
                    return;
                }
                Player debugPlayer = (Player) sender;
                if (marineLifeManager != null) {
                    marineLifeManager.debugSpawnMarineLife(debugPlayer.getLocation(), 5);
                    debugPlayer.sendMessage("§a已執行海洋生物調試生成，請查看控制台日誌");
                } else {
                    debugPlayer.sendMessage("§c海洋生物管理器未初始化!");
                }
                break;
            case "status":
                if (marineLifeManager != null) {
                    sender.sendMessage(marineLifeManager.getStatusInfo());
                } else {
                    sender.sendMessage("§c海洋生物管理器未初始化!");
                }
                break;
            default:
                sender.sendMessage("§c未知的海洋生物指令!");
                break;
        }
    }

    private void handleHealthCommand(CommandSender sender) {
        if (!sender.hasPermission("raftgen.admin")) {
            sender.sendMessage("§c你沒有權限使用此指令!");
            return;
        }
        sender.sendMessage(getDiagnostics());
    }

    private void handleDiagnoseCommand(CommandSender sender) {
        if (!sender.hasPermission("raftgen.admin")) {
            sender.sendMessage("§c你沒有權限使用此指令!");
            return;
        }

        sender.sendMessage("§6=== 插件診斷報告 ===");
        sender.sendMessage(getDiagnostics());

        // 執行修復建議
        if (raftManager == null) {
            sender.sendMessage("§cRaftManager 為 null，嘗試重新初始化...");
            this.raftManager = new RaftManager(this);
            if (raftManager != null) {
                sender.sendMessage("§aRaftManager 重新初始化成功");
            } else {
                sender.sendMessage("§cRaftManager 重新初始化失敗");
            }
        }

        if (marineLifeManager == null) {
            sender.sendMessage("§cMarineLifeManager 為 null，嘗試重新初始化...");
            this.marineLifeManager = new MarineLifeManager(this);
            if (marineLifeManager != null) {
                sender.sendMessage("§aMarineLifeManager 重新初始化成功");
            } else {
                sender.sendMessage("§cMarineLifeManager 重新初始化失敗");
            }
        }

        if (raftManager != null && raftManager.getRaftWorld() == null) {
            sender.sendMessage("§c木筏世界為 null，嘗試重新初始化...");
            raftManager.initializeRaftWorld();
            if (raftManager.getRaftWorld() != null) {
                sender.sendMessage("§a木筏世界重新初始化成功");
            } else {
                sender.sendMessage("§c木筏世界重新初始化失敗");
            }
        }

        if (marineLifeManager != null && !marineLifeManager.isEnabled()) {
            sender.sendMessage("§c海洋生物系統未啟用，嘗試啟用...");
            marineLifeManager.setEnabled(true);
            if (marineLifeManager.isEnabled()) {
                sender.sendMessage("§a海洋生物系統啟用成功");
            } else {
                sender.sendMessage("§c海洋生物系統啟用失敗");
            }
        }

        sender.sendMessage("§a診斷完成!");
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage("§6=== 木筏插件 ===");
        player.sendMessage("§a/raft create §7- 創建一個新木筏");
        player.sendMessage("§a/raft home §7- 傳送到你的木筏");
        player.sendMessage("§a/raft reset §7- 重置你的木筏");
        player.sendMessage("§c/raft delete §7- 刪除你的木筏");
        player.sendMessage("§a/raft info §7- 查看木筏資訊");
        player.sendMessage("§b/raft team §7- 團隊系統");
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
            player.sendMessage("§b/raft marine §7- 海洋生物管理");
            player.sendMessage("§a/raft health §7- 插件健康檢查");
            player.sendMessage("§a/raft diagnose §7- 插件診斷與修復");
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
        sender.sendMessage("§a/raft marine §7- 海洋生物管理");
        sender.sendMessage("§a/raft health §7- 插件健康檢查");
        sender.sendMessage("§a/raft diagnose §7- 插件診斷與修復");
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

    public MarineLifeManager getMarineLifeManager() {
        return marineLifeManager;
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

            // 添加配置信息
            status.append("§a木筏間距: §e").append(getConfig().getInt("raft.spacing", 200)).append(" 格\n");
        }

        // 添加海洋生物狀態
        status.append("§a海洋生物生成: §e").append(getConfig().getBoolean("marine-life.enabled", true) ? "已啟用" : "已禁用").append("\n");
        if (marineLifeManager != null) {
            status.append("§a活躍海洋生物: §e").append(marineLifeManager.getActiveMarineLifeCount()).append("\n");
            status.append("§a海洋系統狀態: §e").append(marineLifeManager.isEnabled() ? "已啟用" : "已禁用").append("\n");
            status.append("§a海洋系統初始化: §e").append(marineLifeManager.isInitialized() ? "已初始化" : "未初始化").append("\n");
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
                marineLifeManager != null;
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
        diag.append("§a海洋生物管理器: §e").append(marineLifeManager != null ? "正常" : "異常").append("\n");

        if (raftManager != null) {
            diag.append("§a木筏世界: §e").append(raftManager.getRaftWorld() != null ? "已載入" : "未載入").append("\n");
            diag.append("§a數據完整性: §e").append(raftManager.getRaftCount() >= 0 ? "正常" : "異常").append("\n");
            diag.append("§a海洋生物系統狀態: §e").append(raftManager.isMarineLifeEnabled() ? "已啟用" : "未啟用").append("\n");
        }

        if (marineLifeManager != null) {
            diag.append("§a海洋系統狀態: §e").append(marineLifeManager.isEnabled() ? "已啟用" : "已禁用").append("\n");
            diag.append("§a海洋系統初始化: §e").append(marineLifeManager.isInitialized() ? "已初始化" : "未初始化").append("\n");
            diag.append("§a海洋生物數量: §e").append(marineLifeManager.getActiveMarineLifeCount()).append("\n");
        }

        diag.append("§a調度器狀態: §e").append(Bukkit.getScheduler().isCurrentlyRunning(raftManager != null ? raftManager.hashCode() : 0) ? "運行中" : "停止").append("\n");

        // 添加修復建議
        diag.append("§6=== 修復建議 ===\n");
        if (raftManager == null) {
            diag.append("§c- RaftManager 為 null，使用 §e/raft diagnose §c進行修復\n");
        }
        if (marineLifeManager == null) {
            diag.append("§c- MarineLifeManager 為 null，使用 §e/raft diagnose §c進行修復\n");
        }
        if (raftManager != null && raftManager.getRaftWorld() == null) {
            diag.append("§c- 木筏世界未載入，使用 §e/raft diagnose §c進行修復\n");
        }
        if (marineLifeManager != null && !marineLifeManager.isEnabled()) {
            diag.append("§c- 海洋生物系統未啟用，使用 §e/raft marine enable §c啟用\n");
        }
        if (marineLifeManager != null && !marineLifeManager.isInitialized()) {
            diag.append("§c- 海洋生物系統未初始化，使用 §e/raft marine reinit §c重新初始化\n");
        }

        return diag.toString();
    }
}
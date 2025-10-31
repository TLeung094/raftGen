package me.tleung.raftGen;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class DataManager {
    private final RaftGen plugin;
    private File dataFile;
    private FileConfiguration dataConfig;

    public DataManager(RaftGen plugin) {
        this.plugin = plugin;
        setupDataFile();
    }

    private void setupDataFile() {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            plugin.saveResource("data.yml", false);
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    /**
     * 保存所有木筏數據
     */
    public void saveAllData(Map<UUID, Location> playerRafts,
                            Map<UUID, Integer> raftLevels,
                            Map<UUID, String> raftNames,
                            Map<UUID, Double> raftValues,
                            Map<UUID, Long> lastScanTime,
                            TeamManager teamManager) {
        try {
            // 清除舊數據
            dataConfig.set("rafts", null);
            dataConfig.set("teams", null);

            // 保存木筏數據
            for (UUID playerId : playerRafts.keySet()) {
                String path = "rafts." + playerId.toString();

                Location loc = playerRafts.get(playerId);
                dataConfig.set(path + ".location.world", loc.getWorld().getName());
                dataConfig.set(path + ".location.x", loc.getX());
                dataConfig.set(path + ".location.y", loc.getY());
                dataConfig.set(path + ".location.z", loc.getZ());

                dataConfig.set(path + ".level", raftLevels.get(playerId));
                dataConfig.set(path + ".name", raftNames.get(playerId));
                dataConfig.set(path + ".value", raftValues.get(playerId));
                dataConfig.set(path + ".lastScan", lastScanTime.get(playerId));
            }

            // 保存團隊數據
            saveTeamData(teamManager);

            dataConfig.save(dataFile);
            plugin.getLogger().info("木筏數據已保存: " + playerRafts.size() + " 個木筏");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "保存木筏數據時發生錯誤", e);
        }
    }

    /**
     * 保存團隊數據
     */
    private void saveTeamData(TeamManager teamManager) {
        // 保存團隊結構
        Set<UUID> processedTeams = new HashSet<>();

        for (UUID playerId : teamManager.getAllPlayersInTeams()) {
            UUID leaderId = teamManager.getPlayerTeamLeader(playerId);
            if (leaderId != null && !processedTeams.contains(leaderId)) {
                String teamPath = "teams." + leaderId.toString();

                // 保存團隊成員
                Set<UUID> members = teamManager.getTeamMembers(leaderId);
                List<String> memberList = new ArrayList<>();
                for (UUID memberId : members) {
                    memberList.add(memberId.toString());
                }
                dataConfig.set(teamPath + ".members", memberList);

                processedTeams.add(leaderId);
            }
        }
    }

    /**
     * 加載所有數據
     */
    public RaftData loadAllData() {
        RaftData raftData = new RaftData();

        // 加載木筏數據
        if (dataConfig.isConfigurationSection("rafts")) {
            for (String playerIdStr : dataConfig.getConfigurationSection("rafts").getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(playerIdStr);
                    String path = "rafts." + playerIdStr;

                    // 加載位置
                    String worldName = dataConfig.getString(path + ".location.world");
                    double x = dataConfig.getDouble(path + ".location.x");
                    double y = dataConfig.getDouble(path + ".location.y");
                    double z = dataConfig.getDouble(path + ".location.z");

                    Location location = new Location(Bukkit.getWorld(worldName), x, y, z);

                    // 加載其他數據
                    int level = dataConfig.getInt(path + ".level", 1);
                    String name = dataConfig.getString(path + ".name", "木筏");
                    double value = dataConfig.getDouble(path + ".value", 0.0);
                    long lastScan = dataConfig.getLong(path + ".lastScan", 0);

                    raftData.playerRafts.put(playerId, location);
                    raftData.raftLevels.put(playerId, level);
                    raftData.raftNames.put(playerId, name);
                    raftData.raftValues.put(playerId, value);
                    raftData.lastScanTime.put(playerId, lastScan);

                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("無效的UUID格式: " + playerIdStr);
                }
            }
        }

        // 加載團隊數據
        loadTeamData(raftData);

        plugin.getLogger().info("木筏數據加載完成: " + raftData.playerRafts.size() + " 個木筏");
        return raftData;
    }

    /**
     * 加載團隊數據
     */
    private void loadTeamData(RaftData raftData) {
        if (dataConfig.isConfigurationSection("teams")) {
            for (String leaderIdStr : dataConfig.getConfigurationSection("teams").getKeys(false)) {
                try {
                    UUID leaderId = UUID.fromString(leaderIdStr);
                    String path = "teams." + leaderIdStr;

                    List<String> memberList = dataConfig.getStringList(path + ".members");
                    for (String memberIdStr : memberList) {
                        try {
                            UUID memberId = UUID.fromString(memberIdStr);
                            raftData.teamMembers.put(memberId, leaderId);
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("無效的團隊成員UUID: " + memberIdStr);
                        }
                    }

                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("無效的隊長UUID: " + leaderIdStr);
                }
            }
        }
    }

    /**
     * 定時保存數據
     */
    public void startAutoSave() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            saveAllData(
                    plugin.getRaftManager().getAllRafts(),
                    plugin.getRaftManager().getAllRaftLevels(),
                    plugin.getRaftManager().getAllRaftNames(),
                    plugin.getRaftManager().getAllRaftValues(),
                    plugin.getRaftManager().getAllLastScanTimes(),
                    plugin.getRaftManager().getTeamManager()
            );
        }, 20L * 60 * 5, 20L * 60 * 5); // 每5分鐘自動保存
    }

    /**
     * 數據容器類
     */
    public static class RaftData {
        public Map<UUID, Location> playerRafts = new HashMap<>();
        public Map<UUID, Integer> raftLevels = new HashMap<>();
        public Map<UUID, String> raftNames = new HashMap<>();
        public Map<UUID, Double> raftValues = new HashMap<>();
        public Map<UUID, Long> lastScanTime = new HashMap<>();
        public Map<UUID, UUID> teamMembers = new HashMap<>(); // 玩家ID -> 隊長ID
    }
}
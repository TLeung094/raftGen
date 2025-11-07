// DataManager.java
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
     * 保存所有木筏数据 - 移除等级相关数据
     */
    public void saveAllData(Map<UUID, Location> playerRafts,
                            Map<UUID, String> raftNames,
                            TeamManager teamManager) {
        try {
            // 清除旧数据
            dataConfig.set("rafts", null);
            dataConfig.set("teams", null);

            // 保存木筏数据 - 只保存位置和名称
            for (UUID playerId : playerRafts.keySet()) {
                String path = "rafts." + playerId.toString();

                Location loc = playerRafts.get(playerId);
                dataConfig.set(path + ".location.world", loc.getWorld().getName());
                dataConfig.set(path + ".location.x", loc.getX());
                dataConfig.set(path + ".location.y", loc.getY());
                dataConfig.set(path + ".location.z", loc.getZ());

                dataConfig.set(path + ".name", raftNames.get(playerId));
            }

            // 保存团队数据
            saveTeamData(teamManager);

            dataConfig.save(dataFile);
            plugin.getLogger().info("木筏数据已保存: " + playerRafts.size() + " 个木筏");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "保存木筏数据时发生错误", e);
        }
    }

    /**
     * 保存团队数据
     */
    private void saveTeamData(TeamManager teamManager) {
        // 保存团队结构
        Set<UUID> processedTeams = new HashSet<>();

        for (UUID playerId : teamManager.getAllPlayersInTeams()) {
            UUID leaderId = teamManager.getPlayerTeamLeader(playerId);
            if (leaderId != null && !processedTeams.contains(leaderId)) {
                String teamPath = "teams." + leaderId.toString();

                // 保存团队成员
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
     * 加载所有数据 - 移除等级相关数据
     */
    public RaftData loadAllData() {
        RaftData raftData = new RaftData();

        // 加载木筏数据
        if (dataConfig.isConfigurationSection("rafts")) {
            for (String playerIdStr : dataConfig.getConfigurationSection("rafts").getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(playerIdStr);
                    String path = "rafts." + playerIdStr;

                    // 加载位置
                    String worldName = dataConfig.getString(path + ".location.world");
                    double x = dataConfig.getDouble(path + ".location.x");
                    double y = dataConfig.getDouble(path + ".location.y");
                    double z = dataConfig.getDouble(path + ".location.z");

                    Location location = new Location(Bukkit.getWorld(worldName), x, y, z);

                    // 加载名称
                    String name = dataConfig.getString(path + ".name", "木筏");

                    raftData.playerRafts.put(playerId, location);
                    raftData.raftNames.put(playerId, name);

                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("无效的UUID格式: " + playerIdStr);
                }
            }
        }

        // 加载团队数据
        loadTeamData(raftData);

        plugin.getLogger().info("木筏数据加载完成: " + raftData.playerRafts.size() + " 个木筏");
        return raftData;
    }

    /**
     * 加载团队数据
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
                            plugin.getLogger().warning("无效的团队成员UUID: " + memberIdStr);
                        }
                    }

                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("无效的队长UUID: " + leaderIdStr);
                }
            }
        }
    }

    /**
     * 定时保存数据
     */
    public void startAutoSave() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            saveAllData(
                    plugin.getRaftManager().getAllRafts(),
                    plugin.getRaftManager().getAllRaftNames(),
                    plugin.getRaftManager().getTeamManager()
            );
        }, 20L * 60 * 5, 20L * 60 * 5); // 每5分钟自动保存
    }

    /**
     * 数据容器类
     */
    public static class RaftData {
        public Map<UUID, Location> playerRafts = new HashMap<>();
        public Map<UUID, String> raftNames = new HashMap<>();
        public Map<UUID, UUID> teamMembers = new HashMap<>(); // 玩家ID -> 队长ID
    }
}
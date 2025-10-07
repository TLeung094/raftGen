package me.tleung.raftGen.api;

import me.tleung.raftGen.RaftGen;
import me.tleung.raftGen.RaftManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class RaftGenAPIImpl implements RaftGenAPI {
    private final RaftGen plugin;
    private final RaftManager raftManager;

    public RaftGenAPIImpl(@NotNull RaftGen plugin) {
        this.plugin = plugin;
        this.raftManager = plugin.getRaftManager();
    }

    @NotNull
    @Override
    public Plugin getPlugin() {
        return plugin;
    }

    @Override
    public boolean createRaft(@NotNull Player player, @Nullable Location location) {
        if (location != null) {
            return raftManager.createRaftAtLocation(player, location);
        }
        raftManager.createRaft(player);
        return true;
    }

    @Nullable
    @Override
    public Location getRaftLocation(@NotNull UUID playerId) {
        return raftManager.getPlayerRaftLocation(playerId);
    }

    @Override
    public int getRaftLevel(@NotNull UUID playerId) {
        return raftManager.getPlayerRaftLevel(playerId);
    }

    @Override
    public boolean setRaftLevel(@NotNull UUID playerId, int level) {
        if (level < 1) return false;
        raftManager.setPlayerRaftLevel(playerId, level);
        return true;
    }

    @Override
    public boolean deleteRaft(@NotNull UUID playerId) {
        if (!raftManager.hasRaft(playerId)) return false;

        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            raftManager.deleteRaft(player, null);
        } else {
            raftManager.forceDeleteRaft(playerId);
        }
        return true;
    }

    @Override
    public boolean hasRaft(@NotNull UUID playerId) {
        return raftManager.hasRaft(playerId);
    }

    @NotNull
    @Override
    public org.bukkit.World getRaftWorld() {
        return raftManager.getRaftWorld();
    }

    @Override
    public double calculateRaftValue(@NotNull Location location, int radius) {
        return raftManager.calculateRaftValue(location, radius);
    }

    @Override
    public void updateRaftLevel(@NotNull UUID playerId) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            raftManager.handleCalculateCommand(player);
        }
    }
}
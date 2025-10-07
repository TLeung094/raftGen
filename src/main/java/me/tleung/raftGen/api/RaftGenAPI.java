package me.tleung.raftGen.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface RaftGenAPI {

    @NotNull
    Plugin getPlugin();

    boolean createRaft(@NotNull Player player, @Nullable Location location);

    @Nullable
    Location getRaftLocation(@NotNull UUID playerId);

    int getRaftLevel(@NotNull UUID playerId);

    boolean setRaftLevel(@NotNull UUID playerId, int level);

    boolean deleteRaft(@NotNull UUID playerId);

    boolean hasRaft(@NotNull UUID playerId);

    @NotNull
    org.bukkit.World getRaftWorld();

    double calculateRaftValue(@NotNull Location location, int radius);

    void updateRaftLevel(@NotNull UUID playerId);
}
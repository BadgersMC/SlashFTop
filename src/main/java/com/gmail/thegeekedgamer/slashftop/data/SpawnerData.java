package com.gmail.thegeekedgamer.slashftop.data;

import com.gmail.thegeekedgamer.slashftop.*;
import com.gmail.thegeekedgamer.slashftop.config.*;
import com.gmail.thegeekedgamer.slashftop.utils.*;
import lombok.*;
import org.bukkit.*;
import org.bukkit.entity.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

@Getter
@ToString
public class SpawnerData {

    private final FTopPlugin plugin;
    private final Location location;
    private final long placedTimestamp;
    @Setter
    private int stackSize;
    private final String spawnerType;
    @Setter
    private double baseValue;
    @Setter
    private boolean locked;

    public SpawnerData(FTopPlugin plugin, Location location, long placedTimestamp, int stackSize, String spawnerType, boolean locked) {
        if (stackSize <= 0) {
            throw new IllegalArgumentException("Stack size must be greater than 0, got: " + stackSize);
        }
        try {
            EntityType.valueOf(spawnerType);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid spawner type: " + spawnerType);
        }

        this.plugin = plugin;
        this.location = location;
        this.placedTimestamp = placedTimestamp;
        this.stackSize = stackSize;
        this.spawnerType = spawnerType;
        this.locked = locked;
        this.baseValue = FTopConfig.getSpawnerBaseValue(spawnerType);
    }

    public double getCurrentValue() {
        if (stackSize <= 0) {
            Log.warn("Invalid stack size for spawner at " + location + " (type: " + spawnerType + ", faction: " + FactionUtils.getFactionIdAtLocation(location) + "): " + stackSize + ". Returning 0.");
            return 0.0;
        }
        if (baseValue <= 0) {
            Log.warn("Invalid base value for spawner at " + location + " (type: " + spawnerType + ", faction: " + FactionUtils.getFactionIdAtLocation(location) + "): " + baseValue + ". Using default value.");
            baseValue = FTopConfig.getDefaultSpawnerValue();
        }
        double perSpawnerValue = locked ? calculateAgedValue() : baseValue * FTopConfig.getStartingPercentage();
        if (Double.isNaN(perSpawnerValue) || Double.isInfinite(perSpawnerValue)) {
            Log.error("Calculated NaN or infinite perSpawnerValue for spawner at " + location + " (type: " + spawnerType + ", faction: " + FactionUtils.getFactionIdAtLocation(location) + "). Returning 0.");
            return 0.0;
        }
        return perSpawnerValue * stackSize;
    }

    public double getFullyAgedValue() {
        return baseValue * stackSize;
    }

    private double calculateAgedValue() {
        long ageMillis = System.currentTimeMillis() - placedTimestamp;
        double ageFactor = Math.min(1.0, ageMillis / (double) FTopConfig.getAgingDurationMillis());
        double start = FTopConfig.getStartingPercentage();
        return baseValue * (start + ageFactor * (1.0 - start));
    }

    public long getFullyAgedTimestamp() {
        return placedTimestamp + FTopConfig.getAgingDurationMillis();
    }

    public boolean isFullyAged() {
        return System.currentTimeMillis() >= getFullyAgedTimestamp();
    }

    public CompletableFuture<Void> saveAsync(String factionId) {
        String query = "INSERT OR REPLACE INTO spawner_data (location, world, x, y, z, faction_id, placed_timestamp, stack_size, spawner_type, base_value, locked) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = plugin.getDatabase().getConnection();
                 PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, FactionUtils.locationToString(location));
                ps.setString(2, location.getWorld().getName());
                ps.setInt(3, location.getBlockX());
                ps.setInt(4, location.getBlockY());
                ps.setInt(5, location.getBlockZ());
                ps.setString(6, factionId);
                ps.setLong(7, placedTimestamp);
                ps.setInt(8, stackSize);
                ps.setString(9, spawnerType);
                ps.setDouble(10, baseValue);
                ps.setBoolean(11, locked);
                ps.executeUpdate();
                Log.info("Spawner data saved to database for location: " + location);
            } catch (SQLException e) {
                Log.error("Error saving spawner data to database: " + e.getMessage());
                throw new RuntimeException("Failed to save spawner data", e);
            }
        });
    }

    public CompletableFuture<Void> deleteAsync() {
        return plugin.getDatabase().deleteSpawnerDataAsync(FactionUtils.locationToString(location));
    }

    public static CompletableFuture<SpawnerData> loadFromDatabaseAsync(FTopPlugin plugin, Location location) {
        String query = "SELECT placed_timestamp, stack_size, spawner_type, base_value, locked FROM spawner_data WHERE location = ?;";
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = plugin.getDatabase().getConnection();
                 PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, FactionUtils.locationToString(location));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long placedTimestamp = rs.getLong("placed_timestamp");
                        int stackSize = rs.getInt("stack_size");
                        String spawnerType = rs.getString("spawner_type");
                        double baseValue = rs.getDouble("base_value");
                        boolean locked = rs.getBoolean("locked");
                        SpawnerData spawnerData = new SpawnerData(plugin, location, placedTimestamp, stackSize, spawnerType, locked);
                        spawnerData.setBaseValue(baseValue);
                        return spawnerData;
                    }
                }
            } catch (SQLException e) {
                Log.error("Error loading spawner data from database: " + e.getMessage());
            }
            return null;
        });
    }

    public static CompletableFuture<Void> saveAllAsync(FTopPlugin plugin, Map<Location, SpawnerData> spawnerDataMap, Map<Location, String> factionIds) {
        Log.info("Saving all tracked spawners to the database...");
        String query = "INSERT OR REPLACE INTO spawner_data (location, world, x, y, z, faction_id, placed_timestamp, stack_size, spawner_type, base_value, locked) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";

        return CompletableFuture.runAsync(() -> {
            try (Connection conn = plugin.getDatabase().getConnection();
                 PreparedStatement ps = conn.prepareStatement(query)) {
                for (Map.Entry<Location, SpawnerData> entry : spawnerDataMap.entrySet()) {
                    Location location = entry.getKey();
                    SpawnerData spawnerData = entry.getValue();
                    String factionId = factionIds.get(location);
                    if (factionId == null) {
                        Log.warn("No faction ID provided for spawner at " + location + ". Skipping.");
                        continue;
                    }

                    ps.setString(1, FactionUtils.locationToString(location));
                    ps.setString(2, location.getWorld().getName());
                    ps.setInt(3, location.getBlockX());
                    ps.setInt(4, location.getBlockY());
                    ps.setInt(5, location.getBlockZ());
                    ps.setString(6, factionId);
                    ps.setLong(7, spawnerData.getPlacedTimestamp());
                    ps.setInt(8, spawnerData.getStackSize());
                    ps.setString(9, spawnerData.getSpawnerType());
                    ps.setDouble(10, spawnerData.getBaseValue());
                    ps.setBoolean(11, spawnerData.isLocked());
                    ps.addBatch();
                }

                ps.executeBatch();
                Log.info("All tracked spawners have been saved to the database.");
            } catch (SQLException e) {
                Log.error("Error saving all spawner data to database: " + e.getMessage());
                throw new RuntimeException("Failed to save all spawner data", e);
            }
        });
    }
}
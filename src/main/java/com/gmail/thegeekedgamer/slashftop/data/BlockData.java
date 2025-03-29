package com.gmail.thegeekedgamer.slashftop.data;

import com.gmail.thegeekedgamer.slashftop.*;
import com.gmail.thegeekedgamer.slashftop.config.*;
import com.gmail.thegeekedgamer.slashftop.utils.*;
import lombok.*;
import org.bukkit.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

@Getter
@ToString
public class BlockData {

    private final FTopPlugin plugin;
    private final Location location;
    private final long placedTimestamp; // In milliseconds
    @Setter
    private int amount;
    private final String blockType;
    @Setter
    private double baseValue;
    @Setter
    private boolean locked;
    private final String locationFactionId;

    public BlockData(FTopPlugin plugin, Location location, long placedTimestamp, int amount, String blockType, boolean locked, String locationFactionId) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0, got: " + amount);
        }
        if (Material.getMaterial(blockType) == null) {
            throw new IllegalArgumentException("Invalid block type: " + blockType);
        }
        this.plugin = plugin;
        this.location = location;
        this.placedTimestamp = placedTimestamp;
        this.amount = amount;
        this.blockType = blockType;
        this.locked = locked;
        this.baseValue = FTopConfig.getBlockBaseValue(blockType);
        this.locationFactionId = locationFactionId;
    }

    public double getCurrentValue() {
        if (amount <= 0) {
            Log.warn("Invalid amount for block at " + location + " (type: " + blockType + ", faction: " + FactionUtils.getFactionIdAtLocation(location) + "): " + amount + ". Returning 0.");
            return 0.0;
        }
        if (baseValue <= 0) {
            Log.warn("Invalid base value for block at " + location + " (type: " + blockType + ", faction: " + FactionUtils.getFactionIdAtLocation(location) + "): " + baseValue + ". Using 0.");
            return 0.0;
        }
        double perBlockValue = locked ? calculateAgedValue() : baseValue * FTopConfig.getStartingPercentage();
        if (Double.isNaN(perBlockValue) || Double.isInfinite(perBlockValue)) {
            Log.error("Calculated NaN or infinite perBlockValue for block at " + location + " (type: " + blockType + ", faction: " + FactionUtils.getFactionIdAtLocation(location) + "). Returning 0.");
            return 0.0;
        }
        return perBlockValue * amount;
    }

    public double getFullyAgedValue() {
        return baseValue * amount;
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
        String query = "INSERT OR REPLACE INTO block_data (location, world, x, y, z, faction_id, placed_timestamp, amount, block_type, base_value, locked) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
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
                ps.setInt(8, amount);
                ps.setString(9, blockType);
                ps.setDouble(10, baseValue);
                ps.setBoolean(11, locked);
                ps.executeUpdate();
                Log.info("Block data saved to database for location: " + location);
            } catch (SQLException e) {
                Log.error("Error saving block data to database: " + e.getMessage());
                throw new RuntimeException("Failed to save block data", e);
            }
        });
    }

    public CompletableFuture<Void> deleteAsync() {
        return plugin.getDatabase().deleteBlockDataAsync(FactionUtils.locationToString(location));
    }

    public static CompletableFuture<BlockData> loadFromDatabaseAsync(FTopPlugin plugin, Location location) {
        String query = "SELECT placed_timestamp, amount, block_type, base_value, locked, faction_id FROM block_data WHERE location = ?;";
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = plugin.getDatabase().getConnection();
                 PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, FactionUtils.locationToString(location));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long placedTimestamp = rs.getLong("placed_timestamp");
                        int amount = rs.getInt("amount");
                        String blockType = rs.getString("block_type");
                        double baseValue = rs.getDouble("base_value");
                        boolean locked = rs.getBoolean("locked");
                        String factionId = rs.getString("faction_id");
                        BlockData blockData = new BlockData(plugin, location, placedTimestamp, amount, blockType, locked, factionId);
                        blockData.setBaseValue(baseValue);
                        return blockData;
                    }
                }
            } catch (SQLException e) {
                Log.error("Error loading block data from database: " + e.getMessage());
            }
            return null;
        });
    }

    public static CompletableFuture<Void> saveAllAsync(FTopPlugin plugin, Map<Location, BlockData> blockDataMap, Map<Location, String> factionIds) {
        Log.info("Saving all tracked blocks to the database...");
        String query = "INSERT OR REPLACE INTO block_data (location, world, x, y, z, faction_id, placed_timestamp, amount, block_type, base_value, locked) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";

        return CompletableFuture.runAsync(() -> {
            try (Connection conn = plugin.getDatabase().getConnection();
                 PreparedStatement ps = conn.prepareStatement(query)) {
                for (Map.Entry<Location, BlockData> entry : blockDataMap.entrySet()) {
                    Location location = entry.getKey();
                    BlockData blockData = entry.getValue();
                    String factionId = factionIds.get(location);
                    if (factionId == null) {
                        Log.warn("No faction ID provided for block at " + location + ". Skipping.");
                        continue;
                    }

                    ps.setString(1, FactionUtils.locationToString(location));
                    ps.setString(2, location.getWorld().getName());
                    ps.setInt(3, location.getBlockX());
                    ps.setInt(4, location.getBlockY());
                    ps.setInt(5, location.getBlockZ());
                    ps.setString(6, factionId);
                    ps.setLong(7, blockData.getPlacedTimestamp());
                    ps.setInt(8, blockData.getAmount());
                    ps.setString(9, blockData.getBlockType());
                    ps.setDouble(10, blockData.getBaseValue());
                    ps.setBoolean(11, blockData.isLocked());
                    ps.addBatch();
                }

                ps.executeBatch();
                Log.info("All tracked blocks have been saved to the database.");
            } catch (SQLException e) {
                Log.error("Error saving all block data to database: " + e.getMessage());
                throw new RuntimeException("Failed to save all block data", e);
            }
        });
    }
}
package com.gmail.thegeekedgamer.slashftop.data;

import com.gmail.thegeekedgamer.slashftop.*;
import com.gmail.thegeekedgamer.slashftop.config.*;
import com.gmail.thegeekedgamer.slashftop.utils.*;
import com.zaxxer.hikari.*;

import java.io.*;
import java.sql.*;
import java.util.concurrent.*;

public class SQLite implements IDatabase {

    private final HikariDataSource dataSource;

    public SQLite(FTopPlugin plugin) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + new File(plugin.getDataFolder(), "database.db").getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setMaxLifetime(600000);
        config.setConnectionTimeout(10000);
        config.setPoolName("FTopSQLitePool");
        this.dataSource = new HikariDataSource(config);
        createTables();
        plugin.getLogger().info(FTopConfig.getPrefix() + " Configured HikariCP for SQLite database.");
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public boolean connect() {
        try (Connection conn = dataSource.getConnection()) {
            // Test the connection by executing a simple query
            try (PreparedStatement ps = conn.prepareStatement("SELECT 1")) {
                ps.executeQuery();
            }
            return true;
        } catch (SQLException e) {
            Log.error("Failed to connect to SQLite database: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void disconnect() {
        if (dataSource != null) {
            dataSource.close();
            Log.info("Disconnected from SQLite database successfully.");
        }
    }

    @Override
    public CompletableFuture<Void> deleteSpawnerDataAsync(String location) {
        return CompletableFuture.runAsync(() -> {
            String query = "DELETE FROM spawner_data WHERE location = ?;";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, location);
                ps.executeUpdate();
                Log.info("Spawner data deleted for location: " + location);
            } catch (SQLException e) {
                Log.error("Error deleting spawner data: " + e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<Void> deleteBlockDataAsync(String location) {
        return CompletableFuture.runAsync(() -> {
            String query = "DELETE FROM block_data WHERE location = ?;";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, location);
                ps.executeUpdate();
                Log.info("Block data deleted for location: " + location);
            } catch (SQLException e) {
                Log.error("Error deleting block data: " + e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<Void> updateFactionValueAsync(String factionId, double newValue) {
        return CompletableFuture.runAsync(() -> {
            String query = "INSERT OR REPLACE INTO faction_values (faction_id, value) VALUES (?, ?);";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, factionId);
                ps.setDouble(2, newValue);
                ps.executeUpdate();
                Log.info("Faction value for ID " + factionId + " saved to database.");
            } catch (SQLException e) {
                Log.error("Error updating faction value in database: " + e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<Double> getFactionValueAsync(String factionId) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT value FROM faction_values WHERE faction_id = ?;";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, factionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getDouble("value");
                    }
                    return 0.0; // Default value if faction not found
                }
            } catch (SQLException e) {
                Log.error("Error retrieving faction value for ID " + factionId + ": " + e.getMessage());
                return 0.0;
            }
        });
    }

    private void createTables() {
        String spawnerTable = "CREATE TABLE IF NOT EXISTS spawner_data (" +
                "location TEXT PRIMARY KEY, " +
                "world TEXT, " +
                "x INTEGER, " +
                "y INTEGER, " +
                "z INTEGER, " +
                "faction_id TEXT, " +
                "placed_timestamp INTEGER, " +
                "stack_size INTEGER, " +
                "spawner_type TEXT, " +
                "base_value REAL, " +
                "locked BOOLEAN" +
                ");";

        String blockTable = "CREATE TABLE IF NOT EXISTS block_data (" +
                "location TEXT PRIMARY KEY, " +
                "world TEXT, " +
                "x INTEGER, " +
                "y INTEGER, " +
                "z INTEGER, " +
                "faction_id TEXT, " +
                "placed_timestamp INTEGER, " +
                "amount INTEGER, " +
                "block_type TEXT, " +
                "base_value REAL, " +
                "locked BOOLEAN" +
                ");";

        String factionValuesTable = "CREATE TABLE IF NOT EXISTS faction_values (" +
                "faction_id TEXT PRIMARY KEY, " +
                "value REAL" +
                ");";

        try (Connection conn = getConnection();
             PreparedStatement statement1 = conn.prepareStatement(spawnerTable);
             PreparedStatement statement2 = conn.prepareStatement(blockTable);
             PreparedStatement statement3 = conn.prepareStatement(factionValuesTable)) {
            statement1.execute();
            statement2.execute();
            statement3.execute();
            Log.info("Database tables created or confirmed to exist successfully.");
        } catch (SQLException e) {
            Log.error("Could not create database tables: " + e.getMessage());
        }
    }
}
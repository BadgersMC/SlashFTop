package com.gmail.thegeekedgamer.slashftop.data;

import java.sql.*;
import java.util.concurrent.*;

public interface IDatabase {

    Connection getConnection() throws SQLException;

    boolean connect();

    void disconnect();

    CompletableFuture<Void> deleteSpawnerDataAsync(String location);

    CompletableFuture<Void> deleteBlockDataAsync(String location);

    CompletableFuture<Void> updateFactionValueAsync(String factionId, double newValue);

    CompletableFuture<Double> getFactionValueAsync(String factionId);
}
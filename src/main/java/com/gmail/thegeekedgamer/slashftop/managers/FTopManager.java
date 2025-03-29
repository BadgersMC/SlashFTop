package com.gmail.thegeekedgamer.slashftop.managers;

import com.gmail.thegeekedgamer.slashftop.*;
import com.gmail.thegeekedgamer.slashftop.config.*;
import com.gmail.thegeekedgamer.slashftop.data.*;
import com.gmail.thegeekedgamer.slashftop.tracking.*;
import com.gmail.thegeekedgamer.slashftop.utils.*;
import com.massivecraft.factions.*;
import lombok.*;
import org.bukkit.*;
import org.bukkit.scheduler.*;

import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

public class FTopManager {

    private final FTopPlugin plugin;
    private final SpawnerTracker spawnerTracker;
    private final BlockTracker blockTracker;
    private final Map<String, Double> factionValues = new ConcurrentHashMap<>();
    @Getter
    private final Map<String, FactionData> factionDataMap = new ConcurrentHashMap<>();
    private BukkitTask recalculationTask;

    public FTopManager(FTopPlugin plugin, SpawnerTracker spawnerTracker, BlockTracker blockTracker) {
        this.plugin = plugin;
        this.spawnerTracker = spawnerTracker;
        this.blockTracker = blockTracker;
    }

    public FactionData getFactionData(String factionId) {
        return factionDataMap.get(factionId);
    }

    private FactionData computeFactionData(Faction faction) {
        String factionId = faction.getId();

        // Fetch spawner and block data
        List<SpawnerData> spawners = getSpawnersForFaction(factionId);
        Map<String, BlockData> blocks = blockTracker.getBlockData(factionId);

        // Compute values
        double spawnerValue = spawnerTracker.getTotalSpawnerValueForFaction(factionId);
        double blockValue = blockTracker.getTotalBlockValueForFaction(factionId);
        double totalValue = spawnerValue + blockValue;
        double fullyAgedSpawners = spawners.stream().mapToDouble(SpawnerData::getFullyAgedValue).sum();
        double fullyAgedBlocks = blocks.values().stream().mapToDouble(BlockData::getFullyAgedValue).sum();
        double fullyAgedValue = fullyAgedSpawners + fullyAgedBlocks;
        String formattedValue = NumberFormat.getCurrencyInstance(Locale.US).format(totalValue);

        // Dominant types
        String dominantSpawner = spawnerTracker.getDominantSpawnerTypeForFaction(factionId);
        Material dominantBlock = blockTracker.getDominantBlockTypeForFaction(factionId);

        // Counts
        int spawnerCount = spawners.size();
        long blockCount = blocks.values().stream().mapToLong(BlockData::getAmount).sum();

        // Representative coordinate
        String representativeCoordinate = spawners.isEmpty() ? "N/A" : FactionUtils.locationToString(spawners.get(0).getLocation());

        // Average age percentage
        double totalAgePct = spawners.stream().mapToDouble(s -> {
            double full = s.getFullyAgedValue();
            double current = s.getCurrentValue();
            return (full > 0) ? (current / full) * 100.0 : 0.0;
        }).sum();
        double averageAgePercentage = spawners.isEmpty() ? 0.0 : totalAgePct / spawners.size();

        // Time till fully aged
        long currentTime = System.currentTimeMillis();
        long minRemaining = Long.MAX_VALUE;
        for (SpawnerData s : spawners) {
            long remaining = s.getFullyAgedTimestamp() - currentTime;
            if (remaining > 0 && remaining < minRemaining) minRemaining = remaining;
        }
        for (BlockData b : blocks.values()) {
            long remaining = b.getFullyAgedTimestamp() - currentTime;
            if (remaining > 0 && remaining < minRemaining) minRemaining = remaining;
        }
        String timeTillFullyAged = (minRemaining == Long.MAX_VALUE) ? "N/A" : formatTime(minRemaining);

        FPlayer admin = faction.getFPlayerAdmin();

        return new FactionData(
                faction, admin, totalValue, spawnerValue, blockValue, fullyAgedValue,
                formattedValue, dominantBlock, dominantSpawner != null ? dominantSpawner : "None",
                spawnerCount, blockCount, representativeCoordinate, averageAgePercentage, timeTillFullyAged
        );
    }

    private List<SpawnerData> getSpawnersForFaction(String factionId) {
        return spawnerTracker.getTrackedSpawners().entrySet().stream()
                .filter(e -> factionId.equals(FactionUtils.getFactionIdAtLocation(e.getKey())))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    public void calculateFactionValue(String factionId) {
        Faction faction = Factions.getInstance().getFactionById(factionId);
        if (faction == null || faction.isWilderness() || faction.isWarZone() || faction.isSafeZone()) {
            return;
        }
        FactionData data = computeFactionData(faction);
        factionValues.put(factionId, data.totalValue);
        factionDataMap.put(factionId, data);
    }

    public void recalculateFactionValues() {
        List<Faction> validFactions = Factions.getInstance().getAllFactions().stream()
                .filter(f -> !f.isWilderness() && !f.isWarZone() && !f.isSafeZone())
                .toList();

        if (validFactions.isEmpty()) {
            Log.info("No valid factions to recalculate.");
            return;
        }

        int threadCount = FTopConfig.getRecalculationThreadCount();
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            validFactions.forEach(faction -> executor.submit(() -> calculateFactionValue(faction.getId())));
        }
        Log.info("Recalculated values for " + validFactions.size() + " factions.");
    }

    public void startRecalculationTask() {
        recalculationTask = new BukkitRunnable() {
            @Override
            public void run() {
                recalculateFactionValues();
            }
        }.runTaskTimerAsynchronously(plugin, 0L, FTopConfig.getFtopCalculationInterval() * 20L);
    }

    public void stopRecalculationTask() {
        if (recalculationTask != null) {
            recalculationTask.cancel();
            recalculationTask = null;
        }
    }

    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        seconds %= 60;
        minutes %= 60;
        return String.format("%dh %dm %ds", hours, minutes, seconds);
    }
}
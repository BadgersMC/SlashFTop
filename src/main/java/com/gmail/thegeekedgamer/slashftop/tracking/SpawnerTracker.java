package com.gmail.thegeekedgamer.slashftop.tracking;

import com.gmail.thegeekedgamer.slashftop.*;
import com.gmail.thegeekedgamer.slashftop.config.*;
import com.gmail.thegeekedgamer.slashftop.data.*;
import com.gmail.thegeekedgamer.slashftop.utils.*;
import com.massivecraft.factions.*;
import dev.rosewood.rosestacker.api.*;
import dev.rosewood.rosestacker.event.*;
import dev.rosewood.rosestacker.stack.*;
import lombok.*;
import net.milkbowl.vault.economy.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.jetbrains.annotations.*;

import java.util.Comparator;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.stream.*;

public class SpawnerTracker implements Listener {

    private final FTopPlugin plugin;
    private final Economy economy;
    @Getter
    private final Map<Location, SpawnerData> trackedSpawners;
    private final Map<String, Double> factionValues;
    private final Set<String> dirtyFactions;
    private final Map<Location, ReentrantLock> spawnerLocks;
    private final ReentrantReadWriteLock rwLock;

    public SpawnerTracker(FTopPlugin plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.trackedSpawners = new ConcurrentHashMap<>();
        this.factionValues = new ConcurrentHashMap<>();
        this.dirtyFactions = ConcurrentHashMap.newKeySet();
        this.spawnerLocks = new WeakHashMap<>();
        this.rwLock = new ReentrantReadWriteLock(true);
        Log.info("SpawnerTracker initialized.");
    }

    private ReentrantLock getLockForLocation(Location location) {
        return spawnerLocks.computeIfAbsent(location, k -> new ReentrantLock(true));
    }

    public void loadSpawnersFromRoseStacker() {
        Log.info("Loading stacked spawners from RoseStacker and scanning claimed chunks...");
        RoseStackerAPI roseStackerAPI = RoseStackerAPI.getInstance();
        Map<Block, StackedSpawner> allSpawners = roseStackerAPI.getStackedSpawners();
        int loadedFromRoseStacker = 0;

        rwLock.writeLock().lock();
        try {
            for (StackedSpawner spawner : allSpawners.values()) {
                Location location = spawner.getLocation();
                String factionId = FactionUtils.getFactionIdAtLocation(location);
                if (factionId == null || isSpecialFaction(factionId)) {
                    continue;
                }
                ReentrantLock lock = getLockForLocation(location);
                lock.lock();
                try {
                    SpawnerData existing = trackedSpawners.get(location);
                    if (existing != null) {
                        existing.setStackSize(spawner.getStackSize());
                        existing.saveAsync(factionId).exceptionally(throwable -> {
                            Log.error("Failed to save spawner data at " + location + ": " + throwable.getMessage());
                            return null;
                        });
                    } else {
                        trackSpawner(spawner);
                        loadedFromRoseStacker++;
                    }
                } finally {
                    lock.unlock();
                }
            }

            int scannedChunks = 0;
            int additionalSpawners = 0;
            for (Faction faction : Factions.getInstance().getAllFactions()) {
                if (faction.isWilderness() || faction.isWarZone() || faction.isSafeZone()) {
                    continue;
                }
                Collection<Chunk> chunks = FactionUtils.getFactionChunks(faction.getId());
                for (Chunk chunk : chunks) {
                    if (!chunk.isLoaded()) {
                        chunk.load();
                    }
                    scannedChunks++;
                    for (BlockState state : chunk.getTileEntities()) {
                        if (state instanceof CreatureSpawner spawnerState) {
                            Location location = state.getLocation();
                            ReentrantLock lock = getLockForLocation(location);
                            lock.lock();
                            try {
                                if (!trackedSpawners.containsKey(location)) {
                                    StackedSpawner stackedSpawner = roseStackerAPI.getStackedSpawner(state.getBlock());
                                    if (stackedSpawner != null) {
                                        trackSpawner(stackedSpawner);
                                    } else {
                                        SpawnerData spawnerData = new SpawnerData(
                                                plugin,
                                                location,
                                                System.currentTimeMillis(),
                                                1,
                                                Objects.requireNonNull(spawnerState.getSpawnedType()).name(),
                                                false
                                        );
                                        trackedSpawners.put(location, spawnerData);
                                        scheduleLockTimer(spawnerData);
                                        dirtyFactions.add(faction.getId());
                                        spawnerData.saveAsync(faction.getId()).exceptionally(throwable -> {
                                            Log.error("Failed to save spawner data at " + location + ": " + throwable.getMessage());
                                            return null;
                                        });
                                    }
                                    additionalSpawners++;
                                }
                            } finally {
                                lock.unlock();
                            }
                        }
                    }
                }
            }

            Log.info("Loaded " + loadedFromRoseStacker + " spawners from RoseStacker, found " + additionalSpawners + " additional spawners in " + scannedChunks + " claimed chunks.");
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void syncWithRoseStacker() {
        RoseStackerAPI roseStackerAPI = RoseStackerAPI.getInstance();
        Map<Block, StackedSpawner> currentSpawners = roseStackerAPI.getStackedSpawners();

        rwLock.writeLock().lock();
        try {
            for (StackedSpawner stackedSpawner : currentSpawners.values()) {
                Location location = stackedSpawner.getLocation();
                if (location == null) continue;
                String factionId = FactionUtils.getFactionIdAtLocation(location);
                if (factionId == null || isSpecialFaction(factionId)) continue;

                ReentrantLock lock = getLockForLocation(location);
                lock.lock();
                try {
                    SpawnerData spawnerData = trackedSpawners.get(location);
                    if (spawnerData != null) {
                        int oldStackSize = spawnerData.getStackSize();
                        int newStackSize = stackedSpawner.getStackSize();
                        if (oldStackSize != newStackSize) {
                            spawnerData.setStackSize(newStackSize);
                            dirtyFactions.add(factionId);
                            Log.info("Updated spawner stack size at " + location + " from " + oldStackSize + " to " + newStackSize);
                            spawnerData.saveAsync(factionId).exceptionally(throwable -> {
                                Log.error("Failed to save spawner data at " + location + ": " + throwable.getMessage());
                                return null;
                            });
                        }
                    } else {
                        trackSpawner(stackedSpawner);
                    }
                } finally {
                    lock.unlock();
                }
            }

            Set<Location> currentLocations = currentSpawners.keySet().stream()
                    .map(Block::getLocation)
                    .collect(Collectors.toSet());
            Iterator<Map.Entry<Location, SpawnerData>> iterator = trackedSpawners.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Location, SpawnerData> entry = iterator.next();
                Location location = entry.getKey();
                if (!currentLocations.contains(location)) {
                    String factionId = FactionUtils.getFactionIdAtLocation(location);
                    if (factionId != null) {
                        dirtyFactions.add(factionId);
                    }
                    SpawnerData spawnerData = entry.getValue();
                    iterator.remove();
                    spawnerData.deleteAsync().exceptionally(throwable -> {
                        Log.error("Failed to delete spawner data at " + location + ": " + throwable.getMessage());
                        return null;
                    });
                    Log.info("Removed spawner from tracking at " + location + " as it no longer exists.");
                }
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private boolean isSpecialFaction(String factionId) {
        Faction faction = Factions.getInstance().getFactionById(factionId);
        return faction.isWilderness() || faction.isWarZone() || faction.isSafeZone();
    }

    public List<SpawnerData> getSpawnersForFaction(String factionId) {
        rwLock.readLock().lock();
        try {
            return trackedSpawners.entrySet().stream()
                    .filter(entry -> factionId.equals(FactionUtils.getFactionIdAtLocation(entry.getKey())))
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toList());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    private void trackSpawner(StackedSpawner stackedSpawner) {
        Location location = stackedSpawner.getLocation();
        if (location == null) {
            Log.warn("A StackedSpawner with null location found. Skipping.");
            return;
        }

        Block block = location.getBlock();
        if (!(block.getState() instanceof CreatureSpawner spawnerState)) {
            Log.warn("Block at " + location + " is not a CreatureSpawner. Skipping.");
            return;
        }

        String factionId = FactionUtils.getFactionIdAtLocation(location);
        if (factionId == null) {
            Log.info("Spawner at " + location + " is not in a claimed area. Skipping.");
            return;
        }

        ReentrantLock lock = getLockForLocation(location);
        lock.lock();
        try {
            SpawnerData spawnerData = new SpawnerData(
                    plugin,
                    location,
                    System.currentTimeMillis(),
                    stackedSpawner.getStackSize(),
                    Objects.requireNonNull(spawnerState.getSpawnedType()).name(),
                    false
            );

            trackedSpawners.put(location, spawnerData);
            scheduleLockTimer(spawnerData);
            dirtyFactions.add(factionId);
            spawnerData.saveAsync(factionId).exceptionally(throwable -> {
                Log.error("Failed to save spawner data at " + location + ": " + throwable.getMessage());
                return null;
            });
            Log.info("Spawner at " + location + " has been added to tracking.");
        } finally {
            lock.unlock();
        }
    }

    private void trackSingleSpawner(Block block) {
        Location location = block.getLocation();
        String factionId = FactionUtils.getFactionIdAtLocation(location);
        if (factionId == null) {
            Log.info("Spawner at " + location + " is not in a claimed area. Skipping.");
            return;
        }
        if (isSpecialFaction(factionId)) {
            return;
        }

        if (!(block.getState() instanceof CreatureSpawner spawnerState)) {
            Log.warn("Block at " + location + " is not a CreatureSpawner. Skipping.");
            return;
        }

        StackedSpawner stackedSpawner = RoseStackerAPI.getInstance().getStackedSpawner(block);
        if (stackedSpawner != null) {
            trackSpawner(stackedSpawner);
            return;
        }

        ReentrantLock lock = getLockForLocation(location);
        lock.lock();
        try {
            SpawnerData spawnerData = new SpawnerData(
                    plugin,
                    location,
                    System.currentTimeMillis(),
                    1,
                    Objects.requireNonNull(spawnerState.getSpawnedType()).name(),
                    false
            );

            trackedSpawners.put(location, spawnerData);
            scheduleLockTimer(spawnerData);
            dirtyFactions.add(factionId);
            spawnerData.saveAsync(factionId).exceptionally(throwable -> {
                Log.error("Failed to save spawner data at " + location + ": " + throwable.getMessage());
                return null;
            });
            Log.info("Single spawner at " + location + " has been added to tracking.");
        } finally {
            lock.unlock();
        }
    }

    private void scheduleLockTimer(SpawnerData spawnerData) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            ReentrantLock lock = getLockForLocation(spawnerData.getLocation());
            lock.lock();
            try {
                if (trackedSpawners.containsKey(spawnerData.getLocation())) {
                    spawnerData.setLocked(true);
                    Log.info("Spawner at " + spawnerData.getLocation() + " is now locked.");
                    String factionId = FactionUtils.getFactionIdAtLocation(spawnerData.getLocation());
                    if (factionId != null) {
                        spawnerData.saveAsync(factionId).exceptionally(throwable -> {
                            Log.error("Failed to save spawner data at " + spawnerData.getLocation() + ": " + throwable.getMessage());
                            return null;
                        });
                    }
                }
            } finally {
                lock.unlock();
            }
        }, FTopConfig.getLockGracePeriodTicks());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(@NotNull BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) {
            return;
        }

        StackedSpawner stackedSpawner = RoseStackerAPI.getInstance().getStackedSpawner(block);
        if (stackedSpawner != null) {
            return;
        }

        trackSingleSpawner(block);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawnerBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) return;

        Location location = block.getLocation();
        String factionId = FactionUtils.getFactionIdAtLocation(location);
        if (factionId == null) return;

        ReentrantLock lock = getLockForLocation(location);
        lock.lock();
        try {
            SpawnerData spawnerData = trackedSpawners.get(location);
            if (spawnerData == null) {
                Log.warn("Attempted to break a spawner that was not being tracked at " + location);
                return;
            }

            StackedSpawner stackedSpawner = RoseStackerAPI.getInstance().getStackedSpawner(block);
            if (stackedSpawner != null && event.getPlayer().isSneaking()) {
                return;
            }

            if (spawnerData.isLocked()) {
                double perSpawnerValue = spawnerData.getCurrentValue() / spawnerData.getStackSize();
                double breakCost = perSpawnerValue * FTopConfig.getLockedBreakCostPercentage();

                if (economy == null) {
                    Log.error("Economy is not available. Cannot process spawner break cost.");
                    Log.sendToPlayer(event.getPlayer(), "&cAn error occurred: Economy system is not available.");
                    event.setCancelled(true);
                    return;
                }

                if (!economy.withdrawPlayer(event.getPlayer(), breakCost).transactionSuccess()) {
                    Log.sendToPlayer(event.getPlayer(), "&cYou need $" + String.format("%.2f", breakCost) + " to break this locked spawner!");
                    event.setCancelled(true);
                    return;
                }

                Log.sendToPlayer(event.getPlayer(), "&aSuccessfully broke a locked spawner for $" + String.format("%.2f", breakCost) + ".");
            }

            int newStackSize = spawnerData.getStackSize() - 1;
            if (newStackSize < 1) {
                trackedSpawners.remove(location);
                spawnerData.deleteAsync().exceptionally(throwable -> {
                    Log.error("Failed to delete spawner data at " + location + ": " + throwable.getMessage());
                    return null;
                });
                dirtyFactions.add(factionId);
                Log.info("Spawner removed from tracking at " + location + " after being broken.");
            } else {
                spawnerData.setStackSize(newStackSize);
                dirtyFactions.add(factionId);
                Log.info("Spawner at " + location + " stack decreased by 1. New stack size: " + newStackSize);
                spawnerData.saveAsync(factionId).exceptionally(throwable -> {
                    Log.error("Failed to save spawner data at " + location + ": " + throwable.getMessage());
                    return null;
                });
            }
        } finally {
            lock.unlock();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerStackChange(SpawnerStackEvent event) {
        StackedSpawner stackedSpawner = event.getStack();
        Location location = stackedSpawner.getLocation();
        String factionId = FactionUtils.getFactionIdAtLocation(location);
        if (factionId == null) return;

        ReentrantLock lock = getLockForLocation(location);
        lock.lock();
        try {
            SpawnerData spawnerData = trackedSpawners.get(location);
            if (spawnerData != null && !event.isNew()) {
                int newStackSize = spawnerData.getStackSize() + event.getIncreaseAmount();
                spawnerData.setStackSize(newStackSize);
                dirtyFactions.add(factionId);
                Log.info("Spawner stack increased at " + location + " by " + event.getIncreaseAmount() + ". New stack size: " + newStackSize);
                spawnerData.saveAsync(factionId).exceptionally(throwable -> {
                    Log.error("Failed to save spawner data at " + location + ": " + throwable.getMessage());
                    return null;
                });
            } else {
                trackSpawner(stackedSpawner);
            }
        } finally {
            lock.unlock();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawnerUnstack(SpawnerUnstackEvent event) {
        StackedSpawner stackedSpawner = event.getStack();
        Location location = stackedSpawner.getLocation();
        String factionId = FactionUtils.getFactionIdAtLocation(location);
        if (factionId == null) return;

        ReentrantLock lock = getLockForLocation(location);
        lock.lock();
        try {
            SpawnerData spawnerData = trackedSpawners.get(location);
            if (spawnerData == null) {
                Log.warn("Attempted to unstack a spawner that was not being tracked at " + location);
                return;
            }

            int removedAmount = event.getDecreaseAmount();
            if (spawnerData.isLocked()) {
                double perSpawnerValue = spawnerData.getCurrentValue() / spawnerData.getStackSize();
                double breakCost = perSpawnerValue * removedAmount * FTopConfig.getLockedBreakCostPercentage();

                if (economy == null) {
                    Log.error("Economy is not available. Cannot process spawner unstack cost.");
                    Log.sendToPlayer(Objects.requireNonNull(event.getPlayer()), "&cAn error occurred: Economy system is not available.");
                    event.setCancelled(true);
                    return;
                }

                if (!economy.withdrawPlayer(event.getPlayer(), breakCost).transactionSuccess()) {
                    Log.sendToPlayer(Objects.requireNonNull(event.getPlayer()), "&cYou need $" + String.format("%.2f", breakCost) + " to break this locked spawner stack!");
                    event.setCancelled(true);
                    return;
                }

                Log.sendToPlayer(Objects.requireNonNull(event.getPlayer()), "&aSuccessfully broke " + removedAmount + " locked spawners for $" + String.format("%.2f", breakCost) + ".");
            }

            int newStackSize = spawnerData.getStackSize() - removedAmount;
            if (newStackSize < 1) {
                trackedSpawners.remove(location);
                spawnerData.deleteAsync().exceptionally(throwable -> {
                    Log.error("Failed to delete spawner data at " + location + ": " + throwable.getMessage());
                    return null;
                });
                dirtyFactions.add(factionId);
                Log.info("Spawner stack removed from tracking at " + location + " after unstacking.");
            } else {
                spawnerData.setStackSize(newStackSize);
                dirtyFactions.add(factionId);
                Log.info("Spawner stack at " + location + " decreased by " + removedAmount + ". New stack size: " + newStackSize);
                spawnerData.saveAsync(factionId).exceptionally(throwable -> {
                    Log.error("Failed to save spawner data at " + location + ": " + throwable.getMessage());
                    return null;
                });
            }
        } finally {
            lock.unlock();
        }
    }

    public double getTotalSpawnerValueForFaction(String factionId) {
        rwLock.readLock().lock();
        try {
            if (dirtyFactions.remove(factionId)) {
                double totalValue = trackedSpawners.entrySet().stream()
                        .filter(entry -> {
                            String locFactionId = FactionUtils.getFactionIdAtLocation(entry.getKey());
                            return locFactionId != null && locFactionId.equals(factionId);
                        })
                        .mapToDouble(entry -> {
                            double value = entry.getValue().getCurrentValue();
                            if (Double.isNaN(value) || Double.isInfinite(value)) {
                                Log.warn("NaN or infinite value detected for spawner at " + entry.getKey() + " in faction " + factionId + ". Treating as 0.");
                                return 0.0;
                            }
                            return value;
                        })
                        .sum();
                if (Double.isNaN(totalValue) || Double.isInfinite(totalValue)) {
                    Log.error("Total spawner value for faction " + factionId + " is NaN or infinite. Setting to 0.");
                    totalValue = 0.0;
                }
                factionValues.put(factionId, totalValue);
                // Removed database write - let FTopManager handle it
            }
            return factionValues.getOrDefault(factionId, 0.0);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public String getDominantSpawnerTypeForFaction(String factionId) {
        rwLock.readLock().lock();
        try {
            Map<String, SpawnerData> spawnerDataMap = new HashMap<>();
            for (Map.Entry<Location, SpawnerData> entry : trackedSpawners.entrySet()) {
                Location location = entry.getKey();
                SpawnerData spawnerData = entry.getValue();
                String locFactionId = FactionUtils.getFactionIdAtLocation(location);
                if (locFactionId != null && locFactionId.equals(factionId)) {
                    spawnerDataMap.compute(spawnerData.getSpawnerType(), (type, existing) -> {
                        if (existing == null) {
                            return new SpawnerData(plugin, location, spawnerData.getPlacedTimestamp(), spawnerData.getStackSize(), type, spawnerData.isLocked());
                        } else {
                            existing.setStackSize(existing.getStackSize() + spawnerData.getStackSize());
                            return existing;
                        }
                    });
                }
            }

            return spawnerDataMap.entrySet().stream()
                    .max(Comparator.comparingDouble(entry -> entry.getValue().getCurrentValue()))
                    .map(Map.Entry::getKey)
                    .orElse(null);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void saveAll() {
        rwLock.readLock().lock();
        try {
            Map<Location, String> factionIds = new HashMap<>();
            for (Map.Entry<Location, SpawnerData> entry : trackedSpawners.entrySet()) {
                String factionId = FactionUtils.getFactionIdAtLocation(entry.getKey());
                if (factionId != null) {
                    factionIds.put(entry.getKey(), factionId);
                }
            }
            SpawnerData.saveAllAsync(plugin, trackedSpawners, factionIds)
                    .exceptionally(throwable -> {
                        Log.error("Failed to save all spawner data: " + throwable.getMessage());
                        return null;
                    });
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void loadAllFromDatabase() {
        rwLock.writeLock().lock();
        try {
            for (World world : Bukkit.getWorlds()) {
                for (Chunk chunk : world.getLoadedChunks()) {
                    for (BlockState state : chunk.getTileEntities()) {
                        if (state instanceof CreatureSpawner) {
                            Location location = state.getLocation();
                            SpawnerData.loadFromDatabaseAsync(plugin, location)
                                    .thenAccept(spawnerData -> {
                                        if (spawnerData != null) {
                                            ReentrantLock lock = getLockForLocation(location);
                                            lock.lock();
                                            try {
                                                trackedSpawners.put(location, spawnerData);
                                                String factionId = FactionUtils.getFactionIdAtLocation(location);
                                                if (factionId != null && !isSpecialFaction(factionId)) {
                                                    factionValues.merge(factionId, spawnerData.getCurrentValue(), Double::sum);
                                                }
                                            } finally {
                                                lock.unlock();
                                            }
                                        }
                                    })
                                    .exceptionally(throwable -> {
                                        Log.error("Error loading spawner data for location " + location + ": " + throwable.getMessage());
                                        return null;
                                    });
                        }
                    }
                }
            }
            Log.info("Loaded " + trackedSpawners.size() + " spawners from database.");
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
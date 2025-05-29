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
import org.bukkit.configuration.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.jetbrains.annotations.*;

import java.util.Comparator;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.stream.*;

public class BlockTracker implements Listener {

    private final FTopPlugin plugin;
    private final Economy economy;
    @Getter
    private final Map<Location, BlockData> trackedBlocks;
    private final Map<String, Double> factionValues;
    private final Set<Material> valuableBlocks;
    private final Map<Material, Double> blockBaseValues;
    private final Set<String> dirtyFactions;
    private final Map<Location, ReentrantLock> blockLocks;
    private final ReentrantReadWriteLock rwLock;

    public BlockTracker(FTopPlugin plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.trackedBlocks = new ConcurrentHashMap<>();
        this.factionValues = new ConcurrentHashMap<>();
        this.valuableBlocks = new HashSet<>();
        this.blockBaseValues = new HashMap<>();
        this.dirtyFactions = ConcurrentHashMap.newKeySet();
        this.blockLocks = new ConcurrentHashMap<>();
        this.rwLock = new ReentrantReadWriteLock(true);

        loadValuableBlocksFromConfig();
        Log.info("BlockTracker initialized with " + valuableBlocks.size() + " valuable block types.");
    }

    private ReentrantLock getLockForLocation(Location location) {
        return blockLocks.computeIfAbsent(location, k -> new ReentrantLock(true));
    }

    private void loadValuableBlocksFromConfig() {
        ConfigurationSection blockValuesSection = plugin.getConfig().getConfigurationSection("ftop.block-values");
        if (blockValuesSection == null) {
            Log.warn("No 'ftop.block-values' section found in config. Defaulting to DIAMOND_BLOCK and EMERALD_BLOCK.");
            valuableBlocks.add(Material.NETHERITE_BLOCK);
            valuableBlocks.add(Material.DIAMOND_BLOCK);
            valuableBlocks.add(Material.EMERALD_BLOCK);
            valuableBlocks.add(Material.GOLD_BLOCK);
            valuableBlocks.add(Material.IRON_BLOCK);
            blockBaseValues.put(Material.NETHERITE_BLOCK, 1200.0);
            blockBaseValues.put(Material.DIAMOND_BLOCK, 1000.0);
            blockBaseValues.put(Material.EMERALD_BLOCK, 800.0);
            blockBaseValues.put(Material.GOLD_BLOCK, 600.0);
            blockBaseValues.put(Material.IRON_BLOCK, 400.0);
            return;
        }

        for (String blockName : blockValuesSection.getKeys(false)) {
            Material material = Material.getMaterial(blockName.toUpperCase());
            if (material == null || !material.isBlock()) {
                Log.warn("Invalid block type in config: " + blockName + ". Skipping.");
                continue;
            }

            double baseValue = blockValuesSection.getDouble(blockName);
            if (baseValue <= 0) {
                Log.warn("Invalid base value for block " + blockName + ": " + baseValue + ". Skipping.");
                continue;
            }

            valuableBlocks.add(material);
            blockBaseValues.put(material, baseValue);
            Log.info("Loaded valuable block: " + blockName + " with base value $" + baseValue);
        }

        if (valuableBlocks.isEmpty()) {
            Log.warn("No valid valuable blocks defined in config. Defaulting to DIAMOND_BLOCK and EMERALD_BLOCK.");
            valuableBlocks.add(Material.NETHERITE_BLOCK);
            valuableBlocks.add(Material.DIAMOND_BLOCK);
            valuableBlocks.add(Material.EMERALD_BLOCK);
            valuableBlocks.add(Material.GOLD_BLOCK);
            valuableBlocks.add(Material.IRON_BLOCK);
            blockBaseValues.put(Material.NETHERITE_BLOCK, 1200.0);
            blockBaseValues.put(Material.DIAMOND_BLOCK, 1000.0);
            blockBaseValues.put(Material.EMERALD_BLOCK, 800.0);
            blockBaseValues.put(Material.GOLD_BLOCK, 600.0);
            blockBaseValues.put(Material.IRON_BLOCK, 400.0);
        }
    }

    public void loadBlocksFromRoseStacker() {
        Log.info("Loading stacked blocks from RoseStacker...");
        RoseStackerAPI roseStackerAPI = RoseStackerAPI.getInstance();
        Map<Block, StackedBlock> allBlocks = roseStackerAPI.getStackedBlocks();

        rwLock.writeLock().lock();
        try {
            for (StackedBlock block : allBlocks.values()) {
                Location location = block.getLocation();
                String factionId = FactionUtils.getFactionIdAtLocation(location);
                if (factionId == null || isSpecialFaction(factionId)) {
                    continue;
                }
                trackBlock(block);
            }
            Log.info("Finished loading " + allBlocks.size() + " stacked blocks.");
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void syncWithRoseStacker() {
        RoseStackerAPI roseStackerAPI = RoseStackerAPI.getInstance();
        Map<Block, StackedBlock> currentBlocks = roseStackerAPI.getStackedBlocks();

        rwLock.writeLock().lock();
        try {
            for (StackedBlock stackedBlock : currentBlocks.values()) {
                Location location = stackedBlock.getLocation();
                if (location == null) continue;
                String factionId = FactionUtils.getFactionIdAtLocation(location);
                if (factionId == null || isSpecialFaction(factionId)) continue;

                ReentrantLock lock = getLockForLocation(location);
                lock.lock();
                try {
                    BlockData blockData = trackedBlocks.get(location);
                    if (blockData != null) {
                        int oldAmount = blockData.getAmount();
                        int newAmount = stackedBlock.getStackSize();
                        if (oldAmount != newAmount) {
                            blockData.setAmount(newAmount);
                            dirtyFactions.add(factionId);
                            Log.info("Updated block stack size at " + location + " from " + oldAmount + " to " + newAmount);
                            blockData.saveAsync(factionId).exceptionally(throwable -> {
                                Log.error("Failed to save block data at " + location + ": " + throwable.getMessage());
                                return null;
                            });
                        }
                    } else {
                        trackBlock(stackedBlock);
                    }
                } finally {
                    lock.unlock();
                }
            }

            Set<Location> currentLocations = currentBlocks.keySet().stream()
                    .map(Block::getLocation)
                    .collect(Collectors.toSet());
            Iterator<Map.Entry<Location, BlockData>> iterator = trackedBlocks.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Location, BlockData> entry = iterator.next();
                Location location = entry.getKey();
                if (!currentLocations.contains(location)) {
                    String factionId = FactionUtils.getFactionIdAtLocation(location);
                    if (factionId != null) {
                        dirtyFactions.add(factionId);
                    }
                    BlockData blockData = entry.getValue();
                    iterator.remove();
                    blockData.deleteAsync().exceptionally(throwable -> {
                        Log.error("Failed to delete block data at " + location + ": " + throwable.getMessage());
                        return null;
                    });
                    Log.info("Removed block from tracking at " + location + " as it no longer exists.");
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

    private void trackBlock(StackedBlock stackedBlock) {
        Location location = stackedBlock.getLocation();
        if (location == null) {
            Log.warn("A StackedBlock with null location found. Skipping.");
            return;
        }

        Block block = location.getBlock();
        Material blockType = block.getType();
        if (!valuableBlocks.contains(blockType)) {
            Log.info("Block at " + location + " is not a valuable block (" + blockType + "). Skipping.");
            return;
        }

        String factionId = FactionUtils.getFactionIdAtLocation(location);
        if (factionId == null) {
            Log.info("Block at " + location + " is not in a claimed area. Skipping.");
            return;
        }

        ReentrantLock lock = getLockForLocation(location);
        lock.lock();
        try {
            BlockData blockData = new BlockData(
                    plugin,
                    location,
                    System.currentTimeMillis(),
                    stackedBlock.getStackSize(), // Use the stack size from RoseStacker
                    blockType.name(),
                    false,
                    factionId
            );

            trackedBlocks.put(location, blockData);
            scheduleLockTimer(blockData);
            dirtyFactions.add(factionId);
            blockData.saveAsync(factionId).exceptionally(throwable -> {
                Log.error("Failed to save block data at " + location + ": " + throwable.getMessage());
                return null;
            });
            Log.info("Block at " + location + " has been added to tracking with stack size: " + stackedBlock.getStackSize());
        } finally {
            lock.unlock();
        }
    }

    private void trackSingleBlock(Block block) {
        Location location = block.getLocation();
        String factionId = FactionUtils.getFactionIdAtLocation(location);
        if (factionId == null) {
            Log.info("Block at " + location + " is not in a claimed area. Skipping.");
            return;
        }
        if (isSpecialFaction(factionId)) {
            return;
        }

        Material blockType = block.getType();
        if (!valuableBlocks.contains(blockType)) {
            Log.info("Block at " + location + " is not a valuable block (" + blockType + "). Skipping.");
            return;
        }

        ReentrantLock lock = getLockForLocation(location);
        lock.lock();
        try {
            // Check if the block is already tracked
            if (trackedBlocks.containsKey(location)) {
                Log.info("Block at " + location + " is already tracked. Skipping.");
                return;
            }

            // Check if RoseStacker has already created a StackedBlock
            StackedBlock stackedBlock = RoseStackerAPI.getInstance().getStackedBlock(block);
            if (stackedBlock != null) {
                int stackSize = stackedBlock.getStackSize();
                if (stackSize <= 0) {
                    Log.warn("Block at " + location + " has an invalid stack size of " + stackSize + " on placement. Defaulting to 1.");
                    stackSize = 1;
                }
                BlockData blockData = new BlockData(
                        plugin,
                        location,
                        System.currentTimeMillis(),
                        stackSize,
                        blockType.name(),
                        false,
                        factionId
                );
                trackedBlocks.put(location, blockData);
                scheduleLockTimer(blockData);
                dirtyFactions.add(factionId);
                blockData.saveAsync(factionId).exceptionally(throwable -> {
                    Log.error("Failed to save block data at " + location + ": " + throwable.getMessage());
                    return null;
                });
                Log.info("Block at " + location + " has been added to tracking with stack size: " + stackSize);
            } else {
                BlockData blockData = new BlockData(
                        plugin,
                        location,
                        System.currentTimeMillis(),
                        1,
                        blockType.name(),
                        false,
                        factionId
                );
                trackedBlocks.put(location, blockData);
                scheduleLockTimer(blockData);
                dirtyFactions.add(factionId);
                blockData.saveAsync(factionId).exceptionally(throwable -> {
                    Log.error("Failed to save block data at " + location + ": " + throwable.getMessage());
                    return null;
                });
                Log.info("Single block at " + location + " has been added to tracking.");
            }
        } finally {
            lock.unlock();
        }
    }

    private void scheduleLockTimer(BlockData blockData) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            ReentrantLock lock = getLockForLocation(blockData.getLocation());
            lock.lock();
            try {
                if (trackedBlocks.containsKey(blockData.getLocation())) {
                    blockData.setLocked(true);
                    Log.info("Block at " + blockData.getLocation() + " is now locked.");
                    String factionId = blockData.getLocationFactionId();
                    if (factionId != null) {
                        blockData.saveAsync(factionId).exceptionally(throwable -> {
                            Log.error("Failed to save block data at " + blockData.getLocation() + ": " + throwable.getMessage());
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
        if (!valuableBlocks.contains(block.getType())) {
            return;
        }

        Location location = block.getLocation();
        Log.info("BlockPlaceEvent for block at " + location);

        StackedBlock stackedBlock = RoseStackerAPI.getInstance().getStackedBlock(block);
        if (stackedBlock != null) {
            Log.info("Block at " + location + " is already stacked with size: " + stackedBlock.getStackSize());
        } else {
            Log.info("Block at " + location + " is not yet stacked.");
        }

        trackSingleBlock(block);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!valuableBlocks.contains(block.getType())) {
            return;
        }

        Location location = block.getLocation();
        String factionId = FactionUtils.getFactionIdAtLocation(location);
        if (factionId == null) {
            return;
        }

        ReentrantLock lock = getLockForLocation(location);
        lock.lock();
        try {
            BlockData blockData = trackedBlocks.get(location);
            if (blockData == null) {
                Log.warn("Attempted to break a block that was not being tracked at " + location);
                return;
            }

            StackedBlock stackedBlock = RoseStackerAPI.getInstance().getStackedBlock(block);
            if (stackedBlock != null && event.getPlayer().isSneaking()) {
                return;
            }

            if (blockData.isLocked()) {
                double perBlockValue = blockData.getCurrentValue() / blockData.getAmount();
                double breakCost = perBlockValue * FTopConfig.getLockedBreakCostPercentage();

                if (economy == null) {
                    Log.error("Economy is not available. Cannot process block break cost.");
                    Log.sendToPlayer(event.getPlayer(), "&cAn error occurred: Economy system is not available.");
                    event.setCancelled(true);
                    return;
                }

                if (!economy.withdrawPlayer(event.getPlayer(), breakCost).transactionSuccess()) {
                    Log.sendToPlayer(event.getPlayer(), "&cYou need $" + String.format("%.2f", breakCost) + " to break this locked block!");
                    event.setCancelled(true);
                    return;
                }

                Log.sendToPlayer(event.getPlayer(), "&aSuccessfully broke a locked block for $" + String.format("%.2f", breakCost) + ".");
            }

            int newAmount = stackedBlock != null ? stackedBlock.getStackSize() - 1 : blockData.getAmount() - 1;
            if (newAmount < 1) {
                trackedBlocks.remove(location);
                blockData.deleteAsync().exceptionally(throwable -> {
                    Log.error("Failed to delete block data at " + location + ": " + throwable.getMessage());
                    return null;
                });
                dirtyFactions.add(factionId);
                Log.info("Block removed from tracking at " + location + " after being broken.");
            } else {
                blockData.setAmount(newAmount);
                dirtyFactions.add(factionId);
                Log.info("Block at " + location + " stack decreased by 1. New stack size: " + newAmount);
                blockData.saveAsync(factionId).exceptionally(throwable -> {
                    Log.error("Failed to save block data at " + location + ": " + throwable.getMessage());
                    return null;
                });
            }
        } finally {
            lock.unlock();
        }
    }

    public List<BlockData> getBlocksForFaction(String factionId) {
        rwLock.readLock().lock();
        try {
            return trackedBlocks.entrySet().stream()
                    .filter(entry -> factionId.equals(FactionUtils.getFactionIdAtLocation(entry.getKey())))
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toList());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public double getTotalBlockValueForFaction(String factionId) {
        rwLock.readLock().lock();
        try {
            if (dirtyFactions.remove(factionId)) {
                double totalValue = trackedBlocks.entrySet().stream()
                        .filter(entry -> {
                            String locFactionId = entry.getValue().getLocationFactionId();
                            return locFactionId != null && locFactionId.equals(factionId);
                        })
                        .mapToDouble(entry -> {
                            double value = entry.getValue().getCurrentValue();
                            if (Double.isNaN(value) || Double.isInfinite(value)) {
                                Log.warn("NaN or infinite value detected for block at " + entry.getKey() + " in faction " + factionId + ". Treating as 0.");
                                return 0.0;
                            }
                            return value;
                        })
                        .sum();
                if (Double.isNaN(totalValue) || Double.isInfinite(totalValue)) {
                    Log.error("Total block value for faction " + factionId + " is NaN or infinite. Setting to 0.");
                    totalValue = 0.0;
                }
                factionValues.put(factionId, totalValue);
            }
            return factionValues.getOrDefault(factionId, 0.0);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockUnstack(BlockUnstackEvent event) {
        StackedBlock stackedBlock = event.getStack();
        Location location = stackedBlock.getLocation();
        String factionId = FactionUtils.getFactionIdAtLocation(location);
        if (factionId == null) return;

        ReentrantLock lock = getLockForLocation(location);
        lock.lock();
        try {
            BlockData blockData = trackedBlocks.get(location);
            if (blockData == null) {
                Log.warn("Attempted to unstack a block that was not being tracked at " + location);
                return;
            }

            int removedAmount = event.getDecreaseAmount();
            if (blockData.isLocked()) {
                double perBlockValue = blockData.getCurrentValue() / blockData.getAmount();
                double breakCost = perBlockValue * removedAmount * FTopConfig.getLockedBreakCostPercentage();

                if (economy == null) {
                    Log.error("Economy is not available. Cannot process block unstack cost.");
                    Log.sendToPlayer(Objects.requireNonNull(event.getPlayer()), "&cAn error occurred: Economy system is not available.");
                    event.setCancelled(true);
                    return;
                }

                if (!economy.withdrawPlayer(event.getPlayer(), breakCost).transactionSuccess()) {
                    Log.sendToPlayer(Objects.requireNonNull(event.getPlayer()), "&cYou need $" + String.format("%.2f", breakCost) + " to break this locked block stack!");
                    event.setCancelled(true);
                    return;
                }

                Log.sendToPlayer(Objects.requireNonNull(event.getPlayer()), "&aSuccessfully broke " + removedAmount + " locked blocks for $" + String.format("%.2f", breakCost) + ".");
            }

            int newAmount = blockData.getAmount() - removedAmount;
            if (newAmount < 1) {
                trackedBlocks.remove(location);
                blockData.deleteAsync().exceptionally(throwable -> {
                    Log.error("Failed to delete block data at " + location + ": " + throwable.getMessage());
                    return null;
                });
                dirtyFactions.add(factionId);
                Log.info("Block stack removed from tracking at " + location + " after unstacking.");
            } else {
                blockData.setAmount(newAmount);
                dirtyFactions.add(factionId);
                Log.info("Block stack at " + location + " decreased by " + removedAmount + ". New stack size: " + newAmount);
                blockData.saveAsync(factionId).exceptionally(throwable -> {
                    Log.error("Failed to save block data at " + location + ": " + throwable.getMessage());
                    return null;
                });
            }
        } finally {
            lock.unlock();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockStackChange(BlockStackEvent event) {
        StackedBlock stackedBlock = event.getStack();
        Location location = stackedBlock.getLocation();
        String factionId = FactionUtils.getFactionIdAtLocation(location);
        if (factionId == null) return;

        int currentStackSize = stackedBlock.getStackSize();
        int increaseAmount = event.getIncreaseAmount();
        boolean isNew = event.isNew();
        Log.info("BlockStackEvent at " + location + ": currentStackSize=" + currentStackSize + ", increaseAmount=" + increaseAmount + ", isNew=" + isNew);

        if (event.isNew()) {
            Log.info("Ignoring BlockStackEvent for new block at " + location + ". Handled by onBlockPlace.");
            return;
        }

        ReentrantLock lock = getLockForLocation(location);
        lock.lock();
        try {
            BlockData blockData = trackedBlocks.get(location);
            if (blockData != null) {
                int newAmount = blockData.getAmount() + event.getIncreaseAmount();
                if (newAmount <= 0) {
                    Log.warn("New stack size for block at " + location + " would be " + newAmount + ". Removing from tracking.");
                    trackedBlocks.remove(location);
                    blockData.deleteAsync().exceptionally(throwable -> {
                        Log.error("Failed to delete block data at " + location + ": " + throwable.getMessage());
                        return null;
                    });
                    dirtyFactions.add(factionId);
                    return;
                }
                blockData.setAmount(newAmount);
                dirtyFactions.add(factionId);
                Log.info("Block stack increased at " + location + " by " + event.getIncreaseAmount() + ". New stack size: " + newAmount);
                blockData.saveAsync(factionId).exceptionally(throwable -> {
                    Log.error("Failed to save block data at " + location + ": " + throwable.getMessage());
                    return null;
                });
            } else {
                Log.warn("Block at " + location + " is not tracked during BlockStackEvent. This should not happen.");
            }
        } finally {
            lock.unlock();
        }
    }

    public Material getDominantBlockTypeForFaction(String factionId) {
        rwLock.readLock().lock();
        try {
            Map<String, BlockData> blockData = getBlockData(factionId);
            return blockData.entrySet().stream()
                    .max(Comparator.comparingDouble(entry -> entry.getValue().getCurrentValue()))
                    .map(entry -> Material.getMaterial(entry.getKey()))
                    .orElse(Material.AIR);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public Map<String, BlockData> getBlockData(String factionId) {
        rwLock.readLock().lock();
        try {
            Map<String, BlockData> blockDataMap = new HashMap<>();
            for (Map.Entry<Location, BlockData> entry : trackedBlocks.entrySet()) {
                Location location = entry.getKey();
                BlockData blockData = entry.getValue();
                String locFactionId = blockData.getLocationFactionId();
                if (locFactionId != null && locFactionId.equals(factionId)) {
                    blockDataMap.compute(blockData.getBlockType(), (type, existing) -> {
                        if (existing == null) {
                            return new BlockData(plugin, location, blockData.getPlacedTimestamp(), blockData.getAmount(), type, blockData.isLocked(), factionId);
                        } else {
                            existing.setAmount(existing.getAmount() + blockData.getAmount());
                            return existing;
                        }
                    });
                }
            }
            return blockDataMap;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void saveAll() {
        rwLock.readLock().lock();
        try {
            Map<Location, String> factionIds = new HashMap<>();
            for (Map.Entry<Location, BlockData> entry : trackedBlocks.entrySet()) {
                String factionId = entry.getValue().getLocationFactionId();
                if (factionId != null) {
                    factionIds.put(entry.getKey(), factionId);
                }
            }
            BlockData.saveAllAsync(plugin, trackedBlocks, factionIds)
                    .exceptionally(throwable -> {
                        Log.error("Failed to save all block data: " + throwable.getMessage());
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
                        Location location = state.getLocation();
                        BlockData.loadFromDatabaseAsync(plugin, location)
                                .thenAccept(blockData -> {
                                    if (blockData != null) {
                                        ReentrantLock lock = getLockForLocation(location);
                                        lock.lock();
                                        try {
                                            trackedBlocks.put(location, blockData);
                                            String factionId = blockData.getLocationFactionId();
                                            if (factionId != null && !isSpecialFaction(factionId)) {
                                                factionValues.merge(factionId, blockData.getCurrentValue(), Double::sum);
                                            }
                                        } finally {
                                            lock.unlock();
                                        }
                                    }
                                })
                                .exceptionally(throwable -> {
                                    Log.error("Error loading block data for location " + location + ": " + throwable.getMessage());
                                    return null;
                                });
                    }
                }
            }
            Log.info("Loaded " + trackedBlocks.size() + " blocks from database.");
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
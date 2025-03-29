package com.gmail.thegeekedgamer.slashftop.gui;

import com.gmail.thegeekedgamer.slashftop.data.*;
import com.gmail.thegeekedgamer.slashftop.utils.*;
import com.massivecraft.factions.*;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.jetbrains.annotations.*;
import xyz.xenondevs.invui.item.*;
import xyz.xenondevs.invui.item.builder.*;
import xyz.xenondevs.invui.item.impl.*;

import java.text.*;
import java.util.*;

public class FactionItem extends AbstractItem {
    private final FTopGuiManager guiManager;
    private final Player viewer;
    private final FactionItem.FactionData factionData;
    private int tickCounter = 0;

    public FactionItem(FTopGuiManager guiManager, Player viewer, FactionItem.FactionData factionData) {
        this.guiManager = guiManager;
        this.viewer = viewer;
        this.factionData = factionData;
    }

    @Override
    public ItemProvider getItemProvider() {
        // Fetch the spawners and blocks for the faction
        List<SpawnerData> spawners = guiManager.getSpawnerTracker().getTrackedSpawners().entrySet().stream()
                .filter(entry -> factionData.faction.getId().equals(FactionUtils.getFactionIdAtLocation(entry.getKey())))
                .map(Map.Entry::getValue)
                .toList();
        List<BlockData> blocks = guiManager.getBlockTracker().getTrackedBlocks().entrySet().stream()
                .filter(entry -> factionData.faction.getId().equals(FactionUtils.getFactionIdAtLocation(entry.getKey())))
                .map(Map.Entry::getValue)
                .toList();

        // Calculate the current values on-demand
        double spawnerValue = spawners.stream()
                .mapToDouble(SpawnerData::getCurrentValue)
                .sum();
        double blockValue = blocks.stream()
                .mapToDouble(BlockData::getCurrentValue)
                .sum();
        double totalValue = spawnerValue + blockValue;
        String formattedValue = NumberFormat.getCurrencyInstance(Locale.US).format(totalValue);

        // Compute fully aged value on-demand (this should remain static unless spawners/blocks are added/removed)
        double fullyAgedSpawners = spawners.stream()
                .mapToDouble(SpawnerData::getFullyAgedValue)
                .sum();
        double fullyAgedBlocks = blocks.stream()
                .mapToDouble(BlockData::getFullyAgedValue)
                .sum();
        double fullyAgedValue = fullyAgedSpawners + fullyAgedBlocks;

        String tickAnimation;
        switch (tickCounter % 3) {
            case 0 -> tickAnimation = "[.]";
            case 1 -> tickAnimation = "[..]";
            default -> tickAnimation = "[...]";
        }
        tickCounter++;

        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            OfflinePlayer leader = null;
            if (factionData.admin != null) {
                try {
                    UUID adminUUID = UUID.fromString(factionData.admin.getId());
                    leader = Bukkit.getOfflinePlayer(adminUUID);
                    if (leader.hasPlayedBefore()) {
                        meta.setOwningPlayer(leader);
                        meta.displayName(Component.text(factionData.faction.getTag() + " (§6" + formattedValue + "§e)", NamedTextColor.YELLOW)
                                .decoration(TextDecoration.ITALIC, false));
                        meta.lore(Arrays.asList(
                                Component.text("Leader: ", NamedTextColor.GREEN)
                                        .append(Component.text(leader.getName() != null ? leader.getName() : "Unknown", NamedTextColor.WHITE))
                                        .decoration(TextDecoration.ITALIC, false),
                                Component.text("Total Value: ", NamedTextColor.GREEN)
                                        .append(Component.text(formattedValue, NamedTextColor.WHITE))
                                        .decoration(TextDecoration.ITALIC, false),
                                Component.text("Spawner Value: ", NamedTextColor.GREEN)
                                        .append(Component.text(NumberFormat.getCurrencyInstance(Locale.US).format(spawnerValue), NamedTextColor.WHITE))
                                        .decoration(TextDecoration.ITALIC, false),
                                Component.text("Block Value: ", NamedTextColor.GREEN)
                                        .append(Component.text(NumberFormat.getCurrencyInstance(Locale.US).format(blockValue), NamedTextColor.WHITE))
                                        .decoration(TextDecoration.ITALIC, false),
                                Component.text("Fully Aged Value: ", NamedTextColor.GREEN)
                                        .append(Component.text(NumberFormat.getCurrencyInstance(Locale.US).format(fullyAgedValue), NamedTextColor.WHITE))
                                        .decoration(TextDecoration.ITALIC, false),
                                Component.text("Dominant Block: ", NamedTextColor.GREEN)
                                        .append(Component.text(guiManager.formatBlockType(factionData.dominantBlock), NamedTextColor.WHITE))
                                        .decoration(TextDecoration.ITALIC, false),
                                Component.text("Dominant Spawner: ", NamedTextColor.GREEN)
                                        .append(Component.text(guiManager.formatSpawnerType(factionData.dominantSpawner), NamedTextColor.WHITE))
                                        .decoration(TextDecoration.ITALIC, false),
                                Component.text("Spawners: ", NamedTextColor.GREEN)
                                        .append(Component.text(String.valueOf(factionData.spawnerCount), NamedTextColor.WHITE))
                                        .decoration(TextDecoration.ITALIC, false),
                                Component.text("Blocks: ", NamedTextColor.GREEN)
                                        .append(Component.text(String.valueOf(factionData.blockCount), NamedTextColor.WHITE))
                                        .decoration(TextDecoration.ITALIC, false),
                                Component.text("Click to view details ", NamedTextColor.AQUA)
                                        .append(Component.text(tickAnimation, NamedTextColor.GRAY))
                                        .decoration(TextDecoration.ITALIC, false)
                        ));
                    }
                } catch (Exception e) {
                    meta.displayName(Component.text(factionData.faction.getTag(), NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false));
                    meta.lore(Arrays.asList(
                            Component.text("Leader: ", NamedTextColor.GREEN)
                                    .append(Component.text("Error", NamedTextColor.RED))
                                    .decoration(TextDecoration.ITALIC, false),
                            Component.text("Total Value: ", NamedTextColor.GREEN)
                                    .append(Component.text(formattedValue, NamedTextColor.WHITE))
                                    .decoration(TextDecoration.ITALIC, false),
                            Component.text("Click to view details ", NamedTextColor.AQUA)
                                    .append(Component.text(tickAnimation, NamedTextColor.GRAY))
                                    .decoration(TextDecoration.ITALIC, false)
                    ));
                }
            }
            if (leader == null) {
                meta.displayName(Component.text(factionData.faction.getTag(), NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                meta.lore(Arrays.asList(
                        Component.text("Leader: ", NamedTextColor.GREEN)
                                .append(Component.text("None", NamedTextColor.WHITE))
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("Total Value: ", NamedTextColor.GREEN)
                                .append(Component.text(formattedValue, NamedTextColor.WHITE))
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("Click to view details ", NamedTextColor.AQUA)
                                .append(Component.text(tickAnimation, NamedTextColor.GRAY))
                                .decoration(TextDecoration.ITALIC, false)
                ));
            }
            item.setItemMeta(meta);
        }
        return new ItemBuilder(item);
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent event) {
        guiManager.openDetailGui(viewer, factionData.faction);
    }

    public void update() {
        notifyWindows();
    }

    public static class FactionData {
        public final Faction faction;
        public final FPlayer admin;
        public double spawnerValue; // No longer used in getItemProvider()
        public double blockValue;   // No longer used in getItemProvider()
        public double totalValue;   // No longer used in getItemProvider()
        public double fullyAgedValue;
        public String formattedValue; // No longer used in getItemProvider()
        public final Material dominantBlock;
        public final String dominantSpawner;
        public final int spawnerCount;
        public final long blockCount;
        public String representativeCoordinate;
        public double averageAgePercentage;
        public String timeTillFullyAged;

        public FactionData(Faction faction, FPlayer admin,
                           double totalValue, double spawnerValue, double blockValue,
                           String formattedValue, Material dominantBlock, String dominantSpawner,
                           int spawnerCount, long blockCount) {
            this.faction = faction;
            this.admin = admin;
            this.spawnerValue = spawnerValue;
            this.blockValue = blockValue;
            this.totalValue = totalValue;
            this.fullyAgedValue = 0.0;
            this.formattedValue = formattedValue;
            this.dominantBlock = dominantBlock;
            this.dominantSpawner = dominantSpawner;
            this.spawnerCount = spawnerCount;
            this.blockCount = blockCount;
            this.representativeCoordinate = "N/A";
            this.averageAgePercentage = 0.0;
            this.timeTillFullyAged = "N/A";
        }
    }
}
package com.gmail.thegeekedgamer.slashftop.timer;

import com.gmail.thegeekedgamer.slashftop.*;
import com.gmail.thegeekedgamer.slashftop.config.*;
import com.gmail.thegeekedgamer.slashftop.data.*;
import com.gmail.thegeekedgamer.slashftop.managers.*;
import com.gmail.thegeekedgamer.slashftop.tracking.*;
import com.gmail.thegeekedgamer.slashftop.utils.*;
import com.massivecraft.factions.*;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.event.*;
import net.kyori.adventure.text.format.*;
import org.bukkit.*;
import org.bukkit.scheduler.*;

import java.text.*;
import java.util.*;
import java.util.stream.*;

public class Announcement {

    private final FTopPlugin plugin;
    private final SpawnerTracker spawnerTracker;
    private final BlockTracker blockTracker;
    private final long announcementInterval;
    private List<Faction> previousTopFactions = new ArrayList<>(); // Track previous rankings for overtake detection

    public Announcement(FTopPlugin plugin, FTopManager fTopManager) {
        this.plugin = plugin;
        this.spawnerTracker = plugin.getSpawnerTracker();
        this.blockTracker = plugin.getBlockTracker();
        this.announcementInterval = FTopConfig.getAnnouncementInterval();
    }

    public void startAnnouncements() {
        new BukkitRunnable() {
            @Override
            public void run() {
                announceTopFactions();
            }
        }.runTaskTimer(plugin, 0, announcementInterval * 20L);
    }

    /**
     * Announces the top factions with basic info in chat and breakdown on hover.
     */
    public void announceTopFactions() {
        // Get all valid factions, excluding special ones like Wilderness, WarZone, SafeZone
        List<Faction> allValidFactions = Factions.getInstance().getAllFactions().stream()
                .filter(faction -> !faction.isWilderness() && !faction.isWarZone() && !faction.isSafeZone())
                .sorted(Comparator.comparingDouble(f -> -calculateFactionValue(f)))
                .toList();

        int totalFactions = allValidFactions.size();
        List<Faction> topFactions = allValidFactions.stream().limit(5).collect(Collectors.toList());

        // Check for overtakes
        checkForOvertakes(topFactions);

        // Build and send the message
        List<Component> messages = buildConsolidatedMessage(topFactions, totalFactions);
        for (Component message : messages) {
            Bukkit.getServer().sendMessage(message);
        }

        // Update previous rankings for the next overtake check
        previousTopFactions = new ArrayList<>(topFactions);
    }

    /**
     * Builds a consolidated message with hover events for faction breakdowns.
     *
     * @param topFactions   List of top factions to display
     * @param totalFactions Total number of valid factions
     * @return The list of formatted Components
     */
    private List<Component> buildConsolidatedMessage(List<Faction> topFactions, int totalFactions) {
        List<Component> messages = new ArrayList<>();

        // Header
        Component header = Component.text("Top 5 Factions", NamedTextColor.GREEN, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false);
        messages.add(header);

        // Separator
        Component separator = Component.text("--------------------", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false);
        messages.add(separator);

        // Faction entries
        for (int i = 0; i < topFactions.size(); i++) {
            Faction faction = topFactions.get(i);
            double totalValue = calculateFactionValue(faction);
            String formattedTotalValue = NumberFormat.getCurrencyInstance(Locale.US).format(totalValue);

            // Calculate breakdown values for the hover message
            List<SpawnerData> spawners = spawnerTracker.getTrackedSpawners().entrySet().stream()
                    .filter(entry -> faction.getId().equals(FactionUtils.getFactionIdAtLocation(entry.getKey())))
                    .map(Map.Entry::getValue)
                    .toList();
            double spawnerValue = spawners.stream()
                    .mapToDouble(SpawnerData::getCurrentValue)
                    .sum();
            Map<String, BlockData> blockDataMap = blockTracker.getBlockData(faction.getId());
            double blockValue = blockDataMap.values().stream()
                    .mapToDouble(BlockData::getCurrentValue)
                    .sum();
            double fullyAgedValue = spawners.stream()
                    .mapToDouble(SpawnerData::getFullyAgedValue)
                    .sum() + blockDataMap.values().stream()
                    .mapToDouble(BlockData::getFullyAgedValue)
                    .sum();

            String formattedSpawnerValue = NumberFormat.getCurrencyInstance(Locale.US).format(spawnerValue);
            String formattedBlockValue = NumberFormat.getCurrencyInstance(Locale.US).format(blockValue);
            String formattedFullyAgedValue = NumberFormat.getCurrencyInstance(Locale.US).format(fullyAgedValue);

            // Build the hover text component
            Component hoverText = Component.text("Spawners: ", NamedTextColor.YELLOW)
                    .append(Component.text(formattedSpawnerValue, NamedTextColor.GREEN))
                    .append(Component.newline())
                    .append(Component.text("Blocks: ", NamedTextColor.YELLOW))
                    .append(Component.text(formattedBlockValue, NamedTextColor.GREEN))
                    .append(Component.newline())
                    .append(Component.text("Fully Aged: ", NamedTextColor.YELLOW))
                    .append(Component.text(formattedFullyAgedValue, NamedTextColor.GREEN))
                    .decoration(TextDecoration.ITALIC, false);

            // Build the line components
            Component rankComponent = Component.text("#" + (i + 1) + " ", NamedTextColor.GOLD);
            Component factionNameComponent = Component.text(faction.getTag(), NamedTextColor.YELLOW, TextDecoration.BOLD)
                    .hoverEvent(HoverEvent.showText(hoverText));
            Component totalValueComponent = Component.text(": " + formattedTotalValue, NamedTextColor.GREEN, TextDecoration.BOLD);

            // Combine into a single line
            Component line = rankComponent
                    .append(factionNameComponent)
                    .append(totalValueComponent)
                    .decoration(TextDecoration.ITALIC, false);
            messages.add(line);
        }

        // Footer
        Component footer = Component.text("Total Factions: ", NamedTextColor.AQUA)
                .append(Component.text(totalFactions, NamedTextColor.WHITE))
                .append(Component.text(" (Viewing #1 - #" + topFactions.size() + ")", NamedTextColor.AQUA))
                .decoration(TextDecoration.ITALIC, false);
        messages.add(footer);

        // Final separator
        messages.add(separator);

        return messages;
    }

    /**
     * Checks for overtakes in the top 5 and announces them.
     *
     * @param currentTopFactions The current top 5 factions
     */
    private void checkForOvertakes(List<Faction> currentTopFactions) {
        if (previousTopFactions.isEmpty()) {
            return; // No previous data to compare
        }

        for (int i = 0; i < currentTopFactions.size(); i++) {
            Faction currentFaction = currentTopFactions.get(i);
            if (i >= previousTopFactions.size()) {
                break; // New faction in top 5, no comparison needed
            }
            Faction previousFaction = previousTopFactions.get(i);
            if (!currentFaction.getId().equals(previousFaction.getId())) {
                // An overtake has occurred
                announceOvertake(currentFaction, previousFaction, i + 1);
            }
        }
    }

    /**
     * Announces when a faction overtakes another in the top 5.
     *
     * @param newFaction The faction that moved up
     * @param oldFaction The faction that was overtaken
     * @param rank       The rank position they are now in
     */
    private void announceOvertake(Faction newFaction, Faction oldFaction, int rank) {
        Component message = Component.text("📈 ", NamedTextColor.GREEN)
                .append(Component.text(newFaction.getTag(), NamedTextColor.AQUA))
                .append(Component.text(" has overtaken ", NamedTextColor.GRAY))
                .append(Component.text(oldFaction.getTag(), NamedTextColor.RED))
                .append(Component.text(" for FTop #" + rank + " 📈", NamedTextColor.GREEN))
                .decoration(TextDecoration.ITALIC, false);
        Bukkit.getServer().sendMessage(message);
    }

    /**
     * Calculates the live value of a faction, matching the GUI logic.
     *
     * @param faction The faction to calculate the value for
     * @return The total value (spawners + blocks)
     */
    private double calculateFactionValue(Faction faction) {
        // Same calculation logic as in FactionItem
        List<SpawnerData> spawners = spawnerTracker.getTrackedSpawners().entrySet().stream()
                .filter(entry -> faction.getId().equals(FactionUtils.getFactionIdAtLocation(entry.getKey())))
                .map(Map.Entry::getValue)
                .toList();
        double spawnerValue = spawners.stream()
                .mapToDouble(SpawnerData::getCurrentValue)
                .sum();
        Map<String, BlockData> blockDataMap = blockTracker.getBlockData(faction.getId());
        double blockValue = blockDataMap.values().stream()
                .mapToDouble(BlockData::getCurrentValue)
                .sum();
        return spawnerValue + blockValue;
    }
}
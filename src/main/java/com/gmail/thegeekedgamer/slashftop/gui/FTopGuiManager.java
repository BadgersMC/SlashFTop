package com.gmail.thegeekedgamer.slashftop.gui;

import com.gmail.thegeekedgamer.slashftop.*;
import com.gmail.thegeekedgamer.slashftop.data.*;
import com.gmail.thegeekedgamer.slashftop.tracking.*;
import com.gmail.thegeekedgamer.slashftop.utils.*;
import com.massivecraft.factions.*;
import lombok.*;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.jetbrains.annotations.*;
import xyz.xenondevs.inventoryaccess.component.*;
import xyz.xenondevs.invui.gui.*;
import xyz.xenondevs.invui.gui.structure.*;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.builder.*;
import xyz.xenondevs.invui.item.impl.*;
import xyz.xenondevs.invui.window.*;

import java.text.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

public class FTopGuiManager {

    private final FTopPlugin plugin;
    @Getter
    private final SpawnerTracker spawnerTracker;
    @Getter
    private final BlockTracker blockTracker;
    private final Map<UUID, Boolean> playerInteractionLock = new ConcurrentHashMap<>();

    // Register global ingredients for reuse
    static {
        Structure.addGlobalIngredient('#', new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setDisplayName(""));
        Structure.addGlobalIngredient('x', Markers.CONTENT_LIST_SLOT_HORIZONTAL);
        Structure.addGlobalIngredient('<', BackItem::new);
        Structure.addGlobalIngredient('>', ForwardItem::new);
        Structure.addGlobalIngredient('c', CloseItem::new);
    }

    public FTopGuiManager(FTopPlugin plugin, SpawnerTracker spawnerTracker, BlockTracker blockTracker) {
        this.plugin = plugin;
        this.spawnerTracker = plugin.getSpawnerTracker();
        this.blockTracker = plugin.getBlockTracker();
    }

    public void openFTopGui(Player player) {
        if (playerInteractionLock.getOrDefault(player.getUniqueId(), false)) {
            return;
        }
        playerInteractionLock.put(player.getUniqueId(), true);

        CompletableFuture.supplyAsync(() -> {
            // Step 1: Retrieve top factions (async)
            List<Faction> topFactions = Factions.getInstance().getAllFactions().stream()
                    .filter(f -> !f.isWilderness() && !f.isWarZone() && !f.isSafeZone() && f.getFPlayerAdmin() != null)
                    .sorted(Comparator.comparingDouble(f -> {
                        // Use precomputed values from trackers
                        double blockValue = blockTracker.getTotalBlockValueForFaction(f.getId());
                        double spawnerValue = spawnerTracker.getTotalSpawnerValueForFaction(f.getId());
                        return -(blockValue + spawnerValue);
                    }))
                    .limit(10)
                    .toList();
            Log.info("Top factions retrieved: " + topFactions.size());

            // Step 2: Collect faction data (async)
            List<FactionItem.FactionData> factionDataList = topFactions.stream()
                    .map(faction -> {
                        double spawnerValue = spawnerTracker.getTotalSpawnerValueForFaction(faction.getId());
                        double blockValue = blockTracker.getTotalBlockValueForFaction(faction.getId());
                        double totalValue = spawnerValue + blockValue;

                        // Final safeguard for NaN values
                        if (Double.isNaN(spawnerValue) || Double.isInfinite(spawnerValue)) {
                            Log.error("NaN or infinite spawner value for faction " + faction.getTag() + ". Setting to 0.");
                            spawnerValue = 0.0;
                        }
                        if (Double.isNaN(blockValue) || Double.isInfinite(blockValue)) {
                            Log.error("NaN or infinite block value for faction " + faction.getTag() + ". Setting to 0.");
                            blockValue = 0.0;
                        }
                        totalValue = spawnerValue + blockValue;

                        String formattedValue = NumberFormat.getCurrencyInstance(Locale.US).format(totalValue);
                        Material dominantBlock = blockTracker.getDominantBlockTypeForFaction(faction.getId());
                        String dominantSpawner = spawnerTracker.getDominantSpawnerTypeForFaction(faction.getId());
                        FPlayer admin = faction.getFPlayerAdmin();
                        List<SpawnerData> spawners = factionSpawners(faction);
                        Map<String, BlockData> blockDataMap = blockTracker.getBlockData(faction.getId());
                        int spawnerCount = spawners.stream()
                                .mapToInt(SpawnerData::getStackSize)
                                .sum();
                        long blockCount = blockDataMap.values().stream()
                                .mapToLong(BlockData::getAmount)
                                .sum();

                        // Updated instantiation
                        return new FactionItem.FactionData(
                                faction, admin, totalValue, spawnerValue, blockValue, formattedValue,
                                dominantBlock, dominantSpawner, spawnerCount, blockCount
                        );
                    })
                    .toList();

            // Step 3: Switch to main thread for item creation and GUI setup
            Bukkit.getScheduler().runTask(plugin, () -> {
                List<Item> items = new ArrayList<>();
                List<FactionItem> factionItems = new ArrayList<>(); // Track for updates
                for (FactionItem.FactionData data : factionDataList) {
                    FactionItem factionItem = new FactionItem(this, player, data);
                    items.add(factionItem);
                    factionItems.add(factionItem);
                }

                PagedGui<Item> gui = PagedGui.items()
                        .setStructure(
                                "# # # # # # # # #",
                                "# x x x x x x x #",
                                "# x x x x x x x #",
                                "# x x x x x x x #",
                                "# # # < c > # # #"
                        )
                        .build();

                if (items.isEmpty()) {
                    Log.info("No items generated, adding placeholder");
                    items.add(new SimpleItem(new ItemBuilder(Material.BARRIER)
                            .setDisplayName(new AdventureComponentWrapper(Component.text("No Factions Found", NamedTextColor.RED)
                                    .decoration(TextDecoration.ITALIC, false)))
                            .addLoreLines(new AdventureComponentWrapper(Component.text("There are no factions to display.", NamedTextColor.GRAY)
                                    .decoration(TextDecoration.ITALIC, false)))));
                }
                Log.info("Setting GUI content with " + items.size() + " items");
                gui.setContent(items);

                Window window = Window.single()
                        .setViewer(player)
                        .setTitle(new AdventureComponentWrapper(Component.text("Tᴏᴘ 10 Fᴀᴄᴛɪᴏɴs", NamedTextColor.DARK_AQUA)
                                .decoration(TextDecoration.ITALIC, false)))
                        .setGui(gui)
                        .build();
                window.open();

                // Schedule updates every second
                Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    if (!window.isOpen()) return;
                    factionItems.forEach(FactionItem::update);
                }, 20L, 20L); // 20 ticks = 1 second

                playerInteractionLock.put(player.getUniqueId(), false);
            });
            return null;
        }).exceptionally(throwable -> {
            Log.error("Error opening FTop GUI for player " + player.getName() + ": " + throwable.getMessage());
            playerInteractionLock.put(player.getUniqueId(), false);
            return null;
        });
    }

    public void openDetailGui(Player player, Faction faction) {
        @NotNull PagedGui<Item> gui = PagedGui.items()
                .setStructure(
                        "# # # # # # # # #",
                        "# x x x x x x x #",
                        "# x x x x x x x #",
                        "# x x x x x x x #",
                        "# # # < c > # # #"
                )
                .addIngredient('<', new BackToGuiItem<>("Back to Top Factions", this::openFTopGui))
                .build();

        List<Item> items = new ArrayList<>();
        List<SpawnerItem> spawnerItems = new ArrayList<>();
        List<BlockItem> blockItems = new ArrayList<>();

        // Add spawners
        List<SpawnerData> spawners = factionSpawners(faction);
        for (SpawnerData spawnerData : spawners) {
            SpawnerItem spawnerItem = new SpawnerItem(this, spawnerData);
            items.add(spawnerItem);
            spawnerItems.add(spawnerItem);
        }

        // Add blocks
        Map<String, BlockData> blockDataMap = blockTracker.getBlockData(faction.getId());
        for (Map.Entry<String, BlockData> entry : blockDataMap.entrySet()) {
            BlockItem blockItem = new BlockItem(this, entry.getKey(), entry.getValue());
            items.add(blockItem);
            blockItems.add(blockItem);
        }

        if (items.isEmpty()) {
            items.add(new SimpleItem(new ItemBuilder(Material.BARRIER)
                    .setDisplayName(new AdventureComponentWrapper(Component.text("No Assets Found", NamedTextColor.RED)
                            .decoration(TextDecoration.ITALIC, false)))
                    .addLoreLines(new AdventureComponentWrapper(Component.text("This faction has no spawners or blocks.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)))));
        }

        gui.setContent(items);

        Window window = Window.single()
                .setViewer(player)
                .setTitle(new AdventureComponentWrapper(Component.text("Assets for " + faction.getTag(), NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)))
                .setGui(gui)
                .build();

        window.open();

        // Schedule updates every second for both spawners and blocks
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!window.isOpen()) return;
            spawnerItems.forEach(SpawnerItem::update);
            blockItems.forEach(BlockItem::update);
        }, 20L, 20L); // 20 ticks = 1 second
    }

    public Map<String, Integer> factionSpawners(String factionId) {
        if (factionId == null) {
            Log.warn("factionSpawners called with null factionId. Returning empty map.");
            return new HashMap<>();
        }

        Faction faction = Factions.getInstance().getFactionById(factionId);
        if (faction == null) {
            Log.warn("Faction with ID " + factionId + " does not exist. Returning empty map.");
            return new HashMap<>();
        }

        return plugin.getSpawnerTracker().getTrackedSpawners().entrySet().stream()
                .filter(entry -> {
                    Location location = entry.getKey();
                    String locFactionId = FactionUtils.getFactionIdAtLocation(location);
                    return locFactionId != null && locFactionId.equals(factionId);
                })
                .collect(Collectors.toMap(
                        entry -> entry.getValue().getSpawnerType(),
                        entry -> entry.getValue().getStackSize(),
                        Integer::sum
                ));
    }

    // Add this overload to FTopGuiManager
    public List<SpawnerData> factionSpawners(Faction faction) {
        if (faction == null) {
            Log.warn("factionSpawners called with null faction. Returning empty list.");
            return new ArrayList<>();
        }
        return plugin.getSpawnerTracker().getTrackedSpawners().entrySet().stream()
                .filter(entry -> {
                    Location location = entry.getKey();
                    String locFactionId = FactionUtils.getFactionIdAtLocation(location);
                    return locFactionId != null && locFactionId.equals(faction.getId());
                })
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    public ItemStack getHeadFromSpawnerType(String spawnerType) {
        Material headMaterial = switch (spawnerType.toLowerCase()) {
            case "zombie" -> Material.ZOMBIE_HEAD;
            case "creeper" -> Material.CREEPER_HEAD;
            case "skeleton" -> Material.SKELETON_SKULL;
            case "wither_skeleton" -> Material.WITHER_SKELETON_SKULL;
            case "piglin" -> Material.PIGLIN_HEAD;
            default -> Material.PLAYER_HEAD;
        };
        return new ItemStack(headMaterial);
    }

    public String formatTimeSincePlaced(long timeSincePlaced) {
        Instant instant = Instant.ofEpochMilli(System.currentTimeMillis() - timeSincePlaced);
        ZonedDateTime then = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault());
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        Duration duration = Duration.between(then, now);

        long days = duration.toDays();
        duration = duration.minusDays(days);
        long hours = duration.toHours();
        duration = duration.minusHours(hours);
        long minutes = duration.toMinutes();
        duration = duration.minusMinutes(minutes);
        long seconds = duration.getSeconds();

        List<String> parts = new ArrayList<>();
        if (days > 0) parts.add(days + "d");
        if (hours > 0) parts.add(hours + "h");
        if (minutes > 0) parts.add(minutes + "m");
        if (seconds > 0 || parts.isEmpty()) parts.add(seconds + "s");
        return String.join(" ", parts);
    }

    public String formatSpawnerType(String spawnerType) {
        // Just call the two-argument version, defaulting to false
        return formatSpawnerType(spawnerType, false);
    }

    // The existing two-parameter version, if you still need it
    public String formatSpawnerType(String spawnerType, boolean addPluralSuffix) {
        if (spawnerType == null) return "None";
        String base = spawnerType.substring(0, 1).toUpperCase() + spawnerType.substring(1).toLowerCase();
        return addPluralSuffix ? base + "s" : base;
    }


    public String formatBlockType(Material blockType, boolean addPluralSuffix) {
        if (blockType == null || blockType == Material.AIR) return "None";
        String formatted = Arrays.stream(blockType.name().split("_"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
        return addPluralSuffix ? formatted + "s" : formatted;
    }

    public String formatBlockType(Material blockType) {
        return formatBlockType(blockType, false);
    }
}
package com.gmail.thegeekedgamer.slashftop.gui;

import com.gmail.thegeekedgamer.slashftop.data.*;
import com.gmail.thegeekedgamer.slashftop.utils.*;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.*;
import org.jetbrains.annotations.*;
import xyz.xenondevs.inventoryaccess.component.*;
import xyz.xenondevs.invui.item.*;
import xyz.xenondevs.invui.item.builder.*;
import xyz.xenondevs.invui.item.impl.*;

import java.text.*;
import java.util.*;

public class BlockItem extends AbstractItem {
    private final FTopGuiManager guiManager;
    private final String blockType;
    private BlockData blockData;
    private int tickCounter = 0;

    public BlockItem(FTopGuiManager guiManager, String blockType, BlockData blockData) {
        this.guiManager = guiManager;
        this.blockType = blockType;
        this.blockData = blockData;
    }

    @Override
    public ItemProvider getItemProvider() {
        Material blockMaterial = Material.getMaterial(blockType);
        if (blockMaterial == null) {
            Log.warn("Invalid block material: " + blockType + ", falling back to STONE");
            blockMaterial = Material.STONE;
        }

        long timeSincePlaced = System.currentTimeMillis() - blockData.getPlacedTimestamp();
        String timeFormatted = guiManager.formatTimeSincePlaced(timeSincePlaced);

        // Animation for ticking
        String tickAnimation;
        switch (tickCounter % 3) {
            case 0 -> tickAnimation = "[.]";
            case 1 -> tickAnimation = "[..]";
            default -> tickAnimation = "[...]";
        }
        tickCounter++;

        return new ItemBuilder(blockMaterial)
                .setDisplayName(new AdventureComponentWrapper(Component.text(guiManager.formatBlockType(blockMaterial, true), NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)))
                .addLoreLines(
                        new AdventureComponentWrapper(Component.text("Current Value: ", NamedTextColor.GREEN)
                                .append(Component.text(NumberFormat.getCurrencyInstance(Locale.US).format(blockData.getCurrentValue()), NamedTextColor.WHITE))
                                .decoration(TextDecoration.ITALIC, false)),
                        new AdventureComponentWrapper(Component.text("Fully Aged Value: ", NamedTextColor.GREEN)
                                .append(Component.text(NumberFormat.getCurrencyInstance(Locale.US).format(blockData.getFullyAgedValue()), NamedTextColor.WHITE))
                                .decoration(TextDecoration.ITALIC, false)),
                        new AdventureComponentWrapper(Component.text("Amount: ", NamedTextColor.GREEN)
                                .append(Component.text(blockData.getAmount(), NamedTextColor.WHITE))
                                .decoration(TextDecoration.ITALIC, false)),
                        new AdventureComponentWrapper(Component.text("Time Since Placed: ", NamedTextColor.GRAY)
                                .append(Component.text(timeFormatted, NamedTextColor.WHITE))
                                .decoration(TextDecoration.ITALIC, false)),
                        new AdventureComponentWrapper(Component.text(tickAnimation, NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false))
                );
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent event) {
        // No action on click for now
    }

    public void update() {
        // Fetch the latest BlockData for this block type
        String factionId = blockData.getLocationFactionId();
        if (factionId != null) {
            Map<String, BlockData> blockDataMap = guiManager.getBlockTracker().getBlockData(factionId);
            BlockData updatedData = blockDataMap.get(blockType);
            if (updatedData != null) {
                this.blockData = updatedData;
            }
        } else {
            Log.warn("Cannot update BlockItem for block type " + blockType + " at " + blockData.getLocation() + ": factionId is null.");
        }
        notifyWindows();
    }
}
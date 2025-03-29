package com.gmail.thegeekedgamer.slashftop.gui;

import com.gmail.thegeekedgamer.slashftop.data.*;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.*;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.jetbrains.annotations.*;
import xyz.xenondevs.inventoryaccess.component.*;
import xyz.xenondevs.invui.item.*;
import xyz.xenondevs.invui.item.builder.*;
import xyz.xenondevs.invui.item.impl.*;

import java.text.*;
import java.util.*;

public class SpawnerItem extends AbstractItem {
    private final FTopGuiManager guiManager;
    private final SpawnerData spawnerData;
    private int tickCounter = 0;

    public SpawnerItem(FTopGuiManager guiManager, SpawnerData spawnerData) {
        this.guiManager = guiManager;
        this.spawnerData = spawnerData;
    }

    @Override
    public ItemProvider getItemProvider() {
        ItemStack head = guiManager.getHeadFromSpawnerType(spawnerData.getSpawnerType());
        long timeSincePlaced = System.currentTimeMillis() - spawnerData.getPlacedTimestamp();
        String timeFormatted = guiManager.formatTimeSincePlaced(timeSincePlaced);

        // Animation for ticking
        String tickAnimation;
        switch (tickCounter % 3) {
            case 0 -> tickAnimation = "[.]";
            case 1 -> tickAnimation = "[..]";
            default -> tickAnimation = "[...]";
        }
        tickCounter++;

        return new ItemBuilder(head)
                .setDisplayName(new AdventureComponentWrapper(Component.text(guiManager.formatSpawnerType(spawnerData.getSpawnerType()) + " Spawner", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)))
                .addLoreLines(
                        new AdventureComponentWrapper(Component.text("Current Value: ", NamedTextColor.GREEN)
                                .append(Component.text(NumberFormat.getCurrencyInstance(Locale.US).format(spawnerData.getCurrentValue()), NamedTextColor.WHITE))
                                .decoration(TextDecoration.ITALIC, false)),
                        new AdventureComponentWrapper(Component.text("Fully Aged Value: ", NamedTextColor.GREEN)
                                .append(Component.text(NumberFormat.getCurrencyInstance(Locale.US).format(spawnerData.getFullyAgedValue()), NamedTextColor.WHITE))
                                .decoration(TextDecoration.ITALIC, false)),
                        new AdventureComponentWrapper(Component.text("Stack Size: ", NamedTextColor.GREEN)
                                .append(Component.text(spawnerData.getStackSize(), NamedTextColor.WHITE))
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
        notifyWindows();
    }
}
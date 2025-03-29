package com.gmail.thegeekedgamer.slashftop.gui;

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

public class CloseItem extends AbstractItem {

    @Override
    public ItemProvider getItemProvider() {
        return new ItemBuilder(Material.BARRIER)
                .setDisplayName(new AdventureComponentWrapper(Component.text("Close", NamedTextColor.RED)));
    }
    
    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent event) {
        event.getInventory().close();
    }
}
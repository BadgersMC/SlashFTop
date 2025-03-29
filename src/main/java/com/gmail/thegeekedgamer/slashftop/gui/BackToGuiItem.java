package com.gmail.thegeekedgamer.slashftop.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.inventoryaccess.component.AdventureComponentWrapper;
import xyz.xenondevs.invui.gui.PagedGui;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.controlitem.ControlItem;

import java.util.function.Consumer;

public class BackToGuiItem<T> extends ControlItem<PagedGui<T>> {
    private final Consumer<Player> openGuiAction;
    private final String displayName;

    public BackToGuiItem(String displayName, Consumer<Player> openGuiAction) {
        this.displayName = displayName;
        this.openGuiAction = openGuiAction;
    }

    @Override
    public ItemProvider getItemProvider(PagedGui<T> gui) {
        return new ItemBuilder(Material.ARROW)
                .setDisplayName(new AdventureComponentWrapper(Component.text(displayName, NamedTextColor.RED)));
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent event) {
        openGuiAction.accept(player);
        event.setCancelled(true);
    }
}
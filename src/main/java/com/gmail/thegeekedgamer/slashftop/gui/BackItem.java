package com.gmail.thegeekedgamer.slashftop.gui;

import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.*;
import org.bukkit.*;
import xyz.xenondevs.inventoryaccess.component.*;
import xyz.xenondevs.invui.gui.*;
import xyz.xenondevs.invui.item.*;
import xyz.xenondevs.invui.item.builder.*;
import xyz.xenondevs.invui.item.impl.controlitem.*;

public class BackItem extends PageItem {

    public BackItem() {
        super(false); // false indicates this is the "previous page" item
    }

    @Override
    public ItemProvider getItemProvider(PagedGui<?> gui) {
        ItemBuilder builder = new ItemBuilder(Material.RED_STAINED_GLASS_PANE);
        builder.setDisplayName(new AdventureComponentWrapper(Component.text("Previous Page", NamedTextColor.RED)))
                .addLoreLines(
                        gui.hasPreviousPage()
                                ? new AdventureComponentWrapper(Component.text("Go to page " + gui.getCurrentPage() + "/" + gui.getPageAmount(), NamedTextColor.GRAY))
                                : new AdventureComponentWrapper(Component.text("You can't go further back", NamedTextColor.GRAY))
                );
        return builder;
    }
}
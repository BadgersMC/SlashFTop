package com.gmail.thegeekedgamer.slashftop.gui;

import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.*;
import org.bukkit.*;
import xyz.xenondevs.inventoryaccess.component.*;
import xyz.xenondevs.invui.gui.*;
import xyz.xenondevs.invui.item.*;
import xyz.xenondevs.invui.item.builder.*;
import xyz.xenondevs.invui.item.impl.controlitem.*;

public class ForwardItem extends PageItem {

    public ForwardItem() {
        super(true); // true indicates this is the "next page" item
    }

    @Override
    public ItemProvider getItemProvider(PagedGui<?> gui) {
        ItemBuilder builder = new ItemBuilder(Material.GREEN_STAINED_GLASS_PANE);
        builder.setDisplayName(new AdventureComponentWrapper(Component.text("Next Page", NamedTextColor.GREEN)))
                .addLoreLines(
                        gui.hasNextPage()
                                ? new AdventureComponentWrapper(Component.text("Go to page " + (gui.getCurrentPage() + 2) + "/" + gui.getPageAmount(), NamedTextColor.GRAY))
                                : new AdventureComponentWrapper(Component.text("There are no more pages", NamedTextColor.GRAY))
                );
        return builder;
    }
}
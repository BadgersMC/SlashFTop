package com.gmail.thegeekedgamer.slashftop.utils;

import com.gmail.thegeekedgamer.slashftop.config.*;
import net.kyori.adventure.audience.*;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.*;
import net.kyori.adventure.text.serializer.legacy.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;

public class Log {

    private static final Audience console = Bukkit.getConsoleSender();
    private static final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacy('&');
    private static Component cachedPrefix = Component.text(""); // Default to empty component, or we will get npe on startup since it isn't cached yet

    public static void updatePrefix() {
        cachedPrefix = serializer.deserialize(FTopConfig.getPrefix());
    }

    public static void log(String message) {
        Component component = Component.text()
                .append(cachedPrefix)
                .append(Component.text(" " + message))
                .build();
        console.sendMessage(component);
    }

    public static void warn(String message) {
        Component component = Component.text()
                .append(cachedPrefix)
                .append(Component.text(" [WARNING] " + message, NamedTextColor.YELLOW, TextDecoration.BOLD))
                .build();
        console.sendMessage(component);
    }

    public static void error(String message) {
        Component component = Component.text()
                .append(cachedPrefix)
                .append(Component.text(" [ERROR] " + message, NamedTextColor.RED, TextDecoration.BOLD))
                .build();
        console.sendMessage(component);
    }

    public static void info(String message) {
        if (FTopConfig.isDebugModeEnabled()) {
            Component component = Component.text()
                    .append(cachedPrefix)
                    .append(Component.text(" [INFO] " + message, NamedTextColor.AQUA))
                    .build();
            console.sendMessage(component);
        }
    }

    public static void sendToPlayer(Player player, String message) {
        Component component = Component.text()
                .append(cachedPrefix)
                .append(Component.text(" "))
                .append(serializer.deserialize(message))
                .build();
        player.sendMessage(component);
    }

    public static void sendToSender(CommandSender sender, String message) {
        Component component = Component.text()
                .append(cachedPrefix)
                .append(Component.text(" "))
                .append(serializer.deserialize(message))
                .build();
        sender.sendMessage(component);
    }
}
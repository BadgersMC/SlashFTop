package com.gmail.thegeekedgamer.slashftop.command;

import com.gmail.thegeekedgamer.slashftop.*;
import com.gmail.thegeekedgamer.slashftop.config.*;
import com.gmail.thegeekedgamer.slashftop.gui.*;
import com.gmail.thegeekedgamer.slashftop.managers.*;
import com.gmail.thegeekedgamer.slashftop.utils.*;
import lombok.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.jetbrains.annotations.*;

import java.io.*;

@RequiredArgsConstructor
public class FTopCommand implements CommandExecutor {

    @Getter
    private final FTopPlugin plugin;
    private final FTopGuiManager fTopGuiManager;
    private final FTopManager fTopManager;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        // Handle /ftop (open GUI) or /ftop gui
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            if (!(sender instanceof Player player)) {
                Log.log("&cOnly players can use this command.");
                return true;
            }

            if (!player.hasPermission("slashftop.use")) {
                Log.sendToPlayer(player, "&cYou do not have permission to use this command.");
                return true;
            }

            fTopGuiManager.openFTopGui(player);
            return true;
        }

        // Handle /ftop reload
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("slashftop.reload")) {
                Log.sendToSender(sender, "&cYou do not have permission to reload the config.");
                return true;
            }

            try {
                // Reload the config
                File configFile = new File(plugin.getDataFolder(), "config.yml");
                FTopConfig.init(configFile);

                // Update the cached prefix in Log
                Log.updatePrefix();

                // Restart the recalculation task to apply new settings (e.g., recalculation-threads)
                fTopManager.stopRecalculationTask();
                fTopManager.startRecalculationTask();

                Log.sendToSender(sender, "&aConfiguration reloaded successfully.");
            } catch (IOException e) {
                Log.error("Failed to reload configuration: " + e.getMessage());
                Log.sendToSender(sender, "&cFailed to reload configuration. Check the console for details.");
            }
            return true;
        }

        // Handle /ftop force
        if (args[0].equalsIgnoreCase("force")) {
            if (!sender.hasPermission("slashftop.force")) {
                Log.sendToSender(sender, "&cYou do not have permission to force a recalculation.");
                return true;
            }

            // Run the recalculation asynchronously
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                fTopManager.recalculateFactionValues();
                Log.sendToSender(sender, "&aFaction values recalculated successfully.");
            });
            return true;
        }

        // Unknown subcommand
        Log.sendToSender(sender, "&eUsage: /ftop [gui|reload|force]");
        return true;
    }
}
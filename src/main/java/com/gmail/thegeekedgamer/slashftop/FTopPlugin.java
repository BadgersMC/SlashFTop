package com.gmail.thegeekedgamer.slashftop;

import com.gmail.thegeekedgamer.slashftop.command.*;
import com.gmail.thegeekedgamer.slashftop.config.*;
import com.gmail.thegeekedgamer.slashftop.data.*;
import com.gmail.thegeekedgamer.slashftop.gui.*;
import com.gmail.thegeekedgamer.slashftop.managers.*;
import com.gmail.thegeekedgamer.slashftop.timer.*;
import com.gmail.thegeekedgamer.slashftop.tracking.*;
import com.gmail.thegeekedgamer.slashftop.utils.*;
import lombok.*;
import net.milkbowl.vault.economy.*;
import org.bukkit.*;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.*;
import xyz.xenondevs.invui.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

@Getter
public class FTopPlugin extends JavaPlugin {

  private SpawnerTracker spawnerTracker;
  private BlockTracker blockTracker;
  private IDatabase database;
  private FTopManager fTopManager;
  private FTopGuiManager fTopGuiManager;
  private Announcement announcement;
  private Economy economy;

  @Override
  public void onEnable() {
    // Set the plugin instance for InvUI
    InvUI.getInstance().setPlugin(this);

    // Save default config if it doesn't exist
    try {
      File configFile = new File(getDataFolder(), "config.yml");
      if (!configFile.exists()) {
        saveResource("config.yml", false);
      }
      FTopConfig.init(configFile); // Loads recalculation-threads
    } catch (IOException e) {
      Log.error("Could not load configuration file: " + e.getMessage());
      getServer().getPluginManager().disablePlugin(this);
      return;
    }

    Log.log("&aEnabling FTop Plugin...");

    // Dependency checks
    if (!checkDependencies()) {
      getServer().getPluginManager().disablePlugin(this);
      return;
    }

    // Initialize economy
    RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
    if (rsp == null) {
      Log.error("Vault economy provider not found! Disabling plugin.");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    this.economy = rsp.getProvider();

    // Initialize database
    this.database = FTopConfig.getDatabaseType().equalsIgnoreCase("mysql") ? new MySQL(this) : new SQLite(this);
    if (!this.database.connect()) {
      Log.error("Failed to connect to the database. Disabling plugin.");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }

    // Initialize trackers and managers
    this.spawnerTracker = new SpawnerTracker(this, economy);
    this.blockTracker = new BlockTracker(this, economy);
    this.fTopGuiManager = new FTopGuiManager(this, spawnerTracker, blockTracker);
    this.fTopManager = new FTopManager(this, spawnerTracker, blockTracker);
    this.announcement = new Announcement(this, fTopManager);

    // Load data from database
    this.spawnerTracker.loadAllFromDatabase();
    this.blockTracker.loadAllFromDatabase();

    // Sync with RoseStacker and load spawners/blocks immediately
    this.spawnerTracker.syncWithRoseStacker();
    this.blockTracker.syncWithRoseStacker();
    this.spawnerTracker.loadSpawnersFromRoseStacker();
    this.blockTracker.loadBlocksFromRoseStacker();

    // Start tasks
    this.fTopManager.startRecalculationTask();
    this.announcement.startAnnouncements();

    // Register listeners and commands
    getServer().getPluginManager().registerEvents(this.spawnerTracker, this);
    getServer().getPluginManager().registerEvents(this.blockTracker, this);
    Objects.requireNonNull(getCommand("ftop")).setExecutor(new FTopCommand(this, fTopGuiManager, fTopManager));

    // Delayed initialization (1 minute after server start)
    getServer().getScheduler().runTaskLater(this, () -> {
      Log.info("Starting delayed initialization of spawners and blocks...");
      spawnerTracker.loadSpawnersFromRoseStacker();
      blockTracker.loadBlocksFromRoseStacker();
      Log.info("Completed initial load of spawners and blocks from RoseStacker.");
      fTopManager.startRecalculationTask();
      announcement.startAnnouncements();
    }, 20 * 60L);

    Log.log("&aFTop Plugin Enabled successfully.");
  }

  @Override
  public void onDisable() {
    Log.log("&cDisabling FTop Plugin...");

    // Cancel all scheduled tasks
    getServer().getScheduler().cancelTasks(this);

    // Save spawner and block data asynchronously
    CompletableFuture<Void> saveSpawnersFuture = CompletableFuture.completedFuture(null);
    CompletableFuture<Void> saveBlocksFuture = CompletableFuture.completedFuture(null);

    if (this.spawnerTracker != null) {
      Map<Location, String> spawnerFactionIds = new HashMap<>();
      for (Map.Entry<Location, SpawnerData> entry : spawnerTracker.getTrackedSpawners().entrySet()) {
        String factionId = FactionUtils.getFactionIdAtLocation(entry.getKey());
        if (factionId != null) {
          spawnerFactionIds.put(entry.getKey(), factionId);
        }
      }
      saveSpawnersFuture = SpawnerData.saveAllAsync(this, spawnerTracker.getTrackedSpawners(), spawnerFactionIds)
              .exceptionally(throwable -> {
                Log.error("Failed to save spawner data on disable: " + throwable.getMessage());
                return null;
              });
    }

    if (this.blockTracker != null) {
      Map<Location, String> blockFactionIds = new HashMap<>();
      for (Map.Entry<Location, BlockData> entry : blockTracker.getTrackedBlocks().entrySet()) {
        String factionId = FactionUtils.getFactionIdAtLocation(entry.getKey());
        if (factionId != null) {
          blockFactionIds.put(entry.getKey(), factionId);
        }
      }
      saveBlocksFuture = BlockData.saveAllAsync(this, blockTracker.getTrackedBlocks(), blockFactionIds)
              .exceptionally(throwable -> {
                Log.error("Failed to save block data on disable: " + throwable.getMessage());
                return null;
              });
    }

    // Wait for both save operations to complete before proceeding
    try {
      CompletableFuture.allOf(saveSpawnersFuture, saveBlocksFuture).join();
    } catch (Exception e) {
      Log.error("Error while waiting for save operations to complete: " + e.getMessage());
    }

    // Disconnect from database
    if (this.database != null) {
      database.disconnect();
    }

    Log.log("&cFTop Plugin Disabled successfully.");
  }

  private boolean checkDependencies() {
    if (getServer().getPluginManager().getPlugin("Factions") == null) {
      Log.error("Factions plugin not found! Disabling plugin.");
      return false;
    }
    if (getServer().getPluginManager().getPlugin("RoseStacker") == null) {
      Log.error("RoseStacker plugin not found! Disabling plugin.");
      return false;
    }
    if (getServer().getPluginManager().getPlugin("Vault") == null) {
      Log.error("Vault plugin not found! Disabling plugin.");
      return false;
    }
    return true;
  }
}
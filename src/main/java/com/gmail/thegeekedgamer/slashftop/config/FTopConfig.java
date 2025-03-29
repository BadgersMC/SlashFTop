package com.gmail.thegeekedgamer.slashftop.config;

import com.gmail.thegeekedgamer.slashftop.utils.*;
import org.avarion.yaml.*;

import java.io.*;
import java.util.*;

public class FTopConfig extends YamlFileInterface {

    public static FTopConfig configInstance;

    public static void init(File configFile) throws IOException {
        configInstance = new FTopConfig();
        configInstance.load(configFile);
        Log.updatePrefix();
    }

    // -=[DATABASE SETTINGS]=-
    @YamlKey("database.type")
    @YamlComment("Database type: 'sqlite' or 'mysql'")
    public String DATABASE_TYPE = "sqlite";

    @YamlKey("database.host")
    @YamlComment("Database host for MySQL (ignored for SQLite)")
    public String DATABASE_HOST = "localhost";

    @YamlKey("database.port")
    @YamlComment("Database port for MySQL (ignored for SQLite)")
    public int DATABASE_PORT = 3306;

    @YamlKey("database.name")
    @YamlComment("Database name (file name for SQLite or schema name for MySQL)")
    public String DATABASE_NAME = "ftop_db";

    @YamlKey("database.username")
    @YamlComment("Username for MySQL authentication")
    public String DATABASE_USERNAME = "user";

    @YamlKey("database.password")
    @YamlComment("Password for MySQL authentication")
    public String DATABASE_PASSWORD = "password";

    // -=[FTOP SETTINGS]=-
    @YamlKey("ftop.spawner-values")
    @YamlComment("Base values for spawners by type")
    public Map<String, Double> SPAWNER_VALUES = new HashMap<>(Map.of(
            "ZOMBIE", 1000.0,
            "SKELETON", 1000.0,
            "SPIDER", 800.0,
            "CREEPER", 1200.0
    ));

    @YamlKey("ftop.block-values")
    @YamlComment("Base values for blocks by type")
    public Map<String, Double> BLOCK_VALUES = new HashMap<>(Map.of(
            "DIAMOND_BLOCK", 1000.0,
            "EMERALD_BLOCK", 800.0,
            "GOLD_BLOCK", 500.0,
            "IRON_BLOCK", 200.0
    ));

    @YamlKey("ftop.calculation-interval")
    @YamlComment("Interval (in seconds) to recalculate FTop values")
    public int FTOP_CALCULATION_INTERVAL = 300;

    @YamlKey("ftop.async-value-update")
    @YamlComment("Whether to calculate FTop values asynchronously to reduce performance impact")
    public boolean FTOP_ASYNC_VALUE_UPDATE = true;

    @YamlKey("ftop.default-spawner-value")
    @YamlComment("Default base value of a spawner when placed")
    public double FTOP_DEFAULT_SPAWNER_VALUE = 1000.0;

    @YamlKey("ftop.placeholder-percentage-decimals")
    @YamlComment("Number of decimal places for displaying percentages in placeholders")
    public int FTOP_PLACEHOLDER_PERCENTAGE_DECIMALS = 2;

    @YamlKey("ftop.announcement-interval")
    @YamlComment("Interval (in seconds) to announce FTop standings")
    public int FTOP_ANNOUNCEMENT_INTERVAL = 600;

    @YamlKey("ftop.recalculation-threads")
    @YamlComment("Number of threads to use for faction value recalculation (1 to available processors)")
    public int FTOP_RECALCULATION_THREADS = 4;

    // -=[AGING SETTINGS]=-
    @YamlKey("aging.duration-days")
    @YamlComment("Number of days over which a spawner or block's value grows to 100%")
    public int AGING_DURATION_DAYS = 4;

    @YamlKey("aging.starting-percentage")
    @YamlComment("Starting percentage of a spawner or block's value when placed (1.0 = 1%)")
    public double AGING_STARTING_PERCENTAGE = 1.0;

    @YamlKey("aging.lock-grace-period-minutes")
    @YamlComment("Number of minutes before a newly placed spawner or block locks (can be broken for free during this time)")
    public int AGING_LOCK_GRACE_PERIOD_MINUTES = 5;

    @YamlKey("aging.locked-break-cost-percentage")
    @YamlComment("Percentage of a spawner or block's current value required to break it after locking (50.0 = 50%)")
    public double AGING_LOCKED_BREAK_COST_PERCENTAGE = 50.0;

    // -=[MISC SETTINGS]=-
    @YamlKey("misc.debug-mode")
    @YamlComment("Toggle debug mode for detailed logs")
    public boolean MISC_DEBUG_MODE = false;

    @YamlKey("misc.lang")
    @YamlComment("Language file to use for plugin messages")
    public String MISC_LANGUAGE_FILE = "en.yml";

    @YamlKey("misc.prefix")
    @YamlComment("Prefix used for messages in chat and logs")
    public String MISC_PREFIX = "&6[SlashFTop]";

    // -=[METHODS FOR FEATURE TOGGLES]=-
    public static boolean isDebugModeEnabled() {
        return configInstance != null && configInstance.MISC_DEBUG_MODE;
    }

    public static String getDatabaseType() {
        return configInstance != null ? configInstance.DATABASE_TYPE : "sqlite";
    }

    public static String getDatabaseHost() {
        return configInstance != null ? configInstance.DATABASE_HOST : "localhost";
    }

    public static int getDatabasePort() {
        return configInstance != null ? configInstance.DATABASE_PORT : 3306;
    }

    public static String getDatabaseName() {
        return configInstance != null ? configInstance.DATABASE_NAME : "ftop_db";
    }

    public static String getDatabaseUsername() {
        return configInstance != null ? configInstance.DATABASE_USERNAME : "user";
    }

    public static String getDatabasePassword() {
        return configInstance != null ? configInstance.DATABASE_PASSWORD : "password";
    }

    public static int getFtopCalculationInterval() {
        return configInstance != null ? configInstance.FTOP_CALCULATION_INTERVAL : 300;
    }

    public static boolean isAsyncValueUpdateEnabled() {
        return configInstance != null && configInstance.FTOP_ASYNC_VALUE_UPDATE;
    }

    public static double getDefaultSpawnerValue() {
        return configInstance != null ? configInstance.FTOP_DEFAULT_SPAWNER_VALUE : 1000.0;
    }

    public static int getPlaceholderPercentageDecimals() {
        return configInstance != null ? configInstance.FTOP_PLACEHOLDER_PERCENTAGE_DECIMALS : 2;
    }

    public static String getPrefix() {
        return configInstance != null ? configInstance.MISC_PREFIX : "&6[SlashFTop]";
    }

    public static double getSpawnerBaseValue(String spawnerType) {
        if (configInstance == null || configInstance.SPAWNER_VALUES == null) {
            return getDefaultSpawnerValue();
        }
        return configInstance.SPAWNER_VALUES.getOrDefault(spawnerType.toUpperCase(), getDefaultSpawnerValue());
    }

    public static double getBlockBaseValue(String blockType) {
        if (configInstance == null || configInstance.BLOCK_VALUES == null) {
            return 0.0;
        }
        return configInstance.BLOCK_VALUES.getOrDefault(blockType.toUpperCase(), 0.0);
    }

    public static long getAgingDurationMillis() {
        return configInstance != null ? configInstance.AGING_DURATION_DAYS * 24L * 60 * 60 * 1000 : 4 * 24L * 60 * 60 * 1000;
    }

    public static double getStartingPercentage() {
        return configInstance != null ? configInstance.AGING_STARTING_PERCENTAGE / 100.0 : 0.01;
    }

    public static long getLockGracePeriodTicks() {
        return configInstance != null ? configInstance.AGING_LOCK_GRACE_PERIOD_MINUTES * 60 * 20L : 5 * 60 * 20L;
    }

    public static double getLockedBreakCostPercentage() {
        return configInstance != null ? configInstance.AGING_LOCKED_BREAK_COST_PERCENTAGE / 100.0 : 0.5;
    }

    public static int getAnnouncementInterval() {
        return configInstance != null ? configInstance.FTOP_ANNOUNCEMENT_INTERVAL : 600;
    }

    public static int getRecalculationThreadCount() {
        if (configInstance == null) {
            return 4; // Default if configInstance isn't initialized
        }
        int threadCount = configInstance.FTOP_RECALCULATION_THREADS;
        if (threadCount < 1) {
            Log.warn("Recalculation thread count cannot be less than 1. Using 1.");
            return 1;
        }
        int maxThreads = Runtime.getRuntime().availableProcessors();
        if (threadCount > maxThreads) {
            Log.warn("Recalculation thread count (" + threadCount + ") exceeds available processors (" + maxThreads + "). Setting to " + maxThreads);
            return maxThreads;
        }
        return threadCount;
    }
}
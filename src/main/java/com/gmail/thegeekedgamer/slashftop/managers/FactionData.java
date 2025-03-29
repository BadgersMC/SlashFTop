package com.gmail.thegeekedgamer.slashftop.managers;

import com.massivecraft.factions.*;
import org.bukkit.*;

public class FactionData {
    public final Faction faction;
    public final FPlayer admin;
    public double totalValue;
    public double spawnerValue;
    public double blockValue;
    public double fullyAgedValue;
    public String formattedValue;
    public Material dominantBlock;
    public String dominantSpawner;
    public int spawnerCount;
    public long blockCount;
    public String representativeCoordinate;
    public double averageAgePercentage;
    public String timeTillFullyAged;

    public FactionData(
            Faction faction,
            FPlayer admin,
            double totalValue,
            double spawnerValue,
            double blockValue,
            double fullyAgedValue,
            String formattedValue,
            Material dominantBlock,
            String dominantSpawner,
            int spawnerCount,
            long blockCount,
            String representativeCoordinate,
            double averageAgePercentage,
            String timeTillFullyAged
    ) {
        this.faction = faction;
        this.admin = admin;
        this.totalValue = totalValue;
        this.spawnerValue = spawnerValue;
        this.blockValue = blockValue;
        this.fullyAgedValue = fullyAgedValue;
        this.formattedValue = formattedValue;
        this.dominantBlock = dominantBlock;
        this.dominantSpawner = dominantSpawner;
        this.spawnerCount = spawnerCount;
        this.blockCount = blockCount;
        this.representativeCoordinate = representativeCoordinate;
        this.averageAgePercentage = averageAgePercentage;
        this.timeTillFullyAged = timeTillFullyAged;
    }
}
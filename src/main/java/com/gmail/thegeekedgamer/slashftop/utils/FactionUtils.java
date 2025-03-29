package com.gmail.thegeekedgamer.slashftop.utils;

import com.massivecraft.factions.*;
import org.bukkit.*;

import java.util.*;

public class FactionUtils {

    public static Collection<Chunk> getFactionChunks(String factionId) {
        Collection<Chunk> claimedChunks = new ArrayList<>();
        Board board = Board.getInstance();
        Faction faction = Factions.getInstance().getFactionById(factionId);

        if (faction == null) {
            Log.warn("Faction with ID " + factionId + " not found.");
            return claimedChunks;
        }

        Set<FLocation> factionClaims = board.getAllClaims(faction);
        for (FLocation fLocation : factionClaims) {
            World world = Bukkit.getWorld(fLocation.getWorldName());
            if (world == null) {
                Log.warn("World " + fLocation.getWorldName() + " not found for faction claim.");
                continue;
            }
            Chunk chunk = world.getChunkAt((int) fLocation.getX(), (int) fLocation.getZ());
            claimedChunks.add(chunk);
        }

        return claimedChunks;
    }

    public static String getFactionIdAtLocation(Location location) {
        if (location == null) {
            return null;
        }

        FLocation fLocation = new FLocation(location);
        Faction faction = Board.getInstance().getFactionAt(fLocation);

        if (faction == null || faction.isWilderness()) {
            return null;
        }

        return faction.getId();
    }

    public static String locationToString(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Location or world cannot be null");
        }
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    public static Location stringToLocation(String locationStr) {
        String[] parts = locationStr.split(":");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid location string format: " + locationStr);
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            throw new IllegalArgumentException("World not found: " + parts[0]);
        }
        int x = Integer.parseInt(parts[1]);
        int y = Integer.parseInt(parts[2]);
        int z = Integer.parseInt(parts[3]);
        return new Location(world, x, y, z);
    }
}
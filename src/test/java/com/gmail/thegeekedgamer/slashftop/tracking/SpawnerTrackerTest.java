package com.thegeekedgamer.slashftop.tracking;

import com.gmail.thegeekedgamer.slashftop.*;
import com.gmail.thegeekedgamer.slashftop.data.*;
import com.gmail.thegeekedgamer.slashftop.tracking.*;
import net.milkbowl.vault.economy.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.event.block.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SpawnerTrackerTest {

    @Mock
    private FTopPlugin plugin;

    @Mock
    private Economy economy;

    @Mock
    private Block block;

    @Mock
    private CreatureSpawner creatureSpawner;

    @Mock
    private BlockPlaceEvent event;

    private SpawnerTracker spawnerTracker;

    @BeforeEach
    public void setUp() {
        spawnerTracker = new SpawnerTracker(plugin, economy);
        when(block.getType()).thenReturn(Material.SPAWNER);
        when(block.getState()).thenReturn(creatureSpawner);
        when(creatureSpawner.getSpawnedType()).thenReturn(org.bukkit.entity.EntityType.PIG);
        when(block.getLocation()).thenReturn(new Location(null, 0, 0, 0));
        when(event.getBlock()).thenReturn(block);
    }

    @Test
    public void testOnBlockPlace_TracksSingleSpawner() {
        // Arrange
        // No stacked spawner, so RoseStackerAPI.getInstance().getStackedSpawner(block) returns null
        // (This is handled by MockitoExtension, which returns null by default for unstubbed methods)

        // Act
        spawnerTracker.onBlockPlace(event);

        // Assert
        Location location = block.getLocation();
        SpawnerData spawnerData = spawnerTracker.getTrackedSpawners().get(location);
        assert spawnerData != null : "Spawner should be tracked";
        assert spawnerData.getStackSize() == 1 : "Stack size should be 1 for a single spawner";
        assert spawnerData.getSpawnerType().equals("PIG") : "Spawner type should be PIG";
    }
}
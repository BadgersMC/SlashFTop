package com.gmail.thegeekedgamer.slashftop.tracking;

import com.gmail.thegeekedgamer.slashftop.*;
import com.gmail.thegeekedgamer.slashftop.data.*;
import dev.rosewood.rosestacker.api.*;
import net.milkbowl.vault.economy.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.event.block.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;

import static org.junit.jupiter.api.Assertions.*;
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

    @Mock
    private RoseStackerAPI roseStackerAPI;

    private SpawnerTracker spawnerTracker;

    @BeforeEach
    public void setUp() {
        // Mock RoseStackerAPI.getInstance() to return our mock instance
        mockStatic(RoseStackerAPI.class);
        when(RoseStackerAPI.getInstance()).thenReturn(roseStackerAPI);

        spawnerTracker = new SpawnerTracker(plugin, economy);
        when(block.getType()).thenReturn(Material.SPAWNER);
        when(block.getState()).thenReturn(creatureSpawner);
        when(creatureSpawner.getSpawnedType()).thenReturn(org.bukkit.entity.EntityType.PIG);
        when(block.getLocation()).thenReturn(new Location(null, 0, 0, 0));
        when(event.getBlock()).thenReturn(block);
        // Mock RoseStackerAPI.getStackedSpawner(block) to return null (no stacked spawner)
        when(roseStackerAPI.getStackedSpawner(block)).thenReturn(null);
    }

    @Test
    public void testOnBlockPlace_TracksSingleSpawner() {
        // Act
        spawnerTracker.onBlockPlace(event);

        // Assert
        Location location = block.getLocation();
        SpawnerData spawnerData = spawnerTracker.getTrackedSpawners().get(location);
        assertNotNull(spawnerData, "Spawner should be tracked");
        assertEquals(1, spawnerData.getStackSize(), "Stack size should be 1 for a single spawner");
        assertEquals("PIG", spawnerData.getSpawnerType(), "Spawner type should be PIG");
    }
}
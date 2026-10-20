package com.riddleslabs.trails.quest;

import com.riddleslabs.trails.Route.Point;
import java.util.List;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WalkingConnectorTest {
    static Block shape(BoundingBox... boxes) {
        Block block = mock(Block.class);
        VoxelShape shape = mock(VoxelShape.class);
        when(shape.getBoundingBoxes()).thenReturn(List.of(boxes));
        when(block.getCollisionShape()).thenReturn(shape);
        return block;
    }

    static World flat() {
        World world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        Block air = shape(), solid = shape(new BoundingBox(0, 0, 0, 1, 1, 1));
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call -> (int) call.getArgument(1) < 64 ? solid : air);
        return world;
    }

    @Test void immediatelyConnectsFromWellOutsideTheOldJoinRadius() {
        var result = WalkingConnector.find(flat(), new Point(.5, 64, .5), new Point(30.5, 64, .5), 256);
        assertNotNull(result);
        assertTrue(result.complete());
        assertEquals(new Point(30.5, 64, .5), result.points().getLast());
    }

    @Test void findsADetourAroundAWallInsteadOfDrawingThroughIt() {
        World world = flat();
        Block solid = shape(new BoundingBox(0, 0, 0, 1, 1, 1));
        for (int z = -1; z <= 1; z++) for (int y = 64; y <= 67; y++) when(world.getBlockAt(2, y, z)).thenReturn(solid);
        var result = WalkingConnector.find(world, new Point(.5, 64, .5), new Point(5.5, 64, .5), 256);
        assertNotNull(result);
        assertTrue(result.complete());
        assertTrue(result.points().stream().anyMatch(p -> Math.abs(p.z() - .5) >= 2));
    }

    @Test void staircaseSamplesLiftOntoSlabsAndDoNotPassThroughFullWalls() {
        World world = flat();
        Block half = shape(new BoundingBox(0, 0, 0, 1, .5, 1));
        when(world.getBlockAt(1, 64, 0)).thenReturn(half);
        Point lifted = SafeCorridor.adjust(world, new Point(1.2, 64.2, .5));
        assertNotNull(lifted);
        assertEquals(64.5, lifted.y());
        Block solid = shape(new BoundingBox(0, 0, 0, 1, 1, 1));
        for (int y = 64; y <= 67; y++) when(world.getBlockAt(2, y, 0)).thenReturn(solid);
        assertNull(SafeCorridor.adjust(world, new Point(2.5, 64, .5)));
    }

    @Test void distantTargetsReturnProgressAndUnloadedTerrainIsNeverRead() {
        World world = flat();
        var partial = WalkingConnector.find(world, new Point(.5, 64, .5), new Point(200.5, 64, .5), 256);
        assertNotNull(partial);
        assertFalse(partial.complete());
        assertEquals(64.5, partial.points().getLast().x());
        World unloaded = mock(World.class);
        when(unloaded.getMinHeight()).thenReturn(-64);
        when(unloaded.getMaxHeight()).thenReturn(320);
        assertNull(WalkingConnector.find(unloaded, new Point(.5, 64, .5), new Point(20.5, 64, .5), 256));
        verify(unloaded, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }
}

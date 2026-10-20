package com.riddleslabs.trails.quest;

import com.riddleslabs.trails.Route;
import java.util.List;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SafeCorridorTest {
    @Test void snapshotReusesCollisionShapesAndNextUpdateSeesBlockChanges() {
        World world=WalkingConnectorTest.flat();
        for(int i=0;i<=100;i++)assertNotNull(SafeCorridor.adjust(world,new Route.Point(.5+i*.2,64,.5)));
        long uncached=mockingDetails(world).getInvocations().stream().filter(i->i.getMethod().getName().equals("getBlockAt")).count();
        clearInvocations(world);
        var snapshot=new SafeCorridor.Snapshot(world);
        for(int i=0;i<=100;i++)assertNotNull(snapshot.adjust(new Route.Point(.5+i*.2,64,.5)));
        long cached=mockingDetails(world).getInvocations().stream().filter(i->i.getMethod().getName().equals("getBlockAt")).count();
        assertTrue(cached<uncached/4,"Cached block queries: "+cached+" vs "+uncached);
        Block obstacle=WalkingConnectorTest.shape(new BoundingBox(0,0,0,1,1,1));
        when(world.getBlockAt(3,65,0)).thenReturn(obstacle);
        assertNull(new SafeCorridor.Snapshot(world).adjust(new Route.Point(3.5,64,.5)));
        when(world.isChunkLoaded(0,0)).thenReturn(false);clearInvocations(world);
        assertFalse(new SafeCorridor.Snapshot(world).clear(new Route.Point(3.5,64,.5)));
        verify(world,never()).getBlockAt(anyInt(),anyInt(),anyInt());
    }
    @Test void translatesLocalCollisionBoxesAndDetectsLowCeilingsAndFences() {
        World world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        Block air = block(List.of());
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);
        var feet = new Route.Point(100.5, 64, 100.5);
        assertTrue(SafeCorridor.clear(world, feet));
        Block solid = block(List.of(new BoundingBox(0, 0, 0, 1, 1, 1)));
        when(world.getBlockAt(100, 65, 100)).thenReturn(solid);
        assertFalse(SafeCorridor.clear(world, feet));
        when(world.getBlockAt(100, 65, 100)).thenReturn(air);
        Block fence = block(List.of(new BoundingBox(.25, 0, .25, .75, 1.5, .75)));
        when(world.getBlockAt(100, 63, 100)).thenReturn(fence);
        assertFalse(SafeCorridor.clear(world, feet));
        assertFalse(SafeCorridor.clear(world, new Route.Point(Double.MAX_VALUE, 64, 0)));
    }

    private Block block(List<BoundingBox> boxes) {
        Block block = mock(Block.class);
        VoxelShape shape = mock(VoxelShape.class);
        when(shape.getBoundingBoxes()).thenReturn(boxes);
        when(block.getCollisionShape()).thenReturn(shape);
        return block;
    }
}

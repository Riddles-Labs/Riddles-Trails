package com.riddleslabs.trails.quest;

import com.riddleslabs.trails.Route;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import java.util.*;

/** Conservative loaded-block clearance checks for configured routes, not a pathfinder. */
final class SafeCorridor {
    private SafeCorridor() {}

    static Route.Point adjust(World world,Route.Point point) {return new Snapshot(world).adjust(point);}
    static Route.Point stand(World world,Route.Point point,double rise,double drop) {return new Snapshot(world).stand(point,rise,drop);}
    static boolean clear(World world,Route.Point point) {return new Snapshot(world).clear(point);}

    /** Only for one entity-owned render/search. Never retain across ticks or world changes. */
    static final class Snapshot {
        private record BlockKey(int x,int y,int z) {}
        private record Bounds(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {}
        private final World world;
        private final int minHeight,maxHeight;
        private final Map<BlockKey,List<Bounds>> collisions=new HashMap<>();
        private final Map<Long,Boolean> chunks=new HashMap<>();
        Snapshot(World world) {this.world=world;minHeight=world.getMinHeight();maxHeight=world.getMaxHeight();}
        private boolean loaded(int x,int z) {
            long key=((long)(x>>4)<<32)|((z>>4)&0xffffffffL);
            // Loaded does not imply owned on Folia. Never read a neighbouring region's blocks.
            return chunks.computeIfAbsent(key, ignored -> {
                int cx = x >> 4, cz = z >> 4;
                // Collision shapes can consult neighbouring blocks, so include a chunk halo.
                if (!com.riddleslabs.paper.api.scheduler.PlatformThreads.owns(world, cx, cz, 1)) return false;
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                    if (!world.isChunkLoaded(cx + dx, cz + dz)) return false;
                return true;
            });
        }
        private List<Bounds> boxes(int x,int y,int z) {
            BlockKey key=new BlockKey(x,y,z);List<Bounds> cached=collisions.get(key);
            if(cached!=null)return cached;
            Collection<BoundingBox> shape=world.getBlockAt(x,y,z).getCollisionShape().getBoundingBoxes();
            List<Bounds> result;
            if(shape.isEmpty())result=List.of();
            else {
                List<Bounds> copy=new ArrayList<>(shape.size());
                for(BoundingBox b:shape)copy.add(new Bounds(x+b.getMinX(),y+b.getMinY(),z+b.getMinZ(),x+b.getMaxX(),y+b.getMaxY(),z+b.getMaxZ()));
                result=List.copyOf(copy);
            }
            // Bound temporary memory even for the largest configured A* search.
            if(collisions.size()<16384)collisions.put(key,result);
            return result;
        }

        /** Lift interpolated stair/slab samples onto the step, without lifting through full walls. */
        Route.Point adjust(Route.Point point) {
            if (clear(point)) return point;
            for (double y : surfaces(point, 1.05, 0)) {
                Route.Point lifted = new Route.Point(point.x(), y, point.z());
                if (clear(lifted)) return lifted;
            }
            return null;
        }

        /** A supported, player-sized walking position for the connector search. */
        Route.Point stand(Route.Point point, double rise, double drop) {
            for (double y : surfaces(point, rise, drop)) {
                Route.Point feet = new Route.Point(point.x(), y, point.z());
                if (clear(feet)) return feet;
            }
            return null;
        }

        private List<Double> surfaces(Route.Point point, double rise, double drop) {
            if (Math.abs(point.x()) > 30_000_000 || Math.abs(point.z()) > 30_000_000
                    || point.y() < minHeight || point.y() > maxHeight) return List.of();
            Set<Double> heights = new HashSet<>();
            for (int x = (int) Math.floor(point.x() - .3); x <= (int) Math.floor(point.x() + .3); x++) {
                for (int z = (int) Math.floor(point.z() - .3); z <= (int) Math.floor(point.z() + .3); z++) {
                    if (!loaded(x,z)) return List.of();
                    for (int y = Math.max(minHeight, (int) Math.floor(point.y() - drop) - 1);
                         y <= Math.min(maxHeight - 1, (int) Math.floor(point.y() + rise)); y++) {
                        for (Bounds box : boxes(x,y,z)) {
                            double top = box.maxY;
                            if (top >= point.y() - drop - .01 && top <= point.y() + rise + .01
                                    && box.maxX > point.x() - .3 && box.minX < point.x() + .3
                                    && box.maxZ > point.z() - .3 && box.minZ < point.z() + .3) heights.add(top);
                        }
                    }
                }
            }
            return heights.stream().sorted(Comparator.comparingDouble(y -> Math.abs(y - point.y()))).toList();
        }

        boolean clear(Route.Point point) {
            if (Math.abs(point.x()) > 30_000_000 || Math.abs(point.z()) > 30_000_000
                    || point.y() < minHeight || point.y() + 1.8 >= maxHeight) return false;
            double minX=point.x()-.3,minY=point.y()+.05,minZ=point.z()-.3,maxX=point.x()+.3,maxY=point.y()+1.8,maxZ=point.z()+.3;
            for (int x = (int) Math.floor(minX); x <= (int) Math.floor(maxX); x++) {
                for (int z = (int) Math.floor(minZ); z <= (int) Math.floor(maxZ); z++) {
                    if (!loaded(x,z)) return false;
                    // Include the block below: fences and walls can extend above their own block.
                    for (int y = Math.max(minHeight, (int) Math.floor(minY) - 1);
                         y <= (int) Math.floor(maxY); y++) {
                        for (Bounds box : boxes(x,y,z)) {
                            if (minX<box.maxX && maxX>box.minX && minY<box.maxY && maxY>box.minY && minZ<box.maxZ && maxZ>box.minZ) return false;
                        }
                    }
                }
            }
            return true;
        }
    }
}

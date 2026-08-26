package dev.lopyluna.create_lnl.content.utils;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@SuppressWarnings("unused")
public class LiftUtils {
    public static final Map<Direction.Axis, Set<Direction>> NON_AXIS_DIRECTIONS;
    public static final Map<Direction.Axis, Set<Direction.Axis>> NON_AXIS_AXES;
    static {
        var mapDir = new EnumMap<Direction.Axis, Set<Direction>>(Direction.Axis.class);
        for (var axis : Direction.Axis.values()) {
            var set = EnumSet.noneOf(Direction.class);
            for (var dir : Direction.values()) if (axis != dir.getAxis()) set.add(dir);
            mapDir.put(axis, Collections.unmodifiableSet(set));
        }
        NON_AXIS_DIRECTIONS = Collections.unmodifiableMap(mapDir);

        var mapAxis = new EnumMap<Direction.Axis, Set<Direction.Axis>>(Direction.Axis.class);
        for (var axis : Direction.Axis.values()) {
            var set = EnumSet.noneOf(Direction.Axis.class);
            for (var dir : Direction.Axis.values()) if (axis != dir) set.add(dir);
            mapAxis.put(axis, Collections.unmodifiableSet(set));
        }
        NON_AXIS_AXES = Collections.unmodifiableMap(mapAxis);
    }

    public static Vector3i chooseInverse(Direction.Axis axis, int x, int y, int z, int r) {
        return switch (axis) {
            case X -> new Vector3i(r, y, z);
            case Y -> new Vector3i(x, r, z);
            case Z -> new Vector3i(x, y, r);
        };
    }
    public static Vec3 chooseInverse(Direction.Axis axis, double x, double y, double z, double r) {
        return switch (axis) {
            case X -> new Vec3(r, y, z);
            case Y -> new Vec3(x, r, z);
            case Z -> new Vec3(x, y, r);
        };
    }
    public static Vector3i choose(Direction.Axis axis, int x, int y, int z, int r) {
        return switch (axis) {
            case X -> new Vector3i(x, r, r);
            case Y -> new Vector3i(r, y, r);
            case Z -> new Vector3i(r, r, z);
        };
    }
    public static Vec3 choose(Direction.Axis axis, double x, double y, double z, double r) {
        return switch (axis) {
            case X -> new Vec3(x, r, r);
            case Y -> new Vec3(r, y, r);
            case Z -> new Vec3(r, r, z);
        };
    }
    public static Vector3i chooseInverse(Direction.Axis axis, int a, int r) {
        return chooseInverse(axis, a, a, a, r);
    }
    public static Vec3 chooseInverse(Direction.Axis axis, double a, double r) {
        return chooseInverse(axis, a, a, a, r);
    }
    public static Vector3i choose(Direction.Axis axis, int a, int r) {
        return choose(axis, a, a, a, r);
    }
    public static Vec3 choose(Direction.Axis axis, double a, double r) {
        return choose(axis, a, a, a, r);
    }
    public static Vector3i chooseInverse(Direction.Axis axis, int a) {
        return chooseInverse(axis, a, a, a, 0);
    }
    public static Vec3 chooseInverse(Direction.Axis axis, double a) {
        return chooseInverse(axis, a, a, a, 0);
    }
    public static Vector3i choose(Direction.Axis axis, int a) {
        return choose(axis, a, a, a, 0);
    }
    public static Vec3 choose(Direction.Axis axis, double a) {
        return choose(axis, a, a, a, 0);
    }

    public static Direction.Axis getBlockAxis(BlockState state) {
        return state.hasProperty(BlockStateProperties.AXIS) ? state.getValue(BlockStateProperties.AXIS) : state.hasProperty(BlockStateProperties.FACING) ? state.getValue(BlockStateProperties.FACING).getAxis() : state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? state.getValue(BlockStateProperties.HORIZONTAL_FACING).getAxis() : state.hasProperty(BlockStateProperties.HORIZONTAL_AXIS) ? state.getValue(BlockStateProperties.HORIZONTAL_AXIS) : Direction.Axis.Y;
    }

    public record LocalShape(VoxelShape shape, List<AABB> boxes, int originX, int originY, int originZ) {}

    public record GroupPart(SubLevel subLevel, VoxelShape shape, int originX, int originY, int originZ) {}

    public record SubLevelGroup(SubLevel root, VoxelShape shape, List<AABB> rootBoxes,
                                double originX, double originY, double originZ, List<GroupPart> parts) {
        public static final SubLevelGroup EMPTY = new SubLevelGroup(null, Shapes.empty(), List.of(), 0, 0, 0, List.of());
    }

    private record CachedShape(long fingerprint, LocalShape shape) {}

    private record CachedGroup(long fingerprint, VoxelShape shape) {}

    private static final Map<UUID, CachedShape> SHAPE_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, CachedGroup> GROUP_CACHE = new ConcurrentHashMap<>();

    public static VoxelShape getSublevelShapeBounds(Level level, SubLevel subLevel, Pose3dc pose) {
        return getSublevelLocalShape(level, subLevel, pose).shape();
    }

    public static LocalShape getSublevelLocalShape(Level level, SubLevel subLevel, Pose3dc pose) {
        var bounds = subLevel.boundingBox().transformInverse(pose, new BoundingBox3d());
        int minX = Mth.floor(bounds.minX()), minY = Mth.floor(bounds.minY()), minZ = Mth.floor(bounds.minZ());
        int maxX = Mth.floor(bounds.maxX()), maxY = Mth.floor(bounds.maxY()), maxZ = Mth.floor(bounds.maxZ());
        if (minX > maxX || minY > maxY || minZ > maxZ) return new LocalShape(Shapes.empty(), List.of(), minX, minY, minZ);

        var fingerprint = 1L;
        for (var subPos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            var state = level.getBlockState(subPos);
            if (state.isAir()) continue;
            fingerprint = fingerprint * 31 + subPos.asLong();
            fingerprint = fingerprint * 31 + Block.getId(state);
        }

        var cached = SHAPE_CACHE.get(subLevel.getUniqueId());
        if (cached != null && cached.fingerprint() == fingerprint) return cached.shape();

        var parts = new ArrayList<VoxelShape>();
        var solid = new BitSetDiscreteVoxelShape(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        for (var subPos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            var state = level.getBlockState(subPos);
            if (state.isAir()) continue;
            var shape = state.getShape(level, subPos);
            if (shape.isEmpty()) continue;
            var box = shape.bounds();
            if (box.minX == 0 && box.minY == 0 && box.minZ == 0 && box.maxX == 1 && box.maxY == 1 && box.maxZ == 1)
                solid.fill(subPos.getX() - minX, subPos.getY() - minY, subPos.getZ() - minZ);
            else parts.add(Shapes.create(box).move(subPos.getX() - minX, subPos.getY() - minY, subPos.getZ() - minZ));
        }
        solid.forAllBoxes((x1, y1, z1, x2, y2, z2) -> parts.add(Shapes.box(x1, y1, z1, x2, y2, z2)), true);

        var merged = mergeShapes(parts);
        var local = new LocalShape(merged, merged.toAabbs(), minX, minY, minZ);
        if (SHAPE_CACHE.size() > 128) SHAPE_CACHE.clear();
        SHAPE_CACHE.put(subLevel.getUniqueId(), new CachedShape(fingerprint, local));
        return local;
    }

    public static List<SubLevel> getConnectedGroup(SubLevel root) {
        var group = new ArrayList<SubLevel>();
        var seen = new HashSet<UUID>();
        var queue = new ArrayDeque<SubLevel>();
        queue.add(root);
        seen.add(root.getUniqueId());

        while (!queue.isEmpty()) {
            var current = queue.poll();
            group.add(current);
            for (var actor : current.getPlot().getBlockEntityActors()) {
                var deps = actor.sable$getConnectionDependencies();
                if (deps == null) continue;
                for (var dep : deps) if (seen.add(dep.getUniqueId())) queue.add(dep);
            }
        }
        return group;
    }

    public static Set<UUID> getConnectedGroupIds(SubLevel root) {
        var ids = new HashSet<UUID>();
        for (var member : getConnectedGroup(root)) ids.add(member.getUniqueId());
        return ids;
    }

    public static VoxelShape getSublevelGroupShape(Level level, SubLevel held, Function<SubLevel, Pose3dc> memberPose) {
        return getSublevelGroup(level, held, memberPose).shape();
    }

    public static SubLevelGroup getSublevelGroup(Level level, SubLevel held, Function<SubLevel, Pose3dc> mPose) {
        var locals = new LinkedHashMap<SubLevel, LocalShape>();
        for (var member : getConnectedGroup(held)) {
            var local = getSublevelLocalShape(level, member, mPose.apply(member));
            if (local.shape().isEmpty()) continue;
            locals.put(member, local);
        }
        if (locals.isEmpty()) return SubLevelGroup.EMPTY;

        var root = pickFrameRoot(locals, mPose);
        var rootPose = mPose.apply(root);
        var parts = new ArrayList<GroupPart>(locals.size());
        var rootLocal = new ArrayList<AABB>();
        var rootOnly = new ArrayList<AABB>();
        var corner = new Vector3d();

        for (var entry : locals.entrySet()) {
            var member = entry.getKey();
            var local = entry.getValue();
            parts.add(new GroupPart(member, local.shape(), local.originX(), local.originY(), local.originZ()));

            var memberPose = mPose.apply(member);
            for (var box : local.boxes()) {
                var moved = box.move(local.originX(), local.originY(), local.originZ());
                var framed = member == root ? moved : toRootSpace(moved, memberPose, rootPose, corner);
                rootLocal.add(framed);
                if (member == root) rootOnly.add(framed);
            }
        }
        if (rootLocal.isEmpty()) return SubLevelGroup.EMPTY;

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        for (var box : rootLocal) {
            minX = Math.min(minX, box.minX);
            minY = Math.min(minY, box.minY);
            minZ = Math.min(minZ, box.minZ);
        }
        var originX = Math.floor(minX);
        var originY = Math.floor(minY);
        var originZ = Math.floor(minZ);

        var rootBoxes = new ArrayList<AABB>(rootOnly.size());
        for (var box : rootOnly) rootBoxes.add(box.move(-originX, -originY, -originZ));
        return new SubLevelGroup(root, groupShape(root, rootLocal, originX, originY, originZ), rootBoxes, originX, originY, originZ, parts);
    }

    private static VoxelShape groupShape(SubLevel root, List<AABB> rootLocal, double originX, double originY, double originZ) {
        var fingerprint = 1L;
        for (var box : rootLocal)
            fingerprint = mix(mix(mix(mix(mix(mix(fingerprint, box.minX), box.minY), box.minZ), box.maxX), box.maxY), box.maxZ);

        var cached = GROUP_CACHE.get(root.getUniqueId());
        if (cached != null && cached.fingerprint() == fingerprint) return cached.shape();

        var shapes = new ArrayList<VoxelShape>(rootLocal.size());
        for (var box : rootLocal) shapes.add(Shapes.create(box.move(-originX, -originY, -originZ)));

        var merged = mergeShapes(shapes);
        if (GROUP_CACHE.size() > 128) GROUP_CACHE.clear();
        GROUP_CACHE.put(root.getUniqueId(), new CachedGroup(fingerprint, merged));
        return merged;
    }

    private static long mix(long hash, double value) {
        return hash * 31 + Double.doubleToLongBits(value);
    }

    private static SubLevel pickFrameRoot(Map<SubLevel, LocalShape> locals, Function<SubLevel, Pose3dc> mPose) {
        SubLevel best = null;
        var bestBottom = Double.MAX_VALUE;
        var bestVolume = -1.0;
        var corner = new Vector3d();

        for (var entry : locals.entrySet()) {
            var local = entry.getValue();
            var bounds = local.shape().bounds();
            var volume = bounds.getXsize() * bounds.getYsize() * bounds.getZsize();
            var bottom = worldBottom(bounds.move(local.originX(), local.originY(), local.originZ()),
                    mPose.apply(entry.getKey()), corner);

            if (best != null) {
                if (bottom > bestBottom + 1 / 16d) continue;
                if (bottom > bestBottom - 1 / 16d && volume <= bestVolume) continue;
            }
            best = entry.getKey();
            bestVolume = volume;
            bestBottom = Math.min(bestBottom, bottom);
        }
        return best;
    }

    private static double worldBottom(AABB box, Pose3dc pose, Vector3d corner) {
        var bottom = Double.MAX_VALUE;
        for (int c = 0; c < 8; c++) {
            corner.set((c & 1) == 0 ? box.minX : box.maxX,
                    ((c >> 1) & 1) == 0 ? box.minY : box.maxY,
                    ((c >> 2) & 1) == 0 ? box.minZ : box.maxZ);
            pose.transformPosition(corner);
            bottom = Math.min(bottom, corner.y);
        }
        return bottom;
    }

    private static AABB toRootSpace(AABB box, Pose3dc from, Pose3dc rootPose, Vector3d corner) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int c = 0; c < 8; c++) {
            corner.set((c & 1) == 0 ? box.minX : box.maxX,
                    ((c >> 1) & 1) == 0 ? box.minY : box.maxY,
                    ((c >> 2) & 1) == 0 ? box.minZ : box.maxZ);
            from.transformPosition(corner);
            rootPose.transformPositionInverse(corner);
            minX = Math.min(minX, corner.x); maxX = Math.max(maxX, corner.x);
            minY = Math.min(minY, corner.y); maxY = Math.max(maxY, corner.y);
            minZ = Math.min(minZ, corner.z); maxZ = Math.max(maxZ, corner.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static VoxelShape mergeShapes(List<VoxelShape> parts) {
        if (parts.isEmpty()) return Shapes.empty();
        while (parts.size() > 1) {
            var merged = new ArrayList<VoxelShape>((parts.size() + 1) / 2);
            for (int i = 0; i < parts.size(); i += 2) {
                if (i + 1 < parts.size()) merged.add(Shapes.join(parts.get(i), parts.get(i + 1), BooleanOp.OR));
                else merged.add(parts.get(i));
            }
            parts = merged;
        }
        return parts.getFirst().optimize();
    }
}

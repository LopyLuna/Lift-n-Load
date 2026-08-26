package dev.lopyluna.create_lnl.content.blocks.contraption_lift;

import dev.lopyluna.create_lnl.content.utils.LiftUtils;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.*;

public class LiftPlacement {
    public static final float MAX_HEIGHT = 8f;
    public static final double SEAT_GAP = 0.25 / 16d;
    public static final float ROTATION_DEGREES = 22.5f;
    public static final int ROTATION_STEPS = 16;

    public enum Reason { OK, NO_SUPPORT, OCCUPIED, BLOCKED, TOO_LARGE, ON_SUBLEVEL, UNSUPPORTED }

    public record Result(boolean valid, float height, Reason reason, Vec3 anchor, List<BlockPos> blockers) {
        public static final Result INVALID = new Result(false, 0, Reason.BLOCKED, Vec3.ZERO, List.of());

        public static Result fail(Reason reason) {
            return new Result(false, 0, reason, Vec3.ZERO, List.of());
        }

        public String message() {
            return switch (reason) {
                case NO_SUPPORT -> "Needs a solid block below";
                case OCCUPIED -> "Something is Occupied in the space";
                case TOO_LARGE -> "Contraption is too big";
                case ON_SUBLEVEL -> "Cannot be placed on a Contraption";
                case UNSUPPORTED -> "Contraption wouldn't rest on the lift";
                default -> "Not enough room";
            };
        }
    }

    public static double platformY(BlockPos liftPos, double height) {
        return liftPos.getY() + 12 / 16d + height;
    }

    public static Vec3 pivot(BlockPos liftPos, double height) {
        return new Vec3(liftPos.getX() + 0.5, platformY(liftPos, height) + SEAT_GAP, liftPos.getZ() + 0.5);
    }

    public static boolean isTooLarge(BoundingBox3ic bounds) {
        return bounds.maxX() - bounds.minX() + 1 > 512
                || bounds.maxY() - bounds.minY() + 1 > 512
                || bounds.maxZ() - bounds.minZ() + 1 > 512;
    }

    public static LiftUtils.SubLevelGroup structureGroup(Level level, SubLevel held) {
        return LiftUtils.getSublevelGroup(level, held, SubLevel::logicalPose);
    }

    public static Vec3 anchor(List<AABB> seating, List<AABB> all, boolean lowest, int rotation) {
        if (seating.isEmpty()) return Vec3.ZERO;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (var box : seating) {
            minX = Math.min(minX, box.minX); maxX = Math.max(maxX, box.maxX);
            minZ = Math.min(minZ, box.minZ); maxZ = Math.max(maxZ, box.maxZ);
            minY = Math.min(minY, box.minY);
        }
        var floor = Double.MAX_VALUE;
        for (var box : all) floor = Math.min(floor, box.minY);
        if (floor == Double.MAX_VALUE) floor = minY;

        var targetX = (minX + maxX) / 2;
        var targetZ = (minZ + maxZ) / 2;

        AABB best = null;
        var bestDistance = Double.MAX_VALUE;
        var bestY = Double.MAX_VALUE;
        for (var box : seating) {
            if (lowest && box.minY > minY + 1.0E-7) continue;
            var candidateX = Mth.clamp(targetX, box.minX, box.maxX);
            var candidateZ = Mth.clamp(targetZ, box.minZ, box.maxZ);
            var distance = Mth.square(candidateX - targetX) + Mth.square(candidateZ - targetZ);
            if (distance > bestDistance + 1.0E-7) continue;
            if (distance > bestDistance - 1.0E-7 && box.minY >= bestY) continue;
            bestDistance = distance;
            bestY = box.minY;
            best = box;
        }
        if (best == null) return new Vec3(targetX, floor, targetZ);

        var anchorX = Mth.clamp(targetX, best.minX, best.maxX);
        var anchorZ = Mth.clamp(targetZ, best.minZ, best.maxZ);
        var seat = new Vec3(anchorX, floor, anchorZ);
        return new Vec3(anchorX, footprintBottom(all, seat, rotation, floor), anchorZ);
    }

    private static double footprintBottom(List<AABB> boxes, Vec3 seat, int rotation, double fallback) {
        var half = 6 / 16d;
        var bottom = Double.MAX_VALUE;
        for (var box : boxes) {
            var rotated = rotateAround(box, seat, rotation);
            if (rotated.maxX <= seat.x - half + 1.0E-7 || rotated.minX >= seat.x + half - 1.0E-7) continue;
            if (rotated.maxZ <= seat.z - half + 1.0E-7 || rotated.minZ >= seat.z + half - 1.0E-7) continue;
            bottom = Math.min(bottom, rotated.minY);
        }
        return bottom == Double.MAX_VALUE ? fallback : bottom;
    }

    private static boolean separated(List<AABB> boxes, Vec3 anchor, int rotation) {
        var half = 6 / 16d;
        var contact = Double.MAX_VALUE;
        for (var box : boxes) {
            var rotated = rotateAround(box, anchor, rotation);
            if (rotated.maxX - anchor.x <= -half + 1.0E-7 || rotated.minX - anchor.x >= half - 1.0E-7) continue;
            if (rotated.maxZ - anchor.z <= -half + 1.0E-7 || rotated.minZ - anchor.z >= half - 1.0E-7) continue;
            contact = Math.min(contact, rotated.minY);
        }
        return contact > anchor.y + 1 / 64d;
    }

    private static boolean liftObstructed(List<AABB> boxes, Vec3 anchor, int rotation) {
        var half = 6 / 16d;
        for (var box : boxes) {
            if (box.minY >= anchor.y - 1.0E-7) continue;
            var rotated = rotateAround(box, anchor, rotation);
            if (rotated.maxX - anchor.x <= -half + 1.0E-7 || rotated.minX - anchor.x >= half - 1.0E-7) continue;
            if (rotated.maxZ - anchor.z <= -half + 1.0E-7 || rotated.minZ - anchor.z >= half - 1.0E-7) continue;
            return true;
        }
        return false;
    }

    public static double rotationRadians(int rotation) {
        return Math.toRadians(Math.floorMod(rotation, ROTATION_STEPS) * ROTATION_DEGREES);
    }

    public static AABB rotateAround(AABB box, Vec3 anchor, int rotation) {
        if (Math.floorMod(rotation, ROTATION_STEPS) == 0) return box;
        var radians = rotationRadians(rotation);
        var sin = Math.sin(radians);
        var cos = Math.cos(radians);
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (int c = 0; c < 4; c++) {
            var dx = ((c & 1) == 0 ? box.minX : box.maxX) - anchor.x;
            var dz = ((c >> 1) == 0 ? box.minZ : box.maxZ) - anchor.z;
            var rx = anchor.x + dx * cos - dz * sin;
            var rz = anchor.z + dx * sin + dz * cos;
            minX = Math.min(minX, rx); maxX = Math.max(maxX, rx);
            minZ = Math.min(minZ, rz); maxZ = Math.max(maxZ, rz);
        }
        return new AABB(minX, box.minY, minZ, maxX, box.maxY, maxZ);
    }

    public static double rotatedX(double dx, double dz, double sin, double cos) {
        return dx * cos - dz * sin;
    }

    public static double rotatedZ(double dx, double dz, double sin, double cos) {
        return dx * sin + dz * cos;
    }

    public static Result solve(Level level, BlockPos liftPos, @Nullable SubLevel held, @Nullable LiftUtils.SubLevelGroup group, int rotation) {
        var state = level.getBlockState(liftPos);
        if (!state.isAir() && !state.canBeReplaced()) return Result.fail(Reason.OCCUPIED);
        if (level.getBlockState(liftPos.below()).getBlock() instanceof DockingLiftBlock) return Result.fail(Reason.OCCUPIED);
        if (!Block.canSupportRigidBlock(level, liftPos.below())) return Result.fail(Reason.NO_SUPPORT);

        var groupIds = held == null ? Set.<UUID>of() : LiftUtils.getConnectedGroupIds(held);
        if (Sable.HELPER.getContaining(level, liftPos) != null) return Result.fail(Reason.ON_SUBLEVEL);
        if (Sable.HELPER.getContaining(level, liftPos.below()) != null) return Result.fail(Reason.ON_SUBLEVEL);

        var maxHeight = liftClearance(level, liftPos, mastHeight(level, liftPos), groupIds, true);
        if (maxHeight < 0) return Result.fail(Reason.OCCUPIED);
        if (held == null || group == null || group.shape().isEmpty()) return new Result(true, 0, Reason.OK, Vec3.ZERO, List.of());
        if (isTooLarge(held.getPlot().getBoundingBox())) return Result.fail(Reason.TOO_LARGE);

        var boxes = group.shape().toAabbs();
        if (boxes.isEmpty()) return new Result(true, 0, Reason.OK, Vec3.ZERO, List.of());
        var seating = group.rootBoxes().isEmpty() ? boxes : group.rootBoxes();

        var centered = anchor(seating, boxes, false, rotation);
        var blocked = liftObstructed(boxes, centered, rotation);
        var attempt = attempt(level, liftPos, boxes, centered, maxHeight, groupIds, rotation);
        if (!blocked && attempt.valid() && attempt.height() <= MAX_HEIGHT - 2) return attempt;

        var lowest = anchor(seating, boxes, true, rotation);
        if (lowest.y == centered.y && !blocked) return attempt;

        var fallback = attempt(level, liftPos, boxes, lowest, maxHeight, groupIds, rotation);
        if (!fallback.valid()) return attempt;
        if (blocked || !attempt.valid() || fallback.height() < attempt.height())
            return separated(boxes, lowest, rotation) ? Result.fail(Reason.UNSUPPORTED) : fallback;
        return attempt;
    }

    private static Result attempt(Level level, BlockPos liftPos, List<AABB> boxes, Vec3 anchor, float maxHeight, Set<UUID> group, int rotation) {
        var pivot = pivot(liftPos, 0);
        var offX = pivot.x - anchor.x;
        var offY = pivot.y - anchor.y;
        var offZ = pivot.z - anchor.z;

        var world = new ArrayList<AABB>(boxes.size());
        for (var box : boxes) world.add(rotateAround(box, anchor, rotation).move(offX, offY, offZ));

        var forbidden = new ArrayList<double[]>();
        collectBlockIntervals(level, world, maxHeight, forbidden, null);
        collectEntityIntervals(level, world, maxHeight, forbidden);
        collectSubLevelIntervals(level, world, maxHeight, group, forbidden);

        var height = lowestFreeHeight(forbidden, maxHeight);
        if (height < 0 || embedded(level, world, height)) {
            var blockers = new LinkedHashSet<BlockPos>();
            collectBlockIntervals(level, world, maxHeight, new ArrayList<>(), blockers);
            return new Result(false, 0, Reason.BLOCKED, anchor, List.copyOf(blockers));
        }
        return new Result(true, height, Reason.OK, anchor, List.of());
    }

    private static boolean embedded(Level level, List<AABB> world, float height) {
        var cursor = new BlockPos.MutableBlockPos();
        for (var box : world) {
            var raised = box.move(0, height, 0);
            int minX = Mth.floor(raised.minX + 1.0E-7), maxX = Mth.floor(raised.maxX - 1.0E-7);
            int minY = Mth.floor(raised.minY + 1.0E-7), maxY = Mth.floor(raised.maxY - 1.0E-7);
            int minZ = Mth.floor(raised.minZ + 1.0E-7), maxZ = Mth.floor(raised.maxZ - 1.0E-7);

            for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
                cursor.set(x, y, z);
                var state = level.getBlockState(cursor);
                if (state.isAir()) continue;
                var shape = state.getCollisionShape(level, cursor);
                if (shape.isEmpty()) continue;
                for (var solid : shape.toAabbs()) if (solid.move(x, y, z).intersects(raised)) return true;
            }
        }
        return false;
    }

    private static float liftClearance(Level level, BlockPos liftPos, float maxHeight, Set<UUID> ignore, boolean entities) {
        var column = new AABB(liftPos.getX() + 2 / 16d, liftPos.getY(), liftPos.getZ() + 2 / 16d,
                liftPos.getX() + 14 / 16d, platformY(liftPos, maxHeight), liftPos.getZ() + 14 / 16d);

        var ceiling = Double.MAX_VALUE;
        if (entities) for (var entity : level.getEntities(null, column)) {
            if (entity.isSpectator() || entity.noPhysics) continue;
            if (Sable.HELPER.getContaining(entity) != null || Sable.HELPER.getTrackingSubLevel(entity) != null) continue;
            ceiling = Math.min(ceiling, entity.getBoundingBox().minY);
        }

        var container = SubLevelContainer.getContainer(level);
        if (container != null) for (var other : container.queryIntersecting(new BoundingBox3d(column))) {
            if (ignore.contains(other.getUniqueId())) continue;
            if (DockingLiftBE.TRACKING_NO_COLLISIONS.contains(other.getUniqueId())) continue;
            ceiling = Math.min(ceiling, subLevelCeiling(other, column));
        }

        if (ceiling == Double.MAX_VALUE) return maxHeight;
        var allowed = ceiling - (liftPos.getY() + 12 / 16d);
        if (allowed < 0) return -1;
        return (float) Math.min(maxHeight, Math.floor(allowed / (1 / 16f)) * (1 / 16f));
    }

    private static double subLevelCeiling(SubLevel other, AABB column) {
        var pose = other.logicalPose();
        var local = other.boundingBox().transformInverse(pose, new BoundingBox3d());
        int minX = Mth.floor(local.minX()), minY = Mth.floor(local.minY()), minZ = Mth.floor(local.minZ());
        int maxX = Mth.floor(local.maxX()), maxY = Mth.floor(local.maxY()), maxZ = Mth.floor(local.maxZ());

        var volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > 32768) {
            var global = other.boundingBox();
            return global.maxX() <= column.minX || global.minX() >= column.maxX
                    || global.maxZ() <= column.minZ || global.minZ() >= column.maxZ
                    ? Double.MAX_VALUE : global.minY();
        }

        var plotLevel = other.getLevel();
        var pos = new BlockPos.MutableBlockPos();
        var corner = new Vector3d();
        var ceiling = Double.MAX_VALUE;

        for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
            pos.set(x, y, z);
            var state = plotLevel.getBlockState(pos);
            if (state.isAir() || state.getCollisionShape(plotLevel, pos).isEmpty()) continue;

            double bMinX = Double.MAX_VALUE, bMinY = Double.MAX_VALUE, bMinZ = Double.MAX_VALUE;
            double bMaxX = -Double.MAX_VALUE, bMaxZ = -Double.MAX_VALUE;
            for (int c = 0; c < 8; c++) {
                corner.set(x + (c & 1), y + ((c >> 1) & 1), z + ((c >> 2) & 1));
                pose.transformPosition(corner);
                bMinX = Math.min(bMinX, corner.x); bMaxX = Math.max(bMaxX, corner.x);
                bMinZ = Math.min(bMinZ, corner.z); bMaxZ = Math.max(bMaxZ, corner.z);
                bMinY = Math.min(bMinY, corner.y);
            }
            if (bMaxX <= column.minX + 1.0E-7 || bMinX >= column.maxX - 1.0E-7) continue;
            if (bMaxZ <= column.minZ + 1.0E-7 || bMinZ >= column.maxZ - 1.0E-7) continue;
            ceiling = Math.min(ceiling, bMinY);
        }
        return ceiling;
    }

    public static float ceiling(Level level, BlockPos liftPos, Set<UUID> ignore) {
        return liftClearance(level, liftPos, mastHeight(level, liftPos), ignore, false);
    }

    public static float mastHeight(Level level, BlockPos liftPos) {
        return Math.min(MAX_HEIGHT, freeMastBlocks(level, liftPos) + 0.25f);
    }

    public static int freeMastBlocks(Level level, BlockPos liftPos) {
        for (int i = 1; i <= MAX_HEIGHT; i++) {
            var state = level.getBlockState(liftPos.above(i));
            if (state.isAir() || state.canBeReplaced() || state.getBlock() instanceof DockingLiftBlock) continue;
            return i - 1;
        }
        return (int) MAX_HEIGHT;
    }

    private static void collectBlockIntervals(Level level, List<AABB> subBoxes, float maxHeight,
                                              List<double[]> out, @Nullable Set<BlockPos> blockersAtZero) {
        var windowFrom = Integer.MAX_VALUE;
        var windowTo = Integer.MIN_VALUE;
        for (var box : subBoxes) {
            windowFrom = Math.min(windowFrom, Mth.floor(box.minY + 1.0E-7));
            windowTo = Math.max(windowTo, Mth.floor(box.maxY + maxHeight - 1.0E-7));
        }
        var fromY = windowFrom;
        var toY = windowTo;

        var columnCache = new HashMap<Long, List<Span>>();

        for (var box : subBoxes) {
            int minX = Mth.floor(box.minX + 1.0E-7), maxX = Mth.floor(box.maxX - 1.0E-7),
                minZ = Mth.floor(box.minZ + 1.0E-7), maxZ = Mth.floor(box.maxZ - 1.0E-7);

            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
                var cx = x;
                var cz = z;
                var spans = columnCache.computeIfAbsent(BlockPos.asLong(x, 0, z), k -> {
                    var found = new ArrayList<Span>();
                    var cursor = new BlockPos.MutableBlockPos();
                    for (int y = fromY; y <= toY; y++) {
                        cursor.set(cx, y, cz);
                        var state = level.getBlockState(cursor);
                        if (state.isAir()) continue;
                        var shape = state.getCollisionShape(level, cursor);
                        if (shape.isEmpty()) continue;
                        var immutable = cursor.immutable();
                        for (var world : shape.toAabbs()) found.add(new Span(world.minY + y, world.maxY + y, immutable));
                    }
                    return found;
                });

                for (var span : spans) {
                    var lo = span.min() - box.maxY;
                    var hi = span.max() - box.minY;
                    if (hi <= 0 || lo >= maxHeight) continue;
                    out.add(new double[]{lo, hi});
                    if (blockersAtZero != null && lo < 0 && blockersAtZero.size() < 32)
                        blockersAtZero.add(span.pos());
                }
            }
        }
    }

    private record Span(double min, double max, BlockPos pos) {}

    private static void collectEntityIntervals(Level level, List<AABB> subBoxes, float maxHeight, List<double[]> out) {
        var search = searchBox(subBoxes);
        if (search == null) return;

        for (var entity : level.getEntities(null, search.expandTowards(0, maxHeight, 0))) {
            if (entity.isSpectator() || entity.noPhysics) continue;
            if (Sable.HELPER.getContaining(entity) != null || Sable.HELPER.getTrackingSubLevel(entity) != null) continue;
            addObstacle(subBoxes, maxHeight, entity.getBoundingBox(), out);
        }
    }

    private static void collectSubLevelIntervals(Level level, List<AABB> subBoxes, float maxHeight,
                                                 Set<UUID> group, List<double[]> out) {
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        var search = searchBox(subBoxes);
        if (search == null) return;
        search = search.expandTowards(0, maxHeight, 0);

        for (var other : container.queryIntersecting(new BoundingBox3d(search))) {
            if (group.contains(other.getUniqueId())) continue;
            if (DockingLiftBE.TRACKING_NO_COLLISIONS.contains(other.getUniqueId())) continue;
            addSubLevelObstacles(other, subBoxes, maxHeight, search, out);
        }
    }

    private static void addSubLevelObstacles(SubLevel other, List<AABB> subBoxes, float maxHeight, AABB search, List<double[]> out) {
        var pose = other.logicalPose();
        var local = other.boundingBox().transformInverse(pose, new BoundingBox3d());
        int minX = Mth.floor(local.minX()), minY = Mth.floor(local.minY()), minZ = Mth.floor(local.minZ());
        int maxX = Mth.floor(local.maxX()), maxY = Mth.floor(local.maxY()), maxZ = Mth.floor(local.maxZ());

        var volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > 32768) {
            var global = other.boundingBox();
            addObstacle(subBoxes, maxHeight, new AABB(global.minX(), global.minY(), global.minZ(), global.maxX(), global.maxY(), global.maxZ()), out);
            return;
        }

        var plotLevel = other.getLevel();
        var pos = new BlockPos.MutableBlockPos();
        var corner = new Vector3d();

        for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
            pos.set(x, y, z);
            var state = plotLevel.getBlockState(pos);
            if (state.isAir() || state.getCollisionShape(plotLevel, pos).isEmpty()) continue;

            double bMinX = Double.MAX_VALUE, bMinY = Double.MAX_VALUE, bMinZ = Double.MAX_VALUE;
            double bMaxX = -Double.MAX_VALUE, bMaxY = -Double.MAX_VALUE, bMaxZ = -Double.MAX_VALUE;
            for (int c = 0; c < 8; c++) {
                corner.set(x + (c & 1), y + ((c >> 1) & 1), z + ((c >> 2) & 1));
                pose.transformPosition(corner);
                bMinX = Math.min(bMinX, corner.x); bMaxX = Math.max(bMaxX, corner.x);
                bMinY = Math.min(bMinY, corner.y); bMaxY = Math.max(bMaxY, corner.y);
                bMinZ = Math.min(bMinZ, corner.z); bMaxZ = Math.max(bMaxZ, corner.z);
            }
            var world = new AABB(bMinX, bMinY, bMinZ, bMaxX, bMaxY, bMaxZ);
            if (!world.intersects(search)) continue;
            addObstacle(subBoxes, maxHeight, world, out);
        }
    }

    private static void addObstacle(List<AABB> subBoxes, float maxHeight, AABB obstacle, List<double[]> out) {
        for (var box : subBoxes) {
            if (obstacle.maxX <= box.minX + 1.0E-7 || obstacle.minX >= box.maxX - 1.0E-7) continue;
            if (obstacle.maxZ <= box.minZ + 1.0E-7 || obstacle.minZ >= box.maxZ - 1.0E-7) continue;
            var lo = obstacle.minY - box.maxY;
            var hi = obstacle.maxY - box.minY;
            if (hi <= 0 || lo >= maxHeight) continue;
            out.add(new double[]{lo, hi});
        }
    }

    private static @Nullable AABB searchBox(List<AABB> subBoxes) {
        AABB search = null;
        for (var box : subBoxes) search = search == null ? box : search.minmax(box);
        return search;
    }

    private static float lowestFreeHeight(List<double[]> forbidden, float maxHeight) {
        forbidden.sort(Comparator.comparingDouble(a -> a[0]));
        double height = 0;
        for (var interval : forbidden) {
            if (interval[0] > height) break;
            if (interval[1] <= height) continue;
            height = Math.ceil(interval[1] / (1 / 16f)) * (1 / 16f);
            if (height > maxHeight) return -1;
        }
        return height > maxHeight ? -1 : (float) height;
    }
}

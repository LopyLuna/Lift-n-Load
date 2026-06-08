package dev.lopyluna.create_lnl.content.utils;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3i;

import java.util.*;

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
}

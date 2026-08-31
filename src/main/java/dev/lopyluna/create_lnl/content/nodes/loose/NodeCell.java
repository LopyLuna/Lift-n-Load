package dev.lopyluna.create_lnl.content.nodes.loose;

import com.mojang.math.Axis;
import com.simibubi.create.content.contraptions.StructureTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;

public class NodeCell {
    public static final int TOTAL = 3072;
    public static final int DEFAULT_COLOR = 0x8C8C8A;

    public static int pack(Direction face, int u, int v, int depth) {
        return (face.get3DDataValue() << 9) | (depth << 6) | (v << 3) | u;
    }

    public static Direction face(int id) {
        return Direction.from3DDataValue((id >> 9) & 7);
    }

    public static int depth(int id) {
        return (id >> 6) & 7;
    }

    public static int u(int id) {
        return id & 7;
    }

    public static int v(int id) {
        return (id >> 3) & 7;
    }

    public static String id(int id) {
        return "n" + id;
    }

    @Nullable
    public static Integer parse(String id) {
        if (id.length() < 2 || id.charAt(0) != 'n') return null;
        try {
            var value = Integer.parseInt(id.substring(1));
            return value < 0 || value >= TOTAL || face(value).get3DDataValue() > 5 ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Quaternionf rotation(Direction face) {
        return switch (face) {
            case DOWN -> Axis.XP.rotationDegrees(180);
            case NORTH -> Axis.XP.rotationDegrees(-90);
            case SOUTH -> Axis.XP.rotationDegrees(90);
            case EAST -> Axis.ZP.rotationDegrees(-90);
            case WEST -> Axis.ZP.rotationDegrees(90);
            default -> new Quaternionf();
        };
    }

    public static Vec3 local(int id) {
        return new Vec3((2 * u(id) - 7) / 16d, depth(id) * 2 / 16d, (2 * v(id) - 7) / 16d);
    }

    public static Vec3 offset(int id) {
        var base = depth(id) * 2 / 16d;
        var point = new Vec3((2 * u(id) + 1) / 16d, base + 5 / 16d, (2 * v(id) + 1) / 16d);
        return rotate(point, face(id)).subtract(0.5, 0.5, 0.5);
    }

    public static VoxelShape shape(int id) {
        var face = face(id);
        var u = u(id) * 2 / 16d;
        var v = v(id) * 2 / 16d;
        var base = depth(id) * 2 / 16d;
        return box(rotate(new Vec3(u, base, v), face), rotate(new Vec3(u + 2 / 16d, base + 5 / 16d, v + 2 / 16d), face), Vec3.ZERO);
    }

    public static VoxelShape footprint(int id) {
        var face = face(id);
        var u = u(id) * 2 / 16d;
        var v = v(id) * 2 / 16d;
        var base = depth(id) * 2 / 16d;
        var shift = depth(id) == 0 ? Vec3.atLowerCornerOf(face.getNormal()) : Vec3.ZERO;
        return box(rotate(new Vec3(u, base - 1 / 32d, v), face), rotate(new Vec3(u + 2 / 16d, base, v + 2 / 16d), face), shift);
    }

    public static int transform(int id, StructureTransform transform) {
        var face = face(id);
        var moved = transform.rotateFacing(transform.mirrorFacing(face));
        var point = rotate(new Vec3(u(id) * 2 / 16d + 1 / 16d, depth(id) * 2 / 16d + 1 / 32d, v(id) * 2 / 16d + 1 / 16d), face);
        var local = unrotate(transform.applyWithoutOffset(point), moved);
        return pack(moved, cell(local.x), cell(local.z), cell(local.y));
    }

    public static int of(BlockPos pos, Direction face, Vec3 hit) {
        var local = unrotate(hit.subtract(Vec3.atLowerCornerOf(pos)), face);
        return pack(face, cell(local.x), cell(local.z), cell(local.y));
    }

    private static VoxelShape box(Vec3 a, Vec3 b, Vec3 shift) {
        var first = a.add(shift);
        var second = b.add(shift);
        return Shapes.box(Math.min(first.x, second.x), Math.min(first.y, second.y), Math.min(first.z, second.z),
                Math.max(first.x, second.x), Math.max(first.y, second.y), Math.max(first.z, second.z));
    }

    private static Vec3 rotate(Vec3 local, Direction face) {
        var vec = new Vector3f((float) (local.x - 0.5), (float) (local.y - 0.5), (float) (local.z - 0.5));
        rotation(face).transform(vec);
        return new Vec3(snap(vec.x + 0.5), snap(vec.y + 0.5), snap(vec.z + 0.5));
    }

    private static Vec3 unrotate(Vec3 world, Direction face) {
        var vec = new Vector3f((float) (world.x - 0.5), (float) (world.y - 0.5), (float) (world.z - 0.5));
        rotation(face).conjugate(new Quaternionf()).transform(vec);
        return new Vec3(vec.x + 0.5, vec.y + 0.5, vec.z + 0.5);
    }

    private static double snap(double value) {
        return Math.round(value * 32) / 32d;
    }

    private static int cell(double value) {
        var index = (int) Math.floor(value * 8);
        return index < 0 ? 0 : Math.min(index, 7);
    }
}

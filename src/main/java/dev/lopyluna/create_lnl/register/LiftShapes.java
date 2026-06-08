package dev.lopyluna.create_lnl.register;

import com.simibubi.create.AllShapes;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@SuppressWarnings("unused")
public class LiftShapes {
    public static Map<String, VoxelShape> CUBOID_CACHE = new ConcurrentHashMap<>();

    public static final VoxelShaper THRUSTER_HEAD = shape(Stream.of(Block.box(0, 0, 0, 16, 16, 9), Block.box(2, 11, 9, 5, 14, 16), Block.box(3, 3, 9, 13, 13, 16), Block.box(2, 2, 9, 5, 5, 16), Block.box(11, 2, 9, 14, 5, 16), Block.box(11, 11, 9, 14, 14, 16))
            .reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR)).get()).forDirectional(Direction.NORTH);
    public static final VoxelShaper THRUSTER_NOZZLE = shape(Stream.of(Block.box(3, 3, 0, 13, 13, 8), Block.box(1, 1, 8, 15, 15, 16), Block.box(11, 2, 0, 14, 5, 5), Block.box(2, 2, 0, 5, 5, 5), Block.box(2, 11, 0, 5, 14, 5), Block.box(11, 11, 0, 14, 14, 5))
            .reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR)).get()).forDirectional(Direction.NORTH);


    public static AllShapes.Builder shape(VoxelShape shape) {
        return new AllShapes.Builder(shape);
    }

    public static AllShapes.Builder shape(double x1, double y1, double z1, double x2, double y2, double z2) {
        return shape(cuboid(x1, y1, z1, x2, y2, z2));
    }

    public static VoxelShape cuboid(double x1, double y1, double z1, double x2, double y2, double z2) {
        String key = x1 + "_" + y1 + "_" + z1 + "_" + x2 + "_" + y2 + "_" + z2;
        return CUBOID_CACHE.computeIfAbsent(key, k -> Block.box(x1, y1, z1, x2, y2, z2));
    }
}

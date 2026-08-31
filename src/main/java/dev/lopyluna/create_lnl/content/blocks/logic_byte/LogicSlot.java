package dev.lopyluna.create_lnl.content.blocks.logic_byte;

import com.simibubi.create.content.contraptions.StructureTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum LogicSlot {
    BFL(false, true, false),
    BFR(false, true, true),
    BBL(false, false, false),
    BBR(false, false, true),
    TFL(true, true, false),
    TFR(true, true, true),
    TBL(true, false, false),
    TBR(true, false, true);

    public static final LogicSlot[] ALL = values();
    private static final Map<String, LogicSlot> BY_ID = new HashMap<>();

    public final boolean top;
    public final boolean front;
    public final boolean right;
    public final String id;
    public final int bit;
    public final Vec3 offset;
    public final VoxelShape shape;

    LogicSlot(boolean top, boolean front, boolean right) {
        this.top = top;
        this.front = front;
        this.right = right;
        id = name().toLowerCase(Locale.ROOT);
        bit = 1 << ordinal();
        offset = new Vec3(right ? 0.25 : -0.25, top ? 0.25 : -0.25, front ? -0.25 : 0.25);
        shape = Shapes.box(right ? 0.5 : 0, top ? 0.5 : 0, front ? 0 : 0.5, right ? 1 : 0.5, top ? 1 : 0.5, front ? 0.5 : 1);
    }

    static {
        for (var slot : ALL) BY_ID.put(slot.id, slot);
    }

    @Nullable
    public static LogicSlot of(String id) {
        return BY_ID.get(id);
    }

    public static LogicSlot of(boolean top, boolean front, boolean right) {
        for (var slot : ALL) if (slot.top == top && slot.front == front && slot.right == right) return slot;
        return BFL;
    }

    public LogicSlot rotate(Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> of(top, !right, front);
            case CLOCKWISE_180 -> of(top, !front, !right);
            case COUNTERCLOCKWISE_90 -> of(top, right, !front);
            default -> this;
        };
    }

    public LogicSlot mirror(Mirror mirror) {
        return switch (mirror) {
            case LEFT_RIGHT -> of(top, !front, right);
            case FRONT_BACK -> of(top, front, !right);
            default -> this;
        };
    }

    public LogicSlot transform(StructureTransform transform) {
        var moved = transform.applyWithoutOffsetUncentered(offset);
        return of(moved.y > 0, moved.z < 0, moved.x > 0);
    }

    public Vec3 center() {
        return new Vec3(right ? 0.75 : 0.25, top ? 0.75 : 0.25, front ? 0.25 : 0.75);
    }

    @Nullable
    public LogicSlot across(Direction dir) {
        return switch (dir) {
            case NORTH -> front ? null : of(top, true, right);
            case SOUTH -> front ? of(top, false, right) : null;
            case WEST -> right ? of(top, front, false) : null;
            case EAST -> right ? null : of(top, front, true);
            case UP -> top ? null : of(true, front, right);
            case DOWN -> top ? of(false, front, right) : null;
        };
    }

    public static LogicSlot of(BlockPos pos, Vec3 hit, @Nullable Direction face, boolean outward) {
        var slot = at(pos, hit, face, outward);
        return slot == null ? nearest(pos, hit) : slot;
    }

    public static LogicSlot nearest(BlockPos pos, Vec3 hit) {
        var local = hit.subtract(Vec3.atLowerCornerOf(pos));
        return of(local.y >= 0.5, local.z < 0.5, local.x >= 0.5);
    }

    @Nullable
    public static LogicSlot at(BlockPos pos, Vec3 hit, @Nullable Direction face, boolean outward) {
        var local = hit.subtract(Vec3.atLowerCornerOf(pos));
        if (face != null) {
            var step = outward ? 0.002 : -0.002;
            local = local.add(face.getStepX() * step, face.getStepY() * step, face.getStepZ() * step);
        }
        if (local.x < 0 || local.x > 1 || local.y < 0 || local.y > 1 || local.z < 0 || local.z > 1) return null;
        return of(local.y >= 0.5, local.z < 0.5, local.x >= 0.5);
    }
}

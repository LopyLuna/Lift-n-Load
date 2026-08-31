package dev.lopyluna.create_lnl.content.blocks.logic_byte;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class LogicSlotTransform extends ValueBoxTransform {
    public final LogicSlot slot;

    public LogicSlotTransform(LogicSlot slot) {
        this.slot = slot;
    }

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        if (!state.hasProperty(LogicByteBlock.SLOTS) || (state.getValue(LogicByteBlock.SLOTS) & slot.bit) == 0) return null;
        if (!(level.getBlockEntity(pos) instanceof LogicByteBE be)) return null;
        return slot.center().add(Vec3.atLowerCornerOf(facing(be).getNormal()).scale(0.25));
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
        if (!(level.getBlockEntity(pos) instanceof LogicByteBE be)) return;
        TransformStack.of(ms).rotateYDegrees(AngleHelper.horizontalAngle(facing(be)) + 180);
    }

    @Override
    public boolean testHit(LevelAccessor level, BlockPos pos, BlockState state, Vec3 localHit) {
        if (getLocalOffset(level, pos, state) == null) return false;
        return half(localHit.x, slot.right) && half(localHit.y, slot.top) && half(localHit.z, !slot.front);
    }

    private static boolean half(double value, boolean upper) {
        return upper ? value >= 0.5 - 1.0e-4 : value <= 0.5 + 1.0e-4;
    }

    private Direction facing(LogicByteBE be) {
        return be.facings[slot.ordinal()];
    }
}

package dev.lopyluna.create_lnl.content.blocks.logic_byte;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.lopyluna.create_lnl.register.client.LiftsPartialModels;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

@SuppressWarnings("unused")
public class LogicByteRenderer extends SafeBlockEntityRenderer<LogicByteBE> {
    public LogicByteRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(LogicByteBE be, float pt, PoseStack ms, MultiBufferSource bs, int light, int overlay) {
        var state = be.getBlockState();
        var vb = bs.getBuffer(RenderType.solid());
        for (var slot : LogicSlot.ALL) {
            if (!be.has(slot)) continue;
            var lit = be.outputs[slot.ordinal()] > 0;
            var model = CachedBuffers.partial((lit ? LiftsPartialModels.LOGIC_BYTE_LIT : LiftsPartialModels.LOGIC_BYTE).get(slot), state);
            var center = slot.center();
            model.rotateAround(Axis.YP.rotationDegrees(rotation(be.facings[slot.ordinal()])), (float) center.x, (float) center.y, (float) center.z);
            model.light(light).renderInto(ms, vb);
        }
    }

    private static float rotation(Direction facing) {
        return switch (facing) {
            case EAST -> -90;
            case SOUTH -> 180;
            case WEST -> 90;
            default -> 0;
        };
    }
}

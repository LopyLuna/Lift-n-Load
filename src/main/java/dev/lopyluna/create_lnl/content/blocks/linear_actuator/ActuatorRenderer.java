package dev.lopyluna.create_lnl.content.blocks.linear_actuator;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.lopyluna.create_lnl.register.client.LiftsPartialModels;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

public class ActuatorRenderer extends KineticBlockEntityRenderer<ActuatorBE> {
    public ActuatorRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected BlockState getRenderedBlockState(ActuatorBE be) {
        return shaft(getRotationAxisOf(be));
    }

    @Override
    protected void renderSafe(ActuatorBE be, float pt, PoseStack ms, MultiBufferSource bs, int light, int overlay) {
        super.renderSafe(be, pt, ms, bs, light, overlay);
        var state = be.getBlockState();
        if (state.getValue(ActuatorBlock.HEAD)) return;

        var span = be.renderLength(pt) - 14 / 16f;
        if (span <= 0) return;
        var facing = state.getValue(ActuatorBlock.FACING);

        CachedBuffers.partial(LiftsPartialModels.ACTUATOR_MIDDLE, state)
                .center()
                .rotateYDegrees(AngleHelper.horizontalAngle(facing))
                .rotateXDegrees(AngleHelper.verticalAngle(facing) + 90)
                .uncenter()
                .translate(0, 15 / 16f, 0)
                .scale(1, span * 8, 1)
                .translate(0, -7 / 16f, 0)
                .light(light).overlay(overlay).renderInto(ms, bs.getBuffer(RenderType.solid()));
    }
}

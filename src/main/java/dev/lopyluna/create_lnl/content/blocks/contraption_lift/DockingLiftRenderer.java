package dev.lopyluna.create_lnl.content.blocks.contraption_lift;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.lopyluna.create_lnl.register.client.LiftsPartialModels;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.Vec3;

public class DockingLiftRenderer extends SmartBlockEntityRenderer<DockingLiftBE> {
    public DockingLiftRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(DockingLiftBE be, float pt, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(be, pt, ms, buffer, light, overlay);
        var state = be.getBlockState();
        if (state.getRenderShape() == RenderShape.INVISIBLE) return;

        var vb = buffer.getBuffer(RenderType.cutout());
        if (be.animating()) {
            be.angleFlap.tickChaser();
            CachedBuffers.partial(LiftsPartialModels.LIFT_ANIM_BASE, state).light(light).renderInto(ms, vb);

            var angle = be.angleFlap.getValue(pt);
            for (var dir : Iterate.horizontalDirections) {
                var flap = CachedBuffers.partial(be.color == null ? LiftsPartialModels.LIFT_ANIM_FLAPS.get(dir)
                        : LiftsPartialModels.DYED_LIFT_ANIM_FLAPS.get(be.color).get(dir), state);
                var foot = CachedBuffers.partial(be.color == null ? LiftsPartialModels.LIFT_ANIM_FOOTS.get(dir)
                        : LiftsPartialModels.DYED_LIFT_ANIM_FOOTS.get(be.color).get(dir), state);

                var a = (float) ((angle / 180f) * Math.PI);
                if (dir.getAxisDirection() == Direction.AxisDirection.NEGATIVE) a = -a;
                if (dir.getAxis() == Direction.Axis.X) a = -a;

                var flapPivot = new Vec3(0, 0, 0).relative(dir, 6).relative(Direction.DOWN, 5).scale(1 / 16f);
                flap.translate(-0.001, 9 / 16f, -0.001).center().translate(flapPivot).rotate(dir.getClockWise().getAxis(), a)
                        .uncenter().translate(0, -9 / 16f, 0).translate(flapPivot.scale(-1)).light(light).overlay(overlay).renderInto(ms, vb);

                var footPivot = new Vec3(0, 0, 0).relative(dir, 6).relative(Direction.DOWN, 17).scale(1 / 16f);
                foot.translate(0, 9 / 16f, 0).center().translate(footPivot).rotate(dir.getClockWise().getAxis(), -a)
                        .uncenter().translate(0, -9 / 16f, 0).translate(footPivot.scale(-1)).light(light).overlay(overlay).renderInto(ms, vb);
            }
            return;
        }

        var height = be.renderHeight(pt);

        CachedBuffers.partial(be.color != null ? LiftsPartialModels.DYED_LIFT_BOTTOM.get(be.color) : LiftsPartialModels.LIFT_BOTTOM, state)
                .light(light).overlay(overlay).renderInto(ms, vb);

        CachedBuffers.partial(be.color != null ? LiftsPartialModels.DYED_LIFT_TOP.get(be.color) : LiftsPartialModels.LIFT_TOP, state)
                .translate(0, height, 0).light(light).overlay(overlay).renderInto(ms, vb);

        CachedBuffers.partial(LiftsPartialModels.LIFT_MIDDLE, state)
                .translate(0, 5 / 16f, 0)
                .scale(1, 1 + height / (2 / 16f), 1)
                .translate(0, -5 / 16f, 0)
                .light(light).overlay(overlay).renderInto(ms, vb);
    }
}

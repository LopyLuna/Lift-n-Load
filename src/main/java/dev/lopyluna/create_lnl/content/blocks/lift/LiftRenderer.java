package dev.lopyluna.create_lnl.content.blocks.lift;

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

public class LiftRenderer extends SmartBlockEntityRenderer<LiftBE> {
    public LiftRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(LiftBE be, float pt, PoseStack ms, MultiBufferSource b, int l, int o) {
        super.renderSafe(be, pt, ms, b, l, o);
        final var state = be.getBlockState();
        if (state.getRenderShape() == RenderShape.INVISIBLE) return;
        final var vb = b.getBuffer(RenderType.cutout());
        if (!be.placed) {
            be.angleFlap.tickChaser();

            final var base = CachedBuffers.partial(LiftsPartialModels.LIFT_ANIM_BASE, state);
            base.light(l).renderInto(ms, vb);

            var angle = be.angleFlap.getValue(pt);
            for (var dir : Iterate.horizontalDirections) {
                final var flap = CachedBuffers.partial(LiftsPartialModels.LIFT_ANIM_FLAPS.get(dir), state);
                final var foot = CachedBuffers.partial(LiftsPartialModels.LIFT_ANIM_FOOTS.get(dir), state);
                var a = (float) ((angle / 180f) * Math.PI);
                if (dir.getAxisDirection() == Direction.AxisDirection.NEGATIVE) a = -a;
                if (dir.getAxis() == Direction.Axis.X) a = -a;
                {
                    var pivot = new Vec3(0, 0, 0).relative(dir, 6).relative(Direction.DOWN, 5).scale(1 / 16f);
                    flap.translate(-0.001, 9 / 16f, -0.001).center().translate(pivot).rotate(dir.getClockWise().getAxis(), a).uncenter().translate(0, -9 / 16f, 0).translate(pivot.scale(-1)).light(l).overlay(o).renderInto(ms, vb);
                }
                {
                    a = -a;
                    var pivot = new Vec3(0, 0, 0).relative(dir, 6).relative(Direction.DOWN, 17).scale(1 / 16f);
                    foot.translate(0, 9 / 16f, 0).center().translate(pivot).rotate(dir.getClockWise().getAxis(), a).uncenter().translate(0, -9 / 16f, 0).translate(pivot.scale(-1)).light(l).overlay(o).renderInto(ms, vb);
                }
            }
            return;
        }
        final var block = CachedBuffers.block(state);
        block.light(l).overlay(o).renderInto(ms, vb);

        be.cHeight.tickChaser();

        final var top = CachedBuffers.partial(LiftsPartialModels.LIFT_TOP, state);
        final var middle = CachedBuffers.partial(LiftsPartialModels.LIFT_MIDDLE, state);

        var height = be.cHeight.getValue(pt);
        top.translate(0, height, 0).light(l).overlay(o).renderInto(ms, vb);

        middle.translate(0, 5/16f, 0)
                .scale(1, 1+height/(2/16f), 1)
                .translate(0, -5/16f, 0)
                .light(l).overlay(o).renderInto(ms, vb);
    }
}

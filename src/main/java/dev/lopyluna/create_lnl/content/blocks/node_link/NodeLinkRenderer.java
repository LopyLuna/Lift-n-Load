package dev.lopyluna.create_lnl.content.blocks.node_link;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.lopyluna.create_lnl.register.client.LiftsPartialModels;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

@SuppressWarnings("unused")
public class NodeLinkRenderer extends SafeBlockEntityRenderer<NodeLinkBE> {
    public NodeLinkRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(NodeLinkBE be, float pt, PoseStack ms, MultiBufferSource bs, int l, int o) {
        var state = be.getBlockState();
        var model = CachedBuffers.partial(be.facing.getAxis().isHorizontal() ? LiftsPartialModels.NODE_OVERLAY_VERTICAL : LiftsPartialModels.NODE_OVERLAY, state);

        var rX = be.facing == Direction.UP ? 0 : be.facing == Direction.DOWN ? 180 : 90;
        //var rotY = (be.facing.getAxis().isVertical() ? 180 : horizontalAngle(be.facing) + 360) % 360f;
        var rY = AngleHelper.horizontalAngle(be.facing);

        var vb = bs.getBuffer(RenderType.translucent());

        model.rotateCentered(rY/180f*(float)Math.PI, Direction.UP);
        model.rotateCentered(rX/180f*(float)Math.PI, Direction.EAST);
        //model.rotateCentered(rotX/180f*(float)Math.PI, Direction.EAST);

        model.light(LightTexture.FULL_BRIGHT).color(255, 255, 255, (int) (be.getStrength()/15f*255f)).renderInto(ms, vb);
    }
}

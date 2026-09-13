package dev.lopyluna.create_lnl.content.items.gravity_grip;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.lopyluna.create_lnl.Lifts;
import dev.simulated_team.simulated.index.SimSoundEvents;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class GravityGripRenderer extends CustomRenderedItemModelRenderer {
    protected static final PartialModel COG = PartialModel.of(Lifts.loc("item/gravity_grip/cog"));

    protected static final PartialModel LEFT_HANDLE = PartialModel.of(Lifts.loc("item/gravity_grip/left_handle"));
    protected static final PartialModel LEFT_POINT = PartialModel.of(Lifts.loc("item/gravity_grip/left_point"));
    protected static final PartialModel LEFT_CLOSED = PartialModel.of(Lifts.loc("item/gravity_grip/left_closed"));
    protected static final PartialModel LEFT_HOLD = PartialModel.of(Lifts.loc("item/gravity_grip/left_hold"));
    protected static final PartialModel LEFT_UP = PartialModel.of(Lifts.loc("item/gravity_grip/left_up"));
    protected static final PartialModel LEFT_DOWN = PartialModel.of(Lifts.loc("item/gravity_grip/left_down"));

    protected static final PartialModel RIGHT_HANDLE = PartialModel.of(Lifts.loc("item/gravity_grip/right_handle"));
    protected static final PartialModel RIGHT_POINT = PartialModel.of(Lifts.loc("item/gravity_grip/right_point"));
    protected static final PartialModel RIGHT_CLOSED = PartialModel.of(Lifts.loc("item/gravity_grip/right_closed"));
    protected static final PartialModel RIGHT_HOLD = PartialModel.of(Lifts.loc("item/gravity_grip/right_hold"));
    protected static final PartialModel RIGHT_UP = PartialModel.of(Lifts.loc("item/gravity_grip/right_up"));
    protected static final PartialModel RIGHT_DOWN = PartialModel.of(Lifts.loc("item/gravity_grip/right_down"));

    protected static final PartialModel CUBE = PartialModel.of(Lifts.loc("item/gravity_grip/cube"));
    protected static final PartialModel CUBE_OUTLINE = PartialModel.of(Lifts.loc("item/gravity_grip/cube_outline"));

    private static GravityGripItem.HandState oldState = GravityGripItem.HandState.POINT;

    @Override
    protected void render(ItemStack s, CustomRenderedItemModel m, PartialItemModelRenderer pr, ItemDisplayContext ctx, PoseStack ms, MultiBufferSource bs, int l, int o) {
        var mc = Minecraft.getInstance();

        var pt = AnimationTickHolder.getPartialTicks();

        final var left = ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        ms.pushPose();
        pr.renderSolid(m.getOriginalModel(), l);
        ms.popPose();

        ms.pushPose();
        if (left) pr.renderSolid(LEFT_HANDLE.get(), l);
        else pr.renderSolid(RIGHT_HANDLE.get(), l);
        ms.popPose();

        var state = mc.player == null ? GravityGripItem.HandState.POINT : GravityGripHandler.stateOf(mc.player, s);
        if (mc.player != null && ((oldState == GravityGripItem.HandState.POINT && state == GravityGripItem.HandState.CLOSED) || (oldState == GravityGripItem.HandState.CLOSED && state == GravityGripItem.HandState.POINT))) {
            mc.player.playSound(SimSoundEvents.ASSEMBLER_TICK.event(), 0.125f, 0.75f);
            mc.player.playSound(SimSoundEvents.ASSEMBLER_TICK.event(), 0.125f, 2f);
        }
        var holding = state == GravityGripItem.HandState.HOLD;
        var zCtrlAngle = GravityGripHandler.spin(pt);

        ms.pushPose();
        ms.mulPose(Axis.ZP.rotationDegrees(zCtrlAngle % 360));
        pr.renderSolid(getHand(left, state).get(), l);
        ms.popPose();

        if (holding) {
            ms.pushPose();
            ms.translate(0, 0, -15/16f);
            ms.mulPose(GravityGripHandler.cube(pt));
            ms.translate(0, 0, 15/16f);
            pr.renderSolidGlowing(CUBE.get(), LightTexture.FULL_BRIGHT);
            pr.renderGlowing(CUBE_OUTLINE.get(), LightTexture.FULL_BRIGHT);
            ms.popPose();
        }

        var angle = (GravityGripHandler.cog(pt) * (left ? -1 : 1) + (zCtrlAngle * 2)) % 360;

        ms.pushPose();
        ms.mulPose(Axis.ZP.rotationDegrees(angle));
        pr.renderSolid(COG.get(), l);
        ms.popPose();

        oldState = state;
    }


    public static PartialModel getHand(boolean left, GravityGripItem.HandState state) {
        return switch (state) {
            case POINT -> left ? LEFT_POINT : RIGHT_POINT;
            case CLOSED -> left ? LEFT_CLOSED : RIGHT_CLOSED;
            case HOLD -> left ? LEFT_HOLD : RIGHT_HOLD;
            case UP -> left ? LEFT_UP : RIGHT_UP;
            case DOWN -> left ? LEFT_DOWN : RIGHT_DOWN;
        };
    }
}

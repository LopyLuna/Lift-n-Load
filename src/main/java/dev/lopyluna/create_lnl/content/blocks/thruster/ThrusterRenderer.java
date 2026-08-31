package dev.lopyluna.create_lnl.content.blocks.thruster;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.lopyluna.create_lnl.Lifts;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.createmod.ponder.api.level.PonderLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class ThrusterRenderer extends SmartBlockEntityRenderer<ThrusterBE> {
    private static final ResourceLocation THRUSTER_FLAME_SHADER = Lifts.loc("thruster_flame");
    private static final float FLAME_SIZE = 2f;
    private static final float BLOCK_PIXEL = 1f / 16f;
    private static final float FLAME_PIXEL = BLOCK_PIXEL / FLAME_SIZE;

    public ThrusterRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(ThrusterBE be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        var intensity = Mth.clamp(be.intensity.getValue(partialTicks) * 1.5f, 0f, 1f);
        if (!(intensity > 0.05)) return;

        final var state = be.getBlockState();
        final var pos = be.getBlockPos();
        final var facing = state.getValue(ThrusterBlock.FACING);
        final var size = 1f;
        var flameOffset = snapToBlockPixel((0.35f + ((24 - 4 * Mth.clamp(intensity, 0.5f, 1f)) / 16f) - 0.5f) / (Mth.clamp(size*0.5f, 1, 16)));
        var lengthMultiplier = snapToFlamePixel((intensity * 4f + 1f + (0.5f - intensity * intensity * intensity)) * size);
        var widthMultiplier = snapToFlamePixel((intensity * 1.5f + 1) * size);

        ms.pushPose();
        ms.translate(0.5f, 0.5f, 0.5f);
        ms.translate(facing.getStepX() * flameOffset, facing.getStepY() * flameOffset, facing.getStepZ() * flameOffset);
        rotateTowardsFacing(ms, facing);
        ms.mulPose(Axis.YP.rotation(getBillboardAngle(be, pos, facing, flameOffset, partialTicks)));

        final var palette = be.intensitySwitch.getValue(partialTicks); //Soul Fire modifier

        final ShaderProgram shader = VeilRenderSystem.setShader(THRUSTER_FLAME_SHADER);
        if (shader == null) {
            ms.popPose();
            return;
        }
        final var flameRenderTime = (float) Mth.lerp(partialTicks, be.lastRenderTime, be.renderTime) + (pos.hashCode() % 10);
        shader.getUniformSafe("FlameRenderTime").setFloat(flameRenderTime);
        shader.getUniformSafe("Intensity").setFloat(Mth.clamp(intensity * 2f - .35f, 0.25f, 1.5f));
        shader.getUniformSafe("Palette").setFloat(palette);
        shader.getUniformSafe("LengthMultiplier").setFloat(Math.max(lengthMultiplier, FLAME_PIXEL));
        shader.getUniformSafe("WidthMultiplier").setFloat(Math.max(widthMultiplier, FLAME_PIXEL));

        renderFlame(ms, FLAME_SIZE, lengthMultiplier, widthMultiplier);
        ms.popPose();
    }

    private static float snapToBlockPixel(float value) {
        return Math.round(value / BLOCK_PIXEL) * BLOCK_PIXEL;
    }

    private static float snapToFlamePixel(float value) {
        return Math.max(0f, Math.round(value / FLAME_PIXEL) * FLAME_PIXEL);
    }

    private static void rotateTowardsFacing(PoseStack poseStack, Direction facing) {
        switch (facing) {
            case DOWN -> poseStack.mulPose(Axis.ZP.rotation((float) Math.PI));
            case NORTH -> poseStack.mulPose(Axis.XN.rotation((float) (Math.PI / 2f)));
            case SOUTH -> poseStack.mulPose(Axis.XP.rotation((float) (Math.PI / 2f)));
            case EAST -> poseStack.mulPose(Axis.ZN.rotation((float) (Math.PI / 2f)));
            case WEST -> poseStack.mulPose(Axis.ZP.rotation((float) (Math.PI / 2f)));
            default -> {
            }
        }
    }

    private static float getBillboardAngle(ThrusterBE be, BlockPos pos, Direction facing, float offset, float partialTicks) {
        final var origin = pos.getCenter().add(facing.getStepX() * offset, facing.getStepY() * offset, facing.getStepZ() * offset);
        final var toCamera = getCameraPos(be, partialTicks).subtract(origin);
        final var local = toLocalFacingSpace(toCamera, facing);
        return (float) Math.atan2(local.x, local.z);
    }

    private static Vec3 getCameraPos(ThrusterBE be, float partialTicks) {
        final var mc = Minecraft.getInstance();
        var cam = mc.gameRenderer.getMainCamera().getPosition();

        if (be.getLevel() instanceof PonderLevel && mc.getCameraEntity() instanceof Entity camE)
            cam = camE.getPosition(partialTicks);
        if (Sable.HELPER.getContaining(be) instanceof SubLevel subLevel)
            cam = subLevel.logicalPose().transformPositionInverse(cam);

        return cam;
    }

    @SuppressWarnings("SuspiciousNameCombination")
    private static Vec3 toLocalFacingSpace(Vec3 vec, Direction facing) {
        return switch (facing) {
            case DOWN -> new Vec3(-vec.x, -vec.y, vec.z);
            case NORTH -> new Vec3(vec.x, -vec.z, vec.y);
            case SOUTH -> new Vec3(vec.x, vec.z, -vec.y);
            case EAST -> new Vec3(-vec.y, vec.x, vec.z);
            case WEST -> new Vec3(vec.y, -vec.x, vec.z);
            default -> vec;
        };
    }

    @SuppressWarnings("SameParameterValue")
    private static void renderFlame(final PoseStack poseStack, final float size, final float lengthMultiplier, final float widthMultiplier) {
        final var builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        final var halfSize = (size / 2f) * widthMultiplier;

        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();

        final var pose = poseStack.last().pose();
        builder.addVertex(pose, -halfSize, 0f, 0f).setUv(0f, 1f);
        builder.addVertex(pose, halfSize, 0f, 0f).setUv(1f, 1f);
        builder.addVertex(pose, halfSize, size*lengthMultiplier, 0f).setUv(1f, 0f);
        builder.addVertex(pose, -halfSize, size*lengthMultiplier, 0f).setUv(0f, 0f);

        BufferUploader.drawWithShader(builder.buildOrThrow());

        RenderSystem.disableDepthTest();
        RenderSystem.enableCull();
    }
}

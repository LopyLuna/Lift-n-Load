package dev.lopyluna.create_lnl.content.blocks.wheel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.lopyluna.create_lnl.Lifts;
import dev.lopyluna.create_lnl.register.client.LiftsPartialModels;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public class WheelRenderer extends KineticBlockEntityRenderer<WheelBE> {
    public WheelRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(WheelBE be, float pt, PoseStack ps, MultiBufferSource bs, int l, int o) {
        super.renderSafe(be, pt, ps, bs, l, o);
        var level = be.getLevel();
        if (level == null) return;
        var pos = be.getBlockPos();
        var state = be.getBlockState();
        float angle = (float) Mth.lerp(pt, be.lastAngle, be.angle);
        float renderAngle = -angle;

        VertexConsumer vb = bs.getBuffer(RenderType.solid());
        renderWheel(be, ps, l, state, renderAngle, vb);
        if (!be.sticky || be.stickyVisualCastPoints.isEmpty()) return;
        var light = LightTexture.pack(level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.SKY, pos));
        renderStickyGlue(be, ps, bs, light, renderAngle);
    }

    private void renderWheel(WheelBE be, PoseStack ms, int light, BlockState blockState, float angle, VertexConsumer vb) {
        var model = switch (be.type) {
            case MONSTROUS -> be.sticky ? LiftsPartialModels.SLIME_MONSTROUS_TIRE : LiftsPartialModels.MONSTROUS_TIRE;
            case LARGE -> be.sticky ? LiftsPartialModels.SLIME_LARGE_TIRE : LiftsPartialModels.LARGE_TIRE;
            case NORMAL -> be.sticky ? LiftsPartialModels.SLIME_TIRE : LiftsPartialModels.TIRE;
            case SMALL -> be.sticky ? LiftsPartialModels.SLIME_SMALL_TIRE : LiftsPartialModels.SMALL_TIRE;
        };
        var wheel = CachedBuffers.partialFacingVertical(model, blockState, Direction.fromAxisAndDirection(blockState.getValue(WheelBlock.AXIS), Direction.AxisDirection.POSITIVE));
        kineticRotationTransform(wheel, be, getRotationAxisOf(be), angle, light);
        wheel.renderInto(ms, vb);
    }

    private void renderStickyGlue(WheelBE be, PoseStack ms, MultiBufferSource bs, int light, float angle) {
        final Direction axisDir = Direction.fromAxisAndDirection(be.getBlockState().getValue(WheelBlock.AXIS), Direction.AxisDirection.POSITIVE);
        final Vec3 side = new Vec3(axisDir.getStepX(), axisDir.getStepY(), axisDir.getStepZ()).normalize();
        final Vec3 center = new Vec3(0.5, 0.5, 0.5);
        final double strandHalfWidth = Math.max(be.radius * 0.18, 0.09) * (3f * (2f-be.radius));
        VertexConsumer buffer = bs.getBuffer(RenderType.entityCutout(Lifts.loc("textures/block/tire/slime.png")));
        for (int i = 0; i < Math.min(be.stickyVisualCastPoints.size(), be.stickyVisualEndPoints.length); i++) {
            final Vec3 stickyEndPoint = be.stickyVisualEndPoints[i];
            if (stickyEndPoint == null) continue;

            final var castPoint = be.getStickyVisualCastPoint(i, angle);
            final Vec3 start = center.add(castPoint.getFirst());
            final Vec3 stickyEnd = toRenderLocal(be, stickyEndPoint, be.stickyVisualEndSubLevels[i]);
            final Vec3 strand = stickyEnd.subtract(start);
            if (strand.lengthSqr() <= 1.0E-6) continue;

            Vec3 upVec = strand.cross(side);
            if (upVec.lengthSqr() <= 1.0E-6) upVec = strand.cross(new Vec3(0, 1, 0));
            if (upVec.lengthSqr() <= 1.0E-6) upVec = new Vec3(1, 0, 0);

            final Vector3d up = toJoml(upVec.normalize().scale(strandHalfWidth));
            final Vector3d right = toJoml(side.scale(strandHalfWidth));
            renderGlueCross(toJoml(start), up, right, toJoml(stickyEnd), up, right, buffer, ms, light);
        }
    }

    private static Vec3 toRenderLocal(WheelBE be, Vec3 point, SubLevel pointSubLevel) {
        Vector3d pos = new Vector3d(point.x, point.y, point.z);
        if (pointSubLevel instanceof ClientSubLevel pointClientSubLevel) pointClientSubLevel.renderPose().transformPosition(pos);

        SubLevel wheelSubLevel = Sable.HELPER.getContainingClient(be);
        if (wheelSubLevel instanceof ClientSubLevel wheelClientSubLevel) wheelClientSubLevel.renderPose().transformPositionInverse(pos);
        return new Vec3(pos.x, pos.y, pos.z).subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
    }

    private static Vector3d toJoml(Vec3 vec) {
        return new Vector3d(vec.x, vec.y, vec.z);
    }

    private static VertexConsumer addVertex(VertexConsumer buffer, Matrix4f pose, Vector3dc pos) {
        return buffer.addVertex(pose, (float) pos.x(), (float) pos.y(), (float) pos.z());
    }

    private static void renderGlueCross(Vector3dc posA, Vector3dc upA, Vector3dc rightA,
                                        Vector3dc posB, Vector3dc upB, Vector3dc rightB,
                                        VertexConsumer buffer, PoseStack ms, int light) {
        Matrix4f pose = ms.last().pose();
        Vector3d vertex = new Vector3d();

        addVertex(buffer, pose, posA.fma(-1.0, upA, vertex)).setColor(0xffffffff).setUv(0.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(ms.last(), 0.0f, 1.0f, 0.0f);
        addVertex(buffer, pose, posA.fma(1.0, upA, vertex)).setColor(0xffffffff).setUv(0.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(ms.last(), 0.0f, 1.0f, 0.0f);
        addVertex(buffer, pose, posB.fma(1.0, upB, vertex)).setColor(0xffffffff).setUv(1.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(ms.last(), 0.0f, 1.0f, 0.0f);
        addVertex(buffer, pose, posB.fma(-1.0, upB, vertex)).setColor(0xffffffff).setUv(1.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(ms.last(), 0.0f, 1.0f, 0.0f);
        addVertex(buffer, pose, posB.fma(-1.0, upB, vertex)).setColor(0xffffffff).setUv(1.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(ms.last(), 0.0f, 1.0f, 0.0f);
        addVertex(buffer, pose, posB.fma(1.0, upB, vertex)).setColor(0xffffffff).setUv(1.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(ms.last(), 0.0f, 1.0f, 0.0f);
        addVertex(buffer, pose, posA.fma(1.0, upA, vertex)).setColor(0xffffffff).setUv(0.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(ms.last(), 0.0f, 1.0f, 0.0f);
        addVertex(buffer, pose, posA.fma(-1.0, upA, vertex)).setColor(0xffffffff).setUv(0.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(ms.last(), 0.0f, 1.0f, 0.0f);

        addVertex(buffer, pose, posA.fma(-1.0, rightA, vertex)).setColor(0xffffffff).setUv(0.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(ms.last(), 0.0f, 1.0f, 0.0f);
        addVertex(buffer, pose, posA.fma(1.0, rightA, vertex)).setColor(0xffffffff).setUv(0.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(ms.last(), 0.0f, 1.0f, 0.0f);
        addVertex(buffer, pose, posB.fma(1.0, rightB, vertex)).setColor(0xffffffff).setUv(1.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(ms.last(), 0.0f, 1.0f, 0.0f);
        addVertex(buffer, pose, posB.fma(-1.0, rightB, vertex)).setColor(0xffffffff).setUv(1.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(ms.last(), 0.0f, 1.0f, 0.0f);
        addVertex(buffer, pose, posB.fma(-1.0, rightB, vertex)).setColor(0xffffffff).setUv(1.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(ms.last(), 0.0f, 1.0f, 0.0f);
        addVertex(buffer, pose, posB.fma(1.0, rightB, vertex)).setColor(0xffffffff).setUv(1.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(ms.last(), 0.0f, 1.0f, 0.0f);
        addVertex(buffer, pose, posA.fma(1.0, rightA, vertex)).setColor(0xffffffff).setUv(0.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(ms.last(), 0.0f, 1.0f, 0.0f);
        addVertex(buffer, pose, posA.fma(-1.0, rightA, vertex)).setColor(0xffffffff).setUv(0.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(ms.last(), 0.0f, 1.0f, 0.0f);
    }

    @Override
    protected BlockState getRenderedBlockState(WheelBE be) {
        return shaft(getRotationAxisOf(be));
    }
}

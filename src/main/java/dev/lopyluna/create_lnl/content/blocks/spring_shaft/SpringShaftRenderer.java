package dev.lopyluna.create_lnl.content.blocks.spring_shaft;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.blocks.spring.SpringBlock;
import dev.simulated_team.simulated.index.SimRenderTypes;
import dev.simulated_team.simulated.util.SimColors;
import dev.simulated_team.simulated.util.SimMathUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;

@SuppressWarnings("NullableProblems")
public class SpringShaftRenderer extends KineticBlockEntityRenderer<SpringShaftBE> {
    private final Vector3d controlPointA = new Vector3d();
    private final Vector3d controlPointB = new Vector3d();
    private final Vector3d segmentALerp = new Vector3d();
    private final Vector3d segmentBLerp = new Vector3d();
    private final Vector3d segmentCLerp = new Vector3d();
    private final Vector3d startUp = new Vector3d();
    private final Vector3d endUp = new Vector3d();
    private final Vector3d startLeft = new Vector3d();
    private final Vector3d endLeft = new Vector3d();
    private final Vector3d normalizedNormal = new Vector3d();
    private final Vector3d vertex = new Vector3d();

    public static final Minecraft mc = Minecraft.getInstance();

    public SpringShaftRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(SpringShaftBE be, float pt, PoseStack ps, MultiBufferSource bs, int l, int o) {
        super.renderSafe(be, pt, ps, bs, l, o);
        if (!be.isController || mc.level == null) return;
        var other = be.getPairedBE();
        if (other == null) return;
        var name = (be.size == SpringBlock.Size.MEDIUM ? "" : (be.size.getSerializedName() + "_")) + "spring";
        var buffer = bs.getBuffer(SimRenderTypes.spring(Simulated.path("textures/block/spring/" + name + ".png")));

        ps.pushPose();
        var container = SubLevelContainer.getContainer(mc.level);
        var otherSubLevel = be.partnerSubLevel != null && container != null ? container.getSubLevel(be.partnerSubLevel) instanceof ClientSubLevel subLevel ? subLevel : null : null;
        var subLevel = Sable.HELPER.getContainingClient(be);

        var blockPos = be.getBlockPos();
        var center = be.getCenter();
        var otherCenter = other.getCenter();

        var normalA = JOMLConversion.atLowerCornerOf(be.facing.getNormal());
        var normalB = JOMLConversion.atLowerCornerOf(other.facing.getNormal());

        ps.translate(center.x() - (blockPos.getX()), center.y() - (blockPos.getY()), center.z() - (blockPos.getZ()));

        var PI2 = (float) Math.PI / 2.0f;
        var PI4 = PI2 / 2.0f;
        var renderPose = subLevel != null ? subLevel.renderPose() : null;
        var otherRenderPose = otherSubLevel != null ? otherSubLevel.renderPose() : null;

        if (otherRenderPose != null) {
            otherRenderPose.transformNormal(normalB);
            otherRenderPose.transformPosition(otherCenter);
        }

        if (renderPose != null) {
            renderPose.transformNormalInverse(normalB);
            renderPose.transformPositionInverse(otherCenter);
        }

        var color = getStressColor(be, pt, otherCenter, center);

        var splinePoints = this.generateSpline(otherCenter.sub(center, new Vector3d()), normalA, normalB, (float) center.distance(otherCenter) / 5.0f + 0.25f);

        var totalPoints = splinePoints.size();
        var pointNormal = new Vector3d();
        var startUpDir = JOMLConversion.toJOML(getUpDirection(be, otherCenter.sub(center, new Vector3d())));

        pointNormal.set(splinePoints.getFirst().normal);
        var matrix = new Matrix3d(startUpDir, pointNormal, startUpDir.cross(pointNormal, new Vector3d()));

        float totalSpringLength = 0f;
        for (int i = 0; i < totalPoints - 1; i++) {
            var point = splinePoints.get(i);
            var nextPoint = splinePoints.get(i + 1);
            totalSpringLength += (float) point.point.distance(nextPoint.point);
            matrix.rotateLocal(SimMathUtils.getQuaternionfFromVectorRotation(point.normal, nextPoint.normal));
        }

        var orientation = new Quaterniond();

        var orientation1 = renderPose != null ? renderPose.orientation() : JOMLConversion.QUAT_IDENTITY;
        var orientation2 = otherRenderPose != null ? otherRenderPose.orientation() : JOMLConversion.QUAT_IDENTITY;

        var blockOrientation1 = new Quaterniond(be.facing.getRotation());
        var blockOrientation2 = new Quaterniond(other.facing.getRotation());
        blockOrientation2.premul(orientation2).premul(orientation1.conjugate(new Quaterniond()));

        var relativeBlockOrientation = new Quaterniond(blockOrientation1).div(blockOrientation2);

        orientation.mul(new Quaterniond(relativeBlockOrientation));
        orientation.mul(matrix.getNormalizedRotation(new Quaterniond()));

        if (Math.abs(OrientedBoundingBox3d.UP.dot(new Vector3d(orientation.x(), orientation.y(), orientation.z()))) < 1e-5) orientation.rotateLocalX(Math.PI);

        var d = (float) OrientedBoundingBox3d.UP.dot(new Vector3d(orientation.x(), orientation.y(), orientation.z()));
        var deg = 2f * (float) Mth.atan2(-d, orientation.w());
        var twist = (float) Mth.floor((deg + PI4) / PI2) * PI2 - deg;

        var uvScale = (be.renderLength.getValue(pt) - 0.75f) / totalSpringLength;
        float runningSpringLength = 0f;
        matrix.set(startUpDir, pointNormal, startUpDir.cross(pointNormal, new Vector3d()));
        float angle = getAngleForBe(other, other.getBlockPos(), other.facing.getAxis());
        float uvOffset = angle / Mth.TWO_PI;

        for (int i = 0; i < totalPoints - 1; i++) {
            var point = splinePoints.get(i);
            var nextPoint = splinePoints.get(i + 1);

            var upDir = matrix.getColumn(0, new Vector3d());

            matrix.rotateLocal(SimMathUtils.getQuaternionfFromVectorRotation(point.normal, nextPoint.normal));
            matrix.rotateY(-twist / (totalPoints - 1));

            var nextUpDir = matrix.getColumn(0, new Vector3d());

            var length = (float) point.point.distance(nextPoint.point);
            var width = switch (be.size) { case SMALL -> 6f; case MEDIUM -> 8f; case LARGE -> 10f; };
            var textureWidth = switch (be.size) { case SMALL, MEDIUM -> 16f; case LARGE -> 32f; };

            this.renderSegment(ps, point.normal, nextPoint.normal, upDir, nextUpDir, point.point, nextPoint.point,
                    false, runningSpringLength * uvScale + uvOffset, (runningSpringLength + length) * uvScale + uvOffset, l, color, buffer, width, textureWidth);

            this.renderSegment(ps, point.normal.negate(new Vector3d()), nextPoint.normal.negate(new Vector3d()),
                    upDir.negate(new Vector3d()), nextUpDir.negate(new Vector3d()), point.point, nextPoint.point,
                    true, -runningSpringLength * uvScale - uvOffset, -(runningSpringLength + length) * uvScale - uvOffset, l, color, buffer, width, textureWidth);
            runningSpringLength += length;
        }

        ps.popPose();
    }

    @Override
    protected SuperByteBuffer getRotatedModel(SpringShaftBE be, BlockState state) {
        return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, be.facing.getOpposite());
    }

    private static int getStressColor(SpringShaftBE be, float pt, Vector3d otherCenter, Vector3dc center) {
        if (mc.player == null) return 0xFFFFFF;
        var distance = (float) otherCenter.distance(center);
        var snapDistance = be.getSnappingDistance();
        var flashingStartExtension = Mth.lerp(0.7f, (be.renderLength.getValue(pt) - 0.75f), snapDistance);

        float stressAlpha = 0f;
        if (distance > flashingStartExtension) {
            var renderTime = mc.player.tickCount + pt;
            stressAlpha = Mth.clamp((distance - flashingStartExtension) / (snapDistance - flashingStartExtension), 0f, 1f) * 0.3f;
            stressAlpha = stressAlpha * Mth.lerp(0.25f, (float) Math.sin(renderTime / 3f) * 0.5f + 0.5f, 1f);
        }
        return SimColors.STRESSED_RED & 0xFFFFFF | ((int) (stressAlpha * 255) << 24);
    }

    private Vec3 getUpDirection(SpringShaftBE be, Vector3dc directionToSpring) {
        var normal = Vec3.atLowerCornerOf(be.facing.getNormal());
        var dot = directionToSpring.dot(normal.x, normal.y, normal.z);
        var dir = directionToSpring.sub(normal.x * dot, normal.y * dot, normal.z * dot, new Vector3d());

        if (dir.lengthSquared() < 1e-6) return be.facing.getAxis().isHorizontal() ? new Vec3(0, 1, 0) : new Vec3(0, 0, -1);
        return Vec3.atLowerCornerOf(Direction.getNearest(dir.x, dir.y, dir.z).getOpposite().getNormal());
    }

    private List<SplinePoint> generateSpline(Vector3dc pointB, Vector3dc normalA, Vector3dc normalB, float controlPointLength) {
        final List<SplinePoint> list = new ObjectArrayList<>();

        JOMLConversion.ZERO.fma(controlPointLength, normalA, this.controlPointA);
        pointB.fma(controlPointLength, normalB, this.controlPointB);

        var len = (float) JOMLConversion.ZERO.distance(pointB);
        var initialPointCount = Mth.clamp(Mth.ceil(len), 5, 8);
        for (int i = 0; i <= initialPointCount; i++) {
            var t = (float) i / initialPointCount;
            JOMLConversion.ZERO.lerp(this.controlPointA, t, this.segmentALerp);
            this.controlPointA.lerp(this.controlPointB, t, this.segmentBLerp);
            this.controlPointB.lerp(pointB, t, this.segmentCLerp);

            var point = new Vector3d(this.segmentALerp.lerp(this.segmentBLerp, t).lerp(this.segmentBLerp.lerp(this.segmentCLerp, t), t));
            var normal = new Vector3d();

            if (list.isEmpty()) normal.set(normalA);
            else if (list.size() == initialPointCount) normal.set(normalB).negate();
            else point.sub(list.getLast().point, normal).normalize();

            list.add(new SplinePoint(point, normal));
        }
        return list;
    }

    @Override
    public boolean shouldRender(SpringShaftBE be, Vec3 cameraPos) {
        return true;
    }

    private void renderSegment(PoseStack ms, Vector3dc sDir, Vector3dc eDir, Vector3dc sInUp, Vector3dc eInUp, Vector3dc sPos, Vector3dc ePos,
                               boolean second, float sUV, float eUV, int l, int c, VertexConsumer vc, float w, float tw) {
        sInUp.cross(sDir, this.startLeft).normalize();
        eInUp.cross(eDir, this.endLeft).normalize();

        var texW = w / tw;
        var scale = w / 16f / 2f;

        startLeft.mul(scale);
        sInUp.mul(scale, this.startUp);
        endLeft.mul(scale);
        eInUp.mul(scale, this.endUp);

        var sD = startUp.negate(new Vector3d());
        var eD = endUp.negate(new Vector3d());
        var sR = startLeft.negate(new Vector3d());
        var eR = endLeft.negate(new Vector3d());

        var uvScale = 16f / tw;
        var uvXOff = second ? w / tw : 0f;
        vert(ms, vc, sPos.add(startLeft, vertex).sub(startUp), c, 0f + uvXOff, sUV * uvScale, sD, l);
        vert(ms, vc, ePos.add(endLeft, vertex).sub(endUp), c, 0f + uvXOff, eUV * uvScale, eD, l);
        vert(ms, vc, ePos.sub(endLeft, vertex).sub(endUp), c, texW + uvXOff, eUV * uvScale, eD, l);
        vert(ms, vc, sPos.sub(startLeft, vertex).sub(startUp), c, texW + uvXOff, sUV * uvScale, sD, l);

        vert(ms, vc, sPos.sub(startLeft, vertex).add(startUp), c, 0f + uvXOff, sUV * uvScale, startUp, l);
        vert(ms, vc, ePos.sub(endLeft, vertex).add(endUp), c, 0f + uvXOff, eUV * uvScale, endUp, l);
        vert(ms, vc, ePos.add(endLeft, vertex).add(endUp), c, texW + uvXOff, eUV * uvScale, endUp, l);
        vert(ms, vc, sPos.add(startLeft, vertex).add(startUp), c, texW + uvXOff, sUV * uvScale, startUp, l);

        vert(ms, vc, sPos.sub(startLeft, vertex).sub(startUp), c, 0f + uvXOff, sUV * uvScale, sR, l);
        vert(ms, vc, ePos.sub(endLeft, vertex).sub(endUp), c, 0f + uvXOff, eUV * uvScale, eR, l);
        vert(ms, vc, ePos.sub(endLeft, vertex).add(endUp), c, texW + uvXOff, eUV * uvScale, eR, l);
        vert(ms, vc, sPos.sub(startLeft, vertex).add(startUp), c, texW + uvXOff, sUV * uvScale, sR, l);

        vert(ms, vc, sPos.add(startLeft, vertex).add(startUp), c, 0f + uvXOff, sUV * uvScale, startLeft, l);
        vert(ms, vc, ePos.add(endLeft, vertex).add(endUp), c, 0f + uvXOff, eUV * uvScale, endLeft, l);
        vert(ms, vc, ePos.add(endLeft, vertex).sub(endUp), c, texW + uvXOff, eUV * uvScale, endLeft, l);
        vert(ms, vc, sPos.add(startLeft, vertex).sub(startUp), c, texW + uvXOff, sUV * uvScale, startLeft, l);
    }

    private void vert(final PoseStack ms, final VertexConsumer a, final Vector3dc pos, final int color, final float u1, final float v1, final Vector3dc normal, final int light) {
        normal.normalize(this.normalizedNormal);
        a.addVertex(ms.last().pose(), (float) pos.x(), (float) pos.y(), (float) pos.z()).setColor(color).setUv(u1, v1).setLight(light)
                .setNormal(ms.last(), (float) this.normalizedNormal.x(), (float) this.normalizedNormal.y(), (float) this.normalizedNormal.z());
    }

    record SplinePoint(Vector3dc point, Vector3dc normal) {}
}

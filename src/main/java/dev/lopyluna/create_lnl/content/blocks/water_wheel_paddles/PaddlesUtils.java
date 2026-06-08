package dev.lopyluna.create_lnl.content.blocks.water_wheel_paddles;

import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import dev.lopyluna.create_lnl.content.utils.LiftUtils;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

public class PaddlesUtils {
    private static final double FORCE_PER_SPEED = 1.2;
    private static final double SAMPLE_RADIUS = 0.5;

    public static List<Couple<Vec3>> genCastPoints(BlockState state, int size) {
        var axis = LiftUtils.getBlockAxis(state);
        var rayPoints = new ArrayList<Couple<Vec3>>();

        var castDirs = new ArrayList<Vec3>();
        for (var castAxis : LiftUtils.NON_AXIS_AXES.get(axis)) {
            castDirs.add(LiftUtils.choose(castAxis,  1f));
            castDirs.add(LiftUtils.choose(castAxis, -1f));
        }

        for (var diagonal : Iterate.trueAndFalse) for (var dir : castDirs) {
            Vec3 start = dir.scale(SAMPLE_RADIUS);
            if (diagonal) start = VecHelper.rotate(start, 45, axis);
            Vec3 end = start.normalize().scale(size*1.1);
            rayPoints.add(Couple.create(start, end));
        }

        return rayPoints;
    }

    @SuppressWarnings("ConstantValue")
    public static boolean applyForcesFromRays(final ServerSubLevel parentSublevel, final BlockState state, final int size, final double timeStep, final Level level,
                                              final BlockPos blockStart, final WaterWheelBlockEntity be, final List<Couple<Vec3>> rayPoints) {
        if (rayPoints.isEmpty() || Mth.abs(be.getGeneratedSpeed()) > 0 || !be.hasSource()) return false;

        var speed = be.getSpeed()/2f*(1+(size-1)*2f);
        if (Math.abs(speed) < 0.0001) return true;
        var flag = false;
        final var axis = LiftUtils.getBlockAxis(state);
        final var helper = Sable.HELPER;
        final Vec3 center = Vec3.atCenterOf(blockStart);
        final float rotationDegrees = -(speed / 512f * 11.25f);

        for (var rayPoint : rayPoints) {
            final Vec3 s = rayPoint.getFirst();
            final Vec3 e = rayPoint.getSecond();
            final Vec3 localStart = center.add(s);
            final Vec3 localEnd = center.add(e);
            final Vec3 worldStart = helper.projectOutOfSubLevel(level, localStart);
            final Vec3 worldEnd = helper.projectOutOfSubLevel(level, localEnd);

            final ClipContext ctx = new ClipContext(worldStart, worldEnd, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, CollisionContext.empty());
            ((ClipContextExtension) ctx).sable$setIgnoredSubLevel(parentSublevel);

            var ray = level.clip(ctx);
            if (ray.getType() == HitResult.Type.MISS) continue;
            var rayPos = ray.getBlockPos();
            if (level.getFluidState(rayPos).isEmpty()) continue;

            final var hitSublevel = helper.getContaining(level, ray.getLocation());
            if (hitSublevel == parentSublevel) continue;

            final Vec3 rotatedPoint = VecHelper.rotate(s, rotationDegrees, axis);
            final Vec3 rotatedWorldPoint = helper.projectOutOfSubLevel(level, center.add(rotatedPoint));
            final Vec3 direction = rotatedWorldPoint.subtract(worldStart);
            if (direction.lengthSqr() < 1.0E-8) continue;

            final double magnitude = Math.abs(speed) * FORCE_PER_SPEED * timeStep;
            final var worldForce = new Vector3d(direction.x, direction.y, direction.z).normalize(magnitude);
            applyWorldPointForce(parentSublevel, new Vector3d(worldStart.x, worldStart.y, worldStart.z), worldForce);
            flag = true;
        }
        return flag;
    }

    private static void applyWorldPointForce(final ServerSubLevel subLevel, final Vector3d worldPoint, final Vector3d worldForce) {
        final var localPoint = new Vector3d(worldPoint);
        subLevel.logicalPose().transformPositionInverse(localPoint);

        final var localForce = new Vector3d(worldForce);
        subLevel.logicalPose().transformNormalInverse(localForce);
        subLevel.getOrCreateQueuedForceGroup(ForceGroups.PROPULSION.get()).applyAndRecordPointForce(localPoint, localForce);
    }
}

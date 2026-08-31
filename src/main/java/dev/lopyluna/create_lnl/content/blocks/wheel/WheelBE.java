package dev.lopyluna.create_lnl.content.blocks.wheel;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.lopyluna.create_lnl.content.utils.LiftUtils;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3i;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class WheelBE extends KineticBlockEntity implements BlockEntitySubLevelActor {

    private static final Collection<WheelBE> QUEUED_WHEELS = new HashSet<>();

    @Nullable protected SubLevel subLevel;
    protected Direction.Axis axis = Direction.Axis.Y;
    protected Vec3 center;
    @Nullable protected Vec3 outCenter;
    protected float currentSpeed;
    protected double lastAngle;
    protected double angle;
    protected double extension = 0.65;
    protected double angularVelocity = 0.0;
    protected double touchingFriction = 1.0;
    protected boolean liftedUp = true;
    protected List<Couple<Vec3>> stickyVisualCastPoints = Collections.emptyList();
    protected final Vec3[] stickyVisualEndPoints = new Vec3[8];
    protected final Vec3[] stickyVisualEndWorldPoints = new Vec3[8];
    protected final SubLevel[] stickyVisualEndSubLevels = new SubLevel[8];

    public float radius = 12f/16f;
    public boolean sticky = false;
    public WheelType type = WheelType.NORMAL;

    private final Vector3d queuedForcePos = new Vector3d();
    private final Vector3d queuedForce = new Vector3d();
    private final ForceTotal forceTotal = new ForceTotal();

    public List<Couple<Vec3>> castPoints;

    public boolean dropped = false;

    public WheelBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        update();
    }

    public static void applyAllBatchedForces(final double timeStep) {
        for (final WheelBE blockEntity : QUEUED_WHEELS) {
            if (blockEntity.isRemoved()) continue;
            blockEntity.applyBatchedForces();
        }
        QUEUED_WHEELS.clear();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) return;

        final var containing = Sable.HELPER.getContaining(this);
        if (containing != null && (this.subLevel == null || !this.subLevel.getUniqueId().equals(containing.getUniqueId()))) this.subLevel = containing;

        center = worldPosition.getCenter();
        outCenter = Sable.HELPER.projectOutOfSubLevel(level, center);
        currentSpeed = getSpeed();
        lastAngle = angle;

        if (!sticky) clearStickyVisuals();

        final float visualSpeed = -currentSpeed;
        final double attemptedAngularVelocity = Mth.lerp(0.2, angularVelocity, visualSpeed * Math.PI * 2.0 / 60.0 / 20.0);

        if (subLevel == null) {
            angularVelocity = attemptedAngularVelocity;
            angle += angularVelocity;
            updateStickyVisuals();
            return;
        }

        var contact = castPoints.stream()
                .map(this::computeRaycast)
                .filter(Objects::nonNull)
                .min(Comparator.comparingDouble(TerrainCastResult::maxExtension))
                .orElse(null);
        liftedUp = contact == null || contact.maxExtension() > MAX_ALLOWED_EXTENSION + getRadius() + CONTACT_EXTRA_RANGE;
        if (liftedUp) {
            angularVelocity = attemptedAngularVelocity;
            angle += angularVelocity;
            updateStickyVisuals();
            return;
        }

        touchingFriction = fudgeFriction(contact.friction);
        final Vector3d velocity = Sable.HELPER.getVelocity(level, new Vector3d(center.x, center.y, center.z));
        final Vector3d localVelocity = subLevel.logicalPose().transformNormalInverse(velocity).div(20.0);
        final var axisDir = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        final Vector3d sideD = new Vector3d(axisDir.getStepX(), axisDir.getStepY(), axisDir.getStepZ()).normalize();
        final Vector3d normal = new Vector3d(contact.hitNormal.x, contact.hitNormal.y, contact.hitNormal.z).normalize();
        final Vector3d normalD = new Vector3d(sideD).cross(normal);
        if (normalD.lengthSquared() <= 1.0E-8) {
            angularVelocity = attemptedAngularVelocity;
            angle += angularVelocity;
            updateStickyVisuals();
            return;
        }

        normalD.normalize();
        final double translation = localVelocity.dot(normalD);
        final double circumference = Math.PI * getRadius() * 2.0;
        double angularDelta = -translation / circumference * Math.PI * 2.0;
        if (touchingFriction < 1.0) angularDelta = Mth.lerp(touchingFriction, attemptedAngularVelocity, angularDelta);

        angularVelocity = angularDelta;
        angle += angularVelocity;
        updateStickyVisuals();
    }

    private void clearStickyVisuals() {
        Arrays.fill(stickyVisualEndPoints, null);
        Arrays.fill(stickyVisualEndWorldPoints, null);
        Arrays.fill(stickyVisualEndSubLevels, null);
    }

    private void updateStickyVisuals() {
        if (level == null || !level.isClientSide || !sticky || subLevel == null || stickyVisualCastPoints.isEmpty()) {
            clearStickyVisuals();
            return;
        }

        final double renderAngle = -angle;
        boolean removedAny = false;

        for (int i = 0; i < Math.min(stickyVisualCastPoints.size(), stickyVisualEndPoints.length); i++) {
            final Couple<Vec3> rotatedPoint = getStickyVisualCastPoint(i, renderAngle);
            final Vec3 localStart = center.add(rotatedPoint.getFirst());
            final Vec3 worldStart = Sable.HELPER.projectOutOfSubLevel(level, localStart);

            if (stickyVisualEndWorldPoints[i] != null && worldStart.distanceTo(toLocal(stickyVisualEndWorldPoints[i], stickyVisualEndSubLevels[i])) > STICKY_VISUAL_MAX_DISTANCE * (getRadius()/4) * (12 - (radius > 1f ? 6 : -2))) {
                stickyVisualEndPoints[i] = null;
                stickyVisualEndWorldPoints[i] = null;
                stickyVisualEndSubLevels[i] = null;
                removedAny = true;
                continue;
            }

            if (stickyVisualEndPoints[i] == null) {
                final TerrainCastResult hit = computeRaycast(rotatedPoint);
                if (hit != null) {
                    stickyVisualEndPoints[i] = hit.rawHit;
                    stickyVisualEndWorldPoints[i] = hit.worldHit;
                    stickyVisualEndSubLevels[i] = hit.hitSubLevel;
                }
            }
        }

        if (removedAny) {
            final Vec3 soundPos = outCenter != null ? outCenter : worldPosition.getCenter();
            level.playLocalSound(soundPos.x, soundPos.y, soundPos.z, SoundEvents.SLIME_SQUISH, SoundSource.BLOCKS, 0.01f + level.random.nextFloat() * 0.01f, 0.5f + level.random.nextFloat() * 0.2f, false);
        }
    }

    private Vec3 toLocal(Vec3 point, SubLevel pointSubLevel) {
        if (pointSubLevel instanceof ClientSubLevel pointClientSubLevel) point = pointClientSubLevel.logicalPose().transformPositionInverse(point);
        return point;
    }

    private static final double MAX_ALLOWED_EXTENSION = 0.65;
    private static final double CONTACT_EXTRA_RANGE = 0.25;
    private static final double CONTACT_NORMAL_DOT_THRESHOLD = 0.5;
    private static final double SUSPENSION_STRENGTH = 10.0;
    private static final double STICK_STRENGTH = 600.0;
    private static final double STICKY_SNAP_RANGE = 0.5;
    private static final float STICKY_VISUAL_START_INSET = 2f / 16f;
    private static final float STICKY_VISUAL_END_EXTRA = 0.25f;
    private static final double STICKY_VISUAL_MAX_DISTANCE = 2;

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (level == null || level.isClientSide || this.subLevel == null) return;

        final Vec3 localPos = worldPosition.getCenter();
        this.queuedForcePos.set(localPos.x, localPos.y, localPos.z);
        forceTotal.reset();

        var contact = castPoints.stream()
                .map(this::computeRaycast)
                .filter(Objects::nonNull)
                .min(Comparator.comparingDouble(TerrainCastResult::maxExtension))
                .orElse(null);
        if (contact == null) {
            this.extension = MAX_ALLOWED_EXTENSION;
            return;
        }

        final double suspensionRestDistance = MAX_ALLOWED_EXTENSION;
        final double maxExtension = contact.maxExtension();
        this.extension = Mth.lerp(1.0, this.extension, maxExtension);
        if (maxExtension > suspensionRestDistance + getRadius() + CONTACT_EXTRA_RANGE) {
            this.extension = suspensionRestDistance;
            return;
        }

        final var massData = subLevel.getMassTracker();
        final double normalMass = 1.0 / massData.getInverseNormalMass(this.queuedForcePos, OrientedBoundingBox3d.UP);
        final double effectiveStrength = SUSPENSION_STRENGTH;
        final double normalMassScaling = Math.min(normalMass / effectiveStrength, 1.0) * 10.0;
        final double strengthMul = effectiveStrength * normalMassScaling * 2.0;
        final double springStrength = effectiveStrength * normalMassScaling * 40.0;
        final double dampingStrength = effectiveStrength * normalMassScaling;

        final double distance = (suspensionRestDistance / 6.0) + this.extension;
        final double springLength = Mth.clamp(distance - getRadius(), 0.0, suspensionRestDistance);

        final Vector3d localVelocity = Sable.HELPER.getVelocity(level, new Vector3d(localPos.x, localPos.y, localPos.z));
        subLevel.logicalPose().transformNormalInverse(localVelocity);

        final Vector3d normal = new Vector3d(contact.hitNormal.x, contact.hitNormal.y, contact.hitNormal.z).normalize();
        final double normalVelocity = localVelocity.dot(normal);
        final double dampingForce = -normalVelocity * dampingStrength;
        final double springForce = ((suspensionRestDistance - springLength) * springStrength + dampingForce) * timeStep;

        queuedForce.set(normal).mul(springForce);

        touchingFriction = fudgeFriction(contact.friction);
        final double brakeStrength = level.getSignal(worldPosition.above(), Direction.DOWN) / 15.0;
        final double surfaceBraking = Math.min(touchingFriction, 1.0);
        final double brakingFrictionStrength = (0.075 + brakeStrength * 0.3) * surfaceBraking;

        final var axisDir = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        final Vector3d sideD = new Vector3d(axisDir.getStepX(), axisDir.getStepY(), axisDir.getStepZ()).normalize();
        final Vector3d normalD = new Vector3d(sideD).cross(normal);
        if (normalD.lengthSquared() > 1.0E-8) {
            normalD.normalize();
            final float kineticSpeed = getSpeed();
            queuedForce.fma((localVelocity.dot(normalD) * -brakingFrictionStrength * strengthMul * timeStep)
                    + (kineticSpeed * (1.0 - brakeStrength) * surfaceBraking * 1.75 * timeStep), normalD);
        }

        queuedForce.fma(localVelocity.dot(sideD) * -0.6 * touchingFriction * strengthMul * timeStep, sideD);
        if (sticky) {
            final double stickyTargetExtension = getRadius() + 8/16f;
            final double stickyError = maxExtension - stickyTargetExtension;
            if (stickyError <= STICKY_SNAP_RANGE) {
                var stickyMass = normalMass/4f;
                final double stickyImpulse = Mth.clamp(-stickyError * STICK_STRENGTH * stickyMass * timeStep, -STICK_STRENGTH * stickyMass * timeStep, STICK_STRENGTH * stickyMass * timeStep);
                queuedForce.fma(stickyImpulse, normal);
            }
        }

        forceTotal.applyImpulseAtPoint(subLevel, queuedForcePos, queuedForce);
        QUEUED_WHEELS.add(this);
    }

    private void applyBatchedForces() {
        final SubLevel subLevel = Sable.HELPER.getContaining(this);
        if (subLevel == null) return;

        final RigidBodyHandle handle = RigidBodyHandle.of((ServerSubLevel) subLevel);
        handle.applyForcesAndReset(this.forceTotal);
    }

    private static double fudgeFriction(final double realValue) {
        if (realValue < 1.0) return 0.1 + 0.9 * realValue;
        return realValue;
    }

    @Override
    protected void write(CompoundTag nbt, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(nbt, provider, clientPacket);
        nbt.putDouble("LastAngle", lastAngle);
        nbt.putDouble("Angle", angle);
        nbt.putFloat("Radius", radius);
        nbt.putBoolean("Sticky", sticky);
        nbt.putString("WheelType", type.name());
    }

    @Override
    protected void read(CompoundTag nbt, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(nbt, provider, clientPacket);
        lastAngle = nbt.getDouble("LastAngle");
        angle = nbt.getDouble("Angle");
        radius = nbt.getFloat("Radius");
        sticky = nbt.getBoolean("Sticky");
        try {
            type = WheelType.valueOf(nbt.getString("WheelType"));
        } catch (IllegalArgumentException ignored) {
            type = WheelType.NORMAL;
        }
        update();
    }

    public void update() {
        axis = getBlockState().getValue(WheelBlock.AXIS);
        center = worldPosition.getCenter();
        outCenter = Sable.HELPER.projectOutOfSubLevel(level, center);
        castPoints = genCastPoints(0f, (float) (getRadius() + MAX_ALLOWED_EXTENSION + CONTACT_EXTRA_RANGE));
        if (sticky) {
            final float stickyVisualStart = Math.max(radius * 1.75f - STICKY_VISUAL_START_INSET, 0f);
            stickyVisualCastPoints = genCastPoints(stickyVisualStart, stickyVisualStart + STICKY_VISUAL_END_EXTRA);
        }
        clearStickyVisuals();
    }

    protected Couple<Vec3> getStickyVisualCastPoint(int index, double renderAngle) {
        final Couple<Vec3> base = stickyVisualCastPoints.get(index);
        final float degrees = (float) Math.toDegrees(renderAngle);
        return Couple.create(VecHelper.rotate(base.getFirst(), degrees, axis), VecHelper.rotate(base.getSecond(), degrees, axis));
    }

    public float getRadius() {
        return (radius > 1f ? radius + radius * 0.25f : radius - radius * 0.25f);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(radius + 1);
    }

    @Override
    public void notifyUpdate() {
        super.notifyUpdate();
        update();
    }

    @Override
    public void setChanged() {
        super.setChanged();
        update();
    }


    private Vector3dc to3dc(final Vec3i normal) {
        return new Vector3d(normal.getX(), normal.getY(), normal.getZ());
    }

    public enum WheelType {
        MONSTROUS,
        LARGE,
        NORMAL,
        SMALL
        ;

        public float radius() {
            var ratio = (12f/16f)/(1f+6f/16f);
            return switch (this) {
                case MONSTROUS -> 2f;
                case LARGE -> 1+4f/16f;
                case NORMAL -> 15.5f/16f;
                case SMALL -> 12f/16f;
            } * ratio;
        }
    }

    public Vector3i chooseAxisInverse(int x, int y, int z, int r) {
        return LiftUtils.chooseInverse(axis, x, y, z, r);
    }
    public Vec3 chooseAxisInverse(double x, double y, double z, double r) {
        return LiftUtils.chooseInverse(axis, x, y, z, r);
    }
    public Vector3i chooseAxis(int x, int y, int z, int r) {
        return LiftUtils.choose(axis, x, y, z, r);
    }
    public Vec3 chooseAxis(double x, double y, double z, double r) {
        return LiftUtils.choose(axis, x, y, z, r);
    }
    public Vector3i chooseAxis(int a, int r) {
        return chooseAxis(a, a, a, r);
    }
    public Vec3 chooseAxis(double a, double r) {
        return chooseAxis(a, a, a, r);
    }
    public Set<Vector3i> chooseAxesInverseNonAxis(int x, int y, int z, int r) {
        return LiftUtils.NON_AXIS_AXES.get(axis).stream().map(a -> LiftUtils.chooseInverse(a, x, y, z, r)).collect(Collectors.toSet());
    }
    public Set<Vec3> chooseAxesInverseNonAxis(double x, double y, double z, double r) {
        return LiftUtils.NON_AXIS_AXES.get(axis).stream().map(a -> LiftUtils.chooseInverse(a, x, y, z, r)).collect(Collectors.toSet());
    }
    public Set<Vector3i> chooseAxesNonAxis(int x, int y, int z, int r) {
        return LiftUtils.NON_AXIS_AXES.get(axis).stream().map(a -> LiftUtils.choose(a, x, y, z, r)).collect(Collectors.toSet());
    }
    public Set<Vec3> chooseAxesNonAxis(double x, double y, double z, double r) {
        return LiftUtils.NON_AXIS_AXES.get(axis).stream().map(a -> LiftUtils.choose(a, x, y, z, r)).collect(Collectors.toSet());
    }

    private record TerrainCastResult(Vec3 localStart, Vec3 localHit, Vec3 rawHit, Vec3 worldHit, @Nullable SubLevel hitSubLevel, float friction, double maxExtension, Vec3 hitNormal) {}

    @Nullable
    private TerrainCastResult computeRaycast(Couple<Vec3> rayPoint) {
        if (level == null || subLevel == null) return null;
        final var helper = Sable.HELPER;
        var s = rayPoint.getFirst();
        var e = rayPoint.getSecond();

        var localStart = center.add(s);
        var localEnd = center.add(e);
        var ws = helper.projectOutOfSubLevel(level, localStart);
        var we = helper.projectOutOfSubLevel(level, localEnd);

        final ClipContext ctx = new ClipContext(ws, we, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty());
        ((ClipContextExtension) ctx).sable$setIgnoredSubLevel(subLevel);

        var ray = level.clip(ctx);
        if (ray.getType() == HitResult.Type.MISS) return null;

        final Vec3 rawHit = ray.getLocation();
        var hitSubLevel = helper.getContaining(level, rawHit);
        var hitPos = new Vector3d(rawHit.x, rawHit.y, rawHit.z);
        if (hitSubLevel != null) hitSubLevel.logicalPose().transformPosition(hitPos);
        final Vec3 worldHit = new Vec3(hitPos.x, hitPos.y, hitPos.z);
        subLevel.logicalPose().transformPositionInverse(hitPos);

        var hitNormal = new Vector3d(ray.getDirection().getStepX(), ray.getDirection().getStepY(), ray.getDirection().getStepZ());
        if (hitSubLevel != null) hitSubLevel.logicalPose().transformNormal(hitNormal);
        subLevel.logicalPose().transformNormalInverse(hitNormal);
        if (hitNormal.lengthSquared() <= 1.0E-8) return null;
        hitNormal.normalize();

        var localHit = new Vec3(hitPos.x, hitPos.y, hitPos.z);
        var localHitNormal = new Vec3(hitNormal.x, hitNormal.y, hitNormal.z);

        if (localStart.distanceTo(localHit) < 0.05) return null;

        var rayDir = localEnd.subtract(localStart);
        var rayLength = rayDir.length();
        if (rayLength <= 1.0E-8) return null;

        var centerToHit = localHit.subtract(center);
        var castDirection = rayDir.normalize();
        var maxExtension = centerToHit.dot(castDirection);
        if (maxExtension <= 1.0E-5) return null;

        if (localHitNormal.dot(castDirection.scale(-1)) < CONTACT_NORMAL_DOT_THRESHOLD) return null;

        var frict = (float) PhysicsBlockPropertyHelper.getFriction(level.getBlockState(ray.getBlockPos()));
        return new TerrainCastResult(localStart, localHit, rawHit, worldHit, hitSubLevel, frict, maxExtension, localHitNormal);
    }

    public List<Couple<Vec3>> genCastPoints(float startDist, float endDist) {
        startDist = Mth.abs(startDist);
        endDist = Math.max(Mth.abs(endDist), startDist);
        var rayPoints = new ArrayList<Couple<Vec3>>();

        var castDirs = new ArrayList<Vec3>();
        for (var castAxis : LiftUtils.NON_AXIS_AXES.get(axis)) {
            castDirs.add(LiftUtils.choose(castAxis,  1f));
            castDirs.add(LiftUtils.choose(castAxis, -1f));
        }

        for (var diagonal : Iterate.trueAndFalse) for (var dir : castDirs) {
            Vec3 start = dir.scale(startDist);
            Vec3 end = dir.scale(endDist);
            if (diagonal) {
                start = VecHelper.rotate(start, 45, axis);
                end = VecHelper.rotate(end, 45, axis).normalize().scale(endDist);
            }
            rayPoints.add(Couple.create(start, end));
        }
        return rayPoints;
    }
}

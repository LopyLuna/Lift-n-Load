package dev.lopyluna.create_lnl.content.blocks.wheel;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.lopyluna.create_lnl.content.utils.LiftUtils;
import dev.lopyluna.create_lnl.content.utils.TerrainMesher;
import dev.lopyluna.create_lnl.register.LiftsItems;
import dev.lopyluna.create_lnl.register.LiftsTags;
import dev.ryanhcode.offroad.Offroad;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

@SuppressWarnings("deprecation")
public class WheelBE extends KineticBlockEntity implements BlockEntitySubLevelActor {
    private static final Collection<WheelBE> QUEUED_WHEELS = new HashSet<>();
    private static final double MAX_EXTENSION = 0.65;
    private static final double CONTACT_RANGE = 0.25;

    @Nullable protected SubLevel subLevel;
    protected Direction.Axis axis = Direction.Axis.Y;
    protected Vec3 center;
    protected double lastAngle;
    protected double angle;
    protected double extension = MAX_EXTENSION;
    protected double angularVelocity;
    protected double touchingFriction = 1;
    protected List<Couple<Vec3>> stickyVisualCastPoints = Collections.emptyList();
    protected final Vec3[] stickyVisualEndPoints = new Vec3[8];
    protected final Vec3[] stickyVisualEndWorldPoints = new Vec3[8];
    protected final SubLevel[] stickyVisualEndSubLevels = new SubLevel[8];

    public float radius = 12f / 16f;
    public boolean sticky;
    public boolean dropped;
    public WheelType type = WheelType.NORMAL;

    private final Vector3d queuedForcePos = new Vector3d();
    private final Vector3d queuedForce = new Vector3d();
    private final ForceTotal forceTotal = new ForceTotal();

    private List<Couple<Vec3>> castPoints = Collections.emptyList();
    private Vector3d side = new Vector3d(0, 1, 0);
    private float scaledRadius;
    private double stickyBreakDistance;
    private Direction.Axis builtAxis;
    private float builtRadius = -1;
    private boolean builtSticky;

    @Nullable private TerrainCastResult contact;
    private long contactedAt = Long.MIN_VALUE;

    @Nullable private Predicate<SubLevel> linkedFilter;
    @Nullable private ServerSubLevel carrier;
    private long linkedAt = Long.MIN_VALUE;

    private final TerrainMesher mesher = new TerrainMesher(16, 2, 48, 2);
    private final Vector3d heldSlope = new Vector3d();
    private double heldShift;
    private long heldAt = Long.MIN_VALUE;

    public WheelBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        updateStates(state);
        update();
    }

    public static void applyAllBatchedForces() {
        for (var be : QUEUED_WHEELS) if (!be.isRemoved()) be.applyBatchedForces();
        QUEUED_WHEELS.clear();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) return;

        var containing = Sable.HELPER.getContaining(this);
        if (containing != null && (subLevel == null || !subLevel.getUniqueId().equals(containing.getUniqueId()))) subLevel = containing;

        lastAngle = angle;
        if (!sticky) clearStickyVisuals();

        angularVelocity = roll(Mth.lerp(0.2, angularVelocity, -getSpeed() * Math.PI * 2 / 60 / 20));
        angle += angularVelocity;
        updateStickyVisuals();
    }

    private double roll(double free) {
        if (level == null || subLevel == null) return free;
        var hit = level.getGameTime() == contactedAt ? contact : nearestContact();
        if (hit == null || hit.maxExtension() > castRange()) return free;

        var normalD = new Vector3d(side).cross(hit.hitNormal());
        if (normalD.lengthSquared() <= 1.0E-8) return free;
        normalD.normalize();

        touchingFriction = fudgeFriction(hit.friction());
        var rolling = -contactVelocity(hit).div(20).dot(normalD) / getRadius();
        return touchingFriction < 1 ? Mth.lerp(touchingFriction, free, rolling) : rolling;
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (level == null || level.isClientSide || this.subLevel == null) return;

        queuedForcePos.set(center.x, center.y, center.z);
        forceTotal.reset();

        contact = nearestContact();
        contactedAt = level.getGameTime();
        if (contact == null) {
            extension = MAX_EXTENSION;
            return;
        }

        var reach = contact.maxExtension();
        extension = reach;
        if (reach > castRange()) {
            extension = MAX_EXTENSION;
            return;
        }

        relink();
        var normalMass = 1 / subLevel.getMassTracker().getInverseNormalMass(queuedForcePos, OrientedBoundingBox3d.UP);
        if (carrier != null) {
            var borne = new Vector3d(queuedForcePos);
            subLevel.logicalPose().transformPosition(borne);
            carrier.logicalPose().transformPositionInverse(borne);
            normalMass = Math.max(normalMass, 1 / carrier.getMassTracker().getInverseNormalMass(borne, OrientedBoundingBox3d.UP));
        }
        var scaling = Math.min(normalMass / 10, 1) * 10;
        var strengthMul = 20 * scaling;
        var stiffness = 400 * scaling;
        var damping = Math.min(1.2 * Math.sqrt(stiffness * normalMass), normalMass / timeStep);
        var springLength = Mth.clamp((MAX_EXTENSION / 6) + extension - getRadius(), 0, MAX_EXTENSION);

        var localVelocity = contactVelocity(contact);

        var normal = new Vector3d(contact.hitNormal());
        queuedForce.zero();
        touchingFriction = fudgeFriction(contact.friction());
        if (reach <= MAX_EXTENSION + getRadius() + CONTACT_RANGE) {
            queuedForce.fma(((MAX_EXTENSION - springLength) * stiffness - localVelocity.dot(normal) * damping) * timeStep, normal);

            var brakeStrength = level.getSignal(worldPosition.above(), Direction.DOWN) / 15.0;
            var surfaceBraking = Math.min(touchingFriction, 1);
            var rolling = 0.075 + brakeStrength * 0.3;
            var traction = getSpeed() == 0 ? 1 : (rolling + 0.225) / rolling;
            var brakingFrictionStrength = rolling * traction * surfaceBraking;

            var normalD = new Vector3d(side).cross(normal);
            if (normalD.lengthSquared() > 1.0E-8) {
                normalD.normalize();
                queuedForce.fma((localVelocity.dot(normalD) * -brakingFrictionStrength * strengthMul * timeStep)
                        + (getSpeed() * (1 - brakeStrength) * surfaceBraking * traction * 1.75 * timeStep), normalD);
            }
            queuedForce.fma(localVelocity.dot(side) * -1.2 * touchingFriction * strengthMul * timeStep, side);
        }
        if (sticky) {
            var pull = DimensionPhysicsData.getGravity(level);
            subLevel.logicalPose().transformNormalInverse(pull);
            queuedForce.fma(-Math.max(0, 27.5 + pull.dot(normal)) * normalMass * timeStep, normal);
        }

        forceTotal.applyImpulseAtPoint(subLevel, queuedForcePos, queuedForce);
        QUEUED_WHEELS.add(this);
    }

    private void applyBatchedForces() {
        if (!(Sable.HELPER.getContaining(this) instanceof ServerSubLevel sub)) return;
        RigidBodyHandle.of(sub).applyForcesAndReset(forceTotal);
    }

    private static double fudgeFriction(double realValue) {
        return realValue < 1 ? 0.1 + 0.9 * realValue : realValue;
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

        var snapped = false;
        for (var i = 0; i < Math.min(stickyVisualCastPoints.size(), stickyVisualEndPoints.length); i++) {
            var rotatedPoint = getStickyVisualCastPoint(i, -angle);
            var worldStart = Sable.HELPER.projectOutOfSubLevel(level, center.add(rotatedPoint.getFirst()));

            if (stickyVisualEndWorldPoints[i] != null && worldStart.distanceTo(toLocal(stickyVisualEndWorldPoints[i], stickyVisualEndSubLevels[i])) > stickyBreakDistance) {
                stickyVisualEndPoints[i] = null;
                stickyVisualEndWorldPoints[i] = null;
                stickyVisualEndSubLevels[i] = null;
                snapped = true;
                continue;
            }

            if (stickyVisualEndPoints[i] != null) continue;
            var hit = computeRaycast(rotatedPoint);
            if (hit == null) continue;
            var end = hit.rawHit();
            var mesh = mesher.mesh();
            if (hit.smoothable() && hit.hitSubLevel() == null && mesh != null && mesh.covers(end.x, end.y, end.z, 2)) {
                var ray = hit.rayDir();
                var nudge = mesh.contact(end.x, end.y, end.z, ray.x, ray.y, ray.z, new Vector3d());
                if (!Double.isNaN(nudge)) end = end.add(ray.x * nudge, ray.y * nudge, ray.z * nudge);
            }
            stickyVisualEndPoints[i] = end;
            stickyVisualEndWorldPoints[i] = hit.hitSubLevel() == null ? end : hit.worldHit();
            stickyVisualEndSubLevels[i] = hit.hitSubLevel();
        }

        if (!snapped) return;
        var at = Sable.HELPER.projectOutOfSubLevel(level, center);
        level.playLocalSound(at.x, at.y, at.z, SoundEvents.SLIME_SQUISH, SoundSource.BLOCKS, 0.01f + level.random.nextFloat() * 0.01f, 0.5f + level.random.nextFloat() * 0.2f, false);
    }

    private Vec3 toLocal(Vec3 point, SubLevel pointSubLevel) {
        if (pointSubLevel instanceof ClientSubLevel client) point = client.logicalPose().transformPositionInverse(point);
        return point;
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


    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        updateStates(state);
    }

    public void updateStates(@Nullable BlockState state) {
        if (state == null) state = getBlockState();
        axis = state.getValue(WheelBlock.AXIS);
    }

    public void update() {
        center = worldPosition.getCenter();
        if (axis == builtAxis && radius == builtRadius && sticky == builtSticky) return;
        builtAxis = axis;
        builtRadius = radius;
        builtSticky = sticky;

        var dir = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        side = new Vector3d(dir.getStepX(), dir.getStepY(), dir.getStepZ());
        scaledRadius = radius > 1f ? radius + radius * 0.25f : radius - radius * 0.25f;
        stickyBreakDistance = 2 * (scaledRadius / 4) * (12 - (radius > 1f ? 6 : -2));
        castPoints = genCastPoints(0f, (float) castRange());
        if (sticky) {
            var start = Math.max(radius * 1.75f - 2f / 16f, 0f);
            stickyVisualCastPoints = genCastPoints(start, start + 0.25f);
        }
        clearStickyVisuals();
    }

    protected Couple<Vec3> getStickyVisualCastPoint(int index, double renderAngle) {
        var base = stickyVisualCastPoints.get(index);
        var degrees = (float) Math.toDegrees(renderAngle);
        return Couple.create(VecHelper.rotate(base.getFirst(), degrees, axis), VecHelper.rotate(base.getSecond(), degrees, axis));
    }

    private Vector3d contactVelocity(TerrainCastResult hit) {
        if (subLevel == null) return new Vector3d();
        var velocity = Sable.HELPER.getVelocity(level, new Vector3d(center.x, center.y, center.z));
        if (hit.hitSubLevel() != null) {
            var surface = new Vector3d();
            Sable.HELPER.getVelocity(level, hit.hitSubLevel(), new Vector3d(hit.rawHit().x, hit.rawHit().y, hit.rawHit().z), surface);
            velocity.sub(surface);
        }
        return subLevel.logicalPose().transformNormalInverse(velocity);
    }

    private void relink() {
        if (level == null || subLevel == null || linkedAt == level.getGameTime()) return;
        linkedAt = level.getGameTime();
        var ids = new HashSet<UUID>();
        ServerSubLevel heaviest = null;
        var most = 0d;
        for (var member : LiftUtils.getConnectedGroup(subLevel)) {
            ids.add(member.getUniqueId());
            if (!(member instanceof ServerSubLevel server)) continue;
            var mass = server.getMassTracker().getMass();
            if (mass <= most) continue;
            most = mass;
            heaviest = server;
        }
        carrier = heaviest == subLevel ? null : heaviest;
        linkedFilter = ids.size() > 1 ? sub -> ids.contains(sub.getUniqueId()) : null;
    }

    @Nullable
    private Predicate<SubLevel> linked() {
        relink();
        return linkedFilter;
    }

    private double castRange() {
        return MAX_EXTENSION + getRadius() + CONTACT_RANGE + (sticky ? 1 : 0);
    }

    public float getRadius() {
        return scaledRadius;
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

        public ItemStack item(boolean sticky) {
            if (sticky) return switch (this) {
                case MONSTROUS -> LiftsItems.SLIME_MONSTROUS_TIRE.asStack();
                case LARGE -> LiftsItems.SLIME_LARGE_TIRE.asStack();
                case NORMAL -> LiftsItems.SLIME_TIRE.asStack();
                case SMALL -> LiftsItems.SLIME_SMALL_TIRE.asStack();
            };
            return BuiltInRegistries.ITEM.get(Offroad.path(switch (this) {
                case MONSTROUS -> "monstrous_tire";
                case LARGE -> "large_tire";
                case NORMAL -> "tire";
                case SMALL -> "small_tire";
            })).getDefaultInstance();
        }
    }

    private record TerrainCastResult(Vec3 rawHit, Vec3 worldHit, @Nullable SubLevel hitSubLevel, float friction, double maxExtension, Vector3d hitNormal, boolean smoothable, Vector3d rayDir) {}

    @Nullable
    private TerrainCastResult nearestContact() {
        TerrainCastResult best = null;
        for (var point : castPoints) {
            var hit = computeRaycast(point);
            if (hit != null && (best == null || hit.maxExtension() < best.maxExtension())) best = hit;
        }
        return best == null || !best.smoothable() ? best : smooth(best);
    }

    private TerrainCastResult smooth(TerrainCastResult hit) {
        if (level == null || subLevel == null) return hit;
        var at = hit.rawHit();
        mesher.follow(level, at);
        var mesh = mesher.mesh();
        var dir = new Vector3d(hit.rayDir());
        if (hit.hitSubLevel() != null) hit.hitSubLevel().logicalPose().transformNormalInverse(dir);
        var slope = new Vector3d();
        var shift = mesh == null || !mesh.covers(at.x, at.y, at.z, 2)
                ? Double.NaN
                : mesh.contact(at.x, at.y, at.z, dir.x, dir.y, dir.z, slope);
        if (Double.isNaN(shift)) {
            if (heldAt == Long.MIN_VALUE) return hit;
            shift = heldShift;
            slope.set(heldSlope);
        } else {
            heldShift = shift;
            heldSlope.set(slope);
            heldAt = level.getGameTime();
        }
        var spot = new Vector3d(at.x + dir.x * shift, at.y + dir.y * shift, at.z + dir.z * shift);
        if (hit.hitSubLevel() != null) {
            hit.hitSubLevel().logicalPose().transformPosition(spot);
            hit.hitSubLevel().logicalPose().transformNormal(slope);
        }
        subLevel.logicalPose().transformPositionInverse(spot);
        subLevel.logicalPose().transformNormalInverse(slope);
        var reach = Math.max(1.0E-5, (center.x - spot.x) * slope.x + (center.y - spot.y) * slope.y + (center.z - spot.z) * slope.z);
        return new TerrainCastResult(at, hit.worldHit(), hit.hitSubLevel(), hit.friction(), reach, slope, true, hit.rayDir());
    }

    @Nullable
    private TerrainCastResult computeRaycast(Couple<Vec3> rayPoint) {
        if (level == null || subLevel == null) return null;
        var helper = Sable.HELPER;
        var localStart = center.add(rayPoint.getFirst());
        var localEnd = center.add(rayPoint.getSecond());

        var ws = helper.projectOutOfSubLevel(level, localStart);
        var we = helper.projectOutOfSubLevel(level, localEnd);
        var ctx = new ClipContext(ws, we, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty());
        var ext = (ClipContextExtension) ctx;
        ext.sable$setIgnoredSubLevel(subLevel);
        ext.sable$setSubLevelIgnoring(linked());

        var ray = level.clip(ctx);
        if (ray.getType() == HitResult.Type.MISS) return null;

        var rawHit = ray.getLocation();
        var hitSubLevel = helper.getContaining(level, rawHit);
        var hitPos = new Vector3d(rawHit.x, rawHit.y, rawHit.z);
        if (hitSubLevel != null) hitSubLevel.logicalPose().transformPosition(hitPos);
        var worldHit = new Vec3(hitPos.x, hitPos.y, hitPos.z);
        subLevel.logicalPose().transformPositionInverse(hitPos);

        var hitNormal = new Vector3d(ray.getDirection().getStepX(), ray.getDirection().getStepY(), ray.getDirection().getStepZ());
        if (hitSubLevel != null) hitSubLevel.logicalPose().transformNormal(hitNormal);
        subLevel.logicalPose().transformNormalInverse(hitNormal);
        if (hitNormal.lengthSquared() <= 1.0E-8) return null;
        hitNormal.normalize();

        var localHit = new Vec3(hitPos.x, hitPos.y, hitPos.z);
        if (localStart.distanceToSqr(localHit) < 0.0025) return null;

        var rayDir = localEnd.subtract(localStart);
        if (rayDir.lengthSqr() <= 1.0E-16) return null;

        var castDirection = rayDir.normalize();
        var lean = -(hitNormal.x * castDirection.x + hitNormal.y * castDirection.y + hitNormal.z * castDirection.z);
        if (lean < 0.5) return null;
        var maxExtension = localHit.subtract(center).dot(castDirection) * lean;
        if (maxExtension <= 1.0E-5) return null;

        var state = level.getBlockState(ray.getBlockPos());
        return new TerrainCastResult(rawHit, worldHit, hitSubLevel, (float) PhysicsBlockPropertyHelper.getFriction(state), maxExtension, hitNormal, state.is(LiftsTags.SMOOTH_TERRAIN),
                new Vector3d(we.x - ws.x, we.y - ws.y, we.z - ws.z).normalize());
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

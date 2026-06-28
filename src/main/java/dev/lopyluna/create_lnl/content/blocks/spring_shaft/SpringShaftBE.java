package dev.lopyluna.create_lnl.content.blocks.spring_shaft;

import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.spring.SpringBlock;
import dev.simulated_team.simulated.content.items.spring.SpringItemHandler;
import dev.simulated_team.simulated.util.SimLevelUtil;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class SpringShaftBE extends KineticBlockEntity implements BlockEntitySubLevelActor {
    //grrr
    private static final Vector3d frictionForce = new Vector3d();
    private static final Vector3d frictionTorque = new Vector3d();
    private static final Vector3d localLinearVelocity = new Vector3d();
    private static final Vector3d localAngularVelocity = new Vector3d();
    private static final Vector3d expectedVelocity = new Vector3d();
    private static final Vector3d localDampingPointForce = new Vector3d();

    private static final double TIME_TO_SNAP = 0.9;

    public SpringBlock.Size size;
    public Direction facing;
    public Vec3 center;
    public Vec3 outCenter;
    public Vector3d centerFacing;

    protected BlockPos partnerPos;
    @Nullable protected UUID partnerSubLevel;

    protected float ticksWithoutPartner = 0;
    protected float snappingTime;

    protected LerpedFloat renderLength = LerpedFloat.linear();
    protected float desiredLength;
    protected boolean isController;
    protected boolean assembling;

    private ForceTotal forceTotal;
    private ForceTotal partnerForceTotal;

    public SpringShaftBE(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        update(state);
        renderLength.chase(0, 0.2, LerpedFloat.Chaser.EXP);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) return;

        if (level.isClientSide) {
            renderLength.updateChaseTarget(desiredLength);
            renderLength.tickChaser();
            return;
        }

        if (snappingTime > TIME_TO_SNAP) level.destroyBlock(worldPosition, true);

        if (partnerPos != null && SimLevelUtil.isAreaActuallyLoaded(level, partnerPos, 1)) {
            var pairedBE = getPairedBE();
            if (pairedBE == null && ticksWithoutPartner++ > 20) level.destroyBlock(worldPosition, true);
            else ticksWithoutPartner = 0;
        }
        if (partnerPos == null) level.destroyBlock(worldPosition, true);
    }

    @Override
    public List<BlockPos> addPropagationLocations(IRotate block, BlockState state, List<BlockPos> neighbours) {
        if (partnerPos != null) neighbours.add(partnerPos);
        return super.addPropagationLocations(block, state, neighbours);
    }

    @Override
    public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo, BlockPos diff, boolean connectedViaAxes, boolean connectedViaCogs) {
        if (level != null && target instanceof SpringShaftBE && (partnerPos != null && !level.isLoaded(partnerPos) || target.getBlockPos().equals(partnerPos))) return 1;
        return super.propagateRotationTo(target, stateFrom, stateTo, diff, connectedViaAxes, connectedViaCogs);
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        var partner = getPairedBE();
        if (partner == null || ticksWithoutPartner != 0 || !SimLevelUtil.isAreaActuallyLoaded(level, partnerPos, 1)) return;
        var container = SubLevelContainer.getContainer(subLevel.getLevel());
        if (container == null) return;
        var system = container.physicsSystem();
        system.updatePose(subLevel);
        var otherSubLevel = getPairedSubLevel(container);
        if ((partnerSubLevel != null && otherSubLevel == null) || (otherSubLevel != null && !isController) || otherSubLevel == subLevel) return;
        if (otherSubLevel != null) system.updatePose(otherSubLevel);

        var center = new Vector3d(centerFacing);
        var partnerCenter = new Vector3d(partner.centerFacing);

        var velo1 = Sable.HELPER.getVelocity(level, centerFacing, new Vector3d());
        var velo2 = Sable.HELPER.getVelocity(level, partner.centerFacing, new Vector3d());

        var positionA = subLevel.logicalPose().transformPosition(center, new Vector3d());
        var positionB = otherSubLevel != null ? otherSubLevel.logicalPose().transformPosition(JOMLConversion.atCenterOf(partnerPos)) : JOMLConversion.atCenterOf(partnerPos);

        var relativeVelo = velo1.sub(velo2);
        var dampingPointForce = new Vector3d(relativeVelo);
        dampingPointForce.mul(-4.5);

        var desiredLength = (isController ? this.desiredLength : partner.desiredLength) - 0.75;

        if (positionA.distanceSquared(positionB) > Mth.square(getSnappingDistance())) snappingTime += (float) timeStep;
        else snappingTime = 0;

        var globalNormalA = JOMLConversion.atLowerCornerOf(facing.getNormal());
        var globalNormalB = JOMLConversion.atLowerCornerOf(partner.facing.getNormal());

        subLevel.logicalPose().transformNormal(globalNormalA);
        if (otherSubLevel != null) otherSubLevel.logicalPose().transformNormal(globalNormalB);

        var torque = globalNormalA.cross(globalNormalB.negate(), new Vector3d()).mul(20f).mul(timeStep);
        var mediumNormal = globalNormalA.lerp(globalNormalB, 0.5f);

        var middle = new Vector3d(positionA.x, positionA.y, positionA.z).lerp(positionB, 0.5f);
        var desireA = middle.fma(-desiredLength / 2f, mediumNormal, new Vector3d());
        var alignmentForce = desireA.sub(positionA.x, positionA.y, positionA.z);
        var hooksPointForce = alignmentForce.mul(145f);

        var angVelo1 = new Vector3d();
        var angVelo2 = new Vector3d();

        handle.getAngularVelocity(angVelo1);
        if (otherSubLevel != null) RigidBodyHandle.of(otherSubLevel).getAngularVelocity(angVelo2);

        var relativeAngVelo = angVelo1.sub(angVelo2);
        var dampingTorque = new Vector3d();

        if (mediumNormal.lengthSquared() > 0f) {
            mediumNormal.normalize();
            dampingTorque.fma(-2f, relativeAngVelo.set(mediumNormal).mul(mediumNormal.dot(relativeAngVelo)));
        }

        var sizeScale = switch (size) {
            case LARGE -> 8f;
            case MEDIUM -> 1f;
            case SMALL -> 0.5f;
        };

        hooksPointForce.mul(sizeScale);
        torque.mul(sizeScale);
        dampingTorque.mul(sizeScale);
        dampingPointForce.mul(sizeScale);

        if (forceTotal == null || partnerForceTotal == null) {
            forceTotal = new ForceTotal();
            partnerForceTotal = new ForceTotal();
        }

        applyLocalDamping(subLevel, handle, forceTotal, center, dampingPointForce, dampingTorque, timeStep);
        forceTotal.applyImpulseAtPoint(subLevel, center, subLevel.logicalPose().transformNormalInverse(new Vector3d(hooksPointForce)).mul(timeStep));
        forceTotal.applyLinearAndAngularImpulse(JOMLConversion.ZERO, subLevel.logicalPose().transformNormalInverse(torque, new Vector3d()));
        handle.applyForcesAndReset(forceTotal);

        if (otherSubLevel != null) {
            var partnerHandle = RigidBodyHandle.of(otherSubLevel);
            applyLocalDamping(otherSubLevel, partnerHandle, partnerForceTotal, partnerCenter, dampingPointForce.negate(), dampingTorque.negate(), timeStep);
            partnerForceTotal.applyImpulseAtPoint(otherSubLevel, partnerCenter, otherSubLevel.logicalPose().transformNormalInverse(hooksPointForce).mul(-timeStep));
            partnerForceTotal.applyLinearAndAngularImpulse(JOMLConversion.ZERO, otherSubLevel.logicalPose().transformNormalInverse(torque.negate()));
            partnerHandle.applyForcesAndReset(partnerForceTotal);
        }
    }

    @Override
    public @Nullable Iterable<SubLevel> sable$getConnectionDependencies() {
        if (partnerSubLevel != null) {
            var container = SubLevelContainer.getContainer(level);
            if (container == null) return List.of();
            var subLevel = container.getSubLevel(partnerSubLevel);
            if (subLevel != null) return List.of(subLevel);
        }
        return List.of();
    }

    public void setPartner(BlockPos pos, UUID subLevel) {
        partnerPos = pos;
        partnerSubLevel = subLevel;
        sendData();
    }

    public float getSnappingDistance() {
        return desiredLength * 4f + 2f;
    }

    public @Nullable SpringShaftBE getPairedBE() {
        if (level == null || partnerPos == null) return null;
        if (level.getBlockEntity(partnerPos) instanceof SpringShaftBE be) return be;
        return null;
    }
    public @Nullable ServerSubLevel getPairedSubLevel(@Nullable ServerSubLevelContainer container) {
        if (level == null || partnerSubLevel == null || container == null) return null;
        if (container.getSubLevel(partnerSubLevel) instanceof ServerSubLevel subLevel) return subLevel;
        return null;
    }

    @Override
    public void remove() {
        if (level != null && !level.isClientSide && partnerPos != null && !assembling) {
            var be = getPairedBE();
            if (be != null) {
                detachKinetics();
                be.notifyUpdate();
            }
            level.destroyBlock(partnerPos, false);
        }
        partnerPos = null;
        super.remove();
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        invalidateRenderBoundingBox();
    }

    @Override
    public AABB getRenderBoundingBox() {
        var goal = getPairedBE();
        if (goal == null) return new AABB(worldPosition);

        var center = worldPosition.getCenter();
        var other = partnerPos.getCenter();

        var subLevel = Sable.HELPER.getContaining(this);
        var partnerSubLevel = Sable.HELPER.getContaining(level, partnerPos);

        if (partnerSubLevel != null) other = partnerSubLevel.logicalPose().transformPosition(other);
        if (subLevel != null) other = subLevel.logicalPose().transformPositionInverse(other);

        return new AABB(center, other).inflate(3);
    }

    public void update(@Nullable BlockState state) {
        if (state == null) state = getBlockState();
        facing = state.getValue(SpringShaftBlock.FACING);
        size = state.getValue(SpringShaftBlock.SIZE);
        center = worldPosition.getCenter();
        outCenter = Sable.HELPER.projectOutOfSubLevel(level, center);
        centerFacing = getCenter();
    }

    public Vector3d getCenter() {
        var normal = facing.getNormal();
        var scale = 0.5f - 4 / 16f;
        return JOMLConversion.atCenterOf(worldPosition).sub(normal.getX() * scale, normal.getY() * scale, normal.getZ() * scale);
    }

    @Override
    protected void read(CompoundTag nbt, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(nbt, provider, clientPacket);
        isController = nbt.getBoolean("Controller");
        desiredLength = nbt.getFloat("DesiredLength");

        if (renderLength.getValue() == 0) {
            renderLength.updateChaseTarget(desiredLength);
            renderLength.setValue(desiredLength);
            renderLength.setValue(desiredLength);
        }

        var schematicContext = SubLevelSchematicSerializationContext.getCurrentContext();
        var isPlacingFromSchematic = schematicContext != null && schematicContext.getType() == SubLevelSchematicSerializationContext.Type.PLACE;
        SubLevelSchematicSerializationContext.SchematicMapping mapping = null;

        if (nbt.hasUUID("GoalSubLevel")) {
            var subLevelID = nbt.getUUID("GoalSubLevel");

            if (isPlacingFromSchematic) {
                mapping = schematicContext.getMapping(subLevelID);

                subLevelID = mapping != null ? mapping.newUUID() : null;
                if (mapping == null) partnerPos = null;
                if (mapping == null) return;
            }
            partnerSubLevel = subLevelID;
        }

        if (!nbt.contains("Goal")) return;
        var blockPos = BlockPos.of(nbt.getLong("Goal"));

        if (isPlacingFromSchematic) blockPos = mapping != null ? mapping.transform().apply(blockPos) : schematicContext.getPlaceTransform().apply(blockPos);
        partnerPos = blockPos;
    }

    @Override
    protected void write(CompoundTag nbt, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(nbt, provider, clientPacket);
        nbt.putBoolean("Controller", isController);
        nbt.putFloat("DesiredLength", desiredLength);

        if (partnerPos == null) return;

        var schemaCtx = SubLevelSchematicSerializationContext.getCurrentContext();
        if (schemaCtx == null || schemaCtx.getType() == SubLevelSchematicSerializationContext.Type.PLACE) {
            if (partnerSubLevel != null) nbt.putUUID("GoalSubLevel", partnerSubLevel);

            var pos = partnerPos;
            if (schemaCtx != null && partnerSubLevel == null) pos = schemaCtx.getSetupTransform().apply(pos);
            nbt.putLong("Goal", pos.asLong());
            return;
        }
        var pos = partnerPos;
        var id = partnerSubLevel;

        if (id != null) {
            var mapping = schemaCtx.getMapping(id);
            id = mapping != null ? mapping.newUUID() : null;
            partnerPos = mapping != null ? mapping.transform().apply(partnerPos) : null;
        } else partnerPos = schemaCtx.getBoundingBox().contains(partnerPos.getX(), partnerPos.getY(), partnerPos.getZ()) ? schemaCtx.getPlaceTransform().apply(partnerPos) : null;

        if (pos != null) nbt.putLong("Goal", pos.asLong());
        if (id != null) nbt.putUUID("GoalSubLevel", id);
    }

    public @Nullable String tryChangeLengthOrError(Level level, float delta) {
        if (delta > 0 && desiredLength >= SpringItemHandler.MAX_LENGTH) return "max_length";
        if (delta < 0 && desiredLength <= 1) return "min_length";

        var newDesiredLength = Math.round(Mth.clamp(desiredLength + delta, 1, (float) SpringItemHandler.MAX_LENGTH) / 0.25f) * 0.25f;
        var currentLength = Sable.HELPER.distanceSquaredWithSubLevels(level, center, partnerPos.getCenter()) + 1;
        if (delta < 0 && currentLength > newDesiredLength * newDesiredLength * 4) return "too_stretched";
        if (delta > 0 && currentLength < newDesiredLength * newDesiredLength / 4) return "too_compressed";

        this.desiredLength = newDesiredLength;
        this.setChanged();
        this.sendData();
        if (level.getBlockEntity(partnerPos) instanceof final SpringShaftBE be) {
            be.desiredLength = desiredLength;
            be.setChanged();
            be.sendData();
        }
        return null;
    }

    //Love a way to use this without the need of copy+pasting it
    private void applyLocalDamping(final ServerSubLevel subLevel, final RigidBodyHandle handle, final ForceTotal forceTotal,
                                   final Vector3dc worldSpringPos, final Vector3dc dampingPointForce, final Vector3dc dampingTorque, final double timeStep) {
        var pose = subLevel.logicalPose();

        handle.getAngularVelocity(localAngularVelocity);
        handle.getLinearVelocity(localLinearVelocity);

        pose.orientation().transformInverse(localAngularVelocity);
        pose.orientation().transformInverse(localLinearVelocity);

        var centerOfMass = subLevel.getMassTracker().getCenterOfMass();
        pose.orientation().transformInverse(dampingPointForce, localDampingPointForce);

        var angularDamping = new Vector3d();
        angularDamping.add(dampingTorque);
        pose.orientation().transformInverse(angularDamping);
        angularDamping.add(worldSpringPos.sub(centerOfMass, new Vector3d()).cross(localDampingPointForce));

        var linearDamping = new Vector3d();
        linearDamping.add(localDampingPointForce);

        frictionForce.set(linearDamping);
        frictionTorque.set(angularDamping);

        expectedVelocity.set(frictionForce);
        expectedVelocity.mul(subLevel.getMassTracker().getInverseMass());
        expectedVelocity.mul(timeStep);
        var forceScale = getClampingFactor(localLinearVelocity);

        expectedVelocity.set(frictionTorque);
        subLevel.getMassTracker().getInverseInertiaTensor().transform(expectedVelocity);
        expectedVelocity.mul(timeStep);
        var torqueScale = getClampingFactor(localAngularVelocity);

        frictionForce.mul(forceScale * timeStep);
        frictionTorque.mul(torqueScale * timeStep);

        forceTotal.applyLinearAndAngularImpulse(frictionForce, frictionTorque);
    }

    private double getClampingFactor(final Vector3dc currentVelocity) {
        var k = -currentVelocity.dot(expectedVelocity);
        var v = currentVelocity.lengthSquared();
        if (k < 0) return 0;
        if (10 * k < v) return 1;
        if (v < 1E-10) return v / (k + 1E-10);
        return v * (1 - Math.exp(-k / v)) / k;
    }
}

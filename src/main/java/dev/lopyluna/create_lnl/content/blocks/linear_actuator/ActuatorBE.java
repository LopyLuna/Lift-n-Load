package dev.lopyluna.create_lnl.content.blocks.linear_actuator;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.IDisplayAssemblyExceptions;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.lopyluna.create_lnl.content.blocks.PhysicHoldingBEs;
import dev.lopyluna.create_lnl.events.CommonEvents;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.function.BiPredicate;

@SuppressWarnings({"deprecation"})
public class ActuatorBE extends KineticBlockEntity implements IDisplayAssemblyExceptions, PhysicHoldingBEs, BlockEntitySubLevelActor {
    private static final MutableComponent SCROLL_OPTION_TITLE = Component.translatable(Simulated.MOD_ID + ".scroll_option.swivel_default_locked");

    public boolean assembleNextTick;
    protected AssemblyException lastException;

    @Nullable private UUID subLevelID;
    @Nullable private BlockPos anchorPos;
    @Nullable private BlockPos basePos;
    private boolean retracting;
    private boolean assembling;
    private int retryTicks;
    @Nullable private PhysicsConstraintHandle handle;
    private int lastLock = 1;

    private float length = 1;
    private float prevLength = 1;
    private float anchored = -1;

    private final Vector3d goal = new Vector3d();
    private final Vector3d target = new Vector3d();
    private final Vector3d anchor = new Vector3d();
    private final Quaterniond orientation = new Quaterniond();

    private ScrollOptionBehaviour<SwivelBearingBlockEntity.LockingSetting> lockedDefaultOption;

    private Direction facing;
    private Direction.Axis facingAxis;
    private ConstraintJointAxis slide;
    private int step;
    private boolean head;
    private boolean assembled;
    private boolean powered;

    public ActuatorBE(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        updateStates(state);
        assembleNextTick = false;
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide && !head) CommonEvents.addPhysicHolder(this);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        release();
        CommonEvents.removePhysicHolder(this);
    }

    public void beforeAssembly() {
        assembling = true;
    }

    public void setBasePos(BlockPos pos) {
        basePos = pos;
        setChanged();
    }

    public void fixLinkingWhenMoved() {
        if (level == null || level.isClientSide) return;
        if (!head) {
            var other = partnerBE();
            if (other == null) return;
            other.setBasePos(worldPosition);
            relink(other);
            return;
        }
        var sub = Sable.HELPER.getContaining(this);
        if (sub == null || basePos == null) return;
        if (!(level.getBlockEntity(basePos) instanceof ActuatorBE base) || base.head || !base.assembled) return;
        if (Sable.HELPER.getContaining(base) == sub) return;
        base.release();
        base.anchorPos = worldPosition;
        base.readAnchor();
        if (!sub.getUniqueId().equals(base.subLevelID)) base.subLevelID = sub.getUniqueId();
        base.notifyUpdate();
        base.relink(this);
    }

    @Override
    public void remove() {
        if (level != null && !level.isClientSide && !assembling) destroyPartner();
        super.remove();
    }

    private void destroyPartner() {
        if (level == null) return;
        var at = partnerPos();
        if (!isActuator(at)) at = ActuatorBlock.partner(level, worldPosition, getBlockState());
        if (!isActuator(at)) return;
        if (at == null) return;
        if (level.getBlockEntity(at) instanceof ActuatorBE be) be.beforeAssembly();
        level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
    }

    private boolean isActuator(@Nullable BlockPos at) {
        return level != null && at != null && level.getBlockState(at).getBlock() instanceof ActuatorBlock;
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        release();
        CommonEvents.removePhysicHolder(this);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || head) return;
        if (assembleNextTick) {
            assembleNextTick = false;
            if (!assembled) assemble();
            else if (retracting) retracting = false;
            else {
                retracting = true;
                retryTicks = 100;
            }
            return;
        }

        var other = partnerBE();
        syncLock(other);
        if (assembled && other != null && !worldPosition.equals(other.basePos)) other.setBasePos(worldPosition);
        var locked = getLockSetting().shouldLock(level.getBestNeighborSignal(worldPosition));
        prevLength = length;
        if (assembled) {
            if (retracting) {
                length = Math.max(1, length - .25f);
                if (attached() != null && Math.abs(measure() - 1) < .2f) {
                    retracting = false;
                    disassemble();
                    return;
                }
                if (--retryTicks <= 0) retracting = false;
            } else if (!locked) length = Mth.clamp(measure(), 1, ActuatorBlock.MAX_LENGTH);
            else {
                var speed = getSpeed() != 0 || other == null ? getSpeed() : other.getSpeed();
                var travel = Mth.clamp(speed / 512f, -.49f, .49f);
                if (travel != 0) length = Mth.clamp(length + travel, 1, ActuatorBlock.MAX_LENGTH);
            }
            if (length != prevLength) setChanged();
        }
        var hold = locked || retracting;
        if (hold == powered) return;
        level.setBlockAndUpdate(worldPosition, getBlockState().setValue(ActuatorBlock.POWERED, hold));
    }

    public void assemble() {
        if (!(level instanceof ServerLevel server) || head) return;
        var headPos = ActuatorBlock.partner(server, worldPosition, getBlockState());
        if (headPos == null) {
            lastException = AssemblyException.notEnoughSails(0);
            sendData();
            return;
        }

        var headBE = server.getBlockEntity(headPos) instanceof ActuatorBE h ? h : null;
        if (headBE != null) {
            headBE.setBasePos(worldPosition);
            headBE.beforeAssembly();
        }

        SimAssemblyHelper.AssemblyResult result;
        try {
            result = SimAssemblyHelper.assembleFromSingleBlock(server, worldPosition, headPos, false, true);
            lastException = null;
        } catch (AssemblyException e) {
            lastException = e;
            if (headBE != null) headBE.assembling = false;
            sendData();
            return;
        }
        if (result == null) {
            if (headBE != null) headBE.assembling = false;
            return;
        }

        subLevelID = result.subLevel().getUniqueId();
        anchorPos = headPos.offset(result.offset());
        if (result.subLevel().getLevel().getBlockEntity(anchorPos) instanceof ActuatorBE h) {
            h.basePos = worldPosition;
            h.setChanged();
            relink(h);
        }
        length = (float) Math.sqrt(headPos.distSqr(worldPosition));
        prevLength = length;
        readAnchor();
        server.setBlockAndUpdate(worldPosition, getBlockState().setValue(ActuatorBlock.ASSEMBLED, true));
        notifyUpdate();
    }

    public void disassemble() {
        if (level == null || head || !assembled || subLevelID == null) return;
        retracting = false;
        length = 1;
        prevLength = 1;
        release();
        var container = SubLevelContainer.getContainer(level);
        var sub = container == null ? null : container.getSubLevel(subLevelID);
        if (sub != null && anchorPos != null && sub != Sable.HELPER.getContaining(this)) {
            if (sub.getLevel().getBlockEntity(anchorPos) instanceof ActuatorBE h) h.beforeAssembly();
            SimAssemblyHelper.disassembleSubLevel(level, sub, anchorPos, worldPosition.relative(facing), Rotation.NONE, true);
        }

        subLevelID = null;
        anchorPos = null;
        level.setBlockAndUpdate(worldPosition, getBlockState().setValue(ActuatorBlock.ASSEMBLED, false));
        relink(level.getBlockEntity(worldPosition.relative(facing)) instanceof ActuatorBE h ? h : null);
        notifyUpdate();
    }

    @Override
    public void physicsTick(SubLevelPhysicsSystem physicsSystem) {
        if (level == null || head || !assembled || subLevelID == null) return;
        if (!(SubLevelContainer.getContainer(level) instanceof ServerSubLevelContainer container)) return;
        if (!(container.getSubLevel(subLevelID) instanceof ServerSubLevel sub) || sub.isRemoved()) return;
        if (physicsSystem.getLevel() != sub.getLevel()) return;
        var pipeline = physicsSystem.getPipeline();
        if (pipeline == null) return;
        var body = (ServerSubLevel) Sable.HELPER.getContaining(this);
        var normal = facing.getNormal();

        if (!powered) {
            if (handle == null || !handle.isValid() || anchored >= 0) {
                release();
                anchored = -1;
                target.set(goal.x + normal.getX(), goal.y + normal.getY(), goal.z + normal.getZ());
                var axes = EnumSet.allOf(ConstraintJointAxis.class);
                axes.remove(slide);
                var free = pipeline.addConstraint(body, sub,
                        new GenericConstraintConfiguration(target, anchor, orientation, orientation, axes));
                handle = free;
                if (free != null) {
                    var span = (ActuatorBlock.MAX_LENGTH - 1) * step;
                    free.setLimit(slide, Math.min(0, span), Math.max(0, span));
                    free.setContactsEnabled(false);
                }
            }
            correctDrift();
            return;
        }

        var reach = Mth.lerp((float) physicsSystem.getPartialPhysicsTick(), prevLength, length);
        if (handle == null || !handle.isValid() || reach != anchored) {
            release();
            target.set(goal.x + normal.getX() * reach, goal.y + normal.getY() * reach, goal.z + normal.getZ() * reach);
            handle = pipeline.addConstraint(body, sub, new FixedConstraintConfiguration(target, anchor, orientation));
            anchored = reach;
            if (handle != null) handle.setContactsEnabled(false);
        }
        if (length != prevLength) pipeline.wakeUp(sub);
        correctDrift();
    }

    public void correctDrift() {
        if (level == null || head || !assembled || subLevelID == null) return;
        if (!(SubLevelContainer.getContainer(level) instanceof ServerSubLevelContainer container)) return;
        if (!(container.getSubLevel(subLevelID) instanceof ServerSubLevel sub) || sub.isRemoved()) return;
        var body = RigidBodyHandle.of(sub);
        if (!body.isValid()) return;
        correctDrift(sub, body);
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle body, double timeStep) {
        if (!head || level == null || basePos == null) return;
        if (!(level.getBlockEntity(basePos) instanceof ActuatorBE base) || base.head) return;
        base.correctDrift(subLevel, body);
    }

    public void correctDrift(ServerSubLevel sub, RigidBodyHandle body) {
        if (level == null || head || !assembled || anchorPos == null) return;
        var self = Sable.HELPER.getContaining(this);
        var pose = sub.logicalPose();
        var at = pose.transformPosition(new Vector3d(anchor));
        if (self != null) self.logicalPose().transformPositionInverse(at, at);

        var axis = new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        at.sub(goal);
        var reach = at.dot(axis);
        var over = Mth.clamp(reach, 1, ActuatorBlock.MAX_LENGTH) - reach;
        var fix = axis.mul(reach + over, new Vector3d()).sub(at);
        if (fix.lengthSquared() < 1e-4) return;

        if (self != null) self.logicalPose().transformNormal(fix);
        body.teleport(pose.position().add(fix, new Vector3d()),
                self == null ? new Quaterniond() : self.logicalPose().orientation());

        var linear = body.getLinearVelocity(new Vector3d());
        var speed = linear.dot(axis);
        if (over * speed < 0) speed = 0;
        body.addLinearAndAngularVelocity(axis.mul(speed, new Vector3d()).sub(linear),
                body.getAngularVelocity(new Vector3d()).negate());
    }

    private float measure() {
        return measure(false);
    }

    private float measure(boolean render) {
        if (level == null || subLevelID == null || anchorPos == null) return length;
        var container = SubLevelContainer.getContainer(level);
        var sub = container == null ? null : container.getSubLevel(subLevelID);
        if (sub == null) return length;
        var at = poseOf(sub, render).transformPosition(new Vector3d(anchor));
        var self = Sable.HELPER.getContaining(this);
        if (self != null) poseOf(self, render).transformPositionInverse(at, at);
        var normal = facing.getNormal();
        return (float) ((at.x - goal.x) * normal.getX() + (at.y - goal.y) * normal.getY() + (at.z - goal.z) * normal.getZ());
    }

    private static Pose3dc poseOf(SubLevel sub, boolean render) {
        return render && sub instanceof ClientSubLevel client ? client.renderPose() : sub.logicalPose();
    }

    public float renderLength(float partialTicks) {
        if (head || subLevelID == null) return getLength(partialTicks);
        return Mth.clamp(measure(true), 1, ActuatorBlock.MAX_LENGTH);
    }

    @Override
    public AABB getRenderBoundingBox() {
        if (head) return super.getRenderBoundingBox();
        var reach = renderLength(1);
        return super.getRenderBoundingBox()
                .expandTowards(facing.getStepX() * reach, facing.getStepY() * reach, facing.getStepZ() * reach)
                .inflate(.5);
    }

    private void relink(@Nullable ActuatorBE other) {
        detachKinetics();
        updateSpeed = true;
        if (other == null) return;
        other.detachKinetics();
        other.updateSpeed = true;
    }

    private void release() {
        if (handle == null) return;
        if (handle.isValid()) handle.remove();
        handle = null;
    }

    private void readAnchor() {
        if (anchorPos == null) return;
        anchor.set(anchorPos.getX() + 0.5, anchorPos.getY() + 0.5, anchorPos.getZ() + 0.5);
    }

    @Override
    public AssemblyException getLastAssemblyException() {
        return lastException;
    }

    @Override
    protected boolean syncSequenceContext() {
        return true;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        lockedDefaultOption = new ScrollOptionBehaviour<>(SwivelBearingBlockEntity.LockingSetting.class, SCROLL_OPTION_TITLE, this, new ActuatorSlotTransform((state, d) -> {
            var axis = d.getAxis();
            return facingAxis != axis && state.getBlock() instanceof IRotate rot && rot.getRotationAxis(state) != axis;
        }));
        lockedDefaultOption.value = 1;
        behaviours.add(lockedDefaultOption);
    }

    private void syncLock(@Nullable ActuatorBE other) {
        if (other == null) return;
        if (lockedDefaultOption.value != lastLock) other.lockedDefaultOption.setValue(lockedDefaultOption.value);
        else if (other.lockedDefaultOption.value != lastLock) lockedDefaultOption.setValue(other.lockedDefaultOption.value);
        lastLock = lockedDefaultOption.value;
    }

    @Nullable
    private BlockPos partnerPos() {
        if (level == null) return null;
        if (head) return basePos != null ? basePos : ActuatorBlock.partner(level, worldPosition, getBlockState());
        if (assembled) return anchorPos;
        return ActuatorBlock.partner(level, worldPosition, getBlockState());
    }

    @Nullable
    private ActuatorBE partnerBE() {
        var at = partnerPos();
        if (at == null || level == null) return null;
        var target = level;
        if (!head && assembled && subLevelID != null) {
            var container = SubLevelContainer.getContainer(level);
            var sub = container == null ? null : container.getSubLevel(subLevelID);
            if (sub == null) return null;
            target = sub.getLevel();
        }
        return target.getBlockEntity(at) instanceof ActuatorBE be ? be : null;
    }

    @Override
    public List<BlockPos> addPropagationLocations(IRotate block, BlockState state, List<BlockPos> neighbours) {
        var at = partnerPos();
        if (at != null) neighbours.add(at);
        return super.addPropagationLocations(block, state, neighbours);
    }

    @Override
    public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo, BlockPos diff, boolean connectedViaAxes, boolean connectedViaCogs) {
        if (target instanceof ActuatorBE && target.getBlockPos().equals(partnerPos())) return 1;
        return super.propagateRotationTo(target, stateFrom, stateTo, diff, connectedViaAxes, connectedViaCogs);
    }

    @Override
    public boolean isCustomConnection(KineticBlockEntity other, BlockState state, BlockState otherState) {
        return other instanceof ActuatorBE && other.getBlockPos().equals(partnerPos());
    }

    @Nullable
    private SubLevel attached() {
        if (level == null || subLevelID == null) return null;
        var container = SubLevelContainer.getContainer(level);
        return container == null ? null : container.getSubLevel(subLevelID);
    }

    @Override
    public @Nullable Iterable<SubLevel> sable$getConnectionDependencies() {
        var sub = attached();
        return sub == null ? null : List.of(sub);
    }

    protected SwivelBearingBlockEntity.LockingSetting getLockSetting() {
        return lockedDefaultOption.get();
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putFloat("Length", length);
        if (subLevelID != null) compound.putUUID("SubLevelID", subLevelID);
        if (anchorPos != null) compound.putLong("Anchor", anchorPos.asLong());
        if (basePos != null) compound.putLong("Base", basePos.asLong());
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        length = compound.contains("Length") ? compound.getFloat("Length") : 1;
        prevLength = length;
        subLevelID = compound.hasUUID("SubLevelID") ? compound.getUUID("SubLevelID") : null;
        anchorPos = compound.contains("Anchor") ? BlockPos.of(compound.getLong("Anchor")) : null;
        basePos = compound.contains("Base") ? BlockPos.of(compound.getLong("Base")) : null;
        readAnchor();
    }

    public float getLength() {
        return length;
    }

    public float getLength(float partialTicks) {
        return Mth.lerp(partialTicks, prevLength, length);
    }

    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        updateStates(state);
    }

    public void updateStates(@Nullable BlockState state) {
        if (state == null) state = getBlockState();
        facing = state.getValue(DirectionalKineticBlock.FACING);
        facingAxis = facing.getAxis();
        step = facing.getAxisDirection().getStep();
        slide = switch (facingAxis) {
            case X -> ConstraintJointAxis.LINEAR_X;
            case Y -> ConstraintJointAxis.LINEAR_Y;
            case Z -> ConstraintJointAxis.LINEAR_Z;
        };
        head = state.getValue(ActuatorBlock.HEAD);
        assembled = state.getValue(ActuatorBlock.ASSEMBLED);
        powered = state.getValue(ActuatorBlock.POWERED);
        var center = worldPosition.getCenter();
        goal.set(center.x, center.y, center.z);
    }

    public static class ActuatorSlotTransform extends CenteredSideValueBoxTransform {
        public ActuatorSlotTransform(BiPredicate<BlockState, Direction> allowedDirections) {
            super(allowedDirections);
        }

        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            return super.getLocalOffset(level, pos, state).add(Vec3.atLowerCornerOf(facing(state).getNormal()).scale(-2 / 16f));
        }

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            if (!getSide().getAxis().isHorizontal()) TransformStack.of(ms).rotateYDegrees(AngleHelper.horizontalAngle(facing(state)) + 180);
            super.rotate(level, pos, state, ms);
        }

        private static Direction facing(BlockState state) {
            var facing = state.getValue(ActuatorBlock.FACING);
            return state.getValue(ActuatorBlock.HEAD) ? facing.getOpposite() : facing;
        }
    }

}

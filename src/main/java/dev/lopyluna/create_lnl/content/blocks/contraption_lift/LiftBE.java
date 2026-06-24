package dev.lopyluna.create_lnl.content.blocks.contraption_lift;

import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.lopyluna.create_lnl.content.blocks.PhysicHoldingBEs;
import dev.lopyluna.create_lnl.events.CommonEvents;
import dev.lopyluna.create_lnl.register.LiftsBlocks;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.config.server.physics.SimPhysics;
import dev.simulated_team.simulated.index.SimSoundEvents;
import dev.simulated_team.simulated.service.SimConfigService;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LiftBE extends SmartBlockEntity implements PhysicHoldingBEs { //implements IDisplayAssemblyExceptions, SimMagnet, BlockEntitySubLevelActor
    public List<Integer> deltaList = new ArrayList<>();

    public float deltaRot = 0;
    public int deltaMov = 0;
    public float angle = 0;
    public int target = 0;
    public float oldHeight;
    public LerpedFloat lerpAngle = LerpedFloat.linear().chase(0, 0.2, LerpedFloat.Chaser.EXP);
    public LerpedFloat height = LerpedFloat.linear().chase(0, 0.15678, LerpedFloat.Chaser.EXP);
    public LerpedFloat cHeight = LerpedFloat.linear().chase(0, 0.15678, LerpedFloat.Chaser.EXP);

    public boolean initializing = true;

    public UUID subUUID;
    public Vec3 pivot = worldPosition.getCenter().add(0, 4/16f, 0);
    public Vec3 oldPivot = pivot;
    protected AssemblyException lastException;
    private BindingSession session = null;

    public UUID userUUID = null;
    public Player user;

    public boolean sound = true;

    //PLACING ANIMATION
    public boolean placing = true;
    public boolean placed = false;
    public float progress = -1/16f;
    public LerpedFloat angleFlap = LerpedFloat.angular().chase(89, 0.3, LerpedFloat.Chaser.EXP);

    //STRUCTURE
    public int structIndex;
    private int lastStructCheckIndex = -1;

    public LiftBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        angleFlap.startWithValue(89);
        structIndex = state.getValue(LiftBlock.LIFT).structure() ? 1 : 0;
        CommonEvents.addPhysicHolder(this);
        bind();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public void blockAboveUpdate(BlockState state, BlockPos pos) {
        if (level == null || level.isClientSide) return;
        if (session != null || subUUID != null) return;
        if (state.isAir()) return;

        final SimAssemblyHelper.AssemblyResult result = this.assembleBlockAbove(level, pos);
        if (result == null) return;

        this.subUUID = result.subLevel().getUniqueId();
        this.setChanged();
        this.sendData();
        this.bind();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) return;
        bind();
        if (session != null) {
            session.tick();
            if (session.markedForRemoval) clearSubLevelBinding();
        }
        if (structIndex > 0) {
            if (level.getGameTime() % 8 == 0 && !(level.getBlockState(worldPosition.below()).getBlock() instanceof LiftBlock)) LiftBlock.remove(level, worldPosition, false);
            if (!(level.getBlockEntity(worldPosition.below(structIndex)) instanceof LiftBE lift)) return;
            height = lift.height;
            cHeight = lift.cHeight;
            return;
        }
        if (user == null || userUUID == null) if (level.getGameTime() % 4 == 0) user = getUser();
        if (userUUID == null && !initializing) level.removeBlock(worldPosition, false);

        if (placing) {
            progress += progress >=0 ? Math.max(Math.max(progress*2, 0.6f) * (1f - progress) * progress, 1/8f) : 1/20f;

            if (level.isClientSide && progress >=0) angleFlap.updateChaseTarget(Mth.clamp((1f - progress) * 90, 0, 90));
            if (progress > 1) placing = false;
        } else if (!placed) {
            var state = getBlockState();
            var soundType = state.getSoundType(level, worldPosition, null);
            level.playSound(null, worldPosition, soundType.getHitSound(), SoundSource.BLOCKS, (soundType.getVolume() + 1.0F) / 8.0F, soundType.getPitch() * 0.5F);
            placed = true;
            return;
        }

        var pos = worldPosition;
        var center = pos.getCenter();

        if (placing) return;

        if (level.isClientSide) {
            var oldClientHeight = cHeight.getValue();
            cHeight.tickChaser();
            carryEntities(oldClientHeight, cHeight.getValue(), true);
            return;
        }

        var player = getUser();
        if (player == null) return;

        check = ++check % 4;
        check(pos, player);
        if (!(level instanceof ServerLevel)) return;
        move(player);
        if (subUUID != null) {
            rotate(player);
            angle += deltaRot;
            lerpAngle.updateChaseTarget(angle);
            lerpAngle.tickChaser();

            var val = lerpAngle.getChaseTarget();
            if (val > 360) val = -360;
            else if (val < 0) val = 360;
            else val = 0;
            if (val != 0) lerpAngle.updateChaseTarget(lerpAngle.getChaseTarget() + val);
        }
        oldHeight = height.getValue();
        oldPivot = new Vec3(center.x, pos.getY()+(12f/16f) + oldHeight + (1/16f), center.z);
        var abovePos = new BlockPos(worldPosition.getX(), Mth.floor(pivot.y + 1/16f), worldPosition.getZ());
        var aboveState = level.getBlockState(abovePos);
        if (!aboveState.isAir() && !aboveState.canBeReplaced() && !(aboveState.getBlock() instanceof LiftBlock) && deltaMov >= 0) {
            target = Mth.floor(oldHeight*16f);
            height.updateChaseTarget(oldHeight);
            if (subUUID != null) notifyUpdate();
            return;
        }

        target += deltaMov;
        if (!deltaList.isEmpty()) {
            for (var d : deltaList) target += d;
            deltaList.clear();
        }
        target = Mth.clamp(target, 0, 16*8);

        height.updateChaseTarget(target/16f);
        height.tickChaser();
        notifyUpdate();

        var height = this.height.getValue();
        structCheck(height);
        carryEntities(oldHeight, height, false);

        var ticks = level.getGameTime();
        if (ticks % 2 != 0) return;

        if ((sound && deltaMov != 0) || (deltaMov != 0 && (ticks % 3 == 0 || (ticks % 5 == 0 && level.random.nextBoolean())))) {
            sound = false;
            level.playSound(null, worldPosition, SimSoundEvents.DOCKING_CONNECTOR_EXTENDS.event(), SoundSource.BLOCKS, 0.15f + (level.random.nextFloat() * 0.25f), 0.55F + (level.random.nextFloat() * 0.15f) + ((height / 8f) * 0.5f));
            level.playSound(null, pivot.x, pivot.y, pivot.z, SimSoundEvents.DOCKING_CONNECTOR_EXTENDS.event(), SoundSource.BLOCKS, 0.05f + (level.random.nextFloat() * 0.05f), 0.5F + (level.random.nextFloat() * 0.1f) + ((height / 8f)));
        }
        if (deltaMov == 0) sound = true;
    }

    private void carryEntities(float previousHeight, float currentHeight, boolean clientPrediction) {
        if (level == null) return;

        var pos = worldPosition;
        var center = pos.getCenter();
        Player liftUser = clientPrediction ? getUser() : null;
        pivot = new Vec3(center.x, pos.getY()+(12f/16f) + currentHeight + (1/16f), center.z);
        var moveY = currentHeight - previousHeight;
        if (Math.abs(moveY) < 1.0E-5) return;

        var minX = pos.getX() + 2 / 16f;
        var minZ = pos.getZ() + 2 / 16f;
        var maxX = pos.getX() + 14 / 16f;
        var maxZ = pos.getZ() + 14 / 16f;
        var oldTopY = pos.getY() + (12f/16f) + previousHeight;
        var topY = pos.getY() + (12f/16f) + currentHeight;
        var minY = Math.min(oldTopY, topY) - 0.35;
        var maxY = Math.max(oldTopY, topY) + 1.0;
        var box = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        var rideBand = new AABB(minX, oldTopY - 0.3, minZ, maxX, oldTopY + 0.45, maxZ);
        var sweptRideBand = new AABB(minX, Math.min(oldTopY, topY) - 0.3, minZ, maxX, Math.max(oldTopY, topY) + 0.45, maxZ);

        for (var e : level.getEntities(null, box)) {
            if (Sable.HELPER.getContaining(e) != null || Sable.HELPER.getTrackingSubLevel(e) != null) continue;
            if (clientPrediction && e != liftUser) continue;


            var eBox = e.getBoundingBox();
            if (!eBox.intersects(rideBand) && !(moveY > 0 && eBox.intersects(sweptRideBand))) continue;

            e.move(MoverType.SHULKER_BOX, new Vec3(0, moveY, 0));

            var minEntityY = e.getBoundingBox().minY;
            if (moveY > 0 && minEntityY < topY - 1.0E-3) {
                var correction = topY - minEntityY + 1.0E-3;
                e.setPos(e.getX(), e.getY() + correction, e.getZ());
            }

            e.fallDistance = 0;
            e.setOnGround(true);
        }
    }

    public void structCheck(float height) {
        if (level == null) return;
        var h = ((height + 12/16f) * 100f) / 100f;
        var index = Mth.floor(h-0.001f);
        if (index == lastStructCheckIndex) return;
        lastStructCheckIndex = index;
        //if (getUser() instanceof Player player) player.displayClientMessage(Component.literal("i"+index+" h"+(int)((height + 12/16f) * 16)), true);
        for (int i = 1; i <= 8; i++) {
            var pos = worldPosition.above(i);
            boolean shouldExist = i <= index;
            if (shouldExist) {
                var state = level.getBlockState(pos);
                if (state.isAir() || state.canBeReplaced()) {
                    level.setBlockAndUpdate(pos, LiftsBlocks.CONTRAPTION_LIFT.getDefaultState().setValue(LiftBlock.LIFT, LiftBlock.LiftState.STRUCTURE));
                    if (level.getBlockEntity(pos) instanceof LiftBE be) be.structIndex = i;
                } else if (level.getBlockState(pos).getBlock() instanceof LiftBlock block && !block.canSurvive(state, level, pos)) level.removeBlock(pos, true);
            } else if (level.getBlockState(pos).getBlock() instanceof LiftBlock) level.removeBlock(pos, true);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        if (structIndex > 0) return super.getRenderBoundingBox();
        return super.getRenderBoundingBox().expandTowards(0, height.getValue(), 0).inflate(0.5);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Index", structIndex);
        tag.putFloat("OldHeight", oldHeight);
        tag.putFloat("Height", height.getValue());
        tag.putFloat("Chase", height.getChaseTarget());
        tag.putFloat("AngleHeight", lerpAngle.getValue());
        tag.putFloat("AngleChase", lerpAngle.getChaseTarget());
        tag.putFloat("Angle", angle);
        tag.putInt("Target", target);
        tag.putBoolean("Placed", placed);
        if (subUUID != null) tag.putUUID("SubLevelID", subUUID);
        if (userUUID != null) tag.putUUID("UserUUID", userUUID);
        AssemblyException.write(tag, registries, this.lastException);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        structIndex = tag.getInt("Index");
        oldHeight = tag.getFloat("OldHeight");
        height.setValue(tag.getFloat("Height"));
        height.updateChaseTarget(tag.getFloat("Chase"));
        lerpAngle.setValue(tag.getFloat("AngleHeight"));
        lerpAngle.updateChaseTarget(tag.getFloat("AngleChase"));
        if (clientPacket) cHeight.updateChaseTarget(height.getValue());
        angle = tag.getFloat("Angle");
        target = tag.getInt("Target");
        placed = tag.getBoolean("Placed");
        if (tag.contains("SubLevelID")) subUUID = tag.getUUID("SubLevelID");
        if (tag.contains("UserUUID")) {
            userUUID = tag.getUUID("UserUUID");
            user = getUser();
        }
        this.lastException = AssemblyException.read(tag, registries);
        CommonEvents.addPhysicHolder(this);
        bind();
    }

    @Override
    public void initialize() {
        super.initialize();
        initializing = false;
    }

    @Override
    public void physicsTick(final SubLevelPhysicsSystem physicsSystem) {
        if (session == null || level != physicsSystem.getLevel()) return;
        session.physicsTick(physicsSystem);
    }

    public void bind() {
        if (subUUID == null || session != null) return;
        if (!(level instanceof final ServerLevel server)) return;
        if (!(SubLevelContainer.getContainer(server) instanceof final ServerSubLevelContainer container)) return;
        if (!(container.getSubLevel(subUUID) instanceof final ServerSubLevel serverSubLevel)) return;
        session = new BindingSession(this, serverSubLevel);
        session.pivotRelativeGoal.zero();
        session.recenterAroundPivot(container.physicsSystem().getPipeline(), 0, true);
        CommonEvents.addPhysicHolder(this);
    }

    public void unbind() {
        if (session != null) {
            session.remove();
            CommonEvents.removePhysicHolder(this);
            session = null;
        }
    }

    private void clearSubLevelBinding() {
        unbind();
        if (subUUID != null) {
            subUUID = null;
            setChanged();
            sendData();
        }
    }

    public @Nullable SimAssemblyHelper.AssemblyResult assembleBlockAbove(Level level, BlockPos pos) {
        try {
            final SimAssemblyHelper.AssemblyResult result = SimAssemblyHelper.assembleFromSingleBlock(level, pos.below(), pos, true, true);
            this.lastException = null;
            this.sendData();
            return result;
        } catch (final AssemblyException e) {
            this.assemblyFailed(e);
            return null;
        }
    }

    private void assemblyFailed(final AssemblyException exception) {
        this.lastException = exception;
        this.sendData();
    }

    public static Vec3 getLiftPivot(BlockPos pos) {
        return pos.getCenter().add(0, 4 / 16f, 0);
    }

    public static Vector3d getBottomCenter(BoundingBox3ic bb) {
        return new Vector3d((bb.minX() + bb.maxX() + 1) / 2.0, bb.minY(), (bb.minZ() + bb.maxZ() + 1) / 2.0);
    }
    public static Vector3d getSupportedBottomCenter(SubLevel subLevel) {
        var bb = subLevel.getPlot().getBoundingBox();
        var target = getBottomCenter(bb);
        var level = subLevel.getLevel();
        var pos = new BlockPos.MutableBlockPos();
        Vector3d best = null;
        var bestDistance = Double.POSITIVE_INFINITY;
        var foundSupport = false;

        for (int x = bb.minX(); x <= bb.maxX(); x++) for (int z = bb.minZ(); z <= bb.maxZ(); z++) {
            pos.set(x, bb.minY(), z);
            var state = level.getBlockState(pos);
            if (state.isAir() || state.getCollisionShape(level, pos).isEmpty()) continue;

            foundSupport = true;
            var candidateX = Mth.clamp(target.x, x, x + 1.0);
            var candidateZ = Mth.clamp(target.z, z, z + 1.0);
            var candidateDistance = Mth.square(candidateX - target.x) + Mth.square(candidateZ - target.z);
            if (candidateDistance >= bestDistance) continue;

            bestDistance = candidateDistance;
            best = new Vector3d(candidateX, bb.minY(), candidateZ);
        }

        if (!foundSupport) return target;

        return best;
    }

    public int check;
    public void check(BlockPos pos, Player player) {
        if (check != 0 || level == null || initializing) return;
        if (!pos.equals(NBTHelper.readBlockPos(player.getPersistentData(), "LiftPos"))) level.removeBlock(worldPosition, false);
    }

    public void move(@Nullable Player player) {
        var mov = getMovDelta(player);
        if (target >= 16*8 && mov > 0) mov = 0;
        if (0 >= target && 0 > mov) mov = 0;
        deltaMov = mov;
    }
    public void rotate(@Nullable Player player) {
        deltaRot = getRotDelta(player) * 5.625f;
    }

    public int getMovDelta(@Nullable Player player) {
        if (player != null) return (int) LiftBE.getMovDelta(LiftBE.getOrCreateLiftNbt(player)) * 2;
        return target > 0 ? -2 : 0;
    }
    public int getRotDelta(@Nullable Player player) {
        if (player != null) return (int) LiftBE.getRotDelta(LiftBE.getOrCreateLiftNbt(player)) * 2;
        return 0;
    }
    public Player getUser() {
        if (user != null) return user;
        if (level == null || userUUID == null) return null;
        if (level.getPlayerByUUID(userUUID) instanceof Player player) user = player;
        return user;
    }

    @Override
    public void remove() {
        super.remove();
        CommonEvents.removePhysicHolder(this);
        unbind();
    }

    @Override
    public void destroy() {
        super.destroy();
        CommonEvents.removePhysicHolder(this);
        unbind();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        CommonEvents.removePhysicHolder(this);
        unbind();
    }

    public static double getMovDelta(CompoundTag nbt) {
        return nbt.contains("MovDelta") ? nbt.getDouble("MovDelta") : 0;
    }
    public static double getRotDelta(CompoundTag nbt) {
        return nbt.contains("RotDelta") ? nbt.getDouble("RotDelta") : 0;
    }
    public static void setRotDelta(CompoundTag nbt, double movDelta) {
        nbt.putDouble("RotDelta", movDelta);
    }
    public static void setMovDelta(CompoundTag nbt, double rotDelta) {
        nbt.putDouble("MovDelta", rotDelta);
    }
    public static void saveLiftNbt(Player player, CompoundTag nbt) {
        player.getPersistentData().put("Lift", nbt);
    }
    public static CompoundTag getOrCreateLiftNbt(Player player) {
        var nbt = player.getPersistentData();
        if (!nbt.contains("Lift")) nbt.put("Lift", new CompoundTag());
        return nbt.getCompound("Lift");
    }

    private static class BindingSession {
        private final Vector3d plotAnchor = new Vector3d();
        private final Vector3d pivotRelativeGoal = new Vector3d();
        private final Vector3d localGoal = new Vector3d();
        private final Quaterniond orientation = new Quaterniond();

        private final LiftBE be;
        private final ServerSubLevel subLevel;
        private boolean markedForRemoval = false;
        private PhysicsConstraintHandle constraint = null;

        private BindingSession(final LiftBE be, final ServerSubLevel subLevel) {
            this.be = be;
            this.subLevel = subLevel;
        }

        private void tick() {
            if (this.subLevel.isRemoved()) this.markedForRemoval = true;
        }

        private void physicsTick(final SubLevelPhysicsSystem physicsSystem) {
            if (this.subLevel.isRemoved() || physicsSystem.getLevel() != this.subLevel.getLevel()) return;
            final PhysicsPipeline pipeline = physicsSystem.getPipeline();
            if (pipeline == null) return;

            final double partialTick = physicsSystem.getPartialPhysicsTick();
            double angleRad = Math.toRadians(Mth.wrapDegrees(be.lerpAngle.getValue((float) partialTick)) + 180);
            final boolean anchorChanged = this.recenterAroundPivot(pipeline, angleRad, false);
            if (anchorChanged && this.constraint != null) {
                this.constraint.remove();
                this.constraint = null;
            }
            if (this.constraint == null) this.attachConstraint(physicsSystem);

            if (this.be.deltaMov != 0 || this.be.deltaRot != 0) pipeline.wakeUp(this.subLevel);

            final SimPhysics config = SimConfigService.INSTANCE.server().physics;
            if (this.constraint != null) {
                final float angularStiffness = config.physicsStaffAngularStiffness.getF();
                final float angularDamping = config.physicsStaffAngularDamping.getF();
                final float linearStiffness = config.physicsStaffLinearStiffness.getF();
                final float linearDamping = config.physicsStaffLinearDamping.getF();

                for (final ConstraintJointAxis angularAxis : ConstraintJointAxis.ANGULAR)
                    this.constraint.setMotor(angularAxis, 0.0, angularStiffness, angularDamping, false, 0.0);

                final double pivotX = Mth.lerp(partialTick, be.oldPivot.x, be.pivot.x);
                final double pivotY = Mth.lerp(partialTick, be.oldPivot.y, be.pivot.y);
                final double pivotZ = Mth.lerp(partialTick, be.oldPivot.z, be.pivot.z);

                this.localGoal.set(this.pivotRelativeGoal).add(pivotX, pivotY, pivotZ);
                this.orientation.transformInverse(this.localGoal);

                this.constraint.setMotor(ConstraintJointAxis.ANGULAR_Y, angleRad, angularStiffness, angularDamping, false, 0.0);
                this.constraint.setMotor(ConstraintJointAxis.LINEAR_X, this.localGoal.x(), linearStiffness, linearDamping, false, 0.0);
                this.constraint.setMotor(ConstraintJointAxis.LINEAR_Y, this.localGoal.y(), linearStiffness, linearDamping, false, 0.0);
                this.constraint.setMotor(ConstraintJointAxis.LINEAR_Z, this.localGoal.z(), linearStiffness, linearDamping, false, 0.0);
            }
        }

        private boolean recenterAroundPivot(@Nullable final PhysicsPipeline pipeline, final double angle, final boolean resetOrientation) {
            final Vector3d bottomCenter = LiftBE.getSupportedBottomCenter(this.subLevel);
            final var pose = this.subLevel.logicalPose();

            Vector3d desiredPos = JOMLConversion.toJOML(this.be.pivot).sub(new Quaterniond().rotateY(angle).transform(new Vector3d(bottomCenter).sub(pose.rotationPoint())));

            if (!resetOrientation
                    && this.plotAnchor.distanceSquared(bottomCenter) <= 1.0E-6
                    && new Quaterniond().rotateY(angle).equals(pose.orientation(), 1e-6)
                    && desiredPos.distanceSquared(pose.position()) <= 1.0E-6
            ) return false;

            if (resetOrientation) {
                pose.orientation().identity();
                this.orientation.identity();
            }
            this.plotAnchor.set(bottomCenter);

            pose.orientation().identity().rotateY(angle);
            pose.position().set(JOMLConversion.toJOML(this.be.pivot)
                    .sub(pose.orientation().transform(new Vector3d(this.plotAnchor).sub(pose.rotationPoint()))));

            if (pipeline != null) {
                pipeline.wakeUp(this.subLevel);
                pipeline.resetVelocity(this.subLevel);
                pipeline.teleport(this.subLevel, pose.position(), pose.orientation());
            }
            this.subLevel.updateLastPose();
            return true;
        }

        private void attachConstraint(final SubLevelPhysicsSystem physicsSystem) {
            if (physicsSystem.getLevel() != this.subLevel.getLevel()) return;
            final PhysicsPipeline pipeline = physicsSystem.getPipeline();
            if (pipeline == null) return;

            final var pose = this.subLevel.logicalPose();
            this.constraint = pipeline.addConstraint(null, this.subLevel,
                    new FixedConstraintConfiguration(pose.position(), pose.rotationPoint(), pose.orientation()));
        }

        public void remove() {
            if (this.constraint != null) this.constraint.remove();
            this.constraint = null;
        }
    }
}

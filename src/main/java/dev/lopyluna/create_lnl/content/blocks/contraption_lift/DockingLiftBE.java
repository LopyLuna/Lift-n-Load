package dev.lopyluna.create_lnl.content.blocks.contraption_lift;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.lopyluna.create_lnl.content.blocks.PhysicHoldingBEs;
import dev.lopyluna.create_lnl.content.utils.LiftUtils;
import dev.lopyluna.create_lnl.events.CommonEvents;
import dev.lopyluna.create_lnl.register.LiftsBlocks;
import com.simibubi.create.content.contraptions.AssemblyException;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.index.SimSoundEvents;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DockingLiftBE extends SmartBlockEntity implements PhysicHoldingBEs {
    public static final Set<UUID> TRACKING_NO_COLLISIONS = ConcurrentHashMap.newKeySet();
    public static final int MOVE_RATE = 2;
    public static final float MOVE_STEP = MOVE_RATE / 16f;
    private static final List<DockingLiftBE> ACTIVE = new ArrayList<>();
    private static final Map<UUID, Long> CARRIED = new ConcurrentHashMap<>();

    public @Nullable UUID ownerUUID;
    public @Nullable UUID subUUID;
    public @Nullable DyeColor color;
    public boolean global;
    public boolean noCollision;

    public int target;
    public float height;
    public float prevHeight;

    public int rotation;
    public float angle;
    public float prevAngle;

    public @Nullable Vec3 dockAnchor;
    public @Nullable BlockPos supportPos;
    private @Nullable DockSession session;

    public boolean placing = true;
    public boolean placed;
    public float progress = -1 / 16f;
    public LerpedFloat angleFlap = LerpedFloat.angular().chase(89, 0.3, LerpedFloat.Chaser.EXP);
    public LerpedFloat visualHeight = LerpedFloat.linear().chase(0, 0.6, LerpedFloat.Chaser.EXP);

    public int structIndex;
    public int mastIndex = -1;
    private long predictUntil;
    private boolean soundReady = true;
    private boolean synced;
    private final Set<Long> forcedChunks = new HashSet<>();
    private final Set<UUID> suppressed = new HashSet<>();
    private List<SubLevel> cargo = List.of();

    public DockingLiftBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        structIndex = state.getValue(DockingLiftBlock.LIFT).structure() ? 1 : 0;
        angleFlap.startWithValue(89);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    @Override
    public void initialize() {
        super.initialize();
        if (!ACTIVE.contains(this)) ACTIVE.add(this);
        if (level == null || structIndex > 0) return;
        if (level.isClientSide) {
            syncNoCollision();
            return;
        }
        refreshOwnerPresence();
        dropIfSuperseded();
        if (!isRemoved()) forceLoaded(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) return;

        if (structIndex > 0) {
            if (level.getBlockEntity(worldPosition.below(structIndex)) instanceof DockingLiftBE base) {
                prevHeight = base.prevHeight;
                height = base.height;
                visualHeight = base.visualHeight;
                color = base.color;
            }
            return;
        }

        tickPlacing();

        clampTarget();
        prevHeight = height;
        prevAngle = angle;
        var goal = Mth.clamp(target, 0, (int) (LiftPlacement.MAX_HEIGHT * 16)) / 16f;
        if (!placing) {
            if (height != goal) height = goal > height ? Math.min(goal, height + MOVE_STEP) : Math.max(goal, height - MOVE_STEP);

            var goalAngle = -rotation * LiftPlacement.ROTATION_DEGREES;
            if (angle != goalAngle) angle = goalAngle > angle ? Math.min(goalAngle, angle + 5.625f) : Math.max(goalAngle, angle - 5.625f);
        }

        carryEntities(prevHeight, height, level.isClientSide);
        if (noCollision) applyNoCollision();
        if (level.isClientSide) {
            if (cargo.isEmpty() ? subUUID != null : level.getGameTime() % 20 == 0) refreshCargo();
            visualHeight.updateChaseTarget(height);
            visualHeight.tickChaser();
            return;
        }
        movementSound(height != prevHeight, height > prevHeight);

        bind();
        if (session != null) {
            session.tick();
            if (session.markedForRemoval || !supported()) undock();
            else if (session.settled()) {
                if (noCollision) setNoCollision(false);
                if (supportPos == null) session.resolveSupport();
            }
        }

        if (level.getGameTime() % 20 == 0) {
            refreshOwnerPresence();
            refreshCargo();
            refreshForcedChunks();
            CARRIED.values().removeIf(stamp -> level.getGameTime() - stamp > 10);
        }
        updateMast(height);
    }

    private void tickPlacing() {
        if (level == null) return;
        if (placing) {
            progress += progress >= 0 ? Math.max(Math.max(progress * 2, 0.6f) * (1f - progress) * progress, 1 / 8f) : 1 / 20f;
            if (level.isClientSide && progress >= 0) angleFlap.updateChaseTarget(Mth.clamp((1f - progress) * 90, 0, 90));
            if (progress > 1) placing = false;
            return;
        }
        if (placed) return;
        placed = true;
        if (level.isClientSide) return;
        var soundType = getBlockState().getSoundType(level, worldPosition, null);
        level.playSound(null, worldPosition, soundType.getHitSound(), SoundSource.BLOCKS,
                (soundType.getVolume() + 1.0F) / 8.0F, soundType.getPitch() * 0.5F);
        notifyUpdate();
    }

    private void movementSound(boolean moving, boolean rising) {
        if (level == null) return;
        if (!moving) {
            soundReady = true;
            return;
        }
        var ticks = level.getGameTime();
        if (ticks % 2 != 0) return;
        if (!soundReady && ticks % 3 != 0 && !(ticks % 5 == 0 && level.random.nextBoolean())) return;
        soundReady = false;

        var sound = rising ? SimSoundEvents.DOCKING_CONNECTOR_EXTENDS.event() : SimSoundEvents.DOCKING_CONNECTOR_RETRACTS.event();
        var pivot = LiftPlacement.pivot(worldPosition, height);
        level.playSound(null, worldPosition, sound, SoundSource.BLOCKS,
                0.03f + level.random.nextFloat() * 0.02f, 0.55F + level.random.nextFloat() * 0.15f + (height / 8f) * 0.5f);
        level.playSound(null, pivot.x, pivot.y, pivot.z, sound, SoundSource.BLOCKS,
                0.03f + level.random.nextFloat() * 0.02f, 0.5F + level.random.nextFloat() * 0.1f + (height / 8f));
    }

    private void carryEntities(float previousHeight, float currentHeight, boolean client) {
        if (level == null) return;
        var moveY = currentHeight - previousHeight;
        if (Math.abs(moveY) < 1.0E-5) return;
        var r = 0.35;
        var c = 4/16d;

        var pos = worldPosition;
        double minX = pos.getX() - c, minZ = pos.getZ() - c,
                maxX = pos.getX() + 1 + c, maxZ = pos.getZ() + 1 + c;
        double oldTopY = LiftPlacement.platformY(pos, previousHeight), topY = LiftPlacement.platformY(pos, currentHeight);
        var box = new AABB(minX, Math.min(oldTopY, topY) - 0.35, minZ, maxX, Math.max(oldTopY, topY) + 1.0, maxZ);

        for (var e : level.getEntities(null, box)) {
            if (Sable.HELPER.getContaining(e) != null || Sable.HELPER.getTrackingSubLevel(e) != null) continue;

            var feet = e.getBoundingBox().minY;
            if (feet > Math.max(oldTopY, topY) + r) continue;
            if (feet < Math.min(oldTopY, topY) - r) continue;

            e.fallDistance = 0;
            if (!client) CARRIED.put(e.getUUID(), level.getGameTime());
            if (client ? e.isControlledByLocalInstance() : !(e instanceof Player)) {
                e.move(MoverType.SHULKER_BOX, new Vec3(0, topY - feet, 0));

                var landed = e.getBoundingBox().minY;
                if (landed < topY - 1.0E-3 && landed > topY - r) e.setPos(e.getX(), e.getY() + (topY - landed), e.getZ());

                var motion = e.getDeltaMovement();
                if (motion.y < 0) e.setDeltaMovement(motion.x, 0, motion.z);
            }
            e.fallDistance = 0;
            e.setOnGround(true);
        }
    }

    private void refreshOwnerPresence() {
        if (!(level instanceof ServerLevel server)) return;
        var nowGlobal = ownerUUID == null || server.getServer().getPlayerList().getPlayer(ownerUUID) == null;
        if (nowGlobal == global) return;
        global = nowGlobal;
        notifyUpdate();
    }

    private void dropIfSuperseded() {
        if (level == null || ownerUUID == null) return;
        var owner = level.getPlayerByUUID(ownerUUID);
        if (owner == null) return;
        var claimed = LiftHolding.liftPos(owner);
        if (claimed != null && claimed.dimension().equals(level.dimension()) && claimed.pos().equals(worldPosition)) return;
        DockingLiftBlock.destroyLift(level, worldPosition);
    }

    public void setTarget(int value) {
        var clamped = Mth.clamp(value, 0, (int) (LiftPlacement.MAX_HEIGHT * 16));
        if (clamped == target) return;
        target = clamped;
        notifyUpdate();
    }

    private void clampTarget() {
        if (level == null) return;
        var current = Math.round(height * 16);
        if (target == current) return;

        var rising = target > current;
        if (cargoBlocked(rising ? MOVE_STEP : -MOVE_STEP)) {
            stopAt(current);
            return;
        }
        if (!rising) return;
        var limit = Mth.floor(heightLimit() * 16);
        if (limit < target) stopAt(Math.max(limit, current));
    }

    private void stopAt(int value) {
        if (target == value) return;
        target = value;
        if (level != null && !level.isClientSide) notifyUpdate();
    }

    private float heightLimit() {
        if (level == null) return LiftPlacement.MAX_HEIGHT;
        if (level.isClientSide) return LiftPlacement.mastHeight(level, worldPosition);
        return LiftPlacement.ceiling(level, worldPosition, dockedIds());
    }

    private Set<UUID> dockedIds() {
        return session == null ? Set.of() : session.ids;
    }

    private boolean cargoBlocked(double step) {
        if (level == null) return false;
        for (var member : cargo) {
            var bb = member.boundingBox();
            var swept = new AABB(bb.minX(), bb.minY(), bb.minZ(), bb.maxX(), bb.maxY(), bb.maxZ()).move(0, step, 0);
            var from = BlockPos.containing(swept.minX, swept.minY, swept.minZ);
            var to = BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ);

            for (var pos : BlockPos.betweenClosed(from, to)) {
                var state = level.getBlockState(pos);
                if (state.isAir() || state.getBlock() instanceof DockingLiftBlock) continue;
                var shape = state.getCollisionShape(level, pos);
                if (shape.isEmpty()) continue;

                for (var box : shape.toAabbs()) {
                    var world = box.move(pos.getX(), pos.getY(), pos.getZ());
                    if (world.intersects(swept) && occupies(member, world.move(0, -step, 0))) return true;
                }
            }
        }
        return false;
    }

    private static boolean occupies(SubLevel member, AABB box) {
        var plot = member.getLevel();
        var pose = member.logicalPose();
        var corner = new Vector3d();
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (int c = 0; c < 8; c++) {
            corner.set((c & 1) == 0 ? box.minX : box.maxX,
                    ((c >> 1) & 1) == 0 ? box.minY : box.maxY,
                    ((c >> 2) & 1) == 0 ? box.minZ : box.maxZ);
            pose.transformPositionInverse(corner);
            minX = Math.min(minX, corner.x); maxX = Math.max(maxX, corner.x);
            minY = Math.min(minY, corner.y); maxY = Math.max(maxY, corner.y);
            minZ = Math.min(minZ, corner.z); maxZ = Math.max(maxZ, corner.z);
        }
        var local = new AABB(minX, minY, minZ, maxX, maxY, maxZ);

        for (var pos : BlockPos.betweenClosed(Mth.floor(minX), Mth.floor(minY), Mth.floor(minZ),
                Mth.floor(maxX), Mth.floor(maxY), Mth.floor(maxZ))) {
            var state = plot.getBlockState(pos);
            if (state.isAir()) continue;
            for (var part : state.getCollisionShape(plot, pos).toAabbs())
                if (part.move(pos.getX(), pos.getY(), pos.getZ()).intersects(local)) return true;
        }
        return false;
    }

    public void blockAbove(BlockPos above) {
        if (!(level instanceof ServerLevel server) || structIndex > 0 || subUUID != null || placing) return;
        if (!above.equals(worldPosition.above(mastIndex + 1))) return;

        var assembled = assemble(server, above);
        if (assembled == null) return;

        var origin = above.offset(assembled.offset());
        var bounds = assembled.subLevel().getPlot().getBoundingBox();
        snapRotation(0);
        subUUID = assembled.subLevel().getUniqueId();
        dockAnchor = new Vec3(origin.getX() + 0.5, bounds.minY(), origin.getZ() + 0.5);
        supportPos = null;
        seatAt(bounds.minY() - assembled.offset().getY());
        notifyUpdate();
    }

    private void seatAt(double bottomY) {
        var seat = bottomY - worldPosition.getY() - 12 / 16d - LiftPlacement.SEAT_GAP;
        target = Mth.clamp((int) Math.round(seat * 16), 0, (int) (LiftPlacement.MAX_HEIGHT * 16));
        height = target / 16f;
        prevHeight = height;
    }

    private @Nullable SimAssemblyHelper.AssemblyResult assemble(ServerLevel server, BlockPos above) {
        try {
            return SimAssemblyHelper.assembleFromSingleBlock(server, above.below(), above, true, true);
        } catch (AssemblyException e) {
            return null;
        }
    }

    public void predictTarget(int delta) {
        if (level == null) return;
        target = Mth.clamp(target + delta * MOVE_RATE, 0, (int) (LiftPlacement.MAX_HEIGHT * 16));
        predictUntil = level.getGameTime() + 5;
    }

    public void rotate(int delta) {
        if (delta == 0) return;
        rotation += delta;
        notifyUpdate();
    }

    public void snapRotation(int value) {
        rotation = value;
        angle = -value * LiftPlacement.ROTATION_DEGREES;
        prevAngle = angle;
    }

    private void updateMast(float currentHeight) {
        if (level == null) return;
        var index = Mth.floor(currentHeight + 12 / 16f - 0.001f);
        if (index == mastIndex) return;
        mastIndex = index;

        for (int i = (int) LiftPlacement.MAX_HEIGHT; i > index; i--) {
            var pos = worldPosition.above(i);
            if (level.getBlockState(pos).getBlock() instanceof DockingLiftBlock) level.removeBlock(pos, false);
        }
        for (int i = 1; i <= index; i++) {
            var pos = worldPosition.above(i);
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof DockingLiftBlock) continue;
            if (!state.isAir() && !state.canBeReplaced()) continue;
            level.setBlockAndUpdate(pos, LiftsBlocks.CONTRAPTION_LIFT.getDefaultState()
                    .setValue(DockingLiftBlock.LIFT, DockingLiftBlock.LiftState.STRUCTURE));
            if (level.getBlockEntity(pos) instanceof DockingLiftBE segment) segment.structIndex = i;
        }
    }

    @Override
    public void physicsTick(SubLevelPhysicsSystem physicsSystem) {
        if (session == null || level != physicsSystem.getLevel()) return;
        session.physicsTick(physicsSystem);
    }

    public void bind() {
        if (subUUID == null || dockAnchor == null || session != null) return;
        if (!(level instanceof ServerLevel server)) return;
        if (!(SubLevelContainer.getContainer(server) instanceof ServerSubLevelContainer container)) return;
        if (!(container.getSubLevel(subUUID) instanceof ServerSubLevel docked)) return;
        session = new DockSession(this, docked, new Vector3d(dockAnchor.x, dockAnchor.y, dockAnchor.z));
        setNoCollision(true);
        refreshCargo();
        refreshForcedChunks();
        CommonEvents.addPhysicHolder(this);
    }

    private void refreshCargo() {
        if (level == null) return;
        var root = subUUID == null ? null : LiftHolding.resolve(level, subUUID);
        cargo = root == null ? List.of() : LiftUtils.getConnectedGroup(root);
        if (session != null) session.updateMembers(cargo);
    }

    private void refreshForcedChunks() {
        if (!(level instanceof ServerLevel server) || session == null) return;
        var wanted = createWanted(cargo);
        if (wanted.equals(forcedChunks)) return;

        for (var key : forcedChunks)
            if (!wanted.contains(key)) server.setChunkForced(ChunkPos.getX(key), ChunkPos.getZ(key), false);
        for (var key : wanted)
            if (!forcedChunks.contains(key)) server.setChunkForced(ChunkPos.getX(key), ChunkPos.getZ(key), true);

        forcedChunks.clear();
        forcedChunks.addAll(wanted);
    }

    private static @Nonnull HashSet<Long> createWanted(List<SubLevel> group) {
        var wanted = new HashSet<Long>();
        for (var member : group) {
            var bb = member.boundingBox();
            int minX = SectionPos.blockToSectionCoord(Mth.floor(bb.minX())), maxX = SectionPos.blockToSectionCoord(Mth.floor(bb.maxX())),
                minZ = SectionPos.blockToSectionCoord(Mth.floor(bb.minZ())), maxZ = SectionPos.blockToSectionCoord(Mth.floor(bb.maxZ()));
            for (int cx = minX; cx <= maxX && wanted.size() < 64; cx++)
                for (int cz = minZ; cz <= maxZ && wanted.size() < 64; cz++)
                    wanted.add(ChunkPos.asLong(cx, cz));
        }
        return wanted;
    }

    private void releaseForcedChunks() {
        if (forcedChunks.isEmpty()) return;
        if (level instanceof ServerLevel server)
            for (var key : forcedChunks) server.setChunkForced(ChunkPos.getX(key), ChunkPos.getZ(key), false);
        forcedChunks.clear();
    }

    public void undock() {
        setNoCollision(false);
        releaseForcedChunks();
        if (session != null) {
            session.remove();
            CommonEvents.removePhysicHolder(this);
            session = null;
        }
        if (subUUID == null && dockAnchor == null && supportPos == null) return;
        subUUID = null;
        dockAnchor = null;
        supportPos = null;
        notifyUpdate();
    }

    public void setNoCollision(boolean value) {
        if (noCollision == value) return;
        noCollision = value;
        if (!value) releaseNoCollision();
        else applyNoCollision();
        if (level != null && !level.isClientSide) notifyUpdate();
    }

    private void syncNoCollision() {
        if (noCollision) applyNoCollision();
        else releaseNoCollision();
    }

    private void applyNoCollision() {
        if (level == null || subUUID == null) return;
        var container = SubLevelContainer.getContainer(level);
        var root = container == null ? null : container.getSubLevel(subUUID);
        if (root == null) return;
        for (var id : LiftUtils.getConnectedGroupIds(root)) {
            suppressed.add(id);
            TRACKING_NO_COLLISIONS.add(id);
        }
    }

    private void releaseNoCollision() {
        for (var id : suppressed) TRACKING_NO_COLLISIONS.remove(id);
        suppressed.clear();
    }

    private boolean supported() {
        if (session == null || supportPos == null) return true;
        var plot = session.subLevel.getLevel();
        if (!plot.isLoaded(supportPos)) return true;
        var state = plot.getBlockState(supportPos);
        return !state.isAir() && !state.getCollisionShape(plot, supportPos).isEmpty();
    }

    public static void clearAll() {
        ACTIVE.clear();
        CARRIED.clear();
        TRACKING_NO_COLLISIONS.clear();
    }

    public static boolean carriedRecently(Entity entity) {
        var stamp = CARRIED.get(entity.getUUID());
        return stamp != null && entity.level().getGameTime() - stamp <= 10;
    }

    public boolean animating() {
        return structIndex == 0 && !placed && subUUID == null;
    }

    public float renderHeight(float pt) {
        if (level == null || !level.isClientSide) return height;
        if (structIndex > 0) return baseHeight(pt);
        if (subUUID == null || dockAnchor == null) return visualHeight.getValue(pt);

        var predicted = Mth.lerp(pt, prevHeight, height);
        var seated = seatedHeight(pt, predicted);
        return Math.abs(seated - predicted) > 1f ? predicted : seated;
    }

    private float baseHeight(float pt) {
        if (level == null) return height;
        return level.getBlockEntity(worldPosition.below(structIndex)) instanceof DockingLiftBE base && base.structIndex == 0
                ? base.renderHeight(pt) : visualHeight.getValue(pt);
    }

    private float seatedHeight(float pt, float fallback) {
        var container = SubLevelContainer.getContainer(level);
        var docked = container == null || subUUID == null ? null : container.getSubLevel(subUUID);
        if (docked == null || dockAnchor == null) return fallback;

        var last = docked.lastPose().transformPosition(new Vector3d(dockAnchor.x, dockAnchor.y, dockAnchor.z));
        var target = docked.logicalPose().transformPosition(new Vector3d(dockAnchor.x, dockAnchor.y, dockAnchor.z));
        return (float) (Mth.lerp(pt, last.y, target.y) - LiftPlacement.SEAT_GAP - worldPosition.getY() - 12 / 16d);
    }
    public boolean isOwner(Player player) {
        return ownerUUID != null && ownerUUID.equals(player.getUUID());
    }
    public boolean cantControl(Player player) {
        return !isOwner(player) && !global;
    }

    public static @Nullable DockingLiftBE controlledBy(Player player) {
        var own = ownLift(player);
        if (own != null) return own;

        DockingLiftBE best = null;
        var bestDistance = 2048 * 2048d;
        for (var be : ACTIVE) {
            if (be.isRemoved() || be.structIndex > 0 || !be.global || be.level != player.level()) continue;
            var distance = be.worldPosition.distToCenterSqr(player.position());
            if (distance >= bestDistance) continue;
            best = be;
            bestDistance = distance;
        }
        return best;
    }

    private static @Nullable DockingLiftBE ownLift(Player player) {
        var claimed = LiftHolding.liftPos(player);
        if (claimed == null) return null;

        if (!(player.level().getServer() instanceof MinecraftServer server)) {
            for (var be : ACTIVE) {
                if (be.isRemoved() || be.structIndex > 0 || be.level != player.level()) continue;
                if (be.worldPosition.equals(claimed.pos()) && be.isOwner(player)) return be;
            }
            return null;
        }

        var level = server.getLevel(claimed.dimension());
        if (level == null) return null;
        var pos = claimed.pos();
        level.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
        if (level.getBlockEntity(pos) instanceof DockingLiftBE be && !be.isRemoved() && be.structIndex == 0 && be.isOwner(player)) return be;
        return null;
    }

    public void forceLoaded(boolean forced) {
        if (!(level instanceof ServerLevel server) || structIndex > 0) return;
        server.setChunkForced(SectionPos.blockToSectionCoord(worldPosition.getX()),
                SectionPos.blockToSectionCoord(worldPosition.getZ()), forced);
    }

    @Override
    protected void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(nbt, registries, clientPacket);
        nbt.putInt("Index", structIndex);
        nbt.putInt("Target", target);
        nbt.putFloat("Height", height);
        nbt.putBoolean("Global", global);
        nbt.putBoolean("NoCollision", noCollision);
        nbt.putBoolean("Placed", placed);
        nbt.putFloat("Progress", progress);
        nbt.putInt("Rotation", rotation);
        nbt.putFloat("Angle", angle);
        if (color != null) nbt.putInt("Color", color.getId());
        if (ownerUUID != null) nbt.putUUID("Owner", ownerUUID);
        if (subUUID != null) nbt.putUUID("SubLevelID", subUUID);
        if (dockAnchor != null) {
            nbt.putDouble("AnchorX", dockAnchor.x);
            nbt.putDouble("AnchorY", dockAnchor.y);
            nbt.putDouble("AnchorZ", dockAnchor.z);
        }
        if (supportPos != null) nbt.put("SupportPos", NbtUtils.writeBlockPos(supportPos));
    }

    @Override
    protected void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(nbt, registries, clientPacket);
        structIndex = nbt.getInt("Index");
        global = nbt.getBoolean("Global");
        rotation = nbt.getInt("Rotation");
        color = nbt.contains("Color") ? DyeColor.byId(nbt.getInt("Color")) : null;
        ownerUUID = nbt.contains("Owner") ? nbt.getUUID("Owner") : null;
        subUUID = nbt.contains("SubLevelID") ? nbt.getUUID("SubLevelID") : null;
        dockAnchor = nbt.contains("AnchorX")
                ? new Vec3(nbt.getDouble("AnchorX"), nbt.getDouble("AnchorY"), nbt.getDouble("AnchorZ")) : null;
        supportPos = nbt.contains("SupportPos") ? NBTHelper.readBlockPos(nbt, "SupportPos") : null;
        noCollision = nbt.getBoolean("NoCollision");
        syncNoCollision();
        placed = nbt.getBoolean("Placed");
        progress = nbt.getFloat("Progress");
        if (placed) placing = false;

        var syncedTarget = nbt.getInt("Target");
        var predicting = clientPacket && level != null && level.getGameTime() < predictUntil;
        if (!predicting || Math.abs(syncedTarget - target) > MOVE_RATE * 4) target = syncedTarget;

        var syncedHeight = nbt.getFloat("Height");
        if (!clientPacket || Math.abs(syncedHeight - height) > 0.5f) {
            height = syncedHeight;
            prevHeight = syncedHeight;
            if (clientPacket) visualHeight.startWithValue(syncedHeight);
        }
        if (clientPacket && !synced) {
            visualHeight.startWithValue(height);
            synced = true;
        }
        var syncedAngle = nbt.getFloat("Angle");
        if (!clientPacket || Math.abs(syncedAngle - angle) > 90f) {
            angle = syncedAngle;
            prevAngle = syncedAngle;
        }
        if (structIndex == 0) mastIndex = Mth.floor(height + 12 / 16f - 0.001f);
    }

    @Override
    public AABB getRenderBoundingBox() {
        if (structIndex > 0) return super.getRenderBoundingBox();
        return super.getRenderBoundingBox().expandTowards(0, height, 0).inflate(0.5);
    }

    private static class DockSession {
        private final DockingLiftBE be;
        private final ServerSubLevel subLevel;
        private final Vector3d anchor;
        private final Vector3d goal = new Vector3d();
        private final Vector3d mount = new Vector3d();
        private final Quaterniond orientation = new Quaterniond();
        private final List<PhysicsConstraintHandle> locks = new ArrayList<>();
        private volatile List<ServerSubLevel> members = List.of();
        private volatile Set<UUID> ids;
        private volatile boolean relock = true;
        private PhysicsConstraintHandle constraint;
        private boolean markedForRemoval;
        private volatile int settledTicks;
        private volatile int stillTicks;

        private DockSession(DockingLiftBE be, ServerSubLevel subLevel, Vector3d anchor) {
            this.be = be;
            this.subLevel = subLevel;
            this.anchor = subLevel.getPlot().contains(anchor) ? anchor : new Vector3d(subLevel.logicalPose().rotationPoint());
            this.ids = Set.of(subLevel.getUniqueId());
        }

        private void tick() {
            if (subLevel.isRemoved()) markedForRemoval = true;
        }

        private boolean settled() {
            return settledTicks >= 3 || stillTicks >= 20;
        }

        private void updateMembers(List<SubLevel> group) {
            var next = new ArrayList<ServerSubLevel>(group.size());
            var nextIds = new HashSet<UUID>();
            nextIds.add(subLevel.getUniqueId());
            for (var member : group) {
                if (member == subLevel || !(member instanceof ServerSubLevel body) || body.isRemoved()) continue;
                next.add(body);
                nextIds.add(body.getUniqueId());
            }
            ids = nextIds;
            if (next.size() == members.size() && new HashSet<>(members).containsAll(next)) return;
            members = next;
            relock = true;
        }

        @SuppressWarnings("NonAtomicOperationOnVolatileField")
        private void physicsTick(SubLevelPhysicsSystem physicsSystem) {
            if (subLevel.isRemoved() || physicsSystem.getLevel() != subLevel.getLevel()) return;
            var pipeline = physicsSystem.getPipeline();
            if (pipeline == null) return;

            var partial = (float) physicsSystem.getPartialPhysicsTick();
            var pivot = LiftPlacement.pivot(be.worldPosition, Mth.lerp(partial, be.prevHeight, be.height));
            goal.set(pivot.x, pivot.y, pivot.z);
            orientation.identity().rotateY(Math.toRadians(Mth.lerp(partial, be.prevAngle, be.angle)));

            if (relock) applyLocks(pipeline);
            if (constraint != null) constraint.remove();
            constraint = pipeline.addConstraint(null, subLevel, new FixedConstraintConfiguration(goal, anchor, orientation));

            var moving = be.height != be.prevHeight || be.angle != be.prevAngle;
            if (moving) pipeline.wakeUp(subLevel);
            if (partial < 1f) return;
            if (moving) {
                settledTicks = 0;
                stillTicks = 0;
                return;
            }

            if (stillTicks < 20) stillTicks++;
            var deviation = subLevel.logicalPose().transformPosition(new Vector3d(anchor)).distance(goal);
            if (deviation <= 1 / 64d) {
                if (settledTicks < 3) settledTicks++;
            } else settledTicks = 0;
        }

        private void applyLocks(PhysicsPipeline pipeline) {
            relock = false;
            releaseLocks();
            var rootPose = subLevel.logicalPose();
            var plot = subLevel.getPlot();

            for (var member : members) {
                if (member.isRemoved()) continue;
                var memberPose = member.logicalPose();
                rootPose.transformPositionInverse(new Vector3d(memberPose.position()), mount);
                if (!plot.contains(mount)) continue;
                var relative = new Quaterniond(rootPose.orientation()).invert().mul(memberPose.orientation());
                var handle = pipeline.addConstraint(subLevel, member, new FixedConstraintConfiguration(
                        new Vector3d(mount), new Vector3d(memberPose.rotationPoint()), relative));
                if (handle != null) locks.add(handle);
                pipeline.wakeUp(member);
            }
            pipeline.wakeUp(subLevel);
        }

        private void resolveSupport() {
            var pivot = LiftPlacement.pivot(be.worldPosition, be.height);
            var probe = new Vector3d(pivot.x, pivot.y + 1.0E-4, pivot.z);
            var found = findSupport(probe, false);
            if (found == null) found = findSupport(probe, true);
            if (found == null) return;
            be.supportPos = found;
            be.notifyUpdate();
        }

        private @Nullable BlockPos findSupport(Vector3d probe, boolean neighbours) {
            var plot = subLevel.getLevel();
            var local = new Vector3d();

            for (var member : holders()) {
                member.logicalPose().transformPositionInverse(probe, local);
                if (!member.getPlot().contains(local)) continue;
                var pos = BlockPos.containing(local.x, local.y, local.z);
                if (!neighbours) {
                    if (!plot.getBlockState(pos).isAir()) return pos;
                    continue;
                }
                for (var dir : Direction.Plane.HORIZONTAL) {
                    var side = pos.relative(dir);
                    if (!plot.getBlockState(side).isAir()) return side;
                }
            }
            return null;
        }

        private List<ServerSubLevel> holders() {
            var all = new ArrayList<ServerSubLevel>(members.size() + 1);
            all.add(subLevel);
            all.addAll(members);
            return all;
        }

        private void releaseLocks() {
            for (var lock : locks) lock.remove();
            locks.clear();
        }

        private void remove() {
            releaseLocks();
            if (constraint != null) constraint.remove();
            constraint = null;
        }
    }

    @Override
    public void remove() {
        super.remove();
        ACTIVE.remove(this);
        releaseNoCollision();
    }

    @Override
    public void destroy() {
        super.destroy();
        ACTIVE.remove(this);
        forceLoaded(false);
        releaseForcedChunks();
        undock();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        ACTIVE.remove(this);
        releaseNoCollision();
        if (session != null) {
            session.remove();
            CommonEvents.removePhysicHolder(this);
            session = null;
        }
    }
}

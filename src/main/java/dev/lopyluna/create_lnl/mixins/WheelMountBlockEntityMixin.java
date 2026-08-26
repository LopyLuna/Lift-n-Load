package dev.lopyluna.create_lnl.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.lopyluna.create_lnl.Lifts;
import dev.lopyluna.create_lnl.content.blocks.connectors.IConnection;
import dev.lopyluna.create_lnl.content.blocks.node_link.NodeLinkBE;
import dev.lopyluna.create_lnl.content.blocks.node_link.NodeLinkReceiver;
import dev.lopyluna.create_lnl.register.LiftsDataComps;
import dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.data.TriState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

@SuppressWarnings("DiscouragedShift")
@Mixin(value = WheelMountBlockEntity.class, remap = false)
public abstract class WheelMountBlockEntityMixin extends KineticBlockEntity implements IConnection<WheelMountBlockEntity>, NodeLinkReceiver {
    @Unique protected final Set<BlockPos> lifts$connections = new HashSet<>();
    @Shadow @Final private Vector3d queuedForce;

    @Unique protected boolean lifts$invert = false;

    public WheelMountBlockEntityMixin(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    @Shadow public abstract ItemStack getHeldItem();

    @Inject(method = "sable$physicsTick", at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/physics/force/ForceTotal;applyImpulseAtPoint(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;)V", shift = At.Shift.BEFORE))
    private void lifts$stickyWheel(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep, CallbackInfo ci, @Local(name = "rayHitNormal") Vec3i rayHitNormal) {
        if (!getHeldItem().has(LiftsDataComps.STICKY)) return;
        var grav = new Vec3(rayHitNormal.getX(), rayHitNormal.getY(), rayHitNormal.getZ()).scale(9.81);
        var gravityLocal = subLevel.logicalPose().transformNormalInverse(new Vector3d(grav.x, grav.y, grav.z));
        var mass = subLevel.getMassTracker().getMass();
        queuedForce.sub(gravityLocal.mul(mass/4 * timeStep));
    }

    @Override
    public void lifts$onConnectionUpdate(BlockPos fromPos, IConnection<?> from, TriState tri) {
        if (isOutOfRange(fromPos, from)) return;
        if (from instanceof NodeLinkBE be) {
            var pre = lifts$invert;
            lifts$invert = be.strength > 0 && tri != TriState.FALSE;
            Lifts.LOGGER.info("invert is now = {}, before = {}", lifts$invert, pre);
            sendData();
        }
    }

    @WrapOperation(method = "computeYaw", at = @At(value = "INVOKE", target = "Ldev/ryanhcode/offroad/content/blocks/wheel_mount/WheelMountBlockEntity;getSteeringSignal()I"))
    protected int computeYaw(WheelMountBlockEntity instance, Operation<Integer> original) {
        return (lifts$invert?-1:1) * original.call(instance);
    }

    @Inject(method = "write", at = @At(value = "TAIL"))
    private void write(CompoundTag nbt, HolderLookup.Provider provider, boolean clientPacket, CallbackInfo ci) {
        writeI((WheelMountBlockEntity) (Object) this, level, nbt, provider, (level != null && level.isClientSide) || clientPacket);
        nbt.putBoolean("Inverting", lifts$invert);
    }

    @Inject(method = "read", at = @At(value = "TAIL"))
    private void read(CompoundTag nbt, HolderLookup.Provider provider, boolean clientPacket, CallbackInfo ci) {
        readI((WheelMountBlockEntity) (Object) this, level, nbt, provider, (level != null && level.isClientSide) || clientPacket);
        lifts$invert = nbt.getBoolean("Inverting");
    }

    @Override
    public void writeSafe(CompoundTag nbt, HolderLookup.Provider provider) {
        super.writeSafe(nbt, provider);
        writeI((WheelMountBlockEntity) (Object) this, level, nbt, provider, level != null && level.isClientSide);
        lifts$invert = nbt.getBoolean("Inverting");
    }

    @Override
    public void onLoad() {
        super.onLoad();
        loadI((WheelMountBlockEntity) (Object) this, level);
    }

    @Override
    public void remove() {
        super.remove();
        removeI((WheelMountBlockEntity) (Object) this, level);
    }

    @Override
    public String lifts$getColor() {
        return lifts$connections.isEmpty() ?  IConnection.DEFAULT_COLOR : "5EFE6D";
    }

    @Override
    public Set<BlockPos> lifts$getConnections() {
        return lifts$connections;
    }

    @Override
    public boolean lifts$containsConnection(BlockPos pos) {
        return lifts$connections.contains(pos);
    }

    @Override
    public void lifts$addConnectionRaw(BlockPos pos, boolean update) {
        lifts$connections.add(pos);
        if (update) notifyUpdate();
    }

    @Override
    public void lifts$removeConnectionRaw(BlockPos pos, boolean update) {
        lifts$connections.remove(pos);
        if (update) notifyUpdate();
    }

    @Override
    public void lifts$clearConnectionRaw(boolean update) {
        lifts$connections.clear();
        if (update) notifyUpdate();
    }

    @Override
    public boolean lifts$isStatic() {
        return false;
    }
}

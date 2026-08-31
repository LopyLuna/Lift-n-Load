package dev.lopyluna.create_lnl.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.lopyluna.create_lnl.content.nodes.hosts.SteerInverter;
import dev.lopyluna.create_lnl.register.LiftsDataComps;
import dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
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

//TOGO IMPLEMENT STEERING WITH NODE TO NODE CONNECTION BETWEEN STEERING WHEEL & A NEW DRIVER CONTROLS LATER

@SuppressWarnings("DiscouragedShift")
@Mixin(value = WheelMountBlockEntity.class, remap = false)
public abstract class WheelMountBlockEntityMixin extends KineticBlockEntity implements SteerInverter {
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

    @WrapOperation(method = "computeYaw", at = @At(value = "INVOKE", target = "Ldev/ryanhcode/offroad/content/blocks/wheel_mount/WheelMountBlockEntity;getSteeringSignal()I"))
    protected int computeYaw(WheelMountBlockEntity instance, Operation<Integer> original) {
        return (lifts$invert?-1:1) * original.call(instance);
    }

    @Override
    public boolean lifts$getInvert() {
        return lifts$invert;
    }

    @Override
    public void lifts$setInvert(boolean invert) {
        if (lifts$invert == invert) return;
        lifts$invert = invert;
        sendData();
    }

    @Inject(method = "write", at = @At(value = "TAIL"))
    private void write(CompoundTag nbt, HolderLookup.Provider provider, boolean clientPacket, CallbackInfo ci) {
        nbt.putBoolean("Inverting", lifts$invert);
    }

    @Inject(method = "read", at = @At(value = "TAIL"))
    private void read(CompoundTag nbt, HolderLookup.Provider provider, boolean clientPacket, CallbackInfo ci) {
        lifts$invert = nbt.getBoolean("Inverting");
    }
}

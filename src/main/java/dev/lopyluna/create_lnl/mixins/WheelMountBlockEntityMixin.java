package dev.lopyluna.create_lnl.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import dev.lopyluna.create_lnl.register.LiftsDataComps;
import dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("DiscouragedShift")
@Mixin(value = WheelMountBlockEntity.class, remap = false)
public abstract class WheelMountBlockEntityMixin {
    @Shadow @Final private Vector3d queuedForce;
    @Shadow public abstract ItemStack getHeldItem();



    @Inject(method = "sable$physicsTick", at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/physics/force/ForceTotal;applyImpulseAtPoint(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;)V", shift = At.Shift.BEFORE))
    private void lifts$stickyWheel(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep, CallbackInfo ci, @Local(name = "rayHitNormal") Vec3i rayHitNormal) {
        if (!getHeldItem().has(LiftsDataComps.STICKY)) return;
        var grav = new Vec3(rayHitNormal.getX(), rayHitNormal.getY(), rayHitNormal.getZ()).scale(9.81);
        Vector3d gravityLocal = subLevel.logicalPose().transformNormalInverse(new Vector3d(grav.x, grav.y, grav.z));

        double mass = subLevel.getMassTracker().getMass();
        queuedForce.sub(gravityLocal.mul(mass/4 * timeStep));
    }
}

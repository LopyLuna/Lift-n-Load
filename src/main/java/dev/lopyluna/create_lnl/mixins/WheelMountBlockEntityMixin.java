package dev.lopyluna.create_lnl.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import dev.lopyluna.create_lnl.content.blocks.wheel.WheelBE;
import dev.lopyluna.create_lnl.register.LiftsDataComps;
import dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("DiscouragedShift")
@Mixin(WheelMountBlockEntity.class)
public class WheelMountBlockEntityMixin {
    @Final @Shadow private Vector3d queuedForce;

    @Inject(method = "sable$physicsTick", at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/physics/force/ForceTotal;applyImpulseAtPoint(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;)V", shift = At.Shift.BEFORE))
    private void lifts$applyStickyForce(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep, CallbackInfo ci, @Local(name = "item") ItemStack item) {
        if (!item.has(LiftsDataComps.STICKY)) return;
        double adhesionForce = WheelBE.STICK_MOUNT_STRENGTH * timeStep;
        queuedForce.fma(adhesionForce, new Vector3d(0, -1, 0));
    }
}

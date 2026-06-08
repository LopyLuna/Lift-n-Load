package dev.lopyluna.create_lnl.mixins;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import dev.lopyluna.create_lnl.content.blocks.water_wheel_paddles.PaddlesUtils;
import dev.lopyluna.create_lnl.register.LiftsConfigs;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = WaterWheelBlockEntity.class, remap = false)
public abstract class WaterWheelPaddlesBEMixin extends GeneratingKineticBlockEntity implements BlockEntitySubLevelActor {
    @Shadow protected abstract int getSize();
    public WaterWheelPaddlesBEMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }

    @Unique private List<Couple<Vec3>> lift$rayPoints;
    @Unique private boolean lift$paddling;

    @Inject(method = "<init>",  at = @At("TAIL"))
    public void lift$generateRays(final BlockEntityType<?> type, final BlockPos pos, final BlockState state, final CallbackInfo ci) {
        this.lift$rayPoints = PaddlesUtils.genCastPoints(state, getSize());
    }
    @Inject(method = "read(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Z)V", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/base/GeneratingKineticBlockEntity;read(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Z)V"))
    public void lift$read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        lift$paddling = nbt.getBoolean("Paddling");
    }

    @Inject(method = "writeSafe(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/base/GeneratingKineticBlockEntity;writeSafe(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V"))
    public void lift$writeSafe(CompoundTag nbt, HolderLookup.Provider registries, CallbackInfo ci) {
        nbt.putBoolean("Paddling", lift$paddling);
    }

    @Inject(method = "write(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Z)V", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/base/GeneratingKineticBlockEntity;write(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Z)V"))
    public void lift$write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        nbt.putBoolean("Paddling", lift$paddling);
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (level == null || level.isClientSide) return;
        var oldPaddle = lift$paddling;
        lift$paddling = PaddlesUtils.applyForcesFromRays(subLevel, getBlockState(), getSize(), timeStep, level, worldPosition, (WaterWheelBlockEntity) (Object) this, lift$rayPoints);
        if (oldPaddle != lift$paddling) {
            if (hasNetwork()) {
                var network = getOrCreateNetwork();
                network.updateStressFor(this, calculateStressApplied());
                network.updateStress();
            }
            notifyUpdate();
            updateSpeed = true;
        }
    }

    @Override
    public float calculateStressApplied() {
        var config = LiftsConfigs.server().kinetics.stressValues.getImpact("water_wheel_paddle");
        var impact = super.calculateStressApplied() + (lift$paddling && config != null ? (float) config.getAsDouble() : 0f) * getSize();
        this.lastStressApplied = impact;
        return impact;
    }
}

package dev.lopyluna.create_lnl.mixins;

import dev.lopyluna.create_lnl.content.nodes.Node;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SignalGetter.class)
public interface SignalGetterMixin {
    @Inject(method = "hasNeighborSignal", at = @At("HEAD"), cancellable = true)
    private void lifts$nodeNeighborSignal(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!Node.Redstone.any) return;
        if (!((Object) this instanceof Level level)) return;
        if (Node.Redstone.get(level, pos) > 0) cir.setReturnValue(true);
    }

    @Inject(method = "getBestNeighborSignal", at = @At("RETURN"), cancellable = true)
    private void lifts$nodeBestSignal(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!Node.Redstone.any) return;
        if (!((Object) this instanceof Level level)) return;
        var power = Node.Redstone.get(level, pos);
        if (power > cir.getReturnValueI()) cir.setReturnValue(power);
    }
}

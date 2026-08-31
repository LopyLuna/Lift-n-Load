package dev.lopyluna.create_lnl.mixins;

import dev.lopyluna.create_lnl.content.nodes.Node;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonBaseBlock.class)
public class PistonBaseBlockMixin {
    @Inject(method = "getNeighborSignal", at = @At("HEAD"), cancellable = true)
    private void nodeSignal(SignalGetter getter, BlockPos pos, Direction facing, CallbackInfoReturnable<Boolean> cir) {
        if (!Node.Redstone.any || !(getter instanceof Level level)) return;
        if (Node.Redstone.get(level, pos) > 0) cir.setReturnValue(true);
    }
}

package dev.lopyluna.create_lnl.mixins;

import dev.lopyluna.create_lnl.content.nodes.Node;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({RedstoneTorchBlock.class, RedstoneWallTorchBlock.class})
public class RedstoneTorchBlockMixin {
    @Inject(method = "hasNeighborSignal", at = @At("HEAD"), cancellable = true)
    private void nodeSignal(Level level, BlockPos pos, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (!Node.Redstone.any) return;
        if (Node.Redstone.get(level, pos) > 0) cir.setReturnValue(true);
    }
}

package dev.lopyluna.create_lnl.mixins;

import dev.lopyluna.create_lnl.content.nodes.Node;
import dev.lopyluna.create_lnl.content.nodes.loose.LooseNodes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public class LevelChunkMixin {
    @Inject(method = "setBlockEntity(Lnet/minecraft/world/level/block/entity/BlockEntity;)V",
            at = @At(value = "INVOKE_ASSIGN", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private void addNode(BlockEntity be, CallbackInfo ci) {
        Node.Tracker.track(be);
    }

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void updateNodes(BlockPos pos, BlockState state, boolean isMoving, CallbackInfoReturnable<BlockState> cir) {
        if (!LooseNodes.any || cir.getReturnValue() == null) return;
        LooseNodes.update(((LevelChunk) (Object) this).getLevel(), pos);
    }
}

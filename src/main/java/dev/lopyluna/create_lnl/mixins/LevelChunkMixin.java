package dev.lopyluna.create_lnl.mixins;

import dev.lopyluna.create_lnl.content.blocks.connectors.Connection;
import dev.lopyluna.create_lnl.content.blocks.connectors.IConnection;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public class LevelChunkMixin {
    @Inject(method = "setBlockEntity(Lnet/minecraft/world/level/block/entity/BlockEntity;)V",
            at = @At(value = "INVOKE_ASSIGN", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private void addConnector(BlockEntity be, CallbackInfo ci) {
        if (be instanceof IConnection<?> c) IConnection.connections.put(be.getBlockPos(), c.lifts$isStatic() ? Connection.of(be, c.rgb(), Connection.Type.getType(be)) : Connection.of(be, c::rgb, () -> Connection.Type.getType(be)));
    }
}

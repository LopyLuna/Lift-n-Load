package dev.lopyluna.create_lnl.mixins;

import dev.lopyluna.create_lnl.content.blocks.connectors.IConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public class BlockEntityMixin {
    @Shadow @Final protected BlockPos worldPosition;

    @Inject(method = "setRemoved()V", at = @At("TAIL"))
    private void removeConnector(CallbackInfo ci) {
        if (this instanceof IConnection) IConnection.connections.remove(worldPosition);
    }
}

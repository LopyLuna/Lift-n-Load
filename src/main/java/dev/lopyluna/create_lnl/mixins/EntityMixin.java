package dev.lopyluna.create_lnl.mixins;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.lopyluna.create_lnl.register.LiftsTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @WrapWithCondition(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;updateEntityAfterFallOn(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;)V"))
    private boolean updateEntityAfterFallOn(Block instance, BlockGetter level, Entity entity) {
        if (entity instanceof ItemEntity item && item.getItem().is(LiftsTags.ItemTags.SPRING_LIKE.tag)) {
            entity.setDeltaMovement(entity.getDeltaMovement().multiply(1, -1, 1));
            return false;
        }
        return true;
    }
}

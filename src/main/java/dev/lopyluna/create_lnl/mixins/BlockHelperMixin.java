package dev.lopyluna.create_lnl.mixins;

import dev.lopyluna.create_lnl.content.nodes.Node;
import com.simibubi.create.foundation.utility.BlockHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockHelper.class, remap = false)
public class BlockHelperMixin {
    @Inject(method = "placeSchematicBlock", at = @At("TAIL"))
    private static void restoreNodes(Level world, BlockState state, BlockPos target, ItemStack stack, CompoundTag data, CallbackInfo ci) {
        Node.Schematics.place(world, target);
    }
}

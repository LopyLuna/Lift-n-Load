package dev.lopyluna.create_lnl.mixins;

import dev.lopyluna.create_lnl.content.nodes.Node;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashMap;
import java.util.Map;

@Mixin(value = SchematicLevel.class, remap = false)
public class SchematicLevelMixin implements Node.Template {
    @Unique private final Map<BlockPos, CompoundTag> lifts$nodes = new HashMap<>();

    @Override
    public Map<BlockPos, CompoundTag> lifts$nodes() {
        return lifts$nodes;
    }
}

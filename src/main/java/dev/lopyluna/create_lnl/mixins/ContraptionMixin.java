package dev.lopyluna.create_lnl.mixins;

import dev.lopyluna.create_lnl.content.nodes.Node;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.StructureTransform;
import dev.lopyluna.create_lnl.content.nodes.NodePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Mixin(value = Contraption.class, remap = false)
public class ContraptionMixin {
    @Shadow protected Map<BlockPos, StructureBlockInfo> blocks;
    @Shadow public BlockPos anchor;

    @Unique private final Map<BlockPos, CompoundTag> lifts$nodes = new HashMap<>();

    @Inject(method = "removeBlocksFromWorld", at = @At("HEAD"))
    private void captureNodes(Level world, BlockPos offset, CallbackInfo ci) {
        if (world.isClientSide) return;
        var graph = Node.Graphs.get(world);
        if (graph.nodes.isEmpty()) return;
        var origin = anchor.offset(offset);
        var moved = new ArrayList<BlockPos>(blocks.size());
        for (var local : blocks.keySet()) moved.add(local.offset(origin));
        lifts$nodes.clear();
        for (var pos : NodePayload.candidates(graph, moved)) {
            var tag = NodePayload.save(graph, pos, link -> blocks.containsKey(link.subtract(origin)), link -> link.subtract(origin), true);
            if (tag != null) lifts$nodes.put(pos.subtract(origin), tag);
        }
        if (lifts$nodes.isEmpty()) return;
        for (var entry : lifts$nodes.entrySet()) {
            var pos = entry.getKey().offset(origin);
            NodePayload.clear(world, graph, pos, blocks.containsKey(entry.getKey()), entry.getValue());
        }
        Node.Graphs.flush(world);
    }

    @Inject(method = "addBlocksToWorld", at = @At("TAIL"))
    private void restoreNodes(Level world, StructureTransform transform, CallbackInfo ci) {
        if (world.isClientSide || lifts$nodes.isEmpty()) return;
        for (var entry : lifts$nodes.entrySet()) NodePayload.load(world, transform.apply(entry.getKey()), entry.getValue(), transform);
        for (var entry : lifts$nodes.entrySet())
            NodePayload.wire(world, transform.apply(entry.getKey()), entry.getValue(), transform::apply, transform);
        Node.Graphs.flush(world);
    }

    @Inject(method = "writeNBT", at = @At("RETURN"))
    private void writeNodes(HolderLookup.Provider registries, boolean spawnPacket, CallbackInfoReturnable<CompoundTag> cir) {
        if (lifts$nodes.isEmpty()) return;
        var list = new ListTag();
        for (var entry : lifts$nodes.entrySet()) {
            var nbt = entry.getValue().copy();
            nbt.putLong("Pos", entry.getKey().asLong());
            list.add(nbt);
        }
        cir.getReturnValue().put("LiftsNodes", list);
    }

    @Inject(method = "readNBT", at = @At("TAIL"))
    private void readNodes(Level world, CompoundTag nbt, boolean spawnData, CallbackInfo ci) {
        lifts$nodes.clear();
        var list = nbt.getList("LiftsNodes", Tag.TAG_COMPOUND);
        for (var i = 0; i < list.size(); i++) {
            var tag = list.getCompound(i);
            lifts$nodes.put(BlockPos.of(tag.getLong("Pos")), tag);
        }
    }
}

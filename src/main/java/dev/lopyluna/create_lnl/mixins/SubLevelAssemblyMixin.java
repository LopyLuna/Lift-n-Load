package dev.lopyluna.create_lnl.mixins;

import dev.lopyluna.create_lnl.content.nodes.Node;
import com.simibubi.create.content.contraptions.StructureTransform;
import dev.lopyluna.create_lnl.content.nodes.NodePayload;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

@Mixin(value = SubLevelAssemblyHelper.class, remap = false)
public class SubLevelAssemblyMixin {
    @Unique private static final Map<BlockPos, CompoundTag> lifts$carried = new HashMap<>();
    @Unique private static final Map<BlockPos, BlockPos> lifts$moves = new HashMap<>();

    @Inject(method = "moveBlocks", at = @At("HEAD"))
    private static void lifts$captureNodes(ServerLevel level, SubLevelAssemblyHelper.AssemblyTransform transform, Iterable<BlockPos> blocks, CallbackInfo ci) {
        lifts$carried.clear();
        lifts$moves.clear();
        var graph = Node.Graphs.get(level);
        if (graph.nodes.isEmpty()) return;
        var moved = new ArrayList<BlockPos>();
        for (var pos : blocks) {
            moved.add(pos.immutable());
            lifts$moves.put(pos.immutable(), transform.apply(pos).immutable());
        }
        var owned = new HashSet<>(moved);
        var taken = new HashMap<BlockPos, CompoundTag>();
        for (var pos : NodePayload.candidates(graph, moved)) {
            var tag = NodePayload.save(graph, pos, owned::contains, link -> link, transform.getLevel() == level);
            if (tag == null) continue;
            taken.put(pos, tag);
            lifts$moves.putIfAbsent(pos, transform.apply(pos).immutable());
            lifts$carried.put(lifts$moves.get(pos), tag);
        }
        if (lifts$carried.isEmpty()) return;
        for (var entry : taken.entrySet()) NodePayload.clear(level, graph, entry.getKey(), owned.contains(entry.getKey()), entry.getValue());
        Node.Graphs.flush(level);
    }

    @Inject(method = "moveBlocks", at = @At("TAIL"))
    private static void lifts$restoreNodes(ServerLevel level, SubLevelAssemblyHelper.AssemblyTransform transform, Iterable<BlockPos> blocks, CallbackInfo ci) {
        if (lifts$carried.isEmpty()) return;
        var target = transform.getLevel();
        var rotation = transform.getRotation();
        var spin = rotation == null || rotation == Rotation.NONE ? null : new StructureTransform(BlockPos.ZERO, Direction.Axis.Y, rotation, Mirror.NONE);
        for (var entry : lifts$carried.entrySet()) NodePayload.load(target, entry.getKey(), entry.getValue(), spin);
        for (var entry : lifts$carried.entrySet())
            NodePayload.wire(target, entry.getKey(), entry.getValue(), pos -> lifts$moves.getOrDefault(pos, pos), spin);
        Node.Graphs.flush(target);
        lifts$carried.clear();
        lifts$moves.clear();
    }
}

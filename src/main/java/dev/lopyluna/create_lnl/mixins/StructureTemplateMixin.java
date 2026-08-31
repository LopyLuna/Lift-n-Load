package dev.lopyluna.create_lnl.mixins;

import dev.lopyluna.create_lnl.content.nodes.Node;
import dev.lopyluna.create_lnl.content.nodes.NodePayload;
import com.simibubi.create.content.contraptions.StructureTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mixin(StructureTemplate.class)
public class StructureTemplateMixin implements Node.Template {
    @Unique private final Map<BlockPos, CompoundTag> lifts$nodes = new HashMap<>();

    @Override
    public Map<BlockPos, CompoundTag> lifts$nodes() {
        return lifts$nodes;
    }

    @Inject(method = "fillFromWorld", at = @At("TAIL"))
    private void captureNodes(Level level, BlockPos pos, Vec3i size, boolean withEntities, Block toIgnore, CallbackInfo ci) {
        lifts$nodes.clear();
        var graph = Node.Graphs.get(level);
        if (graph.nodes.isEmpty()) return;
        var bounds = BoundingBox.fromCorners(pos, pos.offset(size).offset(-1, -1, -1));
        for (var node : List.copyOf(graph.nodes.keySet())) {
            if (!bounds.isInside(node)) continue;
            var tag = NodePayload.save(graph, node, bounds::isInside, link -> link.subtract(pos), false);
            if (tag != null) lifts$nodes.put(node.subtract(pos), tag);
        }
    }

    @Inject(method = "placeInWorld", at = @At("TAIL"))
    private void previewNodes(ServerLevelAccessor level, BlockPos offset, BlockPos pos, StructurePlaceSettings settings, RandomSource random, int flags, CallbackInfoReturnable<Boolean> cir) {
        if (!(level instanceof Node.Template target)) return;
        target.lifts$nodes().clear();
        if (lifts$nodes.isEmpty()) return;
        var spin = new StructureTransform(settings.getRotationPivot(), Direction.Axis.Y, settings.getRotation(), settings.getMirror());
        for (var entry : lifts$nodes.entrySet())
            target.lifts$nodes().put(StructureTemplate.calculateRelativePosition(settings, entry.getKey()).offset(offset),
                    NodePayload.rebase(entry.getValue(), local -> StructureTemplate.calculateRelativePosition(settings, local).offset(offset), spin));
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void saveNodes(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        if (lifts$nodes.isEmpty()) return;
        var list = new ListTag();
        for (var entry : lifts$nodes.entrySet()) {
            var nbt = entry.getValue().copy();
            nbt.putLong("Pos", entry.getKey().asLong());
            list.add(nbt);
        }
        cir.getReturnValue().put("LiftsNodes", list);
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void loadNodes(HolderGetter<Block> blocks, CompoundTag tag, CallbackInfo ci) {
        lifts$nodes.clear();
        var list = tag.getList("LiftsNodes", Tag.TAG_COMPOUND);
        for (var i = 0; i < list.size(); i++) {
            var nbt = list.getCompound(i);
            lifts$nodes.put(BlockPos.of(nbt.getLong("Pos")), nbt);
        }
    }
}

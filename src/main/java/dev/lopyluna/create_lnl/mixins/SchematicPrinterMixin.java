package dev.lopyluna.create_lnl.mixins;

import dev.lopyluna.create_lnl.content.nodes.Node;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.schematics.SchematicItem;
import com.simibubi.create.content.schematics.SchematicPrinter;
import com.simibubi.create.content.schematics.cannon.MaterialChecklist;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import dev.lopyluna.create_lnl.content.nodes.NodePayload;
import dev.lopyluna.create_lnl.register.LiftsItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mixin(value = SchematicPrinter.class, remap = false)
public class SchematicPrinterMixin {
    @Unique private final Map<BlockPos, Integer> lifts$plugs = new HashMap<>();

    @Inject(method = "loadSchematic", at = @At("TAIL"))
    private void stashNodes(ItemStack blueprint, Level originalWorld, boolean processNBT, CallbackInfo ci) {
        lifts$plugs.clear();
        if (originalWorld.isClientSide) return;
        var anchor = blueprint.get(AllDataComponents.SCHEMATIC_ANCHOR);
        if (anchor == null) return;
        if (!(SchematicItem.loadSchematic(originalWorld, blueprint) instanceof Node.Template template) || template.lifts$nodes().isEmpty()) return;
        var settings = SchematicItem.getSettings(blueprint, processNBT);
        var spin = new StructureTransform(settings.getRotationPivot(), Direction.Axis.Y, settings.getRotation(), settings.getMirror());
        var batch = new HashMap<BlockPos, List<Node.Schematics.Placement>>();
        for (var entry : template.lifts$nodes().entrySet()) {
            var pos = lifts$place(settings, entry.getKey(), anchor);
            var tag = NodePayload.rebase(entry.getValue(), local -> lifts$place(settings, local, anchor), spin);
            var trigger = NodePayload.trigger(pos, tag);
            batch.computeIfAbsent(trigger, key -> new ArrayList<>()).add(new Node.Schematics.Placement(pos, tag));
            lifts$plugs.merge(trigger, NodePayload.plugs(tag), Integer::sum);
        }
        Node.Schematics.stash(originalWorld, batch);
    }

    @Inject(method = "markAllBlockRequirements", at = @At("TAIL"))
    private void nodeChecklist(MaterialChecklist checklist, Level world, SchematicPrinter.PlacementPredicate predicate, CallbackInfoReturnable<Integer> cir) {
        for (var plugs : lifts$plugs.values())
            checklist.require(new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, new ItemStack(LiftsItems.NODE_PLUG.get(), plugs)));
    }

    @Inject(method = "getCurrentRequirement", at = @At("RETURN"), cancellable = true)
    private void nodeRequirement(CallbackInfoReturnable<ItemRequirement> cir) {
        if (lifts$plugs.isEmpty()) return;
        var requirement = cir.getReturnValue();
        if (requirement.isInvalid()) return;
        var target = ((SchematicPrinter) (Object) this).getCurrentTarget();
        if (target == null) return;
        var plugs = lifts$plugs.getOrDefault(target, 0);
        if (plugs <= 0) return;
        cir.setReturnValue(requirement.union(new ItemRequirement(ItemRequirement.ItemUseType.CONSUME,
                new ItemStack(LiftsItems.NODE_PLUG.get(), plugs))));
    }

    @Unique
    private static BlockPos lifts$place(StructurePlaceSettings settings, BlockPos local, BlockPos anchor) {
        return StructureTemplate.calculateRelativePosition(settings, local).offset(anchor);
    }
}

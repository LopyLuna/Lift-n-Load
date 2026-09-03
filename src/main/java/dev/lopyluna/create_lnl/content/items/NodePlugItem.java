package dev.lopyluna.create_lnl.content.items;

import dev.lopyluna.create_lnl.content.nodes.loose.LooseNodes;
import dev.lopyluna.create_lnl.content.nodes.loose.NodeCell;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class NodePlugItem extends Item {
    public NodePlugItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        var level = ctx.getLevel();
        var face = ctx.getClickedFace();
        var hit = ctx.getClickLocation();
        var clicked = ctx.getClickedPos();
        var target = target(level, clicked, face, hit);
        if (target == null) target = target(level, clicked.relative(face), face, hit);
        if (target == null) return InteractionResult.PASS;
        if (!level.isClientSide) {
            if (!LooseNodes.place(level, target.pos(), target.id())) return InteractionResult.PASS;
            var player = ctx.getPlayer();
            if (player == null || !player.hasInfiniteMaterials()) ctx.getItemInHand().shrink(1);
            level.playSound(null, target.pos(), SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 1f, 1.6f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static Target target(Level level, BlockPos pos, net.minecraft.core.Direction face, net.minecraft.world.phys.Vec3 hit) {
        var id = NodeCell.of(pos, face, hit);
        if (!LooseNodes.supported(level, pos, id) || !LooseNodes.vacant(level, pos, id)) return null;
        return new Target(pos, id);
    }

    private record Target(BlockPos pos, int id) {}
}

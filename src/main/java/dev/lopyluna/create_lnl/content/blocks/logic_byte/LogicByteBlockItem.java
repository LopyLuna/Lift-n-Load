package dev.lopyluna.create_lnl.content.blocks.logic_byte;

import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

@SuppressWarnings("deprecation")
public class LogicByteBlockItem extends BlockItem {
    public LogicByteBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        var level = ctx.getLevel();
        var pos = ctx.getClickedPos().relative(ctx.getClickedFace());
        if (!(level.getBlockEntity(pos) instanceof LogicByteBE be)) return super.useOn(ctx);
        var slot = LogicSlot.at(pos, ctx.getClickLocation(), ctx.getClickedFace(), true);
        if (slot == null || be.has(slot) || !LogicByteBlock.fits(level, pos, be.getBlockState(), slot)) return super.useOn(ctx);
        var player = ctx.getPlayer();
        if (!level.isClientSide) {
            be.add(slot, player == null ? ctx.getHorizontalDirection().getOpposite() : player.getDirection().getOpposite());
            if (player == null || !player.isCreative()) ctx.getItemInHand().shrink(1);
            level.playSound(null, pos, be.getBlockState().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1f, 0.8f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext ctx, BlockState state) {
        if (!super.placeBlock(ctx, state)) return false;
        if (!(ctx.getLevel().getBlockEntity(ctx.getClickedPos()) instanceof LogicByteBE be)) return true;
        be.add(LogicSlot.of(ctx.getClickedPos(), ctx.getClickLocation(), ctx.getClickedFace(), true), ctx.getHorizontalDirection().getOpposite());
        return true;
    }
}

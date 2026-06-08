package dev.lopyluna.create_lnl.content.blocks.overwrite;

import dev.lopyluna.create_lnl.content.blocks.wheel.WheelBE;
import dev.lopyluna.create_lnl.register.LiftsBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Map;

@SuppressWarnings("NullableProblems")
public class TireBlockItem extends BlockItem {
    public WheelBE.WheelType type;
    public boolean sticky;
    public TireBlockItem(Properties properties, WheelBE.WheelType type, boolean sticky) {
        super(LiftsBlocks.WHEEL.get(), properties);
        this.type = type;
        this.sticky = sticky;
    }

    @Override
    public String getDescriptionId() {
        return this.getOrCreateDescriptionId();
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player, ItemStack stack, BlockState state) {
        var bool = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        if (level.getBlockEntity(pos) instanceof WheelBE be) {
            be.radius = type.radius();
            be.type = type;
            be.sticky = sticky;
        }
        return bool;
    }

    @Override public void registerBlocks(Map<Block, Item> blockToItemMap, Item item) {}
}

package dev.lopyluna.create_lnl.content.blocks.lift;

import com.simibubi.create.AllSoundEvents;
import dev.lopyluna.create_lnl.register.LiftsDataComponents;
import dev.ryanhcode.sable.Sable;
import dev.simulated_team.simulated.index.SimSoundEvents;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public class LiftBlockItem extends BlockItem {
    public LiftBlockItem(Block block, Properties properties) {
        super(block, properties.fireResistant());
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getPlayer() instanceof Player player && !(player instanceof FakePlayer)) {
            var level = ctx.getLevel();
            var stack = ctx.getItemInHand();
            if (player.isShiftKeyDown()) {
                onPlaced(player, level, null);
                player.getCooldowns().addCooldown(this, 10);
                stack.remove(LiftsDataComponents.SUBLEVEL_UUID);
            } else {
                final var loc = ctx.getClickLocation();
                final var subLevel = Sable.HELPER.getContaining(level, loc);
                if (subLevel == null) return super.useOn(ctx);
                var uuid = subLevel.getUniqueId();
                stack.set(LiftsDataComponents.SUBLEVEL_UUID, uuid);
                AllSoundEvents.CONFIRM.play(level, player, player.blockPosition());
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useOn(ctx);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(LiftsDataComponents.SUBLEVEL_UUID);
    }

    public @NotNull InteractionResult place(BlockPlaceContext ctx) {
        if (!this.getBlock().isEnabled(ctx.getLevel().enabledFeatures()) || !ctx.canPlace()) return InteractionResult.FAIL;
        var placeCtx = this.updatePlacementContext(ctx);
        if (placeCtx == null) return InteractionResult.FAIL;
        var placeState = this.getPlacementState(placeCtx);
        if (placeState == null || !this.placeBlock(placeCtx, placeState)) return InteractionResult.FAIL;


        var pos = placeCtx.getClickedPos();
        var level = placeCtx.getLevel();
        var player = placeCtx.getPlayer();
        var stack = placeCtx.getItemInHand();
        var state = level.getBlockState(pos);

        if (state.is(placeState.getBlock())) {
            state = updateBlockStateFromTag(pos, level, stack, state);
            updateCustomBlockEntityTag(pos, level, player, stack, state);
            updateBlockEntityComponents(level, pos, stack);
            state.getBlock().setPlacedBy(level, pos, state, player, stack);
            if (player instanceof ServerPlayer serverPlayer) CriteriaTriggers.PLACED_BLOCK.trigger(serverPlayer, pos, stack);
        }


        var sound = state.getSoundType(level, pos, player);
        var volume = (sound.getVolume() + 1.0F) / 2.0F;
        var pitch = sound.getPitch() * 0.8F;

        level.playSound(player, pos, getPlaceSound(state, level, pos, player), SoundSource.BLOCKS, volume, pitch);
        level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, state));
        if (player != null) {
            onPlaced(player, level, pos);
            LiftBlock.placedBy(level, stack, pos, player);
            player.getCooldowns().addCooldown(this, 10);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static void onPlaced(Player player, Level level, @Nullable BlockPos pos) {
        var placed = getPlaced(player);
        if (placed != null && level.getBlockState(placed).getBlock() instanceof LiftBlock) {
            level.removeBlock(placed, false);
            if (pos == null) level.playSound(player, placed, SimSoundEvents.ASSEMBLER_SHIFT.event(), SoundSource.PLAYERS, 0.2f, 0.85f);
        }
        setPlaced(player, pos);
    }

    public static void setPlaced(Player player, @Nullable BlockPos pos) {
        if (pos == null) player.getPersistentData().remove("LiftPos");
        else player.getPersistentData().put("LiftPos", NbtUtils.writeBlockPos(pos));
    }

    public static BlockPos getPlaced(Player player) {
        if (!player.getPersistentData().contains("LiftPos")) return null;
        return NBTHelper.readBlockPos(player.getPersistentData(), "LiftPos");
    }

    @Override
    protected @NotNull SoundEvent getPlaceSound(BlockState state, @NotNull Level level, @NotNull BlockPos pos, @Nullable Player player) {
        return state.getSoundType(level, pos, player).getPlaceSound();
    }

    private BlockState updateBlockStateFromTag(BlockPos pos, Level level, ItemStack stack, BlockState state) {
        var properties = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
        if (properties.isEmpty()) return state;
        var blockstate = properties.apply(state);
        if (!blockstate.equals(state)) level.setBlock(pos, blockstate, 2);
        return blockstate;
    }

    private static void updateBlockEntityComponents(Level level, BlockPos pos, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof BlockEntity be) {
            be.applyComponentsFromItemStack(stack);
            be.setChanged();
        }
    }
}

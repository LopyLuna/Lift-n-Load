package dev.lopyluna.create_lnl.content.blocks.thruster;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.block.IBE;
import dev.lopyluna.create_lnl.register.LiftShapes;
import dev.lopyluna.create_lnl.register.LiftsBETypes;
import dev.lopyluna.create_lnl.register.LiftsBlocks;
import dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlockEntity;
import dev.simulated_team.simulated.multiloader.inventory.ItemInfoWrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidUtil;

@SuppressWarnings("NullableProblems")
public class ThrusterBlock extends Block implements IBE<ThrusterBE> {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public ThrusterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(final ItemStack stack, final BlockState blockState, final Level level, final BlockPos blockPos, final Player player, final InteractionHand interactionHand, final BlockHitResult blockHitResult) {
        if (!(level.getBlockEntity(blockPos) instanceof ThrusterBE be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (stack.isEmpty()) {
            final var inventory = be.inventory;
            final var slot = inventory.slot;
            final var currentItemStack = slot.getStack().copy();
            if (currentItemStack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

            if (!level.isClientSide) {
                slot.setStack(ItemStack.EMPTY);
                player.getInventory().placeItemBackInInventory(currentItemStack);
                be.notifyUpdate();
            }
            level.playSound(null, blockPos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, .2f, 1f + level.getRandom().nextFloat());
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (AllItems.CREATIVE_BLAZE_CAKE.isIn(stack)) {
            if (!level.isClientSide) {
                if (be.burnTime >= PortableEngineBlockEntity.INFINITE_THRESHOLD) {
                    if (be.superHeated) {
                        be.burnTime = 0;
                        be.superHeated = false;
                    } else be.superHeated = true;
                } else be.burnTime = PortableEngineBlockEntity.INFINITE_THRESHOLD;
            }
            if (!player.hasInfiniteMaterials()) {
                stack.shrink(1);
                player.setItemInHand(interactionHand, stack);
            }
            if (!level.isClientSide) be.notifyUpdate();
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        if (be.tank.isEmpty() && be.inventory.isEmpty()) {
            ItemInteractionResult[] flag = {ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION};
            var opt = FluidUtil.getFluidHandler(stack);
            opt.ifPresent(i -> {
                var result = FluidUtil.tryEmptyContainer(stack, be.tank, be.tank.getSpace(), player, true);
                if (!level.isClientSide && result.success) {
                    stack.shrink(1);
                    be.notifyUpdate();
                }
                if (!result.result.isEmpty()) player.getInventory().placeItemBackInInventory(result.result);
                flag[0] = result.success ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.FAIL;
            });
            if (flag[0] != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) return flag[0];

            if (!stack.isEmpty() && !be.inventory.canInsertItem(ItemInfoWrapper.generateFromStack(stack))) return ItemInteractionResult.FAIL;

            if (!level.isClientSide) {
                be.inventory.slot.setStack(stack.copy());
                player.setItemInHand(interactionHand, ItemStack.EMPTY);
                be.notifyUpdate();
            }
            level.playSound(null, blockPos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, .2f, 1f + level.getRandom().nextFloat());
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        if (be.tank.getSpace() <= 0) {
            ItemInteractionResult[] flag = {ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION};
            var opt = FluidUtil.getFluidHandler(stack);
            opt.ifPresent(i -> {
                var space = i.getTankCapacity(0) - i.getFluidInTank(0).getAmount();
                var result = FluidUtil.tryFillContainer(stack, be.tank, space, player, true);
                if (!level.isClientSide && result.success) {
                    stack.shrink(1);
                    be.notifyUpdate();
                }
                if (!result.result.isEmpty()) player.getInventory().placeItemBackInInventory(result.result);
                flag[0] = result.success ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.FAIL;
            });
            if (flag[0] != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) return flag[0];
        } else if (be.inventory.isEmpty()) {
            ItemInteractionResult[] flag = {ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION};
            var opt = FluidUtil.getFluidHandler(stack);
            opt.ifPresent(i -> {
                var result = FluidUtil.tryEmptyContainer(stack, be.tank, be.tank.getSpace(), player, true);
                if (!level.isClientSide && result.success) {
                    stack.shrink(1);
                    be.notifyUpdate();
                }
                if (!result.result.isEmpty()) player.getInventory().placeItemBackInInventory(result.result);
                flag[0] = result.success ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.FAIL;
            });
            if (flag[0] != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) return flag[0];
        }

        if (!be.tank.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        final var currentItemStack = be.inventory.slot.getStack().copy();
        if (ItemStack.isSameItem(stack, currentItemStack) && ItemStack.isSameItemSameComponents(stack, currentItemStack)) {
            int targetAmount = currentItemStack.getCount() + stack.getCount();
            targetAmount = Math.min(targetAmount, currentItemStack.getMaxStackSize());
            final int transferAmount = Math.min(targetAmount - currentItemStack.getCount(), stack.getCount());

            if (transferAmount <= 0) return ItemInteractionResult.sidedSuccess(level.isClientSide);

            if (!level.isClientSide) {
                be.inventory.slot.shrink(-transferAmount);
                stack.shrink(transferAmount);
                player.setItemInHand(interactionHand, stack);
                be.notifyUpdate();
            }
            level.playSound(null, blockPos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, .2f, 1f + level.getRandom().nextFloat());
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        //final DyeColor color = SimItemService.getDyeColor(stack);
        //if (color != null) {
        //    if (!level.isClientSide) level.playSound(null, blockPos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0f, 1.1f - level.random.nextFloat() * .2f);

        //    final BlockState newState = BlockHelper.copyProperties(blockState, SimBlocks.PORTABLE_ENGINES.get(color).getDefaultState());
        //    level.setBlockAndUpdate(blockPos, newState);

        //    return ItemInteractionResult.SUCCESS;
        //}
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        var dir = state.getValue(FACING);
        return LiftShapes.THRUSTER_HEAD.get(dir.getAxis().isVertical() ? dir : dir.getOpposite());
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ThrusterBE be)) return 0;
        return Mth.clamp(Math.round(be.intensity.getValue() * 15f), 0, 15);
    }

    @Override
    public boolean hasAnalogOutputSignal(final BlockState pState) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(final BlockState pState, final Level pLevel, final BlockPos pPos) {
        if (!(pLevel.getBlockEntity(pPos) instanceof ThrusterBE be)) return 0;
        int power = 0;
        final int ticks = Math.round(be.getTotalBurnTime());
        if (ticks > 0) power = Math.min(ticks/200, 14) + 1;
        return power;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        var player = context.getPlayer();
        var preferred = player == null ? context.getClickedFace().getOpposite() : context.getNearestLookingDirection();
        return defaultBlockState().setValue(FACING, player != null && player.isShiftKeyDown() ? preferred : preferred.getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(FACING));
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirrorIn) {
        return state.rotate(mirrorIn.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        var dir = state.getValue(FACING);
        var structurePos = pos.relative(dir);
        var occupiedState = level.getBlockState(structurePos);
        var requiredStructure = LiftsBlocks.THRUSTER_STRUCTURAL.getDefaultState().setValue(ThrusterStructureBlock.FACING, dir.getOpposite());
        if (occupiedState == requiredStructure) return;
        if (!occupiedState.canBeReplaced()) {
            level.destroyBlock(pos, false);
            return;
        }
        level.setBlockAndUpdate(structurePos, requiredStructure);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (!level.getBlockState(pos.relative(state.getValue(FACING))).canBeReplaced()) return false;
        return super.canSurvive(state, level, pos);
    }

    @Override
    public Class<ThrusterBE> getBlockEntityClass() {
        return ThrusterBE.class;
    }

    @Override
    public BlockEntityType<? extends ThrusterBE> getBlockEntityType() {
        return LiftsBETypes.THRUSTER.get();
    }

    @Override
    public void onRemove(final BlockState state, final Level level, final BlockPos pos, final BlockState newState, final boolean isMoving) {
        if (level.getBlockEntity(pos) instanceof ThrusterBE be) {
            if (!be.inventory.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), be.inventory.getItem(0));
            level.removeBlockEntity(pos);
        }
    }
}

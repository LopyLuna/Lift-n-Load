package dev.lopyluna.create_lnl.content.blocks.node_link;

import com.simibubi.create.AllShapes;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import dev.lopyluna.create_lnl.register.LiftsBETypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FastColor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

@SuppressWarnings("NullableProblems")
public class NodeLinkBlock extends WrenchableDirectionalBlock implements IBE<NodeLinkBE> {
    public static final BooleanProperty RECEIVER = BooleanProperty.create("receiver");

    public NodeLinkBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(RECEIVER, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getClickedFace());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof NodeLinkBE be) {
            if (stack.getItem() instanceof DyeItem dye) {
                var old = be.clr;
                be.clr = FastColor.ARGB32.average(dye.getDyeColor().getMapColor().col, dye.getDyeColor().getTextColor());
                if (old != be.clr) {
                    player.playSound(SoundEvents.DYE_USE);
                    be.updateConnection(be);
                    return ItemInteractionResult.SUCCESS;
                }
            } else if (stack.is(Items.WET_SPONGE) || stack.is(Items.SPONGE)) {
                var old = be.clr;
                be.clr = -1;
                if (old != be.clr) {
                    player.playSound(SoundEvents.SPONGE_ABSORB);
                    be.updateConnection(be);
                    return ItemInteractionResult.SUCCESS;
                }
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player.getMainHandItem().isEmpty()) {
            if (player.isShiftKeyDown()) {
                var newState = state.setValue(RECEIVER, !state.getValue(RECEIVER));
                if (!newState.canSurvive(level, pos)) return InteractionResult.PASS;
                KineticBlockEntity.switchToBlockState(level, pos, Block.updateFromNeighbourShapes(newState, level, pos));
                if (level.getBlockState(pos) != state) AllSoundEvents.WRENCH_ROTATE.playOnServer(level, pos, 1, level.random.nextFloat() + .5f);
                return InteractionResult.SUCCESS;
            } else if (level.getBlockEntity(pos) instanceof NodeLinkBE be && be.receiver) {
                be.invert = !be.invert;
                level.updateNeighborsAt(pos, this);
                AllSoundEvents.CRAFTER_CLICK.playOnServer(level, pos, 1, level.random.nextFloat() + .5f);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return !level.getBlockState(pos.relative(state.getValue(FACING).getOpposite())).canBeReplaced();
    }

    @Override
    public void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
        super.onNeighborChange(state, level, pos, neighbor);
        if (level.getBlockEntity(pos) instanceof NodeLinkBE be) be.update(state);
    }
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (level.isClientSide) return;
        if (level.getBlockEntity(pos) instanceof NodeLinkBE be) be.update(state);
    }


    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof NodeLinkBE be) be.update(state);
        return !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return state.getValue(RECEIVER);
    }

    @Override
    public int getDirectSignal(BlockState blockState, BlockGetter blockAccess, BlockPos pos, Direction side) {
        if (side != blockState.getValue(FACING)) return 0;
        return getSignal(blockState, blockAccess, pos, side);
    }

    @Override
    public int getSignal(BlockState state, BlockGetter blockAccess, BlockPos pos, Direction side) {
        if (!state.getValue(RECEIVER)) return 0;
        return getBlockEntityOptional(blockAccess, pos).map(NodeLinkBE::getStrength).orElse(0);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RECEIVER);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter world, BlockPos pos, Direction side) {
        return side != null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AllShapes.REDSTONE_BRIDGE.get(state.getValue(FACING));
    }

    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        return originalState.setValue(RECEIVER, !originalState.getValue(RECEIVER));
    }

    @Override
    public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pMovedByPiston) {
        IBE.onRemove(pState, pLevel, pPos, pNewState);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    public Class<NodeLinkBE> getBlockEntityClass() {
        return NodeLinkBE.class;
    }

    @Override
    public BlockEntityType<? extends NodeLinkBE> getBlockEntityType() {
        return LiftsBETypes.NODE_LINK.get();
    }
}

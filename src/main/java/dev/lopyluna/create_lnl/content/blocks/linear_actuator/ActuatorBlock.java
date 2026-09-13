package dev.lopyluna.create_lnl.content.blocks.linear_actuator;

import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.impl.contraption.BlockMovementChecksImpl;
import dev.lopyluna.create_lnl.register.LiftShapes;
import dev.lopyluna.create_lnl.register.LiftsBETypes;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

@SuppressWarnings({"NullableProblems"})
public class ActuatorBlock extends DirectionalAxisKineticBlock implements IBE<ActuatorBE>, BlockSubLevelAssemblyListener {
    public static final int MAX_LENGTH = 16;

    public static final VoxelShaper SHAPE = LiftShapes.shape(0, 0, 0, 2, 8, 16)
            .add(14, 0, 0, 16, 8, 16).add(2, 0, 0, 14, 2, 16)
            .add(2, 2, 2, 14, 16, 14).forDirectional(Direction.UP);
    public static final VoxelShaper SHAPE_ROTATED = LiftShapes.shape(0, 0, 0, 16, 8, 2)
            .add(0, 0, 14, 16, 8, 16).add(0, 0, 2, 16, 2, 14)
            .add(2, 2, 2, 14, 16, 14).forDirectional(Direction.UP);
    public static final VoxelShaper HEAD_SHAPE = LiftShapes.shape(0, 8, 0, 2, 16, 16)
            .add(14, 8, 0, 16, 16, 16).add(2, 14, 0, 14, 16, 16)
            .add(2, 0, 2, 14, 14, 14).forDirectional(Direction.UP);
    public static final VoxelShaper HEAD_SHAPE_ROTATED = LiftShapes.shape(0, 8, 0, 16, 16, 2)
            .add(0, 8, 14, 16, 16, 16).add(0, 14, 2, 16, 16, 14)
            .add(2, 0, 2, 14, 14, 14).forDirectional(Direction.UP);

    public static final BooleanProperty HEAD = BooleanProperty.create("head");
    public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    static {
        BlockMovementChecksImpl.registerAttachedCheck((state, level, pos, direction) -> {
            if (!(state.getBlock() instanceof ActuatorBlock)) return BlockMovementChecks.CheckResult.PASS;
            var facing = state.getValue(FACING);
            var head = state.getValue(HEAD);
            if (direction != (head ? facing.getOpposite() : facing)) return BlockMovementChecks.CheckResult.PASS;
            var found = level.getBlockState(pos.relative(direction));
            if (!(found.getBlock() instanceof ActuatorBlock)) return BlockMovementChecks.CheckResult.PASS;
            if (found.getValue(HEAD) == head || found.getValue(FACING) != facing) return BlockMovementChecks.CheckResult.PASS;
            return BlockMovementChecks.CheckResult.SUCCESS;
        });
    }

    public ActuatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(HEAD, false).setValue(ASSEMBLED, false).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(HEAD, ASSEMBLED, POWERED));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        var state = super.getStateForPlacement(context);
        if (state == null) return null;
        var level = context.getLevel();
        var at = context.getClickedPos().relative(state.getValue(FACING));
        if (!level.isInWorldBounds(at)) return null;
        return level.getBlockState(at).canBeReplaced(context) ? state : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (state.getValue(HEAD)) return;
        var at = pos.relative(state.getValue(FACING));
        level.setBlock(at, state.setValue(HEAD, true), 3);
        withBlockEntityDo(level, at, be -> be.setBasePos(pos));
    }

    @Override
    public void beforeMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState, BlockPos oldPos, BlockPos newPos) {
        withBlockEntityDo(originLevel, oldPos, ActuatorBE::beforeAssembly);
    }

    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState, BlockPos oldPos, BlockPos newPos) {
        withBlockEntityDo(resultingLevel, newPos, ActuatorBE::fixLinkingWhenMoved);
    }

    @Nullable
    public static BlockPos partner(BlockGetter level, BlockPos pos, BlockState state) {
        var facing = state.getValue(FACING);
        boolean head = state.getValue(HEAD);
        var search = head ? facing.getOpposite() : facing;
        for (var i = 1; i <= MAX_LENGTH; i++) {
            var at = pos.relative(search, i);
            var found = level.getBlockState(at);
            if (found.isAir()) continue;
            if (found.getBlock() instanceof ActuatorBlock && found.getValue(HEAD) != head && found.getValue(FACING) == facing) return at;
            break;
        }
        return null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!player.mayBuild()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (player.isShiftKeyDown()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!stack.isEmpty()) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        var at = pos;
        if (state.getValue(HEAD)) {
            at = partner(level, pos, state);
            if (at == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        withBlockEntityDo(level, at, be -> be.assembleNextTick = true);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext ctx) {
        var level = ctx.getLevel();
        var clicked = ctx.getClickedPos();
        var basePos = state.getValue(HEAD) ? partner(level, clicked, state) : clicked;
        if (basePos == null) return InteractionResult.PASS;
        if (!getRotatedBlockState(level.getBlockState(basePos), ctx.getClickedFace()).canSurvive(level, basePos)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.sidedSuccess(true);

        withBlockEntityDo(level, basePos, ActuatorBE::disassemble);
        var base = level.getBlockState(basePos);
        if (!(base.getBlock() instanceof ActuatorBlock)) return InteractionResult.PASS;

        var rotated = getRotatedBlockState(base, ctx.getClickedFace());
        var facing = rotated.getValue(FACING);
        var headPos = partner(level, basePos, base);
        var target = basePos.relative(facing);
        var moving = headPos != null && !headPos.equals(target);
        if (moving && !level.getBlockState(target).canBeReplaced()) return InteractionResult.PASS;

        var headState = headPos == null ? null : level.getBlockState(headPos)
                .setValue(FACING, facing)
                .setValue(AXIS_ALONG_FIRST_COORDINATE, rotated.getValue(AXIS_ALONG_FIRST_COORDINATE));
        if (moving) {
            withBlockEntityDo(level, headPos, ActuatorBE::beforeAssembly);
            level.setBlock(headPos, Blocks.AIR.defaultBlockState(), 2);
        }

        KineticBlockEntity.switchToBlockState(level, basePos, rotated);
        if (headState != null) {
            if (moving) level.setBlock(target, headState, 3);
            else KineticBlockEntity.switchToBlockState(level, target, headState);
            withBlockEntityDo(level, target, be -> be.setBasePos(basePos));
        }

        IWrenchable.playRotateSound(level, basePos);
        return InteractionResult.sidedSuccess(false);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        var facing = state.getValue(FACING);
        var rotated = (facing.getAxis() == Direction.Axis.X) ^ state.getValue(AXIS_ALONG_FIRST_COORDINATE);
        return (state.getValue(HEAD) ? rotated ? HEAD_SHAPE_ROTATED : HEAD_SHAPE : rotated ? SHAPE_ROTATED : SHAPE).get(facing);
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.block();
    }

    @Override
    public Class<ActuatorBE> getBlockEntityClass() {
        return ActuatorBE.class;
    }

    @Override
    public BlockEntityType<? extends ActuatorBE> getBlockEntityType() {
        return LiftsBETypes.LINEAR_ACTUATOR.get();
    }
}

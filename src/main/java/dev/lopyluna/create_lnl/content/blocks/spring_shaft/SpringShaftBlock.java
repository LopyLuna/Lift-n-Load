package dev.lopyluna.create_lnl.content.blocks.spring_shaft;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.impl.contraption.BlockMovementChecksImpl;
import dev.lopyluna.create_lnl.content.blocks.IBlockFromHandInteraction;
import dev.lopyluna.create_lnl.register.LiftsBETypes;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.simulated_team.simulated.content.blocks.spring.SpringBlock;
import dev.simulated_team.simulated.data.SimLang;
import dev.simulated_team.simulated.index.SimBlockShapes;
import dev.simulated_team.simulated.index.SimTags;
import dev.simulated_team.simulated.util.SimColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

@SuppressWarnings("NullableProblems")
public class SpringShaftBlock extends DirectionalKineticBlock implements IBE<SpringShaftBE>, BlockSubLevelAssemblyListener, IWrenchable, IBlockFromHandInteraction {
    public static final EnumProperty<SpringBlock.Size> SIZE = SpringBlock.SIZE;

    static {
        BlockMovementChecksImpl.registerAttachedCheck((state, level, pos, direction) -> {
            var block = state.getBlock();
            if (block instanceof SpringShaftBlock) return direction.getOpposite() == state.getValue(FACING) ? BlockMovementChecks.CheckResult.SUCCESS : BlockMovementChecks.CheckResult.FAIL;
            return BlockMovementChecks.CheckResult.PASS;
        });
    }

    public SpringShaftBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SIZE, SpringBlock.Size.MEDIUM));
    }

    @Override
    public InteractionResult onWrenched(final BlockState state, final UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();

        var be = getBlockEntity(level, pos);
        if (be == null) return InteractionResult.SUCCESS;

        var partner = be.getPairedBE();
        if (partner == null) return InteractionResult.SUCCESS;

        var partnerState = partner.getBlockState();
        var partnerPos = partner.getBlockPos();
        var newSize = state.getValue(SIZE).cycle();

        var newState = state.setValue(SIZE, newSize);
        var newPartnerState = partnerState.setValue(SIZE, newSize);

        be.update(newState);
        partner.update(newPartnerState);

        level.setBlockAndUpdate(pos, newState);
        level.setBlockAndUpdate(partnerPos, newPartnerState);
        AllSoundEvents.WRENCH_ROTATE.playOnServer(level, pos, 1, level.random.nextFloat() + .5f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult rightClickBlock(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.is(SimTags.Items.SPRING_ADJUSTER)) {
            if (level.getBlockEntity(pos) instanceof final SpringShaftBE spring) {
                final String error = spring.tryChangeLengthOrError(level, player.isShiftKeyDown() ? -0.25f : 0.25f);
                if (error == null) {
                    SimLang.translate("spring.new_length", String.format("%.2f", spring.desiredLength)).color(SimColors.SUCCESS_LIME).sendStatus(player);
                    return InteractionResult.SUCCESS;
                }
                SimLang.translate("spring." + error, String.format("%.2f", spring.desiredLength)).color(SimColors.NUH_UH_RED).sendStatus(player);
            }
            return InteractionResult.FAIL;
        }
        return null;
    }

    @Override
    public void beforeMove(final ServerLevel originLevel, final ServerLevel newLevel, final BlockState newState, final BlockPos oldPos, final BlockPos newPos) {
        if (newLevel.getBlockEntity(oldPos) instanceof final SpringShaftBE spring) spring.assembling = true;
    }

    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel newLevel, BlockState newState, BlockPos oldPos, BlockPos newPos) {
        if (newLevel.getBlockEntity(newPos) instanceof SpringShaftBE spring) {
            var partner = spring.getPairedBE();
            if (partner == null) return;
            var subLevel = Sable.HELPER.getContaining(newLevel, newPos);
            partner.setPartner(newPos, subLevel != null ? subLevel.getUniqueId() : null);
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(SIZE)) {
            case SMALL -> SimBlockShapes.SMALL_SPRING.get(state.getValue(FACING));
            case MEDIUM -> SimBlockShapes.SPRING.get(state.getValue(FACING));
            case LARGE -> SimBlockShapes.LARGE_SPRING.get(state.getValue(FACING));
        };
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite();
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(SIZE));
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return canAttach(level, pos, state.getValue(FACING).getOpposite());
    }

    public static boolean canAttach(LevelReader level, BlockPos pos, Direction dir) {
        var rel = pos.relative(dir);
        var relState = level.getBlockState(rel);
        return relState.isFaceSturdy(level, rel, dir.getOpposite()) ||  (relState.getBlock() instanceof IRotate rot && rot.hasShaftTowards(level, rel, relState, dir.getOpposite()));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(FACING).getOpposite() == direction && !state.canSurvive(level, pos)) return Blocks.AIR.defaultBlockState();
        if (level.getBlockEntity(pos) instanceof SpringShaftBE be) be.update(state);
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
        super.onNeighborChange(state, level, pos, neighbor);
        if (level.getBlockEntity(pos) instanceof SpringShaftBE be) be.update(state);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (level.getBlockEntity(pos) instanceof SpringShaftBE be) be.update(state);
    }

    @Override
    public Class<SpringShaftBE> getBlockEntityClass() {
        return SpringShaftBE.class;
    }

    @Override
    public BlockEntityType<? extends SpringShaftBE> getBlockEntityType() {
        return LiftsBETypes.SPRING_SHAFT.get();
    }
}

package dev.lopyluna.create_lnl.content.blocks.thruster;

import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.render.MultiPosDestructionHandler;
import com.simibubi.create.impl.contraption.BlockMovementChecksImpl;
import dev.lopyluna.create_lnl.register.LiftShapes;
import dev.lopyluna.create_lnl.register.LiftsBlocks;
import net.createmod.catnip.data.Pair;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

@SuppressWarnings("NullableProblems")
public class ThrusterStructureBlock extends Block implements IWrenchable, IProxyHoveringInformation {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public ThrusterStructureBlock(Properties properties) {
        super(properties);
    }


    static {
        BlockMovementChecksImpl.registerAttachedCheck((state, level, pos, direction) -> {
            var block = state.getBlock();
            var thruster = block instanceof ThrusterBlock;
            var struct = block instanceof ThrusterStructureBlock;
            if (!(thruster || struct)) return BlockMovementChecks.CheckResult.PASS;
            final var facing = state.getValue(FACING);
            if (facing != direction) return BlockMovementChecks.CheckResult.PASS;

            var rel = pos.relative(facing);
            var rState = level.getBlockState(rel);
            var rBlock = rState.getBlock();

            var rThruster = rBlock instanceof ThrusterBlock;
            var rStruct = rBlock instanceof ThrusterStructureBlock;

            if (!(thruster == rStruct || rThruster == struct)) return BlockMovementChecks.CheckResult.PASS;

            final var rFacing = rState.getValue(FACING);
            if (rFacing.getOpposite() != direction) return BlockMovementChecks.CheckResult.PASS;

            return BlockMovementChecks.CheckResult.SUCCESS;
        }
        );
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return LiftShapes.THRUSTER_NOZZLE.get(state.getValue(FACING));
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState pState) {
        return PushReaction.BLOCK;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return LiftsBlocks.THRUSTER.asStack();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(FACING));
    }

    @Override
    public boolean hasAnalogOutputSignal(final BlockState pState) {
        return true;
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        var clickedPos = context.getClickedPos();
        var level = context.getLevel();
        var master = getMaster(level, clickedPos, state);
        if (!master.getFirst().isEmpty()) {
            var masterPos = master.getSecond();
            context = new UseOnContext(level, context.getPlayer(), context.getHand(), context.getItemInHand(), new BlockHitResult(context.getClickLocation(), context.getClickedFace(), masterPos, context.isInside()));
            state = level.getBlockState(masterPos);
        }
        return IWrenchable.super.onSneakWrenched(state, context);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) level.scheduleTick(pos, this, 1);
        return super.useWithoutItem(state, level, pos, player, hitResult);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        var master = getMaster(level, pos, state);
        if (master.getFirst().isEmpty()) return ItemInteractionResult.FAIL;
        return master.getFirst().getBlock() instanceof ThrusterBlock thruster ? thruster.useItemOn(stack, master.getFirst(), level, master.getSecond(), player, hand, hitResult) : ItemInteractionResult.FAIL;
    }

    @Override
    public int getAnalogOutputSignal(final BlockState pState, final Level pLevel, final BlockPos pPos) {
        var masterState = pLevel.getBlockState(pPos.relative(pState.getValue(FACING)));
        return masterState.getAnalogOutputSignal(pLevel, pPos);
    }

    @Override
    public BlockState updateShape(BlockState pState, Direction pFacing, BlockState pFacingState, LevelAccessor pLevel, BlockPos pCurrentPos, BlockPos pFacingPos) {
        var master = getMaster(pLevel, pCurrentPos, pState);
        if (!master.getFirst().isEmpty()) {
            BlockPos masterPos = master.getSecond();
            if (!pLevel.getBlockTicks().hasScheduledTick(masterPos, LiftsBlocks.THRUSTER.get())) pLevel.scheduleTick(masterPos, LiftsBlocks.THRUSTER.get(), 1);
            return pState;
        }
        if (!(pLevel instanceof Level level) || level.isClientSide()) return pState;
        if (!level.getBlockTicks().hasScheduledTick(pCurrentPos, this)) level.scheduleTick(pCurrentPos, this, 1);
        return pState;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos n, boolean moving) {
        super.neighborChanged(state, level, pos, block, n, moving);
        if (level.getGameTime() % 20 == 0 && !level.getBlockTicks().hasScheduledTick(pos, this)) level.scheduleTick(pos, this, 1);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        super.stepOn(level, pos, state, entity);
        if (level.getGameTime() % 20 == 0 && !level.getBlockTicks().hasScheduledTick(pos, this)) level.scheduleTick(pos, this, 1);
    }

    @Override
    public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
        var master = getMaster(pLevel, pPos, pState);
        if (!master.getFirst().isEmpty()) {
            if (!pIsMoving) pLevel.destroyBlock(master.getSecond(), true);
            else pLevel.setBlockAndUpdate(master.getSecond(), Blocks.AIR.defaultBlockState());
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        var master = getMaster(level, pos, state);
        if (master.getFirst().isEmpty()) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    public BlockState playerWillDestroy(Level pLevel, BlockPos pPos, BlockState pState, Player pPlayer) {
        var master = getMaster(pLevel, pPos, pState);
        if (!master.getFirst().isEmpty()) {
            BlockPos masterPos = master.getSecond();
            pLevel.destroyBlockProgress(masterPos.hashCode(), masterPos, -1);
            if (!pLevel.isClientSide() && pPlayer.isCreative()) pLevel.destroyBlock(masterPos, false);
        }
        return super.playerWillDestroy(pLevel, pPos, pState, pPlayer);
    }

    public static Pair<BlockState, BlockPos> getMaster(BlockGetter level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof ThrusterStructureBlock)) return Pair.of(Blocks.AIR.defaultBlockState(), BlockPos.ZERO);
        var dir = state.getValue(FACING);
        var rel = pos.relative(dir);
        var mState = level.getBlockState(rel);
        return Pair.of(mState.getBlock() instanceof ThrusterBlock && mState.getValue(ThrusterStructureBlock.FACING) == dir.getOpposite() ? mState : Blocks.AIR.defaultBlockState(), rel);
    }

    @Override
    public void tick(BlockState pState, ServerLevel pLevel, BlockPos pPos, RandomSource pRandom) {
        if (getMaster(pLevel, pPos, pState).getFirst().isEmpty()) pLevel.setBlockAndUpdate(pPos, Blocks.AIR.defaultBlockState());
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        var master = getMaster(level, pos, state);
        return master.getFirst().getBlock().getLightEmission(master.getFirst(), level, master.getSecond());
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
    public boolean addLandingEffects(BlockState state1, ServerLevel level, BlockPos pos, BlockState state2, LivingEntity entity, int numberOfParticles) {
        return true;
    }

    @Override
    public BlockPos getInformationSource(Level level, BlockPos pos, BlockState state) {
        var master = getMaster(level, pos, state);
        return master.getFirst().isEmpty() ? pos : master.getSecond();
    }

    public static class RenderProperties implements IClientBlockExtensions, MultiPosDestructionHandler {

        @Override
        public boolean addDestroyEffects(BlockState state, Level Level, BlockPos pos, ParticleEngine manager) {
            return true;
        }

        @Override
        public boolean addHitEffects(BlockState state, Level level, HitResult target, ParticleEngine manager) {
            if (target instanceof BlockHitResult bhr) {
                BlockPos targetPos = bhr.getBlockPos();
                var master = getMaster(level, targetPos, state);
                if (master.getFirst().isEmpty()) return true;
                manager.crack(master.getSecond(), bhr.getDirection());
                return true;
            }
            return IClientBlockExtensions.super.addHitEffects(state, level, target, manager);
        }

        @Override
        @Nullable
        public Set<BlockPos> getExtraPositions(net.minecraft.client.multiplayer.ClientLevel level, BlockPos pos, BlockState blockState, int progress) {
            var master = getMaster(level, pos, blockState);
            if (master.getFirst().isEmpty()) return null;
            HashSet<BlockPos> set = new HashSet<>();
            set.add(master.getSecond());
            return set;
        }
    }
}

package dev.lopyluna.create_lnl.content.blocks.contraption_lift;

import com.mojang.serialization.Codec;
import com.simibubi.create.foundation.block.IBE;
import dev.lopyluna.create_lnl.register.LiftsBETypes;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.lang.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.util.FakePlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.stream.Stream;

@SuppressWarnings("NullableProblems")
public class DockingLiftBlock extends Block implements IBE<DockingLiftBE>, BlockSubLevelAssemblyListener {
    public static final EnumProperty<LiftState> LIFT = EnumProperty.create("lift", LiftState.class);
    private static boolean destroying = false;

    public DockingLiftBlock(Properties properties) {
        super(properties.forceSolidOn());
        this.registerDefaultState(defaultBlockState().setValue(LIFT, LiftState.PLACED));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIFT);
    }

    @Override
    public Class<DockingLiftBE> getBlockEntityClass() {
        return DockingLiftBE.class;
    }

    @Override
    public BlockEntityType<? extends DockingLiftBE> getBlockEntityType() {
        return LiftsBETypes.CONTRAPTION_LIFT.get();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(LIFT).structure() ? RenderShape.INVISIBLE : RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter getter, BlockPos pos, CollisionContext context) {
        if (!(getter.getBlockEntity(pos) instanceof DockingLiftBE be)) return Shapes.empty();
        if (be.animating()) return Shapes.box(2 / 16f, 0, 2 / 16f, 14 / 16f, 12 / 16f, 14 / 16f);
        var client = getter instanceof Level level && level.isClientSide;
        return shape(be, (client ? be.renderHeight(AnimationTickHolder.getPartialTicks()) : be.height) - be.structIndex);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter getter, BlockPos pos, CollisionContext context) {
        if (!(getter.getBlockEntity(pos) instanceof DockingLiftBE be)) return Shapes.empty();
        if (be.animating()) return Shapes.box(2 / 16f, 0, 2 / 16f, 14 / 16f, 12 / 16f, 14 / 16f);
        return shape(be, be.height - be.structIndex);
    }

    private static VoxelShape shape(DockingLiftBE be, float height) {
        return shape(height, be.structIndex > 0);
    }

    public static VoxelShape shape(float height, boolean structure) {
        var topYA = Math.clamp(12 / 16f + height, 0, 1);
        var topYB = Math.clamp(9 / 16f + height, 0, 1);
        VoxelShape topA = Shapes.empty(), topB = Shapes.empty();
        if (topYB < topYA) {
            topA = Shapes.box(2 / 16f, topYB, -4 / 16f, 14 / 16f, topYA, 20 / 16f);
            topB = Shapes.box(-4 / 16f, topYB, 2 / 16f, 20 / 16f, topYA, 14 / 16f);
        }
        var middle = 3 / 16f < topYB ? Shapes.box(4 / 16f, 0f, 4 / 16f, 12 / 16f, topYB, 12 / 16f) : Shapes.empty();
        if (structure) return join(topA, topB, middle);

        var bottomA = Shapes.box(2 / 16f, 0, -4 / 16f, 14 / 16f, 3 / 16f, 20 / 16f);
        var bottomB = Shapes.box(-4 / 16f, 0, 2 / 16f, 20 / 16f, 3 / 16f, 14 / 16f);
        return join(topA, topB, middle, bottomA, bottomB);
    }

    public static VoxelShape fullShape(float height) {
        var index = Mth.floor(height + 12 / 16f - 0.001f);
        var parts = new ArrayList<VoxelShape>();
        parts.add(shape(height, false));
        for (int i = 1; i <= index; i++) parts.add(shape(height - i, true).move(0, i, 0));
        return join(parts.toArray(new VoxelShape[0]));
    }

    private static VoxelShape join(VoxelShape... shapes) {
        return Stream.of(shapes).reduce((a, b) -> Shapes.join(a, b, BooleanOp.OR)).orElse(Shapes.empty()).optimize();
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader reader, BlockPos pos) {
        var below = pos.below();
        if (reader instanceof Level level && Sable.HELPER.getContaining(level, pos) != null) return false;
        if (state.getValue(LIFT).structure()) return reader.getBlockState(below).getBlock() instanceof DockingLiftBlock;
        return Block.canSupportRigidBlock(reader, below) && !(reader.getBlockState(below).getBlock() instanceof DockingLiftBlock);
    }

    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState, BlockPos oldPos, BlockPos newPos) {
        if (Sable.HELPER.getContaining(resultingLevel, newPos) == null) return;
        destroyLift(originLevel, basePos(originLevel, oldPos));
        destroyLift(resultingLevel, basePos(resultingLevel, newPos));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor accessor, BlockPos pos, BlockPos neighborPos) {
        if (destroying || canSurvive(state, accessor, pos) || !(accessor instanceof Level level)) return state;
        destroyLift(level, basePos(level, pos));
        return state;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (state.getValue(LIFT).structure()) return;
        if (!(placer instanceof Player player) || placer instanceof FakePlayer) return;
        if (!(level.getBlockEntity(pos) instanceof DockingLiftBE be)) return;
        be.color = LiftHolding.dye(player);
        if (level.isClientSide) return;
        be.ownerUUID = player.getUUID();
        be.global = false;
        be.notifyUpdate();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(stack.is(Items.WET_SPONGE) || stack.is(Items.SPONGE) || stack.getItem() instanceof DyeItem))
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);

        if (!(level.getBlockEntity(basePos(level, pos)) instanceof DockingLiftBE be))
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        if (be.cantControl(player)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        var dye = stack.getItem() instanceof DyeItem item ? item.getDyeColor() : null;
        if (be.color == dye) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        be.color = dye;
        if (level.isClientSide) player.playNotifySound(dye == null ? SoundEvents.SPONGE_ABSORB : SoundEvents.DYE_USE, SoundSource.PLAYERS, 1, 1);
        else {
            if (be.isOwner(player)) LiftHolding.setColor(player, dye);
            be.notifyUpdate();
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide || !neighborPos.equals(pos.above())) return;
        var above = level.getBlockState(neighborPos);
        if (above.isAir() || above.getBlock() instanceof DockingLiftBlock) return;
        if (level.getBlockEntity(basePos(level, pos)) instanceof DockingLiftBE be) be.blockAbove(neighborPos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.is(newState.getBlock())) {
            super.onRemove(state, level, pos, newState, movedByPiston);
            return;
        }
        var index = 0;
        var broken = false;
        if (level.getBlockEntity(pos) instanceof DockingLiftBE be) {
            index = be.structIndex;
            if (index == 0) {
                clearOwnerClaim(level, be);
                be.undock();
                be.forceLoaded(false);
                broken = true;
            } else if (level.getBlockEntity(pos.below(index)) instanceof DockingLiftBE base) broken = base.mastIndex >= index;
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (broken && !destroying) destroyLift(level, index == 0 ? pos : pos.below(index));
    }

    public static void destroyLift(Level level, BlockPos base) {
        if (destroying) return;
        destroying = true;
        try {
            for (int i = 1; i <= LiftPlacement.MAX_HEIGHT; i++) {
                var above = base.above(i);
                if (level.getBlockState(above).getBlock() instanceof DockingLiftBlock) level.removeBlock(above, false);
            }
            if (level.getBlockState(base).getBlock() instanceof DockingLiftBlock) level.removeBlock(base, false);
        } finally {
            destroying = false;
        }
    }

    private static void clearOwnerClaim(Level level, DockingLiftBE be) {
        if (level.isClientSide || be.ownerUUID == null) return;
        var owner = level.getPlayerByUUID(be.ownerUUID);
        if (owner == null) return;
        var claimed = LiftHolding.liftPos(owner);
        if (claimed != null && claimed.dimension().equals(level.dimension()) && claimed.pos().equals(be.getBlockPos()))
            LiftHolding.setLiftPos(owner, null);
    }

    public static BlockPos basePos(BlockGetter getter, BlockPos pos) {
        if (getter.getBlockEntity(pos) instanceof DockingLiftBE be && be.structIndex > 0) return pos.below(be.structIndex);
        return pos;
    }

    @Override protected boolean canBeReplaced(BlockState state, Fluid fluid) { return false; }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return state.getValue(LIFT).structure()
                && getShape(state, context.getLevel(), context.getClickedPos(), CollisionContext.empty()).isEmpty();
    }

    @SuppressWarnings("unused")
    public enum LiftState implements StringRepresentable {
        PLACED, STRUCTURE,
        ;
        public static final Codec<LiftState> CODEC = StringRepresentable.fromEnum(LiftState::values);

        public boolean structure() { return this == STRUCTURE; }

        @Override
        public String getSerializedName() {
            return Lang.asId(name());
        }
    }
}

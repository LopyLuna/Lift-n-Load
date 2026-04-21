package dev.lopyluna.create_lnl.content.blocks.lift;

import com.mojang.serialization.Codec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import dev.lopyluna.create_lnl.register.LiftsBETypes;
import dev.lopyluna.create_lnl.register.LiftsDataComponents;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.lang.Lang;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

@SuppressWarnings("NullableProblems")
public class LiftBlock extends Block implements IBE<LiftBE>, IWrenchable, BlockSubLevelAssemblyListener {
    public static final EnumProperty<LiftState> LIFT = EnumProperty.create("lift", LiftState.class);

    public LiftBlock(Properties properties) {
        super(properties.forceSolidOn());
        this.registerDefaultState(defaultBlockState().setValue(LIFT, LiftState.PLACED));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(LIFT).structure()) {
            if (!canSurvive(state, level, pos) || getShape(state, level, pos, CollisionContext.empty()).isEmpty()) remove(level, pos, true);
            return state;
        }
        if (direction == Direction.UP && !(neighborState.getBlock() instanceof LiftBlock) && level.getBlockEntity(pos) instanceof LiftBE be) be.blockAboveUpdate(neighborState, neighborPos);
        if (!canSurvive(state, level, pos)) remove(level, pos, false);
        return state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (level.getBlockEntity(pos) instanceof LiftBE be) {
            var height = Math.round((level instanceof ClientLevel ? be.cHeight.getValue(AnimationTickHolder.getPartialTicks()) : be.height.getValue()) * 100f) / 100f;
            height -= be.structIndex ;
            var topYA = Math.clamp(12/16f + height, 0, 1);
            var topYB = Math.clamp(9/16f + height, 0, 1);
            VoxelShape top = Shapes.empty(), topA = Shapes.empty(), topB = Shapes.empty();
            if (topYB < topYA) {
                top = Shapes.box(2 / 16f, topYB, 2 / 16f, 14 / 16f, topYA, 14 / 16f);
                topA = Shapes.box(2 / 16f, topYB, -4 / 16f, 14 / 16f, topYA, 20 / 16f);
                topB = Shapes.box(-4 / 16f, topYB, 2 / 16f, 20 / 16f, topYA, 14 / 16f);
            }
            var middle = 3/16f < topYB ? Shapes.box(4/16f, 3/16f, 4/16f, 12/16f, topYB, 12/16f) : Shapes.empty();
            if (be.structIndex > 0) return Stream.of(topA, topB, middle).reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR)).orElse(Shapes.empty()).optimize();
            var bottom = Shapes.box(2/16f, 0, 2/16f, 14/16f, 3/16f, 14/16f);
            var bottomA = Shapes.box(2/16f, 0, -4/16f, 14/16f, 3/16f, 20/16f);
            var bottomB = Shapes.box(-4/16f, 0, 2/16f, 20/16f, 3/16f, 14/16f);
            return (be.placing ? Stream.of(top, middle, bottom) : Stream.of(topA, topB, middle, bottomA, bottomB)).reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR)).orElse(Shapes.empty()).optimize();
        }
        return Shapes.empty();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (state.getValue(LIFT).structure()) return;
        if (placer instanceof Player player) placedBy(level, stack, pos, player);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader reader, BlockPos pos) {
        var below = pos.below();
        if (state.getValue(LIFT).structure()) return reader.getBlockState(below).getBlock() instanceof LiftBlock;
        if (reader instanceof Level level && Sable.HELPER.getContaining(level, pos) != null) return false;
        return canSupportRigidBlock(reader, below) && !(reader.getBlockState(below).getBlock() instanceof LiftBlock);
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (state.getValue(LIFT).structure()) return super.useWithoutItem(state, level, pos, player, hitResult);
        if (level.getBlockEntity(pos) instanceof LiftBE be && !(player instanceof FakePlayer)) {
            be.bind();
            if (be.userUUID == null) {
                be.user = player;
                be.userUUID = player.getUUID();
            }
            be.initializing = false;
            return super.useWithoutItem(state, level, pos, player, hitResult);
        }
        return super.useWithoutItem(state, level, pos, player, hitResult);
    }


    public static void placedBy(Level level, @Nullable ItemStack stack, BlockPos pos, Player placer) {
        if (level.getBlockEntity(pos) instanceof LiftBE be && !(placer instanceof FakePlayer)) {
            be.user = placer;
            be.userUUID = placer.getUUID();
            be.initializing = false;
            if (stack != null) {
                if (stack.has(LiftsDataComponents.SUBLEVEL_UUID)) be.subUUID = stack.get(LiftsDataComponents.SUBLEVEL_UUID);
                stack.remove(LiftsDataComponents.SUBLEVEL_UUID);
            }
        }
    }

    @Override
    public Class<LiftBE> getBlockEntityClass() {
        return LiftBE.class;
    }

    @Override
    public BlockEntityType<? extends LiftBE> getBlockEntityType() {
        return LiftsBETypes.CONTRAPTION_LIFT.get();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(LIFT).structure() ? RenderShape.INVISIBLE : RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIFT);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) remove(level, pos, movedByPiston);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState, BlockPos oldPos, BlockPos newPos) {
        final SubLevel subLevel = Sable.HELPER.getContaining(resultingLevel, newPos);
        if (subLevel != null) {
            remove(originLevel, oldPos, false);
            remove(resultingLevel, newPos, false);
        }
    }

    public static void remove(LevelAccessor accessor, BlockPos pos, boolean moving) {
        if (accessor.getBlockEntity(pos) instanceof LiftBE be && accessor instanceof Level level) {
            var state = be.getBlockState();
            if (!state.canBeReplaced(new BlockPlaceContext(level, null, InteractionHand.MAIN_HAND, ItemStack.EMPTY, new BlockHitResult(pos.getCenter(), Direction.NORTH, pos, true)))) {
                if (be.structIndex > 0 && !moving) {
                    remove(level, pos.below(be.structIndex), false);
                    return;
                }
                var h = ((be.height.getValue() + 12 / 16f) * 100f) / 100f;
                var index = Mth.floor(h - 0.001f);
                for (int i = 1; i <= index; i++) {
                    var relative = pos.above(i);
                    if (level.getBlockState(relative).getBlock() instanceof LiftBlock) level.removeBlock(relative, true);
                }
            }
            if (be.userUUID != null && accessor.getPlayerByUUID(be.userUUID) instanceof Player player) {
                if (!(state.getBlock() instanceof LiftBlock)) accessor.removeBlock(pos, false);
                player.getPersistentData().remove("LiftPos");
                be.userUUID = null;
                be.user = null;
            }
        }
    }

    @Override protected boolean canBeReplaced(BlockState state, Fluid fluid) { return false; }
    @Override protected boolean canBeReplaced(BlockState state, BlockPlaceContext useContext) { return state.getValue(LIFT).structure() && getShape(state, useContext.getLevel(), useContext.getClickedPos(), CollisionContext.empty()).isEmpty(); }

    public enum LiftState implements StringRepresentable {
        PLACED, STRUCTURE,
        ;
        public static final Codec<LiftState> CODEC = StringRepresentable.fromEnum(LiftState::values);

        public static LiftState byIndex(int index) {
            return values()[index];
        }
        public LiftState nextActiveLevel() {
            return byIndex(ordinal() % (values().length - 1) + 1);
        }
        public boolean structure() {return this == STRUCTURE;}

        @Override
        public String getSerializedName() {
            return Lang.asId(name());
        }
    }
}

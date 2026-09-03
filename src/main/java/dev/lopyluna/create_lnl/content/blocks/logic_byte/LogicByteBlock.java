package dev.lopyluna.create_lnl.content.blocks.logic_byte;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.api.contraption.transformable.TransformableBlock;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import dev.lopyluna.create_lnl.content.blocks.IBlockFromHandInteraction;
import dev.lopyluna.create_lnl.register.LiftsBETypes;
import dev.lopyluna.create_lnl.register.LiftsBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.UnaryOperator;

@SuppressWarnings({"NullableProblems", "deprecation"})
public class LogicByteBlock extends Block implements IBE<LogicByteBE>, IWrenchable, IBlockFromHandInteraction, TransformableBlock {
    public static final MapCodec<LogicByteBlock> CODEC = simpleCodec(LogicByteBlock::new);
    public static final IntegerProperty SLOTS = IntegerProperty.create("slots", 0, 255);
    private static final VoxelShape[] SHAPES = new VoxelShape[256];

    static {
        for (var mask = 0; mask < SHAPES.length; mask++) {
            var shape = Shapes.empty();
            for (var slot : LogicSlot.ALL) if ((mask & slot.bit) != 0) shape = Shapes.or(shape, slot.shape);
            SHAPES[mask] = shape.isEmpty() ? Shapes.block() : shape;
        }
    }

    public LogicByteBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SLOTS, LogicSlot.BFL.bit));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SLOTS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(SLOTS)];
    }

    public static boolean fits(Level level, BlockPos pos, BlockState state, LogicSlot slot) {
        return level.isUnobstructed(state.setValue(SLOTS, state.getValue(SLOTS) | slot.bit), pos, CollisionContext.empty());
    }

    @Nullable
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return moved(state, slot -> slot.rotate(rotation));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return moved(state, slot -> slot.mirror(mirror));
    }

    @Override
    public BlockState transform(BlockState state, StructureTransform transform) {
        return moved(state, slot -> slot.transform(transform));
    }

    private static BlockState moved(BlockState state, UnaryOperator<LogicSlot> mapper) {
        int slots = state.getValue(SLOTS);
        var mapped = 0;
        for (var slot : LogicSlot.ALL) if ((slots & slot.bit) != 0) mapped |= mapper.apply(slot).bit;
        return state.setValue(SLOTS, mapped);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(SLOTS, LogicSlot.of(ctx.getClickedPos(), ctx.getClickLocation(), ctx.getClickedFace(), true).bit);
    }

    @Nullable
    @Override
    public InteractionResult rightClickBlock(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(asItem())) return null;
        if (!(level.getBlockEntity(pos) instanceof LogicByteBE be)) return null;
        var slot = LogicSlot.at(pos, hit.getLocation(), hit.getDirection(), false);
        if (slot != null && be.has(slot)) slot = LogicSlot.at(pos, hit.getLocation(), hit.getDirection(), true);
        if (slot == null || be.has(slot) || !fits(level, pos, state, slot)) return null;
        if (!level.isClientSide) {
            be.add(slot, player.getDirection().getOpposite());
            if (!player.hasInfiniteMaterials()) stack.shrink(1);
            level.playSound(null, pos, getSoundType(state).getPlaceSound(), SoundSource.BLOCKS, 1f, 0.8f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext ctx) {
        var level = ctx.getLevel();
        var pos = ctx.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof LogicByteBE be)) return InteractionResult.PASS;
        var slot = LogicSlot.at(pos, ctx.getClickLocation(), ctx.getClickedFace(), false);
        if (slot == null || !be.has(slot)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            be.rotate(slot, ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown());
            IWrenchable.playRotateSound(level, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext ctx) {
        var level = ctx.getLevel();
        var pos = ctx.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof LogicByteBE be)) return IWrenchable.super.onSneakWrenched(state, ctx);
        var slot = LogicSlot.at(pos, ctx.getClickLocation(), ctx.getClickedFace(), false);
        if (slot == null || !be.has(slot) || be.count() <= 1) return IWrenchable.super.onSneakWrenched(state, ctx);
        if (!level.isClientSide) {
            be.remove(slot);
            var player = ctx.getPlayer();
            if (player != null && !player.hasInfiniteMaterials()) player.getInventory().placeItemBackInInventory(new ItemStack(this));
            IWrenchable.playRemoveSound(level, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(this, Math.max(1, Integer.bitCount(state.getValue(SLOTS)))));
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, net.minecraft.world.phys.HitResult target, net.minecraft.world.level.LevelReader level, BlockPos pos, Player player) {
        return new ItemStack(LiftsBlocks.LOGIC_BYTE.get());
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<LogicByteBE> getBlockEntityClass() {
        return LogicByteBE.class;
    }

    @Override
    public BlockEntityType<? extends LogicByteBE> getBlockEntityType() {
        return LiftsBETypes.LOGIC_BYTE.get();
    }
}

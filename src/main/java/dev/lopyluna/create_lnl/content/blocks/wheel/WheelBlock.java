package dev.lopyluna.create_lnl.content.blocks.wheel;

import com.simibubi.create.AllShapes;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import dev.lopyluna.create_lnl.content.blocks.overwrite.TireBlockItem;
import dev.lopyluna.create_lnl.register.LiftsBETypes;
import dev.lopyluna.create_lnl.register.LiftsItems;
import dev.ryanhcode.offroad.Offroad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;

@SuppressWarnings("NullableProblems")
public class WheelBlock extends RotatedPillarKineticBlock implements IBE<WheelBE> {
    private static final EnumMap<Direction.Axis, VoxelShape> WHEEL_SHAPE = new EnumMap<>(Direction.Axis.class);
    static {
        for (var axis : Direction.Axis.values()) {
            var dir = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
            var vec = Vec3.atLowerCornerOf(dir.getNormal()).scale(2/16f);
            WHEEL_SHAPE.put(axis, AllShapes.CASING_12PX.get(dir).move(vec.x, vec.y, vec.z));
        }
    }

    public WheelBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return WHEEL_SHAPE.get(pState.getValue(AXIS));
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof WheelBE be) return switch (be.type) {
            case MONSTROUS -> be.sticky ? LiftsItems.SLIME_MONSTROUS_TIRE.asStack() : BuiltInRegistries.ITEM.get(Offroad.path("monstrous_tire")).getDefaultInstance();
            case LARGE -> be.sticky ? LiftsItems.SLIME_LARGE_TIRE.asStack() : BuiltInRegistries.ITEM.get(Offroad.path("large_tire")).getDefaultInstance();
            case NORMAL -> be.sticky ? LiftsItems.SLIME_TIRE.asStack() : BuiltInRegistries.ITEM.get(Offroad.path("tire")).getDefaultInstance();
            case SMALL -> be.sticky ? LiftsItems.SLIME_SMALL_TIRE.asStack() : BuiltInRegistries.ITEM.get(Offroad.path("small_tire")).getDefaultInstance();
        };
        return super.getCloneItemStack(state, target, level, pos, player);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public Class<WheelBE> getBlockEntityClass() {
        return WheelBE.class;
    }

    @Override
    public BlockEntityType<? extends WheelBE> getBlockEntityType() {
        return LiftsBETypes.WHEEL.get();
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(AXIS);
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == getRotationAxis(state);
    }

    @Override
    public float getParticleTargetRadius() {
        return 2f;
    }

    @Override
    public float getParticleInitialRadius() {
        return 1.75f;
    }

    @Override
    public void setPlacedBy(Level worldIn, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(worldIn, pos, state, placer, stack);
        if (stack.getItem() instanceof TireBlockItem item && worldIn.getBlockEntity(pos) instanceof WheelBE be) {
            be.radius = item.type.radius();
            be.type = item.type;
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        var player = context.getPlayer();
        if (player == null) return super.getStateForPlacement(context);
        var stack = player.getItemInHand(context.getHand());
        if (stack.getItem() instanceof TireBlockItem item && context.getLevel().getBlockEntity(context.getClickedPos()) instanceof WheelBE be) {
            be.radius = item.type.radius();
            be.type = item.type;
        }
        return super.getStateForPlacement(context);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (level.getBlockEntity(pos) instanceof WheelBE be) be.update();
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
        super.onNeighborChange(state, level, pos, neighbor);
        if (level.getBlockEntity(pos) instanceof WheelBE be) be.update();
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level.getBlockEntity(pos) instanceof WheelBE be) be.dropped = player.hasInfiniteMaterials();
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (level.getBlockEntity(pos) instanceof WheelBE be) be.update();
    }

    @Override
    public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
        if (pLevel.getBlockEntity(pPos) instanceof WheelBE be && !be.dropped && !pIsMoving) Containers.dropItemStack(pLevel, pPos.getX(), pPos.getY(), pPos.getZ(), switch (be.type) {
            case MONSTROUS -> be.sticky ? LiftsItems.SLIME_MONSTROUS_TIRE.asStack() : BuiltInRegistries.ITEM.get(Offroad.path("monstrous_tire")).getDefaultInstance();
            case LARGE -> be.sticky ? LiftsItems.SLIME_LARGE_TIRE.asStack() : BuiltInRegistries.ITEM.get(Offroad.path("large_tire")).getDefaultInstance();
            case NORMAL -> be.sticky ? LiftsItems.SLIME_TIRE.asStack() : BuiltInRegistries.ITEM.get(Offroad.path("tire")).getDefaultInstance();
            case SMALL -> be.sticky ? LiftsItems.SLIME_SMALL_TIRE.asStack() : BuiltInRegistries.ITEM.get(Offroad.path("small_tire")).getDefaultInstance();
        });
        super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving);
    }
}

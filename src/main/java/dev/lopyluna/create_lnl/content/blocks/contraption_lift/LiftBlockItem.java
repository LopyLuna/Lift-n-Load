package dev.lopyluna.create_lnl.content.blocks.contraption_lift;

import com.simibubi.create.AllSoundEvents;
import dev.lopyluna.create_lnl.register.LiftsDataComps;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.index.SimSoundEvents;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.joml.Vector3d;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;

@SuppressWarnings("NullableProblems")
public class LiftBlockItem extends BlockItem {
    private static final int MAX_PICKUP_DIMENSION = 24;
    private static final int MAX_MARKED_BLOCKS = 32;
    private static final DustParticleOptions BLOCKED_PLACEMENT_PARTICLE = new DustParticleOptions(new Vector3f(1.0F, 0.25F, 0.25F), 1.0F);

    public LiftBlockItem(Block block, Properties properties) {
        super(block, properties.fireResistant());
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getPlayer() instanceof Player player && !(player instanceof FakePlayer)) {
            var level = ctx.getLevel();
            var stack = ctx.getItemInHand();
            if (player.isShiftKeyDown()) {
                onPlaced(player, level, null);
                player.getCooldowns().addCooldown(this, 10);
                stack.remove(LiftsDataComps.SUBLEVEL_UUID);
            } else {
                final var loc = ctx.getClickLocation();
                final var subLevel = Sable.HELPER.getContaining(level, loc);
                if (subLevel == null) return super.useOn(ctx);
                if (isTooLarge(subLevel.getPlot().getBoundingBox())) {
                    if (!level.isClientSide) player.displayClientMessage(Component.literal("Structure is too big!").withStyle(ChatFormatting.RED), true);
                    AllSoundEvents.DENY.play(level, player, player.blockPosition());
                    return InteractionResult.FAIL;
                }
                var uuid = subLevel.getUniqueId();
                stack.set(LiftsDataComps.SUBLEVEL_UUID, uuid);
                AllSoundEvents.CONFIRM.play(level, player, player.blockPosition());
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useOn(ctx);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(LiftsDataComps.SUBLEVEL_UUID);
    }

    public InteractionResult place(BlockPlaceContext ctx) {
        if (!this.getBlock().isEnabled(ctx.getLevel().enabledFeatures()) || !ctx.canPlace()) return InteractionResult.FAIL;
        var placeCtx = this.updatePlacementContext(ctx);
        if (placeCtx == null) return InteractionResult.FAIL;
        var placeState = this.getPlacementState(placeCtx);
        var pos = placeCtx.getClickedPos();
        var level = placeCtx.getLevel();
        var player = placeCtx.getPlayer();
        var stack = placeCtx.getItemInHand();

        if (placeState == null) return InteractionResult.FAIL;
        final Set<BlockPos> blockingBlocks = getStoredSubLevelObstructions(level, pos, stack);
        if (!blockingBlocks.isEmpty()) {
            if (level instanceof ServerLevel serverLevel) markBlockedPlacement(serverLevel, blockingBlocks);
            if (!level.isClientSide && player != null) player.displayClientMessage(Component.literal("Not enough room").withStyle(ChatFormatting.RED), true);
            if (player != null) AllSoundEvents.DENY.play(level, player, player.blockPosition());
            return InteractionResult.FAIL;
        }
        if (!this.placeBlock(placeCtx, placeState)) return InteractionResult.FAIL;

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
    protected SoundEvent getPlaceSound(BlockState state, Level level, BlockPos pos, @Nullable Player player) {
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

    private static boolean isTooLarge(BoundingBox3ic bounds) {
        return bounds.maxX() - bounds.minX() + 1 > MAX_PICKUP_DIMENSION
                || bounds.maxY() - bounds.minY() + 1 > MAX_PICKUP_DIMENSION
                || bounds.maxZ() - bounds.minZ() + 1 > MAX_PICKUP_DIMENSION;
    }

    private static Set<BlockPos> getStoredSubLevelObstructions(Level level, BlockPos liftPos, ItemStack stack) {
        final Set<BlockPos> blockingBlocks = new LinkedHashSet<>();
        if (!stack.has(LiftsDataComps.SUBLEVEL_UUID)) return blockingBlocks;

        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return blockingBlocks;

        final var uuid = stack.get(LiftsDataComps.SUBLEVEL_UUID);
        if (uuid == null) return blockingBlocks;

        final SubLevel subLevel = container.getSubLevel(uuid);
        if (subLevel == null) return blockingBlocks;

        final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        if (isTooLarge(bounds)) return blockingBlocks;

        final Vector3d plotAnchor = LiftBE.getSupportedBottomCenter(subLevel);
        final Vec3 pivot = LiftBE.getLiftPivot(liftPos);
        final double offsetX = pivot.x - plotAnchor.x;
        final double offsetY = pivot.y - plotAnchor.y;
        final double offsetZ = pivot.z - plotAnchor.z;

        final BlockPos.MutableBlockPos plotPos = new BlockPos.MutableBlockPos();
        final Level subLevelLevel = subLevel.getLevel();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) for (int y = bounds.minY(); y <= bounds.maxY(); y++) for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
            plotPos.set(x, y, z);
            final BlockState subLevelState = subLevelLevel.getBlockState(plotPos);
            if (subLevelState.isAir()) continue;

            final var shape = subLevelState.getCollisionShape(subLevelLevel, plotPos);
            if (shape.isEmpty()) continue;

            for (final var box : shape.toAabbs()) {
                final var movedBox = box.move(x + offsetX, y + offsetY, z + offsetZ);
                final int minX = Mth.floor(movedBox.minX);
                final int minY = Mth.floor(movedBox.minY);
                final int minZ = Mth.floor(movedBox.minZ);
                final int maxX = Mth.floor(movedBox.maxX - 1.0E-7);
                final int maxY = Mth.floor(movedBox.maxY - 1.0E-7);
                final int maxZ = Mth.floor(movedBox.maxZ - 1.0E-7);
                final var movedShape = Shapes.create(movedBox);

                for (int wx = minX; wx <= maxX; wx++) for (int wy = minY; wy <= maxY; wy++) for (int wz = minZ; wz <= maxZ; wz++) {
                    final BlockPos worldPos = new BlockPos(wx, wy, wz);
                    final BlockState worldState = level.getBlockState(worldPos);
                    if (worldState.isAir()) continue;

                    final var worldShape = worldState.getCollisionShape(level, worldPos);
                    if (worldShape.isEmpty()) continue;

                    if (Shapes.joinIsNotEmpty(movedShape, worldShape.move(wx, wy, wz), BooleanOp.AND)) {
                        blockingBlocks.add(worldPos.immutable());
                        if (blockingBlocks.size() >= MAX_MARKED_BLOCKS) return blockingBlocks;
                    }
                }
            }
        }
        return blockingBlocks;
    }

    private static void markBlockedPlacement(ServerLevel level, Set<BlockPos> blockingBlocks) {
        for (final BlockPos pos : blockingBlocks) {
            spawnParticlesOnBlockFaces(level, pos, BLOCKED_PLACEMENT_PARTICLE, UniformInt.of(6, 8));
        }
    }

    public static void spawnParticlesOnBlockFaces(ServerLevel level, BlockPos pos, ParticleOptions particle, IntProvider count) {
        for (var direction : Direction.values()) spawnParticlesOnBlockFace(level, pos, particle, count, direction, 0.55);
    }
    public static void spawnParticlesOnBlockFace(ServerLevel level, BlockPos pos, ParticleOptions particle, IntProvider count, Direction direction, double spread) {
        spawnParticleOnFace(level, pos, direction, particle, spread, count.sample(level.random));
    }
    public static void spawnParticleOnFace(ServerLevel level, BlockPos pos, Direction direction, ParticleOptions particle, double spread, int count) {
        Vec3 vec3 = Vec3.atCenterOf(pos);
        int i = direction.getStepX();
        int j = direction.getStepY();
        int k = direction.getStepZ();
        double d0 = vec3.x + (i == 0 ? Mth.nextDouble(level.random, -0.5, 0.5) : (double)i * spread);
        double d1 = vec3.y + (j == 0 ? Mth.nextDouble(level.random, -0.5, 0.5) : (double)j * spread);
        double d2 = vec3.z + (k == 0 ? Mth.nextDouble(level.random, -0.5, 0.5) : (double)k * spread);
        level.sendParticles(particle, d0, d1, d2, count, 0.2, 0.2, 0.2, 0.0);
    }
}

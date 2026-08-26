package dev.lopyluna.create_lnl.content.blocks.contraption_lift;

import com.simibubi.create.AllSoundEvents;
import dev.lopyluna.create_lnl.content.utils.LiftUtils;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.Optional;

@SuppressWarnings("NullableProblems")
public class DockingLiftBlockItem extends BlockItem {
    private static final DustParticleOptions BLOCKED_PARTICLE = new DustParticleOptions(new Vector3f(1.0F, 0.25F, 0.25F), 1.0F);

    public DockingLiftBlockItem(Block block, Properties properties) {
        super(block, properties.fireResistant());
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        var player = ctx.getPlayer();
        if (player == null || player instanceof FakePlayer) return InteractionResult.PASS;

        var level = ctx.getLevel();
        if (player.isShiftKeyDown() && LiftHolding.isHolding(player)) {
            if (!level.isClientSide) LiftHolding.release(player);
            AllSoundEvents.DENY.play(level, player, player.blockPosition());
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!LiftHolding.isHolding(player) && !player.isShiftKeyDown()) {
            var clicked = Sable.HELPER.getContaining(level, ctx.getClickedPos());
            if (clicked == null) clicked = Sable.HELPER.getContaining(level, ctx.getClickLocation());
            if (clicked != null) return pickUp(level, player, clicked);
        }
        return place(new BlockPlaceContext(ctx));
    }

    private InteractionResult pickUp(Level level, Player player, SubLevel subLevel) {
        if (LiftPlacement.isTooLarge(subLevel.getPlot().getBoundingBox())) {
            if (!level.isClientSide) player.displayClientMessage(Component.literal("Structure is too big!").withStyle(ChatFormatting.RED), true);
            AllSoundEvents.DENY.play(level, player, player.blockPosition());
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide) LiftHolding.hold(player, subLevel.getUniqueId());
        AllSoundEvents.CONFIRM.play(level, player, player.blockPosition());
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        if (!this.getBlock().isEnabled(ctx.getLevel().enabledFeatures())) return InteractionResult.FAIL;

        var placeCtx = this.updatePlacementContext(ctx);
        if (placeCtx == null) return InteractionResult.FAIL;

        var level = placeCtx.getLevel();
        var pos = placeCtx.getClickedPos();
        var player = placeCtx.getPlayer();
        var stack = placeCtx.getItemInHand();
        if (player == null) return InteractionResult.FAIL;

        var held = LiftHolding.held(player);
        var rotation = LiftHolding.rotation(player);
        var group = held == null ? null : LiftPlacement.structureGroup(level, held);
        var placement = LiftPlacement.solve(level, pos, held, group, rotation);
        if (!placement.valid()) {
            reject(level, player, placement);
            return InteractionResult.FAIL;
        }

        var placeState = this.getPlacementState(placeCtx);
        if (placeState == null || !this.placeBlock(placeCtx, placeState)) return InteractionResult.FAIL;

        var state = level.getBlockState(pos);
        if (state.is(placeState.getBlock())) {
            state.getBlock().setPlacedBy(level, pos, state, player, stack);
            if (level.getBlockEntity(pos) instanceof DockingLiftBE be) {
                be.target = Mth.clamp(Math.round(placement.height() * 16), 0, (int) (LiftPlacement.MAX_HEIGHT * 16));
                be.height = placement.height();
                be.prevHeight = placement.height();
                be.snapRotation(rotation);
                dock(be, held, group, placement);
                be.notifyUpdate();
            }
            if (player instanceof ServerPlayer serverPlayer) CriteriaTriggers.PLACED_BLOCK.trigger(serverPlayer, pos, stack);
        }

        var sound = state.getSoundType(level, pos, player);
        level.playSound(player, pos, getPlaceSound(state, level, pos, player), SoundSource.BLOCKS,
                (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, state));

        if (!level.isClientSide) {
            claim(player, level, pos);
            LiftHolding.release(player);
        }
        player.getCooldowns().addCooldown(this, 5);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void dock(DockingLiftBE be, @Nullable SubLevel held, @Nullable LiftUtils.SubLevelGroup group, LiftPlacement.Result placement) {
        if (held == null) return;
        var degenerate = group == null || group.root() == null;
        var root = degenerate ? held : group.root();
        var anchor = degenerate ? fallbackAnchor(root)
                : placement.anchor().add(group.originX(), group.originY(), group.originZ());

        be.subUUID = root.getUniqueId();
        be.dockAnchor = anchor;
        be.supportPos = null;
    }

    private static Vec3 fallbackAnchor(SubLevel root) {
        var local = root.boundingBox().transformInverse(root.logicalPose(), new BoundingBox3d());
        return new Vec3((local.minX() + local.maxX()) / 2, local.minY(), (local.minZ() + local.maxZ()) / 2);
    }

    private static void claim(Player player, Level level, BlockPos pos) {
        var previous = LiftHolding.liftPos(player);
        if (previous != null && previous.dimension().equals(level.dimension()) && !previous.pos().equals(pos)
                && level.getBlockState(previous.pos()).getBlock() instanceof DockingLiftBlock)
            DockingLiftBlock.destroyLift(level, previous.pos());
        LiftHolding.mutate(player, data -> data.withLift(Optional.of(GlobalPos.of(level.dimension(), pos))));
    }

    private static void reject(Level level, Player player, LiftPlacement.Result placement) {
        if (level instanceof ServerLevel serverLevel) {
            for (var pos : placement.blockers()) spawnParticlesOnBlockFaces(serverLevel, pos, BLOCKED_PARTICLE, UniformInt.of(6, 8));
            player.displayClientMessage(Component.literal(placement.message()).withStyle(ChatFormatting.RED), true);
        }
        AllSoundEvents.DENY.play(level, player, player.blockPosition());
    }

    @Override
    protected SoundEvent getPlaceSound(BlockState state, Level level, BlockPos pos, @Nullable Player player) {
        return state.getSoundType(level, pos, player).getPlaceSound();
    }

    public static void spawnParticlesOnBlockFaces(ServerLevel level, BlockPos pos, ParticleOptions particle, IntProvider count) {
        for (var direction : Direction.values()) spawnParticleOnFace(level, pos, direction, particle, 0.55, count.sample(level.random));
    }

    public static void spawnParticleOnFace(ServerLevel level, BlockPos pos, Direction direction, ParticleOptions particle, double spread, int count) {
        var center = Vec3.atCenterOf(pos);
        int i = direction.getStepX(), j = direction.getStepY(), k = direction.getStepZ();
        var x = center.x + (i == 0 ? Mth.nextDouble(level.random, -0.5, 0.5) : i * spread);
        var y = center.y + (j == 0 ? Mth.nextDouble(level.random, -0.5, 0.5) : j * spread);
        var z = center.z + (k == 0 ? Mth.nextDouble(level.random, -0.5, 0.5) : k * spread);
        level.sendParticles(particle, x, y, z, count, 0.2, 0.2, 0.2, 0.0);
    }
}

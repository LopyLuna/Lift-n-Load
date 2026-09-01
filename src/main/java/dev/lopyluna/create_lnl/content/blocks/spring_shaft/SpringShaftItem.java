package dev.lopyluna.create_lnl.content.blocks.spring_shaft;

import com.simibubi.create.content.kinetics.base.IRotate;
import dev.lopyluna.create_lnl.register.LiftsBlocks;
import dev.lopyluna.create_lnl.register.LiftsDataComps;
import dev.ryanhcode.sable.Sable;
import dev.simulated_team.simulated.content.items.spring.SpringItemHandler;
import dev.simulated_team.simulated.data.SimLang;
import dev.simulated_team.simulated.util.SimColors;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.theme.Color;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

@SuppressWarnings("NullableProblems")
public class SpringShaftItem extends Item {
    public SpringShaftItem(Properties pProperties) {
        super(pProperties);
    }

    @OnlyIn(Dist.CLIENT)
    public void tick(Level level, ItemStack stack, @Nullable BlockHitResult hit) {
        if (net.minecraft.client.Minecraft.getInstance().isPaused()) return;
        var firstPos = stack.get(LiftsDataComps.FIRST_POS);
        if (firstPos == null) return;
        var firstDir = stack.get(LiftsDataComps.FIRST_DIR);
        if (firstDir == null) return;

        int firstColor = SimColors.SUCCESS_LIME;
        var parentCenter = firstPos.relative(firstDir);
        if (!level.getBlockState(parentCenter).canBeReplaced()) firstColor = SimColors.NUH_UH_RED;
        else if (cantSupportFaceOrShaft(level, firstPos, firstDir)) firstColor = SimColors.NUH_UH_RED;

        final Vec3 linkVec = new Vec3(firstDir.getStepX(), firstDir.getStepY(), firstDir.getStepZ());
        final AABB linkAABB = new AABB(firstPos).inflate(-0.3).move(linkVec.scale(0.65));
        Outliner.getInstance().showAABB(firstPos + "Spring", linkAABB).colored(firstColor).lineWidth(1 / 16f);

        if (firstColor == SimColors.NUH_UH_RED) return;
        if (hit == null || hit.getType() == HitResult.Type.MISS) return;
        var pos = hit.getBlockPos();
        var dir = hit.getDirection();

        var childCenter = pos.relative(dir);

        int color = SimColors.SUCCESS_LIME;

        if (parentCenter.equals(childCenter)) color = SimColors.NUH_UH_RED;
        else if (!level.getBlockState(parentCenter).canBeReplaced() || !level.getBlockState(childCenter).canBeReplaced()) color = SimColors.NUH_UH_RED;
        else if (cantSupportFaceOrShaft(level, pos, dir)) color = SimColors.NUH_UH_RED;
        else if (testExceedsRange(level, childCenter, parentCenter)) color = SimColors.NUH_UH_RED;

        var hitAABB = new AABB(pos).inflate(-0.3).move(new Vec3(dir.getStepX(), dir.getStepY(), dir.getStepZ()).scale(0.65));

        var globalFirstPoint = Sable.HELPER.projectOutOfSubLevel(level, linkAABB.getCenter());
        var globalTarget = Sable.HELPER.projectOutOfSubLevel(level, hitAABB.getCenter());

        var data = new DustParticleOptions(new Color(color).asVectorF(), 1);
        var totalFlyingTicks = 10;
        var segments = totalFlyingTicks/3+1;

        for (int i = 0; i < segments; i++) {
            var vec = globalFirstPoint.lerp(globalTarget, level.getRandom().nextFloat());
            level.addParticle(data, vec.x, vec.y, vec.z, 0, 0, 0);
        }

        Outliner.getInstance().showAABB(firstPos + " Spring Selection", hitAABB).colored(color).lineWidth(1 / 16f);

    }

    @Override
    public InteractionResult useOn(final UseOnContext ctx) {
        var player = ctx.getPlayer();
        var stack = ctx.getItemInHand();

        var firstPos = stack.get(LiftsDataComps.FIRST_POS);
        var firstDir = stack.get(LiftsDataComps.FIRST_DIR);

        if (firstPos != null && firstDir != null) {
            if (player == null || player.isShiftKeyDown()) {
                stack.remove(LiftsDataComps.FIRST_POS);
                stack.remove(LiftsDataComps.FIRST_DIR);
                return sendMessage("connection_terminated", SimColors.NUH_UH_RED, player, InteractionResult.SUCCESS_NO_ITEM_USED);
            }
            var level = ctx.getLevel();
            var parentRel = firstPos.relative(firstDir);
            var failed = !level.getBlockState(parentRel).canBeReplaced() || cantSupportFaceOrShaft(level, firstPos, firstDir);
            if (failed) {
                stack.remove(LiftsDataComps.FIRST_POS);
                stack.remove(LiftsDataComps.FIRST_DIR);
                return sendMessage("connection_terminated", SimColors.NUH_UH_RED, player, InteractionResult.SUCCESS_NO_ITEM_USED);
            }
            var dir = ctx.getClickedFace();
            var pos = ctx.getClickedPos();

            var childRel = pos.relative(dir);

            if (testExceedsRange(level, childRel, parentRel)) return sendMessage("out_of_range", SimColors.NUH_UH_RED, player, InteractionResult.FAIL);
            if (parentRel.equals(childRel)) return sendMessage("same_block", SimColors.NUH_UH_RED, player, InteractionResult.FAIL);
            if (!level.getBlockState(childRel).canBeReplaced()) return sendMessage("block_exists", SimColors.NUH_UH_RED, player, InteractionResult.FAIL);
            if (cantSupportFaceOrShaft(level, pos, dir)) return sendMessage("not_enough_support", SimColors.NUH_UH_RED, player, InteractionResult.FAIL);

            stack.remove(LiftsDataComps.FIRST_POS);
            stack.remove(LiftsDataComps.FIRST_DIR);
            placeSprings(level, stack, player, childRel, dir, parentRel, firstDir);
            return InteractionResult.SUCCESS;
        }
        if (player == null) return InteractionResult.PASS;

        var level = ctx.getLevel();
        var dir = ctx.getClickedFace();
        var pos = ctx.getClickedPos();

        var childCenter = pos.relative(dir);

        if (!level.getBlockState(childCenter).canBeReplaced()) return sendMessage("block_exists", SimColors.NUH_UH_RED, player, InteractionResult.FAIL);
        if (cantSupportFaceOrShaft(level, pos, dir)) return sendMessage("not_enough_support", SimColors.NUH_UH_RED, player, InteractionResult.FAIL);

        stack.set(LiftsDataComps.FIRST_POS, pos);
        stack.set(LiftsDataComps.FIRST_DIR, dir);
        return InteractionResult.SUCCESS;
    }

    public boolean cantSupportFaceOrShaft(Level level, BlockPos pos, Direction dir) {
        if (Block.canSupportCenter(level, pos, dir)) return false;
        var state = level.getBlockState(pos);
        return !(state.getBlock() instanceof IRotate rot) || !rot.hasShaftTowards(level, pos, state, dir);
    }

    public InteractionResult sendMessage(String message, int color, @Nullable Player player, InteractionResult result) {
        if (player != null) SimLang.translate("spring." + message).color(color).sendStatus(player);
        return result;
    }

    private boolean testExceedsRange(final Level level, final BlockPos childPos, final BlockPos parentPos) {
        return Sable.HELPER.distanceSquaredWithSubLevels(level, childPos.getX() + 0.5, childPos.getY() + 0.5, childPos.getZ() + 0.5,
                parentPos.getX() + 0.5, parentPos.getY() + 0.5, parentPos.getZ() + 0.5) > 9 * 9;
    }

    public void placeSprings(Level level, ItemStack stack, Player player, BlockPos childRel, Direction childDir, BlockPos parentRel, Direction parentDir) {
        var controllerSpring = addSpring(level, parentRel, childRel, parentDir, true);
        var partnerSpring = addSpring(level, childRel, parentRel, childDir, false);

        if (controllerSpring == null || partnerSpring == null) {
            level.setBlockAndUpdate(parentRel, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(childRel, Blocks.AIR.defaultBlockState());
            return;
        }
        var distanceSquared = (float) Sable.HELPER.distanceSquaredWithSubLevels(level, parentRel.getCenter(), childRel.getCenter());

        var distance = Mth.clamp(Mth.sqrt(distanceSquared) + 1f, 1f, (float) SpringItemHandler.MAX_LENGTH);
        controllerSpring.desiredLength = distance;
        partnerSpring.desiredLength = distance;
        controllerSpring.renderLength.setValue(distance);
        partnerSpring.renderLength.setValue(distance);

        player.awardStat(Stats.ITEM_USED.get(this));
        if (!player.hasInfiniteMaterials()) stack.shrink(1);
    }

    private SpringShaftBE addSpring(Level level, BlockPos placedPos, BlockPos otherPos, Direction facing, boolean controller) {
        var state = LiftsBlocks.SPRING_SHAFT.getDefaultState();
        if (level.setBlockAndUpdate(placedPos, state.setValue(SpringShaftBlock.FACING, facing))) {
            if (!(level.getBlockEntity(placedPos) instanceof SpringShaftBE be)) return null;
            be.isController = controller;

            var subLevel = Sable.HELPER.getContaining(level, otherPos);
            be.setPartner(otherPos, subLevel != null ? subLevel.getUniqueId() : null);
            be.notifyUpdate();
            return be;
        }
        return null;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        var firstPos = stack.get(LiftsDataComps.FIRST_POS);
        if (firstPos == null) return super.isFoil(stack);
        var firstDir = stack.get(LiftsDataComps.FIRST_DIR);
        if (firstDir == null) return super.isFoil(stack);
        return true;
    }
}

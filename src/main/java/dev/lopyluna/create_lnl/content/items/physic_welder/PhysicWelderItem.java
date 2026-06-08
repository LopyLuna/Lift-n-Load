package dev.lopyluna.create_lnl.content.items.physic_welder;

import com.simibubi.create.foundation.item.CustomArmPoseItem;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import dev.lopyluna.create_lnl.register.LiftsDataComps;
import dev.lopyluna.create_lnl.register.LiftsItems;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.merging_glue.MergingGlueBlock;
import dev.simulated_team.simulated.content.blocks.merging_glue.MergingGlueBlockEntity;
import dev.simulated_team.simulated.data.SimLang;
import dev.simulated_team.simulated.index.SimBlocks;
import dev.simulated_team.simulated.service.SimConfigService;
import dev.simulated_team.simulated.util.SimColors;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import javax.annotation.Nullable;
import java.util.function.Consumer;

@SuppressWarnings({"removal", "NullableProblems"})
public class PhysicWelderItem extends Item implements CustomArmPoseItem {
    public PhysicWelderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        final var level = context.getLevel();
        if (level.isClientSide) return InteractionResult.PASS;
        final var player = context.getPlayer();
        final var stack = context.getItemInHand();
        if (player != null && player.isShiftKeyDown()) {
            this.reset(stack,true, player);
            return InteractionResult.PASS;
        }
        final var pos = context.getClickedPos();
        final var dir = context.getClickedFace();

        final var firstPos = stack.get(LiftsDataComps.FIRST_POS);
        final var firstDir = stack.get(LiftsDataComps.FIRST_DIR);

        final var hasFirstPos = stack.has(LiftsDataComps.FIRST_POS) && firstPos != null;
        final var hasFirstDir = stack.has(LiftsDataComps.FIRST_DIR) && firstDir != null;

        if (hasFirstPos && Sable.HELPER.getContaining(level, firstPos) == null) {
            sendMessage("only_between_sub_levels", SimColors.NUH_UH_RED, player);
            return InteractionResult.FAIL;
        }
        final var subLevel = Sable.HELPER.getContaining(level, pos);
        if (subLevel == null) {
            sendMessage("only_between_sub_levels", SimColors.NUH_UH_RED, player);
            return InteractionResult.FAIL;
        }
        final var maxRange = SimConfigService.INSTANCE.server().assembly.mergingGlueRange.getF() * 6f;
        if (hasFirstPos && Sable.HELPER.distanceSquaredWithSubLevels(level, Vec3.atCenterOf(pos), firstPos.getCenter()) > maxRange * maxRange) {
            sendMessage("out_of_range", SimColors.NUH_UH_RED, player);
            return InteractionResult.FAIL;
        }
        final var relative = pos.relative(dir);
        if (hasFirstPos && hasFirstDir && firstPos.relative(firstDir).equals(relative)) {
            sendMessage("same_block", SimColors.NUH_UH_RED, player);
            return InteractionResult.FAIL;
        }
        if (!level.getBlockState(relative).canBeReplaced()) {
            sendMessage("block_exists", SimColors.NUH_UH_RED, player);
            return InteractionResult.FAIL;
        }
        if (hasFirstDir && (firstDir.getAxis().isHorizontal() != dir.getAxis().isHorizontal() || (dir.getAxis().isVertical() && dir == firstDir))) {
            sendMessage("invalid_directions", SimColors.NUH_UH_RED, player);
            return InteractionResult.FAIL;
        }
        if (hasFirstPos && subLevel == Sable.HELPER.getContaining(level, firstPos)) {
            sendMessage("same_sub_level", SimColors.NUH_UH_RED, player);
            return InteractionResult.FAIL;
        }

        if (!canSupportGlue(level, pos, dir)) {
            if (!hasFirstPos) {
                stack.set(LiftsDataComps.FIRST_POS, pos);
                stack.set(LiftsDataComps.FIRST_DIR, dir);
                return InteractionResult.SUCCESS;
            } else if (hasFirstDir && !firstPos.relative(firstDir).equals(relative)) { //we are connecting!
                handle(level, firstPos, pos, firstDir, dir);
                reset(stack, false, player);
                return InteractionResult.SUCCESS;
            }
        } else if (hasFirstPos) sendMessage("not_enough_support", SimColors.NUH_UH_RED, player);
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (stack.is(LiftsItems.PHYSIC_WELDER.get()) && (player.isShiftKeyDown() || Item.getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE).getType() == HitResult.Type.MISS)) {
            reset(stack, true, player);
            return InteractionResultHolder.pass(stack);
        }
        return super.use(level, player, hand);
    }

    public void handle(final Level level, final BlockPos parentPos, final BlockPos childPos, final Direction parentFacing, final Direction childFacing) {
        final var distanceSquared = Sable.HELPER.distanceSquaredWithSubLevels(level, parentPos.getCenter(), childPos.getCenter());
        final var mergingGlueRange = SimConfigService.INSTANCE.server().assembly.mergingGlueRange.getF() * 6f;

        if (distanceSquared > mergingGlueRange * mergingGlueRange) return;

        final BlockPos parentRelative = parentPos.relative(parentFacing);
        final BlockPos childRelative = childPos.relative(childFacing);

        final SubLevel parentSubLevel = Sable.HELPER.getContaining(level, parentRelative);
        final SubLevel childSubLevel = Sable.HELPER.getContaining(level, childRelative);
        if (parentSubLevel == null || childSubLevel == null) return;

        final var controller = this.addMergingGlue(level, parentRelative, childRelative, parentFacing);
        final var partner = this.addMergingGlue(level, childRelative, parentRelative, childFacing);

        if (controller == null || partner == null) {
            level.setBlockAndUpdate(parentRelative, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(childRelative, Blocks.AIR.defaultBlockState());
            return;
        }
        controller.startControlling(partner);
    }

    private MergingGlueBlockEntity addMergingGlue(final Level level, final BlockPos placedPos, final BlockPos childPos, final Direction facing) {
        final var newState = SimBlocks.MERGING_GLUE.getDefaultState();
        if (level.setBlockAndUpdate(placedPos, newState.setValue(MergingGlueBlock.FACING, facing))) {
            if (!(level.getBlockEntity(placedPos) instanceof MergingGlueBlockEntity be)) return null;
            be.setPartnerPos(childPos);
            be.notifyUpdate();
            return be;
        }
        return null;
    }

    public void reset(final ItemStack stack, final boolean sayMessage, @Nullable Player player) {
        if (sayMessage && stack.has(LiftsDataComps.FIRST_POS)) sendMessage("connection_terminated", SimColors.DISCARDABLE_ORANGE, player);
        stack.remove(LiftsDataComps.FIRST_POS);
        stack.remove(LiftsDataComps.FIRST_DIR);
    }

    public static void sendMessage(final String message, final int color, @Nullable Player player) {
        if (player == null) return;
        SimLang.translate("merging_glue." + message).color(color).sendStatus(player);
    }

    protected static boolean canSupportGlue(final Level level, final BlockPos pos, final Direction normal) {
        return level.getBlockState(pos).getBlockSupportShape(level, pos).getFaceShape(normal).isEmpty();
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level world, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || newStack.getItem() != oldStack.getItem();
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity, InteractionHand hand) {
        return true;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    @Nullable
    public HumanoidModel.ArmPose getArmPose(ItemStack stack, AbstractClientPlayer player, InteractionHand hand) {
        if (!player.swinging) return HumanoidModel.ArmPose.CROSSBOW_HOLD;
        return null;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new PhysicWelderRenderer()));
    }
}

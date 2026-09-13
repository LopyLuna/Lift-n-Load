package dev.lopyluna.create_lnl.content.items.gravity_grip;

import com.simibubi.create.foundation.item.CustomArmPoseItem;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import dev.lopyluna.create_lnl.content.items.gravity_grip.packets.GripDeny;
import dev.lopyluna.create_lnl.content.utils.LiftUtils;
import dev.lopyluna.create_lnl.register.LiftsItems;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.service.SimConfigService;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@SuppressWarnings("removal")
public class GravityGripItem extends Item implements CustomArmPoseItem {
    private static final Map<UUID, Grab> GRABS = new Object2ObjectOpenHashMap<>();

    public GravityGripItem(Properties properties) {
        super(properties);
    }

    public static boolean isHolding(Player player) {
        return player.getMainHandItem().is(LiftsItems.GRAVITY_GRIP) || player.getOffhandItem().is(LiftsItems.GRAVITY_GRIP);
    }

    public static InteractionHand handOf(Player player) {
        return player.getMainHandItem().is(LiftsItems.GRAVITY_GRIP) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    public static double maxReach(Player player) {
        var attribute = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        return Math.max(2.5, (attribute == null ? 4.5 : attribute.getValue()) + 2);
    }

    public static double slipRange(Player player) {
        return maxReach(player) * 0.75;
    }

    public static boolean slipped(Player player, Vector3dc borne, Vector3dc goal) {
        return borne.distanceSquared(goal) > Mth.square(slipRange(player));
    }

    public static boolean carrying(Player player, SubLevel sub) {
        var standing = Sable.HELPER.getTrackingSubLevel(player);
        return standing != null && LiftUtils.getConnectedGroupIds(sub).contains(standing.getUniqueId());
    }

    @Nullable
    public static SubLevel resolve(Level level, UUID id) {
        var container = SubLevelContainer.getContainer(level);
        return container == null ? null : container.getSubLevel(id);
    }

    public static boolean immovable(double mass) {
        return mass > 150;
    }

    public static boolean immovable(SubLevel sub) {
        return sub instanceof ServerSubLevel server && immovable(server.getMassTracker().getMass());
    }

    public static void sync(Player player, UUID id, Vector3dc anchor, Vector3dc goal, Quaterniondc frame, double grip) {
        if (!isHolding(player) || player.isSpectator()) {
            deny(player);
            return;
        }
        var sub = resolve(player.level(), id);
        if (!(sub instanceof ServerSubLevel server) || server.isRemoved() || carrying(player, sub)
                || !server.getPlot().contains(anchor) || immovable(server)) {
            deny(player);
            return;
        }
        var grab = GRABS.get(player.getUUID());
        if (grab != null && !grab.subLevel.equals(id)) {
            grab.release();
            grab = null;
        }
        if (grab == null) GRABS.put(player.getUUID(), grab = new Grab(id, player.level().dimension(), anchor, goal));
        grab.aim(goal, frame, grip);
    }

    public static void release(Player player) {
        var grab = GRABS.remove(player.getUUID());
        if (grab != null) grab.release();
    }

    private static void deny(Player player) {
        release(player);
        if (player instanceof ServerPlayer server) CatnipServices.NETWORK.sendToClient(server, GripDeny.INSTANCE);
    }

    public static void tick(ServerLevel level) {
        if (GRABS.isEmpty()) return;
        var iter = GRABS.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            var grab = entry.getValue();
            if (!grab.dimension.equals(level.dimension())) continue;
            var player = level.getPlayerByUUID(entry.getKey());
            if (player != null && grab.holds(player)) continue;
            grab.release();
            iter.remove();
            if (player instanceof ServerPlayer server) CatnipServices.NETWORK.sendToClient(server, GripDeny.INSTANCE);
        }
    }

    public static void physicTick(SubLevelPhysicsSystem system) {
        if (GRABS.isEmpty()) return;
        var level = system.getLevel();
        if (level == null) return;
        for (var grab : GRABS.values()) {
            if (!grab.dimension.equals(level.dimension())) continue;
            if (resolve(level, grab.subLevel) instanceof ServerSubLevel sub && !sub.isRemoved())
                grab.physicsTick(level, sub, system.getPartialPhysicsTick());
            else grab.release();
        }
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
    public HumanoidModel.@Nullable ArmPose getArmPose(ItemStack stack, AbstractClientPlayer player, InteractionHand hand) {
        return HumanoidModel.ArmPose.CROSSBOW_HOLD;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new GravityGripRenderer()));
    }

    public enum HandState {
        POINT,
        CLOSED,
        HOLD,
        UP,
        DOWN
    }

    private static class Grab {
        private final UUID subLevel;
        private final ResourceKey<Level> dimension;
        private final Vector3d anchor = new Vector3d();
        private final Vector3d goal = new Vector3d();
        private final Vector3d last = new Vector3d();
        private final Quaterniond frame = new Quaterniond();
        private double grip;
        @Nullable private PhysicsConstraintHandle constraint;

        private Grab(UUID subLevel, ResourceKey<Level> dimension, Vector3dc anchor, Vector3dc goal) {
            this.subLevel = subLevel;
            this.dimension = dimension;
            this.anchor.set(anchor);
            this.goal.set(goal);
            this.last.set(goal);
        }

        private void aim(Vector3dc goal, Quaterniondc frame, double grip) {
            this.last.set(this.goal);
            this.goal.set(goal);
            this.frame.set(frame);
            this.grip = grip;
        }

        private boolean holds(Player player) {
            if (player.isDeadOrDying() || player.isSpectator() || !isHolding(player)) return false;
            var sub = resolve(player.level(), subLevel);
            if (sub == null || sub.isRemoved() || carrying(player, sub)) return false;
            var eye = player.getEyePosition();
            if (goal.distanceSquared(eye.x, eye.y, eye.z) > Mth.square(maxReach(player) + 2)) return false;
            return !slipped(player, sub.logicalPose().transformPosition(anchor, new Vector3d()), goal);
        }

        private void physicsTick(ServerLevel level, ServerSubLevel sub, double partial) {
            release();
            var container = SubLevelContainer.getContainer(level);
            if (container == null) return;
            var aim = last.lerp(goal, partial, new Vector3d());
            if (container.inBounds(aim)) return;
            var pipeline = container.physicsSystem().getPipeline();
            if (pipeline == null) return;

            constraint = pipeline.addConstraint(null, sub, new FreeConstraintConfiguration(aim, anchor, frame));
            if (constraint == null) return;

            var maxForce = SimConfigService.INSTANCE.server().physics.handleMaxForce.getF();
            var inertia = sub.getMassTracker().getInertiaTensor();
            var swing = Math.max(0.05, (inertia.m00() + inertia.m11() + inertia.m22()) / 3);
            var authority = Math.min(1, 3 / Math.max(1, sub.getMassTracker().getMass()));
            var stiffness = 36 * swing * grip * authority;
            var damping = swing * (0.6 + 8 * grip);
            for (var axis : ConstraintJointAxis.LINEAR) constraint.setMotor(axis, 0, 240, 30, true, maxForce);
            for (var axis : ConstraintJointAxis.ANGULAR) constraint.setMotor(axis, 0, stiffness, damping, false, 0);
        }

        private void release() {
            if (constraint == null) return;
            if (constraint.isValid()) constraint.remove();
            constraint = null;
        }
    }
}

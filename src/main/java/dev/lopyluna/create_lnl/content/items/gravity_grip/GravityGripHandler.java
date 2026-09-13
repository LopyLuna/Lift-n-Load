package dev.lopyluna.create_lnl.content.items.gravity_grip;

import com.simibubi.create.AllSpecialTextures;
import dev.lopyluna.create_lnl.content.items.gravity_grip.packets.GripSync;
import dev.lopyluna.create_lnl.content.utils.LiftUtils;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.mass.MassTracker;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.LevelAccelerator;
import dev.simulated_team.simulated.index.SimClickInteractions;
import dev.simulated_team.simulated.index.SimKeys;
import dev.simulated_team.simulated.index.SimSoundEvents;
import dev.simulated_team.simulated.service.SimConfigService;
import dev.simulated_team.simulated.util.click_interactions.InteractCallback;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.Nullable;
import org.joml.*;
import org.lwjgl.glfw.GLFW;

import java.lang.Math;
import java.util.List;
import java.util.UUID;

public class GravityGripHandler implements InteractCallback {
    private static final GravityGripHandler INSTANCE = new GravityGripHandler();

    private final Vector3d anchor = new Vector3d();
    private final Vector3d goal = new Vector3d();
    private final Quaterniond frame = new Quaterniond();
    private final Quaternionf cube = new Quaternionf();
    private final Quaternionf cubeOld = new Quaternionf();
    private InteractionHand hand = InteractionHand.MAIN_HAND;
    @Nullable private ItemStack stack;
    @Nullable private UUID held;
    @Nullable private BlockPos hover;
    private double reach;
    private double drawn;
    private double spin;
    private double spinOld;
    private double cog;
    private double cogOld;
    private double cogSpin = 5;
    private double grip;
    private boolean steering;
    private boolean clenched;

    public static void init() {
        SimClickInteractions.register(INSTANCE);
    }

    public static GravityGripItem.HandState stateOf(Player player, ItemStack stack) {
        if (player.getItemInHand(INSTANCE.hand) != stack) return GravityGripItem.HandState.POINT;
        if (INSTANCE.held != null) return INSTANCE.stack == stack ? GravityGripItem.HandState.HOLD : GravityGripItem.HandState.POINT;
        return INSTANCE.clenched ? GravityGripItem.HandState.CLOSED : GravityGripItem.HandState.POINT;
    }

    public static float spin(float partial) {
        return (float) (Mth.lerp(partial, INSTANCE.spinOld, INSTANCE.spin) % 360);
    }

    public static float cog(float partial) {
        return (float) (Mth.lerp(partial, INSTANCE.cogOld, INSTANCE.cog) % 360);
    }

    public static Quaternionf cube(float partial) {
        return new Quaternionf(INSTANCE.cubeOld).slerp(INSTANCE.cube, partial);
    }

    public static void denied() {
        var player = Minecraft.getInstance().player;
        if (INSTANCE.held == null || player == null) return;
        INSTANCE.forget();
        player.playSound(SimSoundEvents.STAFF_EXTINGUISH.event(), 0.25f, 0.5f);
        player.playSound(SimSoundEvents.ASSEMBLER_FAIL.event(), 0.125f, 0.75f);
        player.playSound(SimSoundEvents.ASSEMBLER_FAIL.event(), 0.125f, 2f);
    }

    @Override
    public Result onUse(int modifiers, int action, KeyMapping rightKey) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (mc.level == null || player == null || player.isSpectator() || !GravityGripItem.isHolding(player)) return InteractCallback.super.onUse(modifiers, action, rightKey);
        if (action == GLFW.GLFW_RELEASE) clenched = false;
        if (action != GLFW.GLFW_PRESS) return new Result(true);
        clenched = true;
        hand = GravityGripItem.handOf(player);
        if (held != null) drop(player);
        else grab(mc.level, player);
        return new Result(true);
    }

    @Override
    public Result onScroll(double deltaX, double deltaY) {
        var player = Minecraft.getInstance().player;
        if (held == null || player == null) return InteractCallback.super.onScroll(deltaX, deltaY);
        double sensitivity = SimConfigService.INSTANCE.client().itemConfig.physicsStaffScrollSensitivity.get();
        var sprint = Minecraft.getInstance().options.keySprint.isDown();
        var step = Mth.clamp(Math.sqrt(reach / 10), 1, 5) * (sprint ? 4 : 1);
        reach = Mth.clamp(reach + deltaY * sensitivity * step, 2, GravityGripItem.maxReach(player));
        return new Result(true);
    }

    @Override
    public Result onMouseMove(double yaw, double pitch) {
        var player = Minecraft.getInstance().player;
        if (held == null || player == null || !SimKeys.ROTATE_MODE.isPressed()) return InteractCallback.super.onMouseMove(yaw, pitch);
        if (yaw != 0 || pitch != 0) {
            double sensitivity = SimConfigService.INSTANCE.client().itemConfig.physicsStaffRotateSensitivity.get();
            var axis = player.calculateViewVector(0, player.getYRot() - 90);
            frame.rotateLocalY(Math.toRadians(yaw) * sensitivity);
            frame.premul(new Quaterniond(new AxisAngle4d(Math.toRadians(-pitch) * sensitivity, axis.x, axis.y, axis.z)));
            frame.normalize();
            spin += (yaw - pitch) * sensitivity;
            steering = true;
        }
        return new Result(true);
    }

    @Override
    public void clientTick(Level level, LocalPlayer player) {
        cogOld = cog;
        spinOld = spin;
        spin *= 0.95;
        cubeOld.set(cube);
        var steer = steering;
        steering = false;
        cogSpin = Mth.lerp(0.12, cogSpin, steer ? 0 : 5);
        cog += cogSpin;
        if (cog >= 360) {
            cog -= 360;
            cogOld -= 360;
        }

        if (!(level instanceof ClientLevel client) || player == null) {
            forget();
            return;
        }
        var equipped = held == null ? GravityGripItem.isHolding(player) : player.getItemInHand(hand) == stack;
        if (Minecraft.getInstance().screen != null) clenched = false;
        if (player.isSpectator() || !equipped) {
            if (held != null) drop(player);
            clenched = false;
            hover = null;
            return;
        }
        if (held == null) {
            hand = GravityGripItem.handOf(player);
            var hit = pick(client, player, 1);
            var sub = hit == null ? null : Sable.HELPER.getContaining(client, hit.getLocation());
            hover = sub == null || sub.isRemoved() || GravityGripItem.carrying(player, sub) ? null : hit.getBlockPos();
            outline();
            return;
        }

        var sub = GravityGripItem.resolve(client, held);
        if (sub == null || sub.isRemoved() || GravityGripItem.carrying(player, sub) || touching(client, player, sub)) {
            drop(player);
            return;
        }

        drawn = Mth.lerp(0.25, drawn, reach);
        grip = Mth.lerp(0.2, grip, steer ? 1 : 0);
        if (steer) restrain(sub.logicalPose().orientation());
        else frame.slerp(sub.logicalPose().orientation(), 1 - grip);
        cube.slerp(orientationOf(sub), 0.35f);

        var eye = player.getEyePosition();
        var look = player.getLookAngle();
        goal.set(eye.x + look.x * drawn, eye.y + look.y * drawn, eye.z + look.z * drawn);

        if (GravityGripItem.slipped(player, sub.logicalPose().transformPosition(anchor, new Vector3d()), goal)) {
            drop(player);
            return;
        }

        hover = BlockPos.containing(anchor.x, anchor.y, anchor.z);
        outline();
        CatnipServices.NETWORK.sendToServer(new GripSync(held, new Vector3d(anchor), new Vector3d(goal), new Quaterniond(frame), grip));
    }

    private void grab(ClientLevel level, LocalPlayer player) {
        var hit = pick(level, player, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
        if (hit == null) return;
        var sub = Sable.HELPER.getContaining(level, hit.getLocation());
        if (sub == null || sub.isRemoved() || GravityGripItem.carrying(player, sub) || touching(level, player, sub)) return;
        if (GravityGripItem.immovable(massOf(level, sub))) return;

        held = sub.getUniqueId();
        stack = player.getItemInHand(hand);
        anchor.set(JOMLConversion.atCenterOf(hit.getBlockPos()));
        frame.set(sub.logicalPose().orientation());
        grip = 0;
        cube.set(orientationOf(sub));
        cubeOld.set(cube);
        var borne = sub.logicalPose().transformPosition(anchor, new Vector3d());
        var eye = player.getEyePosition();
        reach = Mth.clamp(borne.distance(eye.x, eye.y, eye.z), 2, GravityGripItem.maxReach(player));
        drawn = reach;
        player.playSound(SimSoundEvents.STAFF_IGNITE.event(), 0.25f, 0.7f);
        player.playSound(SimSoundEvents.ASSEMBLER_TICK.event(), 0.125f, 0.75f);
        player.playSound(SimSoundEvents.ASSEMBLER_TICK.event(), 0.125f, 2f);
        sparkle(level);
    }

    private void drop(Player player) {
        forget();
        player.playSound(SimSoundEvents.STAFF_EXTINGUISH.event(), 0.25f, 0.5f);
        player.playSound(SimSoundEvents.ASSEMBLER_FAIL.event(), 0.125f, 0.75f);
        player.playSound(SimSoundEvents.ASSEMBLER_FAIL.event(), 0.125f, 2f);
        CatnipServices.NETWORK.sendToServer(GripSync.drop());
    }

    private void forget() {
        held = null;
        stack = null;
        hover = null;
        grip = 0;
        frame.identity();
        cube.identity();
        cubeOld.identity();
    }

    private void sparkle(ClientLevel level) {
        var random = level.getRandom();
        for (var i = 0; i < 10; i++)
            level.addParticle(ParticleTypes.END_ROD,
                    anchor.x + (random.nextDouble() - 0.5) * 0.8,
                    anchor.y + (random.nextDouble() - 0.5) * 0.8,
                    anchor.z + (random.nextDouble() - 0.5) * 0.8,
                    (random.nextDouble() - 0.5) * 0.2, (random.nextDouble() - 0.5) * 0.2, (random.nextDouble() - 0.5) * 0.2);
    }

    private void restrain(Quaterniondc actual) {
        var lead = new Quaterniond(actual).conjugate().mul(frame).normalize();
        var angle = 2 * Math.acos(Mth.clamp(Math.abs(lead.w), -1, 1));
        if (angle > 0.6) frame.slerp(actual, 1 - 0.6 / angle);
    }

    private static double massOf(ClientLevel level, SubLevel sub) {
        var total = 0.0;
        for (var member : LiftUtils.getConnectedGroup(sub)) total += MassTracker.build(new LevelAccelerator(level), member.getPlot().getBoundingBox()).getMass();
        return total;
    }

    private boolean touching(ClientLevel level, LocalPlayer player, SubLevel sub) {
        var group = LiftUtils.getSublevelGroup(level, sub, SubLevel::logicalPose);
        if (group.parts().isEmpty()) return false;
        var box = player.getBoundingBox().inflate(0.02);
        var corner = new Vector3d();
        for (var part : group.parts()) {
            var local = localOf(box, part.subLevel().logicalPose(), corner).move(-part.originX(), -part.originY(), -part.originZ());
            if (Shapes.joinIsNotEmpty(part.shape(), Shapes.create(local), BooleanOp.AND)) return true;
        }
        return false;
    }

    private static AABB localOf(AABB box, Pose3dc pose, Vector3d corner) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (var i = 0; i < 8; i++) {
            corner.set((i & 1) == 0 ? box.minX : box.maxX, (i & 2) == 0 ? box.minY : box.maxY, (i & 4) == 0 ? box.minZ : box.maxZ);
            pose.transformPositionInverse(corner);
            minX = Math.min(minX, corner.x);
            minY = Math.min(minY, corner.y);
            minZ = Math.min(minZ, corner.z);
            maxX = Math.max(maxX, corner.x);
            maxY = Math.max(maxY, corner.y);
            maxZ = Math.max(maxZ, corner.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private Quaternionf orientationOf(SubLevel sub) {
        var pose = sub instanceof ClientSubLevel client ? client.renderPose() : sub.logicalPose();
        return toFloat(pose.orientation());
    }

    private static Quaternionf toFloat(Quaterniondc orientation) {
        return new Quaternionf((float) orientation.x(), (float) orientation.y(), (float) orientation.z(), (float) orientation.w());
    }

    @Nullable
    private BlockHitResult pick(ClientLevel level, LocalPlayer player, float partial) {
        var extension = (LevelPoseProviderExtension) level;
        extension.sable$pushPoseSupplier(x -> ((ClientSubLevel) x).renderPose());
        var hit = player.pick(GravityGripItem.maxReach(player), partial, false);
        extension.sable$popPoseSupplier();
        if (!(hit instanceof BlockHitResult result) || result.getType() == HitResult.Type.MISS) return null;
        return result;
    }

    private void outline() {
        if (hover == null || Minecraft.getInstance().options.hideGui) return;
        Outliner.getInstance().showCluster("gravityGripSelection", List.of(hover))
                .colored(0xBFBFBF)
                .disableLineNormals()
                .lineWidth(1 / 32f)
                .withFaceTexture(AllSpecialTextures.CHECKERED);
    }
}

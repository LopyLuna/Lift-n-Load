package dev.lopyluna.create_lnl.content.blocks.contraption_lift.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lopyluna.create_lnl.client.LiftsRenderTypes;
import dev.lopyluna.create_lnl.content.blocks.contraption_lift.DockingLiftBlock;
import dev.lopyluna.create_lnl.content.blocks.contraption_lift.DockingLiftHandler;
import dev.lopyluna.create_lnl.content.blocks.contraption_lift.LiftHolding;
import dev.lopyluna.create_lnl.content.blocks.contraption_lift.LiftPlacement;
import dev.lopyluna.create_lnl.content.utils.LiftUtils;
import dev.lopyluna.create_lnl.register.LiftsBlocks;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.util.SimColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.UUID;

public class DockingLiftGhost {
    private static @Nullable BlockPos targetPos;
    private static @Nullable SubLevel heldSubLevel;
    private static @Nullable ClientSubLevel targetSubLevel;
    private static LiftPlacement.Result result = LiftPlacement.Result.INVALID;
    private static int rotation;

    private static LiftUtils.SubLevelGroup group = LiftUtils.SubLevelGroup.EMPTY;
    private static VoxelShape shape = Shapes.empty();
    private static Vec3 defaultAnchor = Vec3.ZERO;
    private static @Nullable UUID heldId;
    private static @Nullable UUID shapeId;
    private static int shapeAge = Integer.MAX_VALUE;
    private static final Vector3d SCRATCH_A = new Vector3d();
    private static final Vector3d SCRATCH_B = new Vector3d();
    private static final Vector3d GROUP_A = new Vector3d();
    private static final Vector3d GROUP_B = new Vector3d();

    public static void tick(Minecraft mc) {
        targetPos = null;
        heldSubLevel = null;
        targetSubLevel = null;
        result = LiftPlacement.Result.INVALID;

        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null || DockingLiftHandler.holdingNoLiftItem(player)) return;

        var held = LiftHolding.held(player);
        if (held == null) {
            invalidate();
            heldId = null;
            return;
        }
        if (!held.getUniqueId().equals(heldId)) {
            heldId = held.getUniqueId();
            invalidate();
        }

        var pos = placementPos(mc, player);
        if (pos == null) return;

        targetPos = pos;
        heldSubLevel = held;
        targetSubLevel = Sable.HELPER.getContaining(level, pos) instanceof ClientSubLevel sub ? sub : null;
        updateShape(level, held);
        rotation = LiftHolding.rotation(player);
        result = LiftPlacement.solve(level, pos, held, group, rotation);
    }

    private static void updateShape(Level level, SubLevel held) {
        var sameSubLevel = held.getUniqueId().equals(shapeId);
        if (sameSubLevel && shapeAge++ < 2) return;

        var rebuilt = LiftUtils.getSublevelGroup(level, held, DockingLiftGhost::poseOf);
        shapeAge = 0;
        if (rebuilt.shape().isEmpty() && sameSubLevel && !shape.isEmpty()) return;

        shapeId = held.getUniqueId();
        group = rebuilt;
        shape = group.shape();
        var boxes = shape.toAabbs();
        var seating = group.rootBoxes().isEmpty() ? boxes : group.rootBoxes();
        defaultAnchor = shape.isEmpty() ? Vec3.ZERO : LiftPlacement.anchor(seating, boxes, false, rotation);
    }

    public static void invalidate() {
        shapeId = null;
        shapeAge = Integer.MAX_VALUE;
        group = LiftUtils.SubLevelGroup.EMPTY;
        shape = Shapes.empty();
        defaultAnchor = Vec3.ZERO;
    }

    private static Pose3dc poseOf(SubLevel subLevel) {
        return subLevel instanceof ClientSubLevel sub ? sub.renderPose() : subLevel.logicalPose();
    }

    private static Vec3 anchor() {
        return result.anchor().equals(Vec3.ZERO) ? defaultAnchor : result.anchor();
    }

    private static @Nullable BlockPos placementPos(Minecraft mc, LocalPlayer player) {
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return null;
        var hand = player.getMainHandItem().is(LiftsBlocks.CONTRAPTION_LIFT.asItem()) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        return new BlockPlaceContext(player, hand, player.getItemInHand(hand), hit).getClickedPos();
    }

    public static void render(PoseStack ps, Vec3 cam, VertexConsumerProvider buffers) {
        var pos = targetPos;
        var held = heldSubLevel;
        if (pos == null || held == null) return;

        var color = result.valid() ? 0xE6_66CCFF : SimColors.NUH_UH_RED;
        var height = result.valid() ? result.height() : 0;
        var pose = targetSubLevel == null ? null : targetSubLevel.renderPose();

        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        var last = ps.last();

        var visible = buffers.buffer(LiftsRenderTypes.GHOST_LINES);
        lift(visible, last, pose, pos, height, color);
        silhouette(visible, last, pose, pos, height, color);

        ps.popPose();
    }

    private static void lift(VertexConsumer vc, PoseStack.Pose last, @Nullable Pose3dc pose, BlockPos liftPos, double height, int color) {
        DockingLiftBlock.fullShape((float) height).forAllEdges((x1, y1, z1, x2, y2, z2) -> line(vc, last, pose,
                liftPos.getX() + x1, liftPos.getY() + y1, liftPos.getZ() + z1,
                liftPos.getX() + x2, liftPos.getY() + y2, liftPos.getZ() + z2, color));
    }

    private static void silhouette(VertexConsumer vc, PoseStack.Pose last, @Nullable Pose3dc pose, BlockPos liftPos, double height, int color) {
        var pivot = LiftPlacement.pivot(liftPos, height);
        var anchor = anchor();
        var radians = LiftPlacement.rotationRadians(rotation);
        var sin = Math.sin(radians);
        var cos = Math.cos(radians);
        var frame = group.root();
        if (frame == null) return;
        var rootPose = poseOf(frame);

        for (var part : group.parts()) {
            var member = part.subLevel();
            var memberPose = member == frame ? null : poseOf(member);

            part.shape().forAllEdges((x1, y1, z1, x2, y2, z2) -> {
                var a = toGroupSpace(x1 + part.originX(), y1 + part.originY(), z1 + part.originZ(), memberPose, rootPose, GROUP_A);
                place(a, anchor, pivot, sin, cos);
                var b = toGroupSpace(x2 + part.originX(), y2 + part.originY(), z2 + part.originZ(), memberPose, rootPose, GROUP_B);
                place(b, anchor, pivot, sin, cos);
                line(vc, last, pose, a.x, a.y, a.z, b.x, b.y, b.z, color);
            });
        }
    }

    private static void place(Vector3d point, Vec3 anchor, Vec3 pivot, double sin, double cos) {
        var dx = point.x - group.originX() - anchor.x;
        var dy = point.y - group.originY() - anchor.y;
        var dz = point.z - group.originZ() - anchor.z;
        point.set(pivot.x + LiftPlacement.rotatedX(dx, dz, sin, cos), pivot.y + dy, pivot.z + LiftPlacement.rotatedZ(dx, dz, sin, cos));
    }

    private static Vector3d toGroupSpace(double x, double y, double z, @Nullable Pose3dc memberPose, Pose3dc rootPose, Vector3d dest) {
        dest.set(x, y, z);
        if (memberPose == null) return dest;
        memberPose.transformPosition(dest);
        rootPose.transformPositionInverse(dest);
        return dest;
    }

    private static void line(VertexConsumer vc, PoseStack.Pose last, @Nullable Pose3dc pose,
                             double x1, double y1, double z1, double x2, double y2, double z2, int color) {
        SCRATCH_A.set(x1, y1, z1);
        SCRATCH_B.set(x2, y2, z2);
        if (pose != null) {
            pose.transformPosition(SCRATCH_A);
            pose.transformPosition(SCRATCH_B);
        }
        float ax = (float) SCRATCH_A.x, ay = (float) SCRATCH_A.y, az = (float) SCRATCH_A.z;
        float bx = (float) SCRATCH_B.x, by = (float) SCRATCH_B.y, bz = (float) SCRATCH_B.z;

        float dx = bx - ax, dy = by - ay, dz = bz - az;
        var length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-6f) return;
        dx /= length; dy /= length; dz /= length;
        vc.addVertex(last, ax, ay, az).setColor(color).setNormal(last, dx, dy, dz);
        vc.addVertex(last, bx, by, bz).setColor(color).setNormal(last, dx, dy, dz);
    }

    @FunctionalInterface
    public interface VertexConsumerProvider {
        VertexConsumer buffer(RenderType type);
    }
}

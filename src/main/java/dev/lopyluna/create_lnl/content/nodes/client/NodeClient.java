package dev.lopyluna.create_lnl.content.nodes.client;

import dev.lopyluna.create_lnl.content.nodes.Node;
import dev.lopyluna.create_lnl.content.nodes.loose.NodeCell;
import dev.lopyluna.create_lnl.content.nodes.NodeHosts;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import javax.annotation.Nullable;
import java.util.Set;

public class NodeClient {
    @Nullable public static Node.Key holding = null;

    public static Set<BlockPos> positions() {
        return Node.Tracker.positions();
    }

    @Nullable
    public static BlockHitResult hit(@Nullable Level level, @Nullable Player player, float pt) {
        if (player == null) return null;
        if (level == null) level = player.level();
        var start = Sable.HELPER.getEyePositionInterpolated(player, pt);
        var end = start.add(player.getViewVector(pt).scale(32));
        var clip = level.clip(new ClipContext(start, end, ClipContext.Block.valueOf("CONNECTOR"), ClipContext.Fluid.NONE, CollisionContext.empty()));
        return clip instanceof BlockHitResult hit ? hit : null;
    }

    @Nullable
    public static Node.Key pick(@Nullable Level level, @Nullable Player player, float pt) {
        return level == null || player == null ? null : pick(level, hit(level, player, pt));
    }

    @Nullable
    public static Node.Key punch(@Nullable Level level, @Nullable Player player, float pt) {
        if (level == null || player == null) return null;
        var hit = hit(level, player, pt);
        var key = pick(level, hit);
        if (key == null) return null;
        if (NodeCell.parse(key.port()) == null && !Node.Graphs.get(level).anchored(key.pos())) return null;
        var eye = Sable.HELPER.getEyePositionInterpolated(player, pt);
        var point = Sable.HELPER.projectOutOfSubLevel(level, hit.getLocation());
        var reach = player.blockInteractionRange() + 1;
        return eye.distanceToSqr(point) > reach * reach ? null : key;
    }

    @Nullable
    public static Node.Key pick(Level level, @Nullable BlockHitResult hit) {
        if (hit == null || hit.getType() == HitResult.Type.MISS) return null;
        var pos = hit.getBlockPos();
        var target = hit.getLocation();
        Node.Port best = null;
        var closest = Double.MAX_VALUE;
        for (var port : NodeHosts.ports(level, pos)) {
            var dist = port.center(pos).distanceToSqr(target);
            if (dist >= closest) continue;
            closest = dist;
            best = port;
        }
        return best == null ? null : best.key(pos);
    }

    public static Vec3 render(Level level, Vec3 pos) {
        if (!(Sable.HELPER.getContaining(level, pos) instanceof ClientSubLevel sub)) return pos;
        return JOMLConversion.toMojang(sub.renderPose().transformPosition(JOMLConversion.toJOML(pos)));
    }

    public static Vec3 render(Level level, BlockPos pos, Node.Port port) {
        return render(level, port.center(pos));
    }
}

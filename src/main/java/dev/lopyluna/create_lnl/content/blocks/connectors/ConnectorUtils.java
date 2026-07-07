package dev.lopyluna.create_lnl.content.blocks.connectors;

import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;

import javax.annotation.Nullable;

public class ConnectorUtils {

    public static BlockHitResult hit(@Nullable Level level, @Nullable Player player, float pt) {
        if (player == null) return null;
        if (level == null) level = player.level();
        var start = Sable.HELPER.getEyePositionInterpolated(player, pt);
        var end = start.add(player.getViewVector(pt).scale(32));
        return level.clip(new ClipContext(start, end, ClipContext.Block.valueOf("CONNECTOR"), ClipContext.Fluid.NONE, CollisionContext.empty())) instanceof BlockHitResult hit ? hit : null;
    }

    public static boolean canConnect(Level level, BlockPos aPos, IConnection<?> a, BlockPos bPos, IConnection<?> b) {
        return a.canConnect(level, bPos, b) && b.canConnect(level, aPos, a);
    }
}

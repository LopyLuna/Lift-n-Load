package dev.lopyluna.create_lnl.content.nodes.hosts;

import dev.lopyluna.create_lnl.content.nodes.Node;
import dev.lopyluna.create_lnl.content.nodes.NodeHosts;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class PlugHost implements Node.Host {
    public static final PlugHost INSTANCE = new PlugHost();
    private static final List<Node.Port> BOTH = List.of(Node.Port.node("node", Node.Channel.REDSTONE, Vec3.ZERO));
    private static final List<Node.Port> IN = List.of(Node.Port.in("node", Node.Channel.REDSTONE, Vec3.ZERO));
    private static final List<Node.Port> OUT = List.of(Node.Port.out("node", Node.Channel.REDSTONE, Vec3.ZERO));

    @Override
    public List<Node.Port> ports(Level level, BlockPos pos, BlockState state) {
        var flow = NodeHosts.flow(state);
        if (flow == null) return List.of();
        return switch (flow) {
            case IN -> IN;
            case OUT -> OUT;
            case BOTH -> BOTH;
        };
    }

    @Override
    public boolean implicit() {
        return false;
    }

    @Override
    public Node.Signal read(Level level, BlockPos pos, BlockState state, Node.Port port) {
        var power = 0;
        for (var dir : Iterate.directions) power = Math.max(power, level.getSignal(pos.relative(dir), dir));
        if (state.isSignalSource()) for (var dir : Iterate.directions) power = Math.max(power, state.getSignal(level, pos, dir));
        return Node.Channel.REDSTONE.of(power);
    }

    @Override
    public void write(Level level, BlockPos pos, BlockState state, Node.Port port, Node.Signal signal) {
        Node.Redstone.set(level, pos, signal.strength());
    }
}

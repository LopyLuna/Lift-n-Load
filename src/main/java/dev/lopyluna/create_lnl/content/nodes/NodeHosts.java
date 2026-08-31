package dev.lopyluna.create_lnl.content.nodes;

import dev.lopyluna.create_lnl.content.nodes.hosts.KineticHost;
import dev.lopyluna.create_lnl.content.nodes.loose.LooseNodes;
import dev.lopyluna.create_lnl.content.nodes.loose.NodeCell;
import dev.lopyluna.create_lnl.content.nodes.hosts.PlugHost;
import dev.lopyluna.create_lnl.content.nodes.hosts.WheelMountHost;
import dev.lopyluna.create_lnl.register.LiftsTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class NodeHosts {
    private static final Map<Block, Node.Host> BLOCKS = new IdentityHashMap<>();
    private static final Map<BlockEntityType<?>, Node.Host> ENTITIES = new IdentityHashMap<>();
    private static final List<Function<BlockEntity, Node.Host>> ADAPTERS = new ArrayList<>();
    private static Node.Host fallback = PlugHost.INSTANCE;

    public static void register() {
        if (!ADAPTERS.isEmpty()) return;
        adapter(WheelMountHost::of);
        adapter(KineticHost::of);
    }

    public static void block(Block block, Node.Host host) {
        BLOCKS.put(block, host);
    }

    public static void blockEntity(BlockEntityType<?> type, Node.Host host) {
        ENTITIES.put(type, host);
    }

    public static void adapter(Function<BlockEntity, Node.Host> adapter) {
        ADAPTERS.add(adapter);
    }

    public static void fallback(Node.Host host) {
        fallback = host;
    }

    @Nullable
    public static Node.Host find(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.isAir()) return null;
        if (state.getBlock() instanceof Node.Host host) return host;
        var direct = BLOCKS.get(state.getBlock());
        if (direct != null) return direct;
        var be = level.getBlockEntity(pos);
        if (be instanceof Node.Host host) return host;
        if (be != null) {
            var typed = ENTITIES.get(be.getType());
            if (typed != null) return typed;
            for (var adapter : ADAPTERS) {
                var host = adapter.apply(be);
                if (host != null) return host;
            }
        }
        return fallback;
    }

    public static List<Node.Port> ports(Level level, BlockPos pos) {
        var node = Node.Graphs.get(level).node(pos);
        var loose = node == null ? List.<Node.Port>of() : LooseNodes.ports(level, pos, node);
        var host = find(level, pos);
        if (host == null || (!host.implicit() && (node == null || !node.anchored))) return loose;
        var own = host.ports(level, pos, level.getBlockState(pos));
        if (loose.isEmpty()) return own;
        if (own.isEmpty()) return loose;
        var all = new ArrayList<Node.Port>(own.size() + loose.size());
        all.addAll(own);
        all.addAll(loose);
        return all;
    }

    public static Node.Signal read(Level level, BlockPos pos, Node.Port port) {
        if (NodeCell.parse(port.id()) != null) return LooseNodes.read(level, pos, port);
        var host = find(level, pos);
        return host == null ? port.channel().zero() : host.read(level, pos, level.getBlockState(pos), port);
    }

    public static void write(Level level, BlockPos pos, Node.Port port, Node.Signal signal) {
        if (NodeCell.parse(port.id()) != null) return;
        var host = find(level, pos);
        if (host != null) host.write(level, pos, level.getBlockState(pos), port, signal);
    }

    @Nullable
    public static Node.Port port(Level level, Node.Key key) {
        for (var port : ports(level, key.pos())) if (port.id().equals(key.port())) return port;
        return null;
    }

    public static boolean pluggable(Level level, BlockPos pos) {
        var host = find(level, pos);
        return host != null && !host.implicit() && flow(level.getBlockState(pos)) != null;
    }

    @Nullable
    public static Node.Flow flow(BlockState state) {
        if (state.is(LiftsTags.NODE_BOTH)) return Node.Flow.BOTH;
        if (state.is(LiftsTags.NODE_INPUT)) return Node.Flow.IN;
        if (state.is(LiftsTags.NODE_OUTPUT)) return Node.Flow.OUT;
        return null;
    }
}

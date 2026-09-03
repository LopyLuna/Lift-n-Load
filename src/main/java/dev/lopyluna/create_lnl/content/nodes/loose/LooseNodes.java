package dev.lopyluna.create_lnl.content.nodes.loose;

import dev.lopyluna.create_lnl.content.nodes.Node;
import dev.lopyluna.create_lnl.content.nodes.NodeGraph;
import dev.lopyluna.create_lnl.content.nodes.NodeHosts;
import dev.lopyluna.create_lnl.register.LiftsItems;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LooseNodes {
    public static boolean any;
    private static boolean updating;

    public static boolean supported(LevelReader level, BlockPos pos, int id) {
        var anchor = NodeCell.depth(id) == 0 ? pos.relative(NodeCell.face(id).getOpposite()) : pos;
        var state = level.getBlockState(anchor);
        if (state.is(Blocks.MOVING_PISTON)) return true;
        var support = state.getBlockSupportShape(level, anchor);
        return !support.isEmpty() && !Shapes.joinIsNotEmpty(support, NodeCell.footprint(id), BooleanOp.ONLY_SECOND);
    }

    public static boolean vacant(LevelReader level, BlockPos pos, int id) {
        var state = level.getBlockState(pos);
        if (state.is(Blocks.MOVING_PISTON)) return true;
        var occupied = state.getCollisionShape(level, pos);
        return occupied.isEmpty() || !Shapes.joinIsNotEmpty(occupied, NodeCell.shape(id), BooleanOp.AND);
    }

    public static boolean place(Level level, BlockPos pos, int id) {
        if (!supported(level, pos, id) || !vacant(level, pos, id)) return false;
        var graph = Node.Graphs.get(level);
        var node = graph.node(pos);
        if (node != null && node.loose.contains(id)) return false;
        graph.attach(pos, id);
        Node.Graphs.flush(level);
        return true;
    }

    public static void pull(Level level, BlockPos pos, int id) {
        var graph = Node.Graphs.get(level);
        var node = graph.node(pos);
        if (node == null || !node.loose.contains(id)) return;
        graph.detach(level, pos, id);
        Node.Graphs.flush(level);
        give(level, pos, 1);
    }

    public static void update(Level level, BlockPos pos) {
        if (!any || updating || level.isClientSide) return;
        var graph = Node.Graphs.get(level);
        if (graph.nodes.isEmpty()) return;
        updating = true;
        try {
            validate(level, graph, pos);
            for (var dir : Direction.values()) validate(level, graph, pos.relative(dir));
        } finally {
            updating = false;
        }
        Node.Graphs.flush(level);
    }

    public static void validate(Level level, NodeGraph graph, BlockPos pos) {
        var node = graph.node(pos);
        if (node == null || node.loose.isEmpty()) return;
        var loose = new ArrayList<Integer>();
        for (var id : node.loose) if (!supported(level, pos, id) || !vacant(level, pos, id)) loose.add(id);
        if (loose.isEmpty()) return;
        for (var id : loose) graph.detach(level, pos, id);
        give(level, pos, loose.size());
    }

    public static List<Node.Port> ports(Level level, BlockPos pos, Node node) {
        if (node.loose.isEmpty()) return List.of();
        var graph = Node.Graphs.get(level);
        var ports = new ArrayList<Node.Port>(node.loose.size());
        for (var id : node.loose) {
            var key = new Node.Key(pos, NodeCell.id(id));
            ports.add(Node.Port.node(key.port(), Node.Channel.REDSTONE, NodeCell.offset(id)).colored(graph.resolving ? NodeCell.DEFAULT_COLOR : color(level, key)));
        }
        return ports;
    }

    public static Node.Signal read(Level level, BlockPos pos, Node.Port port) {
        var node = Node.Graphs.get(level).node(pos);
        var value = node == null ? null : node.applied.get(port.id());
        return value == null ? port.channel().zero() : value;
    }

    public static int color(Level level, Node.Key start) {
        var graph = Node.Graphs.get(level);
        var time = level.getGameTime();
        if (graph.colorStamp != time) {
            graph.colorStamp = time;
            graph.colors.clear();
        }
        var cached = graph.colors.get(start);
        if (cached != null) return cached;
        graph.resolving = true;
        try {
            var color = resolve(level, graph, start);
            graph.colors.put(start, color);
            return color;
        } finally {
            graph.resolving = false;
        }
    }

    private static int resolve(Level level, NodeGraph graph, Node.Key start) {
        var seen = new HashSet<Node.Key>();
        var queue = new ArrayDeque<Node.Key>();
        seen.add(start);
        expand(graph, start, queue, seen);
        var steps = 0;
        while (!queue.isEmpty() && steps++ < 64) {
            var key = queue.poll();
            if (NodeCell.parse(key.port()) != null) {
                expand(graph, key, queue, seen);
                continue;
            }
            if (!level.isLoaded(key.pos())) continue;
            var port = NodeHosts.port(level, key);
            if (port != null) return port.color();
        }
        return NodeCell.DEFAULT_COLOR;
    }

    private static void expand(NodeGraph graph, Node.Key key, Deque<Node.Key> queue, Set<Node.Key> seen) {
        for (var link : graph.outputs(key)) if (seen.add(link)) queue.add(link);
        for (var link : graph.inputs(key)) if (seen.add(link)) queue.add(link);
    }

    public static void give(Level level, BlockPos pos, int count) {
        var stack = new ItemStack(LiftsItems.NODE_PLUG.get(), count);
        var point = Sable.HELPER.projectOutOfSubLevel(level, pos.getCenter());
        var player = level.getNearestPlayer(point.x, point.y, point.z, 6, false);
        if (player != null) {
            if (player.hasInfiniteMaterials()) return;
            player.getInventory().placeItemBackInInventory(stack);
        } else Block.popResource(level, pos, stack);
    }
}

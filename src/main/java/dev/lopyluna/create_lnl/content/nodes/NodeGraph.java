package dev.lopyluna.create_lnl.content.nodes;

import dev.lopyluna.create_lnl.content.nodes.loose.LooseNodes;
import dev.lopyluna.create_lnl.content.nodes.loose.NodeCell;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@SuppressWarnings("UnusedReturnValue")
public class NodeGraph {
    public static final Comparator<Node.Key> ORDER = Comparator.comparingLong((Node.Key key) -> key.pos().asLong()).thenComparing(Node.Key::port);

    public final Map<BlockPos, Node> nodes = new HashMap<>();
    public final Map<Node.Key, Integer> colors = new HashMap<>();
    public long colorStamp = -1;
    public boolean resolving;
    public final Set<BlockPos> pending = new HashSet<>();
    @Nullable public Consumer<BlockPos> listener;

    @Nullable
    public Node node(BlockPos pos) {
        return nodes.get(pos);
    }

    public Node create(BlockPos pos) {
        return nodes.computeIfAbsent(pos.immutable(), p -> new Node());
    }

    public boolean anchored(BlockPos pos) {
        var node = nodes.get(pos);
        return node != null && node.anchored;
    }

    public Set<Node.Key> outputs(Node.Key key) {
        var node = nodes.get(key.pos());
        if (node == null) return Set.of();
        var set = node.outputs.get(key.port());
        return set == null ? Set.of() : set;
    }

    public List<Node.Key> inputs(Node.Key key) {
        var node = nodes.get(key.pos());
        if (node == null) return List.of();
        var set = node.inputs.get(key.port());
        if (set == null || set.isEmpty()) return List.of();
        var list = new ArrayList<>(set);
        list.sort(Comparator.comparingInt((Node.Key link) -> node.order.getOrDefault(link, Integer.MAX_VALUE)).thenComparing(ORDER));
        return list;
    }

    public boolean linked(Node.Key a, Node.Key b) {
        return outputs(a).contains(b) || outputs(b).contains(a);
    }

    public void attach(BlockPos pos, int id) {
        LooseNodes.any = true;
        create(pos).loose.add(id);
        changed(pos);
    }

    public void detach(Level level, BlockPos pos, int id) {
        var node = nodes.get(pos);
        if (node == null || !node.loose.remove(id)) return;
        var key = new Node.Key(pos, NodeCell.id(id));
        for (var link : List.copyOf(node.outputs(key.port()))) unlink(level, key, link);
        for (var link : List.copyOf(node.inputs(key.port()))) unlink(level, key, link);
        node.applied.remove(key.port());
        node.published.remove(key.port());
        if (nodes.containsKey(pos) && node.empty()) remove(level, pos);
        changed(pos);
    }

    public void cut(Level level, Node.Key key) {
        var node = nodes.get(key.pos());
        if (node == null) return;
        for (var link : List.copyOf(node.outputs(key.port()))) unlink(level, key, link);
        for (var link : List.copyOf(node.inputs(key.port()))) unlink(level, key, link);
    }

    public void anchor(BlockPos pos) {
        create(pos).anchored = true;
        changed(pos);
    }

    public void unanchor(Level level, BlockPos pos) {
        var node = nodes.get(pos);
        if (node == null || !node.anchored) return;
        node.anchored = false;
        node.applied.clear();
        node.published.clear();
        Node.Redstone.set(level, pos, 0);
        changed(pos);
    }

    public int plugs(BlockPos pos) {
        var node = nodes.get(pos);
        if (node == null) return 0;
        return (node.anchored ? 1 : 0) + node.loose.size();
    }

    public void remove(Level level, BlockPos pos) {
        if (!nodes.containsKey(pos)) return;
        reset(level, pos);
        Node.Redstone.set(level, pos, 0);
        var node = nodes.remove(pos);
        for (var set : node.outputs.values()) for (var link : set) strip(link.pos(), pos);
        for (var set : node.inputs.values()) for (var link : set) strip(link.pos(), pos);
        changed(pos);
    }

    private void strip(BlockPos from, BlockPos pos) {
        var other = nodes.get(from);
        if (other == null) return;
        var dirty = false;
        for (var set : other.outputs.values()) dirty |= set.removeIf(key -> key.pos().equals(pos));
        for (var set : other.inputs.values()) dirty |= set.removeIf(key -> key.pos().equals(pos));
        if (!dirty) return;
        other.order.keySet().removeIf(key -> key.pos().equals(pos));
        other.applied.clear();
        changed(from);
    }

    public boolean link(Level level, Node.Key from, Node.Key to) {
        if (!valid(level, from, to)) {
            if (!valid(level, to, from)) return false;
            var flipped = from;
            from = to;
            to = flipped;
        }
        var target = create(to.pos());
        create(from.pos()).outputs(from.port()).add(to);
        if (target.inputs(to.port()).add(from)) target.order.putIfAbsent(from, stamp(target));
        changed(from.pos());
        changed(to.pos());
        return true;
    }

    public boolean unlink(Level level, Node.Key a, Node.Key b) {
        var dropped = drop(a, b);
        dropped |= drop(b, a);
        if (!dropped) return false;
        var first = nodes.get(a.pos());
        var second = nodes.get(b.pos());
        if (first != null) {
            first.applied.clear();
            if (first.empty()) remove(level, a.pos());
        }
        if (second != null) {
            second.applied.clear();
            if (second.empty()) remove(level, b.pos());
        }
        changed(a.pos());
        changed(b.pos());
        return true;
    }

    private boolean drop(Node.Key from, Node.Key to) {
        var source = nodes.get(from.pos());
        var target = nodes.get(to.pos());
        if (source == null || target == null) return false;
        var dropped = source.outputs(from.port()).remove(to);
        dropped |= target.inputs(to.port()).remove(from);
        if (dropped && !feeds(target, from)) target.order.remove(from);
        return dropped;
    }

    private static boolean feeds(Node node, Node.Key source) {
        for (var set : node.inputs.values()) if (set.contains(source)) return true;
        return false;
    }

    private static int stamp(Node node) {
        var next = 0;
        for (var index : node.order.values()) next = Math.max(next, index + 1);
        return next;
    }

    public void order(Node.Key from, Node.Key to, int index) {
        if (index < 0) return;
        var target = nodes.get(to.pos());
        if (target != null && target.inputs(to.port()).contains(from)) {
            target.order.put(from, index);
            return;
        }
        var flipped = nodes.get(from.pos());
        if (flipped != null && flipped.inputs(from.port()).contains(to)) flipped.order.put(to, index);
    }

    public int order(Node.Key from, Node.Key to) {
        var target = nodes.get(to.pos());
        var index = target == null ? null : target.order.get(from);
        return index == null ? -1 : index;
    }

    private void reset(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return;
        for (var port : NodeHosts.ports(level, pos)) {
            if (!port.flow().canInput()) continue;
            NodeHosts.write(level, pos, port, port.merge().reduce(port.channel(), List.of()));
        }
    }

    public boolean valid(Level level, Node.Key from, Node.Key to) {
        if (from.equals(to)) return false;
        var source = NodeHosts.port(level, from);
        var target = NodeHosts.port(level, to);
        if (source == null || target == null) return false;
        if (!source.flow().canOutput() || !target.flow().canInput()) return false;
        return !outOfRange(level, from.pos(), to.pos());
    }

    public static boolean outOfRange(Level level, BlockPos a, BlockPos b) {
        var from = Sable.HELPER.projectOutOfSubLevel(level, a.getCenter());
        var to = Sable.HELPER.projectOutOfSubLevel(level, b.getCenter());
        return from.distanceTo(to) >= 32;
    }

    public void remap(Map<BlockPos, BlockPos> moves) {
        if (moves.isEmpty() || nodes.isEmpty()) return;
        var moved = new HashMap<BlockPos, Node>(nodes.size());
        for (var entry : nodes.entrySet()) moved.put(moves.getOrDefault(entry.getKey(), entry.getKey()), entry.getValue());
        for (var node : moved.values()) for (var set : node.outputs.values()) {
            var mapped = new HashSet<Node.Key>(set.size());
            for (var link : set) {
                var target = moves.get(link.pos());
                mapped.add(target == null ? link : new Node.Key(target, link.port()));
            }
            set.clear();
            set.addAll(mapped);
        }
        for (var pos : nodes.keySet()) changed(pos);
        nodes.clear();
        nodes.putAll(moved);
        rebuild();
        for (var pos : moved.keySet()) changed(pos);
    }

    public void rebuild() {
        for (var node : nodes.values()) node.inputs.clear();
        for (var entry : nodes.entrySet()) {
            var from = entry.getKey();
            for (var port : entry.getValue().outputs.entrySet()) for (var link : port.getValue()) {
                var target = nodes.get(link.pos());
                if (target == null) continue;
                target.inputs(link.port()).add(new Node.Key(from, port.getKey()));
            }
        }
    }

    public void tick(Level level) {
        if (nodes.isEmpty()) return;
        var active = new HashMap<BlockPos, List<Node.Port>>(nodes.size());
        var dead = new ArrayList<BlockPos>();
        var stale = new ArrayList<Node.Key>();
        for (var entry : nodes.entrySet()) {
            var pos = entry.getKey();
            if (!level.isLoaded(pos)) continue;
            var ports = NodeHosts.ports(level, pos);
            if (ports.isEmpty()) {
                if (!level.getBlockState(pos).is(Blocks.MOVING_PISTON) || ++entry.getValue().missing > 20) dead.add(pos);
                continue;
            }
            entry.getValue().missing = 0;
            active.put(pos, ports);
            collectStale(pos, entry.getValue().outputs, ports, stale);
            collectStale(pos, entry.getValue().inputs, ports, stale);
        }
        for (var pos : dead) {
            var plugs = plugs(pos);
            if (plugs > 0) LooseNodes.give(level, pos, plugs);
            remove(level, pos);
        }
        for (var key : stale) {
            var node = nodes.get(key.pos());
            if (node == null) continue;
            for (var link : List.copyOf(node.outputs(key.port()))) unlink(level, key, link);
            for (var link : List.copyOf(node.inputs(key.port()))) unlink(level, key, link);
        }
        active.keySet().retainAll(nodes.keySet());
        if (active.isEmpty()) return;
        var signals = new ArrayList<Node.Signal>();
        for (var i = 0; i < 8; i++) if (!step(level, active, signals)) break;
    }

    private void collectStale(BlockPos pos, Map<String, Set<Node.Key>> links, List<Node.Port> ports, List<Node.Key> stale) {
        for (var entry : links.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            var found = false;
            for (var port : ports) if (port.id().equals(entry.getKey())) {
                found = true;
                break;
            }
            if (!found) stale.add(new Node.Key(pos, entry.getKey()));
        }
    }

    private boolean step(Level level, Map<BlockPos, List<Node.Port>> active, List<Node.Signal> signals) {
        for (var entry : active.entrySet()) {
            var pos = entry.getKey();
            var node = nodes.get(pos);
            if (node == null) continue;
            for (var port : entry.getValue()) {
                if (!port.flow().canOutput()) continue;
                var signal = NodeHosts.read(level, pos, port);
                node.published.put(port.id(), signal == null ? port.channel().zero() : signal);
            }
        }
        var changed = false;
        for (var entry : active.entrySet()) {
            var pos = entry.getKey();
            var node = nodes.get(pos);
            if (node == null) continue;
            for (var port : entry.getValue()) {
                if (!port.flow().canInput()) continue;
                signals.clear();
                for (var link : inputs(new Node.Key(pos, port.id()))) {
                    var source = nodes.get(link.pos());
                    if (source == null) continue;
                    var value = source.published.get(link.port());
                    if (value == null) continue;
                    signals.add(value.as(port.channel()));
                }
                var merged = port.merge().reduce(port.channel(), signals);
                if (merged.equals(node.applied.get(port.id()))) continue;
                node.applied.put(port.id(), merged);
                NodeHosts.write(level, pos, port, merged);
                changed = true;
            }
        }
        return changed;
    }

    public CompoundTag write(CompoundTag tag) {
        var list = new ListTag();
        for (var entry : nodes.entrySet()) {
            var node = entry.getValue();
            var nbt = new CompoundTag();
            nbt.putLong("Pos", entry.getKey().asLong());
            if (node.anchored) nbt.putBoolean("Anchored", true);
            if (!node.loose.isEmpty()) {
                var loose = new int[node.loose.size()];
                var cursor = 0;
                for (var id : node.loose) loose[cursor++] = id;
                nbt.putIntArray("Loose", loose);
            }
            var ports = createPorts(entry.getKey(), node);
            nbt.put("Ports", ports);
            list.add(nbt);
        }
        tag.put("Nodes", list);
        return tag;
    }

    private @NotNull ListTag createPorts(BlockPos pos, Node node) {
        var ports = new ListTag();
        for (var port : node.outputs.entrySet()) {
            if (port.getValue().isEmpty()) continue;
            var mine = new Node.Key(pos, port.getKey());
            var links = new ListTag();
            for (var link : port.getValue()) {
                var linkNbt = new CompoundTag();
                linkNbt.putLong("Pos", link.pos().asLong());
                linkNbt.putString("Port", link.port());
                var index = order(mine, link);
                if (index >= 0) linkNbt.putInt("Order", index);
                links.add(linkNbt);
            }
            var portNbt = new CompoundTag();
            portNbt.putString("Id", port.getKey());
            portNbt.put("Links", links);
            ports.add(portNbt);
        }
        return ports;
    }

    public void read(CompoundTag tag) {
        nodes.clear();
        var orders = new HashMap<BlockPos, Map<Node.Key, Integer>>();
        var list = tag.getList("Nodes", Tag.TAG_COMPOUND);
        for (var i = 0; i < list.size(); i++) {
            var nbt = list.getCompound(i);
            var pos = BlockPos.of(nbt.getLong("Pos"));
            var node = new Node();
            node.anchored = nbt.getBoolean("Anchored");
            for (var id : nbt.getIntArray("Loose")) {
                LooseNodes.any = true;
                node.loose.add(id);
            }
            var ports = nbt.getList("Ports", Tag.TAG_COMPOUND);
            for (var p = 0; p < ports.size(); p++) {
                var portNbt = ports.getCompound(p);
                var links = portNbt.getList("Links", Tag.TAG_COMPOUND);
                var set = node.outputs(portNbt.getString("Id"));
                for (var l = 0; l < links.size(); l++) {
                    var linkNbt = links.getCompound(l);
                    var target = BlockPos.of(linkNbt.getLong("Pos"));
                    if (linkNbt.contains("Order")) orders.computeIfAbsent(target, key -> new HashMap<>())
                            .put(new Node.Key(pos, portNbt.getString("Id")), linkNbt.getInt("Order"));
                    set.add(new Node.Key(target, linkNbt.getString("Port")));
                }
            }
            nodes.put(pos, node);
        }
        for (var entry : orders.entrySet()) {
            var node = nodes.get(entry.getKey());
            if (node != null) node.order.putAll(entry.getValue());
        }
        rebuild();
    }

    public void changed(BlockPos pos) {
        if (listener != null) listener.accept(pos.immutable());
    }
}

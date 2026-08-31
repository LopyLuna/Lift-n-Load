package dev.lopyluna.create_lnl.content.nodes;

import com.simibubi.create.content.contraptions.StructureTransform;
import dev.lopyluna.create_lnl.content.blocks.logic_byte.LogicSlot;
import dev.lopyluna.create_lnl.content.nodes.loose.LooseNodes;
import dev.lopyluna.create_lnl.content.nodes.loose.NodeCell;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public class NodePayload {
    public static BlockPos anchor(BlockPos pos, int cell) {
        return NodeCell.depth(cell) == 0 ? pos.relative(NodeCell.face(cell).getOpposite()) : pos;
    }

    public static boolean carried(Node.Key key, Predicate<BlockPos> owns) {
        var cell = NodeCell.parse(key.port());
        return owns.test(cell == null ? key.pos() : anchor(key.pos(), cell));
    }

    public static Set<BlockPos> candidates(NodeGraph graph, Iterable<BlockPos> moved) {
        var found = new HashSet<BlockPos>();
        for (var pos : moved) {
            if (graph.nodes.containsKey(pos)) found.add(pos.immutable());
            for (var dir : Direction.values()) {
                var side = pos.relative(dir);
                if (graph.nodes.containsKey(side)) found.add(side);
            }
        }
        return found;
    }

    @Nullable
    public static CompoundTag save(NodeGraph graph, BlockPos pos, Predicate<BlockPos> owns, UnaryOperator<BlockPos> localise, boolean external) {
        var node = graph.node(pos);
        if (node == null) return null;
        var tag = new CompoundTag();
        if (node.anchored && owns.test(pos)) tag.putBoolean("Anchored", true);
        var carried = new ArrayList<Integer>(node.loose.size());
        for (var id : node.loose) if (owns.test(anchor(pos, id))) carried.add(id);
        if (!carried.isEmpty()) {
            var loose = new int[carried.size()];
            for (var i = 0; i < loose.length; i++) loose[i] = carried.get(i);
            tag.putIntArray("Loose", loose);
        }
        var links = new ListTag();
        for (var entry : node.outputs.entrySet()) {
            if (!carried(new Node.Key(pos, entry.getKey()), owns)) continue;
            for (var link : entry.getValue()) {
                var inside = carried(link, owns);
                if (!inside && !external) continue;
                links.add(wire(entry.getKey(), inside ? localise.apply(link.pos()) : link.pos(), link.port(), true, !inside));
            }
        }
        if (external) for (var entry : node.inputs.entrySet()) {
            if (!carried(new Node.Key(pos, entry.getKey()), owns)) continue;
            for (var link : entry.getValue()) {
                if (carried(link, owns)) continue;
                links.add(wire(entry.getKey(), link.pos(), link.port(), false, true));
            }
        }
        if (!links.isEmpty()) tag.put("Links", links);
        return tag.isEmpty() ? null : tag;
    }

    private static CompoundTag wire(String mine, BlockPos pos, String port, boolean out, boolean external) {
        var nbt = new CompoundTag();
        nbt.putString("Mine", mine);
        nbt.putLong("Pos", pos.asLong());
        nbt.putString("Port", port);
        if (out) nbt.putBoolean("Out", true);
        if (external) nbt.putBoolean("Ext", true);
        return nbt;
    }

    public static void clear(Level level, NodeGraph graph, BlockPos pos, boolean moved, CompoundTag tag) {
        if (tag.getBoolean("Anchored")) graph.unanchor(level, pos);
        for (var id : tag.getIntArray("Loose")) graph.detach(level, pos, id);
        if (!moved) return;
        var node = graph.node(pos);
        if (node == null) return;
        for (var port : List.copyOf(node.outputs.keySet())) if (NodeCell.parse(port) == null) graph.cut(level, new Node.Key(pos, port));
        for (var port : List.copyOf(node.inputs.keySet())) if (NodeCell.parse(port) == null) graph.cut(level, new Node.Key(pos, port));
    }

    public static void load(Level level, BlockPos pos, CompoundTag tag, @Nullable StructureTransform transform) {
        var graph = Node.Graphs.get(level);
        var node = graph.create(pos);
        if (tag.getBoolean("Anchored")) node.anchored = true;
        for (var id : tag.getIntArray("Loose")) node.loose.add(transform == null ? id : NodeCell.transform(id, transform));
        if (!node.loose.isEmpty()) LooseNodes.any = true;
        graph.changed(pos);
    }

    public static void wire(Level level, BlockPos pos, CompoundTag tag, UnaryOperator<BlockPos> place, @Nullable StructureTransform transform) {
        var graph = Node.Graphs.get(level);
        for (var link : wires(pos, tag, place, transform)) graph.link(level, link.from(), link.to());
    }

    public static List<Wire> wires(BlockPos pos, CompoundTag tag, UnaryOperator<BlockPos> place, @Nullable StructureTransform transform) {
        var links = tag.getList("Links", Tag.TAG_COMPOUND);
        var wires = new ArrayList<Wire>(links.size());
        for (var i = 0; i < links.size(); i++) {
            var nbt = links.getCompound(i);
            var external = nbt.getBoolean("Ext");
            var stored = BlockPos.of(nbt.getLong("Pos"));
            var mine = new Node.Key(pos, port(nbt.getString("Mine"), transform));
            var other = new Node.Key(external ? stored : place.apply(stored),
                    external ? nbt.getString("Port") : port(nbt.getString("Port"), transform));
            wires.add(nbt.getBoolean("Out") ? new Wire(mine, other) : new Wire(other, mine));
        }
        return wires;
    }

    public record Wire(Node.Key from, Node.Key to) {}

    public static CompoundTag rebase(CompoundTag tag, UnaryOperator<BlockPos> place, @Nullable StructureTransform transform) {
        var out = new CompoundTag();
        if (tag.getBoolean("Anchored")) out.putBoolean("Anchored", true);
        var loose = tag.getIntArray("Loose");
        if (loose.length > 0) {
            var cells = new int[loose.length];
            for (var i = 0; i < loose.length; i++) cells[i] = transform == null ? loose[i] : NodeCell.transform(loose[i], transform);
            out.putIntArray("Loose", cells);
        }
        var links = tag.getList("Links", Tag.TAG_COMPOUND);
        var moved = new ListTag();
        for (var i = 0; i < links.size(); i++) {
            var nbt = links.getCompound(i);
            if (nbt.getBoolean("Ext")) continue;
            moved.add(wire(port(nbt.getString("Mine"), transform), place.apply(BlockPos.of(nbt.getLong("Pos"))),
                    port(nbt.getString("Port"), transform), nbt.getBoolean("Out"), false));
        }
        if (!moved.isEmpty()) out.put("Links", moved);
        return out;
    }

    public static int plugs(CompoundTag tag) {
        return (tag.getBoolean("Anchored") ? 1 : 0) + tag.getIntArray("Loose").length;
    }

    public static BlockPos trigger(BlockPos pos, CompoundTag tag) {
        if (tag.getBoolean("Anchored")) return pos;
        var loose = tag.getIntArray("Loose");
        if (loose.length == 0) return pos;
        return NodeCell.depth(loose[0]) == 0 ? pos.relative(NodeCell.face(loose[0]).getOpposite()) : pos;
    }

    public static String port(String id, @Nullable StructureTransform transform) {
        if (transform == null) return id;
        var cell = NodeCell.parse(id);
        if (cell != null) return NodeCell.id(NodeCell.transform(cell, transform));
        var slot = LogicSlot.of(id);
        return slot == null ? id : slot.transform(transform).id;
    }
}

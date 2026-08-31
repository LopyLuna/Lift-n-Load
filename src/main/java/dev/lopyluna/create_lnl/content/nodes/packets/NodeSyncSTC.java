package dev.lopyluna.create_lnl.content.nodes.packets;

import dev.lopyluna.create_lnl.content.nodes.Node;
import dev.lopyluna.create_lnl.content.nodes.NodeGraph;
import dev.lopyluna.create_lnl.register.LiftsPackets;
import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.ClientboundPacketPayload;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record NodeSyncSTC(boolean full, List<Entry> entries) implements ClientboundPacketPayload {
    public static final StreamCodec<ByteBuf, NodeSyncSTC> STREAM_CODEC = StreamCodec.of(NodeSyncSTC::encode, NodeSyncSTC::decode);

    public record Entry(BlockPos pos, boolean present, boolean anchored, int[] loose, Map<String, List<Node.Key>> outputs) {}

    public static Entry of(NodeGraph graph, BlockPos pos) {
        var node = graph.node(pos);
        if (node == null) return new Entry(pos, false, false, new int[0], Map.of());
        var outputs = new HashMap<String, List<Node.Key>>(node.outputs.size());
        for (var port : node.outputs.entrySet()) if (!port.getValue().isEmpty()) outputs.put(port.getKey(), List.copyOf(port.getValue()));
        var loose = new int[node.loose.size()];
        var cursor = 0;
        for (var id : node.loose) loose[cursor++] = id;
        return new Entry(pos, true, node.anchored, loose, outputs);
    }

    private static void encode(ByteBuf buf, NodeSyncSTC packet) {
        buf.writeBoolean(packet.full);
        VarInt.write(buf, packet.entries.size());
        for (var entry : packet.entries) {
            BlockPos.STREAM_CODEC.encode(buf, entry.pos());
            buf.writeBoolean(entry.present());
            if (!entry.present()) continue;
            buf.writeBoolean(entry.anchored());
            VarInt.write(buf, entry.loose().length);
            for (var id : entry.loose()) VarInt.write(buf, id);
            VarInt.write(buf, entry.outputs().size());
            for (var port : entry.outputs().entrySet()) {
                ByteBufCodecs.STRING_UTF8.encode(buf, port.getKey());
                VarInt.write(buf, port.getValue().size());
                for (var link : port.getValue()) Node.Key.STREAM_CODEC.encode(buf, link);
            }
        }
    }

    private static NodeSyncSTC decode(ByteBuf buf) {
        var full = buf.readBoolean();
        var count = VarInt.read(buf);
        var entries = new ArrayList<Entry>(count);
        for (var i = 0; i < count; i++) {
            var pos = BlockPos.STREAM_CODEC.decode(buf);
            var present = buf.readBoolean();
            if (!present) {
                entries.add(new Entry(pos, false, false, new int[0], Map.of()));
                continue;
            }
            var anchored = buf.readBoolean();
            var loose = new int[VarInt.read(buf)];
            for (var l = 0; l < loose.length; l++) loose[l] = VarInt.read(buf);
            var ports = VarInt.read(buf);
            var outputs = new HashMap<String, List<Node.Key>>(ports);
            for (var p = 0; p < ports; p++) {
                var id = ByteBufCodecs.STRING_UTF8.decode(buf);
                var size = VarInt.read(buf);
                var keys = new ArrayList<Node.Key>(size);
                for (var l = 0; l < size; l++) keys.add(Node.Key.STREAM_CODEC.decode(buf));
                outputs.put(id, keys);
            }
            entries.add(new Entry(pos, true, anchored, loose, outputs));
        }
        return new NodeSyncSTC(full, entries);
    }

    @Override
    public void handle(LocalPlayer player) {
        var graph = Node.Graphs.CLIENT;
        if (full) graph.nodes.clear();
        for (var entry : entries) {
            if (!entry.present()) {
                graph.nodes.remove(entry.pos());
                continue;
            }
            var node = graph.create(entry.pos());
            node.anchored = entry.anchored();
            node.loose.clear();
            for (var id : entry.loose()) node.loose.add(id);
            node.outputs.clear();
            for (var port : entry.outputs().entrySet()) node.outputs(port.getKey()).addAll(port.getValue());
        }
        graph.rebuild();
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return LiftsPackets.NODE_SYNC;
    }
}

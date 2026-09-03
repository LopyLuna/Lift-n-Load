package dev.lopyluna.create_lnl.content.nodes.packets;

import dev.lopyluna.create_lnl.content.nodes.Node;
import dev.lopyluna.create_lnl.content.nodes.loose.LooseNodes;
import dev.lopyluna.create_lnl.content.nodes.loose.NodeCell;
import dev.lopyluna.create_lnl.content.nodes.NodeGraph;
import dev.lopyluna.create_lnl.register.LiftsItems;
import dev.lopyluna.create_lnl.register.LiftsPackets;
import dev.ryanhcode.sable.Sable;
import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public record NodeEditCTS(Node.Key from, Node.Key to) implements ServerboundPacketPayload {
    public static final StreamCodec<ByteBuf, NodeEditCTS> STREAM_CODEC = StreamCodec.composite(
            Node.Key.STREAM_CODEC, NodeEditCTS::from,
            Node.Key.STREAM_CODEC, NodeEditCTS::to,
            NodeEditCTS::new
    );

    @Override
    public void handle(ServerPlayer player) {
        var level = player.level();
        if (!level.isLoaded(from.pos()) || !level.isLoaded(to.pos())) return;
        var eye = player.getEyePosition();
        if (eye.distanceTo(Sable.HELPER.projectOutOfSubLevel(level, from.pos().getCenter())) > 48) return;
        if (eye.distanceTo(Sable.HELPER.projectOutOfSubLevel(level, to.pos().getCenter())) > 48) return;
        var graph = Node.Graphs.get(level);
        if (from.equals(to)) unplug(level, player, graph, from);
        else if (graph.linked(from, to)) graph.unlink(level, from, to);
        else graph.link(level, from, to);
        Node.Graphs.flush(level);
    }

    private static void unplug(Level level, ServerPlayer player, NodeGraph graph, Node.Key key) {
        var id = NodeCell.parse(key.port());
        if (id != null) {
            LooseNodes.pull(level, key.pos(), id);
            return;
        }
        if (!graph.anchored(key.pos())) return;
        graph.unanchor(level, key.pos());
        if (player.hasInfiniteMaterials()) return;
        player.getInventory().placeItemBackInInventory(new ItemStack(LiftsItems.NODE_PLUG.get()));
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return LiftsPackets.NODE_EDIT;
    }
}

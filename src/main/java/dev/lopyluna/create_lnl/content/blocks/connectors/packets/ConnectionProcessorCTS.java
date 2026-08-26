package dev.lopyluna.create_lnl.content.blocks.connectors.packets;

import dev.lopyluna.create_lnl.content.blocks.connectors.ConnectionType;
import dev.lopyluna.create_lnl.content.blocks.connectors.ConnectorUtils;
import dev.lopyluna.create_lnl.content.blocks.connectors.IConnection;
import dev.lopyluna.create_lnl.register.LiftsPackets;
import dev.ryanhcode.sable.Sable;
import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Optional;

public record ConnectionProcessorCTS(@Nullable BlockPos from, @Nullable BlockPos to) implements ServerboundPacketPayload {
    public static final StreamCodec<ByteBuf, ConnectionProcessorCTS> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), p -> Optional.ofNullable(p.from),
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), p -> Optional.ofNullable(p.to),
            (from, to) -> new ConnectionProcessorCTS(from.orElse(null), to.orElse(null))
    );

    @Override
    public void handle(ServerPlayer player) {
        if (from == null || to == null) return;
        var level = player.level();
        var outOfRange = Sable.HELPER.projectOutOfSubLevel(level, from.getCenter()).distanceTo(Sable.HELPER.projectOutOfSubLevel(level, to.getCenter())) >= 32;
        var sBE = level.getBlockEntity(from);
        var eBE = level.getBlockEntity(to);
        if (!(sBE instanceof IConnection<?> s) || !(eBE instanceof IConnection<?> e)) return;
        var flag = false;

        var sOut = s.getConnectType() == ConnectionType.OUT;
        var eOut = e.getConnectType() == ConnectionType.OUT;

        if (sOut) {
            if (eOut && e.lifts$containsConnection(from)) e.removeConnection(eBE, level, from, true);
            else if (s.lifts$containsConnection(to)) s.removeConnection(sBE, level, to, true);
            else if (ConnectorUtils.canConnect(level, from, s, to, e) && !outOfRange) s.addConnection(sBE, level, to, true);

            flag = (eOut && e.lifts$containsConnection(from)) || s.lifts$containsConnection(to) || (ConnectorUtils.canConnect(level, from, s, to, e) && !outOfRange);
        } else if (eOut) {
            if (e.lifts$containsConnection(from)) e.removeConnection(eBE, level, from, true);
            else if (ConnectorUtils.canConnect(level, from, s, to, e) && !outOfRange) e.addConnection(eBE, level, from, true);
            flag = e.lifts$containsConnection(from) || (ConnectorUtils.canConnect(level, from, s, to, e) && !outOfRange);
        }
        if (flag) {
            s.updateConnection(sBE, from);
            e.updateConnection(eBE, to);
        }
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return LiftsPackets.CONNECTION_PROCESSOR;
    }
}

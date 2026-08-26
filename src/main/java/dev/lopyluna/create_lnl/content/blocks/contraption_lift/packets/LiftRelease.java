package dev.lopyluna.create_lnl.content.blocks.contraption_lift.packets;

import dev.lopyluna.create_lnl.content.blocks.contraption_lift.LiftHolding;
import dev.lopyluna.create_lnl.register.LiftsPackets;
import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

public record LiftRelease() implements ServerboundPacketPayload {
    public static final LiftRelease INSTANCE = new LiftRelease();
    public static final StreamCodec<ByteBuf, LiftRelease> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override public void handle(ServerPlayer player) { LiftHolding.release(player); }
    @Override public PacketTypeProvider getTypeProvider() { return LiftsPackets.LIFT_RELEASE; }
}

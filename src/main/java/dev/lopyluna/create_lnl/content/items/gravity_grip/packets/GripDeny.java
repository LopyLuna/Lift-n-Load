package dev.lopyluna.create_lnl.content.items.gravity_grip.packets;

import dev.lopyluna.create_lnl.content.items.gravity_grip.GravityGripHandler;
import dev.lopyluna.create_lnl.register.LiftsPackets;
import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.ClientboundPacketPayload;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public record GripDeny() implements ClientboundPacketPayload {
    public static final GripDeny INSTANCE = new GripDeny();
    public static final StreamCodec<ByteBuf, GripDeny> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override @OnlyIn(Dist.CLIENT) public void handle(LocalPlayer player) { GravityGripHandler.denied(); }
    @Override public PacketTypeProvider getTypeProvider() { return LiftsPackets.GRIP_DENY; }
}

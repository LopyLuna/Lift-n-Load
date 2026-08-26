package dev.lopyluna.create_lnl.content.blocks.contraption_lift.packets;

import dev.lopyluna.create_lnl.content.blocks.contraption_lift.LiftPlayerData;
import dev.lopyluna.create_lnl.register.LiftsAttachments;
import dev.lopyluna.create_lnl.register.LiftsPackets;
import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.ClientboundPacketPayload;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public record LiftPlayerDataSync(LiftPlayerData data) implements ClientboundPacketPayload {
    public static final StreamCodec<ByteBuf, LiftPlayerDataSync> STREAM_CODEC = StreamCodec.composite(
            LiftPlayerData.STREAM_CODEC, LiftPlayerDataSync::data,
            LiftPlayerDataSync::new
    );

    @Override @OnlyIn(Dist.CLIENT) public void handle(LocalPlayer player) { player.setData(LiftsAttachments.LIFT_PLAYER_DATA, data); }
    @Override public PacketTypeProvider getTypeProvider() { return LiftsPackets.LIFT_PLAYER_DATA; }
}

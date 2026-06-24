package dev.lopyluna.create_lnl.content.blocks.contraption_lift;

import dev.lopyluna.create_lnl.register.LiftsPackets;
import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

public record LiftActions(double movDelta, double rotDelta) implements ServerboundPacketPayload {
    public static final StreamCodec<ByteBuf, LiftActions> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, LiftActions::movDelta,
            ByteBufCodecs.DOUBLE, LiftActions::rotDelta,
            LiftActions::new
    );

    @Override
    public void handle(ServerPlayer player) {
        var nbt = LiftBE.getOrCreateLiftNbt(player);
        LiftBE.setMovDelta(nbt, movDelta);
        LiftBE.setRotDelta(nbt, rotDelta);
        LiftBE.saveLiftNbt(player, nbt);
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return LiftsPackets.LIFT_ACTIONS;
    }
}

package dev.lopyluna.create_lnl.content.blocks.contraption_lift.packets;

import dev.lopyluna.create_lnl.content.blocks.contraption_lift.DockingLiftBE;
import dev.lopyluna.create_lnl.content.blocks.contraption_lift.DockingLiftHandler;
import dev.lopyluna.create_lnl.content.blocks.contraption_lift.LiftHolding;
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
        var mov = (int) Math.signum(movDelta);
        var rot = (int) Math.signum(rotDelta);
        if (rot != 0) {
            if (LiftHolding.isHolding(player)) LiftHolding.rotate(player, rot);
            else {
                var lift = DockingLiftBE.controlledBy(player);
                if (lift != null && !lift.placing && !lift.cantControl(player)) lift.rotate(rot);
            }
        }
        DockingLiftHandler.setInput(player, mov, rot);
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return LiftsPackets.LIFT_ACTIONS;
    }
}

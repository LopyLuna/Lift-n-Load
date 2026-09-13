package dev.lopyluna.create_lnl.content.items.gravity_grip.packets;

import dev.lopyluna.create_lnl.content.items.gravity_grip.GravityGripItem;
import dev.lopyluna.create_lnl.register.LiftsPackets;
import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.UUID;

public record GripSync(@Nullable UUID sub, Vector3d anchor, Vector3d goal, Quaterniond frame, double grip) implements ServerboundPacketPayload {
    public static final StreamCodec<ByteBuf, GripSync> STREAM_CODEC = StreamCodec.ofMember(GripSync::write, GripSync::read);

    public static GripSync drop() {
        return new GripSync(null, new Vector3d(), new Vector3d(), new Quaterniond(), 0);
    }

    private static GripSync read(ByteBuf buf) {
        if (!buf.readBoolean()) return drop();
        var sub = new UUID(buf.readLong(), buf.readLong());
        var anchor = new Vector3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
        var goal = new Vector3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
        var frame = new Quaterniond(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
        var length = frame.lengthSquared();
        if (!Double.isFinite(length) || length < 1.0E-6) frame.identity();
        var grip = buf.readDouble();
        return new GripSync(sub, anchor, goal, frame.normalize(), Double.isFinite(grip) ? Mth.clamp(grip, 0, 1) : 0);
    }

    private static boolean finite(Vector3d vec) {
        return Double.isFinite(vec.x) && Double.isFinite(vec.y) && Double.isFinite(vec.z);
    }

    private void write(ByteBuf buf) {
        buf.writeBoolean(sub != null);
        if (sub == null) return;
        buf.writeLong(sub.getMostSignificantBits());
        buf.writeLong(sub.getLeastSignificantBits());
        buf.writeDouble(anchor.x);
        buf.writeDouble(anchor.y);
        buf.writeDouble(anchor.z);
        buf.writeDouble(goal.x);
        buf.writeDouble(goal.y);
        buf.writeDouble(goal.z);
        buf.writeDouble(frame.x);
        buf.writeDouble(frame.y);
        buf.writeDouble(frame.z);
        buf.writeDouble(frame.w);
        buf.writeDouble(grip);
    }

    @Override
    public void handle(ServerPlayer player) {
        if (sub == null || !finite(goal) || !finite(anchor)) GravityGripItem.release(player);
        else GravityGripItem.sync(player, sub, anchor, goal, frame, grip);
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return LiftsPackets.GRIP_SYNC;
    }
}

package dev.lopyluna.create_lnl.content.blocks.contraption_lift;

import dev.lopyluna.create_lnl.content.blocks.contraption_lift.packets.LiftPlayerDataSync;
import dev.lopyluna.create_lnl.register.LiftsAttachments;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

public class LiftHolding {

    public static LiftPlayerData get(Player player) {
        return player.getData(LiftsAttachments.LIFT_PLAYER_DATA);
    }

    public static boolean isHolding(Player player) {
        return get(player).isHolding();
    }

    public static @Nullable UUID heldId(Player player) {
        return get(player).heldSubLevel().orElse(null);
    }

    public static @Nullable SubLevel held(Player player) {
        return resolve(player.level(), heldId(player));
    }

    public static @Nullable SubLevel resolve(Level level, @Nullable UUID uuid) {
        if (uuid == null) return null;
        var container = SubLevelContainer.getContainer(level);
        return container == null ? null : container.getSubLevel(uuid);
    }

    public static void hold(Player player, @Nullable UUID subLevel) {
        mutate(player, data -> data.withHeld(Optional.ofNullable(subLevel)));
    }

    public static void release(Player player) {
        if (!isHolding(player)) return;
        mutate(player, data -> data.withHeld(Optional.empty()));
    }

    public static int rotation(Player player) {
        return get(player).rotation();
    }

    public static void rotate(Player player, int delta) {
        mutate(player, data -> data.withRotation(data.rotation() + delta));
    }

    public static int color(Player player) {
        return get(player).color();
    }

    public static @Nullable DyeColor dye(Player player) {
        var id = color(player);
        return id < 0 ? null : DyeColor.byId(id);
    }

    public static void setColor(Player player, @Nullable DyeColor dye) {
        mutate(player, data -> data.withColor(dye == null ? -1 : dye.getId()));
    }

    public static @Nullable GlobalPos liftPos(Player player) {
        return get(player).lift().orElse(null);
    }

    public static void setLiftPos(Player player, @Nullable GlobalPos pos) {
        mutate(player, data -> data.withLift(Optional.ofNullable(pos)));
    }

    public static void mutate(Player player, UnaryOperator<LiftPlayerData> op) {
        var current = get(player);
        var updated = op.apply(current);
        if (updated.equals(current)) return;
        player.setData(LiftsAttachments.LIFT_PLAYER_DATA, updated);
        if (player instanceof ServerPlayer serverPlayer) sync(serverPlayer);
    }

    public static void sync(ServerPlayer player) {
        CatnipServices.NETWORK.sendToClient(player, new LiftPlayerDataSync(get(player)));
    }
}

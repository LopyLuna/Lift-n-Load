package dev.lopyluna.create_lnl.content.utils;

import dev.lopyluna.create_lnl.content.blocks.thruster.ThrusterSoundInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

@SuppressWarnings("unused")
public class LiftSoundDistUtil {
    public static final Minecraft mc = Minecraft.getInstance();
    public static void tickGlobalThrusterSound() {

        if (mc.level != null && !mc.isPaused()) {
            var soundManager = mc.getSoundManager();

            if (!soundManager.isActive(ThrusterSoundInstance.GLOBAL_HOT_AIR_BURNER_SOUND)) soundManager.queueTickingSound(ThrusterSoundInstance.GLOBAL_HOT_AIR_BURNER_SOUND);
            if (!soundManager.isActive(ThrusterSoundInstance.GLOBAL_STEAM_VENT_AIR_BURNER_SOUND)) soundManager.queueTickingSound(ThrusterSoundInstance.GLOBAL_STEAM_VENT_AIR_BURNER_SOUND);
        }
    }

    public static void addPosHotAirBurnerSound(final BlockPos pos) {
        ThrusterSoundInstance.GLOBAL_HOT_AIR_BURNER_SOUND.addPos(pos);
    }

    public static void removePosHotAirBurnerSound(final BlockPos pos) {
        ThrusterSoundInstance.GLOBAL_HOT_AIR_BURNER_SOUND.removePos(pos);
    }

    public static void addPosSteamVentSound(final BlockPos pos) {
        ThrusterSoundInstance.GLOBAL_STEAM_VENT_AIR_BURNER_SOUND.addPos(pos);
    }

    public static void removePosSteamVentSound(final BlockPos pos) {
        ThrusterSoundInstance.GLOBAL_STEAM_VENT_AIR_BURNER_SOUND.removePos(pos);
    }
}

package dev.lopyluna.create_lnl.content.blocks.connectors;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lopyluna.create_lnl.content.blocks.connectors.packets.ConnectionProcessorCTS;
import dev.simulated_team.simulated.index.SimSoundEvents;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.Tags;

import javax.annotation.Nullable;

import static dev.lopyluna.create_lnl.Lifts.MOD_ID;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = MOD_ID)
public class ConnectionClientHandler {
    private static final Minecraft mc = Minecraft.getInstance();
    @Nullable public static BlockPos holdingPos = null;
    @Nullable private static LoopingSoundInstance sound = null;

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;
        if (!mc.player.getMainHandItem().is(Tags.Items.TOOLS_WRENCH)) return;
        if (sound != null && sound.getVolume() > 0) sound.setPos();
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != InputConstants.MOUSE_BUTTON_RIGHT) return;
        if (mc.player == null || mc.level == null) {
            if (sound != null) { mc.getSoundManager().stop(sound); sound = null; }
            return;
        }
        if (!mc.player.getMainHandItem().is(Tags.Items.TOOLS_WRENCH)) return;
        var action = event.getAction();

        if (action == 1) {
            var pt = AnimationTickHolder.getPartialTicks(mc.level);
            var hit = ConnectorUtils.hit(mc.level, mc.player, pt);
            holdingPos = hit == null || hit.getType() == HitResult.Type.MISS ? null : hit.getBlockPos();
            if (holdingPos != null) {
                if (sound == null) sound = new LoopingSoundInstance(mc.player, SimSoundEvents.STAFF_IDLE.event(), mc.level.random);
                sound.setVolume(0.75f);
                sound.setPitch(0.625f);
                if (!mc.getSoundManager().isActive(sound)) mc.getSoundManager().play(sound);

                event.setCanceled(true);
            }
            return;
        }
        if (action == 0 && holdingPos != null) {
            var pt = AnimationTickHolder.getPartialTicks(mc.level);
            var hit = ConnectorUtils.hit(mc.level, mc.player, pt);
            CatnipServices.NETWORK.sendToServer(new ConnectionProcessorCTS(holdingPos, hit == null || hit.getType() == HitResult.Type.MISS ? null : hit.getBlockPos()));
            holdingPos = null;
            event.setCanceled(true);
            if (sound != null) { sound.setVolume(0f); }
        }
    }

    public static class LoopingSoundInstance extends AbstractTickableSoundInstance {
        private final LocalPlayer player;
        public LoopingSoundInstance(final LocalPlayer player, final SoundEvent event, final RandomSource random) {
            super(event, SoundSource.PLAYERS, random);
            this.player = player;
            looping = true;
            delay = 0;
        }
        public void setVolume(final float volume) { this.volume = volume; }
        public void setPitch(final float pitch) { this.pitch = pitch; }
        public void setPos() { var pos = player.position(); x = pos.x; y = pos.y; z = pos.z; }
        @Override public void tick() {}
    }
}

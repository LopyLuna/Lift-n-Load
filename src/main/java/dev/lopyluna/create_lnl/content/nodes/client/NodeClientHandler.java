package dev.lopyluna.create_lnl.content.nodes.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lopyluna.create_lnl.content.nodes.NodeHosts;
import dev.lopyluna.create_lnl.content.nodes.packets.NodeEditCTS;
import dev.lopyluna.create_lnl.register.LiftsTags;
import dev.simulated_team.simulated.index.SimSoundEvents;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

import javax.annotation.Nullable;

import static dev.lopyluna.create_lnl.Lifts.MOD_ID;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
public class NodeClientHandler {
    private static final Minecraft mc = Minecraft.getInstance();
    @Nullable private static LoopingSoundInstance sound = null;

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (mc.player == null || mc.level == null || mc.screen != null || !mc.player.getMainHandItem().is(LiftsTags.NODE_CONNECTOR)) {
            drop();
            return;
        }
        if (NodeClient.holding != null && NodeHosts.port(mc.level, NodeClient.holding) == null) drop();
        if (sound != null && sound.getVolume() > 0) sound.setPos();
    }

    private static void drop() {
        NodeClient.holding = null;
        if (sound != null) sound.setVolume(0f);
    }

    @SubscribeEvent
    public static void onAttack(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack() || mc.player == null || mc.level == null) return;
        var key = NodeClient.punch(mc.level, mc.player, AnimationTickHolder.getPartialTicks(mc.level));
        if (key == null) return;
        CatnipServices.NETWORK.sendToServer(new NodeEditCTS(key, key));
        event.setSwingHand(true);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != InputConstants.MOUSE_BUTTON_RIGHT || mc.screen != null) return;
        if (mc.player == null || mc.level == null) {
            if (sound != null) {
                mc.getSoundManager().stop(sound);
                sound = null;
            }
            return;
        }
        if (!mc.player.getMainHandItem().is(LiftsTags.NODE_CONNECTOR)) return;
        var action = event.getAction();

        if (action == 1) {
            NodeClient.holding = NodeClient.pick(mc.level, mc.player, AnimationTickHolder.getPartialTicks(mc.level));
            if (NodeClient.holding == null) return;
            if (sound == null) sound = new LoopingSoundInstance(mc.player, SimSoundEvents.STAFF_IDLE.event(), mc.level.random);
            sound.setVolume(0.75f);
            sound.setPitch(0.625f);
            if (!mc.getSoundManager().isActive(sound)) mc.getSoundManager().play(sound);
            event.setCanceled(true);
            return;
        }
        if (action == 0) {
            var held = NodeClient.holding;
            drop();
            if (held == null) return;
            var target = NodeClient.pick(mc.level, mc.player, AnimationTickHolder.getPartialTicks(mc.level));
            if (target != null) CatnipServices.NETWORK.sendToServer(new NodeEditCTS(held, target));
            event.setCanceled(true);
        }
    }

    public static class LoopingSoundInstance extends AbstractTickableSoundInstance {
        private final LocalPlayer player;

        public LoopingSoundInstance(LocalPlayer player, SoundEvent event, RandomSource random) {
            super(event, SoundSource.PLAYERS, random);
            this.player = player;
            looping = true;
            delay = 0;
        }

        public void setVolume(float volume) {
            this.volume = volume;
        }

        public void setPitch(float pitch) {
            this.pitch = pitch;
        }

        public void setPos() {
            var pos = player.position();
            x = pos.x;
            y = pos.y;
            z = pos.z;
        }

        @Override
        public void tick() {}
    }
}

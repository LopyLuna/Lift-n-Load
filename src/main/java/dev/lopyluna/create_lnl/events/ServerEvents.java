package dev.lopyluna.create_lnl.events;

import dev.lopyluna.create_lnl.Lifts;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = Lifts.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class ServerEvents {
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        Lifts.LOGGER.info(Lifts.NAME + " SERVER SETUP");
    }

    @SubscribeEvent
    public static void useItemOn(UseItemOnBlockEvent event) {
    }
}
